package com.vibe.client.mixin;

import com.vibe.client.cosmetics.CosmeticsManager;
import com.vibe.client.cosmetics.VibeCapeTextureRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Local cosmetics: swap the cape texture for our procedural one.
 *
 * The vanilla cape renderer asks the player entity for its cape texture
 * (Mojang's or a mod's) and simply draws nothing when the answer is null.
 * Redirecting that single lookup is the least invasive way to draw our own
 * cape: the vanilla model, animation and sway all keep working, and every
 * other player in the world keeps whatever cape they actually have.
 *
 * Only the local player is touched. Without a cosmetics server we cannot
 * know what anyone else wears, and guessing (showing our cape on everyone)
 * would be a lie the user can see in multiplayer.
 */
@Mixin(AbstractClientPlayerEntity.class)
public class CapeTextureMixin {

    @Inject(method = "getCapeTexture()Lnet/minecraft/util/Identifier;", at = @At("HEAD"), cancellable = true)
    private void vibeCapeTexture(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        if (net.minecraft.client.MinecraftClient.getInstance().player != self) return;

        CosmeticsManager.Cosmetic cosmetic = CosmeticsManager.INSTANCE.getEquipped();
        if (cosmetic == null) return;

        Identifier texture = VibeCapeTextureRegistry.identifier(cosmetic);
        if (texture != null) {
            cir.setReturnValue(texture);
        }
    }
}
