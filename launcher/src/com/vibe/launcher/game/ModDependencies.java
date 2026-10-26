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
 * Fetches required Fabric mod dependencies automatically.
 *
 * Vibe Client is written in Kotlin, so it declares a hard dependency on
 * `fabric-language-kotlin`. Without it Fabric refuses to start the whole game
 * with "Incompatible mods found", which looks like the client is broken to an
 * end user. Rather than shipping a second jar and hoping the user copies it,
 * the launcher resolves and installs such dependencies itself.
 */
public final class ModDependencies {

    private static final String MODRINTH = "https://api.modrinth.com/v2";

    /**
     * Record of every jar this launcher installed, one file name per line.
     *
     * Why a file and not a name pattern: dependencies are discovered at
     * runtime (`cloth-config`, `indium`, whatever a mod happens to need), so
     * their file names cannot be listed in code ahead of time. Without this
     * record `purgeIncompatible` would leave a stale dependency behind on a
     * Minecraft version switch and the game would refuse to start again --
     * exactly the bug this class exists to prevent.
     */
    private static final String MANIFEST_NAME = ".vibe-managed.txt";

    /** Dependencies the bundled client needs, by Modrinth slug. */
    private static final String[] REQUIRED_SLUGS = {"fabric-language-kotlin", "fabric-api"};

    /**
     * Optional performance pack, installed when the user enables it.
     *
     * These are the mods that actually produce the "200 FPS on a potato" result,
     * and they are the same set every serious performance modpack ships:
     *
     *  - sodium          replaces the terrain renderer (the single biggest win)
     *  - lithium         rewrites hot game-logic paths (ticking, AI, physics)
     *  - ferritecore     cuts blockstate/model memory use
     *  - immediatelyfast batches immediate-mode GUI/entity text rendering
     *  - entityculling   stops drawing entities hidden behind geometry
     *  - moreculling     same idea for block faces
     *  - krypton         optimises the network stack
     *  - dynamic-fps     drops the frame rate when the window is not focused
     *  - modernfix       faster startup, lower memory, fewer stalls
     *
     * Every one of them is client-side and server-safe: joining a vanilla
     * server with these installed is normal and cannot get anyone banned.
     * Install failures are per-mod and non-fatal — a missing optimisation must
     * never stop the game from launching.
     */
    private static final String[] PERFORMANCE_SLUGS = {
        "sodium",
        "lithium",
        "ferrite-core",
        "immediatelyfast",
        "entityculling",
        "moreculling",
        "krypton",
        "dynamic-fps",
        "modernfix",
        // Declared REQUIRED by moreculling. Listed explicitly as well as being
        // resolved from the API, so a fresh install never depends on the
        // dependency walk succeeding on the first try.
        "cloth-config"
    };

    private ModDependencies() {
    }

    /**
     * Makes sure every required dependency is in `<game>/mods` for
     * [gameVersion] + the given Fabric loader.
     */
    public static List<String> installRequired(Path gameDir, String gameVersion,
                                               String loaderVersion,
                                               VersionInstaller.Listener listener) {
        List<String> installed = new ArrayList<>();
        Path modsDir = gameDir.resolve("mods");
        try {
            Files.createDirectories(modsDir);
        } catch (IOException e) {
            return installed;
        }

        for (String slug : REQUIRED_SLUGS) {
            try {
                if (alreadyInstalled(modsDir, slug, gameVersion)) continue;
                if (listener != null) listener.status("Installing dependency: " + slug);
                Resolved resolved = downloadLatestDetailed(modsDir, slug, gameVersion, loaderVersion);
                if (resolved == null) continue;
                installed.add(resolved.path.getFileName().toString());
                installRequiredDeps(resolved.json, modsDir, gameVersion, loaderVersion,
                    listener, 0);
            } catch (Exception e) {
                if (listener != null) {
                    listener.status("Dependency " + slug + " failed: " + e.getMessage());
                }
            }
        }
        return installed;
    }

