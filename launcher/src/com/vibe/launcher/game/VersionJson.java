package com.vibe.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One version descriptor, already merged with its parent (`inheritsFrom`).
 *
 * Fabric profiles are tiny: they only carry the loader libraries, the main
 * class and `inheritsFrom: "1.21.1"`. [resolve] merges the child over the
 * parent so the rest of the launcher never has to think about inheritance.
 */
public final class VersionJson {

    public String id;
    public String mainClass;
    /** Null when the version only borrows assets from its parent. */
    public String assetIndexId;
    public String assetIndexUrl;
    public String assetIndexSha1;
    public long assetIndexSize;
    public String assets;
    public String type;
    public int javaMajorVersion;
    public String javaComponent;

    /** Allowed placeholders for the game's `--versionType` string. */
    public final List<Library> libraries = new ArrayList<>();
    public final Map<String, String> substitutions = new LinkedHashMap<>();
    public final List<Argument> gameArguments = new ArrayList<>();
    public final List<Argument> jvmArguments = new ArrayList<>();

    public JsonObject clientDownload;
    public JsonObject loggingClient;

    /** A single command-line argument, possibly gated by rules. */
    public static final class Argument {
        public final String value;
        public final JsonArray rules;

        Argument(String value, JsonArray rules) {
            this.value = value;
            this.rules = rules;
        }

        public boolean applies(Map<String, Boolean> features) {
            return RuleSet.allows(rules, features);
        }
    }

    // -- Parsing ------------------------------------------------------------

    public static VersionJson parse(JsonObject json) {
        VersionJson version = new VersionJson();
        version.id = RuleSet.optString(json, "id", "unknown");
        version.mainClass = RuleSet.optString(json, "mainClass", null);
        version.type = RuleSet.optString(json, "type", "release");
        version.assets = RuleSet.optString(json, "assets", null);

        JsonObject assetIndex = json.getAsJsonObject("assetIndex");
        if (assetIndex != null) {
            version.assetIndexId = RuleSet.optString(assetIndex, "id", null);
            version.assetIndexUrl = RuleSet.optString(assetIndex, "url", null);
            version.assetIndexSha1 = RuleSet.optString(assetIndex, "sha1", null);
            version.assetIndexSize = optLong(assetIndex, "size");
        }

        JsonObject javaVersion = json.getAsJsonObject("javaVersion");
        if (javaVersion != null) {
            version.javaMajorVersion = (int) optLong(javaVersion, "majorVersion");
            version.javaComponent = RuleSet.optString(javaVersion, "component", null);
        }

        JsonObject downloads = json.getAsJsonObject("downloads");
        if (downloads != null && downloads.has("client") && downloads.get("client").isJsonObject()) {
            version.clientDownload = downloads.getAsJsonObject("client");
        }

        JsonObject logging = json.getAsJsonObject("logging");
        if (logging != null && logging.has("client") && logging.get("client").isJsonObject()) {
            version.loggingClient = logging.getAsJsonObject("client");
        }

        if (json.has("libraries") && json.get("libraries").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("libraries")) {
                if (!element.isJsonObject()) continue;
                version.libraries.add(Library.parse(element.getAsJsonObject()));
            }
        }

        readArguments(json.get("minecraftArguments"), json.get("arguments"), version);
        readSubstitutions(json, version);
        return version;
    }

    /** Supports both the legacy string list and the modern object list. */
    private static void readArguments(JsonElement legacy, JsonElement modern, VersionJson version) {
        if (legacy != null && legacy.isJsonPrimitive()) {
            for (String token : legacy.getAsString().split(" ")) {
                if (token.isBlank()) continue;
                version.gameArguments.add(new Argument(token, null));
            }
        }

        if (modern == null || !modern.isJsonObject()) return;
        JsonObject arguments = modern.getAsJsonObject();
        readArgumentArray(arguments.get("game"), version.gameArguments);
        readArgumentArray(arguments.get("jvm"), version.jvmArguments);
    }

    private static void readArgumentArray(JsonElement element, List<Argument> target) {
        if (element == null || !element.isJsonArray()) return;
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) {
                target.add(new Argument(entry.getAsString(), null));
            } else if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                String value = RuleSet.optString(object, "value", null);
                JsonArray rules = object.has("rules") && object.get("rules").isJsonArray()
                    ? object.getAsJsonArray("rules") : null;
                if (value != null) {
                    target.add(new Argument(value, rules));
                } else if (object.has("value") && object.get("value").isJsonArray()) {
                    for (JsonElement valueEntry : object.getAsJsonArray("value")) {
                        try {
                            target.add(new Argument(valueEntry.getAsString(), rules));
                        } catch (Exception ignored) {
                            // Skip malformed entries rather than fail the launch.
                        }
                    }
                }
            }
        }
    }

    /**
     * `substitutions` is not used by vanilla, but some third-party loaders add
     * it; honouring it costs nothing and avoids a crash on their profiles.
     */
    private static void readSubstitutions(JsonObject json, VersionJson version) {
        JsonObject subs = json.getAsJsonObject("substitutions");
        if (subs == null) return;
        for (String key : subs.keySet()) {
            try {
                JsonObject entry = subs.getAsJsonObject(key);
                String value = entry != null
                    ? RuleSet.optString(entry, "value", null)
                    : subs.get(key).getAsString();
                if (value != null) version.substitutions.put(key, value);
            } catch (Exception ignored) {
                // Best effort only.
            }
        }
    }

    static long optLong(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) return 0L;
            return element.getAsLong();
        } catch (Exception e) {
            return 0L;
        }
    }
}
