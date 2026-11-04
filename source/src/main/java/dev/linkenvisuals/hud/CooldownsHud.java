package dev.linkenvisuals.hud;

import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.clickgui.link.LinkGuiTheme;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import dev.linkenvisuals.util.ColorMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Donor cooldown list: compact title row plus one item icon/name/time row per
 * active cooldown. Preview entries are shown in HUD edit mode.
 */
public final class CooldownsHud extends HudElement {
    private static final float PADDING = 6.0f;
    private static final float HEADER_H = 18.0f;
    private static final float ROW_H = 13.0f;
    private static final float ICON_SIZE = 12.0f;
    private static final float ICON_INSET = 3.0f;
    private static final float ICON_GAP = 2.0f;
    private static final float FONT_SIZE = 7.0f;
    private static final float TITLE_SIZE = 7.5f;
    private static final float MIN_WIDTH = 90.0f;
    private static final long CACHE_MS = 50L;

    private final HudTween fade = new HudTween(true);
    private List<CooldownTracker.Entry> entries = List.of();
    private long cacheUntil;
    private float cachedWidth = MIN_WIDTH;
    private float cachedHeight = HEADER_H + 5.0f;
    private static boolean lastRenderVisible;

    public CooldownsHud() {
        super("cooldownshud", 0.5f, 0.72f);
    }

    public static boolean lastRenderVisible() {
        return lastRenderVisible;
    }

    @Override
    public float width() {
        refresh();
        return cachedWidth;
    }

    @Override
    public float height() {
        refresh();
        return cachedHeight;
    }

    @Override
    public void render(DrawContext context, float x, float y) {
        lastRenderVisible = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        boolean editing = isEditing(client);
        if (!isEnabled() && !editing) {
            return;
        }
        refresh();
        boolean show = editing || !entries.isEmpty();
        float alpha = fade.value(show ? 1.0f : 0.0f, 200L);
        if (alpha <= 0.001f) {
            return;
        }
        if (entries.isEmpty()) {
            return;
        }
        float fa = Math.max(0.0f, Math.min(1.0f, alpha));
        int globalAlpha = Math.round(255.0f * fa);
        HudSurface.panel(context, id(), x, y, cachedWidth, cachedHeight, fa);

        GlyphAtlas bold = Fonts.titleFont();
        GlyphAtlas regular = Fonts.textFont();
        GlyphAtlas iconFont = LinkenFonts.iconsNew();
        if (iconFont == null) {
            iconFont = bold;
        }
        String title = I18n.get("elements.settings.elements.cooldownshud");
        if (title.equals("elements.settings.elements.cooldownshud")) {
            title = "Задержка";
        }
        float centerY = y + HEADER_H * 0.5f;
        MsdfText.drawOn(context, bold, title, x + PADDING, centerY, TITLE_SIZE, textColor(globalAlpha));

        if (iconFont != null) {
            float iconX = x + cachedWidth - PADDING - 10.0f;
            MsdfText.drawOn(context, iconFont, "Q", iconX, centerY, 8.0f, textColor(globalAlpha));
        }

        int accent = LinkGuiTheme.ACCENT;
        float dividerY = y + HEADER_H - 1.0f;
        context.fill(Math.round(x + PADDING), Math.round(dividerY),
            Math.round(x + cachedWidth - PADDING), Math.round(dividerY + 1.0f),
            ColorMath.withAlpha(accent, 90));

        float rowY = y + HEADER_H;
        Matrix3x2fStack matrices = context.getMatrices();
        for (CooldownTracker.Entry entry : entries) {
            ItemStack stack = entry.stack();
            float itemX = x + PADDING + ICON_INSET;
            float itemY = rowY + (ROW_H - ICON_SIZE) * 0.5f - 1.0f;
            if (stack != null && !stack.isEmpty()) {
                matrices.pushMatrix();
                matrices.translate(itemX, itemY);
                matrices.scale(ICON_SIZE / 16.0f, ICON_SIZE / 16.0f);
                context.drawItem(stack, 0, 0);
                matrices.popMatrix();
            }

            float textX = itemX + ICON_SIZE + ICON_GAP;
            String name = entry.displayName();
            String time = entry.formattedTime();
            float timeWidth = regular.width(time, FONT_SIZE);
            float timeX = x + cachedWidth - PADDING - timeWidth - 2.0f;
            String clipped = fitText(regular, name, FONT_SIZE, Math.max(0.0f, timeX - textX - 4.0f));
            float textCenterY = rowY + ROW_H * 0.5f - 1.0f;
            MsdfText.drawOn(context, regular, clipped, textX, textCenterY, FONT_SIZE,
                textColor(globalAlpha));
            MsdfText.drawOn(context, regular, time, timeX, textCenterY, FONT_SIZE,
                ColorMath.withAlpha(timeColor(entry), globalAlpha));
            rowY += ROW_H;
        }
        lastRenderVisible = true;
    }

