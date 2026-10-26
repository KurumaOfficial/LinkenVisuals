package dev.linkenvisuals.hud;

import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.render.CirclePipeline;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

/**
 * 1:1 Linken Watermark matching donor Watermark.java:
 * t.me/LinkenVisuals • 🖥 {fps} FPS • 📶 {ping} MS
 */
public final class Watermark extends HudElement {
    private static final float HEIGHT = 17.0f;
    private static final float PADDING = 6.0f;
    private static final float FONT_SIZE = 7.5f;
    private static final float LABEL_SIZE = 6.0f;
    private static final float ICON_SIZE = 8.5f;
    private static final float GAP = 5.0f;
    private static final float BULLET_GAP = 4.0f;
    private static final float BULLET_RADIUS = 1.2f;

    private static final String BRAND = "t.me/LinkenVisuals";
    private static final String FPS_ICON = "C";
    private static final String PING_ICON = "O";

    private GlyphAtlas text;
    private GlyphAtlas bold;
    private GlyphAtlas icons;
    private float totalWidth = 160.0f;

    public Watermark() {
        super("watermark", 0.045f, 0.045f);
    }

    private void ensureLoaded() {
        if (text == null) {
            text = Fonts.textFont();
        }
        if (bold == null) {
            bold = Fonts.titleFont();
        }
        if (icons == null) {
            icons = LinkenFonts.iconsNew();
            if (icons == null) {
                icons = LinkenFonts.icons();
            }
        }
    }

    @Override
    public float width() {
        return totalWidth;
    }

    @Override
    public float height() {
        return HEIGHT;
    }

    @Override
    public void render(DrawContext context, float x, float y) {
        ensureLoaded();
        MinecraftClient client = MinecraftClient.getInstance();

        String fps = String.valueOf(client.getCurrentFps());
        String ping = String.valueOf(currentPing(client));

        float brandWidth = MsdfText.width(bold, BRAND, FONT_SIZE);
        float fpsIconW = MsdfText.width(icons, FPS_ICON, ICON_SIZE);
        float fpsValW = MsdfText.width(bold, fps, FONT_SIZE);
        float fpsLabelW = MsdfText.width(text, "FPS", LABEL_SIZE);

        float pingIconW = MsdfText.width(icons, PING_ICON, ICON_SIZE);
        float pingValW = MsdfText.width(bold, ping, FONT_SIZE);
        float pingLabelW = MsdfText.width(text, "MS", LABEL_SIZE);

        float bulletSpacing = BULLET_RADIUS * 2.0f + BULLET_GAP * 2.0f;

        totalWidth = PADDING
            + brandWidth + GAP + bulletSpacing
            + fpsIconW + 3.0f + fpsValW + 1.5f + fpsLabelW + GAP + bulletSpacing
            + pingIconW + 3.0f + pingValW + 1.5f + pingLabelW
            + PADDING;

        HudSurface.panel(context, "watermark", x, y, totalWidth, HEIGHT, 1.0f);

        float centerY = y + HEIGHT * 0.5f;
        float curX = x + PADDING;

        // 1. Brand: "t.me/LinkenVisuals"
        MsdfText.draw(context, bold, BRAND, curX, centerY - FONT_SIZE * 0.35f, FONT_SIZE, 0xFFFFFFFF);
        curX += brandWidth + GAP;

        // Bullet 1
        curX += BULLET_GAP;
        CirclePipeline.draw(context, curX + BULLET_RADIUS, centerY, BULLET_RADIUS,
            ColorMath.withAlpha(0xFFFFFF, 75));
        curX += BULLET_RADIUS * 2.0f + BULLET_GAP;

        // 2. 🖥 {fps} FPS
        MsdfText.draw(context, icons, FPS_ICON, curX, centerY - ICON_SIZE * 0.35f, ICON_SIZE,
            ColorMath.withAlpha(0x7EDFB4, 230));
        curX += fpsIconW + 3.0f;

        MsdfText.draw(context, bold, fps, curX, centerY - FONT_SIZE * 0.35f, FONT_SIZE, 0xFFFFFFFF);
        curX += fpsValW + 1.5f;

        MsdfText.draw(context, text, "FPS", curX, centerY - LABEL_SIZE * 0.35f, LABEL_SIZE,
            ColorMath.withAlpha(0xFFFFFF, 140));
        curX += fpsLabelW + GAP;

        // Bullet 2
        curX += BULLET_GAP;
        CirclePipeline.draw(context, curX + BULLET_RADIUS, centerY, BULLET_RADIUS,
            ColorMath.withAlpha(0xFFFFFF, 75));
        curX += BULLET_RADIUS * 2.0f + BULLET_GAP;

        // 3. 📶 {ping} MS
        MsdfText.draw(context, icons, PING_ICON, curX, centerY - ICON_SIZE * 0.35f, ICON_SIZE,
            ColorMath.withAlpha(0x7EDFB4, 230));
        curX += pingIconW + 3.0f;

        MsdfText.draw(context, bold, ping, curX, centerY - FONT_SIZE * 0.35f, FONT_SIZE, 0xFFFFFFFF);
        curX += pingValW + 1.5f;

        MsdfText.draw(context, text, "MS", curX, centerY - LABEL_SIZE * 0.35f, LABEL_SIZE,
            ColorMath.withAlpha(0xFFFFFF, 140));
    }

    private static int currentPing(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return 0;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry != null ? Math.max(0, entry.getLatency()) : 0;
    }
}
