package dev.linkenvisuals.hud;

import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.clickgui.link.LinkGuiTheme;
import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.render.Shapes;
import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import dev.linkenvisuals.util.ColorMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Donor coordinates card: compass glyph, integer X/Y/Z and a two-decimal BPS
 * readout. Geometry follows {@code CoordinatesHud} (height 17, padding 5, icon
 * 9, text 7.5, BPS label 6.5), including the animated card width.
 */
public final class CoordinatesHud extends HudElement {
    private static final float PADDING = 5.0f;
    private static final float HEIGHT = 17.0f;
    private static final float FONT_SIZE = 7.5f;
    private static final float LABEL_SIZE = 6.5f;
    private static final float ICON_SIZE = 9.0f;
    private static final float ICON_SPACING = 4.0f;
    private static final float EXTRA_LENGTH = 4.0f;
    private static final float VALUE_LABEL_SPACING = 1.5f;
    private static final float BULLET_SIZE = 4.0f;
    private static final float BULLET_SPACING = 2.0f;
    private static final float BULLET_WIDTH = BULLET_SIZE + BULLET_SPACING * 2.0f;

    private static final String ICON = "J";
    private static final String BPS_LABEL = "BPS";

    private final HudTween fade = new HudTween(true);
    private final HudTween widthTween = new HudTween(true);

    private GlyphAtlas bold;
    private GlyphAtlas label;
    private GlyphAtlas icon;

    private double bps;
    private String coordsText = " 0  0  0";
    private int cachedMetricX = Integer.MIN_VALUE;
    private int cachedMetricY = Integer.MIN_VALUE;
    private int cachedMetricZ = Integer.MIN_VALUE;

    private float cachedWidth = 100.0f;

    private static boolean lastRenderVisible;

    public CoordinatesHud() {
        super("coordinateshud", 0.045f, 0.13f);
    }

    /** Autotest hook: the most recent frame actually drew the coordinates card. */
    public static boolean lastRenderVisible() {
        return lastRenderVisible;
    }

