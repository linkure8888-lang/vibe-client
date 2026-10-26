package com.vibe.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vibe.launcher.net.Http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Finds (or fetches) a Java runtime that can run the selected Minecraft version.
 *
 * Order of preference:
 *   1. a runtime shipped next to the app (`runtime/`), which the installer can
 *      bundle later;
 *   2. JAVA_HOME;
 *   3. `java` on PATH;
 *   4. well-known JDK install folders;
 *   5. Mojang's own JRE, downloaded into the game directory.
 *
 * Every candidate is actually executed with `-version`, so we never hand the
 * game a broken path.
 */
public final class JavaRuntime {

    /** Mojang's per-platform Java runtime index. */
    private static final String RUNTIME_INDEX =
        "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8c7c39df7210883d12871/all.json";

    /** A usable runtime. */
    public static final class Found {
        public final Path executable;
        public final int majorVersion;
        public final String description;

        Found(Path executable, int majorVersion, String description) {
            this.executable = executable;
            this.majorVersion = majorVersion;
            this.description = description;
        }
    }

    private JavaRuntime() {
    }

    /**
     * Returns a runtime able to run a version needing [requiredMajor] or newer,
     * downloading Mojang's JRE when nothing local is good enough.
     */
    public static Found find(Path gameDir, Path appDir, int requiredMajor,
                             String component, VersionInstaller.Listener listener)
        throws IOException {
        int need = Math.max(requiredMajor, 8);

        for (Path candidate : localCandidates(appDir)) {
            Found found = probe(candidate);
            if (found != null && found.majorVersion >= need) return found;
        }

        if (listener != null) listener.status("Downloading Java runtime (" + need + "+)...");
        Path downloaded = downloadRuntime(gameDir, component, listener);
        Found found = probe(downloaded);
        if (found == null) {
            throw new IOException("downloaded Java runtime is not runnable: " + downloaded);
        }
        return found;
    }

    /** Every plausible local `java` executable, cheapest check first. */
    private static List<Path> localCandidates(Path appDir) {
        List<Path> candidates = new ArrayList<>();
        String exe = isWindows() ? "java.exe" : "java";

        if (appDir != null) {
            candidates.add(appDir.resolve("runtime").resolve("bin").resolve(exe));
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(Path.of(javaHome, "bin", exe));
        }

        // `java` from PATH: resolve it through where/which once.
        try {
            Process process = new ProcessBuilder(isWindows() ? "where" : "which", "java")
                .redirectErrorStream(true).start();
            if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
                String out = new String(process.getInputStream().readAllBytes()).trim();
                for (String line : out.split("\\R")) {
                    if (!line.isBlank()) candidates.add(Path.of(line.trim()));
                }
            }
        } catch (Exception ignored) {
            // No PATH java: keep going.
        }

        if (isWindows()) {
            for (String root : new String[]{"C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft"}) {
                Path dir = Path.of(root);
                if (!Files.isDirectory(dir)) continue;
                try (var stream = Files.list(dir)) {
                    stream.filter(Files::isDirectory)
                        .forEach(jdk -> candidates.add(jdk.resolve("bin").resolve(exe)));
                } catch (Exception ignored) {
                    // Unreadable folder: skip.
                }
            }
        } else {
            candidates.add(Path.of("/usr/lib/jvm"));
        }

