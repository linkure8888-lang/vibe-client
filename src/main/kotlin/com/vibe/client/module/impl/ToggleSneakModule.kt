package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object ToggleSneakModule : Module("Toggle Sneak", Category.MOVEMENT, enabled = false, x = 4f, y = 50f) {
    override fun toggle() {
        super.toggle()
    }
}
