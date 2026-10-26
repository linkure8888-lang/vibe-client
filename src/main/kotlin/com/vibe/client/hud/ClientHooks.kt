package com.vibe.client.hud

import com.vibe.client.binds.BindManager
import com.vibe.client.config.ConfigManager
import com.vibe.client.feature.Crosshair
import com.vibe.client.feature.SessionStats
import com.vibe.client.feature.SmoothSneak
import com.vibe.client.feature.WorldTweaks
import com.vibe.client.module.impl.ArmorStatusModule
import com.vibe.client.module.impl.BiomeModule
import com.vibe.client.module.impl.ClockModule
import com.vibe.client.module.impl.CoordinatesModule
import com.vibe.client.module.impl.CpsModule
import com.vibe.client.module.impl.KeystrokesModule
import com.vibe.client.module.impl.MemoryModule
import com.vibe.client.module.impl.PingModule
import com.vibe.client.module.impl.DirectionModule
import com.vibe.client.module.impl.HealthModule
import com.vibe.client.module.impl.HungerModule
import com.vibe.client.module.impl.ItemCounterModule
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
import com.vibe.client.module.impl.ServerAddressModule
import com.vibe.client.module.impl.SpeedModule
import com.vibe.client.module.impl.TargetInfoModule
import com.vibe.client.module.impl.WeatherModule
import com.vibe.client.module.impl.WorldDayModule
import com.vibe.client.module.impl.XpModule
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
import com.vibe.client.perf.FpsBoost
import com.vibe.client.perf.LowFpsProtection
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Direction

/**
 * Per-tick updates for every module.
 *
 * Runs 20x/sec. Design rule: all expensive work (string building, time
 * formatting, heap sampling) happens HERE or slower — never in the render path.
 */
object ClientHooks {

    private var ticks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player

            if (player != null) {
                updateCoordinates(player)
                updateKeystrokes(client)
                updateArmor(player.inventory.getArmorStack(3), player.inventory.getArmorStack(2),
                    player.inventory.getArmorStack(1), player.inventory.getArmorStack(0),
                    player.mainHandStack)
                updateBiome(client, player)
                updateVitals(player)
                // Session distance accumulates per tick, so it needs the
                // position every tick rather than the 1 Hz block below.
                SessionStats.tick(Triple(player.x, player.y, player.z))
                SpeedModule.tick(player.x, player.z)

                // -- New world/player modules (cheap, per tick) ----------
                updateWorldInfo(client, player)
                updateLookAngles(player)
                updateDurability(player)
                updateDeathWatch(player)
                WaypointModule.update(player.x, player.y, player.z, player.yaw)
                PlaytimeModule.tick()
                updatePlayerStateModules(player)
                updateWorldStateModules(client, player)
                updateTargetModules(client)
            } else {
                // Leaving the world must break the distance chain, or the next
                // world's first tick adds a bogus jump between dimensions.
                SessionStats.tick(null)
                SpeedModule.reset()
            }

            updateServerAddress(client)

            // Camera easing runs every tick so the crouch transition is smooth.
            SmoothSneak.tick(client)

            // Fullbright / locked time / clear weather are re-applied every tick
            // because the game keeps writing the vanilla values back.
            WorldTweaks.tick(client)

            // Takes effect (or lifts) the moment the menu toggle is flipped.
            FpsBoost.tick(client)

            // User bindings: fires on the press edge, never while a screen is open.
            BindManager.tick(client)

            // Largest cost per frame is the crosshair target query.
            Crosshair.tick(client)

            // Hit marker kill edge detection.
            com.vibe.client.feature.HitMarker.tick(client)

            // 1 Hz work: CPS window, ping, memory, config flush, low-FPS guard,
            // plus the client-option modules (plain settings reads).
            if (++ticks % 20 == 0) {
                CpsModule.tick()
                MemoryModule.sample()
                ConfigManager.tick()
                LowFpsProtection.tick(client)
                SessionStats.rebuild()

                // -- New 1 Hz work --------------------------------------
                // Graphs sample at 1 Hz: 90 points is then 90 seconds, which
                // is long enough to see a trend and short enough to react.
                val fps = FpsCounter.fps
                FpsGraphModule.sample(fps)
                FpsStatsModule.sample(fps)
                FpsStatsModule.rebuild()
                MemoryGraphModule.sample(MemoryModule.usedMegabytes())
                PlaytimeModule.rebuild()
                updateEntityCount(client)
                updateServerBrand(client)

                if (player != null) {
                    updatePing(client)
                    updateItemCounts(client, player)
                    updateInventorySpace(player)
                    PingGraphModule.sample(PingModule.pingMs)
                    updateTargetInfo(client)
                    updateTotems(player)
                    updateTeamHp(client, player)
                    updateMountStats(player)
                    updateSpawnSafety(client, player)
                    updateTntTimer(client)
                    updateClientOptionModules(client)
                    FrameTimeModule.update(fps)
                    PingJitterModule.sample(PingModule.pingMs)
                    HitsPerMinModule.rebuild(PlaytimeModule.seconds)
                    MinedPerHourModule.rebuild(PlaytimeModule.seconds)
                    PlacedPerHourModule.rebuild(PlaytimeModule.seconds)
                }
            }

