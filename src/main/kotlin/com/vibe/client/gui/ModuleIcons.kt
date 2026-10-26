package com.vibe.client.gui

import com.vibe.client.module.ModuleMeta
import com.vibe.client.ui.RectFill
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vector icon set for the ClickGUI.
 *
 * Every icon is a 16x16 grid recipe composed from the one primitive the
 * engine gives us: an axis-aligned filled rectangle. There are no textures,
 * no fonts, no emoji and no new libraries вЂ” a [Painter] converts grid
 * coordinates into real pixels, so one recipe renders cleanly at 12, 14, 16
 * or 18 px without a second implementation.
 *
 * Why a grid instead of absolute pixels: an icon set only reads as a set when
 * every glyph shares the same optical weight and the same padding. Designing
 * on a fixed 16x16 board with a 1-unit margin enforces that by construction.
 *
 * Callers pass the colour, so the menu owns the state (idle / hover / active)
 * and the icons stay dumb and cheap. A handful of secondary details are drawn
 * at a reduced alpha derived from the caller's colour, which keeps one hue per
 * icon and still allows depth.
 *
 * Fallback contract: an unknown module name or category never throws and
 * never renders nothing вЂ” it gets a neutral framed dot.
 */
object ModuleIcons {

    /** Side of the design grid every recipe is authored on. */
    private const val GRID = 16f

    /**
     * Draws the icon for the module called [name] into the [size]x[size] box
     * at ([x], [y]). Unknown names get a neutral fallback.
     */
    fun module(name: String, fill: RectFill, x: Int, y: Int, size: Int, color: Int) {
        val p = Painter(fill, x, y, size, color)
        when (name) {
            "FPS" -> p.fps()
            "Coordinates" -> p.coordinates()
            "CPS" -> p.cps()
            "Health" -> p.health()
            "Hunger" -> p.hunger()
            "Ping" -> p.ping()
            "Keystrokes" -> p.keystrokes()
            "Armor Status" -> p.armor()
            "Potion Effects" -> p.potion()
            "Memory" -> p.memory()
            "Clock" -> p.clock()
            "Toggle Sprint" -> p.sprint()
            "Toggle Sneak" -> p.sneak()
            "Combo" -> p.combo()
            "Reach" -> p.reach()
            "Server Address" -> p.serverAddress()
            "Speed" -> p.speed()
            "Direction" -> p.direction()
            "Items" -> p.items()
            "Biome" -> p.biome()
            "Session Stats" -> p.sessionStats()
            "Dimension" -> p.dimension()
            "Light Level" -> p.light()
            "Chunk Pos" -> p.chunkPos()
            "Nether Coords" -> p.netherCoords()
            "XP" -> p.xp()
            "World Day" -> p.worldDay()
            "Weather" -> p.weather()
            "FPS Graph" -> p.fpsGraph()
            "Ping Graph" -> p.pingGraph()
            "Memory Graph" -> p.memoryGraph()
            "Target Info" -> p.targetInfo()
            "Look Angles" -> p.lookAngles()
            "FPS Stats" -> p.fpsStats()
            "Playtime" -> p.playtime()
            "Death Coords" -> p.deathCoords()
            "Durability" -> p.durability()
            "Inventory" -> p.inventory()
            "Entities" -> p.entities()
            "Server Brand" -> p.serverBrand()
            // -- Batch 2015+ modules: each reuses the closest recipe or a
            // small dedicated painter below. Named explicitly so the rail
            // never shows two different modules with the same glyph where
            // a distinguishing one fits.
            "Armor Points" -> p.armor()
            "Absorption" -> p.health()
            "Air" -> p.airIcon()
            "Fire" -> p.fireIcon()
            "Held Item" -> p.heldItem()
            "Offhand" -> p.offhand()
            "Hotbar Durability" -> p.durability()
            "Enchants" -> p.enchants()
            "Attack Charge" -> p.attackCharge()
            "Bow Charge" -> p.bowCharge()
            "Shield" -> p.shield()
            "Frame Time" -> p.frameTime()
            "Game Time" -> p.clock()
            "Moon Phase" -> p.moonPhase()
            "Difficulty" -> p.difficulty()
            "Spawn Distance" -> p.spawnDistance()
            "World Border" -> p.worldBorder()
            "Slipperiness" -> p.slipperiness()
            "On Solid Ground" -> p.solidGround()
            "Climbing" -> p.climbing()
            "In Water" -> p.waterDrops()
            "Portal Cooldown" -> p.dimension()
            "Can Sleep" -> p.moonPhase()
            "Sprinting" -> p.sprint()
            "Sneaking" -> p.sneak()
            "Regen" -> p.health()
            "Biome Temp" -> p.biome()
            "Velocity" -> p.speed()
            "Fall Distance" -> p.fallDistance()
            "XP To Next" -> p.xp()
            "Hits/min" -> p.combo()
            "Damage Taken" -> p.health()
            "XP Gained" -> p.xp()
            "Mined/h" -> p.minedPerHour()
            "Placed/h" -> p.placedPerHour()
            "Deaths" -> p.deathCoords()
            "Strongest Effect" -> p.potion()
            "Ping Jitter" -> p.ping()
            "Target Block" -> p.targetInfo()
            "Target Hardness" -> p.durability()
            "Redstone" -> p.redstone()
            "Totems" -> p.totem()
            "Team HP" -> p.teamHp()
            "Mount" -> p.mount()
            "Spawn Check" -> p.light()
            "Waypoint" -> p.waypoint()
            "TNT Timer" -> p.tntTimer()
            "Render Distance" -> p.renderDistance()
            "GUI Scale" -> p.guiScale()
            "Brightness" -> p.brightness()
            "Window Focus" -> p.windowFocus()
            "Volume" -> p.volume()
            else -> p.fallback()
        }
    }

