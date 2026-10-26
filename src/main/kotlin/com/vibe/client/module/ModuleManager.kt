package com.vibe.client.module

import com.vibe.client.module.impl.ArmorStatusModule
import com.vibe.client.module.impl.BiomeModule
import com.vibe.client.module.impl.ClockModule
import com.vibe.client.module.impl.ComboModule
import com.vibe.client.module.impl.CoordinatesModule
import com.vibe.client.module.impl.CpsModule
import com.vibe.client.module.impl.FpsModule
import com.vibe.client.module.impl.HealthModule
import com.vibe.client.module.impl.HungerModule
import com.vibe.client.module.impl.KeystrokesModule
import com.vibe.client.module.impl.MemoryModule
import com.vibe.client.module.impl.PingModule
import com.vibe.client.module.impl.PotionModule
import com.vibe.client.module.impl.ReachDisplayModule
import com.vibe.client.module.impl.DirectionModule
import com.vibe.client.module.impl.ItemCounterModule
import com.vibe.client.module.impl.ServerAddressModule
import com.vibe.client.module.impl.SpeedModule
import com.vibe.client.module.impl.ChunkPosModule
import com.vibe.client.module.impl.DeathCoordsModule
import com.vibe.client.module.impl.DimensionModule
import com.vibe.client.module.impl.DurabilityWarnModule
import com.vibe.client.module.impl.EntityCountModule
import com.vibe.client.module.impl.FpsGraphModule
import com.vibe.client.module.impl.FpsStatsModule
import com.vibe.client.module.impl.InventorySpaceModule
import com.vibe.client.module.impl.LightLevelModule
import com.vibe.client.module.impl.LookAnglesModule
import com.vibe.client.module.impl.MemoryGraphModule
import com.vibe.client.module.impl.NetherCoordsModule
import com.vibe.client.module.impl.PingGraphModule
import com.vibe.client.module.impl.PlaytimeModule
import com.vibe.client.module.impl.ServerBrandModule
import com.vibe.client.module.impl.TargetInfoModule
import com.vibe.client.module.impl.WeatherModule
import com.vibe.client.module.impl.TotemCounterModule
import com.vibe.client.module.impl.TeamHpModule
import com.vibe.client.module.impl.MountStatsModule
import com.vibe.client.module.impl.SpawnSafetyModule
import com.vibe.client.module.impl.WaypointModule
import com.vibe.client.module.impl.TntTimerModule
import com.vibe.client.module.impl.ArmorPointsModule
import com.vibe.client.module.impl.AbsorptionModule
import com.vibe.client.module.impl.AirModule
import com.vibe.client.module.impl.FireTicksModule
import com.vibe.client.module.impl.HeldItemModule
import com.vibe.client.module.impl.OffhandModule
import com.vibe.client.module.impl.HotbarDurabilityModule
import com.vibe.client.module.impl.EnchantsModule
import com.vibe.client.module.impl.AttackChargeModule
import com.vibe.client.module.impl.BowChargeModule
import com.vibe.client.module.impl.ShieldModule
import com.vibe.client.module.impl.FrameTimeModule
import com.vibe.client.module.impl.GameTimeModule
import com.vibe.client.module.impl.MoonPhaseModule
import com.vibe.client.module.impl.DifficultyModule
import com.vibe.client.module.impl.SpawnDistanceModule
import com.vibe.client.module.impl.WorldBorderModule
import com.vibe.client.module.impl.SlipperinessModule
import com.vibe.client.module.impl.SolidGroundModule
import com.vibe.client.module.impl.ClimbingModule
import com.vibe.client.module.impl.InWaterModule
import com.vibe.client.module.impl.PortalCooldownModule
import com.vibe.client.module.impl.CanSleepModule
import com.vibe.client.module.impl.SprintStateModule
import com.vibe.client.module.impl.SneakStateModule
import com.vibe.client.module.impl.RegenModule
import com.vibe.client.module.impl.BiomeTempModule
import com.vibe.client.module.impl.VelocityModule
import com.vibe.client.module.impl.FallDistanceModule
import com.vibe.client.module.impl.XpToNextModule
import com.vibe.client.module.impl.HitsPerMinModule
import com.vibe.client.module.impl.DamageTakenModule
import com.vibe.client.module.impl.XpGainedModule
import com.vibe.client.module.impl.MinedPerHourModule
import com.vibe.client.module.impl.PlacedPerHourModule
import com.vibe.client.module.impl.DeathsTotalModule
import com.vibe.client.module.impl.StrongestEffectModule
import com.vibe.client.module.impl.PingJitterModule
import com.vibe.client.module.impl.TargetBlockModule
import com.vibe.client.module.impl.TargetHardnessModule
import com.vibe.client.module.impl.RedstoneModule
import com.vibe.client.module.impl.RenderDistanceModule
import com.vibe.client.module.impl.GuiScaleModule
import com.vibe.client.module.impl.BrightnessModule
import com.vibe.client.module.impl.WindowFocusModule
import com.vibe.client.module.impl.VolumeModule
import com.vibe.client.module.impl.WorldDayModule
import com.vibe.client.module.impl.XpModule
import com.vibe.client.module.impl.ToggleSneakModule
import com.vibe.client.module.impl.ToggleSprintModule
import com.vibe.client.safety.SafetyGuard
import com.vibe.client.feature.SessionStats

