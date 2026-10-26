package com.vibe.client

import com.vibe.client.binds.BindManager
import com.vibe.client.config.ConfigManager
import com.vibe.client.feature.BlockOverlay
import com.vibe.client.feature.ChunkBorders
import com.vibe.client.feature.HitColor
import com.vibe.client.feature.MotionBlur
import com.vibe.client.config.Env
import com.vibe.client.hud.ClientHooks
import com.vibe.client.hud.GameEvents
import com.vibe.client.hud.HudRenderer
import com.vibe.client.input.KeyInputHandler
import com.vibe.client.lang.Lang
import com.vibe.client.module.ModuleManager
import com.vibe.client.plugin.PluginManager
import com.vibe.client.store.ModsManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Client entrypoint.
 *
 * Keep this class tiny: it only wires things up.
 * All real logic lives in modules / managers / hooks.
 */
object VibeClient : ClientModInitializer {

    const val MOD_ID = "vibeclient"

    /** Client build version, shown in the ClickGUI sidebar. */
    const val version: String = "1.0"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    /**
     * The exact Minecraft version this mod is running inside, read from the
     * game itself (e.g. "1.21.1"). Used by the mod store so we never offer a
     * build the user cannot run.
     */
    @JvmStatic
    val mcVersion: String
        get() = SharedConstants.getGameVersion().name

    override fun onInitializeClient() {
        logger.info("Vibe Client initializing (MC $mcVersion)...")

        val gameDir: File = FabricLoader.getInstance().gameDir.toFile()

        // .env must be loaded before anything reads a token.
        Env.load(gameDir)

        // Files first: config + lang + mods dir, then everything that uses them.
        ConfigManager.load(File(gameDir, "config"))
        Lang.load()
        ModsManager.init(File(gameDir, "mods"))

        ModuleManager.registerAll()
        // Binds read the config, so they load after it and before the tick hook
        // that will execute them.
        BindManager.load()
        HudRenderer.register()
        ClientHooks.register()
        KeyInputHandler.register()

        // World-render features hook into fabric-api's render events; they must
        // be registered once, before the first frame.
        BlockOverlay.register()
        HitColor.register()
        ChunkBorders.register()
        MotionBlur.register()

        // Combat and session counters need real game events; without these
        // subscriptions those modules read zero forever.
        GameEvents.register()

        // Cosmetics read the launcher-provided property, so they init after
        // the config but before the first world render.
        com.vibe.client.cosmetics.CosmeticsManager.init()

        // Plugins load last: they may add HUD lines, so the HUD must exist.
        PluginManager.init(File(gameDir, "plugins"))

        logger.info("Vibe Client ready.")
    }
}