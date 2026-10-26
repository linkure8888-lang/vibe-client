package com.vibe.client.mixin;

import com.vibe.client.feature.FreeLook;
import com.vibe.client.feature.SmoothSneak;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera hooks: FreeLook and Smooth Sneak.
 *
 * Java, not Kotlin: Loom only runs the mixin annotation processor over Java
 * sources, and a mixin without a refMap dies at runtime with "Critical
 * injection failure". Only a mixin class may call a protected target method,
 * which is why the rotation is set here rather than from Kotlin.
 */
@Mixin(Camera.class)
public class CameraMixin {

    /**
     * FreeLook: after vanilla has positioned and rotated the camera, override
     * the rotation with the free-look angles.
     *
     * TAIL is the right place because vanilla's own rotation maths still needs
     * to run — it is what keeps third-person, sleeping and the death screen
     * behaving normally once the free camera is released.
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void vibeCameraHooks(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                 boolean inverseView, float tickDelta, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;

        if (FreeLook.INSTANCE.getActive() && focusedEntity instanceof PlayerEntity) {
            ((CameraAccessor) this).vibeSetRotation(
                FreeLook.INSTANCE.getYaw(), FreeLook.INSTANCE.getPitch());
            return;
        }

        if (!(focusedEntity instanceof PlayerEntity player)) return;

        float correction = SmoothSneak.INSTANCE.correction(player);
        if (correction == 0.0f) return;

        var pos = self.getPos();
        ((CameraAccessor) this).vibeSetPos(pos.x, pos.y + correction, pos.z);
    }
}
