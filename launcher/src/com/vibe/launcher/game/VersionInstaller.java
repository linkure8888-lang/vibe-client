package com.vibe.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vibe.launcher.net.Http;
import com.vibe.launcher.net.ParallelDownloader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs a Minecraft version into a game directory: the client jar, every
 * allowed library, the natives, the asset index and all asset objects.
 *
 * Design points:
 *  - Idempotent: every file is checked by SHA-1 before it is downloaded, so a
 *    second run is fast and a corrupted file repairs itself.
 *  - Never trusts the network: all JSON is parsed defensively and a missing
 *    optional field degrades instead of throwing.
 *  - Fabric-aware: a loader profile only lists its own libraries and points at
 *    a parent via `inheritsFrom`; [resolve] merges the two.
 */
public final class VersionInstaller {

    public static final String VERSION_MANIFEST =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String RESOURCES =
        "https://resources.download.minecraft.net/";
    public static final String LIBRARIES =
        "https://libraries.minecraft.net/";

    private static final String LAUNCHER_NAME = "vibeclient";
    private static final String LAUNCHER_VERSION = "1.0";

    /** Everything the download phase reports back to the UI. */
    public interface Listener {
        void status(String text);

        void progress(long done, long total);
    }

    /** A resolved version: parent merged into child, ready to download. */
    public static final class Resolved {
        public final String id;
        public final VersionJson version;
        /** Ordered parent chain, child first. */
        public final List<VersionJson> chain;

        Resolved(String id, VersionJson version, List<VersionJson> chain) {
            this.id = id;
            this.version = version;
            this.chain = chain;
        }

        public String mainClass() {
            for (VersionJson entry : chain) {
                if (entry.mainClass != null && !entry.mainClass.isBlank()) return entry.mainClass;
            }
            return "net.minecraft.client.main.Main";
        }

        public String assetIndexId() {
            for (VersionJson entry : chain) {
                if (entry.assetIndexId != null) return entry.assetIndexId;
            }
            return null;
        }
    }

    private VersionInstaller() {
    }

    // -- Version manifest ---------------------------------------------------