/**
 * Holds every registered module and renders them in order.
 * One place to add/remove modules.
 */
object ModuleManager {

    private val modules = mutableListOf<Module>()

    /** True while the PvP preset is active (clean fighting HUD). */
    @Volatile
    var pvpPreset: Boolean = false
        private set

    fun registerAll() {
        if (modules.isNotEmpty()) return
        // Order matters: it defines both the menu order and the default
        // vertical stacking of newly placed modules.
        modules += FpsModule
        modules += CoordinatesModule
        modules += CpsModule
        modules += HealthModule
        modules += HungerModule
        modules += PingModule
        modules += KeystrokesModule
        modules += ArmorStatusModule
        modules += PotionModule
        modules += MemoryModule
        modules += ClockModule
        modules += ToggleSprintModule
        modules += ToggleSneakModule
        modules += ComboModule
        modules += ReachDisplayModule
        modules += ServerAddressModule
        modules += SpeedModule
        modules += DirectionModule
        modules += ItemCounterModule
        modules += BiomeModule
        modules += SessionStats

        // -- World information (7) --------------------------------------
        modules += DimensionModule
        modules += LightLevelModule
        modules += ChunkPosModule
        modules += NetherCoordsModule
        modules += XpModule
        modules += WorldDayModule
        modules += WeatherModule

        // -- Graphs and session counters (12) --------------------------
        modules += FpsGraphModule
        modules += PingGraphModule
        modules += MemoryGraphModule
        modules += TargetInfoModule
        modules += LookAnglesModule
        modules += FpsStatsModule
        modules += PlaytimeModule
        modules += DeathCoordsModule
        modules += DurabilityWarnModule
        modules += InventorySpaceModule
        modules += EntityCountModule
        modules += ServerBrandModule

        // -- Survival and team (4), the Lunar/Badlion set -------------
        modules += TotemCounterModule
        modules += TeamHpModule
        modules += MountStatsModule
        modules += SpawnSafetyModule
        modules += WaypointModule
        modules += TntTimerModule

        // -- Player state (12), the Lunar widget set -------------------
        modules += ArmorPointsModule
        modules += AbsorptionModule
        modules += AirModule
        modules += FireTicksModule
        modules += HeldItemModule
        modules += OffhandModule
        modules += HotbarDurabilityModule
        modules += EnchantsModule
        modules += AttackChargeModule
        modules += BowChargeModule
        modules += ShieldModule
        modules += FrameTimeModule

        // -- World state (17) --------------------------------------------
        modules += GameTimeModule
        modules += MoonPhaseModule
        modules += DifficultyModule
        modules += SpawnDistanceModule
        modules += WorldBorderModule
        modules += SlipperinessModule
        modules += SolidGroundModule
        modules += ClimbingModule
        modules += InWaterModule
        modules += PortalCooldownModule
        modules += CanSleepModule
        modules += SprintStateModule
        modules += SneakStateModule
        modules += RegenModule
        modules += BiomeTempModule
        modules += VelocityModule
        modules += FallDistanceModule
        modules += XpToNextModule

        // -- Session and targets (11) -------------------------------------
        modules += HitsPerMinModule
        modules += DamageTakenModule
        modules += XpGainedModule
        modules += MinedPerHourModule
        modules += PlacedPerHourModule
        modules += DeathsTotalModule
        modules += StrongestEffectModule
        modules += PingJitterModule
        modules += TargetBlockModule
        modules += TargetHardnessModule
        modules += RedstoneModule

        // -- Client options (5) --------------------------------------------
        modules += RenderDistanceModule
        modules += GuiScaleModule
        modules += BrightnessModule
        modules += WindowFocusModule
        modules += VolumeModule
    }

    fun all(): List<Module> = modules

    fun byName(name: String): Module? = modules.firstOrNull { it.name == name }

    /** Applies (or lifts) the PvP preset without destroying the user's toggles. */
    fun togglePvpPreset() {
        pvpPreset = !pvpPreset
    }

    /** Used by ConfigManager when restoring the saved state. */
    fun setPvpPreset(value: Boolean) {
        pvpPreset = value
    }

    /** A module is drawn if it's enabled and not hidden by the PvP preset. */
    fun shouldDraw(module: Module): Boolean {
        if (!module.enabled) return false
        if (pvpPreset && !module.inPvpPreset) return false
        return true
    }

    /**
     * Draws every visible module.
     *
     * Each module is isolated: a broken one is caught by [SafetyGuard], logged,
     * and eventually switched off, while the rest of the HUD keeps rendering.
     * Without this a single bad module turns every frame into a crash.
     */
    fun render(ctx: RenderContext) {
        for (module in modules) {
            if (!shouldDraw(module)) continue
            SafetyGuard.guardModule(module, "render") { module.render(ctx) }
        }
    }
}