        return candidates;
    }

    /** Runs `java -version` and parses the major version. */
    public static Found probe(Path executable) {
        try {
            if (executable == null || !Files.isRegularFile(executable)) return null;
            Process process = new ProcessBuilder(executable.toString(), "-version")
                .redirectErrorStream(true).start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes());
            int major = parseMajorVersion(output);
            if (major <= 0) return null;
            return new Found(executable, major, executable + " (Java " + major + ")");
        } catch (Exception e) {
            return null;
        }
    }

    /** Parses both `"1.8.0_402"` and `"21.0.1"` style banners. */
    static int parseMajorVersion(String output) {
        if (output == null) return -1;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("version \"(\\d+)(?:\\.(\\d+))?")
            .matcher(output);
        if (!matcher.find()) return -1;
        int first = Integer.parseInt(matcher.group(1));
        if (first == 1 && matcher.group(2) != null) {
            return Integer.parseInt(matcher.group(2));  // 1.8 -> 8
        }
        return first;
    }

    // -- Mojang runtime download -------------------------------------------

    private static Path downloadRuntime(Path gameDir, String component,
                                        VersionInstaller.Listener listener) throws IOException {
        String osKey = switch (RuleSet.osName()) {
            case "windows" -> "windows-x64";
            case "osx" -> RuleSet.osArch().equals("x86_64") ? "mac-os-x64" : "mac-os-arm64";
            default -> "linux";
        };

        JsonObject index = Http.getJson(RUNTIME_INDEX);
        JsonObject platform = index.getAsJsonObject(osKey);
        if (platform == null) throw new IOException("no Java runtime offered for " + osKey);

        // Prefer the component the version asks for, else any java-runtime-*.
        JsonObject chosen = null;
        if (component != null && platform.has(component) && platform.get(component).isJsonArray()
            && platform.getAsJsonArray(component).size() > 0) {
            chosen = platform.getAsJsonArray(component).get(0).getAsJsonObject();
        }
        if (chosen == null) {
            for (String key : platform.keySet()) {
                if (!key.startsWith("java-runtime")) continue;
                JsonArray array = platform.getAsJsonArray(key);
                if (array != null && array.size() > 0) {
                    chosen = array.get(0).getAsJsonObject();
                    break;
                }
            }
        }
        if (chosen == null) throw new IOException("no Java runtime entry found for " + osKey);

        JsonObject manifest = chosen.getAsJsonObject("manifest");
        String manifestUrl = manifest != null ? RuleSet.optString(manifest, "url", null) : null;
        if (manifestUrl == null) throw new IOException("Java runtime manifest has no URL");

        JsonObject files = Http.getJson(manifestUrl).getAsJsonObject("files");
        if (files == null) throw new IOException("Java runtime manifest has no files");

        Path target = gameDir.resolve("runtime").resolve("java");
        Files.createDirectories(target);

        int total = files.size();
        int done = 0;
        for (String name : files.keySet()) {
            done++;
            JsonElement element = files.get(name);
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            String type = RuleSet.optString(file, "type", "file");
            Path out = target.resolve(name).normalize();
            if (!out.startsWith(target)) continue;

            if ("directory".equals(type)) {
                Files.createDirectories(out);
                continue;
            }
            if (!"file".equals(type)) continue;  // links are Windows-only

            JsonObject downloads = file.getAsJsonObject("downloads");
            JsonObject raw = downloads != null ? downloads.getAsJsonObject("raw") : null;
            if (raw == null) continue;
            String url = RuleSet.optString(raw, "url", null);
            String sha1 = RuleSet.optString(raw, "sha1", null);
            long size = VersionJson.optLong(raw, "size");
            if (url == null) continue;
            if (Http.matches(out, sha1, size)) continue;

            if (listener != null && done % 20 == 0) {
                listener.status("Downloading Java runtime (" + done + "/" + total + ")");
            }
            Http.download(url, out, sha1, null);
        }

        Path executable = target.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        if (!Files.isRegularFile(executable)) {
            // Some manifests name the folder differently; search for it once.
            try (var stream = Files.walk(target, 3)) {
                executable = stream
                    .filter(path -> path.getFileName().toString().equals(isWindows() ? "java.exe" : "java"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Java runtime has no java executable"));
            }
        }
        markExecutable(executable);
        return executable;
    }

    private static void markExecutable(Path executable) {
        if (isWindows()) return;
        try {
            executable.toFile().setExecutable(true);
        } catch (Exception ignored) {
            // Non-fatal: the launcher will report a clearer error on start.
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