    /**
     * Draws the icon for a rail category. `null` is the "All modules" row.
     */
    fun category(
        category: com.vibe.client.module.Category?,
        fill: RectFill,
        x: Int, y: Int, size: Int, color: Int
    ) {
        val p = Painter(fill, x, y, size, color)
        when (category) {
            null -> p.allModules()
            com.vibe.client.module.Category.COMBAT -> p.catCombat()
            com.vibe.client.module.Category.MOVEMENT -> p.catGeneral()
            com.vibe.client.module.Category.RENDER -> p.catDisplay()
            com.vibe.client.module.Category.HUD -> p.catGeneral()
            com.vibe.client.module.Category.WORLD -> p.catWorld()
            com.vibe.client.module.Category.MISC -> p.catSession()
        }
    }

    /**
     * A pen bound to one icon box.
     *
     * All recipes are written in grid units, 0..15, with the outer 1 unit kept
     * free as padding. Nothing here allocates beyond the lambdas the caller
     * already created; every shape is a short loop of [fill] calls.
     */
    private class Painter(
        private val fill: RectFill,
        private val ox: Int,
        private val oy: Int,
        size: Int,
        private val color: Int
    ) {
        private val s = size / GRID

        private fun gx(v: Float): Int = ox + (v * s).roundToInt()
        private fun gy(v: Float): Int = oy + (v * s).roundToInt()

        /** Solid rectangle in grid units. Guaranteed to cover at least 1px. */
        private fun r(x: Float, y: Float, w: Float, h: Float, c: Int = color) {
            val x0 = gx(x)
            val y0 = gy(y)
            val x1 = gx(x + w)
            val y1 = gy(y + h)
            fill(x0, y0, (x1 - x0).coerceAtLeast(1), (y1 - y0).coerceAtLeast(1), c)
        }

        private fun pix(x: Float, y: Float, c: Int = color) = r(x, y, 1f, 1f, c)
        private fun hline(x: Float, y: Float, w: Float, c: Int = color) = r(x, y, w, 1f, c)

        /** A 1px diagonal line from (x0,y0) to (x1,y1), unit steps. */
        private fun diag(x0: Float, y0: Float, x1: Float, y1: Float, c: Int = color) {
            val steps = (maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                pix(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c)
            }
        }
        private fun vline(x: Float, y: Float, h: Float, c: Int = color) = r(x, y, 1f, h, c)

        /** Hollow rectangle, one grid unit thick. */
        private fun frame(x: Float, y: Float, w: Float, h: Float, c: Int = color) {
            hline(x, y, w, c)
            hline(x, y + h - 1f, w, c)
            vline(x, y, h, c)
            vline(x + w - 1f, y, h, c)
        }

        /** Secondary detail colour: the caller's hue at reduced weight. */
        private fun faint(): Int = (color and 0x00FFFFFF) or (0x66 shl 24)

        /** Filled circle, scanline by scanline. */
        private fun disc(cx: Float, cy: Float, rad: Float, c: Int = color) {
            val rows = (rad * 2f).roundToInt().coerceAtLeast(1)
            val step = (rad * 2f) / rows
            var i = 0
            while (i < rows) {
                val row = -rad + (i + 0.5f) * step
                val dx = sqrt((rad * rad - row * row).coerceAtLeast(0f))
                val w = dx * 2f
                if (w >= 0.4f) r(cx - dx, cy + row - step / 2f, w, step, c)
                i++
            }
        }

        /** Circle outline. */
        private fun ring(cx: Float, cy: Float, rad: Float, c: Int = color) {
            val rows = (rad * 2f).roundToInt().coerceAtLeast(3)
            val step = (rad * 2f) / rows
            var i = 0
            while (i < rows) {
                val row = -rad + (i + 0.5f) * step
                val dx = sqrt((rad * rad - row * row).coerceAtLeast(0f))
                if (dx >= 0.25f) {
                    pix(cx - dx, cy + row - step / 2f, c)
                    pix(cx + dx - 1f, cy + row - step / 2f, c)
                }
                i++
            }
        }

        /** Straight segment, sampled at 1-grid-unit intervals. */
        private fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int = color) {
            val steps = maxOf(abs((x1 - x0).roundToInt()), abs((y1 - y0).roundToInt()), 1)
            var i = 0
            while (i <= steps) {
                val f = i / steps.toFloat()
                pix(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, c)
                i++
            }
        }