            ClockModule.tick()
        }

        // Never leave the user's particles downgraded or the world at max
        // brightness after a session — that would outlive the feature.
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { LowFpsProtection.reset(MinecraftClient.getInstance()) }
            runCatching { WorldTweaks.reset(MinecraftClient.getInstance()) }
            runCatching { FpsBoost.reset(MinecraftClient.getInstance()) }
        })
    }

    /** Player-state modules; all plain field reads, per tick. */
    private fun updatePlayerStateModules(player: net.minecraft.client.network.ClientPlayerEntity) {
        ArmorPointsModule.update(player.armor, 0f)
        AbsorptionModule.update(player.absorptionAmount)
        AirModule.update(player.air)
        FireTicksModule.update(player.fireTicks)
        DamageTakenModule.update(player.health)

        val main = player.mainHandStack
        val maxDamage = main.maxDamage
        HeldItemModule.update(
            if (main.isEmpty) "--" else main.name.string,
            if (maxDamage > 0) ((maxDamage - main.damage) * 100) / maxDamage else -1
        )
        val off = player.offHandStack
        OffhandModule.update(if (off.isEmpty) "--" else off.name.string)

        var worst = -1
        var worstSlot = -1
        for (i in 0 until 9) {
            val stack = player.inventory.getStack(i)
            if (stack.isEmpty) continue
            val max = stack.maxDamage
            if (max <= 0) continue
            val percent = ((max - stack.damage) * 100) / max
            if (worst < 0 || percent < worst) {
                worst = percent
                worstSlot = i
            }
        }
        HotbarDurabilityModule.update(worst, worstSlot)

        var enchCount = 0
        var enchMax = 0
        if (!main.isEmpty) {
            val component = main.enchantments
            enchCount = component.size
            for (entry in component.enchantmentEntries) {
                val level = entry.intValue
                if (level > enchMax) enchMax = level
            }
        }
        EnchantsModule.update(enchCount, enchMax)

        val cooldown = (player.getAttackCooldownProgress(0f) * 100).toInt()
        AttackChargeModule.update(cooldown)

        val usingBow = player.activeItem.item is net.minecraft.item.BowItem &&
            player.isUsingItem
        BowChargeModule.update(if (usingBow) 0 else -1)
        ShieldModule.update(player.isBlocking)
    }

    /** World-state modules; one registry/world read each, per tick. */
    private fun updateWorldStateModules(
        client: MinecraftClient,
        player: net.minecraft.client.network.ClientPlayerEntity
    ) {
        val world = client.world ?: return
        GameTimeModule.update(world.timeOfDay)
        MoonPhaseModule.update(((world.timeOfDay / 24000L) % 8L + 8L).toInt() % 8)
        DifficultyModule.update(world.difficulty.getName())

        val spawn = world.spawnPos
        val spawnDist = kotlin.math.sqrt(
            (player.x - spawn.x) * (player.x - spawn.x) +
            (player.z - spawn.z) * (player.z - spawn.z)
        ).toInt()
        SpawnDistanceModule.update(spawnDist)

        val border = world.worldBorder
        val borderDist = border.getDistanceInsideBorder(player.x, player.z)
        WorldBorderModule.update(borderDist)

        val ground = world.getBlockState(player.blockPos.down())
        SlipperinessModule.update(ground.block.getSlipperiness())
        SolidGroundModule.update(player.isOnGround)
        ClimbingModule.update(player.isClimbing)
        InWaterModule.update(player.isTouchingWater)
        PortalCooldownModule.update(player.getPortalCooldown())

        val timeOfDay = (world.timeOfDay % 24000L).toInt()
        CanSleepModule.update(timeOfDay in 12542..23458 && !world.isThundering)
        SprintStateModule.update(player.isSprinting, player.hungerManager.foodLevel > 6)
        SneakStateModule.update(player.isSneaking)
        RegenModule.update(player.hungerManager.foodLevel >= 18)

        val biome = world.getBiome(player.blockPos)
        BiomeTempModule.update(biome.value().getTemperature())
        VelocityModule.update(player.velocity.x, player.velocity.y, player.velocity.z)
        FallDistanceModule.update(player.fallDistance)
        XpToNextModule.update(player.experienceLevel, player.experienceProgress)

        var strongest = "none"
        var strongestAmp = -1
        for (effect in player.getStatusEffects()) {
            val amp = effect.amplifier
            if (amp > strongestAmp) {
                strongestAmp = amp
                strongest = effect.effectType.value().toString().substringAfterLast('.') + " " + (amp + 1)
            }
        }
        StrongestEffectModule.update(strongest)
        XpGainedModule.update(player.totalExperience)
    }

    /** Target modules; raycast result reads, per tick. */
    private fun updateTargetModules(client: MinecraftClient) {
        val target = client.crosshairTarget ?: run {
            TargetBlockModule.update("--", -1)
            TargetHardnessModule.update(0f, true)
            RedstoneModule.update(-1)
            return
        }
        if (target.type != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            TargetBlockModule.update("--", -1)
            TargetHardnessModule.update(0f, true)
            RedstoneModule.update(-1)
            return
        }
        val hit = target as net.minecraft.util.hit.BlockHitResult
        val world = client.world ?: return
        val state = world.getBlockState(hit.blockPos)
        val name = state.block.translationKey.substringAfterLast('.')
        val light = world.getLightLevel(net.minecraft.world.LightType.BLOCK, hit.blockPos)
        TargetBlockModule.update(name, light)
        val hardness = state.getHardness(world, hit.blockPos)
        TargetHardnessModule.update(hardness, state.isToolRequired)
        val power = state.getWeakRedstonePower(world, hit.blockPos, net.minecraft.util.math.Direction.UP)
        RedstoneModule.update(power)
    }

    /** Client-option modules; settings reads, once per second. */
    private fun updateClientOptionModules(client: MinecraftClient) {
        RenderDistanceModule.update(client.options.viewDistance.value)
        GuiScaleModule.update(client.options.guiScale.value)
        BrightnessModule.update(client.options.gamma.value)
        VolumeModule.update(
            client.options.getSoundVolume(net.minecraft.sound.SoundCategory.MASTER).toDouble())
        WindowFocusModule.update(client.isWindowFocused)
    }

    private fun updateCoordinates(player: net.minecraft.client.network.ClientPlayerEntity) {
        // Yaw drives the compass readout; normalised inside the module.
        DirectionModule.setYaw(player.yaw)
        CoordinatesModule.xPos = player.x
        CoordinatesModule.yPos = player.y
        CoordinatesModule.zPos = player.z
        CoordinatesModule.facing = when (player.horizontalFacing) {
            Direction.NORTH -> "N (-Z)"
            Direction.SOUTH -> "S (+Z)"
            Direction.WEST -> "W (-X)"
            Direction.EAST -> "E (+X)"
            else -> "?"
        }
    }

    /** Health, absorption and hunger, refreshed every tick. */
    private fun updateVitals(player: net.minecraft.client.network.ClientPlayerEntity) {
        HealthModule.update(player.health, player.absorptionAmount)
        HungerModule.update(player.hungerManager.foodLevel, player.hungerManager.saturationLevel)
    }

    private fun updateKeystrokes(client: MinecraftClient) {
        val options = client.options
        KeystrokesModule.w = options.forwardKey.isPressed
        KeystrokesModule.a = options.leftKey.isPressed
        KeystrokesModule.s = options.backKey.isPressed
        KeystrokesModule.d = options.rightKey.isPressed
        KeystrokesModule.lmb = options.attackKey.isPressed
        KeystrokesModule.rmb = options.useKey.isPressed
        KeystrokesModule.space = options.jumpKey.isPressed
        KeystrokesModule.shift = options.sneakKey.isPressed
        // Advance the press animations once per tick, never per frame.
        KeystrokesModule.tick()
    }

    private fun updateArmor(
        helmet: ItemStack,
        chest: ItemStack,
        legs: ItemStack,
        boots: ItemStack,
        hand: ItemStack
    ) {
        ArmorStatusModule.setArmor(0, describe(helmet))
        ArmorStatusModule.setArmor(1, describe(chest))
        ArmorStatusModule.setArmor(2, describe(legs))
        ArmorStatusModule.setArmor(3, describe(boots))
        ArmorStatusModule.setArmor(4, describe(hand))
    }

    /** "current / max" for damageable items, "--" otherwise. */
    private fun describe(stack: ItemStack): String {
        if (stack.isEmpty) return "--"
        val max = stack.maxDamage
        if (max <= 0) return "--"
        return "${max - stack.damage} / $max"
    }

    private fun updatePing(client: MinecraftClient) {
        val uuid = client.player?.uuid ?: return
        val entry = client.networkHandler?.getPlayerListEntry(uuid) ?: return
        PingModule.pingMs = entry.latency
    }

    /**
     * Resolves the biome under the player. Registry lookups are not free, so
     * this runs on the tick hook and only stores the finished string.
     */
    private fun updateBiome(
        client: MinecraftClient,
        player: net.minecraft.client.network.ClientPlayerEntity
    ) {
        val world = client.world ?: return
        // getBiome returns a RegistryEntry; getKey() is an Optional because an
        // entry may not be bound to a key. Fall back to a plain placeholder.
        val key = world.getBiome(player.blockPos).key
        BiomeModule.setBiome(if (key.isPresent) key.get().value.path else "unknown")
    }

    /**
     * Counts arrows, golden apples and ender pearls across the whole inventory.
     *
     * Runs at 1 Hz: 36 stack lookups are cheap but pointless 20x per second,
     * and a counter that jitters while an item is being moved is worse than one
     * that lags by half a second.
     */
    private fun updateItemCounts(
        client: MinecraftClient,
        player: net.minecraft.client.network.ClientPlayerEntity
    ) {
        var arrows = 0
        var gaps = 0
        var pearls = 0
        val inventory = player.inventory
        // main + armor + offhand; the inventory size differs between game
        // versions, so iterate the collection rather than a fixed range.
        for (stack in inventory.main) {
            if (stack.isEmpty) continue
            val id = stack.item.toString()
            when {
                id.contains("arrow") -> arrows += stack.count
                id.contains("golden_apple") || id.contains("enchanted_golden_apple") ->
                    gaps += stack.count
                id.contains("ender_pearl") -> pearls += stack.count
            }
        }
        for (stack in inventory.offHand) {
            if (stack.isEmpty) continue
            val id = stack.item.toString()
            when {
                id.contains("arrow") -> arrows += stack.count
                id.contains("golden_apple") || id.contains("enchanted_golden_apple") ->
                    gaps += stack.count
                id.contains("ender_pearl") -> pearls += stack.count
            }
        }
        ItemCounterModule.update(arrows, gaps, pearls)
    }

    /** Shows the server address, or "Singleplayer" in a local world. */
    private fun updateServerAddress(client: MinecraftClient) {
        val entry = client.currentServerEntry
        val address = entry?.address ?: "Singleplayer"
        ServerAddressModule.setAddress(address)
    }

    // =====================================================================
    // New modules (world info + graphs). All run on the tick hook.
    // =====================================================================

    /** Dimension, light, chunk, nether coords, XP, weather and world day. */
    private fun updateWorldInfo(
        client: MinecraftClient,
        player: net.minecraft.client.network.ClientPlayerEntity
    ) {
        val world = client.world ?: return

        // Dimension id, e.g. "overworld" / "the_nether" / "the_end".
        val dimKey = world.registryKey.value.path
        DimensionModule.setDimension(dimKey)

        // Block light at the feet and at the head. BlockPos is mutable in
        // vanilla; a fresh one per tick is cheap and avoids sharing state.
        val feet = player.blockPos
        val head = feet.up()
        val feetLight = world.getLightLevel(net.minecraft.world.LightType.BLOCK, feet)
        val headLight = world.getLightLevel(net.minecraft.world.LightType.BLOCK, head)
        LightLevelModule.update(feetLight, headLight)

        // Chunk coordinates plus the offset inside the chunk.
        ChunkPosModule.update(
            player.chunkPos.x, player.chunkPos.z,
            ((player.x % 16) + 16).toInt() % 16,
            ((player.z % 16) + 16).toInt() % 16
        )

        // Portal maths: nether divides by 8, overworld multiplies by 8.
        val nether = dimKey == "the_nether"
        NetherCoordsModule.update(player.x, player.z, nether)

        // XP: level, progress to next level, and lifetime total.
        XpModule.update(player.experienceLevel, player.experienceProgress, player.totalExperience)

        // World time drives the day counter and the phase label.
        WorldDayModule.update(world.timeOfDay)

        // Weather is already client-side state; no packet needed.
        WeatherModule.update(world.isRaining, world.isThundering)
    }

    /** Exact yaw and pitch, rounded for display. */
    private fun updateLookAngles(player: net.minecraft.client.network.ClientPlayerEntity) {
        LookAnglesModule.update(player.yaw, player.pitch)
    }

    /**
     * Warns when a worn item is close to breaking.
     *
     * Scans the four armour slots plus both hands; the worst percentage wins.
     * A non-damageable item (block, food) is skipped rather than counted as
     * 100%, which would hide a nearly-broken pickaxe behind a stack of dirt.
     */
    private fun updateDurability(player: net.minecraft.client.network.ClientPlayerEntity) {
        var worst = -1
        var worstName = ""
        val inventory = player.inventory

        // No list is built here on purpose: this runs 20x per second and an
        // ArrayList per tick is exactly the kind of garbage the HUD path is
        // supposed to avoid. Six explicit slots instead.
        var slot = 0
        while (slot < 6) {
            val stack = when (slot) {
                0 -> inventory.getArmorStack(3)
                1 -> inventory.getArmorStack(2)
                2 -> inventory.getArmorStack(1)
                3 -> inventory.getArmorStack(0)
                4 -> player.mainHandStack
                else -> player.offHandStack
            }
            slot++

            if (stack.isEmpty) continue
            val max = stack.maxDamage
            if (max <= 0) continue
            val percent = ((max - stack.damage) * 100) / max
            if (worst < 0 || percent < worst) {
                worst = percent
                worstName = stack.name.string
            }
        }
        DurabilityWarnModule.update(worst, worstName)
    }

    /**
     * Loaded entities around the player.
     *
     * Counting is capped: a server that streams a thousand entities into a
     * farm is exactly when a per-tick full count would cost frames, so the
     * loop stops early and reports the cap. The number is a warning sign,
     * not an exact census.
     */
    private fun updateEntityCount(client: MinecraftClient) {
        val world = client.world ?: return
        var count = 0
        for (entity in world.entities) {
            if (entity === client.player) continue
            count++
            if (count >= ENTITY_COUNT_CAP) break
        }
        EntityCountModule.update(count)
    }

    /** Free slots in the 36-slot main inventory. */
    private fun updateInventorySpace(player: net.minecraft.client.network.ClientPlayerEntity) {
        var free = 0
        for (stack in player.inventory.main) if (stack.isEmpty) free++
        InventorySpaceModule.update(free)
    }

    /** Totems of undying across the whole inventory, at 1 Hz. */
    private fun updateTotems(player: net.minecraft.client.network.ClientPlayerEntity) {
        var totems = 0
        for (stack in player.inventory.main) {
            if (stack.isEmpty) continue
            if (stack.item.toString().contains("totem")) totems += stack.count
        }
        for (stack in player.inventory.offHand) {
            if (stack.isEmpty) continue
            if (stack.item.toString().contains("totem")) totems += stack.count
        }
        TotemCounterModule.update(totems)
    }

    /**
     * Health of teammates sharing the player's scoreboard team.
     *
     * Only names present in the tab list are considered - the client knows
     * nothing about teammates it cannot see, and inventing rows would be a
     * lie the user could act on.
     */
    private fun updateTeamHp(
        client: MinecraftClient,
        player: net.minecraft.client.network.ClientPlayerEntity
    ) {
        val handler = client.networkHandler ?: return
        val myTeam = player.scoreboardTeam?.name
        if (myTeam == null) {
            TeamHpModule.update(emptyList(), false)
            return
        }
        val rows = ArrayList<String>(4)
        var urgent = false
        for (entry in handler.playerList) {
            val team = entry.scoreboardTeam ?: continue
            if (team.name != myTeam) continue
            val name = entry.profile.name
            val other = client.world?.getPlayerByUuid(entry.profile.id) ?: continue
            if (other === player) continue
            val hp = other.health.toInt()
            if (hp in 1..6) urgent = true
            rows.add("$name $hp")
        }
        // Keep the plate short: four rows is readable at a glance, more is a
        // wall the player ignores in a fight.
        TeamHpModule.update(rows.take(4), urgent)
    }

    /** Species and stats of the entity being ridden, when there is one. */
    private fun updateMountStats(player: net.minecraft.client.network.ClientPlayerEntity) {
        val vehicle = player.vehicle as? net.minecraft.entity.LivingEntity
        if (vehicle == null) {
            MountStatsModule.update(emptyList())
            return
        }
        val species = vehicle.type.translationKey.substringAfterLast('.')
        val rows = ArrayList<String>(2)
        rows.add("Riding: $species")
        // Jump strength and movement speed are protected fields on the horse
        // base class; reaching into them needs an accessor mixin, which is
        // not worth it for one HUD line. Health and species are public and
        // honest, so that is what the module shows.
        rows.add("HP: ${vehicle.health.toInt()}")
        MountStatsModule.update(rows)
    }

    /** Shortest primed-TNT fuse in the world, at 1 Hz. */
    private fun updateTntTimer(client: MinecraftClient) {
        val world = client.world ?: return
        var shortest = -1
        var count = 0
        for (entity in world.entities) {
            if (entity !is net.minecraft.entity.TntEntity) continue
            count++
            val fuse = entity.fuse
            if (shortest < 0 || fuse < shortest) shortest = fuse
        }
        TntTimerModule.update(shortest, count)
    }

    /** Spawn-safety of the block under the player's feet, at 1 Hz. */
    private fun updateSpawnSafety(
        client: MinecraftClient,
        player: net.minecraft.client.network.ClientPlayerEntity
    ) {
        val world = client.world ?: return
        val feet = player.blockPos
        val blockLight = world.getLightLevel(net.minecraft.world.LightType.BLOCK, feet)
        val skyLight = world.getLightLevel(net.minecraft.world.LightType.SKY, feet)
        val time = world.timeOfDay % 24000
        val isDay = time in 0..12999
        SpawnSafetyModule.update(blockLight, skyLight, isDay)
    }

    /**
     * What the crosshair is on: a block id or an entity with its health.
     *
     * Uses the game's own raycast result, so it respects reach, fluids and
     * spectator mode without a second raycast per tick.
     */
    private fun updateTargetInfo(client: MinecraftClient) {
        val target = client.crosshairTarget
        if (target == null) {
            TargetInfoModule.update("Target: --")
            return
        }
        when (target.type) {
            net.minecraft.util.hit.HitResult.Type.BLOCK -> {
                val hit = target as net.minecraft.util.hit.BlockHitResult
                val world = client.world
                val state = world?.getBlockState(hit.blockPos)
                val name = state?.block?.translationKey?.substringAfterLast('.') ?: "block"
                TargetInfoModule.update("Block: $name")
            }
            net.minecraft.util.hit.HitResult.Type.ENTITY -> {
                val hit = target as net.minecraft.util.hit.EntityHitResult
                val entity = hit.entity
                val name = entity.type.translationKey.substringAfterLast('.')
                // Only LivingEntity has health; a minecart or item frame does
                // not, and casting blindly would throw on every lookup.
                val health = (entity as? net.minecraft.entity.LivingEntity)?.health
                TargetInfoModule.update(
                    if (health == null) "Entity: $name"
                    else "Entity: $name ${health.toInt()}hp"
                )
            }
            else -> TargetInfoModule.update("Target: --")
        }
    }

    /** Server brand string, e.g. "vanilla" or "Paper". */
    private fun updateServerBrand(client: MinecraftClient) {
        val handler = client.networkHandler ?: return
        val brand = runCatching { handler.brand }.getOrNull() ?: "?"
        ServerBrandModule.update(brand)
    }

    /**
     * Watches the death edge and records where it happened.
     *
     * The position must be captured BEFORE the respawn teleports the player,
     * which is why this is a per-tick edge detector rather than a respawn
     * callback: by the time a respawn event fires the coordinates are already
     * the spawn point, which is the one thing the player does not need.
     */
    private fun updateDeathWatch(player: net.minecraft.client.network.ClientPlayerEntity) {
        val dead = player.health <= 0f
        if (dead && !wasDead) {
            DeathCoordsModule.record(player.x, player.y, player.z)
            SessionStats.onDeath()
            DeathsTotalModule.onDeath()
        }
        wasDead = dead
    }

    /** Previous-tick death state, so the event fires once per death. */
    private var wasDead = false

    private const val ENTITY_COUNT_CAP = 500
}