package com.vibe.client.config

/**
 * The serialized shape of `config/vibeclient.json`.
 *
 * Kept as a dumb data class on purpose: no logic here, so a corrupt file can
 * never crash the client — ConfigManager replaces bad values with defaults.
 */
data class ClientConfig(
    var language: String = "en",
    var pvpPreset: Boolean = false,
    var lowFpsProtection: Boolean = true,

    // -- Badlion/Lunar-style features --
    /** Camera eases down when crouching instead of snapping. */
    var smoothSneak: Boolean = true,
    /** Turns the whole world to full brightness, no potion needed. */
    var fullbright: Boolean = false,
    var zoomSmoothness: Double = 0.3,
    var crosshairPreset: Int = 0,
    var crosshairSize: Int = 6,
    var crosshairThickness: Int = 1,
    var crosshairColor: Int = 0xFFFFFFFF.toInt(),
    var crosshairEntityTint: Boolean = true,
    var nametagCustomizer: Boolean = false,
    var nametagShowHp: Boolean = true,
    var nametagColor: Int = 0xFFFFFFFF.toInt(),
    var nametagScale: Float = 1.0f,
    var nametagThroughWalls: Boolean = true,
    var bossbarCustomizer: Boolean = false,
    var bossbarHide: Boolean = false,
    var bossbarScale: Float = 1.0f,
    var bossbarOpacity: Float = 1.0f,
    /** Locks the client-side time of day; -1 means "leave it alone". */
    var lockedTime: Int = -1,
    /** Stops rain and snow client-side. */
    var clearWeather: Boolean = false,
    /** Hold-to-zoom key multiplier; 1.0 disables zoom. */
    var zoomFactor: Double = 4.0,
    /** Fades the world in/out around the GUI for a softer menu. */
    var guiBlur: Boolean = true,
    /**
     * Aggressive, reversible frame-rate optimisation: fast clouds, no entity
     * shadows, no biome blend, reduced particles, no view bobbing.
     */
    var fpsBoost: Boolean = true,
    /** Re-colours the crosshair based on what the player is aiming at. */
    var customCrosshair: Boolean = true,

    /** Hold-to-look-around without changing movement direction. */
    var freeLook: Boolean = false,

    /** Custom outline on the block being looked at. */
    var blockOverlay: Boolean = true,

    /** Grid lines at every chunk boundary around the player. */
    var chunkBorders: Boolean = false,

    /** Frame-accumulation motion blur; 0 strength disables it. */
    var motionBlur: Boolean = false,
    /** 0..0.9 - how strongly the previous frame ghosts over the current one. */
    var motionBlurStrength: Float = 0.35f,

    /** Crosshair hit markers on attacks that land. */
    var hitMarker: Boolean = true,
    /** Plays a pling when a watched target dies. */
    var killSounds: Boolean = false,
    /** ARGB colour of the block overlay. Default: white, fully opaque. */
    var blockOverlayColor: Int = 0xFFFFFFFF.toInt(),

    /** Coloured box around entities that have just taken damage. */
    var hitColor: Boolean = false,
    /** ARGB colour of the hit indicator. Default: red. */
    var hitColorValue: Int = 0xFFFF3030.toInt(),

    /** Replaces the vanilla title screen with the Vibe panorama + logo. */
    var customMainMenu: Boolean = true,

    /** Stops the camera tilting when the player takes damage. */
    var noHurtCam: Boolean = false,
    /** Stops the walking view bob. */
    var noViewBob: Boolean = false,
    /** Hides the first-person hand. */
    var hideHand: Boolean = false,

    /** The equipped cosmetic id from the Cosmetics store; blank = none. */
    var equippedCosmetic: String = "",

    /** User-defined key bindings; see com.vibe.client.binds.BindManager. */
    var binds: MutableList<com.vibe.client.binds.Bind> = ArrayList(),

    var modules: MutableMap<String, ModuleState> = LinkedHashMap()
)

data class ModuleState(
    var enabled: Boolean = true,
    var x: Float = 4f,
    var y: Float = 4f
)