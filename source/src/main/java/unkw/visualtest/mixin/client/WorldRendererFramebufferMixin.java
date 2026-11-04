package unkw.visualtest.mixin.client;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unkw.visualtest.client.hud.HudEntityPreview;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererFramebufferMixin {
    @Inject(method = "getEntityFramebuffer", at = @At("HEAD"), cancellable = true)
    private void visualTestRedirectPreviewEntityFramebuffer(CallbackInfoReturnable<Framebuffer> callback) {
        Framebuffer framebuffer = HudEntityPreview.getFramebufferOverride();
        if (framebuffer != null) {
            callback.setReturnValue(framebuffer);
        }
    }
}