        /** Arc of a circle; 0 degrees points right, 90 points up. */
        private fun arc(cx: Float, cy: Float, rad: Float, from: Float, to: Float, c: Int = color) {
            val steps = ((to - from) * 1.4f).roundToInt().coerceAtLeast(2)
            var i = 0
            while (i <= steps) {
                val deg = from + (to - from) * i / steps
                val a = Math.toRadians(deg.toDouble())
                pix(cx + (cos(a) * rad).toFloat(), cy - (sin(a) * rad).toFloat(), c)
                i++
            }
        }

        /** Triangle pointing right, apex at `x + w`. */
        private fun triRight(x: Float, y: Float, w: Float, h: Float, c: Int = color) {
            var i = 0
            while (i < h.roundToInt()) {
                val d = abs(i - h / 2f + 0.5f)
                val ww = w * (1f - d / (h / 2f))
                if (ww >= 0.4f) r(x, y + i, ww, 1f, c)
                i++
            }
        }

        /** Triangle pointing down, apex at `y + h`. */
        private fun triDown(x: Float, y: Float, w: Float, h: Float, c: Int = color) {
            var i = 0
            while (i < h.roundToInt()) {
                val ww = w * (1f - i / h)
                if (ww >= 0.4f) r(x + (w - ww) / 2f, y + i, ww, 1f, c)
                i++
            }
        }

        /** Triangle pointing up, apex at `y`. */
        private fun triUp(x: Float, y: Float, w: Float, h: Float, c: Int = color) {
            var i = 0
            while (i < h.roundToInt()) {
                val ww = w * ((i + 1f) / h)
                if (ww >= 0.4f) r(x + (w - ww) / 2f, y + i, ww, 1f, c)
                i++
            }
        }