    private boolean isEnabled() {
        return ModuleRegistry.cooldownsHud() == null || ModuleRegistry.cooldownsHud().value();
    }

    private static boolean isEditing(MinecraftClient client) {
        return HudManager.isEditing() || (client != null && client.currentScreen instanceof ChatScreen);
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now < cacheUntil) {
            return;
        }
        cacheUntil = now + CACHE_MS;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            entries = List.of();
            return;
        }
        boolean editing = isEditing(client);
        List<CooldownTracker.Entry> active = CooldownTracker.collect(client,
            client.getRenderTickCounter().getTickProgress(true));
        entries = !active.isEmpty() ? active : (editing ? CooldownTracker.preview() : List.of());
        if (entries.isEmpty()) {
            cachedHeight = HEADER_H + 5.0f;
            return;
        }

        GlyphAtlas bold = Fonts.titleFont();
        GlyphAtlas regular = Fonts.textFont();
        GlyphAtlas iconFont = LinkenFonts.iconsNew();
        float titleWidth = bold.width(I18n.get("linkenvisuals.hud.cooldowns"), TITLE_SIZE)
            + (iconFont == null ? 0.0f : iconFont.width("Q", 8.0f)) + 4.0f;
        float width = Math.max(MIN_WIDTH, titleWidth + PADDING * 2.0f);
        for (CooldownTracker.Entry entry : entries) {
            String name = entry.displayName();
            if (name.length() > 26) {
                name = name.substring(0, 23) + "...";
            }
            float rowWidth = PADDING * 2.0f + ICON_INSET + ICON_SIZE + ICON_GAP
                + regular.width(name, FONT_SIZE) + 10.0f + regular.width(entry.formattedTime(), FONT_SIZE);
            width = Math.max(width, rowWidth);
        }
        cachedWidth = width;
        cachedHeight = HEADER_H + entries.size() * ROW_H + 5.0f;
    }

    private static String fitText(GlyphAtlas font, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty() || font.width(text, size) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        float ellipsisWidth = font.width(ellipsis, size);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            builder.append(text.charAt(i));
            if (font.width(builder.toString(), size) + ellipsisWidth > maxWidth) {
                builder.deleteCharAt(builder.length() - 1);
                break;
            }
        }
        return builder.append(ellipsis).toString();
    }

    private static int timeColor(CooldownTracker.Entry entry) {
        if (entry.remainingTicks() > 20.0f * 20.0f) {
            return 0x82C8FF;
        }
        if (entry.progress() > 0.75f) {
            return 0xFF5555;
        }
        if (entry.progress() > 0.5f) {
            return 0xFFAA33;
        }
        if (entry.progress() > 0.25f) {
            return 0xFFE45C;
        }
        return 0x55DD77;
    }

    private static int textColor(int alpha) {
        return ColorMath.withAlpha(LinkGuiTheme.isLight() ? 0x1C1F26 : 0xFFFFFF, alpha);
    }
}
