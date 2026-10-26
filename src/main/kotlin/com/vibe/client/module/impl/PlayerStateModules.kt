package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object ArmorPointsModule : Module("Armor Points", Category.HUD, enabled = false, x = 4f, y = 4f) {
    private var armor = 0; private var tough = 0f
    fun update(armorVal: Int, toughVal: Float) { armor = armorVal; tough = toughVal }
    override fun getLines() = listOf("Armor: $armor  Tough: ${"%.1f".format(tough)}")
}

object AbsorptionModule : Module("Absorption", Category.HUD, enabled = false, x = 4f, y = 16f) {
    private var abs = 0f
    fun update(v: Float) { abs = v }
    override fun getLines() = if (abs > 0f) listOf("Absorb: ${"%.1f".format(abs)}") else emptyList()
}

object AirModule : Module("Air", Category.HUD, enabled = false, x = 4f, y = 28f) {
    private var air = 0; private var max = 300; private var inWater = false
    fun update(airVal: Int) { air = airVal; max = 300 }
    override fun getLines() = if (inWater) listOf("Air: $air/$max") else emptyList()
}

object FireTicksModule : Module("Fire", Category.HUD, enabled = false, x = 4f, y = 40f) {
    private var ticks = 0
    fun update(t: Int) { ticks = t }
    override fun getLines() = if (ticks > 0) listOf("Fire: ${ticks}t") else emptyList()
}

object HeldItemModule : Module("Held Item", Category.HUD, enabled = false, x = 4f, y = 52f) {
    private var itemName = "--"; private var durPct = -1
    fun update(name: String, pct: Int) { itemName = name; durPct = pct }
    override fun getLines(): List<String> {
        if (itemName == "--") return emptyList()
        return if (durPct >= 0) listOf("Hold: $itemName  $durPct%") else listOf("Hold: $itemName")
    }
}

object OffhandModule : Module("Offhand", Category.HUD, enabled = false, x = 4f, y = 64f) {
    private var itemName = "--"
    fun update(name: String) { itemName = name }
    override fun getLines() = if (itemName != "--") listOf("Off: $itemName") else emptyList()
}

object HotbarDurabilityModule : Module("Hotbar Durability", Category.HUD, enabled = false, x = 4f, y = 76f) {
    private var pct = -1; private var slot = -1
    fun update(percent: Int, slotIdx: Int) { pct = percent; slot = slotIdx }
    override fun getLines() = if (pct >= 0 && pct < 50) listOf("Hotbar: $pct% (slot ${slot+1})") else emptyList()
}

object EnchantsModule : Module("Enchants", Category.HUD, enabled = false, x = 4f, y = 88f) {
    private var count = 0; private var maxLvl = 0
    fun update(cnt: Int, max: Int) { count = cnt; maxLvl = max }
    override fun getLines() = if (count > 0) listOf("Enchants: $count (max $maxLvl)") else emptyList()
}

object AttackChargeModule : Module("Attack Charge", Category.COMBAT, enabled = false, x = 4f, y = 100f) {
    private var charge = 100
    fun update(pct: Int) { charge = pct }
    override fun getLines() = if (charge < 100) listOf("Charge: $charge%") else emptyList()
}

object BowChargeModule : Module("Bow Charge", Category.COMBAT, enabled = false, x = 4f, y = 112f) {
    private var pct = -1
    fun update(v: Int) { pct = v }
    override fun getLines() = if (pct >= 0) listOf("Bow: $pct%") else emptyList()
}

object ShieldModule : Module("Shield", Category.COMBAT, enabled = false, x = 4f, y = 124f) {
    private var blocking = false
    fun update(b: Boolean) { blocking = b }
    override fun getLines() = if (blocking) listOf("Shield: UP") else emptyList()
}

object FrameTimeModule : Module("Frame Time", Category.MISC, enabled = false, x = 4f, y = 136f) {
    private var ms = 0f
    fun update(fps: Int) { if (fps > 0) ms = 1000f / fps }
    override fun getLines() = if (ms > 0f) listOf("Frame: ${"%.2f".format(ms)}ms") else emptyList()
}
