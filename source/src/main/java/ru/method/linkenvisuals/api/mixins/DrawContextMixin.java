package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.method.linkenvisuals.utils.debug.TabProfiler;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

@Mixin(DrawContext.class)
public class DrawContextMixin {

    private static boolean linkenvisuals$skipSanitization() {
        return TabProfiler.isActive() || StreamerModeUtils.shouldBypassDrawSanitization();
    }

    @ModifyVariable(
            method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private Text linkenvisuals$sanitizeShadowText(Text text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeText(text);
    }

    @ModifyVariable(
            method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$sanitizeShadowOrdered(OrderedText text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeOrderedText(text);
    }

    @ModifyVariable(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private Text linkenvisuals$sanitizeText(Text text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeText(text);
    }

    @ModifyVariable(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$sanitizeOrdered(OrderedText text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeOrderedText(text);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private String linkenvisuals$sanitizeShadowString(String text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeString(text);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private String linkenvisuals$sanitizeString(String text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeString(text);
    }

    @ModifyVariable(
            method = "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private Text linkenvisuals$sanitizeCenteredText(Text text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeText(text);
    }

    @ModifyVariable(
            method = "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$sanitizeCenteredOrdered(OrderedText text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeOrderedText(text);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private String linkenvisuals$sanitizeCenteredString(String text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeString(text);
    }

    @ModifyVariable(
            method = "drawTextWithBackground(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private Text linkenvisuals$sanitizeBackgroundText(Text text) {
        if (linkenvisuals$skipSanitization()) return text;
        return StreamerModeUtils.sanitizeText(text);
    }

}