    /** Fetches the list of every version Mojang offers, newest first. */
    public static List<VersionEntry> listVersions() throws IOException {
        JsonObject manifest = Http.getJson(VERSION_MANIFEST);
        JsonArray versions = manifest.getAsJsonArray("versions");
        List<VersionEntry> result = new ArrayList<>();
        if (versions == null) return result;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject json = element.getAsJsonObject();
            String id = RuleSet.optString(json, "id", null);
            String url = RuleSet.optString(json, "url", null);
            if (id == null || url == null) continue;
            result.add(new VersionEntry(
                id,
                RuleSet.optString(json, "type", "release"),
                url,
                RuleSet.optString(json, "sha1", null),
                RuleSet.optString(json, "releaseTime", null)
            ));
        }
        return result;
    }

    /** One line of `version_manifest_v2.json`. */
    public static final class VersionEntry {
        public final String id;
        public final String type;
        public final String url;
        public final String sha1;
        public final String releaseTime;

        VersionEntry(String id, String type, String url, String sha1, String releaseTime) {
            this.id = id;
            this.type = type;
            this.url = url;
            this.sha1 = sha1;
            this.releaseTime = releaseTime;
        }

        public boolean isRelease() {
            return "release".equals(type);
        }
    }

    // -- Resolution ---------------------------------------------------------

    /**
     * Loads `<game>/versions/<id>/<id>.json` and merges its parent chain.
     * The version JSON is downloaded first when it is missing.
     */
    public static Resolved resolve(Path gameDir, String id, String jsonUrl,
                                   Listener listener) throws IOException {
        List<VersionJson> chain = new ArrayList<>();
        String currentId = id;
        String currentUrl = jsonUrl;

        for (int depth = 0; depth < 5 && currentId != null; depth++) {
            Path file = versionJsonFile(gameDir, currentId);
            if (!Files.isRegularFile(file)) {
                if (currentUrl == null) {
                    throw new IOException("no local JSON for version " + currentId
                        + " and no download URL");
                }
                if (listener != null) listener.status("Downloading version JSON: " + currentId);
                Http.download(currentUrl, file, null, null);
            }

            JsonObject json = Http.readJsonFile(file);
            VersionJson parsed = VersionJson.parse(json);
            chain.add(parsed);

            String parent = RuleSet.optString(json, "inheritsFrom", null);
            if (parent == null || parent.isBlank()) break;
            currentId = parent;
            currentUrl = null;  // parents must already be installed
        }

        if (chain.isEmpty()) throw new IOException("could not resolve version " + id);
        VersionJson merged = merge(chain);
        return new Resolved(id, merged, chain);
    }

    /** Child fields win; libraries are appended parent-last. */
    private static VersionJson merge(List<VersionJson> chain) {
        VersionJson merged = chain.get(0);
        List<Library> libraries = new ArrayList<>();
        List<VersionJson.Argument> gameArgs = new ArrayList<>();
        List<VersionJson.Argument> jvmArgs = new ArrayList<>();

        for (VersionJson entry : chain) {
            for (Library library : entry.libraries) {
                if (!containsLibrary(libraries, library.name)) libraries.add(library);
            }
            gameArgs.addAll(entry.gameArguments);
            jvmArgs.addAll(entry.jvmArguments);
        }

        merged.gameArguments.clear();
        merged.gameArguments.addAll(gameArgs);
        merged.jvmArguments.clear();
        merged.jvmArguments.addAll(jvmArgs);
        merged.libraries.clear();
        merged.libraries.addAll(libraries);
        return merged;
    }

    private static boolean containsLibrary(List<Library> libraries, String name) {
        for (Library library : libraries) {
            if (library.name.equals(name)) return true;
        }
        return false;
    }

    public static Path versionJsonFile(Path gameDir, String id) {
        return gameDir.resolve("versions").resolve(id).resolve(id + ".json");
    }

    public static Path versionJarFile(Path gameDir, String id) {
        return gameDir.resolve("versions").resolve(id).resolve(id + ".jar");
    }

    // -- Installation -------------------------------------------------------

    /**
     * Downloads everything a launch needs. Safe to call on an installed game:
     * existing, hash-valid files are skipped.
     */
    public static void install(Path gameDir, Resolved resolved, Listener listener)
        throws IOException {
        Path librariesDir = gameDir.resolve("libraries");
        Files.createDirectories(librariesDir);

        Map<String, Boolean> features = Map.of("is_demo_user", false, "has_custom_resolution", false);

        // Collect what must be downloaded, then do it with one progress bar.
        List<Download> downloads = new ArrayList<>();

        // 1. Client jar (only the vanilla parent has one).
        String jarVersion = resolved.id;
        for (VersionJson entry : resolved.chain) {
            if (entry.clientDownload != null) {
                String url = RuleSet.optString(entry.clientDownload, "url", null);
                String sha1 = RuleSet.optString(entry.clientDownload, "sha1", null);
                long size = VersionJson.optLong(entry.clientDownload, "size");
                if (url != null) {
                    downloads.add(new Download(url, versionJarFile(gameDir, entry.id), sha1, size));
                    jarVersion = entry.id;
                }
                break;
            }
        }

        // 2. Libraries + natives.
        Path nativesDir = gameDir.resolve("versions").resolve(resolved.id).resolve("natives");
        Files.createDirectories(nativesDir);
        List<Path> nativeJars = new ArrayList<>();

        for (Library library : resolved.version.libraries) {
            if (!RuleSet.allows(library.rules, features)) continue;

            Library.Artifact artifact = library.artifact;
            if (artifact != null && artifact.isUsable()) {
                Path target = librariesDir.resolve(artifact.path);
                downloads.add(new Download(artifact.url, target, artifact.sha1, artifact.size));
                // Modern metadata marks natives by name: such a jar is extracted
                // rather than added to the classpath.
                if (library.isNativeForCurrentOs()) nativeJars.add(target);
            }

            // Legacy metadata (<= 1.18) keeps natives in downloads.classifiers.
            Library.Artifact nativeJar = library.nativesForCurrentOs();
            if (nativeJar != null && nativeJar.isUsable()) {
                Path target = librariesDir.resolve(nativeJar.path);
                downloads.add(new Download(nativeJar.url, target, nativeJar.sha1, nativeJar.size));
                nativeJars.add(target);
            }
        }

        // 3. Asset index + objects.
        String indexId = resolved.assetIndexId();
        VersionJson indexOwner = null;
        for (VersionJson entry : resolved.chain) {
            if (entry.assetIndexId != null && entry.assetIndexUrl != null) {
                indexOwner = entry;
                break;
            }
        }
        if (indexOwner != null && indexId != null) {
            Path indexPath = gameDir.resolve("assets").resolve("indexes").resolve(indexId + ".json");
            if (!Http.matches(indexPath, indexOwner.assetIndexSha1, indexOwner.assetIndexSize)) {
                downloads.add(new Download(indexOwner.assetIndexUrl, indexPath,
                    indexOwner.assetIndexSha1, indexOwner.assetIndexSize));
            }
        }

        // Run the file downloads.
        //
        // Checking and downloading are split into two passes: the check is a
        // local hash and costs nothing, so everything already present is
        // reported immediately, and only the genuinely missing files are handed
        // to the parallel downloader.
        long totalFiles = downloads.size();
        long[] lastReport = {0L};
        List<ParallelDownloader.Item> missing = new ArrayList<>();
        long doneFiles = 0;

        for (Download download : downloads) {
            if (Http.matches(download.target, download.sha1, download.size)) {
                doneFiles++;
                report(listener, "Checking files", doneFiles, totalFiles, lastReport);
                continue;
            }
            missing.add(new ParallelDownloader.Item(
                download.url, download.target, download.sha1, download.size));
        }

        if (!missing.isEmpty()) {
            final long alreadyDone = doneFiles;
            ParallelDownloader.downloadAll(missing, (name, done, batchTotal) -> {
                if (listener == null) return;
                long overall = alreadyDone + done;
                listener.status("Downloading " + name + " (" + overall + "/" + totalFiles + ")");
                report(listener, "Downloading files", overall, totalFiles, lastReport);
            });
        }

        // Extract natives once everything is on disk.
        if (listener != null) listener.status("Extracting natives");
        for (Path nativeJar : nativeJars) {
            extractNatives(nativeJar, nativesDir);
        }

        // Asset objects: thousands of tiny files, so they get their own pass.
        if (indexId != null) {
            installAssets(gameDir, indexId, listener);
        }

        // Remember which jar this profile uses, so the launch step is trivial.
        writeProfileJarHint(gameDir, resolved.id, jarVersion);
    }

    private static void report(Listener listener, String label, long done, long total,
                               long[] lastReport) {
        if (listener == null) return;
        // Throttle: one UI update per file is plenty at these counts.
        listener.progress(done, total);
    }

    private static void installAssets(Path gameDir, String indexId, Listener listener)
        throws IOException {
        Path indexPath = gameDir.resolve("assets").resolve("indexes").resolve(indexId + ".json");
        if (!Files.isRegularFile(indexPath)) return;

        JsonObject index = Http.readJsonFile(indexPath);
        JsonObject objects = index.getAsJsonObject("objects");
        if (objects == null) return;

        Path objectsDir = gameDir.resolve("assets").resolve("objects");
        Files.createDirectories(objectsDir);

        List<Download> pending = new ArrayList<>();
        for (String key : objects.keySet()) {
            JsonElement element = objects.get(key);
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String hash = RuleSet.optString(object, "hash", null);
            long size = VersionJson.optLong(object, "size");
            if (hash == null || hash.length() < 2) continue;
            String prefix = hash.substring(0, 2);
            Path target = objectsDir.resolve(prefix).resolve(hash);
            if (Http.matches(target, hash, size)) continue;
            pending.add(new Download(RESOURCES + prefix + "/" + hash, target, hash, size));
        }

        long total = pending.size();
        if (total == 0) return;

        // Parallel, not sequential. These files are small, so the cost is
        // almost entirely per-request latency (~4300 round trips); running
        // eight at once is the difference between minutes and seconds.
        List<ParallelDownloader.Item> items = new ArrayList<>(pending.size());
        for (Download download : pending) {
            items.add(new ParallelDownloader.Item(
                download.url, download.target, download.sha1, download.size));
        }

        final long[] lastReport = {0L};
        ParallelDownloader.downloadAll(items, (name, done, totalFiles) -> {
            if (listener == null) return;
            listener.status("Downloading assets (" + done + "/" + totalFiles + ")");
            // Throttle the UI update: one repaint per file saturates the Swing
            // event queue and makes the window stutter.
            if (done % 25 == 0 || done == totalFiles) {
                report(listener, "Assets", done, totalFiles, lastReport);
            }
        });
    }

    /** Remembers the vanilla jar a loader profile inherits its classes from. */
    private static void writeProfileJarHint(Path gameDir, String profileId, String jarVersion)
        throws IOException {
        if (profileId.equals(jarVersion)) return;
        Path hint = gameDir.resolve("versions").resolve(profileId).resolve("jar-version.txt");
        Files.createDirectories(hint.getParent());
        Files.writeString(hint, jarVersion);
    }

    /** Reads the hint back; falls back to [profileId] when absent. */
    public static String jarVersionFor(Path gameDir, String profileId) {
        Path hint = gameDir.resolve("versions").resolve(profileId).resolve("jar-version.txt");
        try {
            if (Files.isRegularFile(hint)) {
                String text = Files.readString(hint).trim();
                if (!text.isEmpty()) return text;
            }
        } catch (Exception ignored) {
            // A missing hint is not an error; the caller falls back.
        }
        return profileId;
    }

    private static void extractNatives(Path jar, Path targetDir) throws IOException {
        if (!Files.isRegularFile(jar)) return;
        Files.createDirectories(targetDir);
        try (InputStream fileIn = Files.newInputStream(jar);
             ZipInputStream zip = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) continue;
                if (name.endsWith(".class") || name.endsWith(".java")) continue;
                if (name.startsWith("META-INF/")) continue;
                // Native jars keep their dlls under a path like
                // `windows/x64/org/lwjgl/lwjgl.dll`, but java.library.path only
                // looks at the folder itself. Flatten on extract so the dll is
                // where the JVM expects it.
                int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                if (slash >= 0) name = name.substring(slash + 1);
                if (name.isEmpty()) continue;

                Path out = targetDir.resolve(name).normalize();
                if (!out.startsWith(targetDir)) continue;
                Files.createDirectories(out.getParent());
                try (var stream = Files.newOutputStream(out)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        stream.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    // A dll still mapped by a running game cannot be overwritten.
                    // That is not a reason to abort the whole launch: the old
                    // file is by definition the same version, so keep it.
                    if (!Files.isRegularFile(out)) throw e;
                }
            }
        }
    }

    /** One file to download. */
    private static final class Download {
        final String url;
        final Path target;
        final String sha1;
        final long size;

        Download(String url, Path target, String sha1, long size) {
            this.url = url;
            this.target = target;
            this.sha1 = sha1;
            this.size = size;
        }
    }
}
