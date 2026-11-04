package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.CreditsAndAttributionScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1 reproduction of the donor SettingsHubScreen (9b).
 * Topbar with mint corner bracket, FOV slider with glowing track,
 * 10 category tiles (2x5), and mint "Готово" button.
 * Pressing ESC or clicking "Готово" cleanly persists settings and returns to parent.
 */
public final class LinkenSettingsScreen extends Screen {
    private static final float WIN_W = 840.0f;
    private static final float PAD_X = 22.0f;
    private static final float TOPBAR_H = 68.0f;
    private static final float FOV_H = 50.0f;
    private static final float FOOTER_H = 64.0f;
    private static final float TILES_PAD = 20.0f;
    private static final float TILE_H = 56.0f;
    private static final float TILE_GAP = 10.0f;
    private static final float TILES_H = TILES_PAD * 2.0f + 5.0f * TILE_H + 4.0f * TILE_GAP;
    private static final float WIN_H = TOPBAR_H + FOV_H + TILES_H + FOOTER_H;

    private static final int DATA_INDEX = 8;
    private static final String[] CATEGORIES = {
        "Внешний вид", "Музыка и звуки", "Настройки графики", "Управление", "Язык",
        "Настройки чата", "Наборы ресурсов", "Специальные возможности", "Собираемые данные",
        "Титры и атрибуции"
    };

    private final Screen parent;
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring onlineHover = new Spring(0.0, 900.0, 50.0);
    private final Spring doneHover = new Spring(0.0, 900.0, 50.0);
    private final Map<Integer, Spring> tileHovers = new HashMap<>();

    private long lastFrame = System.nanoTime();
    private boolean draggingFov;

    private LinkenMenuLayout.Rect onlineRect;
    private LinkenMenuLayout.Rect doneRect;
    private LinkenMenuLayout.Rect fovTrackRect;
    private final List<LinkenMenuLayout.Rect> tileRects = new ArrayList<>();

    public LinkenSettingsScreen(Screen parent) {
        super(Text.empty());
        this.parent = parent;
        for (int i = 0; i < 10; i++) {
            tileHovers.put(i, new Spring(0.0, 900.0, 50.0));
        }
    }

    @Override
    protected void init() {
        LinkenFonts.body();
        LinkenFonts.bodyMedium();
        LinkenFonts.heading();
        LinkenFonts.headingLight();
        LinkenFonts.iconsFa();
        lastFrame = System.nanoTime();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        MinecraftClient mc = client != null ? client : MinecraftClient.getInstance();
        if (mc != null) {
            mc.options.write();
            mc.setScreen(parent != null ? parent : new LinkenMainMenuScreen());
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.isEscape()) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float deltaSeconds = Math.min((now - lastFrame) / 1_000_000_000.0f, 0.05f);
        lastFrame = now;
        openSpring.step(1.0, deltaSeconds);
        float open = (float) openSpring.get();

        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = mouseX / metrics.scale();
        float my = mouseY / metrics.scale();

        // Dim background if in-game, otherwise draw lighthouse art
        if (client != null && client.world != null) {
            context.fill(0, 0, width, height, 0x88050807);
        } else {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(metrics.scale(), metrics.scale());
            LinkenMenuChrome.drawBackground(context, metrics, 0.0f, 0.0f, 1.0f);
            context.getMatrices().popMatrix();
        }

        float x = (metrics.width() - WIN_W) * 0.5f;
        float y = (metrics.height() - WIN_H) * 0.5f;
        float slide = (1.0f - open) * 20.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        context.getMatrices().translate(0.0f, slide);

        // Window background using WindowMaterial
        WindowMaterial.window(context, x, y, WIN_W, WIN_H, metrics.scale(), open);

        // 1. Topbar
        drawTopbar(context, metrics, x, y, WIN_W, mx, my, deltaSeconds, open);
        LinkenMenuChrome.roundedFill(context, x + PAD_X, y + TOPBAR_H - 1.0f, WIN_W - PAD_X * 2.0f, 1.0f, 0.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(12.0f * open)), metrics.scale());

