package com.vibe.launcher.game;

import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the `java ...` command line for a resolved version.
 *
 * This is where most "it just doesn't start" bugs live, so the rules are
 * written out explicitly:
 *  - the classpath uses the platform separator and puts the version jar first;
 *  - natives are extracted next to the version and passed via
 *    `-Djava.library.path`;
 *  - placeholders (`${auth_player_name}`, `${version_name}`, ...) are replaced
 *    for both the game and the JVM argument lists;
 *  - rule-gated arguments are filtered with the same feature map the official
 *    launcher uses.
 */
public final class LaunchCommand {

    /** Everything the command line needs that is not in the version JSON. */
    public static final class Profile {
        public String playerName = "Player";
        public String uuid = "00000000000000000000000000000000";
        public String accessToken = "0";
        public String userType = "legacy";
        public String versionType = "release";
        public String gameDir;
        public String assetsDir;
        public int width = 854;
        public int height = 480;
        public String javaPath;
        public String extraJvmArgs = "";
        public String serverAddress;
        public String serverPort;
        /** Absolute path of the downloaded log4j XML, or null to skip it. */
        public String log4jConfigPath;
    }

    private LaunchCommand() {
    }

    public static List<String> build(Path gameDir, VersionInstaller.Resolved resolved,
                                     Profile profile, Path javaExecutable) throws Exception {
        VersionJson version = resolved.version;
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("is_demo_user", false);
        features.put("has_custom_resolution",
            profile.width > 0 && profile.height > 0);
        features.put("has_quick_plays_support", false);
        features.put("is_quick_play_singleplayer", false);
        features.put("is_quick_play_multiplayer", false);
        features.put("is_quick_play_realms", false);

        Path nativesDir = gameDir.resolve("versions").resolve(resolved.id).resolve("natives");
        Files.createDirectories(nativesDir);

        String classpath = buildClasspath(gameDir, resolved);
        Map<String, String> placeholders = buildPlaceholders(gameDir, resolved, profile, classpath);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());

        // Memory defaults: only set when the user did not pass their own -Xmx.
        if (!profile.extraJvmArgs.contains("-Xmx")) {
            command.add("-Xmx2G");
        }
        if (!profile.extraJvmArgs.contains("-Xms")) {
            command.add("-Xms512M");
        }

        // NOTE: java.library.path / launcher brand and version are already in
        // the version JSON's jvm arguments for every modern version, and passing
        // them twice produces duplicate -D flags. Only add what the JSON cannot
        // know about.
        command.add("-Dfile.encoding=UTF-8");

        // log4j config: the argument string is declared in the version JSON and
        // contains ${path}; the file itself is downloaded by GameLauncher.
        if (profile.log4jConfigPath != null && resolved.version.loggingClient != null) {
            String template = RuleSet.optString(resolved.version.loggingClient, "argument", null);
            if (template != null && !template.isBlank()) {
                Map<String, String> logPlaceholders = Map.of("path", profile.log4jConfigPath);
                String resolvedArg = substitute(template, logPlaceholders);
                if (!resolvedArg.isBlank()) command.add(resolvedArg);
            }
        }

        if (profile.extraJvmArgs != null && !profile.extraJvmArgs.isBlank()) {
            for (String token : splitArguments(profile.extraJvmArgs)) {
                if (!token.isBlank()) command.add(token);
            }
        }

        for (VersionJson.Argument argument : version.jvmArguments) {
            if (!argument.applies(features)) continue;
            String value = substitute(argument.value, placeholders);
            if (value.isBlank()) continue;
            // The JSON declares ${classpath} as its own token; nothing to split.
            command.add(value);
        }

        // Some profiles have no jvm arguments at all (old versions).
        if (!command.contains("-cp")) {
            command.add("-cp");
            command.add(classpath);
        }

        command.add(resolved.mainClass());

