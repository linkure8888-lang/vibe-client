package com.vibe.launcher.game;

import com.google.gson.JsonObject;
import com.vibe.launcher.auth.Account;
import com.vibe.launcher.net.Http;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * End-to-end launch: resolve -> install -> pick Java -> build command -> run.
 *
 * The class returns the running [Process] so the UI can stream its output; it
 * never blocks on the game itself.
 */
public final class GameLauncher {

    /** Same shape as the installer listener, so one object serves both. */
    public interface Listener extends VersionInstaller.Listener {
    }

    /** Everything needed to start one game session. */
    public static final class Request {
        public Path gameDir;
        public Path appDir;
        public String versionId;
        public String versionJsonUrl;
        public Account account;
        public int width = 854;
        public int height = 480;
        public String extraJvmArgs = "";
        public String serverAddress;
        public String serverPort;
    }

    private GameLauncher() {
    }

    /** Resolves and installs the version without starting it. */
    public static VersionInstaller.Resolved prepare(Request request, Listener listener)
        throws IOException {
        if (request.gameDir == null) throw new IOException("game directory is not set");
        if (request.versionId == null || request.versionId.isBlank()) {
            throw new IOException("no version selected");
        }
        Files.createDirectories(request.gameDir);

        if (listener != null) listener.status("Resolving " + request.versionId + "...");
        VersionInstaller.Resolved resolved = VersionInstaller.resolve(
            request.gameDir, request.versionId, request.versionJsonUrl, listener);

        if (listener != null) listener.status("Checking files...");
        VersionInstaller.install(request.gameDir, resolved, listener);
        return resolved;
    }

    /** Full launch. The caller owns the returned process. */
    public static Process launch(Request request, Listener listener) throws Exception {
        VersionInstaller.Resolved resolved = prepare(request, listener);

        int requiredJava = resolved.version.javaMajorVersion > 0
            ? resolved.version.javaMajorVersion : 8;

        if (listener != null) listener.status("Locating Java (" + requiredJava + "+)...");
        JavaRuntime.Found java = JavaRuntime.find(
            request.gameDir, request.appDir, requiredJava,
            resolved.version.javaComponent, listener);

        // log4j config declared by the version (1.12+).
        String log4jPath = installLoggingConfig(request.gameDir, resolved, listener);

        Account account = request.account != null
            ? request.account.validated()
            : Account.offline("Player");

        LaunchCommand.Profile profile = new LaunchCommand.Profile();
        profile.playerName = account.username;
        profile.uuid = account.uuid;
        profile.accessToken = account.accessToken;
        profile.userType = account.userType;
        profile.gameDir = request.gameDir.toString();
        profile.assetsDir = request.gameDir.resolve("assets").toString();
        profile.width = request.width;
        profile.height = request.height;
        profile.extraJvmArgs = request.extraJvmArgs;
        profile.serverAddress = request.serverAddress;
        profile.serverPort = request.serverPort;
        profile.log4jConfigPath = log4jPath;

        List<String> command = LaunchCommand.build(
            request.gameDir, resolved, profile, java.executable);

        if (listener != null) listener.status("Starting Minecraft...");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(request.gameDir.toFile());
        builder.redirectErrorStream(true);
        // Keep the console window hidden on Windows; stdout is still captured.
        return builder.start();
    }

    /**
     * Downloads the log4j XML the version asks for and returns its absolute path.
     * Returns null for versions without one (pre-1.12).
     */
    private static String installLoggingConfig(Path gameDir,
                                               VersionInstaller.Resolved resolved,
                                               Listener listener) {
        JsonObject logging = LaunchCommand.loggingConfig(resolved);
        if (logging == null) return null;

        try {
            JsonObject file = logging.getAsJsonObject("file");
            if (file == null) return null;
            String id = RuleSet.optString(file, "id", "client-log4j.xml");
            String url = RuleSet.optString(file, "url", null);
            String sha1 = RuleSet.optString(file, "sha1", null);
            long size = VersionJson.optLong(file, "size");
            if (url == null) return null;

            Path target = gameDir.resolve("assets").resolve("log_configs").resolve(id);
            if (!Http.matches(target, sha1, size)) {
                if (listener != null) listener.status("Downloading logging config...");
                Http.download(url, target, sha1, null);
            }
            return target.toString();
        } catch (Exception e) {
            // A missing logging config must never block a launch: the game falls
            // back to its built-in default and only loses coloured log output.
            if (listener != null) listener.status("Logging config unavailable: " + e.getMessage());
            return null;
        }
    }

    /** Opens the game folder in Explorer/Finder. */
    public static void openFolder(Path folder) throws IOException {
        Files.createDirectories(folder);
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().open(folder.toFile());
        }
    }

    /** True when [dir] already has a runnable install of [versionId]. */
    public static boolean isInstalled(Path gameDir, String versionId) {
        if (versionId == null) return false;
        Path json = VersionInstaller.versionJsonFile(gameDir, versionId);
        if (!Files.isRegularFile(json)) return false;
        String jarVersion = VersionInstaller.jarVersionFor(gameDir, versionId);
        Path jar = VersionInstaller.versionJarFile(gameDir, jarVersion);
        return Files.isRegularFile(jar);
    }

    /** Path separator of the current platform, exposed for tests. */
    public static String pathSeparator() {
        return File.pathSeparator;
    }
}
