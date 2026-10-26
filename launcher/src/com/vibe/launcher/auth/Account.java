package com.vibe.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A logged-in identity, online (Microsoft) or offline. */
public final class Account {

    public String username;
    /** 32 hex chars, no dashes — the format the game expects. */
    public String uuid;
    public String accessToken = "0";
    public String userType = "legacy";
    /** "offline" or "microsoft". */
    public String kind = "offline";
    public long expiresAt;
    public String refreshToken;

    public Account() {
    }

    public Account(String username, String uuid, String accessToken, String userType, String kind) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.userType = userType;
        this.kind = kind;
    }

    public boolean isOnline() {
        return "microsoft".equals(kind);
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt - 60_000L;
    }

    /**
     * Offline UUID, computed exactly like the vanilla server does:
     * `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`. This is what makes an
     * offline player keep the same inventory between sessions.
     */
    public static String offlineUuid(String username) {
        UUID uuid = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
    }

    public static Account offline(String username) {
        return new Account(username, offlineUuid(username), "0", "legacy", "offline");
    }

    /** Ensures the fields the game requires are always present. */
    public Account validated() {
        if (username == null || username.isBlank()) username = "Player";
        // Minecraft allows 3..16 chars and [A-Za-z0-9_].
        username = username.replaceAll("[^A-Za-z0-9_]", "_");
        if (username.length() > 16) username = username.substring(0, 16);
        if (username.length() < 3) username = (username + "___").substring(0, 3);

        if (uuid == null || uuid.isBlank()) {
            uuid = isOnline() ? "00000000000000000000000000000000" : offlineUuid(username);
        }
        if (accessToken == null) accessToken = "0";
        if (userType == null) userType = isOnline() ? "msa" : "legacy";
        return this;
    }
}
