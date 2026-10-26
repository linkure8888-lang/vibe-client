package com.vibe.client.feature

import com.vibe.client.config.ConfigManager
import net.minecraft.client.MinecraftClient
import kotlin.math.abs

/**
 * Zoom — плавный зум с интерполяцией FOV и скролл-зумом.
 *
 * Реализация через GameRendererMixin (getFov) — мы делим уже вычисленный
 * vanilla-FOV на [divisor], сохраняя все эффекты (бег, зелья, sliders).
 *
 * Плавность реализована экспоненциальным lerp каждый тик (20 Hz):
 * currentDivisor «едет» к targetDivisor со скоростью [SMOOTH_SPEED].
 * При zoomSmooth=1.0 — мгновенно; при 0.1 — очень плавно.
 *
 * Scroll-zoom: прокрутка колёсиком во время зума меняет [zoomFactor]
 * от 2x до 32x. Кнопка C зарегистрирована в KeyInputHandler.
 */
object Zoom {

    /** Обработчик скролл-эвента, смотри MouseMixin. */
    @Volatile
    var zooming: Boolean = false

    /** Текущий дивизор FOV (lerp между 1.0 и targetDivisor). */
    @Volatile
    private var currentDivisor: Double = 1.0

    private var targetDivisor: Double = 1.0

    /**
     * Divisor, используемый в GameRendererMixin.getFov().
     * Если <= 1.0 — мин в мин, нет эффекта.
     */
    val divisor: Double
        get() = currentDivisor

    /** Нужно ли зумировать? */
    val active: Boolean
        get() = currentDivisor > 1.005

    fun tick(client: MinecraftClient) {
        val cfg = ConfigManager.current
        val factor = cfg.zoomFactor.coerceIn(2.0, 32.0)
        val smooth = cfg.zoomSmoothness.coerceIn(0.05, 1.0)

        targetDivisor = if (zooming) factor else 1.0

        // Экспоненциальный lerp: чем меньше smooth, тем медленнее.
        val speed = smooth * 0.5
        currentDivisor += (targetDivisor - currentDivisor) * speed

        if (abs(currentDivisor - targetDivisor) < 0.002) {
            currentDivisor = targetDivisor
        }
    }

    /**
     * Вызывается из MouseMixin при скролле.
     * @return true — скролл перехвачен зумом, vanilla не обрабатывает.
     */
    fun onScroll(vertical: Double): Boolean {
        if (!zooming) return false
        val cfg = ConfigManager.current
        val newFactor = (cfg.zoomFactor + if (vertical > 0) 1.0 else -1.0)
            .coerceIn(2.0, 32.0)
        cfg.zoomFactor = newFactor
        ConfigManager.requestSave()
        return true
    }

    fun reset() {
        zooming = false
        currentDivisor = 1.0
        targetDivisor = 1.0
    }
}
