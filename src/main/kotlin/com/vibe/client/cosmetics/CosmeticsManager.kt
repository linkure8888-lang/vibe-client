package com.vibe.client.cosmetics

import com.vibe.client.VibeClient

/**
 * Local cosmetics: capes drawn by this client, from the same catalogue the
 * launcher's Cosmetics store shows.
 *
 * There is no cosmetics server. The equipped id arrives as a JVM property
 * from the launcher (-Dvibeclient.cosmetic=...) or from vibeclient.json when
 * the game is started without the launcher; every other player is assumed
 * to wear nothing. That is the honest scope for now: our own back, our own
 * art, rendered locally by [VibeCapeTextureRegistry] + CapeTextureMixin.
 */
object CosmeticsManager {

    /** The equipped cosmetic id, or null when nothing is worn. */
    @Volatile
    private var equippedId: String? = null

    /** One cosmetic recipe; mirrors launcher StoreScreen.Cosmetic exactly. */
    data class Cosmetic(
        val id: String,
        val name: String,
        val base: Int,
        val accent: Int,
        val pattern: Pattern
    )

    enum class Pattern { NONE, STAR, STREAK, GRID, WAVE }

    /** The same catalogue, in the same order, as the launcher store. */
    val catalogue: List<Cosmetic> = listOf(
        Cosmetic("vibe-classic", "Vibe Classic", 0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt(), Pattern.STREAK),
        Cosmetic("obsidian-dusk", "Obsidian Dusk", 0xFF3B4266.toInt(), 0xFF8B5CF6.toInt(), Pattern.WAVE),
        Cosmetic("aurora-bloom", "Aurora Bloom", 0xFF22D3EE.toInt(), 0xFFF472B6.toInt(), Pattern.WAVE),
        Cosmetic("nova-flare", "Nova Flare", 0xFFF472B6.toInt(), 0xFFFFD166.toInt(), Pattern.STAR),
        Cosmetic("starfield", "Starfield", 0xFF101326.toInt(), 0xFFE7E9FF.toInt(), Pattern.STAR),
        Cosmetic("signal-grid", "Signal Grid", 0xFF1B2A4A.toInt(), 0xFF22D3EE.toInt(), Pattern.GRID),
        Cosmetic("vibe-aurora", "Vibe Aurora", 0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt(), Pattern.WAVE),
        Cosmetic("solar-crown", "Solar Crown", 0xFFF59E0B.toInt(), 0xFFF472B6.toInt(), Pattern.STAR)
    )

    fun byId(id: String?): Cosmetic? =
        id?.let { wanted -> catalogue.firstOrNull { it.id == wanted } }

    /** The equipped cosmetic for the local player, or null. */
    val equipped: Cosmetic?
        get() = byId(equippedId)

    /** Called once at client init, after config is loaded. */
    fun init() {
        // Launcher first: the property reflects the freshest choice made in
        // the store screen. Config second: lets a launcher-less start keep
        // its cape across sessions.
        val fromProperty = System.getProperty("vibeclient.cosmetic")
        equippedId = when {
            !fromProperty.isNullOrBlank() -> fromProperty
            else -> CosmeticsConfig.equipped
        }
        VibeClient.logger.info(
            "Cosmetics: equipped={}",
            equipped?.name ?: "none"
        )
    }

    /** The id currently worn; used by the ClickGUI cosmetics section. */
    fun equippedIdOrNull(): String? = equippedId

    /** Equips (or unequips with null) at runtime, from the ClickGUI. */
    fun equip(id: String?) {
        equippedId = id?.takeIf { it.isNotBlank() }
        CosmeticsConfig.equipped = equippedId
    }
}