    /**
     * Installs the optional performance pack for [gameVersion].
     *
     * Unlike [installRequired] this is best-effort per mod: one project being
     * unavailable for the game version must not abort the rest. Returns the
     * file names that were installed or already present.
     */
    public static List<String> installPerformancePack(Path gameDir, String gameVersion,
                                                      String loaderVersion,
                                                      VersionInstaller.Listener listener) {
        List<String> installed = new ArrayList<>();
        Path modsDir = gameDir.resolve("mods");
        try {
            Files.createDirectories(modsDir);
        } catch (IOException e) {
            return installed;
        }

        for (String slug : PERFORMANCE_SLUGS) {
            try {
                if (alreadyInstalled(modsDir, slug, gameVersion)) continue;
                if (listener != null) listener.status("Optimising: " + slug);
                Resolved resolved = downloadLatestDetailed(modsDir, slug, gameVersion, loaderVersion);
                if (resolved == null) continue;
                installed.add(resolved.path.getFileName().toString());
                // A perf mod with an unsatisfied hard dependency does not just
                // fail itself -- Fabric refuses to start the entire game.
                installRequiredDeps(resolved.json, modsDir, gameVersion, loaderVersion,
                    listener, 0);
            } catch (Exception e) {
                // Skipped on purpose: the pack is optional and a single gap
                // still leaves a much faster game than vanilla.
                if (listener != null) listener.status("Skipped " + slug + ": " + e.getMessage());
            }
        }
        return installed;
    }

    /** True when any performance mod is present, for the launcher's status line. */
    public static boolean hasPerformancePack(Path gameDir) {
        Path modsDir = gameDir.resolve("mods");
        for (String slug : PERFORMANCE_SLUGS) {
            if (alreadyInstalled(modsDir, slug, null)) return true;
        }
        return false;
    }

    /**
     * True when a jar for [slug] is present AND compatible with [gameVersion].
     *
     * The version check is the important half. Matching the file name alone let
     * a `fabric-api` built for 1.21.1 satisfy the check while the user ran
     * 1.21.11, so the download was skipped and the game refused to start with
     * "Incompatible mods found". A mod that declares no minecraft range is
     * accepted, since refusing it would block mods that work fine.
     *
     * When [gameVersion] is null the version check is skipped entirely; that is
     * only used by the status query, never by the install path.
     */
    private static boolean alreadyInstalled(Path modsDir, String slug, String gameVersion) {
        // Jar names use dashes, underscores or both depending on the project
        // (`fabric-language-kotlin-…`, `fabric-api-…`), so compare a normalised
        // form instead of guessing the separator.
        //
        // The extra `language-kotlin` alias applies ONLY to that one slug. It
        // used to sit in the same OR for every slug, which made `fabric-api`
        // look installed whenever the Kotlin jar was present and silently
        // skipped its download.
        String needle = slug.replace('_', '-').toLowerCase();
        boolean kotlinAlias = "fabric-language-kotlin".equals(needle);

        try (var stream = Files.list(modsDir)) {
            List<Path> candidates = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase().replace('_', '-');
                    return name.endsWith(".jar")
                        && (name.contains(needle)
                            || (kotlinAlias && name.contains("language-kotlin")));
                })
                .forEach(candidates::add);

