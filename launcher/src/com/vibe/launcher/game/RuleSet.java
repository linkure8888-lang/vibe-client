package com.vibe.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates Mojang "rules" arrays (os / arch / features).
 *
 * Semantics copied from the official launcher: no rules means "allowed";
 * otherwise we start from "disallowed" and walk the list in order, so the last
 * matching rule wins. That ordering matters — several libraries rely on a
 * trailing disallow to exclude macOS/Linux.
 */
public final class RuleSet {

    private RuleSet() {
    }

    public static boolean allows(JsonArray rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (!matches(rule, features)) continue;
            String action = optString(rule, "action", "allow");
            allowed = "allow".equals(action);
        }
        return allowed;
    }

    private static boolean matches(JsonObject rule, Map<String, Boolean> features) {
        JsonObject os = rule.getAsJsonObject("os");
        if (os != null) {
            String name = optString(os, "name", null);
            if (name != null && !name.equalsIgnoreCase(osName())) return false;

            String arch = optString(os, "arch", null);
            if (arch != null && !arch.equalsIgnoreCase(osArch())) return false;

            String version = optString(os, "version", null);
            if (version != null) {
                try {
                    String actual = System.getProperty("os.version", "");
                    if (!Pattern.compile(version).matcher(actual).find()) return false;
                } catch (Exception e) {
                    return false;
                }
            }
        }

        JsonObject wanted = rule.getAsJsonObject("features");
        if (wanted != null) {
            for (String key : wanted.keySet()) {
                boolean want = wanted.get(key).getAsBoolean();
                boolean have = features != null && Boolean.TRUE.equals(features.get(key));
                if (want != have) return false;
            }
        }
        return true;
    }

    /** Mojang's os name for the running machine. */
    public static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    /**
     * Mojang's arch name: "arm64", "x86_64" or "x86".
     * ARM must be detected before the generic "64" check, otherwise a Windows
     * on ARM machine would be handed x86_64 natives and fail to start.
     */
    public static String osArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        return arch.contains("64") ? "x86_64" : "x86";
    }

    static String optString(JsonObject json, String key, String fallback) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) return fallback;
            return element.getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }
}
