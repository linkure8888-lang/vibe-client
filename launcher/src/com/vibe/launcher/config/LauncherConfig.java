package com.vibe.launcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vibe.launcher.auth.Account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's own settings, stored as one small JSON file.
 *
 * Written atomically (temp file + move) so a crash mid-save cannot leave a
 * half-written config that breaks the next start. Unreadable configs are
 * backed up and replaced with defaults rather than thrown at the user.
 */
public final class LauncherConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 1;

    public int configVersion = CURRENT_VERSION;

    /**
     * The Minecraft version Vibe Client is built for.
     *
     * Single source of truth. The mod declares the whole 1.21 family, so any
     * 1.21.x loads the client; anything else means the mod silently does not
     * load — the user sees a vanilla game and concludes the client is broken.
     * Warning about the mismatch turns a mystery into a fixable problem.
     */
    public static final String SUPPORTED_MINECRAFT = "1.21.1";

    /**
     * True when [versionId] is any 1.21.x release — the range the mod's
     * fabric.mod.json declares. The exact patch number does not matter:
     * "1.21.1", "1.21.4" and a future "1.21.9" all load the same client.
     */
    public static boolean isSupportedFamily(String versionId) {
        if (versionId == null) return false;
        String v = versionId.trim();
        if (v.startsWith("1.21.")) return true;
        // "1.21" exactly (rare, but some manifests use it) still counts.
        return v.equals("1.21");
    }

    public String selectedVersion = SUPPORTED_MINECRAFT;
    public String gameDir;
    public String playerName = "Player";
    public boolean useFabric = true;
    public String fabricLoaderVersion;
    public int memoryMb = 2048;
    public int windowWidth = 854;
    public int windowHeight = 480;
    public String extraJvmArgs = "";
    public String serverAddress = "";
    public String serverPort = "";
    public String microsoftClientId = "";
    public boolean closeLauncherOnStart = true;

    /**
     * Install the Sodium/Lithium-class performance pack on launch.
     * On by default: it is client-side only, cannot get anyone banned, and it
     * is the difference between a slideshow and a playable game on weak PCs.
     */
    public boolean performancePack = true;

    /**
     * GitHub repository the mod updates from, e.g. "user/vibeclient".
     *
     * Empty by default, and an empty value disables the updater completely: an
     * updater pointed at a repository that does not exist would fail on every
     * launch and train users to ignore its messages.
     */
    public String updateRepository = "";

    /** Check for a newer client build automatically when the launcher starts. */
    public boolean autoCheckUpdates = true;

    /**
     * The equipped cosmetic id (a cape from the Cosmetics store), or empty
     * when none is worn. The in-game client reads the same id from the
     * vibeclient config on startup and draws the cape locally; there is no
     * store server, so "equipping" is just remembering the choice.
     */
    public String equippedCosmetic = "";

    /** Saved identities; the active one is [activeAccount]. */
    public List<Account> accounts = new ArrayList<>();
    public int activeAccount = -1;

    // -- Load / save -------------------------------------------------------

    public static LauncherConfig load(Path file) {
        LauncherConfig config = new LauncherConfig();
        if (!Files.isRegularFile(file)) {
            config.gameDir = defaultGameDir().toString();
            return config;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            LauncherConfig parsed = GSON.fromJson(json, LauncherConfig.class);
            if (parsed == null) throw new IOException("empty config");
            if (parsed.accounts == null) parsed.accounts = new ArrayList<>();
            if (parsed.gameDir == null || parsed.gameDir.isBlank()) {
                parsed.gameDir = defaultGameDir().toString();
            }
            // A config written by an older build can point at another game
            // version, which silently stops the client from loading and looks
            // like the client is broken. Move it back to the supported version;
            // the user can still pick another one explicitly, and the launcher
            // warns them in that case.
            if (parsed.selectedVersion == null || parsed.selectedVersion.isBlank()) {
                parsed.selectedVersion = SUPPORTED_MINECRAFT;
            }
            // Clamp values a hand-edited file could have made nonsense.
            parsed.memoryMb = Math.max(512, Math.min(parsed.memoryMb, 32768));
            parsed.windowWidth = Math.max(320, parsed.windowWidth);
            parsed.windowHeight = Math.max(240, parsed.windowHeight);
            return parsed;
        } catch (Exception e) {
            // Keep the broken file for diagnosis instead of silently deleting it.
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                // Best effort.
            }
            config.gameDir = defaultGameDir().toString();
            return config;
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // -- Accounts ----------------------------------------------------------

    public Account activeAccount() {
        if (activeAccount < 0 || activeAccount >= accounts.size()) return null;
        return accounts.get(activeAccount);
    }

    /** Adds or replaces an account by uuid and makes it active. */
    public void putAccount(Account account) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).uuid != null && accounts.get(i).uuid.equals(account.uuid)) {
                accounts.set(i, account);
                activeAccount = i;
                return;
            }
        }
        accounts.add(account);
        activeAccount = accounts.size() - 1;
    }

    public void removeAccount(int index) {
        if (index < 0 || index >= accounts.size()) return;
        accounts.remove(index);
        if (activeAccount >= accounts.size()) activeAccount = accounts.size() - 1;
    }

    /**
     * Default game folder for a fresh install.
     *
     * On Windows this is `C:\VibeClient\game`: a fixed, obvious location the
     * user can open from the launcher and drop a fixed mod jar into without
     * hunting through AppData.
     *
     * Two guards, both there to avoid making the user pay for a folder change:
     *  - if a normal `.minecraft` already has an install in it, keep using that
     *    folder rather than re-downloading the whole game;
     *  - if the root is not writable (locked-down machine, no admin), fall back
     *    to the per-user location.
     */
    public static Path defaultGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path standard = standardGameDir();
        if (hasInstalledVersions(standard)) return standard;

        if (os.contains("win")) {
            Path root = Path.of("C:\\VibeClient");
            try {
                Files.createDirectories(root);
                if (Files.isWritable(root)) return root.resolve("game");
            } catch (Exception ignored) {
                // Fall through to the per-user location below.
            }
        }
        return standard;
    }

    /** True when a game folder already holds at least one installed version. */
    private static boolean hasInstalledVersions(Path gameDir) {
        try {
            Path versions = gameDir.resolve("versions");
            if (!Files.isDirectory(versions)) return false;
            try (var stream = Files.list(versions)) {
                return stream.anyMatch(p -> Files.isDirectory(p)
                    && Files.isRegularFile(p.resolve(p.getFileName() + ".json")));
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /** The platform's usual `.minecraft` folder. */
    public static Path standardGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return appData != null
                ? Path.of(appData, ".minecraft")
                : Path.of(home, "AppData", "Roaming", ".minecraft");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "minecraft");
        return Path.of(home, ".minecraft");
    }
}
