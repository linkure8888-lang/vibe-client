package com.vibe.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One entry of a version's `libraries` array. */
public final class Library {

    /** A single downloadable file. */
    public static final class Artifact {
        public final String path;
        public final String url;
        public final String sha1;
        public final long size;

        Artifact(String path, String url, String sha1, long size) {
            this.path = path;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
        }

        public boolean isUsable() {
            return url != null && !url.isBlank();
        }
    }

    /** group:artifact:version[:classifier][@ext] */
    public final String name;
    /** The plain jar, null for natives-only entries. */
    public final Artifact artifact;
    /** classifier -> native jar, in the order declared. */
    public final Map<String, Artifact> natives;
    /** Extra "-Djava.library.path" style directories to exclude when extracting. */
    public final List<String> extractExclude;
    public final JsonArray rules;

    private Library(String name, Artifact artifact, Map<String, Artifact> natives,
                    List<String> extractExclude, JsonArray rules) {
        this.name = name;
        this.artifact = artifact;
        this.natives = natives;
        this.extractExclude = extractExclude;
        this.rules = rules;
    }

    /** Path inside `libraries/` derived from the Maven coordinates. */
    public static String pathFromName(String name) {
        String cleaned = name;
        String ext = "jar";
        int at = cleaned.indexOf('@');
        if (at >= 0) {
            ext = cleaned.substring(at + 1);
            cleaned = cleaned.substring(0, at);
        }
        String[] parts = cleaned.split(":");
        if (parts.length < 3) return cleaned.replace(':', '/') + "." + ext;

        String group = parts[0].replace('.', '/');
        String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifactId + "/" + version + "/"
            + artifactId + "-" + version + classifier + "." + ext;
    }

    public static Library parse(JsonObject json) {
        String name = RuleSet.optString(json, "name", "");
        JsonArray rules = json.has("rules") && json.get("rules").isJsonArray()
            ? json.getAsJsonArray("rules") : null;

        JsonObject downloads = json.getAsJsonObject("downloads");
        Artifact artifact = null;
        Map<String, Artifact> natives = new LinkedHashMap<>();
        if (downloads != null) {
            JsonObject artifactJson = downloads.getAsJsonObject("artifact");
            if (artifactJson != null) {
                artifact = parseArtifact(artifactJson, pathFromName(name));
            }
            JsonObject classifiers = downloads.getAsJsonObject("classifiers");
            if (classifiers != null) {
                for (String key : classifiers.keySet()) {
                    JsonElement value = classifiers.get(key);
                    if (!value.isJsonObject()) continue;
                    JsonObject classifierJson = value.getAsJsonObject();
                    String path = RuleSet.optString(classifierJson, "path", null);
                    Artifact parsed = parseArtifact(classifierJson,
                        path != null ? path : pathFromName(name + ":" + key));
                    if (parsed != null) natives.put(key, parsed);
                }
            }
        }

        // Fabric (and some other loaders) list a Maven base URL instead of
        // pre-computed download objects: { "name": "...", "url": "https://maven..." }
        // Without this branch the loader jars are never downloaded and the
        // classpath is missing Fabric entirely.
        if (artifact == null && !name.isBlank()) {
            String base = RuleSet.optString(json, "url", null);
            if (base != null && !base.isBlank()) {
                String path = pathFromName(name);
                String normalized = base.endsWith("/") ? base : base + "/";
                artifact = new Artifact(path, normalized + path, null, 0);
            }
        }

        List<String> exclude = new ArrayList<>();
        JsonObject extract = json.getAsJsonObject("extract");
        if (extract != null && extract.has("exclude") && extract.get("exclude").isJsonArray()) {
            for (JsonElement element : extract.getAsJsonArray("exclude")) {
                try {
                    exclude.add(element.getAsString());
                } catch (Exception ignored) {
                    // Skip malformed entries.
                }
            }
        }

        return new Library(name, artifact, natives, exclude, rules);
    }

    private static Artifact parseArtifact(JsonObject json, String fallbackPath) {
        String url = RuleSet.optString(json, "url", null);
        String sha1 = RuleSet.optString(json, "sha1", null);
        String path = RuleSet.optString(json, "path", fallbackPath);
        long size = 0;
        try {
            JsonElement sizeElement = json.get("size");
            if (sizeElement != null && !sizeElement.isJsonNull()) size = sizeElement.getAsLong();
        } catch (Exception ignored) {
            // Size is only a fast-path check; SHA-1 is the real guard.
        }
        return new Artifact(path, url, sha1, size);
    }

    /**
     * True when this entry IS a native jar for the running platform.
     *
     * Modern Mojang metadata (1.19+) no longer uses `downloads.classifiers`.
     * Instead each native is its own library whose artifact name contains the
     * platform, e.g. `org.lwjgl:lwjgl:3.3.3:natives-windows`. Those jars must
     * be extracted into the natives folder; treating them as plain classpath
     * entries leaves `java.library.path` empty and the game dies on startup.
     */
    /**
     * True for ANY native jar, whatever the platform. Those must never be on
     * the classpath; [isNativeForCurrentOs] decides which ones to extract.
     */
    public boolean isNativeJar() {
        return name != null && name.contains(":natives-");
    }

    public boolean isNativeForCurrentOs() {
        if (name == null || !name.contains(":natives-")) return false;
        String os = RuleSet.osName();
        String arch = RuleSet.osArch();

        // Reject natives for other platforms.
        if (os.equals("windows") && !name.contains("natives-windows")) return false;
        if (os.equals("osx") && !name.contains("natives-macos")) return false;
        if (os.equals("linux") && !name.contains("natives-linux")) return false;

        // Reject other architectures when the entry names one.
        if (name.contains("natives-windows-arm64")) return arch.equals("arm64");
        if (name.contains("natives-macos-arm64")) return arch.equals("arm64");
        if (name.contains("natives-linux-arm64")) return arch.equals("arm64");
        if (name.contains("-x86") && !name.contains("x86_64")) return arch.equals("x86");
        return true;
    }

    /**
     * Picks the natives jar for the current platform, or null.
     * Handles both the modern `natives-windows` naming and the legacy
     * `natives-windows-64` classifiers.
     */
    public Artifact nativesForCurrentOs() {
        if (natives.isEmpty()) return null;
        String os = RuleSet.osName();
        String arch = RuleSet.osArch();
        String[] candidates = os.equals("windows")
            ? new String[]{"natives-windows-" + arch, "natives-windows"}
            : os.equals("osx")
                ? new String[]{"natives-macos-" + arch, "natives-macos"}
                : new String[]{"natives-linux-" + arch, "natives-linux"};
        for (String candidate : candidates) {
            Artifact found = natives.get(candidate);
            if (found != null) return found;
        }
        // Fall back to any classifier that mentions our OS.
        for (Map.Entry<String, Artifact> entry : natives.entrySet()) {
            if (entry.getKey().contains(os)) return entry.getValue();
        }
        return null;
    }
}
