package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.audio.LinkenSounds;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared LINKEN screen shell used by the pause menu and settings hub.
 *
 * <p>The shell draws a single centred panel over either the blurred world or
 * the tower artwork, with a two-column tile grid. Actions are supplied by the
 * concrete screen; this class only owns chrome, hit-testing and transitions.</p>
 */
public abstract class LinkenHubScreen extends Screen {
    protected record Entry(String label, String detail, int icon, boolean primary, boolean danger, Runnable action) {
    }

    private final Screen returnScreen;
    private final String heading;
    private final String subheading;
    private final List<Entry> entries = new ArrayList<>();
    private final List<LinkenMenuLayout.Rect> entryRects = new ArrayList<>();
    private final Map<Integer, Spring> hover = new HashMap<>();
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring closeSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring backSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring doneSpring = new Spring(0.0, 900.0, 50.0);
    private long lastFrame = System.nanoTime();
    private boolean openSoundPlayed;
    private LinkenMenuLayout.Rect closeRect;
    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect doneRect;

    protected LinkenHubScreen(Screen returnScreen, String heading, String subheading) {
        super(Text.empty());
        this.returnScreen = returnScreen;
        this.heading = heading;
        this.subheading = subheading;
    }

    /** Concrete screens add their actions here; called on every {@link #init()}. */
    protected abstract void buildEntries(List<Entry> out);

    protected Screen returnScreen() {
        return returnScreen;
    }

    @Override
    protected void init() {
        entries.clear();
        entryRects.clear();
        hover.clear();
        buildEntries(entries);
        openSpring.reset(0.0);
        closeSpring.reset(0.0);
        backSpring.reset(0.0);
        doneSpring.reset(0.0);
        openSoundPlayed = false;
        lastFrame = System.nanoTime();
    }

    @Override
    public boolean shouldPause() {
        return returnScreen != null && returnScreen.shouldPause();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        if (client != null) {
            Screen target = returnScreen;
            while (target == this || target instanceof LinkenSettingsScreen
                    || target instanceof net.minecraft.client.gui.screen.option.OptionsScreen) {
                if (target instanceof LinkenHubScreen hub && hub.returnScreen() != target) {
                    target = hub.returnScreen();
                } else if (target instanceof net.minecraft.client.gui.screen.option.OptionsScreen opt) {
                    try {
                        target = ((dev.linkenvisuals.mixin.OptionsScreenAccessor) opt).linkenvisuals$getParent();
                    } catch (Throwable t) {
                        target = null;
                    }
                } else {
                    target = null;
                    break;
                }
            }
            if (target == null || target instanceof LinkenSettingsScreen
                    || target instanceof net.minecraft.client.gui.screen.option.OptionsScreen) {
                target = client.world != null ? new LinkenPauseScreen() : new LinkenMainMenuScreen();
            }
            client.setScreen(target);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float deltaSeconds = Math.min((now - lastFrame) / 1_000_000_000.0f, 0.05f);
        lastFrame = now;
        if (client != null && !openSoundPlayed) {
            client.getSoundManager().play(PositionedSoundInstance.ui(LinkenVisuals.OPEN_SOUND, 0.9f, 1.0f));
            openSoundPlayed = true;
        }
        openSpring.step(1.0, deltaSeconds);
        float fade = (float) openSpring.get();

        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) mouseX / metrics.scale();
        float my = (float) mouseY / metrics.scale();

        if (client != null && client.world != null) {
            // Screen#renderWithTooltip already calls renderBackground (and thus
            // applies the menu blur) before render(). Applying it again here
            // would throw "Can only blur once per frame".
            context.fill(0, 0, width, height, 0x66050807);
        } else {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(metrics.scale(), metrics.scale());
            LinkenMenuChrome.drawBackground(context, metrics, 0.0f, 0.0f, 1.0f);
            context.getMatrices().popMatrix();
        }

        float slideY = (1.0f - fade) * 20.0f;
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        context.getMatrices().translate(0.0f, slideY);
        LinkenMenuChrome.drawWordmark(context, fade);
        LinkenMenuChrome.drawClock(context, metrics, fade);
        GlowPipeline.radial(context, metrics.width() * 0.5f, 540.0f, 640.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 8, fade));

        float panelWidth = Math.min(1040.0f, metrics.width() - 240.0f);
        int primaryCount = 0;
        for (Entry entry : entries) {
            if (entry.primary()) {
                primaryCount++;
            }
        }
        int gridEntries = entries.size() - primaryCount;
        int rows = Math.max(1, (gridEntries + 1) / 2);
        float panelHeight = 118.0f + (primaryCount > 0 ? 60.0f + 16.0f : 0.0f)
            + rows * 90.0f - 14.0f + 44.0f;
        float panelX = (metrics.width() - panelWidth) * 0.5f;
        float panelY = (metrics.height() - panelHeight) * 0.5f;
        float contentMy = my - slideY;

