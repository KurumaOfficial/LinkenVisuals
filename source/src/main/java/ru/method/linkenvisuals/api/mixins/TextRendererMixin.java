package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.method.linkenvisuals.utils.debug.TabProfiler;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {

    private static boolean linkenvisuals$shouldSanitizeSelfNametag() {
        return !TabProfiler.isActive()
                && !StreamerModeUtils.shouldBypassDrawSanitization()
                && StreamerModeUtils.isSelfNametagRenderActive();
    }

    // Здесь СОЗНАТЕЛЬНО нет хуков на draw(Text, ...) — из-за них неймтег рисовался кашей.
    //
    // EntityRenderer.renderLabelIfPresent сперва берёт ширину: x = -getWidth(text) / 2, и только
    // потом рисует. Метку уже правят EntityRendererLabelMixin и PlayerEntityRendererMixin —
    // оба @ModifyVariable на HEAD самого renderLabelIfPresent, то есть ДО getWidth. Хуки на
    // TextRenderer.draw срабатывали ПОСЛЕ, когда ширина посчитана: строку правили ещё раз, она
    // меняла длину, и глифы уезжали с центра, наложившись на то, что уже нарисовано.
    //
    // Хуже: draw(Text,…,II) внутри делегирует в draw(Text,…,IIZ), а хуки висели на ОБОИХ —
    // sanitizeSelfNametag применялся дважды за один вызов. Он не идемпотентен
    // (replaceDonatePrefixSegments не дописывает префикс, а ПЕРЕСОБИРАЕТ его), поэтому
    // f(f(x)) != f(x): ширина считалась по f(x), а рисовалось f(f(f(x))).
    //
    // getWidth хукать нельзя — он вызывается и для чужого текста; правильный слой один,
    // и он уже занят: renderLabelIfPresent HEAD.

    @ModifyVariable(
            method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$sanitizeSelfNametagOrdered(OrderedText text) {
        return linkenvisuals$shouldSanitizeSelfNametag() ? StreamerModeUtils.sanitizeSelfNametagOrdered(text) : text;
    }

    @ModifyVariable(
            method = "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$sanitizeSelfNametagOutline(OrderedText text) {
        return linkenvisuals$shouldSanitizeSelfNametag() ? StreamerModeUtils.sanitizeSelfNametagOrdered(text) : text;
    }

    @ModifyVariable(
            method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private String linkenvisuals$sanitizeSelfNametagString(String text) {
        return linkenvisuals$shouldSanitizeSelfNametag() ? StreamerModeUtils.sanitizeString(text) : text;
    }
}