    @Override
    public float width() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean editing = isEditing(client);
        if (!isEnabled() && !editing) {
            return cachedWidth;
        }
        refreshMetrics(client, editing);
        float target = measureWidth();
        cachedWidth = editing ? target : widthTween.value(target, 200L);
        return cachedWidth;
    }

    @Override
    public float height() {
        return HEIGHT;
    }

    @Override
    public void render(DrawContext context, float x, float y) {
        lastRenderVisible = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        boolean editing = isEditing(client);
        if (!isEnabled() && !editing) {
            return;
        }
        if (!editing && (client.player == null || client.world == null)) {
            return;
        }

        refreshMetrics(client, editing);
        float alpha = fade.value(1.0f, 200L);
        if (alpha <= 0.001f) {
            return;
        }
        if (cachedWidth <= 1.0f || editing) {
            cachedWidth = measureWidth();
        }
        float fa = Math.max(0.0f, Math.min(1.0f, alpha));
        int globalAlpha = Math.round(255.0f * fa);

        HudSurface.panel(context, id(), x, y, cachedWidth, HEIGHT, fa);

        String bpsValue = formatBps(bps);
        float coordsWidth = bold().width(coordsText, FONT_SIZE);
        float bpsWidth = bold().width(bpsValue, FONT_SIZE);
        float iconWidth = icon().width(ICON, ICON_SIZE);
        float centerY = y + HEIGHT * 0.5f;

        float iconX = x + PADDING;
        int iconColor = ColorMath.withAlpha(Theme.accent(), globalAlpha);
        MsdfText.drawOn(context, icon(), ICON, iconX, centerY, ICON_SIZE, iconColor);

        float textX = iconX + iconWidth + ICON_SPACING;
        int textColor = textColor(globalAlpha);
        MsdfText.drawOn(context, bold(), coordsText, textX, centerY, FONT_SIZE, textColor);

        float cursor = textX + coordsWidth;
        int bulletColor = LinkGuiTheme.isLight()
            ? ColorMath.withAlpha(0x6E7886, Math.round(150.0f * fa))
            : ColorMath.withAlpha(0xB4BECC, Math.round(150.0f * fa));
        Shapes.rhombus(context, cursor + BULLET_SPACING + BULLET_SIZE * 0.5f, centerY,
            BULLET_SIZE * 0.5f, bulletColor);
        cursor += BULLET_WIDTH;

        MsdfText.drawOn(context, bold(), bpsValue, cursor, centerY, FONT_SIZE, textColor);
        cursor += bpsWidth + VALUE_LABEL_SPACING;

        int labelColor = LinkGuiTheme.isLight()
            ? ColorMath.withAlpha(0x5A6270, Math.round(200.0f * fa))
            : ColorMath.withAlpha(0xBEC6D4, Math.round(200.0f * fa));
        MsdfText.drawOn(context, label(), BPS_LABEL, cursor, centerY + 0.7f, LABEL_SIZE, labelColor);

        lastRenderVisible = true;
    }

    private boolean isEnabled() {
        return ModuleRegistry.coordinatesHud() == null || ModuleRegistry.coordinatesHud().value();
    }

    private static boolean isEditing(MinecraftClient client) {
        return HudManager.isEditing() || (client != null && client.currentScreen instanceof ChatScreen);
    }

    private void refreshMetrics(MinecraftClient client, boolean editing) {
        int x;
        int y;
        int z;
        double speed = 0.0;
        PlayerEntity player = client == null ? null : client.player;
        if (player != null) {
            x = (int) Math.floor(player.getX());
            y = (int) Math.floor(player.getY());
            z = (int) Math.floor(player.getZ());
            speed = bps(player);
        } else if (editing) {
            x = 1234;
            y = 64;
            z = -567;
            speed = 4.4;
        } else {
            return;
        }
        bps = speed;
        if (x != cachedMetricX || y != cachedMetricY || z != cachedMetricZ) {
            cachedMetricX = x;
            cachedMetricY = y;
            cachedMetricZ = z;
            coordsText = coordsText(x, y, z);
        }
    }

    private float measureWidth() {
        String bpsValue = formatBps(bps);
        float coordsWidth = bold().width(coordsText, FONT_SIZE);
        float bpsValueWidth = bold().width(bpsValue, FONT_SIZE);
        float labelWidth = label().width(BPS_LABEL, LABEL_SIZE);
        float textWidth = coordsWidth + BULLET_WIDTH + bpsValueWidth + VALUE_LABEL_SPACING + labelWidth;
        float iconWidth = icon().width(ICON, ICON_SIZE);
        return iconWidth + ICON_SPACING + textWidth + PADDING * 2.0f + EXTRA_LENGTH;
    }

    private GlyphAtlas bold() {
        if (bold == null) {
            bold = Fonts.titleFont();
        }
        return bold;
    }

    private GlyphAtlas label() {
        if (label == null) {
            label = Fonts.textFont();
        }
        return label;
    }

    private GlyphAtlas icon() {
        if (icon == null) {
            GlyphAtlas glyphs = LinkenFonts.iconsNew();
            icon = glyphs != null && glyphs.glyph(ICON.codePointAt(0)) != null ? glyphs : Fonts.titleFont();
        }
        return icon;
    }

    private static int textColor(int alpha) {
        return ColorMath.withAlpha(LinkGuiTheme.isLight() ? 0x1C1F26 : 0xFFFFFF, alpha);
    }

    private static double bps(PlayerEntity player) {
        var velocity = player.getVelocity();
        return Math.hypot(velocity.x, velocity.z) * 20.0;
    }

    static String coordsText(int x, int y, int z) {
        return " " + x + "  " + y + "  " + z;
    }

    static String formatBps(double value) {
        int hundredths = (int) Math.round(Math.abs(value) * 100.0);
        String fraction = (hundredths % 100 < 10 ? "0" : "") + hundredths % 100;
        return (value < 0.0 ? "-" : "") + hundredths / 100 + "." + fraction;
    }
}
