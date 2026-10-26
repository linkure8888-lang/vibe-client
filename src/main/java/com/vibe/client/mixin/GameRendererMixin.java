package com.vibe.client.mixin;

import com.vibe.client.feature.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the Zoom feature by shrinking the computed FOV.
 *
 * Uses Zoom.divisor — a smooth lerp'd value updated every tick,
 * so the FOV glides rather than snapping. A divisor of 1.0 is a no-op.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vibeZoom(Camera camera, float tickDelta, boolean changingFov,
                          CallbackInfoReturnable<Double> cir) {
        double divisor = Zoom.INSTANCE.getDivisor();
        if (divisor <= 1.001) return;
        cir.setReturnValue(cir.getReturnValueD() / divisor);
    }
}