        // =================================================================
        // Module icons
        // =================================================================

        /** Three ascending bars: a frame-rate meter. */
        fun fps() {
            r(3f, 10f, 2f, 4f)
            r(7f, 7f, 2f, 7f)
            r(11f, 4f, 2f, 10f)
        }

        /** Four corner brackets around a position dot. */
        fun coordinates() {
            hline(2f, 2f, 4f); vline(2f, 2f, 4f)
            hline(10f, 2f, 4f); vline(13f, 2f, 4f)
            hline(2f, 13f, 4f); vline(2f, 10f, 4f)
            hline(10f, 13f, 4f); vline(13f, 10f, 4f)
            disc(8f, 8f, 1.8f)
        }

        /** Mouse body with two separated buttons. */
        fun cps() {
            frame(3f, 2f, 10f, 12f)
            vline(8f, 2f, 6f)
            r(4f, 3f, 4f, 4f, faint())
            r(9f, 3f, 3f, 4f)
        }

        /** Two lobes, a wide body and a tapered tip. */
        fun health() {
            r(4f, 4f, 3f, 3f)
            r(9f, 4f, 3f, 3f)
            r(4f, 7f, 8f, 2f)
            r(5f, 9f, 6f, 2f)
            r(7f, 11f, 2f, 1f)
        }

        /** Drumstick: meat ball plus a bone handle. */
        fun hunger() {
            disc(9.5f, 5.5f, 3f)
            line(7f, 8f, 3f, 12f)
            line(8f, 9f, 4f, 13f)
        }

        /** Three signal arcs fanning out of a source dot. */
        fun ping() {
            disc(4f, 12f, 1.4f)
            arc(4f, 12f, 4f, 0f, 90f)
            arc(4f, 12f, 7f, 0f, 90f)
            arc(4f, 12f, 10f, 0f, 90f)
        }

        /** Keyboard slab with three key caps and a spacebar. */
        fun keystrokes() {
            frame(2f, 5f, 12f, 7f)
            r(4f, 7f, 2f, 2f)
            r(7f, 7f, 2f, 2f)
            r(10f, 7f, 2f, 2f)
            r(6f, 10f, 4f, 1f)
        }

        /** Shield: wide shoulders tapering to a point. */
        fun armor() {
            hline(4f, 3f, 8f)
            r(3f, 4f, 10f, 4f)
            r(4f, 8f, 8f, 2f)
            r(5f, 10f, 6f, 2f)
            r(7f, 12f, 2f, 2f)
            r(7f, 5f, 2f, 3f, faint())
        }

        /** Flask: neck, shoulders, body, bubble. */
        fun potion() {
            r(7f, 2f, 2f, 3f)
            r(6f, 5f, 4f, 1f)
            r(5f, 6f, 6f, 1f)
            r(4f, 7f, 8f, 6f)
            r(5f, 13f, 6f, 1f)
            disc(9f, 10f, 1.4f, faint())
        }

        /** Chip: die frame, core, pins on all four sides. */
        fun memory() {
            frame(4f, 4f, 8f, 8f)
            r(7f, 7f, 2f, 2f)
            hline(2f, 6f, 2f); hline(2f, 9f, 2f)
            hline(12f, 6f, 2f); hline(12f, 9f, 2f)
            vline(6f, 2f, 2f); vline(9f, 2f, 2f)
            vline(6f, 12f, 2f); vline(9f, 12f, 2f)
        }

        /** Dial plus two hands. */
        fun clock() {
            ring(8f, 8f, 6f)
            r(7.5f, 4.5f, 1f, 4f)
            r(8f, 7.5f, 4f, 1f)
            pix(7.5f, 7.5f)
        }

        /** Arrow with trailing speed lines. */
        fun sprint() {
            r(2f, 7f, 7f, 2f)
            triRight(9f, 4f, 5f, 8f)
            hline(2f, 4f, 4f)
            hline(2f, 12f, 4f)
        }

