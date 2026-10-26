package com.vibe.launcher.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * The only place that talks to the network.
 *
 * Rules:
 *  - every request has timeouts, so a dead server never hangs the launcher;
 *  - transient failures are retried a few times with a short backoff;
 *  - downloads go to a `.part` file and are renamed only after a successful
 *    SHA-1 check, so an interrupted download can never look complete;
 *  - callers get exceptions, never silent nulls.
 *
 * Runs on worker threads only. The Swing UI never calls this directly.
 */
public final class Http {

    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 700L;
    private static final String USER_AGENT = "VibeClient/1.0";

    private Http() {
    }

    /** Notified while a download progresses. Called from the downloading thread. */
    public interface ProgressListener {
        void onProgress(long done, long total);
    }

    // -- JSON ---------------------------------------------------------------

    public static JsonObject getJson(String url) throws IOException {
        return JsonParser.parseString(getString(url, "application/json")).getAsJsonObject();
    }

    /** For endpoints whose top-level value is an array (Fabric meta does this). */
    public static JsonArray getJsonArray(String url) throws IOException {
        return JsonParser.parseString(getString(url, "application/json")).getAsJsonArray();
    }

    /**
     * Reads a local JSON file. Separate from [getJson] on purpose: a `file:`
     * URL yields a FileURLConnection, not an HttpURLConnection, so routing a
     * local path through the HTTP code path would throw a ClassCastException.
     */
    public static JsonObject readJsonFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** GET with an `Authorization: Bearer` header (Minecraft services). */
    public static JsonObject getJsonWithBearer(String url, String token) throws IOException {
        HttpURLConnection conn = open(url, "GET", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) throw new IOException("HTTP " + code + " for " + url);
            String text;
            try (InputStream in = stream) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            json.addProperty("_status", code);
            return json;
        } finally {
            conn.disconnect();
        }
    }

    public static String getString(String url, String accept) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return getStringOnce(url, accept);
            } catch (IOException e) {
                last = e;
                if (attempt == MAX_ATTEMPTS) break;
                sleep(BACKOFF_MS * attempt);
            }
        }
        throw last != null ? last : new IOException("request failed: " + url);
    }

    private static String getStringOnce(String url, String accept) throws IOException {
        HttpURLConnection conn = open(url, "GET", accept);
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code + " for " + url);
            }
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** POSTs a form body and returns the JSON answer, whatever the status code. */
    public static JsonObject postForm(String url, String formBody, String contentType) throws IOException {
        HttpURLConnection conn = open(url, "POST", "application/json");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        try {
            byte[] body = formBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) throw new IOException("HTTP " + code + " with empty body for " + url);
            String text;
            try (InputStream in = stream) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            json.addProperty("_status", code);
            return json;
        } finally {
            conn.disconnect();
        }
    }

    /** POSTs JSON and returns the JSON answer. */
    public static JsonObject postJson(String url, JsonObject payload) throws IOException {
        return postForm(url, GSON.toJson(payload), "application/json");
    }

    /**
     * Opens a connection with the launcher's standard timeouts and headers.
     *
     * Package-private rather than private: [ParallelDownloader] needs the same
     * connection setup, and duplicating it there would let the two drift apart
     * on something like the User-Agent, which the Mojang CDN does check.
     */
    static HttpURLConnection open(String url, String method, String accept) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        if (accept != null) conn.setRequestProperty("Accept", accept);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    // -- Downloads ----------------------------------------------------------

    /**
     * Downloads [url] to [target] and verifies its SHA-1.
     * A null/blank [expectedSha1] skips the check (only for files Mojang does
     * not hash, which is currently none of the files we fetch).
     */
    public static void download(String url, Path target, String expectedSha1,
                                ProgressListener listener) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");

        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                downloadOnce(url, part, listener);
                if (expectedSha1 != null && !expectedSha1.isBlank()) {
                    String actual = sha1(part);
                    if (!actual.equalsIgnoreCase(expectedSha1)) {
                        Files.deleteIfExists(part);
                        throw new IOException("SHA-1 mismatch for " + target.getFileName()
                            + ": expected " + expectedSha1 + ", got " + actual);
                    }
                }
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                Files.deleteIfExists(part);
                if (attempt == MAX_ATTEMPTS) break;
                sleep(BACKOFF_MS * attempt);
            }
        }
        throw last != null ? last : new IOException("download failed: " + url);
    }

    private static void downloadOnce(String url, Path part, ProgressListener listener) throws IOException {
        HttpURLConnection conn = open(url, "GET", "application/octet-stream");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + url);
            long total = conn.getContentLengthLong();
            long done = 0;
            try (InputStream raw = conn.getInputStream();
                 BufferedInputStream in = new BufferedInputStream(raw, 64 * 1024);
                 OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (listener != null) listener.onProgress(done, total);
                }
            }
            if (total > 0 && done != total) {
                throw new IOException("short read for " + url + ": " + done + "/" + total);
            }
        } finally {
            conn.disconnect();
        }
    }

    // -- Hashing ------------------------------------------------------------

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("SHA-1 unavailable", e);
        }
    }

    /** True when [file] exists, has the expected size (when known) and SHA-1. */
    public static boolean matches(Path file, String expectedSha1, long expectedSize) {
        try {
            if (!Files.isRegularFile(file)) return false;
            if (expectedSize > 0 && Files.size(file) != expectedSize) return false;
            if (expectedSha1 == null || expectedSha1.isBlank()) return true;
            return sha1(file).equalsIgnoreCase(expectedSha1);
        } catch (Exception e) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
