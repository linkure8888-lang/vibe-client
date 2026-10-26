package com.vibe.launcher;

import com.vibe.launcher.game.FabricInstaller;
import com.vibe.launcher.game.GameLauncher;
import com.vibe.launcher.game.JavaRuntime;
import com.vibe.launcher.game.LaunchCommand;
import com.vibe.launcher.game.VersionInstaller;

import java.nio.file.Path;
import java.util.List;

/**
 * Command-line smoke test for the launcher core, no Swing involved.
 *
 * `DevCheck <gameDir> <version> [--fabric] [--print-command]`
 *
 * It resolves the version, downloads what is missing and prints the exact
 * command line a launch would use. This is how the launch path gets verified
 * on a machine where clicking through the GUI is not practical.
 */
public final class DevCheck {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("usage: DevCheck <gameDir> <version> [--fabric] [--print-command]");
            return;
        }
        Path gameDir = Path.of(args[0]);
        String versionId = args[1];
        boolean fabric = false;
        boolean printCommand = false;
        for (int i = 2; i < args.length; i++) {
            if ("--fabric".equals(args[i])) fabric = true;
            if ("--print-command".equals(args[i])) printCommand = true;
        }

        VersionInstaller.Listener listener = new VersionInstaller.Listener() {
            @Override
            public void status(String text) {
                System.out.println("[status] " + text);
            }

            @Override
            public void progress(long done, long total) {
                if (total > 0) {
                    System.out.println("[progress] " + done + "/" + total);
                }
            }
        };

        try {
            List<VersionInstaller.VersionEntry> versions = VersionInstaller.listVersions();
            System.out.println("versions available: " + versions.size());
            VersionInstaller.VersionEntry entry = null;
            for (VersionInstaller.VersionEntry candidate : versions) {
                if (candidate.id.equals(versionId)) {
                    entry = candidate;
                    break;
                }
            }
            if (entry == null) {
                System.out.println("ERROR: version " + versionId + " is not in the manifest");
                return;
            }
            System.out.println("picked " + entry.id + " (" + entry.type + ")");

            VersionInstaller.Resolved vanilla = VersionInstaller.resolve(
                gameDir, versionId, entry.url, listener);
            System.out.println("main class: " + vanilla.mainClass());
            System.out.println("libraries: " + vanilla.version.libraries.size());
            VersionInstaller.install(gameDir, vanilla, listener);
            System.out.println("vanilla install OK");

            String launchId = versionId;
            if (fabric) {
                List<FabricInstaller.LoaderVersion> loaders = FabricInstaller.listLoaders(versionId);
                String loader = FabricInstaller.pickLoader(loaders);
                System.out.println("fabric loaders: " + loaders.size() + ", picked " + loader);
                if (loader != null) {
                    launchId = FabricInstaller.install(gameDir, versionId, loader, listener);
                    VersionInstaller.Resolved fabricResolved = VersionInstaller.resolve(
                        gameDir, launchId, null, listener);
                    VersionInstaller.install(gameDir, fabricResolved, listener);
                    System.out.println("fabric install OK: " + launchId);
                }
            }

            int requiredJava = vanilla.version.javaMajorVersion > 0
                ? vanilla.version.javaMajorVersion : 8;
            JavaRuntime.Found java = JavaRuntime.find(gameDir, Path.of("."), requiredJava,
                vanilla.version.javaComponent, listener);
            System.out.println("java: " + java.description);

            if (printCommand) {
                LaunchCommand.Profile profile = new LaunchCommand.Profile();
                profile.playerName = "TestPlayer";
                profile.uuid = "00000000000000000000000000000000";
                profile.gameDir = gameDir.toString();
                profile.assetsDir = gameDir.resolve("assets").toString();
                VersionInstaller.Resolved resolved = fabric
                    ? VersionInstaller.resolve(gameDir, launchId, null, listener)
                    : vanilla;
                List<String> command = LaunchCommand.build(gameDir, resolved, profile, java.executable);
                System.out.println("--- command ---");
                for (String part : command) {
                    System.out.println(part);
                }
            }

            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    private DevCheck() {
    }
}
