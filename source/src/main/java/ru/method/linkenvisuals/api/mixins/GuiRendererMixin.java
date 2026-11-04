package ru.method.linkenvisuals.api.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.linkenvisuals.render.ClickGuiFirePass;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the ClickGUI shader fire after the whole GUI batch has been rendered.
 *
 * <p>Doing this from {@code Screen.render} was too early: the GUI batch (which
 * includes the dark backdrop) is submitted later and covered the fire. At
 * {@code GuiRenderer.render} TAIL we are after the GUI pass, so the additive
 * fire is actually visible. The shader discards every panel interior, so it
 * only paints the halos around the panels.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void linkenvisuals$clickGuiFire(GpuBufferSlice buffer, CallbackInfo ci) {
        ClickGuiFirePass.render();
    }
}
