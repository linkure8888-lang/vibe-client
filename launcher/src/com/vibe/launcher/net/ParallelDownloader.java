package com.vibe.launcher.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parallel file downloads.
 *
 * Why this exists: the launcher used to fetch the ~4300 Minecraft asset files
 * one at a time, each as its own HTTP request/response and each followed by a
 * SHA-1 hash. On a normal connection that is a few hundred milliseconds of
 * latency per file and nothing else — the bottleneck is round-trip time, not
 * bandwidth — so a full install crawled for minutes with the progress bar
 * barely moving.
 *
 * Running several downloads at once hides that latency. The cap matters:
 * unbounded parallelism gets the connection throttled or refused by the CDN,
 * which is slower than a modest limit. Eight is the sweet spot for
 * `resources.download.minecraft.net`, and retries handle the occasional 429.
 *
 * Design notes:
 *  - Progress is counted per completed file, not per byte: with many files in
 *    flight, a byte counter produces a bar that jumps around. A file counter
 *    moves smoothly and matches "a/b" in the status text.
 *  - The first failure is remembered and rethrown after every worker has
 *    stopped, so the caller sees one real error rather than a pile of them.
 *  - Files already present and matching their hash are skipped before any
 *    thread is started, so re-launching costs only the hashing.
 */
public final class ParallelDownloader {

    /** Concurrent downloads. Higher gets throttled by the asset CDN. */
    private static final int DEFAULT_THREADS = 8;

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 400;

    /** One file to fetch. */
    public static final class Item {
        public final String url;
        public final Path target;
        public final String sha1;
        public final long size;

        public Item(String url, Path target, String sha1, long size) {
            this.url = url;
            this.target = target;
            this.sha1 = sha1;
            this.size = size;
        }
    }

    /** Called as files complete, always from the worker threads. */
    public interface Progress {
        /**
         * @param name  a label for the current item, for the status line
         * @param done  files finished
         * @param total files to finish in this batch
         */
        void onFile(String name, long done, long total);
    }

    private ParallelDownloader() {
    }

    /**
     * Downloads every item that is not already present and correct.
     *
     * Returns the number of files actually fetched. Throws the first error
     * encountered once all workers have stopped.
     */
    public static int downloadAll(List<Item> items, Progress progress) throws IOException {
        if (items.isEmpty()) return 0;

        List<Item> pending = new ArrayList<>(items.size());
        for (Item item : items) {
            if (!Http.matches(item.target, item.sha1, item.size)) pending.add(item);
        }
        if (pending.isEmpty()) return 0;

        int threads = Math.min(DEFAULT_THREADS, pending.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "vibe-download");
            // Daemon: a wedged download must never keep the launcher alive.
            thread.setDaemon(true);
            return thread;
        });

        AtomicInteger nextIndex = new AtomicInteger(0);
        AtomicLong completed = new AtomicLong(0);
        AtomicReference<IOException> firstError = new AtomicReference<>();
        final long total = pending.size();

        List<Future<?>> futures = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                while (true) {
                    // Claim an index first: two workers must never take the
                    // same file, and the counter is the only shared state.
                    int index = nextIndex.getAndIncrement();
                    if (index >= pending.size()) return;
                    if (firstError.get() != null) return;

                    Item item = pending.get(index);
                    try {
                        fetchWithRetries(item);
                        long done = completed.incrementAndGet();
                        if (progress != null) {
                            progress.onFile(item.target.getFileName().toString(), done, total);
                        }
                    } catch (IOException e) {
                        // Keep the first failure; later ones are usually
                        // consequences of it (server-side throttling).
                        firstError.compareAndSet(null, e);
                        return;
                    }
                }
            }));
        }

        pool.shutdown();
        try {
            // Generous ceiling: a large file on a slow line still needs to finish.
            if (!pool.awaitTermination(30, TimeUnit.MINUTES)) {
                pool.shutdownNow();
                throw new IOException("download timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            throw new IOException("download interrupted", e);
        }

        IOException error = firstError.get();
        if (error != null) throw error;
        return pending.size();
    }

    /** One file, retried on transient failures with a growing backoff. */
    private static void fetchWithRetries(Item item) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                fetch(item);
                return;
            } catch (IOException e) {
                last = e;
                if (attempt == MAX_ATTEMPTS) break;
                try {
                    Thread.sleep(BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", ie);
                }
            }
        }
        throw last != null ? last : new IOException("download failed: " + item.url);
    }

    /**
     * Fetches one file into a `.part` sibling, verifies it, then renames.
     *
     * The rename is what makes an interrupted download harmless: a truncated
     * `.part` never becomes the real file, so the next run simply retries
     * instead of finding a corrupt asset that fails its hash.
     */
    private static void fetch(Item item) throws IOException {
        Path target = item.target;
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");

        HttpURLConnection conn = Http.open(item.url, "GET", "application/octet-stream");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + item.url);

            long declared = conn.getContentLengthLong();
            long written = 0;
            try (InputStream raw = conn.getInputStream();
                 java.io.BufferedInputStream in = new java.io.BufferedInputStream(raw, 64 * 1024);
                 OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    written += read;
                }
            }

            if (declared > 0 && written != declared) {
                Files.deleteIfExists(part);
                throw new IOException("short read for " + item.url
                    + ": " + written + "/" + declared);
            }

            // Verify before publishing: a corrupt asset crashes the game much
            // later, in a place that is far harder to diagnose.
            if (item.sha1 != null && !item.sha1.isBlank()) {
                String actual = sha1(part);
                if (!actual.equalsIgnoreCase(item.sha1)) {
                    Files.deleteIfExists(part);
                    throw new IOException("SHA-1 mismatch for " + target.getFileName());
                }
            }

            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }

    private static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-1 is required by the JDK spec; this cannot happen.
            throw new IOException("SHA-1 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