        for (VersionJson.Argument argument : version.gameArguments) {
            if (!argument.applies(features)) continue;
            String value = substitute(argument.value, placeholders);
            if (value.isBlank() && !argument.value.isBlank()) continue;
            command.add(value);
        }

        // Server join: only when the user asked for it.
        if (profile.serverAddress != null && !profile.serverAddress.isBlank()) {
            command.add("--server");
            command.add(profile.serverAddress);
            if (profile.serverPort != null && !profile.serverPort.isBlank()) {
                command.add("--port");
                command.add(profile.serverPort);
            }
        }

        return command;
    }

    /** Version jar first, then every allowed library jar. */
    private static String buildClasspath(Path gameDir, VersionInstaller.Resolved resolved) {
        List<String> entries = new ArrayList<>();

        String jarVersion = VersionInstaller.jarVersionFor(gameDir, resolved.id);
        Path versionJar = VersionInstaller.versionJarFile(gameDir, jarVersion);
        if (Files.isRegularFile(versionJar)) entries.add(versionJar.toString());

        Map<String, Boolean> features = Map.of("is_demo_user", false);
        Path librariesDir = gameDir.resolve("libraries");
        for (Library library : resolved.version.libraries) {
            if (!RuleSet.allows(library.rules, features)) continue;
            // Native jars DO stay on the classpath. LWJGL's SharedLibraryLoader
            // reads them from there and picks the right architecture itself;
            // that is exactly what the official launcher relies on. Removing
            // them here makes the game fail to find lwjgl.dll at startup.
            Library.Artifact artifact = library.artifact;
            if (artifact == null || !artifact.isUsable()) continue;
            Path jar = librariesDir.resolve(artifact.path);
            if (Files.isRegularFile(jar)) entries.add(jar.toString());
        }

        return String.join(java.io.File.pathSeparator, entries);
    }

    private static Map<String, String> buildPlaceholders(Path gameDir,
                                                         VersionInstaller.Resolved resolved,
                                                         Profile profile,
                                                         String classpath) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth_player_name", profile.playerName);
        map.put("version_name", resolved.id);
        map.put("game_directory", gameDir.toString());
        map.put("assets_root", gameDir.resolve("assets").toString());
        map.put("assets_index_name",
            resolved.assetIndexId() != null ? resolved.assetIndexId() : "legacy");
        map.put("auth_uuid", profile.uuid);
        map.put("auth_access_token", profile.accessToken);
        map.put("auth_session", profile.accessToken);
        map.put("user_type", profile.userType);
        map.put("version_type", resolved.version.type != null ? resolved.version.type : "release");
        map.put("user_properties", "{}");
        map.put("clientid", "");
        map.put("auth_xuid", "");
        map.put("resolution_width", String.valueOf(profile.width));
        map.put("resolution_height", String.valueOf(profile.height));
        map.put("natives_directory",
            gameDir.resolve("versions").resolve(resolved.id).resolve("natives").toString());
        map.put("launcher_name", "vibeclient");
        map.put("launcher_version", "1.0");
        map.put("classpath", classpath);
        map.put("library_directory", gameDir.resolve("libraries").toString());
        return map;
    }

    /** Replaces every `${key}` that we know about; unknown ones are left alone. */
    public static String substitute(String template, Map<String, String> placeholders) {
        if (template == null) return "";
        if (template.indexOf('$') < 0) return template;

        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf("${", i);
            if (start < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, start);
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                out.append(template, start, template.length());
                break;
            }
            String key = template.substring(start + 2, end);
            String value = placeholders.get(key);
            if (value != null) {
                out.append(value);
            } else {
                out.append(template, start, end + 1);
            }
            i = end + 1;
        }
        return out.toString();
    }

    /** Splits a user-supplied argument string on whitespace, honouring quotes. */
    public static List<String> splitArguments(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    /** Reads the logging config path from the version JSON, or null. */
    public static JsonObject loggingConfig(VersionInstaller.Resolved resolved) {
        return resolved.version.loggingClient;
    }
}
