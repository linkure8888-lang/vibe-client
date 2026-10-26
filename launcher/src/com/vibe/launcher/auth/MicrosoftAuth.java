package com.vibe.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vibe.launcher.net.Http;

import java.io.IOException;

/**
 * Microsoft/Xbox/Minecraft authentication using the device-code flow.
 *
 * Why device code: it needs no embedded browser, no redirect URI listener and
 * no client secret, which keeps the launcher dependency-free and safe. The
 * user opens a URL, types a short code, and the launcher polls until Microsoft
 * hands over the tokens.
 *
 * The Azure application id is user-configurable because an app id belongs to
 * whoever publishes the launcher. Offline mode never touches this class.
 */
public final class MicrosoftAuth {

    private static final String DEVICE_CODE_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL =
        "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL =
        "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
        "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
        "https://api.minecraftservices.com/minecraft/profile";

    /** Scopes required to obtain a Minecraft-capable Xbox token. */
    private static final String SCOPE = "XboxLive.signin offline_access";

    /** What the UI shows the user while waiting. */
    public static final class DeviceCode {
        public String userCode;
        public String verificationUri;
        public String message;
        public String deviceCode;
        public int intervalSeconds = 5;
        public int expiresInSeconds = 900;
    }

    private MicrosoftAuth() {
    }

