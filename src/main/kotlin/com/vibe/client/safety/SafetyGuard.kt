package com.vibe.client.safety

import com.vibe.client.VibeClient
import com.vibe.client.module.Module
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client-side crash shield.
 *
 * Rule of the whole client: a broken module must never take the game down.
 * Every call into module/plugin code goes through here, so an exception is
 * caught at the boundary, the culprit is disabled, and the user is told which
 * one it was instead of getting a crash report with no explanation.
 *
 * Design notes:
 *  - Failures are counted per module. One transient failure keeps the module
 *    alive; [MAX_FAILURES] of them disable it for the session.
 *  - The guard never throws. Ever. A guard that throws is worse than no guard.
 *  - Lookups are lock-free: render runs every frame on the game thread, while
 *    the menu can disable modules from the same thread, so a plain
 *    [ConcurrentHashMap] is enough and avoids any monitor in the hot path.
 */
object SafetyGuard {

    /** How many times a module may throw before it is switched off. */
    private const val MAX_FAILURES = 3

    private val failures = ConcurrentHashMap<String, AtomicInteger>(16)

    /** Modules switched off by the guard, by name, so the UI can explain why. */
    private val disabled = ConcurrentHashMap<String, String>(8)

    val disabledModules: Map<String, String> get() = disabled

    /**
     * Runs [block] for [module], catching everything.
     *
     * Returns true when the block completed normally. On failure the module is
     * left enabled until it hits [MAX_FAILURES], then disabled permanently for
     * this session with a reason recorded for the UI.
     */
    fun guardModule(module: Module, stage: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (t: Throwable) {
            onModuleFailure(module, stage, t)
            false
        }
    }

    /** Same as [guardModule] but for arbitrary named work (plugins, hooks). */
    fun guard(name: String, stage: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (t: Throwable) {
            VibeClient.logger.warn("[safety] $name failed in $stage: ${describe(t)}")
            false
        }
    }

    private fun onModuleFailure(module: Module, stage: String, t: Throwable) {
        val count = failures.computeIfAbsent(module.name) { AtomicInteger() }.incrementAndGet()
        val message = describe(t)

        if (count == 1) {
            // First strike: log with a stack trace so a real bug stays findable.
            VibeClient.logger.warn("[safety] module '${module.name}' failed in $stage: $message", t)
        } else {
            VibeClient.logger.warn(
                "[safety] module '${module.name}' failed in $stage " +
                    "($count/$MAX_FAILURES): $message"
            )
        }

        if (count >= MAX_FAILURES && module.enabled) {
            module.enabled = false
            disabled[module.name] = "$stage: $message"
            VibeClient.logger.warn(
                "[safety] module '${module.name}' disabled after $count failures"
            )
        }
    }

    /** Clears the failure history — used when the user re-enables a module. */
    fun forgive(moduleName: String) {
        failures.remove(moduleName)
        disabled.remove(moduleName)
    }

    /** Human-readable one-liner for logs; never includes the whole stack. */
    private fun describe(t: Throwable): String {
        val message = t.message
        val type = t.javaClass.simpleName
        return if (message.isNullOrBlank()) type else "$type: $message"
    }
}
