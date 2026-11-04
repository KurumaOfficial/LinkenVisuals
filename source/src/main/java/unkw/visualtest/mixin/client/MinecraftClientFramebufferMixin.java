package unkw.visualtest.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unkw.visualtest.client.hud.HudEntityPreview;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientFramebufferMixin {
    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void visualTestRedirectPreviewFramebuffer(CallbackInfoReturnable<Framebuffer> callback) {
        Framebuffer framebuffer = HudEntityPreview.getFramebufferOverride();
        if (framebuffer != null) {
            callback.setReturnValue(framebuffer);
        }
    }
}
