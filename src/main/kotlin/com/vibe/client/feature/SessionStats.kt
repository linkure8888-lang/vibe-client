package com.vibe.client.feature

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object SessionStats : Module("Session Stats", Category.MISC, enabled = false, x = 4f, y = 4f) {
    fun tick(pos: Triple<Double, Double, Double>?) {}
    fun rebuild() {}
    fun onDeath() {}
}