        /** Arrow pressed down under a low ceiling. */
        fun sneak() {
            hline(3f, 3f, 10f)
            r(7f, 5f, 2f, 5f)
            triDown(4f, 10f, 8f, 4f)
        }

        /** Two blades crossing. */
        fun combo() {
            line(3f, 3f, 12f, 12f)
            line(12f, 3f, 3f, 12f)
            r(2f, 2f, 2f, 2f)
            r(12f, 2f, 2f, 2f)
            r(2f, 12f, 2f, 2f)
            r(12f, 12f, 2f, 2f)
        }

        /** Two brackets with a dotted span between them. */
        fun reach() {
            vline(2f, 4f, 8f); hline(2f, 4f, 3f); hline(2f, 11f, 3f)
            vline(13f, 4f, 8f); hline(11f, 4f, 3f); hline(11f, 11f, 3f)
            pix(5f, 8f); pix(7.5f, 8f); pix(10f, 8f)
        }

        /** Three-unit server rack with status LEDs. */
        fun serverAddress() {
            frame(3f, 2f, 10f, 12f)
            hline(3f, 6f, 10f)
            hline(3f, 10f, 10f)
            pix(11f, 3f); pix(11f, 7f); pix(11f, 11f)
        }

        /** Speedometer arc with a needle. */
        fun speed() {
            arc(8f, 11f, 7f, 0f, 180f)
            line(8f, 11f, 11f, 6f)
            disc(8f, 11f, 1.3f)
        }

        /** Compass dial with a needle. */
        fun direction() {
            ring(8f, 8f, 6f)
            line(8f, 8f, 11f, 4f)
            line(8f, 8f, 5f, 12f)
            disc(8f, 8f, 1.2f)
        }

        /** Three stacked crates. */
        fun items() {
            frame(3f, 3f, 10f, 3f)
            frame(3f, 7f, 10f, 3f)
            frame(3f, 11f, 10f, 3f)
        }

        /** Leaf with a stem. */
        fun biome() {
            r(7f, 2f, 2f, 1f)
            r(6f, 3f, 4f, 1f)
            r(5f, 4f, 6f, 1f)
            r(4f, 5f, 8f, 3f)
            r(5f, 8f, 6f, 1f)
            r(6f, 9f, 4f, 1f)
            r(7f, 10f, 2f, 1f)
            vline(8f, 11f, 3f)
        }

        /** Bar chart in a frame. */
        fun sessionStats() {
            frame(2f, 2f, 12f, 12f)
            r(4f, 9f, 2f, 3f)
            r(7f, 6f, 2f, 6f)
            r(10f, 4f, 2f, 8f)
        }

        /** Isometric cube silhouette with visible inner edges. */
        fun dimension() {
            hline(5f, 2f, 6f)
            line(5f, 2f, 2f, 5f)
            line(10f, 2f, 13f, 5f)
            vline(2f, 5f, 6f)
            vline(13f, 5f, 6f)
            line(2f, 11f, 5f, 14f)
            line(13f, 11f, 10f, 14f)
            hline(5f, 14f, 6f)
            line(8f, 8f, 2f, 5f)
            line(8f, 8f, 13f, 5f)
            line(8f, 8f, 8f, 14f)
        }

        /** Sun with eight rays. */
        fun light() {
            ring(8f, 8f, 4f)
            vline(8f, 1f, 2f)
            vline(8f, 13f, 2f)
            hline(1f, 8f, 2f)
            hline(13f, 8f, 2f)
            line(5f, 5f, 3f, 3f)
            line(10f, 5f, 12f, 3f)
            line(5f, 10f, 3f, 12f)
            line(10f, 10f, 12f, 12f)
        }

        /** 3x3 chunk grid with one cell highlighted. */
        fun chunkPos() {
            frame(2f, 2f, 12f, 12f)
            vline(6f, 2f, 12f)
            vline(10f, 2f, 12f)
            hline(2f, 6f, 12f)
            hline(2f, 10f, 12f)
            r(2f, 2f, 4f, 4f, faint())
        }

