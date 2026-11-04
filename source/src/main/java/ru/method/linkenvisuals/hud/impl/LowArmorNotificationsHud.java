package ru.method.linkenvisuals.hud.impl;

import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LowArmorNotificationsHud extends HudElement {
    private static final long DISPLAY_TIME_MS = 3500L;
    private static final long PREVIEW_AFTER_CHAT_MS = 1000L;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    public LowArmorNotificationsHud() {
        super("LowArmorNotificationsHud");
    }

    public static void push(String message) {
        if (message == null || message.isBlank()) return;

        ENTRIES.removeIf(entry -> entry.message.equals(message));
        ENTRIES.add(new Entry(message));
        if (ENTRIES.size() > 4) {
            ENTRIES.remove(0);
        }
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        // Стиль «Карточка»: элемент заменён готовой плашкой «Низкая прочность» из visual-test (160x38).
        if (LinkHudStyle.active() && LinkHudStyle.variant() == LinkHudStyle.Variant.Card) {
            float x = getX(), y = getY();
            var ms = e.getContext().getMatrices();
            float ui = beginUiScale(ms, x, y);
            float t = toggledAnimation.getValue() * getDragScale();
            ms.pushMatrix();
            ms.translate(x, y);
            ms.scale(t, t);
            ms.translate(-x, -y);
            boolean drawn = unkw.visualtest.client.hud.VisualHud.cardDurability(e.getContext(), mc, Math.round(x), Math.round(y));
            ms.popMatrix();
            endUiScale(ms);
            if (drawn) {
                setUiScaledBounds(x, y, 112, 27, ui);
            }
            super.onRender2D(e);
            return;
        }

        boolean preview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        if (preview && ENTRIES.isEmpty()) {
            ENTRIES.add(new Entry("\u041f\u0440\u0435\u0434\u043c\u0435\u0442 \u0441\u043a\u043e\u0440\u043e \u0441\u043b\u043e\u043c\u0430\u0435\u0442\u0441\u044f: \u0448\u043b\u0435\u043c (12%)", true));
        }

        long now = System.currentTimeMillis();
        Iterator<Entry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            boolean visible = shouldKeepVisible(entry, preview, now);
            entry.animation.update(visible);
            if (!visible && entry.animation.getValue() <= 0.01f) {
                iterator.remove();
            }
        }

        if (ENTRIES.isEmpty()) return;

        float x = getX();
        float y = getY();
        float padding = 7f;
        float rowHeight = 19f;
        float gap = 5f;
        float fontSize = 8.4f;
        float iconSize = 10f;
        String icon = "!";
        float iconW = Fonts.BOLD.getWidth(icon, iconSize);
        float iconGap = 6f;
        float maxTextWidth = 0f;
        for (Entry entry : ENTRIES) {
            maxTextWidth = Math.max(maxTextWidth, Fonts.MEDIUM.getWidth(entry.message, fontSize));
        }
        float screenWidth = mc.getWindow().getScaledWidth();
        float width = Math.max(150f, Math.min(380f, maxTextWidth + iconW + iconGap + padding * 2f));
        x = MathHelper.clamp(x, 4f, Math.max(4f, screenWidth - width - 4f));
        float height = ENTRIES.size() * rowHeight + Math.max(0, ENTRIES.size() - 1) * gap;

        float uiScale = beginUiScale(e.getContext().getMatrices(), x, y);
        e.getContext().getMatrices().pushMatrix();
        float[] dragOffset = getDragOffset();
        float scale = toggledAnimation.getValue() * getDragScale();
        float centerX = x + width / 2f + dragOffset[0];
        float centerY = y + height / 2f + dragOffset[1];
        e.getContext().getMatrices().translate(centerX, centerY);
        e.getContext().getMatrices().scale(scale, scale);
        e.getContext().getMatrices().translate(-centerX, -centerY);

        float rowY = y;
        for (Entry entry : ENTRIES) {
            float alpha = Math.max(0f, Math.min(1f, entry.animation.getValue()));
            if (alpha <= 0.01f) continue;

            int a = Math.round(255f * alpha);
            if (LinkHudStyle.active()) {
                LinkHudStyle.panel(e.getContext().getMatrices(), x, rowY, width, rowHeight,
                        LinkHudStyle.radius(width, rowHeight), alpha);
            } else {
                Color bg = ColorUtils.getGlassColor(a);
                Render2D.drawLiquidRectHud(
                        e.getContext().getMatrices(),
                        x,
                        rowY,
                        width,
                        rowHeight,
                        new BorderRadius(9f),
                        new ColorRGBA(bg),
                        2.5f,
                        22.0f,
                        11f,
                        175.0f,
                        true,
                        0f,
                        0.19f
                );
            }

            // Иконка-восклицание жирным шрифтом (вместо прежней полосы-акцента), по центру по вертикали.
            float iconX = x + padding;
            float iconH = Fonts.BOLD.getHeight(iconSize);
            Render2D.drawFont(e.getContext().getMatrices(), Fonts.BOLD.getFont(iconSize), icon,
                    iconX, rowY + (rowHeight - iconH) / 2f, new Color(255, 86, 86, a));

            float textX = iconX + iconW + iconGap;
            float textH = Fonts.MEDIUM.getHeight(fontSize);
            // На светлых темах (Light/Purple) панель светлая — белый текст не читается, делаем чёрным.
            boolean darkText = LinkHudStyle.active() && LinkHudStyle.isLightTheme();
            Color textColor = darkText ? new Color(0, 0, 0, a) : new Color(255, 255, 255, a);
            Render2D.drawFont(
                    e.getContext().getMatrices(),
                    Fonts.MEDIUM.getFont(fontSize),
                    clip(entry.message, width - (textX - x) - padding, fontSize),
                    textX,
                    rowY + (rowHeight - textH) / 2f,
                    textColor
            );
            rowY += rowHeight + gap;
        }

        e.getContext().getMatrices().popMatrix();
        endUiScale(e.getContext().getMatrices());

        setUiScaledBounds(x, y, width, height, uiScale);
        super.onRender2D(e);
    }

    private String clip(String text, float maxWidth, float size) {
        if (Fonts.MEDIUM.getWidth(text, size) <= maxWidth) return text;
        String suffix = "...";
        String clipped = text;
        while (!clipped.isEmpty() && Fonts.MEDIUM.getWidth(clipped + suffix, size) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + suffix;
    }

    private boolean shouldKeepVisible(Entry entry, boolean preview, long now) {
        if (preview) {
            if (entry.preview) {
                entry.previewReleasedAt = -1L;
            }
            return true;
        }

        if (entry.preview) {
            if (entry.previewReleasedAt < 0L) {
                entry.previewReleasedAt = now;
            }
            return now - entry.previewReleasedAt < PREVIEW_AFTER_CHAT_MS;
        }

        return now - entry.createdAt < DISPLAY_TIME_MS;
    }

    private static final class Entry {
        private final String message;
        private final boolean preview;
        private final long createdAt = System.currentTimeMillis();
        private long previewReleasedAt = -1L;
        private final Animation animation = new Animation(220, 1f, true, Easing.BOTH_SINE);

        private Entry(String message) {
            this(message, false);
        }

        private Entry(String message, boolean preview) {
            this.message = message;
            this.preview = preview;
        }
    }
}
