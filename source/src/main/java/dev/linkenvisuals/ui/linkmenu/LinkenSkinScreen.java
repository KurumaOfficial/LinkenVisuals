package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * LINKEN skin settings: a live player-head preview, the seven vanilla model
 * parts and the main-arm choice. 1.21.11 exposes no in-game skin chooser for
 * offline accounts, so the account action opens the existing LINKEN account
 * manager instead of a non-existent Mojang skin screen.
 */
public final class LinkenSkinScreen extends Screen {
    private static final float WINDOW_X = 430.0f;
    private static final float WINDOW_Y = 238.0f;
    private static final float WINDOW_W = 1060.0f;
    private static final float WINDOW_H = 740.0f;
    private static final float FOOTER_H = 64.0f;
    private static final float PAD = 20.0f;

    private static final float PREVIEW_X = WINDOW_X + PAD;
    private static final float PREVIEW_Y = WINDOW_Y + 88.0f;
    private static final float PREVIEW_W = 300.0f;
    private static final float PREVIEW_H = 420.0f;

    private static final float LIST_X = WINDOW_X + 370.0f;
    private static final float LIST_Y = WINDOW_Y + 88.0f;
    private static final float LIST_W = WINDOW_W - 370.0f - PAD;
    private static final float ROW_H = 50.0f;
    private static final float ROW_GAP = 6.0f;

    private final Screen parent;
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring backSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring doneSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring accountSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring accountRowSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring[] rowHover = new Spring[16];

    private final List<PlayerModelPart> parts = List.of(PlayerModelPart.values());
    private final List<LinkenMenuLayout.Rect> partRects = new ArrayList<>();

    private SkinTextures skin;
    private long lastFrame = System.nanoTime();

    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect doneRect;
    private LinkenMenuLayout.Rect previewRect;
    private LinkenMenuLayout.Rect accountRect;
    private LinkenMenuLayout.Rect mainArmRect;