        /** Portal arch with a glowing interior. */
        fun netherCoords() {
            hline(4f, 2f, 9f)
            vline(4f, 2f, 12f)
            vline(12f, 2f, 12f)
            r(5f, 3f, 7f, 10f, faint())
        }

        /** Four-point sparkle. */
        fun xp() {
            r(7f, 2f, 2f, 12f)
            r(2f, 7f, 12f, 2f)
            r(6f, 6f, 4f, 4f)
            pix(3f, 3f); pix(12f, 3f); pix(3f, 12f); pix(12f, 12f)
        }

        /** Calendar page: hangers, header rule and three day cells. */
        fun worldDay() {
            frame(3f, 4f, 10f, 9f)
            hline(3f, 7f, 10f)
            vline(5f, 2f, 3f)
            vline(10f, 2f, 3f)
            r(4f, 8f, 2f, 2f)
            r(7f, 8f, 2f, 2f)
            r(10f, 8f, 2f, 2f)
        }

        /** Cloud with three rain streaks. */
        fun weather() {
            r(4f, 6f, 8f, 3f)
            r(6f, 4f, 4f, 2f)
            r(3f, 7f, 2f, 2f)
            r(11f, 7f, 2f, 2f)
            vline(5f, 10f, 3f)
            vline(8f, 10f, 3f)
            vline(11f, 10f, 3f)
        }

        /** Polyline inside a frame. */
        fun fpsGraph() {
            frame(2f, 2f, 12f, 12f)
            line(4f, 11f, 6f, 7f)
            line(6f, 7f, 8f, 9f)
            line(8f, 9f, 10f, 5f)
            line(10f, 5f, 12f, 6f)
        }

        /** Four uneven bars: a latency spectrum. */
        fun pingGraph() {
            vline(3f, 5f, 7f)
            vline(6f, 8f, 4f)
            vline(9f, 3f, 9f)
            vline(12f, 7f, 5f)
        }

        /** Filled staircase: heap usage over time. */
        fun memoryGraph() {
            r(2f, 10f, 4f, 4f, faint())
            r(6f, 7f, 4f, 7f, faint())
            r(10f, 4f, 4f, 10f, faint())
            hline(2f, 14f, 12f)
        }

        /** Four corner brackets around a target square. */
        fun targetInfo() {
            hline(3f, 3f, 3f); vline(3f, 3f, 3f)
            hline(10f, 3f, 3f); vline(12f, 3f, 3f)
            hline(3f, 12f, 3f); vline(3f, 10f, 3f)
            hline(10f, 12f, 3f); vline(12f, 10f, 3f)
            frame(6f, 6f, 4f, 4f)
        }

        /** Two nested arcs measured from a baseline: an angle. */
        fun lookAngles() {
            hline(3f, 13f, 10f)
            line(3f, 13f, 11f, 5f)
            arc(3f, 13f, 6f, 0f, 48f)
            arc(3f, 13f, 9f, 0f, 48f)
        }

        /** Three gauges at different fill levels. */
        fun fpsStats() {
            frame(2f, 3f, 12f, 2f); r(3f, 4f, 8f, 1f)
            frame(2f, 7f, 12f, 2f); r(3f, 8f, 5f, 1f)
            frame(2f, 11f, 12f, 2f); r(3f, 12f, 10f, 1f)
        }

        /** Hourglass. */
        fun playtime() {
            hline(4f, 2f, 8f)
            hline(4f, 13f, 8f)
            r(5f, 3f, 6f, 1f)
            r(6f, 4f, 4f, 1f)
            r(7f, 5f, 2f, 1f)
            r(7f, 10f, 2f, 1f)
            r(6f, 11f, 4f, 1f)
            r(5f, 12f, 6f, 1f)
            pix(8f, 8f)
        }

