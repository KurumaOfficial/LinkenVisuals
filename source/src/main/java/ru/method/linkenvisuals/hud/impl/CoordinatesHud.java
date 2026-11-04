package ru.method.linkenvisuals.hud.impl;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.notify.Notify;
import ru.method.linkenvisuals.utils.notify.NotifyIcons;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.world.MovementUtils;

import java.awt.Color;
import java.util.Locale;


public class CoordinatesHud extends HudElement {

    private final InfinityAnimation fadeAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation widthAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);

    private boolean lastCoordsBoundsValid = false;
    private float lastCoordsX = 0.0f;
    private float lastCoordsY = 0.0f;
    private float lastCoordsW = 0.0f;
    private float lastCoordsH = 0.0f;
    private String lastCoordsValue = null;
    private long lastCopyTimeMs = 0L;

    public CoordinatesHud() {
        super("CoordinatesHud");
    }

    private boolean shouldShow() {
        return ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive() || (mc.player != null && mc.world != null);
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        boolean hasCoords = shouldShow();
        float fadeValue = fadeAnimation.animate(hasCoords ? 1.0f : 0.0f, 200);

        if (fadeValue <= 0.0f) {
            lastCoordsBoundsValid = false;
            return;
        }

        float posX = getX();
        float posY = getY();
        float padding = 5f;
        float fontSize = 7.5f;
        float iconFontSize = 9f;
        String icon = "J";
        Font iconFont = Fonts.ICONSNEW;
        int x = 0, y = 0, z = 0;
        double bps = 0.0;

        if (mc.player != null) {
            x = (int) Math.floor(mc.player.getX());
            y = (int) Math.floor(mc.player.getY());
            z = (int) Math.floor(mc.player.getZ());
            bps = MovementUtils.getBps(mc.player);
        } else if (ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive()) {
            x = 1234;
            y = 64;
            z = -567;
            bps = 4.4;
        }

        String coordsText = " " + x + "  " + y + "  " + z;
        String coordsValue = x + " " + y + " " + z;
        // Без String.format (создаёт Formatter каждый кадр): ручной формат до 2 знаков.
        int bpsH = (int) Math.round(Math.abs(bps) * 100);
        String bpsFrac = (bpsH % 100 < 10 ? "0" : "") + (bpsH % 100);
        String bpsValue = (bps < 0 ? "-" : "") + (bpsH / 100) + "." + bpsFrac;
        String bpsLabel = "BPS";

        float labelFontSize = fontSize - 1f;
        float coordsWidth = Fonts.ROUNDBOLD.getWidth(coordsText, fontSize);
        float bpsValueWidth = Fonts.ROUNDBOLD.getWidth(bpsValue, fontSize);
        float bpsLabelWidth = Fonts.SEMIBOLD.getWidth(bpsLabel, labelFontSize);
        float valueLabelSpacing = 1.5f;
        float bulletSize = 4f;
        float bulletSpacing = 2f;
        float bulletWidth = bulletSize + bulletSpacing * 2f;
        float bpsBlockWidth = bpsValueWidth + valueLabelSpacing + bpsLabelWidth;
        float textWidth = coordsWidth + bulletWidth + bpsBlockWidth;

        float iconWidth = iconFont.getWidth(icon, iconFontSize);
        float iconSpacing = 4f;
        // Плашка чуть длиннее на 4px (откат прежних +24px, было слишком длинно).
        float extraLength = 4f;
        // Без верхнего капа: панель растёт от контента. С капом 144px большие координаты
        // (дальние базы, ±30 млн) рисовались ПОВЕРХ всего справа от панели.
        float targetWidth = iconWidth + iconSpacing + textWidth + (padding * 2) + extraLength;
        float targetHeight = 17f;

        float renderWidth = widthAnimation.animate(targetWidth, 200);

        boolean isPreview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        if (isPreview) {
            renderWidth = targetWidth;
        }

        int globalAlpha = Math.max(0, Math.min(255, Math.round(255 * fadeValue)));

        float uiScale = beginUiScale(e.getContext().getMatrices(), posX, posY);
        e.getContext().getMatrices().pushMatrix();
        float[] dragOffset = getDragOffset();
        float scaleValue = toggledAnimation.getValue() * fadeValue * getDragScale();
        float centerX = posX + renderWidth / 2 + dragOffset[0];
        float centerY = posY + targetHeight / 2 + dragOffset[1];
        e.getContext().getMatrices().translate(centerX, centerY);
        e.getContext().getMatrices().scale(scaleValue, scaleValue);
        e.getContext().getMatrices().translate(-centerX, -centerY);

        boolean link = LinkHudStyle.active();
        if (link) {
            LinkHudStyle.panel(e.getContext().getMatrices(), posX, posY, renderWidth, targetHeight, LinkHudStyle.radius(renderWidth, targetHeight), fadeValue);
        } else {
            Color bgColor = ColorUtils.getGlassColor(255);
            Render2D.drawLiquidRectHud(
                    e.getContext().getMatrices(),
                    posX,
                    posY,
                    renderWidth,
                    targetHeight,
                    new BorderRadius(Render2D.DEFAULT_CORNER_RADIUS),
                    new ColorRGBA(bgColor),
                    2.5f,
                    22.0f,
                    11f,
                    175.0f,
                    true,
                    0f,
                    0.19f
            );
        }

        float iconHeight = iconFont.getHeight(iconFontSize);
        float textHeight = Fonts.ROUNDBOLD.getHeight(fontSize);
        float centerYPos = posY + targetHeight / 2f;

        float iconY = centerYPos - iconHeight / 2f;
        float textY = centerYPos - textHeight / 2f;

        Color iconColor = ColorUtils.getGlobalColor();
        iconColor = new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), globalAlpha);

        // Иконка от левого края на ровный padding; текст — от правого края иконки + iconSpacing.
        // Левый отступ (padding), зазор иконка→текст (iconSpacing) и правый отступ (padding) теперь согласованы.
        float iconX = posX + padding;
        Render2D.drawFont(e.getContext().getMatrices(), iconFont.getFont(iconFontSize), icon, iconX, iconY, iconColor);

        float textX = iconX + iconWidth + iconSpacing;
        Color textColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);
        // На светлой теме светло-серый BPS-лейбл/буллет не видно на белом фоне — берём тёмно-серый.
        boolean lightTheme = LinkHudStyle.isLightTheme();
        Color labelColor = lightTheme
                ? new Color(90, 98, 112, Math.round(200 * fadeValue))
                : new Color(190, 198, 212, Math.round(200 * fadeValue));
        Color bulletColor = lightTheme
                ? new Color(110, 120, 134, Math.round(150 * fadeValue))
                : new Color(180, 190, 204, Math.round(150 * fadeValue));

        float currentX = textX;
        drawSafeText(e.getContext(), Fonts.ROUNDBOLD, 7.5f, coordsText, currentX, textY, textColor);

        float scaledX = centerX + (currentX - centerX) * scaleValue;
        float scaledY = centerY + (textY - centerY) * scaleValue;
        lastCoordsX = scaleUiX(posX, scaledX, uiScale);
        lastCoordsY = scaleUiY(posY, scaledY, uiScale);
        lastCoordsW = coordsWidth * scaleValue * uiScale;
        lastCoordsH = textHeight * scaleValue * uiScale;
        lastCoordsValue = coordsValue;
        lastCoordsBoundsValid = scaleValue > 0.001f && lastCoordsW > 0.001f && lastCoordsH > 0.001f;

        currentX += coordsWidth;

        Render2D.drawRoundedRect(e.getContext().getMatrices(),
                currentX + bulletSpacing, textY + (textHeight - bulletSize) / 2f,
                bulletSize, bulletSize, bulletSize / 2f, bulletColor);
        currentX += bulletWidth;

        drawSafeText(e.getContext(), Fonts.ROUNDBOLD, 7.5f, bpsValue, currentX, textY, textColor);
        currentX += bpsValueWidth + valueLabelSpacing;
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.SEMIBOLD.getFont(labelFontSize), bpsLabel, currentX, textY + 0.7f, labelColor);

        e.getContext().getMatrices().popMatrix();
        endUiScale(e.getContext().getMatrices());

        setUiScaledBounds(posX, posY, renderWidth, targetHeight, uiScale);
        super.onRender2D(e);
    }

    @Override
    protected boolean onMiddleClick(int mouseX, int mouseY) {
        if (!lastCoordsBoundsValid) return false;
        if (!MathUtils.isHovered(lastCoordsX, lastCoordsY, lastCoordsW, lastCoordsH, mouseX, mouseY)) return false;
        if (lastCoordsValue == null || lastCoordsValue.isBlank()) return true;

        long now = System.currentTimeMillis();
        if (now - lastCopyTimeMs < 250L) return true;
        lastCopyTimeMs = now;

        mc.keyboard.setClipboard(lastCoordsValue);
        LinkenVisuals.getInstance().getNotifyManager().add(new Notify(NotifyIcons.successIcon, "\u041a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u044b \u0441\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u044b", 1000));
        return true;
    }
}
