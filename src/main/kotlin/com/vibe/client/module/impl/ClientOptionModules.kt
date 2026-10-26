package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object RenderDistanceModule : Module("Render Distance", Category.WORLD, enabled = false, x = 4f, y = 4f) {
    private var rd = 12
    fun update(v: Int) { rd = v }
    override fun getLines() = listOf("Render: ${rd}c")
}

object GuiScaleModule : Module("GUI Scale", Category.MISC, enabled = false, x = 4f, y = 16f) {
    private var scale = 2
    fun update(v: Int) { scale = v }
    override fun getLines() = listOf("GUI Scale: ${if (scale == 0) "Auto" else scale.toString()}")
}

object BrightnessModule : Module("Brightness", Category.MISC, enabled = false, x = 4f, y = 28f) {
    private var gamma = 1.0
    fun update(v: Double) { gamma = v }
    override fun getLines() = listOf("Brightness: ${(gamma * 100).toInt()}%")
}

object WindowFocusModule : Module("Window Focus", Category.MISC, enabled = false, x = 4f, y = 40f) {
    private var focused = true
    fun update(v: Boolean) { focused = v }
    override fun getLines() = listOf("Focus: ${if (focused) "Yes" else "No"}")
}

object VolumeModule : Module("Volume", Category.MISC, enabled = false, x = 4f, y = 52f) {
    private var vol = 100.0
    fun update(v: Double) { vol = v }
    override fun getLines() = listOf("Volume: ${vol.toInt()}%")
}
