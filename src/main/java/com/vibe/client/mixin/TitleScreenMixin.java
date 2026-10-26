package com.vibe.client.mixin;

import com.vibe.client.feature.VibeMainMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom main menu.
 *
 * Two injections, deliberately split:
 *
 *  - {@code render} at HEAD: pin the vanilla background fade to fully opaque.
 *    Left alone, the fade starts at zero alpha and eases in over a couple of
 *    seconds, which would show the raw panorama behind our overlay and then
 *    wash it out.
 *
 *  - {@code render} at TAIL: draw the Vibe branding over the vanilla menu.
 *
 * The vanilla TitleScreen still runs in full, so every button, the
 * accessibility prompt, the multiplayer/singleplayer flows and the splash text
 * keep working. We decorate; we do not replace. Replacing it would mean
 * reimplementing all of that for no visible gain.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    /** The fade amount vanilla applies to the panorama. */
    @Shadow
    private float backgroundAlpha;

    @Inject(method = "render", at = @At("HEAD"))
    private void vibeMainMenuHead(DrawContext context, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        if (!VibeMainMenu.INSTANCE.getEnabled()) return;
        // 1.0 means "fully faded in": no black-out phase, no surprise.
        this.backgroundAlpha = 1.0f;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vibeMainMenuTail(DrawContext context, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        VibeMainMenu.INSTANCE.draw(context, (TitleScreen) (Object) this);
    }
}
