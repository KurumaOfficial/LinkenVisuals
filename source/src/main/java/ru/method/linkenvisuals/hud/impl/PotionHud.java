package ru.method.linkenvisuals.hud.impl;


import com.mojang.blaze3d.systems.RenderSystem;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.AnimatedTextHelper;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import ru.method.linkenvisuals.utils.text.ColorCodes;
import java.util.Set;
import ru.method.linkenvisuals.hud.LinkHudStyle;

public class PotionHud extends HudElement {
    /**
     * Масштаб окна «Эффекты» по умолчанию. Было ДВА последовательных уменьшения на 15 % (0.85·0.85),
     * затем по требованию окно увеличено на 20 % (·1.2). Одна константа вместо множителей в двух местах
     * рендера, чтобы дальше крутить размер в одной точке.
     */
    private static final float EFFECTS_SCALE = 0.85f * 0.85f * 1.2f * 0.95f;


    private final InfinityAnimation widthAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation heightAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation fadeAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    /** Панель была скрыта — следующий кадр это «появление»: размер берём сразу целевым (как TargetHud). */
    private boolean appearing = true;
    /** Высота карточки (стиль «Карточка») с прошлого кадра — по ней понимаем, есть ли что показывать. */
    private int lastCardH;
    private final Map<String, AnimatedString> durationAnimations = new LinkedHashMap<>();
    // Кэш очищенных имён: набор имён эффектов фиксирован, 4 регулярки на эффект каждый кадр не нужны.
    private final Map<String, String> strippedNameCache = new java.util.HashMap<>();

    private static final float EFFECT_ICON_SIZE = 12f;
    private static final float EFFECT_ICON_GAP = 2f;

    public PotionHud() {
        super("PotionHud");
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private static String getDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "∞";
        int duration = effect.getDuration();
        int mins = duration / 1200;
        int secs = (duration % 1200) / 20;
        // Без String.format (создаёт Formatter каждый эффект-кадр): секунды 0..59, ручной пад до 2 цифр.
        return secs < 10 ? (mins + ":0" + secs) : (mins + ":" + secs);
    }

    private static String toRoman(int number) {
        if (number <= 0) return "";
        return (number < ROMAN.length) ? ROMAN[number] : String.valueOf(number); // ROMAN — static, без аллокации массива
    }

    /** Затемнение RGB на ~30% (×0.7) для читаемости семантических цветов таймера на СВЕТЛОЙ теме. */
    private Color darken(Color c) {
        return new Color(
                Math.round(c.getRed() * 0.7f),
                Math.round(c.getGreen() * 0.7f),
                Math.round(c.getBlue() * 0.7f),
                c.getAlpha()
        );
    }

