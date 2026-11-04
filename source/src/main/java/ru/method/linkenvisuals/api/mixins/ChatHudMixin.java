package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.misc.ChatHelper;
import ru.method.linkenvisuals.modules.impl.misc.PvpSave;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;
import ru.method.linkenvisuals.utils.text.SyncedUserTextUtils;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow private List<ChatHudLine> messages;
    @Shadow protected abstract void refresh();

    @Unique private String linkenvisuals$lastAntiSpamMessage = "";
    @Unique private int linkenvisuals$lastAntiSpamCount = 1;
    @Unique private static final float linkenvisuals$CHAT_APPEAR_TICKS = 9.0f;

    @Inject(method = "render", at = @At("HEAD"))
    private void linkenvisuals$beginChatHudRenderScope(DrawContext context,
                                                       net.minecraft.client.font.TextRenderer textRenderer,
                                                       int currentTick, int mouseX, int mouseY,
                                                       boolean focused, boolean onlyPreview, CallbackInfo ci) {
        StreamerModeUtils.beginChatHudRender();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void linkenvisuals$endChatHudRenderScope(DrawContext context,
                                                     net.minecraft.client.font.TextRenderer textRenderer,
                                                     int currentTick, int mouseX, int mouseY,
                                                     boolean focused, boolean onlyPreview, CallbackInfo ci) {
        StreamerModeUtils.endChatHudRender();
    }

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"),
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$moveChatBadgeBeforeDonate(OrderedText text) {
        return SyncedUserTextUtils.moveSyncedBadgeBeforeChatDonate(text);
    }

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"),
            index = 4,
            require = 0
    )
    private int linkenvisuals$animateNewChatLineColor(int color, @Local(argsOnly = true, ordinal = 0) int currentTick, @Local ChatHudLine.Visible line) {
        if (line == null) return color;
        float age = linkenvisuals$chatLineAge(currentTick, line);
        if (age < 0 || age >= linkenvisuals$CHAT_APPEAR_TICKS) return color;
        float progress = MathHelper.clamp(age / linkenvisuals$CHAT_APPEAR_TICKS, 0.0f, 1.0f);
        progress = 1.0f - (1.0f - progress) * (1.0f - progress);
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) alpha = 255;
        int animatedAlpha = MathHelper.clamp(Math.round(alpha * MathHelper.lerp(progress, 0.0f, 1.0f)), 0, 255);
        return (color & 0x00FFFFFF) | (animatedAlpha << 24);
    }

    /** Новая строка чата «выезжает» слева направо (слайд по X), плавно гаснет вместе с фейдом альфы. */
    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"),
            index = 2,
            require = 0
    )
    private int linkenvisuals$slideNewChatLineX(int x, @Local(argsOnly = true, ordinal = 0) int currentTick, @Local ChatHudLine.Visible line) {
        if (line == null) return x;
        float age = linkenvisuals$chatLineAge(currentTick, line);
        if (age < 0 || age >= linkenvisuals$CHAT_APPEAR_TICKS) return x;
        float progress = MathHelper.clamp(age / linkenvisuals$CHAT_APPEAR_TICKS, 0.0f, 1.0f);
        // EASE_OUT_CUBIC — резкий старт, мягкое торможение у финальной позиции.
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress);
        // Стартовое смещение -24px (заметный выезд слева), плавно к 0.
        return x - Math.round((1.0f - eased) * 24.0f);
    }

    /** Возраст строки чата в тиках С ДРОБНОЙ ЧАСТЬЮ (tickDelta) — анимация идёт каждый кадр, а не по 20 тиков/с. */
    @Unique
    private float linkenvisuals$chatLineAge(int currentTick, ChatHudLine.Visible line) {
        float tickDelta = net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        return (currentTick - line.addedTime()) + tickDelta;
    }

    @ModifyVariable(
            method = "addMessage",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private Text linkenvisuals$badgeChatAny(Text message) {
        if (ChatUtils.isClientMessage(message)) {
            linkenvisuals$resetAntiSpamCounter();
            captureEventCoords(message);
            return ChatHelper.applyTimePrefix(message);
        }

        Text updated = StreamerModeUtils.sanitizeChatText(message);
        StreamerModeUtils.debugChatHudMutation(message, updated);
        if (StreamerModeUtils.isLikelyPlayerChatMessage(updated)) {
            updated = SyncedUserTextUtils.applyBadgeToChatHeader(updated);
            updated = SyncedUserTextUtils.moveSyncedBadgeToChatHeaderStart(updated);
        }
        updated = linkenvisuals$applyAntiSpamCounter(updated);
        captureEventCoords(updated);
        return ChatHelper.applyTimePrefix(updated);
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$keepChatHistory(boolean clearHistory, CallbackInfo ci) {
        if (clearHistory && ChatHelper.isChatHistoryEnabled()) {
            ci.cancel();
        }
    }

    @Unique
    private Text linkenvisuals$applyAntiSpamCounter(Text message) {
        if (!ChatHelper.isAntiSpamEnabled() || message == null) {
            linkenvisuals$resetAntiSpamCounter();
            return message;
        }

        String plain = message.getString();
        if (!plain.equals(linkenvisuals$lastAntiSpamMessage)) {
            linkenvisuals$lastAntiSpamMessage = plain;
            linkenvisuals$lastAntiSpamCount = 1;
            return message;
        }

        linkenvisuals$lastAntiSpamCount++;
        if (messages != null && !messages.isEmpty()) {
            messages.removeFirst();
            refresh();
        }
        return Text.empty()
                .append(message.copy())
                .append(Text.literal(" (x" + linkenvisuals$lastAntiSpamCount + ")").formatted(Formatting.DARK_GRAY));
    }

    @Unique
    private void linkenvisuals$resetAntiSpamCounter() {
        linkenvisuals$lastAntiSpamMessage = "";
        linkenvisuals$lastAntiSpamCount = 1;
    }

    private void captureEventCoords(Text message) {
        if (message == null) return;
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.isPanic()) return;
        if (instance.getWaypointManager() != null) {
            instance.getWaypointManager().captureEventCoords(message.getString());
        }
        if (instance.getModuleManager() != null) {
            PvpSave pvpSave = instance.getModuleManager().getModule(PvpSave.class);
            if (pvpSave != null && pvpSave.isToggled()) {
                pvpSave.handleChatMessage(message.getString());
            }
        }
    }
}
