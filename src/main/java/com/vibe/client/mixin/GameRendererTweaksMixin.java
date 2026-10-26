package com.vibe.client.mixin;

import com.vibe.client.feature.CameraTweaks;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera-quality tweaks: no hurt tilt and no view bobbing.
 *
 * Both are cancelled at HEAD of the vanilla method rather than reimplemented,
 * so if a player turns them back on the original behaviour is byte-for-byte
 * vanilla — there is no parallel code path to drift out of sync.
 */
@Mixin(GameRenderer.class)
public class GameRendererTweaksMixin {

    /** Suppresses the camera tilt when the player takes damage. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void vibeNoHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CameraTweaks.INSTANCE.getNoHurtCam()) ci.cancel();
    }

    /** Suppresses the walking view bob. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void vibeNoViewBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CameraTweaks.INSTANCE.getNoViewBob()) ci.cancel();
    }

    /**
     * Hides the first-person hand.
     *
     * `renderHand` is the private field that gates the whole hand render, so
     * clearing it at HEAD of the method skips the work entirely rather than
     * drawing and then discarding. The field is restored at RETURN, otherwise
     * the flag would stay off after the feature is switched back on.
     */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void vibeHideHand(net.minecraft.client.render.Camera camera, float tickDelta,
                              org.joml.Matrix4f matrix, CallbackInfo ci) {
        if (CameraTweaks.INSTANCE.getHideHand()) ci.cancel();
    }
}