    public LinkenSkinScreen(Screen parent) {
        super(Text.literal("Skin"));
        this.parent = parent;
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0, 900.0, 50.0);
        }
    }

    @Override
    protected void init() {
        skin = loadSkin();
        openSpring.reset(0.0);
        lastFrame = System.nanoTime();
    }

    private SkinTextures loadSkin() {
        if (client == null || client.getGameProfile() == null) {
            return null;
        }
        try {
            Supplier<SkinTextures> lookup = client.getSkinProvider()
                .supplySkinTextures(client.getGameProfile(), false);
            SkinTextures textures = lookup.get();
            if (textures != null) {
                return textures;
            }
        } catch (RuntimeException unavailable) {
            LinkenVisuals.LOGGER.debug("[Visuals Modification] Skin preview lookup failed: {}", unavailable.getMessage());
        }
        try {
            return DefaultSkinHelper.getSkinTextures(client.getGameProfile());
        } catch (RuntimeException unavailable) {
            LinkenVisuals.LOGGER.debug("[Visuals Modification] Default skin preview failed: {}", unavailable.getMessage());
            return null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = Math.min((now - lastFrame) / 1_000_000_000.0f, 0.05f);
        lastFrame = now;
        openSpring.step(1.0, seconds);
        float fade = (float) openSpring.get();

        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = mouseX / metrics.scale();
        float my = mouseY / metrics.scale();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        LinkenMenuChrome.drawBackground(context, metrics, 0.0f, 0.0f, 1.0f);
        GlowPipeline.radial(context, metrics.width() * 0.53f, 470.0f, 720.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 10, fade));

        context.getMatrices().translate(0.0f, (1.0f - fade) * 18.0f);
        LinkenMenuChrome.drawWordmark(context, fade);
        LinkenMenuChrome.drawClock(context, metrics, fade);
        backRect = new LinkenMenuLayout.Rect(metrics.width() - LinkenMenuLayout.MARGIN - 132.0f,
            104.0f, 132.0f, 44.0f);
        drawChip(context, metrics, backRect, "BACK", LinkenMenuChrome.ICON_POWER, mx, my, fade, backSpring, seconds);

        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), "SETTINGS",
            LinkenMenuLayout.MARGIN, 146.0f, 8.5f, 1.6f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(),
            I18n.get("linkenvisuals.linkmenu.skin.title"),
            LinkenMenuLayout.MARGIN, 162.0f, 40.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.skin.subtitle"),
            LinkenMenuLayout.MARGIN, 218.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        drawWindow(context, metrics, mx, my, fade, seconds);
        context.getMatrices().popMatrix();
    }

    private void drawWindow(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my,
                            float fade, float seconds) {
        LinkenMenuChrome.roundedFill(context, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H, 14.0f,
            LinkenMenuChrome.rgba(0x0E1411, 250, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H, 14.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 28, fade), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "SETTINGS  /  SKIN",
            WINDOW_X + 24.0f, WINDOW_Y + 26.0f, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));

        drawPreview(context, metrics, mx, my, fade, seconds);

        LinkenMenuChrome.drawSpaced(context, LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.skin.model_parts"),
            LIST_X + 4.0f, LIST_Y, 8.5f, 1.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        partRects.clear();
        float y = LIST_Y + 24.0f;
        GameOptions options = client != null ? client.options : null;
        for (int i = 0; i < parts.size(); i++) {
            PlayerModelPart part = parts.get(i);
            LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(LIST_X, y, LIST_W, ROW_H);
            partRects.add(rect);
            boolean enabled = options != null && options.isPlayerModelPartEnabled(part);
            drawToggleRow(context, metrics, rect, part.getOptionName().getString(), enabled, i, mx, my, fade, seconds);
            y += ROW_H + ROW_GAP;
        }

        mainArmRect = new LinkenMenuLayout.Rect(LIST_X, y + 4.0f, LIST_W, ROW_H);
        Arm arm = options != null ? options.getMainArm().getValue() : Arm.RIGHT;
        drawValueRow(context, metrics, mainArmRect,
            I18n.get("linkenvisuals.linkmenu.skin.main_arm"),
            arm == Arm.LEFT ? "LEFT" : "RIGHT", mx, my, fade, seconds, 12);

        accountRect = new LinkenMenuLayout.Rect(LIST_X, mainArmRect.bottom() + ROW_GAP, LIST_W, ROW_H);
        drawAccountRow(context, metrics, accountRect, mx, my, fade, seconds);

        float footerY = WINDOW_Y + WINDOW_H - FOOTER_H;
        context.fill(Math.round(WINDOW_X + 1.0f), Math.round(footerY),
            Math.round(WINDOW_X + WINDOW_W - 1.0f), Math.round(WINDOW_Y + WINDOW_H - 1.0f),
            LinkenMenuChrome.rgba(0x0E1411, 250, fade));
        doneRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - PAD - 130.0f,
            WINDOW_Y + WINDOW_H - 54.0f, 130.0f, 40.0f);
        drawDoneButton(context, metrics, doneRect, mx, my, fade, seconds);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), "ESC  /  BACK",
            WINDOW_X + PAD, WINDOW_Y + WINDOW_H - 40.0f, 9.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));

        WindowMaterial.corners(context, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H, metrics.scale(), fade);
    }

    private void drawPreview(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my,
                             float fade, float seconds) {
        previewRect = new LinkenMenuLayout.Rect(PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        LinkenMenuChrome.roundedFill(context, previewRect.x(), previewRect.y(), previewRect.width(),
            previewRect.height(), 12.0f, LinkenMenuChrome.rgba(0x111713, 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, previewRect.x(), previewRect.y(), previewRect.width(),
            previewRect.height(), 12.0f, 1.0f, LinkenMenuChrome.rgba(0xFFFFFF, 24, fade), metrics.scale());

        LinkenMenuChrome.drawSpaced(context, LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.skin.preview"),
            previewRect.x() + 16.0f, previewRect.y() + 14.0f, 8.5f, 1.4f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        float headSize = 112.0f;
        float headX = previewRect.x() + (previewRect.width() - headSize) * 0.5f;
        float headY = previewRect.y() + 58.0f;
        LinkenMenuChrome.roundedFill(context, headX - 8.0f, headY - 8.0f, headSize + 16.0f, headSize + 16.0f,
            16.0f, LinkenMenuChrome.rgba(0x0B120E, 230, fade), metrics.scale());
        if (skin != null) {
            PlayerSkinDrawer.draw(context, skin, Math.round(headX), Math.round(headY), Math.round(headSize));
        } else {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), "?",
                headX + headSize * 0.5f, headY + headSize * 0.5f, 46.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));
        }

        LinkenMenuChrome.drawText(context, LinkenFonts.heading(),
            ellipsize(LinkenFonts.heading(), LinkenMenuChrome.username(), previewRect.width() - 32.0f, 15.0f),
            previewRect.x() + 16.0f, headY + headSize + 22.0f, 15.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.mono(), LinkenMenuChrome.licenseWord(),
            previewRect.x() + 16.0f, headY + headSize + 48.0f, 7.5f, 1.2f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));

        LinkenMenuLayout.Rect accountButton = new LinkenMenuLayout.Rect(
            previewRect.x() + 16.0f, previewRect.bottom() - 58.0f, previewRect.width() - 32.0f, 38.0f);
        boolean hovered = accountButton.contains(mx, my);
        accountSpring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) accountSpring.get();
        LinkenMenuChrome.drawSecondary(context, accountButton.x(), accountButton.y() - hover,
            accountButton.width(), accountButton.height(), 8.0f, hover, false, fade, metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(),
            I18n.get("linkenvisuals.linkmenu.skin.account"),
            accountButton.x() + accountButton.width() * 0.5f, accountButton.y() + 12.0f - hover,
            9.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3,
                LinkenMenuChrome.ACCENT, hover), fade));
    }

    private void drawToggleRow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                               String label, boolean enabled, int index, float mx, float my,
                               float fade, float seconds) {
        boolean hovered = rect.contains(mx, my);
        Spring spring = rowHover[index % rowHover.length];
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x111713, 0x18211B, hover), 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            1.0f, LinkenMenuChrome.rgba(enabled ? LinkenMenuChrome.ACCENT : 0xFFFFFF,
                enabled ? 120 : 20 + Math.round(hover * 30), fade), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(),
            ellipsize(LinkenFonts.headingLight(), label, rect.width() - 140.0f, 12.0f),
            rect.x() + 14.0f, rect.y() + 10.0f - hover, 12.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));

        float badgeW = 58.0f;
        float badgeX = rect.right() - 14.0f - badgeW;
        float badgeY = rect.y() + 12.0f - hover;
        LinkenMenuChrome.roundedFill(context, badgeX, badgeY, badgeW, 26.0f, 13.0f,
            LinkenMenuChrome.rgba(enabled ? LinkenMenuChrome.ACCENT : 0x222B25, enabled ? 28 : 220, fade),
            metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.mono(),
            I18n.get(enabled ? "linkenvisuals.linkmenu.skin.on" : "linkenvisuals.linkmenu.skin.off"),
            badgeX + badgeW * 0.5f, badgeY + 9.0f, 8.0f,
            LinkenMenuChrome.rgba(enabled ? LinkenMenuChrome.ACCENT : LinkenMenuChrome.MUTED2, fade));
    }

    private void drawValueRow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                              String label, String value, float mx, float my, float fade, float seconds,
                              int hoverIndex) {
        boolean hovered = rect.contains(mx, my);
        Spring spring = rowHover[hoverIndex % rowHover.length];
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x111713, 0x18211B, hover), 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            1.0f, LinkenMenuChrome.rgba(0xFFFFFF, 20 + Math.round(hover * 30), fade), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), label,
            rect.x() + 14.0f, rect.y() + 10.0f - hover, 12.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), value,
            rect.right() - 14.0f, rect.y() + 14.0f - hover, 9.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
    }

    private void drawAccountRow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                                float mx, float my, float fade, float seconds) {
        boolean hovered = rect.contains(mx, my);
        accountRowSpring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) accountRowSpring.get();
        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x111713, 0x18211B, hover), 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            1.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 60 + Math.round(hover * 90), fade), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(),
            I18n.get("linkenvisuals.linkmenu.skin.account"),
            rect.x() + 14.0f, rect.y() + 8.0f - hover, 11.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.skin.account_hint"),
            rect.x() + 14.0f, rect.y() + 26.0f - hover, 9.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
        LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), "OPEN",
            rect.right() - 14.0f, rect.y() + 18.0f - hover, 8.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
    }

    private void drawChip(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                          String label, int icon, float mx, float my, float fade, Spring spring, float seconds) {
        boolean hovered = rect.contains(mx, my);
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        LinkenMenuChrome.drawSecondary(context, rect.x(), rect.y(), rect.width(), rect.height(),
            8.0f, hover, false, fade, metrics.scale());
        float iconX = rect.x() + 16.0f;
        if (LinkenFonts.icons() != null) {
            IconPipeline.draw(context, LinkenFonts.icons(), icon, iconX + 7.0f,
                rect.y() + rect.height() * 0.5f, 14.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.MUTED2, LinkenMenuChrome.ACCENT, hover), fade));
        }
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), label, iconX + 24.0f,
            rect.y() + 15.0f, 10.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3, LinkenMenuChrome.TEXT, hover), fade));
    }

    private void drawDoneButton(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                                float mx, float my, float fade, float seconds) {
        boolean hovered = rect.contains(mx, my);
        doneSpring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) doneSpring.get();
        LinkenMenuChrome.roundedFill(context, rect.x() + 4.0f, rect.y() + rect.height() - 6.0f,
            rect.width() - 8.0f, 12.0f, 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(55 + 35 * hover), fade), metrics.scale());
        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF, hover * 0.07f), fade),
            metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "DONE",
            rect.x() + rect.width() * 0.5f, rect.y() + 13.0f - hover, 10.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.INK, fade));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) click.x() / metrics.scale();
        float my = (float) click.y() / metrics.scale();
        if (backRect != null && backRect.contains(mx, my)) {
            close();
            return true;
        }
        if (doneRect != null && doneRect.contains(mx, my)) {
            close();
            return true;
        }
        LinkenMenuLayout.Rect previewAccount = previewRect == null ? null
            : new LinkenMenuLayout.Rect(previewRect.x() + 16.0f, previewRect.bottom() - 58.0f,
                previewRect.width() - 32.0f, 38.0f);
        if ((previewAccount != null && previewAccount.contains(mx, my))
            || (accountRect != null && accountRect.contains(mx, my))) {
            if (client != null) {
                client.setScreen(new LinkenAltManagerScreen(this));
            }
            return true;
        }
        for (int i = 0; i < partRects.size(); i++) {
            if (partRects.get(i).contains(mx, my)) {
                GameOptions options = client != null ? client.options : null;
                if (options != null) {
                    PlayerModelPart part = parts.get(i);
                    options.setPlayerModelPart(part, !options.isPlayerModelPartEnabled(part));
                    options.write();
                }
                return true;
            }
        }
        if (mainArmRect != null && mainArmRect.contains(mx, my) && client != null) {
            GameOptions options = client.options;
            Arm arm = options.getMainArm().getValue();
            options.getMainArm().setValue(arm == Arm.LEFT ? Arm.RIGHT : Arm.LEFT);
            options.write();
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private static String ellipsize(dev.linkenvisuals.render.GlyphAtlas font, String text,
                                    float maxWidth, float size) {
        return LinkenMenuChrome.ellipsize(font, text, maxWidth, size);
    }

    @Override
    public void close() {
        if (client != null) {
            Screen target = parent;
            if (target == null || target == this) {
                target = client.world != null ? new LinkenPauseScreen() : new LinkenMainMenuScreen();
            }
            client.setScreen(target);
        }
    }
}