            for (Path candidate : candidates) {
                if (gameVersion == null) return true;
                String declared = readMinecraftRange(candidate);
                if (declared == null || VersionRange.matches(gameVersion, declared)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads the `minecraft` version range a Fabric mod declares.
     *
     * Every Fabric mod carries `fabric.mod.json` inside its jar with a
     * `depends.minecraft` entry. Returns null when the file cannot be read or
     * declares no range, which the caller treats as "no constraint".
     */
    private static String readMinecraftRange(Path jar) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return null;
            try (java.io.InputStream in = zip.getInputStream(entry)) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                JsonObject json = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                JsonObject depends = json.getAsJsonObject("depends");
                if (depends == null) return null;
                JsonElement minecraft = depends.get("minecraft");
                if (minecraft == null || minecraft.isJsonNull()) return null;
                // The field is usually a range string, but is an array of
                // ranges in some mods; treat the first entry as the constraint.
                if (minecraft.isJsonArray()) {
                    JsonArray array = minecraft.getAsJsonArray();
                    return array.isEmpty() ? null
                        : (array.get(0).isJsonNull() ? null : array.get(0).getAsString());
                }
                return minecraft.getAsString();
            }
        } catch (Exception e) {
            // An unreadable jar is not evidence of compatibility; the caller
            // will simply download the correct build.
            return null;
        }
    }

    /**
     * Removes installed mods that do not support [gameVersion].
     *
     * This is what stops the "Incompatible mods found" screen after a version
     * switch. Without it, mods for the old version stay on disk, Fabric loads
     * all of them, and the game refuses to start — with no obvious hint that
     * the fix is to delete a file the user never knowingly installed.
     *
     * Only mods the launcher itself manages are touched: every slug in
     * [REQUIRED_SLUGS] and [PERFORMANCE_SLUGS]. A mod the user installed by
     * hand is left alone, because deleting someone's files is not this code's
     * job.
     */
    public static List<String> purgeIncompatible(Path gameDir, String gameVersion,
                                                 VersionInstaller.Listener listener) {
        List<String> removed = new ArrayList<>();
        Path modsDir = gameDir.resolve("mods");
        if (!Files.isDirectory(modsDir)) return removed;

        List<Path> jars = new ArrayList<>();
        try (var stream = Files.list(modsDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .forEach(jars::add);
        } catch (Exception e) {
            return removed;
        }

        for (Path jar : jars) {
            if (!isManaged(modsDir, jar.getFileName().toString())) continue;
            String range = readMinecraftRange(jar);
            if (range == null || VersionRange.matches(gameVersion, range)) continue;
            try {
                Files.deleteIfExists(jar);
                removed.add(jar.getFileName().toString());
                if (listener != null) {
                    listener.status("Removed incompatible " + jar.getFileName());
                }
            } catch (Exception e) {
                // Locked (game running) or permission denied; it will be picked
                // up on the next pass rather than failing the whole install.
            }
        }
        return removed;
    }

    /** True when the launcher owns this jar and may replace or delete it. */
    private static boolean isManaged(Path modsDir, String fileName) {
        String name = fileName.toLowerCase().replace('_', '-');
        for (String slug : REQUIRED_SLUGS) {
            if (name.contains(slug.replace('_', '-').toLowerCase())) return true;
        }
        if (name.contains("language-kotlin")) return true;
        for (String slug : PERFORMANCE_SLUGS) {
            if (name.contains(slug.replace('_', '-').toLowerCase())) return true;
        }
        return readManifest(modsDir).contains(fileName);
    }

    /** Every jar name this launcher has installed, from the manifest file. */
    private static java.util.Set<String> readManifest(Path modsDir) {
        Path file = modsDir.resolve(MANIFEST_NAME);
        if (!Files.isRegularFile(file)) return java.util.Set.of();
        try {
            return new java.util.HashSet<>(Files.readAllLines(file));
        } catch (Exception e) {
            // A corrupt manifest means "we know nothing", which is safe: the
            // launcher simply stops deleting files it cannot prove it owns.
            return java.util.Set.of();
        }
    }

    /** Records a jar as launcher-managed. Best effort; never fails an install. */
    private static void rememberManaged(Path modsDir, String fileName) {
        try {
            Path file = modsDir.resolve(MANIFEST_NAME);
            var known = new java.util.LinkedHashSet<>(readManifest(modsDir));
            if (known.add(fileName)) {
                Files.write(file, known);
            }
        } catch (Exception e) {
            // Non-fatal: the file just will not be purged on a version switch.
        }
    }

    /**
     * Resolves the hard dependencies declared by a Modrinth version.
     *
     * This exists because of a real crash: `moreculling` declares a REQUIRED
     * dependency on `cloth-config`, the launcher installed only the primary
     * file, and Fabric then refused to start the whole game with
     * "Incompatible mods found". The dependency list was in the API response
     * the whole time -- nothing read it.
     *
     * Only `required` entries are installed. `optional` entries are not ours to
     * choose (the user may deliberately not want them), and `incompatible`
     * entries cannot be installed by definition.
     *
     * [depth] guards against a dependency cycle: a mod that depends on itself,
     * directly or through a chain, would otherwise recurse forever.
     */
    private static void installRequiredDeps(JsonObject version, Path modsDir,
                                            String gameVersion, String loaderVersion,
                                            VersionInstaller.Listener listener,
                                            int depth) {
        if (depth > 5) return;
        JsonArray deps = version.getAsJsonArray("dependencies");
        if (deps == null) return;

        for (JsonElement element : deps) {
            if (!element.isJsonObject()) continue;
            JsonObject dep = element.getAsJsonObject();

            String kind = RuleSet.optString(dep, "dependency_type", "");
            if (!"required".equals(kind)) continue;

            // A dependency is identified either by its Modrinth project id or
            // by a version id. Project id is the useful one: we want the newest
            // compatible build, not the exact one that happened to be current
            // when the parent mod was published.
            String projectId = RuleSet.optString(dep, "project_id", null);
            if (projectId == null) continue;

            try {
                if (alreadyInstalled(modsDir, projectId, gameVersion)) continue;
                if (listener != null) listener.status("Installing dependency: " + projectId);

                Resolved resolved = downloadLatestDetailed(
                    modsDir, projectId, gameVersion, loaderVersion);
                if (resolved == null) {
                    if (listener != null) {
                        listener.status("WARNING: could not resolve " + projectId
                            + " -- the game may refuse to start");
                    }
                    continue;
                }
                installRequiredDeps(resolved.json, modsDir, gameVersion, loaderVersion,
                    listener, depth + 1);
            } catch (Exception e) {
                if (listener != null) {
                    listener.status("Dependency " + projectId + " failed: " + e.getMessage());
                }
            }
        }
    }

    /** A downloaded version plus the JSON it came from, for dependency walking. */
    private record Resolved(Path path, JsonObject json) {
    }

    /** Picks the newest compatible file and writes it into `mods/`. */
    private static Path downloadLatest(Path modsDir, String slug, String gameVersion,
                                       String loaderVersion) throws IOException {
        Resolved resolved = downloadLatestDetailed(modsDir, slug, gameVersion, loaderVersion);
        return resolved == null ? null : resolved.path;
    }

    /** Same as [downloadLatest], but keeps the version JSON for dependency walking. */
    private static Resolved downloadLatestDetailed(Path modsDir, String slug, String gameVersion,
                                                   String loaderVersion) throws IOException {
        String url = MODRINTH + "/project/" + slug + "/version"
            + "?game_versions=%5B%22" + gameVersion + "%22%5D"
            + "&loaders=%5B%22fabric%22%5D";
        JsonArray versions = Http.getJsonArray(url);
        if (versions == null || versions.isEmpty()) return null;

        // Modrinth returns newest first. Reject entries that declare a
        // conflicting minecraft range: the query filter is a coarse match and a
        // mod can still list a version it only partially supports.
        JsonObject best = null;
        String bestDate = "";
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();
            if (!hasFile(version)) continue;

            String declared = RuleSet.optString(version, "game_versions", null);
            if (!listsVersion(version, gameVersion)) continue;

            String date = RuleSet.optString(version, "date_published", "");
            if (best == null || date.compareTo(bestDate) > 0) {
                best = version;
                bestDate = date;
            }
        }
        if (best == null) return null;

        JsonArray files = best.getAsJsonArray("files");
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            boolean primary = file.has("primary") && !file.get("primary").isJsonNull()
                && file.get("primary").getAsBoolean();
            if (!primary) continue;
            String fileUrl = RuleSet.optString(file, "url", null);
            String fileName = RuleSet.optString(file, "filename", null);
            if (fileUrl == null || fileName == null) continue;

            Path target = modsDir.resolve(fileName);
            if (!Files.isRegularFile(target)) {
                Http.download(fileUrl, target, null, null);
            }
            rememberManaged(modsDir, fileName);
            return new Resolved(target, best);
        }
        return null;
    }

    /** True when the version's own `game_versions` array lists [gameVersion]. */
    private static boolean listsVersion(JsonObject version, String gameVersion) {
        JsonArray list = version.getAsJsonArray("game_versions");
        if (list == null) return true;
        for (JsonElement element : list) {
            if (element.isJsonNull()) continue;
            if (gameVersion.equals(element.getAsString())) return true;
        }
        return false;
    }

    private static boolean hasFile(JsonObject version) {
        JsonArray files = version.getAsJsonArray("files");
        if (files == null) return false;
        for (JsonElement element : files) {
            if (element.isJsonObject()) return true;
        }
        return false;
    }
}
