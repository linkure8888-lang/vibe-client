package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object MemoryModule : Module("Memory", Category.MISC, enabled = false, x = 4f, y = 104f) {
    fun sample() {}
    fun usedMegabytes(): Int {
        val rt = Runtime.getRuntime()
        return ((rt.totalMemory() - rt.freeMemory()) / 1048576L).toInt()
    }

    override fun getLines(): List<String> {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1048576L
        val max = rt.maxMemory() / 1048576L
        return listOf("RAM: ${used}MB / ${max}MB")
    }
}
