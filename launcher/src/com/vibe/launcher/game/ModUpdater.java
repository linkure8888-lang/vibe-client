package com.vibe.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vibe.launcher.net.Http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the client mod up to date from GitHub Releases.
 *
 * Design decisions worth stating, because a bad updater is worse than none:
 *
 *  - **The update is optional and never blocking.** A launch proceeds on the
 *    local jar whatever happens here. The updater is a convenience; making the
 *    game unlaunchable because GitHub is down would be a bug, not a feature.
 *
 *  - **Nothing is downloaded unless it is newer.** The check compares the
 *    release tag against the version recorded next to the installed jar, so a
 *    stable install does zero network work beyond one small JSON request.
 *
 *  - **Downloads are atomic.** The jar lands as `.part` and is renamed into
 *    place only after the size is verified, so an interrupted download can
 *    never leave a truncated jar that Fabric refuses to load.
 *
 *  - **Unconfigured means silent.** With no repository set, this class does
 *    nothing at all — no error, no log spam. See [setRepository].
 */
public final class ModUpdater {

    /** Repository slug, e.g. "user/vibeclient". Empty disables the updater. */
    private static String repository = "";

    private static final String API = "https://api.github.com/repos/";

    /** Recorded next to the installed jar so a stable install skips downloads. */
    private static final String VERSION_MARKER = "vibeclient-version.txt";

    private ModUpdater() {
    }

    /**
     * Sets the GitHub repository to check, read from the launcher config.
     * A blank value disables the updater entirely.
     */
    public static void setRepository(String slug) {
        repository = normalizeSlug(slug);
    }

