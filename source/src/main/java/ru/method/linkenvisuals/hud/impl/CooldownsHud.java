package ru.method.linkenvisuals.hud.impl;


import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.AnimatedTextHelper;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.inv.CooldownUtils;
import ru.method.linkenvisuals.utils.inv.CooldownUtils.CooldownEntry;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.resource.language.I18n;


public class CooldownsHud extends HudElement {

    private final InfinityAnimation widthAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation heightAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation fadeAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    /** Панель была скрыта — следующий кадр это «появление»: размер берём сразу целевым (как TargetHud). */
    private boolean appearing = true;
    /** Высота карточки (стиль «Карточка») с прошлого кадра — по ней понимаем, есть ли что показывать. */
    private int lastCardH;
    private final Map<String, AnimatedString> cooldownAnimations = new LinkedHashMap<>();

    private static final float ITEM_ICON_SIZE = 12f;
    private static final float ITEM_ICON_GAP = 2f;
    // Запас слева под оверсайз-модели ресурспаков (крупный арбалет и т.п. торчал за рамку панели).
    private static final float ITEM_ICON_INSET = 3f;
    // Масштаб окна «Задержка» по умолчанию: −15 % (0.85), +20 % (·1.2), затем −10 % (·0.9) и ещё −10 % (·0.9). Одна точка на оба стиля.
    private static final float PANEL_SCALE = 0.85f * 1.2f * 0.9f * 0.9f * 0.95f;

    private record Tile(ItemStack stack, String keyLabel) {}

    // Кэш собранных кулдаунов: collectActive (рефлексия + скан инвентаря + ItemStack.copy + regex +
    // сортировка) стоит дорого, а сервер меняет кулдауны максимум раз в тик. Пересобираем раз в 50мс
    // (клиентский тик) — визуально идентично (formattedTime всё равно с точностью до секунды).
    private List<CooldownEntry> cachedActive = Collections.emptyList();
    private long cacheExpiresAtMs;

    public CooldownsHud() {
        super("CooldownsHud");
    }

    private boolean shouldShow(List<CooldownEntry> activeEntries) {
        if (ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive()) {
            return true;
        }
        return mc.player != null && activeEntries != null && !activeEntries.isEmpty();
    }

