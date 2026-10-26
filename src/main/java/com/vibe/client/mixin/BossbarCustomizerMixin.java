package com.vibe.client.mixin;

import com.vibe.client.feature.BossbarCustomizer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public class BossbarCustomizerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void vibeBossbarRender(DrawContext context, CallbackInfo ci) {
        if (BossbarCustomizer.INSTANCE.getEnabled() && BossbarCustomizer.INSTANCE.getHide()) {
            ci.cancel();
        }
    }
}
