package com.vibe.client.mixin;

import com.vibe.client.feature.FreeLook;
import com.vibe.client.module.impl.CpsModule;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the CPS module with real mouse presses.
 *
 * Written in Java on purpose: Loom's mixin annotation processor only runs over
 * Java sources. A Kotlin mixin compiles fine but ships without a refMap, so at
 * runtime Mixin looks for the Yarn name `onMouseButton`, finds only the
 * intermediary `method_1595`, and aborts the whole client with
 * "Critical injection failure ... No refMap loaded".
 *
 * Only GLFW_PRESS (action == 1) counts, and only while the cursor is locked:
 * clicking inside a menu must never inflate the counter.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void vibeOnMouseButton(long window, int button, int action, int mods,
                                   CallbackInfo ci) {
        if (action != 1) return;
        Mouse self = (Mouse) (Object) this;
        if (!self.isCursorLocked()) return;
        if (button == 0) {
            CpsModule.INSTANCE.onClick(true);
        } else if (button == 1) {
            CpsModule.INSTANCE.onClick(false);
        }
    }

    /**
     * FreeLook, first half: remember the player's rotation before the game
     * applies this frame's mouse movement to it.
     */
    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void vibeFreeLookBefore(double timeDelta, CallbackInfo ci) {
        FreeLook.INSTANCE.captureBeforeTurn();
    }

    /**
     * FreeLook, second half: the game has just rotated the player. Hand that
     * rotation change to the free-look camera and put the player back the way
     * they were, so movement direction never changes.
     */
    @Inject(method = "updateMouse", at = @At("TAIL"))
    private void vibeFreeLookAfter(double timeDelta, CallbackInfo ci) {
        FreeLook.INSTANCE.consumeTurn();
    }

    /**
     * Scroll-zoom: while the zoom key is held, scroll the wheel to change the
     * zoom factor (2x – 32x). The event is consumed so vanilla doesn't react.
     */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void vibeZoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (com.vibe.client.feature.Zoom.INSTANCE.getZooming() &&
            com.vibe.client.feature.Zoom.INSTANCE.onScroll(vertical)) {
            ci.cancel();
        }
    }
}