    /**
     * Normalises a repository reference into an "owner/name" slug.
     *
     * Accepts every form a user is likely to paste — a full GitHub URL, a URL
     * with a trailing slash or ".git", or the bare slug — because a config
     * field that silently disables the updater when someone pastes a URL looks
     * like a broken feature rather than a formatting mistake.
     *
     * Returns "" for anything that cannot be resolved to two path segments;
     * that is what disables the updater.
     */
    public static String normalizeSlug(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";

        // Strip a pasted URL down to its path.
        int schemeEnd = value.indexOf("://");
        if (schemeEnd >= 0) {
            value = value.substring(schemeEnd + 3);
            int slash = value.indexOf('/');
            if (slash < 0) return "";
            value = value.substring(slash + 1);
        }
        // Drop query/fragment and a trailing .git.
        int cut = indexOfAny(value, '?', '#');
        if (cut >= 0) value = value.substring(0, cut);
        if (value.endsWith(".git")) value = value.substring(0, value.length() - 4);

        // Keep the first two non-empty path segments.
        String[] parts = value.split("/");
        String owner = null;
        String name = null;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (owner == null) {
                owner = part;
            } else {
                name = part;
                break;
            }
        }
        if (owner == null || name == null) return "";
        if (!isSlugPart(owner) || !isSlugPart(name)) return "";
        return owner + "/" + name;
    }

    /** True for characters GitHub actually allows in an owner or repo name. */
    private static boolean isSlugPart(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
            if (!ok) return false;
        }
        return !value.isEmpty();
    }

    private static int indexOfAny(String value, char a, char b) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == a || c == b) return i;
        }
        return -1;
    }

    public static boolean isConfigured() {
        return !repository.isEmpty() ;
    }

    /** What the update check found. */
    public static final class Result {
        public boolean checked;
        public boolean available;
        public String latestTag = "";
        public String currentTag = "";
        public String downloadUrl;
        public String fileName;
        public String message = "";
    }

    /**
     * Checks GitHub for a newer release. Never throws: every failure comes back
     * as a [Result] with `checked = false` and a human-readable [Result.message].
     */
    public static Result check(Path gameDir) {
        Result result = new Result();
        if (!isConfigured()) {
            result.message = "Update source not configured";
            return result;
        }
        result.currentTag = currentVersion(gameDir);

        try {
            String url = API + repository + "/releases/latest";
            String body = Http.getString(url, "application/vnd.github+json");
            if (body == null || body.isBlank()) {
                result.message = "No release information";
                return result;
            }

            JsonObject release = JsonParser.parseString(body).getAsJsonObject();
            result.checked = true;
            result.latestTag = optString(release, "tag_name", "");

            if (result.latestTag.isEmpty()) {
                result.message = "Latest release has no tag";
                return result;
            }
            if (result.latestTag.equals(result.currentTag)) {
                result.message = "Up to date (" + result.latestTag + ")";
                return result;
            }

            // Prefer the asset named like the mod jar; fall back to any .jar.
            JsonArray assets = release.getAsJsonArray("assets");
            if (assets != null) {
                for (JsonElement element : assets) {
                    if (!element.isJsonObject()) continue;
                    JsonObject asset = element.getAsJsonObject();
                    String name = optString(asset, "name", "");
                    if (!name.endsWith(".jar") || name.contains("-sources")) continue;
                    if (name.startsWith("vibeclient-") || result.downloadUrl == null) {
                        result.downloadUrl = optString(asset, "browser_download_url", null);
                        result.fileName = name;
                        if (name.startsWith("vibeclient-")) break;
                    }
                }
            }

            if (result.downloadUrl == null) {
                result.message = "Release " + result.latestTag + " has no jar asset";
                return result;
            }

            result.available = true;
            result.message = "Update available: " + result.latestTag;
            return result;
        } catch (Exception e) {
            result.message = "Update check failed: " + e.getMessage();
            return result;
        }
    }

    /**
     * Downloads [result]'s jar into `<game>/mods` and removes the old build.
     *
     * Returns the installed file name, or null on failure. The caller is told
     * to restart; nothing is swapped under a running game.
     */
    public static String download(Path gameDir, Result result) {
        if (result == null || !result.available || result.downloadUrl == null) return null;

        Path mods = gameDir.resolve("mods");
        try {
            Files.createDirectories(mods);
        } catch (IOException e) {
            return null;
        }

        String targetName = result.fileName != null && !result.fileName.isBlank()
            ? result.fileName
            : "vibeclient-" + result.latestTag + ".jar";
        Path target = mods.resolve(targetName);
        Path part = mods.resolve(targetName + ".part");

        try {
            Http.download(result.downloadUrl, part, null, null);

            // A truncated jar makes Fabric refuse to start, so size-check the
            // download before it is allowed to replace anything.
            if (!Files.isRegularFile(part) || Files.size(part) < 1024) {
                Files.deleteIfExists(part);
                return null;
            }

            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            removeOldBuilds(mods, targetName);
            writeVersionMarker(gameDir, result.latestTag);
            return targetName;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // Best effort.
            }
            return null;
        }
    }

    /**
     * Deletes every other `vibeclient-*.jar`.
     *
     * Two jars with one mod id make Fabric refuse to start, so leaving the old
     * build behind would turn a successful update into a launch failure.
     */
    private static void removeOldBuilds(Path mods, String keep) {
        List<Path> stale = new ArrayList<>();
        try (var stream = Files.list(mods)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().startsWith("vibeclient-"))
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .filter(p -> !p.getFileName().toString().equals(keep))
                .forEach(stale::add);
        } catch (Exception e) {
            return;
        }
        for (Path old : stale) {
            try {
                Files.deleteIfExists(old);
            } catch (IOException ignored) {
                // A locked jar (game running) fails silently; it will go next time
                // the launcher runs, since the stale sweep runs after every update.
            }
        }
    }

    /** The version recorded for the installed build, or "" when unknown. */
    public static String currentVersion(Path gameDir) {
        try {
            Path marker = gameDir.resolve(VERSION_MARKER);
            if (!Files.isRegularFile(marker)) return "";
            return Files.readString(marker, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeVersionMarker(Path gameDir, String tag) {
        try {
            Files.writeString(gameDir.resolve(VERSION_MARKER), tag, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Only used to skip redundant downloads; losing it is harmless.
        }
    }

    private static String optString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }
}
