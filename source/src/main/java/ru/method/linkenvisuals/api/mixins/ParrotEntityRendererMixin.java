package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.entity.ParrotEntityRenderer;
import net.minecraft.client.render.entity.state.ParrotEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.Raven;

@Mixin(ParrotEntityRenderer.class)
public class ParrotEntityRendererMixin {

    @Inject(
            method = "getTexture(Lnet/minecraft/client/render/entity/state/ParrotEntityRenderState;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void linkenvisuals$ravenPreviewTexture(ParrotEntityRenderState state,
                                                   CallbackInfoReturnable<Identifier> cir) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (Raven.previewRavenActive() && client != null
                && client.currentScreen instanceof ru.method.linkenvisuals.screen.cosmetics.CosmeticsScreen) {
            cir.setReturnValue(Raven.VORON_TEX);
        }
    }
}