    private boolean shouldShow() {
        if (ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive()) return true;
        return mc.player != null && !mc.player.getStatusEffects().isEmpty();
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        // Стиль «Карточка»: элемент заменён готовым окном «Эффекты» из visual-test (только реальные).
        if (LinkHudStyle.active() && LinkHudStyle.variant() == LinkHudStyle.Variant.Card) {
            float x = getX(), y = getY();
            var ms = e.getContext().getMatrices();
            float ui = beginUiScale(ms, x, y);
            // Появление как у TargetHud: fade+scale от центра карточки, а не мгновенное возникновение.
            // Наличие контента берём по высоте прошлого кадра (карточку всё равно рисуем — при scale≈0
            // её не видно, зато узнаём, появились ли эффекты, и со следующего кадра она всплывает).
            float fade = fadeAnimation.animate(lastCardH > 0 ? 1.0f : 0.0f, 200);
            float t = Math.max(0.0001f, toggledAnimation.getValue() * getDragScale() * EFFECTS_SCALE * fade);
            float cx = x + 150f * EFFECTS_SCALE / 2f;
            float cy = y + Math.max(1, lastCardH) * EFFECTS_SCALE / 2f;
            ms.pushMatrix();
            ms.translate(cx, cy);
            ms.scale(t, t);
            ms.translate(-cx, -cy);
            int h = unkw.visualtest.client.hud.VisualHud.cardEffects(e.getContext(), mc, Math.round(x), Math.round(y));
            lastCardH = h;
            ms.popMatrix();
            endUiScale(ms);
            if (h > 0) {
                setUiScaledBounds(x, y, 150 * EFFECTS_SCALE, h * EFFECTS_SCALE, ui); // хитбокс = видимой карточке
            }
            super.onRender2D(e);
            return;
        }

        boolean hasEffects = shouldShow();
        float fadeValue = fadeAnimation.animate(hasEffects ? 1.0f : 0.0f, 200);

        if (fadeValue <= 0.0f) {
            appearing = true;   // скрылись — следующий показ снова «появление»
            return;
        }

        Collection<StatusEffectInstance> effectsToRender;
        boolean isPreview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();

        if (isPreview) {
            if (mc.player != null && !mc.player.getStatusEffects().isEmpty()) {
                effectsToRender = mc.player.getStatusEffects();
            } else {
                effectsToRender = new ArrayList<>();
                effectsToRender.add(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1));
                effectsToRender.add(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 0));
                effectsToRender.add(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 3600, 0));
            }
        } else {
            effectsToRender = mc.player.getStatusEffects();
        }

        float posX = getX();
        float posY = getY();
        float padding = HUD_PADDING;
        float fontSize = HUD_FONT_SIZE;
        float lineHeight = HUD_LINE_HEIGHT;
        float titleFontSize = HUD_TITLE_FONT_SIZE - 0.5f;
        float maxWidth = HUD_BASE_MAX_WIDTH - 10f;

        // Один проход по эффектам: имя/уровень/таймер и их ширины собираются здесь и
        // переиспользуются в цикле отрисовки (раньше getString/getDuration/getWidth считались дважды).
        record Row(StatusEffectInstance effect, String name, String level, String duration,
                   float nameWidth, float levelWidth, float timeWidth) {}
        java.util.List<Row> rows = new ArrayList<>(effectsToRender.size());
        for (StatusEffectInstance effect : effectsToRender) {
            StatusEffect potion = effect.getEffectType().value();
            String name = strippedNameCache.computeIfAbsent(potion.getName().getString(), ColorCodes::strip);
            String level = toRoman(effect.getAmplifier() + 1);
            String duration = getDuration(effect);
            float nameWidth = Fonts.MEDIUM.getWidth(name, fontSize);
            float levelWidth = level.isEmpty() ? 0 : Fonts.MEDIUM.getWidth(level, fontSize);
            float timeWidth = Fonts.MEDIUM.getWidth(duration, fontSize);
            rows.add(new Row(effect, name, level, duration, nameWidth, levelWidth, timeWidth));
            float totalWidth = EFFECT_ICON_SIZE + EFFECT_ICON_GAP + nameWidth + levelWidth + timeWidth + padding * 4;
            if (totalWidth > maxWidth) maxWidth = totalWidth;
        }

        String title = I18n.translate("elements.settings.elements.potionhud");
        String icon = "M";
        float iconFontSize = 8f;
        Font iconFont = Fonts.ICONSNEW;

        float iconWidth = iconFont.getWidth(icon, iconFontSize);
        float iconSpacing = 4f;
        float titleWidth = Fonts.SEMIBOLD.getWidth(title, titleFontSize);
        // Шапка тоже участвует в минимальной ширине — длинный перевод заголовка не должен наезжать на иконку справа.
        maxWidth = Math.max(maxWidth, titleWidth + iconSpacing + iconWidth + padding * 2);

        float targetWidth = maxWidth + padding * 2;
        float targetHeight = (HUD_HEADER_BASE_HEIGHT - 2f) + (effectsToRender.size() * lineHeight) + (padding - 1f);

        // Появление как у TargetHud: панель сразу нужного размера и «всплывает» через fade+scale.
        // Раньше ширина/высота ехали с нуля вместе с fade — окно разворачивалось из точки.
        long sizeMs = appearing ? 1L : 200L;
        float renderWidth = widthAnimation.animate(targetWidth, sizeMs);
        float renderHeight = heightAnimation.animate(targetHeight, sizeMs);
        appearing = false;

        if (isPreview) {
            renderWidth = targetWidth;
            renderHeight = targetHeight;
        }

        int globalAlpha = Math.max(0, Math.min(255, Math.round(255 * fadeValue)));
        float uiScale = beginUiScale(e.getContext().getMatrices(), posX, posY);
        e.getContext().getMatrices().pushMatrix();
        float[] dragOffset = getDragOffset();
        float scaleValue = toggledAnimation.getValue() * fadeValue * getDragScale() * EFFECTS_SCALE;
        float centerX = posX + renderWidth / 2 + dragOffset[0];
        float centerY = posY + renderHeight / 2 + dragOffset[1];
        e.getContext().getMatrices().translate(centerX, centerY);
        e.getContext().getMatrices().scale(scaleValue, scaleValue);
        e.getContext().getMatrices().translate(-centerX, -centerY);

        boolean link = LinkHudStyle.active();
        // Единое сглаживание как у ClickGUI / ScoreboardHud (НЕ хардкод 10f).
        float panelRadius = LinkHudStyle.radius(renderWidth - 2f, renderHeight - 2f);
        Color accent = ColorUtils.getPrimaryColor();
        if (link) {
            // Link-стиль: панель в цветах темы (тёмная/светлая) выбранного варианта оформления.
            // panel сам рисует фон+градиент+аутлайн варианта — рамку НЕ дублируем.
            LinkHudStyle.panel(e.getContext().getMatrices(), posX, posY, renderWidth - 2f, renderHeight - 2f, panelRadius, fadeValue);
        } else {
            // Стеклянный дефолт как у CooldownsHud (Задержка): единый liquid-glass фон.
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

        float titleStartX = contentLeft;

        float titleHeight = Fonts.ROUNDBOLD.getHeight(titleFontSize);
        float iconHeight = iconFont.getHeight(iconFontSize);
        // Центрируем иконку по высоте шапки (а не по тексту заголовка) — иначе высокий глиф
        // уезжал вверх и вылезал за скруглённый верхний угол панели.
        float iconY = posY + (HUD_HEADER_BASE_HEIGHT - iconHeight) / 2f;

        Color iconColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);
        Color titleColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);

        // Заголовок одним вызовом (как в CooldownsHud) — посимвольный цикл терял кернинг и плодил draw-вызовы.
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.SEMIBOLD.getFont(titleFontSize), title, titleStartX, posY + padding - 1f, titleColor);
        float iconX = posX + renderWidth - padding - iconWidth;
        drawSafeText(e.getContext(), Fonts.ICONSNEW, iconFontSize, icon, iconX, iconY, iconColor);

        // Тонкий разделитель под шапкой (как у ScoreboardHud) — отбивает заголовок, компактно.
        float dividerY = posY + HUD_HEADER_BASE_HEIGHT - 2f;
        float dividerW = (posX + renderWidth - padding) - contentLeft;
        if (dividerW > 0f) {
            Render2D.drawRoundedRect(e.getContext().getMatrices(), contentLeft, dividerY, dividerW, 0.8f,
                    0.4f, ColorUtils.alpha(accent, Math.round(110f * fadeValue)));
        }

        float yOffset = HUD_HEADER_BASE_HEIGHT;
        Color textColor = ColorUtils.getTextColor(255);
        Set<String> activeKeys = new HashSet<>();
        for (Row row : rows) {
            StatusEffectInstance effect = row.effect();
            float rowY = posY + yOffset;
            float textY = posY + yOffset + (lineHeight - fontSize) / 2f - 2f;

            float effectIconX = contentLeft;
            float effectIconY = rowY + (lineHeight - EFFECT_ICON_SIZE) / 2f - 1f;
            drawEffectIcon(e.getContext(), effect, effectIconX, effectIconY, EFFECT_ICON_SIZE, globalAlpha);

            float nameX = contentLeft + EFFECT_ICON_SIZE + EFFECT_ICON_GAP;
            String level = row.level();
            String duration = row.duration();
            float timeX = posX + renderWidth - padding - row.timeWidth() - 2f;
            // Пока панель дорастает до targetWidth, имя обрезаем по ТЕКУЩЕЙ renderWidth —
            // иначе длинное имя наезжает на таймер справа. Место под уровень резервируем.
            float levelReserve = level.isEmpty() ? 0f : row.levelWidth() + 2f;
            String effectName = fitText(Fonts.MEDIUM, row.name(), fontSize,
                    timeX - nameX - 4f - levelReserve);
            drawSafeText(e.getContext(), Fonts.MEDIUM, HUD_FONT_SIZE, effectName, nameX, textY,
                    link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha));
            if (!level.isEmpty()) {
                float levelX = nameX + Fonts.MEDIUM.getWidth(effectName, fontSize) + 2f;
                drawSafeText(e.getContext(), Fonts.MEDIUM, HUD_FONT_SIZE, level, levelX, textY,
                        ColorUtils.safeColor(ColorUtils.getGlobalColor().getRed(), ColorUtils.getGlobalColor().getGreen(), ColorUtils.getGlobalColor().getBlue(), globalAlpha));
            }
            boolean lightTheme = LinkHudStyle.isLightTheme();
            Color timeColor;
            if (effect.isInfinite()) {
                timeColor = lightTheme ? darken(Color.CYAN) : Color.CYAN;
            } else if (effect.getDuration() < 20 * 10) {
                timeColor = lightTheme ? darken(Color.RED) : Color.RED;
            } else if (effect.getDuration() < 20 * 30) {
                timeColor = lightTheme ? darken(Color.ORANGE) : Color.ORANGE;
            } else {
                // Нейтральный остаток: на светлой теме LIGHT_GRAY нечитаем → вторичный текст темы.
                timeColor = lightTheme ? LinkHudStyle.textSecondary(globalAlpha) : Color.LIGHT_GRAY;
            }
            String key = buildEffectKey(effect);
            AnimatedString animation = durationAnimations.computeIfAbsent(
                    key,
                    k -> new AnimatedString(220, Easing.EASE_OUT_CUBIC, duration)
            );
            animation.update(duration);
            activeKeys.add(key);
            drawAnimatedText(
                    e.getContext(),
                    animation,
                    timeX,
                    textY,
                    ColorUtils.safeColor(timeColor.getRed(), timeColor.getGreen(), timeColor.getBlue(), globalAlpha),
                    lineHeight * 0.6f,
                    Fonts.MEDIUM,
                    fontSize
            );
            yOffset += lineHeight;
        }
        durationAnimations.keySet().removeIf(key -> !activeKeys.contains(key));

        e.getContext().getMatrices().popMatrix();
        endUiScale(e.getContext().getMatrices());

        setUiScaledBounds(posX, posY, renderWidth, renderHeight, uiScale);
        super.onRender2D(e);
    }

    private String buildEffectKey(StatusEffectInstance effect) {
        StatusEffect potion = effect.getEffectType().value();
        String potionKey = (potion != null) ? potion.getTranslationKey() : "unknown";
        return potionKey + ":" + effect.getAmplifier();
    }

    private void drawAnimatedText(DrawContext context, AnimatedString animation, float x, float y, Color color, float shift, Font font, float fontSize) {
        boolean animating = animation.isAnimating();
        float progress = animation.progress();
        if (!animating) {
            drawSafeText(context, Fonts.MEDIUM, HUD_FONT_SIZE, animation.getCurrent(), x, y, color);
            return;
        }

        float easedProgress = Math.max(0f, Math.min(1f, progress));
        int baseAlpha = color.getAlpha();
        int prevAlpha = Math.round(baseAlpha * (1f - easedProgress));
        int nextAlpha = Math.round(baseAlpha * easedProgress);

        Color prevColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), prevAlpha);
        Color nextColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), nextAlpha);

        float prevY = y - shift * easedProgress;
        float nextY = y + shift * (1f - easedProgress);

        String previous = animation.getPrevious();
        String current = animation.getCurrent();
        int prefixLength = AnimatedTextHelper.getPrefixLength(previous, current);
        int suffixLength = AnimatedTextHelper.getSuffixLength(previous, current, prefixLength);

        String prefix = AnimatedTextHelper.substringSafe(current, 0, prefixLength);
        String prevMid = AnimatedTextHelper.substringSafe(previous, prefixLength, previous.length() - suffixLength);
        String currMid = AnimatedTextHelper.substringSafe(current, prefixLength, current.length() - suffixLength);
        String suffix = AnimatedTextHelper.substringSafe(current, current.length() - suffixLength, current.length());

        float prefixWidth = font.getWidth(prefix, fontSize);
        float currMidWidth = font.getWidth(currMid, fontSize);
        float prevMidWidth = font.getWidth(prevMid, fontSize);

        float midStartX = x + prefixWidth;
        float currMidX = midStartX;
        float prevMidX = midStartX + (currMidWidth - prevMidWidth) / 2f;

        drawSafeText(context, Fonts.MEDIUM, HUD_FONT_SIZE, prefix, x, y, color);
        drawSafeText(context, Fonts.MEDIUM, HUD_FONT_SIZE, suffix, midStartX + currMidWidth, y, color);

        drawSafeText(context, Fonts.MEDIUM, HUD_FONT_SIZE, prevMid, prevMidX, prevY, prevColor);
        drawSafeText(context, Fonts.MEDIUM, HUD_FONT_SIZE, currMid, currMidX, nextY, nextColor);
    }

    private void drawEffectIcon(DrawContext context, StatusEffectInstance effect, float x, float y, float size, int alpha) {
        if (effect == null || mc == null) return;
        try {
            net.minecraft.util.Identifier texture = net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effect.getEffectType());
            if (texture == null) return;
            int a = Math.max(0, Math.min(255, alpha));
            int tint = (a << 24) | 0xFFFFFF;
            int sizePx = Math.max(1, Math.round(size));
            context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), sizePx, sizePx, tint);
        } catch (Exception ignored) {
        }
    }

}
