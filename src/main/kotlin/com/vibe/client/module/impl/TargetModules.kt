package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object TargetInfoModule : Module("Target Info", Category.COMBAT, enabled = false, x = 4f, y = 4f) {
    private var text = "Target: --"
    fun update(t: String) { text = t }
    override fun getLines() = listOf(text)
}

object TargetBlockModule : Module("Target Block", Category.COMBAT, enabled = false, x = 4f, y = 28f) {
    private var block = "--"; private var light = 0
    fun update(b: String, l: Int) { block = b; light = l }
    override fun getLines() = listOf("Block: $block (Light $light)")
}

object TargetHardnessModule : Module("Target Hardness", Category.COMBAT, enabled = false, x = 4f, y = 40f) {
    private var hardness = 0f; private var toolReq = false
    fun update(h: Float, tr: Boolean) { hardness = h; toolReq = tr }
    override fun getLines() = listOf("Hardness: ${"%.1f".format(hardness)}")
}

object RedstoneModule : Module("Redstone", Category.COMBAT, enabled = false, x = 4f, y = 52f) {
    private var power = 0
    fun update(p: Int) { power = p }
    override fun getLines() = listOf("Redstone: $power")
}

object LookAnglesModule : Module("Look Angles", Category.RENDER, enabled = false, x = 4f, y = 64f) {
    private var yaw = 0f; private var pitch = 0f
    fun update(y: Float, p: Float) { yaw = y; pitch = p }
    override fun getLines() = listOf("Yaw: ${"%.1f".format(yaw)}  Pitch: ${"%.1f".format(pitch)}")
}
