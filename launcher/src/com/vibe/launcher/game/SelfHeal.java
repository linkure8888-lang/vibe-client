package com.vibe.launcher.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vibe.launcher.net.Http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-healing: finds broken files and puts them back.
 *
 * The launcher can be broken by a crashed download, a full disk, an antivirus
 * quarantine or a user deleting a jar by hand. Each of those shows up as "the
 * game does not start", so instead of making the user debug it, [repair]
 * re-checks every file the version needs and re-downloads the broken ones.
 *
 * The user's own data (saves, options, resource packs) is never touched: only
 * files that come from the version manifest are considered.
 */
public final class SelfHeal {

    /** What a repair run did, for the UI. */
    public static final class Report {
        public final List<String> repaired = new ArrayList<>();
        public final List<String> failures = new ArrayList<>();
        public int checked;

        public boolean isClean() {
            return failures.isEmpty() && repaired.isEmpty();
        }

        public String summary() {
            if (checked == 0) return "Nothing to check.";
            if (isClean()) return "All " + checked + " files are intact.";
            StringBuilder sb = new StringBuilder();
            sb.append("Checked ").append(checked).append(" files.");
            if (!repaired.isEmpty()) sb.append(" Repaired: ").append(repaired.size()).append('.');
            if (!failures.isEmpty()) sb.append(" Failed: ").append(failures.size()).append('.');
            return sb.toString();
        }
    }

    private SelfHeal() {
    }

    /**
     * Verifies every file of [resolved] and re-downloads the broken ones.
     * Runs on a worker thread; the game must not be running.
     */
    public static Report repair(Path gameDir, VersionInstaller.Resolved resolved,
                                VersionInstaller.Listener listener) throws IOException {
        Report report = new Report();
        Path librariesDir = gameDir.resolve("libraries");
        var features = java.util.Map.of("is_demo_user", false);

        // 1. Client jar.
        String jarVersion = VersionInstaller.jarVersionFor(gameDir, resolved.id);
        for (VersionJson entry : resolved.chain) {
            if (entry.clientDownload == null) continue;
            report.checked++;
            Path jar = VersionInstaller.versionJarFile(gameDir, entry.id);
            String sha1 = RuleSet.optString(entry.clientDownload, "sha1", null);
            long size = VersionJson.optLong(entry.clientDownload, "size");
            if (!Http.matches(jar, sha1, size)) {
                String url = RuleSet.optString(entry.clientDownload, "url", null);
                if (repairOne(url, jar, sha1, report, listener, "client jar")) continue;
            }
            break;
        }

        // 2. Libraries.
        for (Library library : resolved.version.libraries) {
            if (!RuleSet.allows(library.rules, features)) continue;

            Library.Artifact artifact = library.artifact;
            if (artifact != null && artifact.isUsable()) {
                report.checked++;
                Path target = librariesDir.resolve(artifact.path);
                if (!Http.matches(target, artifact.sha1, artifact.size)) {
                    repairOne(artifact.url, target, artifact.sha1, report, listener, library.name);
                }
            }

            Library.Artifact nativeJar = library.nativesForCurrentOs();
            if (nativeJar != null && nativeJar.isUsable()) {
                report.checked++;
                Path target = librariesDir.resolve(nativeJar.path);
                if (!Http.matches(target, nativeJar.sha1, nativeJar.size)) {
                    repairOne(nativeJar.url, target, nativeJar.sha1, report, listener,
                        library.name + " (natives)");
                }
            }
        }

        // 3. Asset index and objects.
        String indexId = resolved.assetIndexId();
        for (VersionJson entry : resolved.chain) {
            if (entry.assetIndexId == null || entry.assetIndexUrl == null) continue;
            report.checked++;
            Path index = gameDir.resolve("assets").resolve("indexes").resolve(indexId + ".json");
            if (!Http.matches(index, entry.assetIndexSha1, entry.assetIndexSize)) {
                repairOne(entry.assetIndexUrl, index, entry.assetIndexSha1, report, listener,
                    "asset index");
            }
            break;
        }

        if (indexId != null) {
            checkAssets(gameDir, indexId, report, listener);
        }

        return report;
    }

    private static void checkAssets(Path gameDir, String indexId, Report report,
                                    VersionInstaller.Listener listener) throws IOException {
        Path indexPath = gameDir.resolve("assets").resolve("indexes").resolve(indexId + ".json");
        if (!Files.isRegularFile(indexPath)) return;

        JsonObject index = Http.readJsonFile(indexPath);
        JsonObject objects = index.getAsJsonObject("objects");
        if (objects == null) return;

        Path objectsDir = gameDir.resolve("assets").resolve("objects");
        for (String key : objects.keySet()) {
            JsonElement element = objects.get(key);
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String hash = RuleSet.optString(object, "hash", null);
            if (hash == null || hash.length() < 2) continue;
            long size = VersionJson.optLong(object, "size");

            report.checked++;
            Path target = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
            if (Http.matches(target, hash, size)) continue;

            String url = VersionInstaller.RESOURCES + hash.substring(0, 2) + "/" + hash;
            repairOne(url, target, hash, report, listener, "asset " + key);
        }
    }

    /** Returns true when the file is now in place. */
    private static boolean repairOne(String url, Path target, String sha1, Report report,
                                      VersionInstaller.Listener listener, String label) {
        if (url == null || url.isBlank()) {
            report.failures.add(label + ": no download URL in the manifest");
            return false;
        }
        try {
            if (listener != null) listener.status("Repairing " + label + "...");
            Http.download(url, target, sha1, null);
            report.repaired.add(label);
            return true;
        } catch (Exception e) {
            report.failures.add(label + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Makes sure the Fabric profile still points at an installed parent.
     * A missing parent is the single most common "Fabric will not start"
     * cause after a user cleans `versions/` by hand.
     */
    public static List<String> checkInheritance(Path gameDir, VersionInstaller.Resolved resolved) {
        List<String> problems = new ArrayList<>();
        for (VersionJson entry : resolved.chain) {
            Path json = VersionInstaller.versionJsonFile(gameDir, entry.id);
            if (!Files.isRegularFile(json)) problems.add("missing version JSON: " + entry.id);
        }
        if (resolved.chain.size() > 1) {
            String parent = resolved.chain.get(resolved.chain.size() - 1).id;
            if (!Files.isRegularFile(VersionInstaller.versionJsonFile(gameDir, parent))) {
                problems.add("loader profile points at a missing parent: " + parent);
            }
        }
        return problems;
    }
}
