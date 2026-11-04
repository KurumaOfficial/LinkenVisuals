package ru.method.linkenvisuals.hud.impl;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.managers.system.ModuleManager;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class KeyBindsHud extends HudElement {

    private final InfinityAnimation widthAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation heightAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation fadeAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);

    public KeyBindsHud() {
        super("KeyBindsHud");
    }

    private record Entry(String name, String key, boolean toggled) {}

    // Кэш списка: обход всех модулей с bind.toString() каждый кадр не нужен;
    // задержка до 200 мс скрыта fade-анимацией той же длительности.
    private List<Entry> cachedEntries;
    private long cacheExpiresAtMs;
    private boolean cachedPreview;

    private List<Entry> collectEntries(boolean preview) {
        List<Entry> entries = new ArrayList<>();
        ModuleManager moduleManager = LinkenVisuals.getInstance().getModuleManager();
        if (moduleManager != null) {
            for (Module module : moduleManager.getModules()) {
                Bind bind = module.getBind();
                if (module instanceof UI) continue;
                if (bind == null || bind.isEmpty() || !module.isToggled()) continue;
                entries.add(new Entry(module.getDisplayName(), bind.toString(), true));
            }
        }

        if (preview && entries.isEmpty()) {
            entries.add(new Entry("Sprint", "R", true));
            entries.add(new Entry("Fullbright", "F", true));
            entries.add(new Entry("DiscordRPC", "G", true));
        }

        return entries;
    }

    private boolean shouldShow(List<Entry> entries) {
        if (ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive()) return true;
        return !entries.isEmpty();
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        boolean isPreview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        long now = System.currentTimeMillis();
        if (cachedEntries == null || isPreview != cachedPreview || now >= cacheExpiresAtMs) {
            cachedEntries = collectEntries(isPreview);
            cachedPreview = isPreview;
            cacheExpiresAtMs = now + 200L;
        }
        List<Entry> entries = cachedEntries;
        boolean visible = shouldShow(entries);
        float fadeValue = fadeAnimation.animate(visible ? 1.0f : 0.0f, 200);

        if (fadeValue <= 0.0f) return;

        float posX = getX();
        float posY = getY();
        float padding = HUD_PADDING;
        float fontSize = HUD_FONT_SIZE;
        float lineHeight = HUD_LINE_HEIGHT;
        float titleFontSize = HUD_TITLE_FONT_SIZE - 0.5f;
        float maxWidth = HUD_BASE_MAX_WIDTH - 10f;

        float keyFontSize = HUD_FONT_SIZE - 0.5f;
        float pillPadX = 4f;
        for (Entry entry : entries) {
            float nameWidth = Fonts.MEDIUM.getWidth(entry.name(), fontSize);
            float keyWidth = Fonts.MEDIUM.getWidth(entry.key(), keyFontSize);
            float pillWidth = keyWidth + pillPadX * 2f;
            // name + зазор + пилюля по строке (тот же расчёт ширины, что и при рендере)
            float totalWidth = nameWidth + pillWidth + padding * 3f;
            if (totalWidth > maxWidth) maxWidth = totalWidth;
        }

        String title = I18n.translate("elements.settings.elements.keybindshud");
        String icon = "S";
        float iconFontSize = 8f;
        Font iconFont = Fonts.ICONSNEW;

        float iconWidth = iconFont.getWidth(icon, iconFontSize);
        float iconSpacing = 4f;
        float titleWidth = Fonts.SEMIBOLD.getWidth(title, titleFontSize);
        // Шапка тоже участвует в минимальной ширине — длинный перевод заголовка не должен наезжать на иконку справа.
        maxWidth = Math.max(maxWidth, titleWidth + iconSpacing + iconWidth + padding * 2);

        float targetWidth = maxWidth + padding * 2;
        float targetHeight = (HUD_HEADER_BASE_HEIGHT - 2f) + (entries.size() * lineHeight) + (padding - 1f);

        float renderWidth = widthAnimation.animate(targetWidth, 200);
        float renderHeight = heightAnimation.animate(targetHeight, 200);

        if (isPreview) {
            renderWidth = targetWidth;
            renderHeight = targetHeight;
        }

        // Панель растёт в рантайме (ширина — от числа биндов и длины заголовка, высота — от числа строк).
        // Драг-кламп в HudElement отрабатывает лишь на РАНЕЕ сохранённом размере, поэтому при добавлении
        // биндов или смене разрешения панель уезжала за край экрана. Клампим позицию по фактическому
        // экранному следу (с учётом UI-скейла) перед отрисовкой — setUiScaledBounds ниже сохранит
        // вправленную позицию, так что элемент сам «возвращается» в экран.
        float uiScale = getUiScale();
        float scaledScreenW = mc.getWindow().getScaledWidth();
        float scaledScreenH = mc.getWindow().getScaledHeight();
        posX = Math.max(0f, Math.min(posX, Math.max(0f, scaledScreenW - renderWidth * uiScale)));
        posY = Math.max(0f, Math.min(posY, Math.max(0f, scaledScreenH - renderHeight * uiScale)));

        int globalAlpha = Math.max(0, Math.min(255, Math.round(255 * fadeValue)));
        beginUiScale(e.getContext().getMatrices(), posX, posY);
        e.getContext().getMatrices().pushMatrix();
        float[] dragOffset = getDragOffset();
        float scaleValue = toggledAnimation.getValue() * fadeValue * getDragScale();
        float centerX = posX + renderWidth / 2 + dragOffset[0];
        float centerY = posY + renderHeight / 2 + dragOffset[1];
        e.getContext().getMatrices().translate(centerX, centerY);
        e.getContext().getMatrices().scale(scaleValue, scaleValue);
        e.getContext().getMatrices().translate(-centerX, -centerY);

        boolean link = LinkHudStyle.active();
        float panelRadius = LinkHudStyle.radius(renderWidth - 2f, renderHeight - 2f);
        Color accent = ColorUtils.getPrimaryColor();
        if (link) {
            // Link-стиль: панель в цветах темы выбранного варианта оформления.
            LinkHudStyle.panel(e.getContext().getMatrices(), posX, posY, renderWidth - 2f, renderHeight - 2f, panelRadius, fadeValue);
        } else {
            Color bgColor = ColorUtils.getGlassColor(255);
            Render2D.drawLiquidRectHud(
                    e.getContext().getMatrices(), posX, posY, renderWidth - 2f, renderHeight - 2f,
                    new BorderRadius(10f),
                    new ColorRGBA(bgColor),
                    2.5f, 22.0f, 11f, 175.0f, true, 0f, 0.19f);
        }

        // Акцентная боковая полоса слева убрана — с новым видом панели (Card) она читалась как
        // лишняя белёсая риска у края.
        float contentLeft = posX + padding;

        float titleStartX = posX + padding;

        float iconHeight = iconFont.getHeight(iconFontSize);
        // Центрируем иконку по высоте шапки — иначе высокий глиф вылезал за скруглённый верхний угол.
        float iconY = posY + (HUD_HEADER_BASE_HEIGHT - iconHeight) / 2f;

        Color iconColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);
        Color titleColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);

        try {
            Render2D.drawFont(e.getContext().getMatrices(), Fonts.SEMIBOLD.getFont(titleFontSize), title, titleStartX, posY + padding - 1f, titleColor);
        } catch (Exception ignored) {
        }
        float iconX = posX + renderWidth - padding - iconWidth;
        drawSafeText(e.getContext(), Fonts.ICONSNEW, iconFontSize, icon, iconX, iconY, iconColor);

        // --- Разделитель под шапкой: только в Link-режиме. Старый Panel-вид — без акцентного разделителя. ---
        float dividerY = posY + HUD_HEADER_BASE_HEIGHT - 2f;
        if (link) {
            Render2D.drawRoundedRect(e.getContext().getMatrices(), contentLeft, dividerY,
                    (posX + renderWidth - padding) - contentLeft, 0.8f, 0.4f, ColorUtils.alpha(accent, Math.round(110 * fadeValue)));
        }

        float yOffset = HUD_HEADER_BASE_HEIGHT;
        Color textColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);
        // Классический (старый Panel) цвет клавиши — текст, смешанный с фоном, без акцента.
        Color classicKeyColor = ColorUtils.alpha(ColorUtils.mix(ColorUtils.getTextColor(255), ColorUtils.getGlassBase(), 0.35f), globalAlpha);
        for (Entry entry : entries) {
            float textY = posY + yOffset + (lineHeight - fontSize) / 2f - 2f;
            String keyText = entry.key();
            float keyW = Fonts.MEDIUM.getWidth(keyText, keyFontSize);

            if (link) {
                // Link-режим: клавиша в виде акцентной пилюли/кружка (фирменный элемент ClickGUI Link).
                float pillH = lineHeight - 3f;
                // Ширина пилюли — не меньше круга (pillH) для 1-символьных клавиш, но всегда вмещает текст
                // (M5/MB и т.п. шире круга): раньше при length<=2 ширина жёстко = pillH и «M5» вылезал за пилюлю.
                float pillW = Math.max(pillH, keyW + pillPadX * 2f);
                float pillX = posX + renderWidth - padding - pillW - 1f;
                float pillY = posY + yOffset + (lineHeight - pillH) / 2f;
                float pillRadius = Math.min(LinkHudStyle.radius(pillW, pillH), pillH / 2f);
                drawSafeText(e.getContext(), Fonts.MEDIUM, fontSize,
                        fitText(Fonts.MEDIUM, entry.name(), fontSize, pillX - contentLeft - 4f),
                        contentLeft, textY, textColor);
                Render2D.drawRoundedRect(e.getContext().getMatrices(), pillX, pillY, pillW, pillH, pillRadius,
                        ColorUtils.alpha(accent, Math.round(38 * fadeValue)));
                Render2D.drawBorder(e.getContext().getMatrices(), pillX, pillY, pillW, pillH, pillRadius, 0.8f,
                        new ColorRGBA(ColorUtils.alpha(accent, Math.round(90 * fadeValue))));
                float keyX = pillX + (pillW - keyW) / 2f;
                float keyCenterY = pillY + (pillH - Fonts.MEDIUM.getHeight(keyFontSize)) / 2f;
                try {
                    Render2D.drawFont(e.getContext().getMatrices(), Fonts.MEDIUM.getFont(keyFontSize), keyText,
                            keyX, keyCenterY, ColorUtils.alpha(accent, globalAlpha));
                } catch (Exception ignored) {}
            } else {
                // Panel-режим (старый вид): клавиша простым приглушённым текстом справа — без пилюли, обводки, акцента.
                float keyX = posX + renderWidth - padding - keyW - 2f;
                drawSafeText(e.getContext(), Fonts.MEDIUM, fontSize,
                        fitText(Fonts.MEDIUM, entry.name(), fontSize, keyX - contentLeft - 4f),
                        contentLeft, textY, textColor);
                try {
                    Render2D.drawFont(e.getContext().getMatrices(), Fonts.MEDIUM.getFont(keyFontSize), keyText,
                            keyX, textY, classicKeyColor);
                } catch (Exception ignored) {}
            }

            yOffset += lineHeight;
        }

        e.getContext().getMatrices().popMatrix();
        endUiScale(e.getContext().getMatrices());

        setUiScaledBounds(posX, posY, renderWidth, renderHeight, uiScale);
        super.onRender2D(e);
    }
}
