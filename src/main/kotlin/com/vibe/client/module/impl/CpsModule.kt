package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object CpsModule : Module("CPS", Category.HUD, enabled = false, x = 4f, y = 56f) {
    private val leftClicks = ArrayDeque<Long>()
    private val rightClicks = ArrayDeque<Long>()

    fun tick() {}

    fun recordLeft() { leftClicks.addLast(System.currentTimeMillis()) }
    fun recordRight() { rightClicks.addLast(System.currentTimeMillis()) }

    fun onClick(left: Boolean) {
        if (left) recordLeft() else recordRight()
    }

    private fun count(q: ArrayDeque<Long>): Int {
        val now = System.currentTimeMillis()
        while (q.isNotEmpty() && now - q.first() > 1000L) q.removeFirst()
        return q.size
    }

    override fun getLines(): List<String> {
        val l = count(leftClicks)
        val r = count(rightClicks)
        return listOf("CPS: $l L  $r R")
    }
}