    /** Step 1: ask Microsoft for a code the user can type in a browser. */
    public static DeviceCode requestDeviceCode(String clientId) throws IOException {
        if (clientId == null || clientId.isBlank()) {
            throw new IOException("Microsoft client id is not configured");
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("client_id", clientId);
        payload.addProperty("scope", SCOPE);

        JsonObject response = Http.postJson(DEVICE_CODE_URL, payload);
        int status = response.has("_status") ? response.get("_status").getAsInt() : 200;
        if (status != 200) {
            throw new IOException("device code request failed: " + describeError(response));
        }

        DeviceCode code = new DeviceCode();
        code.deviceCode = optString(response, "device_code");
        code.userCode = optString(response, "user_code");
        code.verificationUri = optString(response, "verification_uri");
        code.message = optString(response, "message");
        code.intervalSeconds = Math.max(2, optInt(response, "interval", 5));
        code.expiresInSeconds = optInt(response, "expires_in", 900);
        return code;
    }

    /**
     * Step 2: poll until the user finishes in the browser.
     * [checker] lets the caller cancel; return false to stop waiting.
     */
    public interface CancelCheck {
        boolean keepWaiting();
    }

    /** Blocking call — run it on a worker thread. */
    public static Account awaitLogin(String clientId, DeviceCode code, CancelCheck checker,
                                     StatusListener listener) throws IOException {
        long deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L;
        String refreshToken = null;

        while (System.currentTimeMillis() < deadline) {
            if (checker != null && !checker.keepWaiting()) {
                throw new IOException("login cancelled");
            }
            sleep(code.intervalSeconds * 1000L);

            JsonObject payload = new JsonObject();
            payload.addProperty("client_id", clientId);
            payload.addProperty("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            payload.addProperty("device_code", code.deviceCode);

            JsonObject response = Http.postJson(TOKEN_URL, payload);
            int status = response.has("_status") ? response.get("_status").getAsInt() : 200;

            if (status == 200) {
                refreshToken = optString(response, "refresh_token");
                String accessToken = optString(response, "access_token");
                return exchangeForMinecraft(accessToken, refreshToken, listener);
            }

            String error = optString(response, "error");
            if ("authorization_pending".equals(error)) {
                if (listener != null) listener.status("Waiting for the browser sign-in...");
                continue;
            }
            if ("slow_down".equals(error)) {
                code.intervalSeconds += 5;
                continue;
            }
            if ("expired_token".equals(error)) {
                throw new IOException("the sign-in code expired, please try again");
            }
            if ("authorization_declined".equals(error)) {
                throw new IOException("sign-in was declined");
            }
            throw new IOException("sign-in failed: " + describeError(response));
        }
        throw new IOException("sign-in timed out");
    }

    /** Refreshes an expired session without user interaction. */
    public static Account refresh(String clientId, String refreshToken,
                                  StatusListener listener) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("client_id", clientId);
        payload.addProperty("grant_type", "refresh_token");
        payload.addProperty("refresh_token", refreshToken);
        payload.addProperty("scope", SCOPE);

        JsonObject response = Http.postJson(TOKEN_URL, payload);
        int status = response.has("_status") ? response.get("_status").getAsInt() : 200;
        if (status != 200) throw new IOException("token refresh failed: " + describeError(response));

        String accessToken = optString(response, "access_token");
        String newRefresh = optString(response, "refresh_token");
        return exchangeForMinecraft(accessToken,
            newRefresh != null ? newRefresh : refreshToken, listener);
    }

    /** Microsoft token -> Xbox Live -> XSTS -> Minecraft services. */
    private static Account exchangeForMinecraft(String microsoftToken, String refreshToken,
                                                StatusListener listener) throws IOException {
        if (listener != null) listener.status("Authenticating with Xbox Live...");

        JsonObject xblProperties = new JsonObject();
        xblProperties.addProperty("AuthMethod", "RPS");
        xblProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProperties.addProperty("RpsTicket", "d=" + microsoftToken);
        JsonObject xblPayload = new JsonObject();
        xblPayload.add("Properties", xblProperties);
        xblPayload.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblPayload.addProperty("TokenType", "JWT");

        JsonObject xbl = Http.postJson(XBL_URL, xblPayload);
        String xblToken = optString(xbl, "Token");
        if (xblToken == null) throw new IOException("Xbox Live rejected the token: " + describeError(xbl));

        JsonObject xstsPayload = new JsonObject();
        xstsPayload.add("Properties", buildXstsProperties(xblToken));
        xstsPayload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsPayload.addProperty("TokenType", "JWT");

        JsonObject xsts = Http.postJson(XSTS_URL, xstsPayload);
        String xstsToken = optString(xsts, "Token");
        if (xstsToken == null) {
            throw new IOException(explainXstsFailure(xsts));
        }

        String userHash = null;
        JsonObject displayClaims = xsts.getAsJsonObject("DisplayClaims");
        if (displayClaims != null && displayClaims.has("xui")) {
            JsonArray xui = displayClaims.getAsJsonArray("xui");
            if (xui != null && xui.size() > 0) {
                userHash = optString(xui.get(0).getAsJsonObject(), "uhs");
            }
        }
        if (userHash == null) throw new IOException("XSTS response has no user hash");

        if (listener != null) listener.status("Logging into Minecraft services...");

        JsonObject loginPayload = new JsonObject();
        loginPayload.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject login = Http.postJson(MC_LOGIN_URL, loginPayload);
        String minecraftToken = optString(login, "access_token");
        if (minecraftToken == null) {
            throw new IOException("Minecraft login failed: " + describeError(login));
        }

        if (listener != null) listener.status("Reading the Minecraft profile...");

        JsonObject profile = Http.getJsonWithBearer(MC_PROFILE_URL, minecraftToken);
        String name = optString(profile, "name");
        String uuid = optString(profile, "id");
        if (name == null || uuid == null) {
            throw new IOException("this account does not own Minecraft");
        }

        Account account = new Account();
        account.username = name;
        account.uuid = uuid.replace("-", "");
        account.accessToken = minecraftToken;
        account.userType = "msa";
        account.kind = "microsoft";
        account.refreshToken = refreshToken;
        account.expiresAt = System.currentTimeMillis() + 60L * 60L * 1000L;
        return account.validated();
    }

    private static JsonObject buildXstsProperties(String xblToken) {
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        properties.add("UserTokens", tokens);
        return properties;
    }

    private static String explainXstsFailure(JsonObject response) {
        String xerr = optString(response, "XErr");
        if (xerr == null) return "Xbox authorization failed: " + describeError(response);
        return switch (xerr) {
            case "2148916233" -> "This Microsoft account has no Xbox profile. Sign in on xbox.com once, then retry.";
            case "2148916235" -> "Xbox Live is not available in this account's country.";
            case "2148916236", "2148916237" -> "This account needs adult verification on xbox.com.";
            case "2148916238" -> "This is a child account and needs to be added to a family first.";
            default -> "Xbox authorization failed (XErr " + xerr + ")";
        };
    }

    // -- Small helpers ------------------------------------------------------

    public interface StatusListener {
        void status(String text);
    }

    private static String describeError(JsonObject json) {
        String error = optString(json, "error");
        String description = optString(json, "error_description");
        String message = optString(json, "Message");
        if (error != null && description != null) return error + ": " + description;
        if (error != null) return error;
        if (message != null) return message;
        return "unknown error";
    }

    private static String optString(JsonObject json, String key) {
        try {
            if (json == null || !json.has(key) || json.get(key).isJsonNull()) return null;
            return json.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static int optInt(JsonObject json, String key, int fallback) {
        try {
            if (json == null || !json.has(key) || json.get(key).isJsonNull()) return fallback;
            return json.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
