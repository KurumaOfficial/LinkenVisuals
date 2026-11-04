package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void linkenvisuals$beginTextFieldRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        StreamerModeUtils.beginTextFieldRender();
    }

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void linkenvisuals$endTextFieldRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        StreamerModeUtils.endTextFieldRender();
    }
}