        LinkenMenuChrome.roundedFill(context, panelX, panelY, panelWidth, panelHeight, 14.0f,
            LinkenMenuChrome.rgba(0x0E1411, 242, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, panelX, panelY, panelWidth, panelHeight, 14.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 24, fade), metrics.scale());
        LinkenMenuChrome.roundedFill(context, panelX + 30.0f, panelY + 1.0f, panelWidth - 60.0f, 1.0f, 0.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, 40, fade), metrics.scale());

        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), heading,
            panelX + 38.0f, panelY + 30.0f, 27.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), subheading,
            panelX + 40.0f, panelY + 72.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
        context.fill(Math.round(panelX + 38.0f), Math.round(panelY + 104.0f),
            Math.round(panelX + panelWidth - 38.0f), Math.round(panelY + 105.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 20, fade));

        closeRect = new LinkenMenuLayout.Rect(panelX + panelWidth - 38.0f - 40.0f, panelY + 28.0f, 40.0f, 40.0f);
        boolean closeOver = closeRect.contains(mx, contentMy, 4.0f) || closeRect.contains(mx, my, 4.0f);
        closeSpring.step(closeOver ? 1.0 : 0.0, deltaSeconds);
        LinkenMenuChrome.drawSecondary(context, closeRect.x(), closeRect.y(), closeRect.width(), closeRect.height(),
            8.0f, (float) closeSpring.get(), false, fade, metrics.scale());
        drawCloseGlyph(context, closeRect, fade, (float) closeSpring.get());

        entryRects.clear();
        float pad = 38.0f;
        float gap = 14.0f;
        float contentWidth = panelWidth - pad * 2.0f;
        float tileWidth = (contentWidth - gap) * 0.5f;
        float y = panelY + 122.0f;
        int gridIndex = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            LinkenMenuLayout.Rect rect;
            if (entry.primary()) {
                rect = new LinkenMenuLayout.Rect(panelX + pad, y, contentWidth, 60.0f);
                y += 60.0f + 16.0f;
            } else {
                int column = gridIndex % 2;
                int row = gridIndex / 2;
                rect = new LinkenMenuLayout.Rect(panelX + pad + column * (tileWidth + gap),
                    y + row * 90.0f, tileWidth, 76.0f);
                gridIndex++;
            }
            entryRects.add(rect);
            drawEntry(context, metrics, entry, rect, mx, contentMy, fade, deltaSeconds, i);
        }

        backRect = new LinkenMenuLayout.Rect(panelX + pad - 6.0f, panelY + panelHeight - 38.0f, 130.0f, 26.0f);
        boolean backOver = backRect.contains(mx, contentMy, 4.0f) || backRect.contains(mx, my, 4.0f);
        backSpring.step(backOver ? 1.0 : 0.0, deltaSeconds);
        float backHover = (float) backSpring.get();
        if (backHover > 0.01f) {
            LinkenMenuChrome.roundedFill(context, backRect.x(), backRect.y(), backRect.width(), backRect.height(),
                6.0f, LinkenMenuChrome.rgba(0xFFFFFF, Math.round(22 * backHover * fade)), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, backRect.x(), backRect.y(), backRect.width(), backRect.height(),
                6.0f, 1.0f, LinkenMenuChrome.rgba(0xFFFFFF, Math.round(50 * backHover * fade)), metrics.scale());
        }
        int backTextColor = LinkenMenuChrome.mix(LinkenMenuChrome.MUTED4, LinkenMenuChrome.TEXT, backHover);
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), "ESC  /  BACK",
            panelX + pad, panelY + panelHeight - 30.0f, 7.5f, 1.4f,
            LinkenMenuChrome.rgba(backTextColor, fade));

        float doneW = 100.0f;
        float doneH = 26.0f;
        doneRect = new LinkenMenuLayout.Rect(panelX + panelWidth - pad - doneW, panelY + panelHeight - 38.0f, doneW, doneH);
        boolean doneOver = doneRect.contains(mx, contentMy, 4.0f) || doneRect.contains(mx, my, 4.0f);
        doneSpring.step(doneOver ? 1.0 : 0.0, deltaSeconds);
        float doneHover = (float) doneSpring.get();
        int doneBg = LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF, doneHover * 0.15f);
        LinkenMenuChrome.roundedFill(context, doneRect.x(), doneRect.y() - doneHover, doneRect.width(), doneRect.height(),
            6.0f, LinkenMenuChrome.rgba(doneBg, Math.round(230 * fade)), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, doneRect.x(), doneRect.y() - doneHover, doneRect.width(), doneRect.height(),
            6.0f, 1.0f, LinkenMenuChrome.rgba(0xFFFFFF, Math.round(60 * fade)), metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "ГОТОВО",
            doneRect.x() + doneW * 0.5f, doneRect.y() + 17.0f - doneHover, 10.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.INK, fade));
        WindowMaterial.corners(context, panelX, panelY, panelWidth, panelHeight, metrics.scale(), fade);
        context.getMatrices().popMatrix();
    }

    private void drawEntry(DrawContext context, LinkenMenuLayout.Metrics metrics, Entry entry,
                           LinkenMenuLayout.Rect rect, float mx, float my, float fade,
                           float deltaSeconds, int index) {
        boolean hovered = rect.contains(mx, my);
        Spring spring = hover.computeIfAbsent(index, key -> new Spring(0.0, 900.0, 50.0));
        spring.step(hovered ? 1.0 : 0.0, deltaSeconds);
        float hoverValue = (float) spring.get();

        if (entry.primary()) {
            LinkenMenuChrome.roundedFill(context, rect.x() + 4.0f, rect.y() + rect.height() - 6.0f,
                rect.width() - 8.0f, 12.0f, 8.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(55 + 35 * hoverValue), fade),
                metrics.scale());
            LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hoverValue, rect.width(), rect.height(),
                8.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF,
                    hoverValue * 0.08f), fade), metrics.scale());
        } else {
            int fill = LinkenMenuChrome.mix(0x181E1B, 0x202A23, hoverValue);
            LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hoverValue, rect.width(), rect.height(),
                8.0f, LinkenMenuChrome.rgba(fill, Math.round(216 * fade)), metrics.scale());
            int border = entry.danger() ? LinkenMenuChrome.DANGER : 0xFFFFFF;
            LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hoverValue, rect.width(), rect.height(),
                8.0f, 1.0f, LinkenMenuChrome.rgba(border,
                    Math.round((11 + (hovered ? 55 : 0)) * fade)), metrics.scale());
        }

        int content = entry.primary() ? LinkenMenuChrome.INK
            : (entry.danger() ? LinkenMenuChrome.DANGER
            : LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3, LinkenMenuChrome.ACCENT, hoverValue));
        float iconSize = 16.0f;
        float iconX = rect.x() + 18.0f;
        float centerY = rect.y() + rect.height() * 0.5f - hoverValue;
        GlyphAtlas icons = LinkenFonts.icons();
        if (icons != null) {
            IconPipeline.draw(context, icons, entry.icon(), iconX + iconSize * 0.5f, centerY,
                iconSize, LinkenMenuChrome.rgba(content, fade));
        }
        float labelX = iconX + iconSize + 14.0f;
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), entry.label(),
            labelX, rect.y() + 18.0f - hoverValue, 13.0f, LinkenMenuChrome.rgba(content, fade));
        if (entry.detail() != null && !entry.detail().isBlank()) {
            String detail = LinkenMenuChrome.ellipsize(LinkenFonts.body(), entry.detail(),
                rect.right() - labelX - 18.0f, 8.8f);
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), detail,
                labelX, rect.y() + 43.0f - hoverValue, 8.8f,
                LinkenMenuChrome.rgba(entry.primary() ? 0x365345 : LinkenMenuChrome.MUTED2, fade));
        }
    }

    private void drawCloseGlyph(DrawContext context, LinkenMenuLayout.Rect rect, float fade, float hover) {
        int color = LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.MUTED,
            LinkenMenuChrome.TEXT, hover), fade);
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(rect.x() + rect.width() * 0.5f, rect.y() + rect.height() * 0.5f);
        matrices.rotate((float) Math.toRadians(45.0));
        context.fill(-7, 0, 7, 1, color);
        matrices.rotate((float) Math.toRadians(90.0));
        context.fill(-7, 0, 7, 1, color);
        matrices.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) click.x() / metrics.scale();
        float my = (float) click.y() / metrics.scale();
        float slideY = (1.0f - (float) openSpring.get()) * 20.0f;
        float contentMy = my - slideY;

        if (closeRect != null && (closeRect.contains(mx, contentMy, 8.0f) || closeRect.contains(mx, my, 8.0f))) {
            playClick();
            close();
            return true;
        }
        if (backRect != null && (backRect.contains(mx, contentMy, 6.0f) || backRect.contains(mx, my, 6.0f))) {
            playClick();
            close();
            return true;
        }
        if (doneRect != null && (doneRect.contains(mx, contentMy, 6.0f) || doneRect.contains(mx, my, 6.0f))) {
            playClick();
            close();
            return true;
        }
        for (int i = 0; i < entryRects.size() && i < entries.size(); i++) {
            LinkenMenuLayout.Rect rect = entryRects.get(i);
            if (rect.contains(mx, contentMy) || rect.contains(mx, my)) {
                playClick();
                entries.get(i).action().run();
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.isEscape()) {
            playClick();
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(LinkenSounds.UI_CLICK, 0.8f, 1.0f));
        }
    }
}