        // 2. FOV Row
        drawFovRow(context, metrics, x, y + TOPBAR_H, WIN_W, mx, my, open);
        LinkenMenuChrome.roundedFill(context, x + PAD_X, y + TOPBAR_H + FOV_H - 1.0f, WIN_W - PAD_X * 2.0f, 1.0f, 0.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(12.0f * open)), metrics.scale());

        // 3. 10 Category Tiles
        drawTiles(context, metrics, x, y + TOPBAR_H + FOV_H, WIN_W, mx, my, deltaSeconds, open);

        // 4. Footer: "Готово" button
        drawFooter(context, metrics, x, y + WIN_H - FOOTER_H, WIN_W, mx, my, deltaSeconds, open);

        // 5. Geometric mint corner brackets
        WindowMaterial.corners(context, x, y, WIN_W, WIN_H, metrics.scale(), open);

        context.getMatrices().popMatrix();
    }

    private void drawTopbar(DrawContext context, LinkenMenuLayout.Metrics metrics,
                            float x, float y, float w, float mx, float my, float dt, float open) {
        float cy = y + TOPBAR_H * 0.5f;

        // "Настройки" title in Golos/BodyMedium, clean Russian text
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), "Настройки",
            x + PAD_X, cy - 8.0f, 17.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, open));

        // "Онлайн" button
        float bh = 36.0f;
        float bw = 110.0f;
        float bx = x + w - PAD_X - bw;
        float by = cy - bh * 0.5f;
        onlineRect = new LinkenMenuLayout.Rect(bx, by, bw, bh);

        boolean hov = onlineRect.contains(mx, my);
        onlineHover.step(hov ? 1.0 : 0.0, dt);
        float t = (float) onlineHover.get();

        LinkenMenuChrome.roundedFill(context, bx, by, bw, bh, 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x181E1B, 0x222E26, t), Math.round(220.0f * open)), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, bx, by, bw, bh, 8.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round((14.0f + 35.0f * t) * open)), metrics.scale());

        ClickGuiIcons.globe(context, bx + 20.0f, by + bh * 0.5f, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, open));
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), "Онлайн",
            bx + 36.0f, by + bh * 0.5f - 5.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, open));
    }

    private void drawFovRow(DrawContext context, LinkenMenuLayout.Metrics metrics,
                            float x, float y, float w, float mx, float my, float open) {
        float cy = y + FOV_H * 0.5f;
        MinecraftClient mc = client != null ? client : MinecraftClient.getInstance();

        // Label: "Поле зрения"
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), "Поле зрения",
            x + PAD_X, cy - 6.0f, 13.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, open));

        // Format FOV value
        int fov = mc != null ? mc.options.getFov().getValue() : 70;
        String val = fovLabel(fov);
        LinkenMenuChrome.drawRightText(context, LinkenFonts.body(), val,
            x + w - PAD_X, cy - 6.0f, 12.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, open));

        float trackX = x + PAD_X + 115.0f;
        float trackRight = x + w - PAD_X - 120.0f;
        float trackW = Math.max(20.0f, trackRight - trackX);
        float ty = cy - 2.0f;
        fovTrackRect = new LinkenMenuLayout.Rect(trackX - 6.0f, cy - 11.0f, trackW + 12.0f, 22.0f);

        if (draggingFov && mc != null) {
            long handle = mc.getWindow().getHandle();
            if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                float frac = MathHelper.clamp((mx - trackX) / trackW, 0.0f, 1.0f);
                mc.options.getFov().setValue(30 + Math.round(frac * 80.0f));
            } else {
                draggingFov = false;
            }
        }

        float frac = MathHelper.clamp((fov - 30) / 80.0f, 0.0f, 1.0f);
        // Track background
        LinkenMenuChrome.roundedFill(context, trackX, ty, trackW, 4.0f, 2.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(22.0f * open)), metrics.scale());
        // Filled glowing track
        if (frac > 0.001f) {
            GlowPipeline.radial(context, trackX + trackW * frac * 0.5f, ty + 2.0f, trackW * frac * 0.5f + 6.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(35.0f * open)));
            LinkenMenuChrome.roundedFill(context, trackX, ty, trackW * frac, 4.0f, 2.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, open), metrics.scale());
        }

        // Draggable knob
        float knobX = trackX + trackW * frac;
        LinkenMenuChrome.roundedFill(context, knobX - 6.0f, cy - 6.0f, 12.0f, 12.0f, 6.0f,
            LinkenMenuChrome.rgba(0x060908, Math.round(90.0f * open)), metrics.scale());
        LinkenMenuChrome.roundedFill(context, knobX - 5.0f, cy - 5.0f, 10.0f, 10.0f, 5.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, open), metrics.scale());
    }

    private static String fovLabel(int fov) {
        if (fov >= 110) return "Квейк Про · " + fov;
        if (fov == 70) return "Нормально · 70";
        if (fov <= 30) return "Мин. · " + fov;
        return String.valueOf(fov);
    }

    private void drawTiles(DrawContext context, LinkenMenuLayout.Metrics metrics,
                           float x, float y, float w, float mx, float my, float dt, float open) {
        float colW = (w - PAD_X * 2.0f - TILE_GAP) * 0.5f;
        float top = y + TILES_PAD;
        tileRects.clear();

        for (int i = 0; i < 10; i++) {
            int col = i % 2;
            int row = i / 2;
            float tx = x + PAD_X + col * (colW + TILE_GAP);
            float tyRest = top + row * (TILE_H + TILE_GAP);
            LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(tx, tyRest, colW, TILE_H);
            tileRects.add(rect);

            boolean disabled = i == DATA_INDEX;
            boolean hov = !disabled && rect.contains(mx, my);
            Spring spring = tileHovers.get(i);
            if (spring != null) {
                spring.step(hov ? 1.0 : 0.0, dt);
            }
            float t = spring != null ? (float) spring.get() : 0.0f;
            float alpha = (disabled ? 0.42f : 1.0f) * open;
            float ty = tyRest - t * 2.0f;

            // Card background and border
            LinkenMenuChrome.roundedFill(context, tx, ty, colW, TILE_H, 10.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x181E1B, 0x222E26, t), Math.round(220.0f * alpha)), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, tx, ty, colW, TILE_H, 10.0f, 1.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round((disabled ? 18.0f : (18.0f + 45.0f * t)) * alpha)), metrics.scale());

            // Icon square box
            float it = 36.0f;
            float ix = tx + 14.0f;
            float iy = ty + (TILE_H - it) * 0.5f;
            LinkenMenuChrome.roundedFill(context, ix, iy, it, it, 8.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(14.0f * alpha)), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, ix, iy, it, it, 8.0f, 1.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(25.0f * alpha)), metrics.scale());

            int iconColor = disabled ? LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, alpha)
                : LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3, LinkenMenuChrome.ACCENT, t), alpha);
            drawCategoryIcon(context, i, ix + it * 0.5f, iy + it * 0.5f, 20.0f, iconColor);

            // Title
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), CATEGORIES[i],
                ix + it + 12.0f, ty + TILE_H * 0.5f - 6.0f, 13.5f,
                LinkenMenuChrome.rgba(disabled ? LinkenMenuChrome.MUTED2 : LinkenMenuChrome.TEXT2, alpha));

            // Chevron "›" on the right
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), "›",
                tx + colW - 18.0f, ty + TILE_H * 0.5f - 7.0f, 15.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, alpha));
        }
    }

    private void drawCategoryIcon(DrawContext context, int index, float cx, float cy, float s, int color) {
        switch (index) {
            case 0 -> ClickGuiIcons.user(context, cx, cy, s, color);
            case 1 -> ClickGuiIcons.musicNote(context, cx, cy, s, color);
            case 2 -> ClickGuiIcons.monitor(context, cx, cy, s, color);
            case 3 -> ClickGuiIcons.keyboard(context, cx, cy, s, color);
            case 4 -> ClickGuiIcons.globe(context, cx, cy, s, color);
            case 5 -> ClickGuiIcons.chat(context, cx, cy, s, color);
            case 6 -> ClickGuiIcons.layers(context, cx, cy, s, color);
            case 7 -> ClickGuiIcons.accessibility(context, cx, cy, s, color);
            case 8 -> ClickGuiIcons.database(context, cx, cy, s, color);
            default -> ClickGuiIcons.info(context, cx, cy, s, color);
        }
    }

    private void drawFooter(DrawContext context, LinkenMenuLayout.Metrics metrics,
                            float x, float y, float w, float mx, float my, float dt, float open) {
        float cy = y + FOOTER_H * 0.5f;
        String label = "Готово";
        float bw = 130.0f;
        float bh = 42.0f;
        float bx = x + w - PAD_X - bw;
        float by = cy - bh * 0.5f - 9.0f;
        doneRect = new LinkenMenuLayout.Rect(bx, by, bw, bh);

        boolean hov = doneRect.contains(mx, my);
        doneHover.step(hov ? 1.0 : 0.0, dt);
        float t = (float) doneHover.get();
        float ty = by - t;

        // Glow behind button
        GlowPipeline.radial(context, bx + bw * 0.5f, ty + bh * 0.5f, bw * 0.55f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round((40.0f + 30.0f * t) * open)));
        // Filled mint green button
        LinkenMenuChrome.roundedFill(context, bx, ty, bw, bh, 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF, t * 0.08f), open), metrics.scale());
        LinkenMenuChrome.roundedFill(context, bx + 8.0f, ty + 1.0f, bw - 16.0f, 1.0f, 0.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(90.0f * open)), metrics.scale());

        int ink = LinkenMenuChrome.rgba(LinkenMenuChrome.INK, open);
        ClickGuiIcons.check(context, bx + 26.0f, ty + bh * 0.5f, 16.0f, ink);
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), label,
            bx + 44.0f, ty + bh * 0.5f - 5.0f, 11.5f, ink);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubleClick);
        }
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) click.x() / metrics.scale();
        float my = (float) click.y() / metrics.scale();

        if (fovTrackRect != null && fovTrackRect.contains(mx, my)) {
            draggingFov = true;
            return true;
        }

        if (doneRect != null && doneRect.contains(mx, my)) {
            close();
            return true;
        }

        if (onlineRect != null && onlineRect.contains(mx, my)) {
            if (client != null) {
                client.setScreen(new LinkenSubSettingsScreen(this, "Онлайн", LinkenSettingsCatalog.graphics()));
            }
            return true;
        }

        for (int i = 0; i < tileRects.size() && i < 10; i++) {
            if (i == DATA_INDEX) continue;
            if (tileRects.get(i).contains(mx, my)) {
                openCategory(i);
                return true;
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            draggingFov = false;
        }
        return super.mouseReleased(click);
    }

    private void openCategory(int i) {
        if (client == null) return;
        switch (i) {
            case 0 -> client.setScreen(new LinkenSkinScreen(this));
            case 1 -> client.setScreen(new LinkenSubSettingsScreen(this, "Музыка и звуки", LinkenSettingsCatalog.sound()));
            case 2 -> client.setScreen(new LinkenSubSettingsScreen(this, "Настройки графики", LinkenSettingsCatalog.graphics()));
            case 3 -> client.setScreen(new LinkenSubSettingsScreen(this, "Управление", LinkenSettingsCatalog.controls()));
            case 4 -> client.setScreen(new LinkenLanguageScreen(this));
            case 5 -> client.setScreen(new LinkenSubSettingsScreen(this, "Настройки чата", LinkenSettingsCatalog.chat()));
            case 6 -> client.setScreen(new LinkenResourcePacksScreen(this));
            case 7 -> client.setScreen(new LinkenSubSettingsScreen(this, "Специальные возможности", LinkenSettingsCatalog.accessibility()));
            case 9 -> client.setScreen(new CreditsAndAttributionScreen(this));
            default -> { }
        }
    }
}
