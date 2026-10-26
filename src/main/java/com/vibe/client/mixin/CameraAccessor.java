package com.vibe.client.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens up {@code Camera.setPos} and {@code Camera.setRotation}, both protected
 * in vanilla.
 *
 * A mixin class is merged into the target at runtime, but at compile time it is
 * still an unrelated class, so calling a protected method on a cast
 * {@code (Camera)(Object)this} does not compile. An {@code @Invoker} generates
 * the bridge for us and stays valid across mapping changes.
 */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setPos")
    void vibeSetPos(double x, double y, double z);

    @Invoker("setRotation")
    void vibeSetRotation(float yaw, float pitch);
}
