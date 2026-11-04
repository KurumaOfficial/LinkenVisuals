package ru.method.linkenvisuals.hud.impl;


import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.AnimatedTextHelper;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.Counter;
import ru.method.linkenvisuals.utils.server.Server;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.*;


public class Watermark extends HudElement {

    private final BooleanSetting fade = new BooleanSetting("settings.hud.fade", true);
    /** Одометр для числа FPS: сменившиеся цифры плавно уезжают/въезжают, как таймеры зелий. */
    private final AnimatedString fpsAnim = new AnimatedString(120, Easing.EASE_OUT_CUBIC, "0");

    public Watermark() {
        super("Watermark");
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        // Стиль «Карточка»: элемент заменён готовой вотермаркой из visual-test.
        if (LinkHudStyle.active() && LinkHudStyle.variant() == LinkHudStyle.Variant.Card) {
            float x = getX(), y = getY();
            var ms = e.getContext().getMatrices();
            float ui = beginUiScale(ms, x, y);
            // Плавное вкл/выкл — тот же toggledAnimation, что у штатных элементов.
            float t = toggledAnimation.getValue() * getDragScale();
            ms.pushMatrix();
            ms.translate(x, y);
            ms.scale(t, t);
            ms.translate(-x, -y);
            int w = unkw.visualtest.client.hud.VisualHud.cardWatermark(e.getContext(), mc, Math.round(x), Math.round(y));
            ms.popMatrix();
            endUiScale(ms);
            setUiScaledBounds(x, y, w, 17, ui);
            super.onRender2D(e);
            return;
        }

        float x = getX();
        float y = getY();
        float padding = 5f;
        float fontSize = 8f;
        float infoIconSize = 9f;
        String fpsIcon = "C";
        String pingIcon = "O";
        String name = "t.me/LinkenVisuals";
        Font font = Fonts.ROUNDBOLD;
        Font iconFont = Fonts.ICONSNEW;

        float textWidth = Fonts.ROUNDBOLD.getWidth(name, fontSize);
        float height = 17f;

        float fpsIconWidth = iconFont.getWidth(fpsIcon, infoIconSize);
        float pingIconWidth = iconFont.getWidth(pingIcon, infoIconSize);
        String fpsValue = String.valueOf(Counter.getCurrentFPS());
        String fpsLabel = "FPS";
        String pingValue = formatPing(Server.getPing(mc.player));
        String pingLabel = "MS";
        float labelFontSize = fontSize - 1.5f;
        float fpsValueWidth = font.getWidth(fpsValue, fontSize);
        float fpsLabelWidth = Fonts.SEMIBOLD.getWidth(fpsLabel, labelFontSize);
        float pingValueWidth = font.getWidth(pingValue, fontSize);
        float pingLabelWidth = Fonts.SEMIBOLD.getWidth(pingLabel, labelFontSize);
        float iconTextSpacing = 3f;
        float valueLabelSpacing = 1.5f;
        float bulletSize = 4f;
        float bulletSpacing = 1.5f;
        float bulletWidth = bulletSize + bulletSpacing * 2f;
        float compactSpacing = 2f;
        float edgePadding = 2f;
        float totalWidth =
                padding + textWidth + compactSpacing + bulletWidth + compactSpacing +
                        fpsIconWidth + iconTextSpacing + fpsValueWidth + valueLabelSpacing + fpsLabelWidth +
                        compactSpacing + bulletWidth + compactSpacing +
                        pingIconWidth + iconTextSpacing + pingValueWidth + valueLabelSpacing + pingLabelWidth + padding + edgePadding;

        float uiScale = beginUiScale(e.getContext().getMatrices(), x, y);
        e.getContext().getMatrices().pushMatrix();
        float dragScale = getDragScale();
        float[] dragOffset = getDragOffset();
        float renderScale = toggledAnimation.getValue() * dragScale;
        e.getContext().getMatrices().translate(x + totalWidth / 2 + dragOffset[0], y + height / 2 + dragOffset[1]);
        e.getContext().getMatrices().scale(renderScale, renderScale);
        e.getContext().getMatrices().translate(-(x + totalWidth / 2 + dragOffset[0]), -(y + height / 2 + dragOffset[1]));

        boolean link = LinkHudStyle.active();
        // ЕДИНОЕ сглаживание (как пилюля ClickGUI/PotionHud/KeyBindsHud) — НЕ хардкод DEFAULT_CORNER_RADIUS.
        float radius = LinkHudStyle.radius(totalWidth, height);
        if (link) {
            // Форма — «пилюля» (полное скругление по высоте), отличается от прямоугольных панелей.
            LinkHudStyle.panel(e.getContext().getMatrices(), x, y, totalWidth, height, radius, toggledAnimation.getValue());
        } else {
            // Единый liquid-glass фон 1в1 как у CooldownsHud (Задержка) в панель-режиме.
            Color bgColor = ColorUtils.getGlassColor(255);
            Render2D.drawLiquidRectHud(
                    e.getContext().getMatrices(), x, y, totalWidth, height,
                    new BorderRadius(10f),
                    new ColorRGBA(bgColor),
                    2.5f, 22.0f, 11f, 175.0f, true, 0f, 0.19f);
        }

        // Аутлайн: на стекле — белая стеклянная нотка (1в1 ScoreboardHud), в Link — тонкий акцент.
        // Радиус рамки ДОЛЖЕН совпадать с радиусом фона: в Panel фон liquid-glass рисуется с BorderRadius(10f),
        // а рамка с другим радиусом не садилась на кромку → видимая «граница» на скруглённых углах.
        Color borderColor = link ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 80) : ColorUtils.alpha(Color.WHITE, 28);
        float borderRadius = link ? radius : 10f;
        Render2D.drawBorder(e.getContext().getMatrices(), x, y, totalWidth, height, borderRadius, 0.8f, new ColorRGBA(borderColor));