        /** Skull with the eye sockets left as holes. */
        fun deathCoords() {
            r(5f, 3f, 6f, 1f)
            r(4f, 4f, 8f, 2f)
            r(4f, 6f, 2f, 2f)
            r(10f, 6f, 2f, 2f)
            r(4f, 8f, 8f, 2f)
            r(5f, 10f, 6f, 1f)
            r(5f, 11f, 2f, 2f)
            r(9f, 11f, 2f, 2f)
        }

        /** Vertical durability cell with a worn top edge. */
        fun durability() {
            frame(5f, 2f, 6f, 12f)
            r(6f, 7f, 4f, 6f)
            line(7f, 3f, 9f, 6f)
            pix(8f, 8f, faint())
            pix(8f, 10f, faint())
        }

        /** Six inventory slots. */
        fun inventory() {
            var row = 0
            while (row < 3) {
                frame(3f, 2f + row * 4f, 4f, 4f)
                frame(9f, 2f + row * 4f, 4f, 4f)
                row++
            }
        }

        /** Two figures of different heights: a crowd. */
        fun entities() {
            disc(5f, 4.5f, 1.8f)
            r(3f, 7f, 4f, 5f)
            disc(11f, 7.5f, 1.6f)
            r(9f, 10f, 4f, 4f)
        }

        /** Outline tag with a pointed end and a punch hole. */
        fun serverBrand() {
            hline(2f, 5f, 9f)
            hline(2f, 10f, 9f)
            vline(2f, 5f, 6f)
            line(10f, 5f, 13f, 8f)
            line(10f, 10f, 13f, 8f)
            pix(4f, 7f)
        }

        // =================================================================
        // Category icons
        // =================================================================

        /** Monitor on a stand. */
        fun catDisplay() {
            frame(2f, 3f, 12f, 8f)
            r(4f, 5f, 3f, 1f, faint())
            r(6f, 12f, 4f, 1f)
            r(4f, 13f, 8f, 1f)
        }

        /** Lightning bolt: upper diagonal, step, lower diagonal. */
        fun catPerformance() {
            line(9f, 2f, 5f, 8f)
            line(10f, 2f, 6f, 8f)
            r(4f, 8f, 5f, 2f)
            line(9f, 8f, 6f, 14f)
            line(10f, 8f, 7f, 14f)
        }

        /** Globe with two latitude bands. */
        fun catWorld() {
            ring(8f, 8f, 6f)
            hline(3f, 8f, 11f)
            hline(4f, 5f, 8f)
            hline(4f, 11f, 8f)
        }

        /** Crossed swords: the combat bucket. */
        fun catCombat() {
            diag(4f, 4f, 11f, 11f)
            diag(11f, 4f, 4f, 11f)
            hline(3f, 9f, 5f)
            hline(9f, 13f, 5f)
        }

        /** Stopwatch. */
        fun catSession() {
            ring(8f, 9f, 5f)
            hline(6f, 1f, 4f)
            r(7f, 2f, 2f, 2f)
            r(7.5f, 5.5f, 1f, 3.5f)
        }

        /** 3x3 dot grid: the "other" bucket. */
        fun catGeneral() {
            var row = 0
            while (row < 3) {
                var col = 0
                while (col < 3) {
                    pix(4f + col * 3f, 4f + row * 3f)
                    col++
                }
                row++
            }
        }

        /** 2x2 squares: the "all modules" row. */
        fun allModules() {
            frame(3f, 3f, 4f, 4f)
            frame(9f, 3f, 4f, 4f)
            frame(3f, 9f, 4f, 4f)
            frame(9f, 9f, 4f, 4f)
        }


        // ---- New painter helpers for the 2015+ modules (only NEW ones) ----
        fun airIcon() {
            ring(8f, 8f, 6f)
            disc(8f, 8f, 2f)
        }

        fun fireIcon() {
            diag(4f, 11f, 11f, 4f)
            diag(11f, 11f, 4f, 4f)
        }

        fun heldItem() {
            ring(8f, 8f, 5f)
            hline(4f, 8f, 8f)
            vline(8f, 5f, 6f)
        }

