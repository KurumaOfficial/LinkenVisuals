package ru.method.linkenvisuals.api.mixins;

import dev.linkenvisuals.hands.HandEditor;
import dev.linkenvisuals.hands.HandPanel;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.debug.ChatProfiler;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

@Mixin(ChatScreen.class)
public abstract class ChatScreenHintsMixin {

    /**
     * Единственная воронка для всего, что игрок набрал в чате — сюда строка попадает раньше, чем
     * решается, уйдёт она командным пакетом или обычным сообщением. Отсюда ловим {@code /ah sell <цена>}
     * и гасим отправку: команда должна остаться на клиенте и до сервера не долетать.
     */
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$noteChatCommand(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (ru.method.linkenvisuals.modules.impl.misc.FakeCosmetic.noteCommand(chatText)) {
            ci.cancel();
        }
    }

    @Unique
    private Animation linkenvisuals$hintsAnimation;
    @Unique
    private static final float linkenvisuals$HINT_MARGIN_X = 6.0f;
    @Unique
    private static final float linkenvisuals$HINT_MARGIN_Y = 8.0f;
    @Unique
    private static final float linkenvisuals$HOTBAR_HEIGHT = 22.0f;
    @Unique
    private static final float linkenvisuals$HOTBAR_HINT_OFFSET = 40.0f;

    @Unique
    private Animation linkenvisuals$getHintsAnimation() {
        if (linkenvisuals$hintsAnimation == null) {
            linkenvisuals$hintsAnimation = new Animation(260, 1f, true, Easing.EASE_OUT_CUBIC);
        }
        return linkenvisuals$hintsAnimation;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void linkenvisuals$init(CallbackInfo ci) {
        Animation animation = linkenvisuals$getHintsAnimation();
        animation.reset();
        animation.snapTo(true);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void linkenvisuals$beginChatProfile(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatProfiler.beginFrame();
        Render2D.prepareHudFrame(context);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void linkenvisuals$renderHints(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long hintsStart = ChatProfiler.startSection();
        float progress = linkenvisuals$getHintsAnimation().getValue();
        if (progress <= 0.01f) {
            ChatProfiler.endSection("hintPanel", hintsStart);
            return;
        }

        float uiScale = UI.getInterfaceScale();
        float fontSize = 7.5f * uiScale;
        // \u041f\u0435\u0440\u0432\u0430\u044f \u0441\u0442\u0440\u043e\u043a\u0430 (\u00ab\u041f\u041a\u041c \u2026 \u043c\u0435\u043d\u044e \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a\u00bb) \u0443\u0431\u0440\u0430\u043d\u0430 \u2014 \u043e\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u0430 \u0442\u043e\u043b\u044c\u043a\u043e \u043f\u043e\u0434\u0441\u043a\u0430\u0437\u043a\u0430 \u043f\u0440\u043e \u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435.
        String line = "\u0421\u041a\u041c \u043f\u043e \u043d\u0438\u043a\u0443/\u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u0430\u043c \u2014 \u0441\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c";

        float padX = 8.0f * uiScale;
        float padY = 5.0f * uiScale;
        float lineHeight = Fonts.ROUNDBOLD.getHeight(fontSize);
        float textWidth = Fonts.ROUNDBOLD.getWidth(line, fontSize);
        float boxWidth = textWidth + padX * 2f;
        float boxHeight = padY * 2f + lineHeight;

        float screenW = context.getScaledWindowWidth();
        float screenH = context.getScaledWindowHeight();

        float hotbarTop = screenH - linkenvisuals$HOTBAR_HEIGHT;
        float targetY = hotbarTop - boxHeight - linkenvisuals$HOTBAR_HINT_OFFSET * uiScale;
        float startOffset = 14.0f * uiScale;
        float y = targetY - (1.0f - progress) * startOffset;
        float x = screenW / 2f - boxWidth / 2f;
        x = Math.max(linkenvisuals$HINT_MARGIN_X, Math.min(x, screenW - boxWidth - linkenvisuals$HINT_MARGIN_X));
        y = Math.max(linkenvisuals$HINT_MARGIN_Y, Math.min(y, screenH - boxHeight - linkenvisuals$HINT_MARGIN_Y));

        int alpha = Math.max(0, Math.min(255, Math.round(255 * progress)));
        boolean link = ru.method.linkenvisuals.hud.LinkHudStyle.active();
        if (link) {
            // \u041f\u0430\u043d\u0435\u043b\u044c \u0432 \u0446\u0432\u0435\u0442\u0430\u0445 \u0442\u0435\u043c\u044b (\u043a\u0430\u043a \u043e\u0441\u0442\u0430\u043b\u044c\u043d\u044b\u0435 HUD-\u044d\u043b\u0435\u043c\u0435\u043d\u0442\u044b Link-\u0441\u0442\u0438\u043b\u044f).
            ru.method.linkenvisuals.hud.LinkHudStyle.panel(context.getMatrices(), x, y, boxWidth, boxHeight,
                    ru.method.linkenvisuals.hud.LinkHudStyle.radius(boxWidth, boxHeight), progress);
        } else {
            Color bg = new Color(245, 245, 245, Math.round(200 * progress));
            if (ColorUtils.isBlurEnabled()) {
                Render2D.drawLiquidRect(context.getMatrices(), x, y, boxWidth, boxHeight,
                        new BorderRadius(Render2D.DEFAULT_CORNER_RADIUS), new ColorRGBA(bg),
                        2.5f, 22.0f, 11f, 175.0f, true, 0f, 0.19f);
            } else {
                Render2D.drawRoundedRect(context.getMatrices(), x, y, boxWidth, boxHeight, Render2D.DEFAULT_CORNER_RADIUS, bg);
            }
        }

        Color textColor = link
                ? ru.method.linkenvisuals.hud.LinkHudStyle.text(alpha)
                : new Color(255, 255, 255, Math.round(200 * progress));
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(fontSize), line,
                x + padX, y + (boxHeight - lineHeight) / 2f, textColor);
        ChatProfiler.endSection("hintPanel", hintsStart);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void linkenvisuals$endChatProfile(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatProfiler.endFrame();
    }

    /** Strange HandEditor panel: visible in chat while Better Hands is on. */
    @Inject(method = "render", at = @At("RETURN"))
    private void linkenvisuals$renderHandEditor(DrawContext context, int mouseX, int mouseY, float delta,
                                                 CallbackInfo ci) {
        HandEditor.renderHandles(context, mouseX, mouseY);
        HandPanel.render(context, mouseX, mouseY);
        HandEditor.renderOverlay(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$handEditorClick(Click click, boolean doubled,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (HandPanel.press(click.x(), click.y())
            || HandEditor.press(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$handEditorScroll(double mouseX, double mouseY,
                                                 double horizontal, double vertical,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (HandEditor.scroll(mouseX, mouseY, vertical)) {
            cir.setReturnValue(true);
        }
    }
}
