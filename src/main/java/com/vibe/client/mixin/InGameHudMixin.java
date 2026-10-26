package com.vibe.client.mixin;

import com.vibe.client.feature.HitMarker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the Vibe hit markers over the in-game HUD.
 *
 * Injected at the TAIL of the HUD render: the vanilla crosshair is already
 * on screen there, so our strokes land exactly around it at every GUI
 * scale, and vanilla widgets drawn later are still above us (markers are
 * feedback, not a layer the inventory should cover).
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void vibeHitMarkers(DrawContext context, RenderTickCounter tickCounter,
                                CallbackInfo ci) {
        HitMarker.INSTANCE.draw(context);
    }
}