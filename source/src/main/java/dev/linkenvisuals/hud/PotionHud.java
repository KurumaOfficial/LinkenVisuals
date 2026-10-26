package dev.linkenvisuals.hud;

import dev.linkenvisuals.clickgui.link.LinkGuiTheme;
import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import dev.linkenvisuals.ui.linkmenu.LinkenMenuChrome;
import dev.linkenvisuals.util.ColorMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 1:1 Linken Potion HUD ported from donor ru.method.linkenvisuals.hud.impl.PotionHud.
 * Renders active potion effects with Roman numerals, duration, and native Linken styling.
 */
public final class PotionHud extends HudElement {

    private static final float PADDING = 6.0f;
    private static final float ROW_H = 18.0f;
    private static final float HEADER_H = 22.0f;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private GlyphAtlas font;
    private GlyphAtlas bold;

    public PotionHud() {
        super("potion_hud", 0.02f, 0.35f);
    }

    private void ensureFonts() {
        if (font == null) {
            font = Fonts.textFont();
            bold = Fonts.titleFont();
        }
    }

    private Collection<StatusEffectInstance> getEffects() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && !mc.player.getStatusEffects().isEmpty()) {
            return mc.player.getStatusEffects();
        }
        if (HudManager.isEditing()) {
            List<StatusEffectInstance> preview = new ArrayList<>();
            preview.add(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1));
            preview.add(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 0));
            preview.add(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 3600, 0));
            return preview;
        }
        return List.of();
    }

    private static String getDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "∞";
        int duration = effect.getDuration();
        int mins = duration / 1200;
        int secs = (duration % 1200) / 20;
        return secs < 10 ? (mins + ":0" + secs) : (mins + ":" + secs);
    }

    private static String toRoman(int number) {
        if (number <= 0) return "";
        return (number < ROMAN.length) ? ROMAN[number] : String.valueOf(number);
    }

    @Override
    public float width() {
        Collection<StatusEffectInstance> effects = getEffects();
        if (effects.isEmpty()) {
            return 0.0f;
        }
        ensureFonts();
        float maxW = 140.0f;
        for (StatusEffectInstance inst : effects) {
            String name = inst.getEffectType().value().getName().getString();
            String level = toRoman(inst.getAmplifier() + 1);
            String dur = getDuration(inst);
            float nameW = font != null ? font.width(name + " " + level, 8.5f) : 60.0f;
            float durW = font != null ? font.width(dur, 8.5f) : 25.0f;
            maxW = Math.max(maxW, nameW + durW + PADDING * 3 + 18.0f);
        }
        return maxW;
    }

    @Override
    public float height() {
        Collection<StatusEffectInstance> effects = getEffects();
        if (effects.isEmpty()) {
            return 0.0f;
        }
        return HEADER_H + effects.size() * ROW_H + PADDING;
    }

    @Override
    public void render(DrawContext context, float x, float y) {
        Collection<StatusEffectInstance> effects = getEffects();
        if (effects.isEmpty()) {
            return;
        }
        ensureFonts();
        float w = width();
        float h = height();

        // 1. Panel background
        HudStyle.panel(context, id(), x, y, w, h, 1.0f);

        // 2. Header
        String title = "Эффекты";
        int textColor = ColorMath.withAlpha(LinkGuiTheme.TEXT, 255);
        int mutedColor = ColorMath.withAlpha(LinkGuiTheme.MUTED, 200);

        if (bold != null) {
            float centerY = y + HEADER_H * 0.5f;
            MsdfText.drawOn(context, bold, title, x + PADDING + 2.0f, centerY, 9.5f, textColor);
        }

        // Header separator line
        float dividerY = y + HEADER_H - 1.0f;
        context.fill(Math.round(x + PADDING), Math.round(dividerY),
            Math.round(x + w - PADDING), Math.round(dividerY + 1.0f),
            ColorMath.withAlpha(LinkGuiTheme.ACCENT, 90));

        // 3. Effect rows
        float rowY = y + HEADER_H;
        for (StatusEffectInstance inst : effects) {
            String name = inst.getEffectType().value().getName().getString();
            String level = toRoman(inst.getAmplifier() + 1);
            String label = level.isEmpty() ? name : (name + " " + level);
            String duration = getDuration(inst);

            // Icon dot / indicator
            int effectRgb = inst.getEffectType().value().getColor();
            LinkenMenuChrome.roundedFill(context, x + PADDING + 3.0f, rowY + 5.0f, 6.0f, 6.0f, 3.0f,
                ColorMath.withAlpha(effectRgb, 230), 1.0f);

            // Effect name
            float textCenterY = rowY + ROW_H * 0.5f - 1.0f;
            if (font != null) {
                MsdfText.drawOn(context, font, label, x + PADDING + 14.0f, textCenterY, 8.5f, textColor);
                float durW = font.width(duration, 8.5f);
                float durX = x + w - PADDING - durW - 2.0f;
                MsdfText.drawOn(context, font, duration, durX, textCenterY, 8.5f, mutedColor);
            }

            rowY += ROW_H;
        }
    }
}
