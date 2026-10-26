package com.vibe.client.cosmetics

import com.vibe.client.config.ConfigManager

/**
 * Cosmetic preferences persisted through the normal client config, so a game
 * started without the launcher keeps its cape. The launcher's own property
 * wins when both are present; see [CosmeticsManager.init].
 */
object CosmeticsConfig {

    var equipped: String?
        get() = ConfigManager.current.equippedCosmetic?.takeIf { it.isNotBlank() }
        set(value) {
            ConfigManager.current.equippedCosmetic = value ?: ""
            ConfigManager.requestSave()
        }
}