        double time = (System.currentTimeMillis() % 3000) / 3000.0;

        float textHeight = font.getHeight(fontSize);
        float infoIconHeight = iconFont.getHeight(infoIconSize);
        float centerY = y + height / 2f;

        float textY = centerY - textHeight / 2f;
        float infoIconY = centerY - infoIconHeight / 2f;
        float currentX = x + padding;

        for (int i = 0; i < name.length(); i++) {
            String character = String.valueOf(name.charAt(i));
            float charPosition = (float) i / (name.length() - 1);
            float gradientPosition = (charPosition + (float) time * 2) % 2.0f;

            float gradientValue;
            if (gradientPosition <= 1.0f) {
                gradientValue = gradientPosition;
            } else {
                gradientValue = 2.0f - gradientPosition;
            }

            gradientValue = (float) ((Math.sin((gradientValue - 0.5f) * Math.PI) + 1) / 2);
            // Panel: имя следует выбранному в ClickGui цвету текста (getTextColor), без акцента/градиента темы.
            Color gradientColor = link
                    ? (fade.getValue() ? ColorUtils.gradient(gradientValue) : ColorUtils.getPrimaryColor())
                    : ColorUtils.getTextColor(255);

            Render2D.drawFont(e.getContext().getMatrices(), font.getFont(fontSize), character, currentX, textY, gradientColor);
            currentX += font.getWidth(character, fontSize);
        }

        // Panel: значения FPS/MS и лейблы следуют выбранному в ClickGui цвету текста (getTextColor).
        Color textColor = link ? LinkHudStyle.text(255) : ColorUtils.getTextColor(255);
        Color labelColor = link ? LinkHudStyle.textSecondary(210) : ColorUtils.getTextColor(200);
        // Panel (старый вид): иконки FPS/MS нейтрального серого, без зелёного акцента темы.
        Color iconColor = link ? (fade.getValue() ? ColorUtils.getGlobalColor() : ColorUtils.getPrimaryColor())
                : new Color(190, 198, 212, 200);
        Color infoSeparatorColor = link ? LinkHudStyle.textSecondary(150) : new Color(180, 188, 202, 150);
        // Живой/статичный акцент для фирменного знака — корректен и на светлой теме.
        Color accentDot = fade.getValue() ? ColorUtils.getGlobalColor() : ColorUtils.getPrimaryColor();

        // Точка после имени: в Link — фирменная акцентная точка с glow; в Panel (старый вид) — плоский серый буллет.
        float dotSize = 4f;
        float dotX = x + padding + textWidth + compactSpacing + bulletSpacing;
        float dotY = y + (height - dotSize) / 2f;
        if (link) {
            // 1:1 donor watermark separator: soft layered halo around a round dot.
            for (int i = 3; i >= 1; i--) {
                float ed = i * 1.1f;
                int a = Math.round((0.12f + (3 - i) * 0.05f) * 255 * toggledAnimation.getValue());
                Render2D.drawRoundedRect(
                        e.getContext().getMatrices(),
                        dotX - ed, dotY - ed,
                        dotSize + 2 * ed, dotSize + 2 * ed,
                        (dotSize + 2 * ed) / 2f,
                        ColorUtils.alpha(accentDot, a)
                );
            }
            Render2D.drawRoundedRect(e.getContext().getMatrices(), dotX, dotY, dotSize, dotSize, dotSize / 2f, accentDot);
        } else {
            Render2D.drawRoundedRect(e.getContext().getMatrices(), dotX, dotY, dotSize, dotSize, dotSize / 2f, infoSeparatorColor);
        }

