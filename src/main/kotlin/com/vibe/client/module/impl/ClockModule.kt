package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ClockModule : Module("Clock", Category.HUD, enabled = false, x = 4f, y = 116f) {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    override fun getLines() = listOf("Time: ${LocalTime.now().format(fmt)}")
    fun tick() {}
}
