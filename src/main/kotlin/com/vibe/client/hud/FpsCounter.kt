package com.vibe.client.hud

/**
 * Frame-rate sampler.
 *
 * Counts frames and publishes an FPS value once per second.
 * Cheap: one increment per frame, one division per second.
 *
 * [onFrame] is called from the HUD callback, which Minecraft invokes exactly
 * once per rendered frame. An earlier version counted frames with a mixin into
 * `MinecraftClient.render` — that broke on 1.21.1 because the method no longer
 * has that name, and a mixin failure is fatal for the whole client. The HUD
 * callback gives the same number without touching Minecraft internals.
 */
object FpsCounter {

    private var frames = 0
    private var lastSample = System.nanoTime()

    @Volatile
    var fps: Int = 0
        private set

    fun onFrame() {
        frames++
        val now = System.nanoTime()
        val elapsed = now - lastSample
        if (elapsed >= 1_000_000_000L) {
            // Avoid divide-by-zero and absurd spikes on lag spikes.
            fps = (frames * 1_000_000_000.0 / elapsed).toInt()
            frames = 0
            lastSample = now
        }
    }
}
