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

/**
 * Installs a Fabric loader profile for a vanilla version.
 *
 * Fabric publishes a complete version JSON per (game, loader) pair, so there is
 * no patching step like Forge needs: download the profile, drop it into
 * `versions/`, and the normal resolver handles `inheritsFrom` for us.
 */
public final class FabricInstaller {

    private static final String META = "https://meta.fabricmc.net/v2/versions/loader/";

    private FabricInstaller() {
    }

    /** A loader build offered for a game version. */
    public static final class LoaderVersion {
        public final String version;
        public final boolean stable;

        LoaderVersion(String version, boolean stable) {
            this.version = version;
            this.stable = stable;
        }
    }

    /** Every loader build Fabric offers for [gameVersion], newest first. */
    public static List<LoaderVersion> listLoaders(String gameVersion) throws IOException {
        JsonArray array = Http.getJsonArray(META + gameVersion);
        List<LoaderVersion> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            JsonObject loader = entry.getAsJsonObject("loader");
            if (loader == null) continue;
            String version = RuleSet.optString(loader, "version", null);
            if (version == null) continue;
            boolean stable = "true".equalsIgnoreCase(RuleSet.optString(loader, "stable", "true"));
            result.add(new LoaderVersion(version, stable));
        }
        return result;
    }

    /**
     * Downloads and writes the loader profile; returns the profile id that can
     * be passed to [VersionInstaller.resolve].
     */
    public static String install(Path gameDir, String gameVersion, String loaderVersion,
                                 VersionInstaller.Listener listener) throws IOException {
        String profileId = "fabric-loader-" + loaderVersion + "-" + gameVersion;
        Path target = VersionInstaller.versionJsonFile(gameDir, profileId);

        if (listener != null) listener.status("Downloading Fabric profile...");
        String url = META + gameVersion + "/" + loaderVersion + "/profile/json";
        JsonObject profile = Http.getJson(url);

        // The id inside the profile must match its folder, or resolution breaks.
        if (!profile.has("id")) profile.addProperty("id", profileId);
        Files.createDirectories(target.getParent());
        Files.writeString(target, profile.toString());

        if (listener != null) listener.status("Fabric " + loaderVersion + " installed for " + gameVersion);
        return profileId;
    }

    /** Picks the newest stable loader, falling back to the newest of any kind. */
    public static String pickLoader(List<LoaderVersion> loaders) {
        for (LoaderVersion loader : loaders) {
            if (loader.stable) return loader.version;
        }
        return loaders.isEmpty() ? null : loaders.get(0).version;
    }
}
