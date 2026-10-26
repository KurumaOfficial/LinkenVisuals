package dev.linkenvisuals.render;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.ui.style.MenuStyle;
import dev.linkenvisuals.ui.style.StyleFlavor;
import dev.linkenvisuals.ui.style.StyleManager;
import dev.linkenvisuals.util.ColorMath;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Shared drawing helpers for the four custom loading screens
 * ({@code ConnectScreen}, {@code MessageScreen}, {@code ProgressScreen},
 * {@code LevelLoadingScreen}), which previously carried four copies of the same
 * scene code. Mixin classes cannot share a non-mixin superclass, so the common
 * part lives here as static state and helpers.
 *
 * <p>All geometry values are passed explicitly and reproduce the previous
 * per-screen drawing 1:1.</p>
 */
public final class LoadingOverlay {
    public static final Identifier STRANGE_LOGO = LinkenVisuals.id("textures/gui/strange_logo.png");
    public static final Identifier LINKEN_LOGO = LinkenVisuals.id("textures/gui/linken_logo.png");
    public static final Identifier LOGO = STRANGE_LOGO;

    /** The backdrop renders every frame; a broken panorama would otherwise log forever. */
    private static boolean panoramaFailureLogged;

    private LoadingOverlay() {
    }

    /** Passes {@code Screen#renderPanoramaBackground} (protected) out of the mixins. */
    @FunctionalInterface
    public interface PanoramaDrawer {
        void draw(DrawContext context, float delta);
    }

    /** Beta-style backdrop for custom loading screens. */
    public static void betaBackdrop(DrawContext context, int width, int height) {
        BackgroundPipeline.draw(1.0f);
        context.fillGradient(0, 0, width, height, 0x66000000, 0x99020406);
    }

    /** Vanilla-style backdrop: panorama through the provider, dirt fallback on any failure. */
    public static void vanillaBackdrop(DrawContext context, float delta, int width, int height, PanoramaDrawer panorama) {
        try {
            panorama.draw(context, delta);
            context.fillGradient(0, 0, width, height, 0x1A000000, 0x30000000);
        } catch (Throwable t) {
            if (!panoramaFailureLogged) {
                panoramaFailureLogged = true;
                LinkenVisuals.LOGGER.warn("Loading-screen panorama failed, using dirt fallback: {}", t.toString());
            }
            VanillaStylePipeline.renderDirtBackground(context, width, height);
        }
    }

    /**
     * Beta-style scene: ambient glow, logo, MSDF title/subtitle and the sliding bar.
     *
     * @param progress {@code >= 0} draws a determinate bar with a percent label, {@code < 0} the indeterminate sweep
     */
    public static void betaScene(DrawContext context, int width, int height, long time,
                                 float glowY, float logoY, float titleY, float subtitleY,
                                 float barY, float barW, float subtitleSize,
                                 String title, String subtitle, float progress) {
        float cx = width * 0.5f;

        // Stable ambient glow - smooth slow breathing, 0 tremble
        float glowRadius = 75.0f + (float) Math.sin(time * 0.001) * 6.0f;
        GlowPipeline.radial(context, cx, glowY, glowRadius, ColorMath.argb(NeonEdge.cool(), 50.0f / 255.0f));

        // Crisp stable logo - no jittering
        Identifier logo = dev.linkenvisuals.clickgui.ModuleRegistry.isLinkenMenu() ? LINKEN_LOGO : STRANGE_LOGO;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, logo,
            Math.round(cx - 23.0f), Math.round(logoY), 0.0f, 0.0f,
            46, 46, 128, 128, 128, 128, 0xFFFFFFFF);

        GlyphAtlas tf = Fonts.titleFont();
        GlyphAtlas sf = Fonts.textFont();
        if (tf != null) {
            MsdfText.drawCentered(context, tf, title, cx, titleY, 11.5f, 0xFFFFFFFF);
        }
        if (sf != null && subtitle != null && !subtitle.isBlank()) {
            MsdfText.drawCentered(context, sf, subtitle, cx, subtitleY, subtitleSize, NeonEdge.cool());
        }

        // Sleek sliding bar
        float barH = 4.0f;
        float barX = cx - barW * 0.5f;
        RoundedRectPipeline.draw(context, barX, barY, barW, barH, 0xEE141822);
        RoundedRectPipeline.drawOutline(context, barX, barY, barW, barH, 0x44252E3E);

        if (progress >= 0.0f) {
            float fillW = barW * Math.max(0.0f, Math.min(1.0f, progress));
            RoundedRectPipeline.draw(context, barX, barY, fillW, barH, NeonEdge.cool());
            if (sf != null) {
                MsdfText.drawCentered(context, sf, Math.round(progress * 100.0f) + "%", cx, barY + 12.0f, 7.2f, 0x9098A8);
            }
        } else {
            float sweep = (float) ((time * 0.0009) % 1.0);
            float beamW = barW * 0.35f;
            float beamX = barX + sweep * (barW - beamW);
            RoundedRectPipeline.draw(context, beamX, barY, beamW, barH, NeonEdge.cool());
        }
    }

    /**
     * Vanilla-style scene: 24px icon, centered title/subtitle and the classic progress bar.
     *
     * @param progress {@code >= 0} draws a determinate bar with a percent label, {@code < 0} the indeterminate sweep
     */
    public static void vanillaScene(DrawContext context, TextRenderer textRenderer, int width, int height,
                                    Identifier icon, float iconU, float iconV, int iconRegionW, int iconRegionH,
                                    int iconSheetW, int iconSheetH,
                                    float iconY, float titleY, float subtitleY, float barY, long time,
                                    String title, String subtitle, float progress) {
        float cx = width * 0.5f;

        int iconSize = 24;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, icon,
            Math.round(cx - iconSize * 0.5f), Math.round(iconY), iconU, iconV,
            iconSize, iconSize, iconRegionW, iconRegionH, iconSheetW, iconSheetH, 0xFFFFFFFF);

        context.drawCenteredTextWithShadow(textRenderer, title, Math.round(cx), Math.round(titleY), 0xFFFFFFFF);
        if (subtitle != null && !subtitle.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, subtitle, Math.round(cx), Math.round(subtitleY), 0xFFFFFFA0);
        }

        int barW = 200;
        int barH = 12;
        int barX = Math.round(cx - barW * 0.5f);
        int barYI = Math.round(barY);
        context.fill(barX, barYI, barX + barW, barYI + barH, 0xFF000000);
        VanillaStylePipeline.drawBorder(context, barX, barYI, barW, barH, 0xFF555555);

        if (progress >= 0.0f) {
            int fillW = (int) ((barW - 2) * Math.max(0.0f, Math.min(1.0f, progress)));
            context.fill(barX + 1, barYI + 1, barX + 1 + fillW, barYI + barH - 1, 0xFF55FF55);
            context.drawCenteredTextWithShadow(textRenderer, Math.round(progress * 100.0f) + "%", Math.round(cx), barYI + barH + 6, 0xFFAAAAAA);
        } else {
            float sweep = (float) ((time * 0.0009) % 1.0);
            int beamW = (int) (barW * 0.35f);
            int beamX = barX + 1 + (int) (sweep * (barW - 2 - beamW));
            context.fill(beamX, barYI + 1, beamX + beamW, barYI + barH - 1, 0xFF55FF55);
        }
    }
}