        float currentInfoX = x + padding + textWidth + compactSpacing + bulletWidth + compactSpacing;

        Render2D.drawFont(e.getContext().getMatrices(), iconFont.getFont(infoIconSize), fpsIcon, currentInfoX, infoIconY, iconColor);
        currentInfoX += fpsIconWidth + iconTextSpacing;

        fpsAnim.update(fpsValue);
        drawAnimatedValue(e.getContext().getMatrices(), fpsAnim, font, fontSize, currentInfoX, y + 4f, textColor);
        currentInfoX += fpsValueWidth + valueLabelSpacing;
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.SEMIBOLD.getFont(labelFontSize), fpsLabel, currentInfoX, y + 5.0f, labelColor);
        currentInfoX += fpsLabelWidth + compactSpacing;

        Render2D.drawRoundedRect(e.getContext().getMatrices(),
                currentInfoX + bulletSpacing, y + (height - bulletSize) / 2f,
                bulletSize, bulletSize, bulletSize / 2f, infoSeparatorColor);
        currentInfoX += bulletWidth + compactSpacing;

        Render2D.drawFont(e.getContext().getMatrices(), iconFont.getFont(infoIconSize), pingIcon, currentInfoX, infoIconY, iconColor);
        currentInfoX += pingIconWidth + iconTextSpacing;

        Render2D.drawFont(e.getContext().getMatrices(), font.getFont(fontSize), pingValue, currentInfoX, y + 4f, textColor);
        currentInfoX += pingValueWidth + valueLabelSpacing;
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.SEMIBOLD.getFont(labelFontSize), pingLabel, currentInfoX, y + 5.0f, labelColor);

        e.getContext().getMatrices().popMatrix();
        endUiScale(e.getContext().getMatrices());
        setUiScaledBounds(x, y, totalWidth, height, uiScale);
        super.onRender2D(e);
    }

    private static String formatPing(int ping) {
        if (ping < 0) {
            return "0";
        }
        if (ping > 999) {
            return "999+";
        }
        return String.valueOf(ping);
    }

    /**
     * Одометр: общий префикс/суффикс стоят на месте, сменившиеся цифры уезжают вверх с затуханием,
     * новые въезжают снизу (паттерн таймеров зелий/задержек, но своим шрифтом и кеглем).
     */
    private static void drawAnimatedValue(Object ms, AnimatedString anim,
                                          Font font, float fontSize, float x, float y, Color color) {
        if (!anim.isAnimating()) {
            Render2D.drawFont(ms, font.getFont(fontSize), anim.getCurrent(), x, y, color);
            return;
        }
        float t = Math.max(0f, Math.min(1f, anim.progress()));
        String previous = anim.getPrevious();
        String current = anim.getCurrent();
        int prefixLen = AnimatedTextHelper.getPrefixLength(previous, current);
        int suffixLen = AnimatedTextHelper.getSuffixLength(previous, current, prefixLen);

        String prefix = AnimatedTextHelper.substringSafe(current, 0, prefixLen);
        String prevMid = AnimatedTextHelper.substringSafe(previous, prefixLen, previous.length() - suffixLen);
        String currMid = AnimatedTextHelper.substringSafe(current, prefixLen, current.length() - suffixLen);
        String suffix = AnimatedTextHelper.substringSafe(current, current.length() - suffixLen, current.length());

        float prefixW = font.getWidth(prefix, fontSize);
        float currMidW = font.getWidth(currMid, fontSize);
        float shift = 6f;
        Color prevC = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * (1f - t)));
        Color currC = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * t));

        Render2D.drawFont(ms, font.getFont(fontSize), prefix, x, y, color);
        Render2D.drawFont(ms, font.getFont(fontSize), suffix, x + prefixW + currMidW, y, color);
        Render2D.drawFont(ms, font.getFont(fontSize), prevMid, x + prefixW, y - shift * t, prevC);
        Render2D.drawFont(ms, font.getFont(fontSize), currMid, x + prefixW, y + shift * (1f - t), currC);
    }

}