    private void drawItemIcon(EventRender2D e, ItemStack stack, float x, float y) {
        if (stack == null || stack.isEmpty()) return;
        try {
            float scale = ITEM_ICON_SIZE / 16f;
            e.getContext().getMatrices().pushMatrix();
            e.getContext().getMatrices().translate(x, y);
            e.getContext().getMatrices().scale(scale, scale);
            e.getContext().drawItem(mc.player, stack, 0, 0, 0);
            e.getContext().getMatrices().popMatrix();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        // Стиль «Карточка»: элемент заменён готовым окном «Задержки» из visual-test (секунды, реальные).
        if (LinkHudStyle.active() && LinkHudStyle.variant() == LinkHudStyle.Variant.Card) {
            float x = getX(), y = getY();
            var ms = e.getContext().getMatrices();
            float ui = beginUiScale(ms, x, y);
            // Появление как у TargetHud: fade+scale от центра карточки, а не мгновенное возникновение.
            // Наличие контента берём по высоте прошлого кадра (карточку всё равно рисуем — при scale≈0
            // её не видно, зато узнаём, появились ли кулдауны, и со следующего кадра она всплывает).
            float fade = fadeAnimation.animate(lastCardH > 0 ? 1.0f : 0.0f, 200);
            float t = Math.max(0.0001f, toggledAnimation.getValue() * getDragScale() * PANEL_SCALE * fade);
            float cx = x + 150f * PANEL_SCALE / 2f;
            float cy = y + Math.max(1, lastCardH) * PANEL_SCALE / 2f;
            ms.pushMatrix();
            ms.translate(cx, cy);
            ms.scale(t, t);
            ms.translate(-cx, -cy);
            int h = unkw.visualtest.client.hud.VisualHud.cardCooldowns(e.getContext(), mc, Math.round(x), Math.round(y));
            lastCardH = h;
            ms.popMatrix();
            endUiScale(ms);
            if (h > 0) {
                setUiScaledBounds(x, y, 150 * PANEL_SCALE, h * PANEL_SCALE, ui); // хитбокс = видимой карточке
            }
            super.onRender2D(e);
            return;
        }

        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        boolean isPreview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        long nowMs = System.currentTimeMillis();
        if (nowMs >= cacheExpiresAtMs) {
            cachedActive = CooldownUtils.collectActive(mc, tickDelta);
            cacheExpiresAtMs = nowMs + 50L;
        }
        List<CooldownEntry> activeCooldowns = cachedActive;
        List<CooldownEntry> entries = !activeCooldowns.isEmpty()
                ? activeCooldowns
                : (isPreview ? CooldownUtils.preview() : Collections.emptyList());

        boolean shouldRender = shouldShow(activeCooldowns);
        float fadeValue = fadeAnimation.animate(shouldRender ? 1.0f : 0.0f, 200);

        if (fadeValue <= 0.0f) {
            appearing = true;   // скрылись — следующий показ снова «появление»
            return;
        }

        float posX = getX();
        float posY = getY();
        float padding = HUD_PADDING;
        float fontSize = HUD_FONT_SIZE;
        float titleFontSize = HUD_TITLE_FONT_SIZE - 0.5f;
        float lineHeight = Math.max(HUD_LINE_HEIGHT, ITEM_ICON_SIZE);
        float maxWidth = HUD_BASE_MAX_WIDTH;

        for (CooldownEntry entry : entries) {
            String name = entry.displayName();
            if (name.length() > 26) {
                name = name.substring(0, 23) + "...";
            }
            String time = entry.formattedTime();
            float nameWidth = Fonts.MEDIUM.getWidth(name, fontSize);
            float timeWidth = Fonts.MEDIUM.getWidth(time, fontSize);
            float totalWidth = padding * 4 + ITEM_ICON_INSET + ITEM_ICON_SIZE + ITEM_ICON_GAP + nameWidth + timeWidth;
            if (totalWidth > maxWidth) {
                maxWidth = totalWidth;
            }
        }

        String title = I18n.translate("elements.settings.elements.cooldownshud");
        String icon = "Q";
        float iconFontSize = 8f;
        Font iconFont = Fonts.ICONSNEW;

        float iconWidth = iconFont.getWidth(icon, iconFontSize);
        float iconSpacing = 4f;
        float titleWidth = Fonts.SEMIBOLD.getWidth(title, titleFontSize);
        // Шапка тоже участвует в минимальной ширине — длинный перевод заголовка не должен наезжать на иконку справа.
        maxWidth = Math.max(maxWidth, titleWidth + iconSpacing + iconWidth + padding * 2);

        float targetWidth = maxWidth + padding * 2;
        float targetHeight = (HUD_HEADER_BASE_HEIGHT - 2f) + (entries.size() * lineHeight) + (padding - 1f);

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
        float scaleValue = toggledAnimation.getValue() * fadeValue * getDragScale() * PANEL_SCALE; // окно «Задержка»
        float centerX = posX + renderWidth / 2f + dragOffset[0];
        float centerY = posY + renderHeight / 2f + dragOffset[1];
        e.getContext().getMatrices().translate(centerX, centerY);
        e.getContext().getMatrices().scale(scaleValue, scaleValue);
        e.getContext().getMatrices().translate(-centerX, -centerY);

        boolean link = LinkHudStyle.active();
        if (link) {
            LinkHudStyle.panel(e.getContext().getMatrices(), posX, posY, renderWidth - 2f, renderHeight - 2f, LinkHudStyle.radius(renderWidth - 2f, renderHeight - 2f), fadeValue);
        } else {
            Color bgColor = ColorUtils.getGlassColor(255);
            Render2D.drawLiquidRectHud(
                    e.getContext().getMatrices(),
                    posX,
                    posY,
                    renderWidth - 2f,
                    renderHeight - 2f,
                    new BorderRadius(10f),
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

        float titleStartX = posX + padding;

        float titleHeight = Fonts.ROUNDBOLD.getHeight(titleFontSize);
        float iconHeight = iconFont.getHeight(iconFontSize);
        // Центрируем иконку по высоте шапки — иначе высокий глиф вылезал за скруглённый верхний угол.
        float iconY = posY + (HUD_HEADER_BASE_HEIGHT - iconHeight) / 2f;

        Color iconColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);
        Color titleColor = link ? LinkHudStyle.text(globalAlpha) : ColorUtils.getTextColor(globalAlpha);

        // Заголовок — одним вызовом (MSDF-рендер сам раскладывает глифы с кернингом). Посимвольный
        // цикл давал N строковых аллокаций и N render-вызовов за кадр без визуальной разницы, к тому же
        // терял кернинг (ширина titleWidth считается по всей строке — была рассинхронизация).
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.SEMIBOLD.getFont(titleFontSize), title,
                titleStartX, posY + padding - 1f, titleColor);
        float iconX = posX + renderWidth - padding - iconWidth;
        drawSafeText(e.getContext(), Fonts.ICONSNEW, iconFontSize, icon, iconX, iconY, iconColor);

        float yOffset = HUD_HEADER_BASE_HEIGHT;
        Set<String> activeKeys = new HashSet<>();
        for (CooldownEntry entry : entries) {
            float rowY = posY + yOffset;
            float textY = rowY + (lineHeight - fontSize) / 2f - 2f;

            String itemName = entry.displayName();
            if (itemName.length() > 26) {
                itemName = itemName.substring(0, 23) + "...";
            }

            Tile tile = new Tile(entry.stack(), entry.formattedTime());

            float itemIconX = posX + padding + ITEM_ICON_INSET;
            float itemIconY = rowY + (lineHeight - ITEM_ICON_SIZE) / 2f - 1f;
            drawItemIcon(e, tile.stack(), itemIconX, itemIconY);

            float textStartX = posX + padding + ITEM_ICON_INSET + ITEM_ICON_SIZE + ITEM_ICON_GAP;
            String timeLabel = tile.keyLabel();
            float timeX = posX + renderWidth - padding - Fonts.MEDIUM.getWidth(timeLabel, fontSize) - 2f;
            // Пока панель дорастает до targetWidth, имя обрезаем по ТЕКУЩЕЙ renderWidth —
            // иначе длинное имя наезжает на время кулдауна справа.
            itemName = fitText(Fonts.MEDIUM, itemName, fontSize, timeX - textStartX - 4f);
            drawSafeText(
                    e.getContext(),
                    Fonts.MEDIUM,
                    HUD_FONT_SIZE,
                    itemName,
                    textStartX,
                    textY,
                    ColorUtils.getTextColor(globalAlpha)
            );

            Color timeColor;
            if (entry.remainingTicks() > 20f * 20f) {
                timeColor = new Color(130, 200, 255);
            } else if (entry.progress() > 0.75f) {
                timeColor = Color.RED;
            } else if (entry.progress() > 0.5f) {
                timeColor = Color.ORANGE;
            } else if (entry.progress() > 0.25f) {
                timeColor = Color.YELLOW;
            } else {
                timeColor = Color.GREEN;
            }

            String key = buildCooldownKey(entry);
            AnimatedString animation = cooldownAnimations.computeIfAbsent(
                    key,
                    k -> new AnimatedString(220, Easing.EASE_OUT_CUBIC, timeLabel)
            );
            animation.update(timeLabel);
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
        cooldownAnimations.keySet().removeIf(key -> !activeKeys.contains(key));

        e.getContext().getMatrices().popMatrix();
        endUiScale(e.getContext().getMatrices());

        setUiScaledBounds(posX, posY, renderWidth, renderHeight, uiScale);
        super.onRender2D(e);
    }

    private String buildCooldownKey(CooldownEntry entry) {
        return entry.displayName();
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
}