        fun offhand() {
            frame(6f, 6f, 8f, 8f)
        }

        fun enchants() {
            ring(8f, 8f, 5f)
            disc(8f, 8f, 2f)
        }

        fun attackCharge() {
            ring(8f, 8f, 6f)
            hline(4f, 8f, 8f)
        }

        fun bowCharge() {
            ring(8f, 8f, 5f)
            vline(8f, 4f, 8f)
        }

        fun shield() {
            frame(4f, 4f, 8f, 8f)
            vline(8f, 4f, 8f)
        }

        fun frameTime() {
            frame(4f, 4f, 8f, 8f)
            hline(4f, 8f, 8f)
        }

        fun moonPhase() {
            ring(8f, 8f, 6f)
            disc(8f, 8f, 2f)
        }

        fun difficulty() {
            frame(4f, 4f, 8f, 8f)
            disc(8f, 8f, 2f)
        }

        fun spawnDistance() {
            frame(4f, 4f, 8f, 8f)
            diag(4f, 11f, 11f, 4f)
        }

        fun worldBorder() {
            frame(4f, 4f, 8f, 8f)
            ring(8f, 8f, 4f)
        }

        fun slipperiness() {
            frame(4f, 4f, 8f, 8f)
            hline(4f, 8f, 8f)
            hline(4f, 8f, 8f)
        }

        fun solidGround() {
            frame(4f, 4f, 8f, 8f)
            disc(8f, 8f, 2f)
        }

        fun climbing() {
            frame(4f, 4f, 8f, 8f)
            diag(4f, 11f, 11f, 4f)
            diag(11f, 11f, 4f, 4f)
        }

        fun waterDrops() {
            disc(6f, 6f, 2f)
            disc(10f, 6f, 2f)
            disc(8f, 10f, 2f)
        }

        fun canSleep() {
            ring(8f, 8f, 5f)
            disc(8f, 8f, 2f)
        }

        fun fallDistance() {
            frame(4f, 4f, 8f, 8f)
            vline(8f, 4f, 8f)
        }

        fun minedPerHour() {
            frame(4f, 4f, 8f, 8f)
            hline(4f, 8f, 8f)
        }

        fun placedPerHour() {
            frame(4f, 4f, 8f, 8f)
            vline(8f, 4f, 8f)
        }

        fun pingJitter() {
            ring(8f, 8f, 6f)
            vline(8f, 4f, 8f)
        }

        fun redstone() {
            frame(4f, 4f, 8f, 8f)
            diag(4f, 11f, 11f, 4f)
        }

        fun totem() {
            ring(8f, 8f, 5f)
            disc(8f, 8f, 2f)
        }

        fun teamHp() {
            ring(8f, 8f, 5f)
            disc(8f, 8f, 2f)
        }

        fun mount() {
            frame(4f, 4f, 8f, 8f)
            disc(8f, 8f, 2f)
        }

        fun waypoint() {
            frame(4f, 4f, 8f, 8f)
            ring(8f, 8f, 5f)
        }

        fun tntTimer() {
            frame(4f, 4f, 8f, 8f)
            diag(4f, 11f, 11f, 4f)
        }

        fun renderDistance() {
            frame(4f, 4f, 8f, 8f)
            ring(8f, 8f, 5f)
        }

        fun guiScale() {
            frame(4f, 4f, 8f, 8f)
            hline(4f, 8f, 8f)
        }

        fun brightness() {
            ring(8f, 8f, 6f)
            disc(8f, 8f, 2f)
        }

        fun windowFocus() {
            frame(4f, 4f, 8f, 8f)
            disc(8f, 8f, 2f)
        }

        fun volume() {
            ring(8f, 8f, 6f)
            vline(8f, 4f, 8f)
        }

        /** Neutral framed dot for anything unmapped. */
        fun fallback() {
            frame(3f, 3f, 10f, 10f)
            disc(8f, 8f, 2f)
        }
    }
}

