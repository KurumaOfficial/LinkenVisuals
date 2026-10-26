package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1 reproduction of the donor LinkenPauseScreen (11a).
 * Clean topbar with mint bracket, server/world name, mint primary "Вернуться к игре" button,
 * 2-column tiles for settings/stats/feedback/bug/advancements/lan, and single bottom disconnect button.
 * Absolutely no English subtitles.
 */
public final class LinkenPauseScreen extends Screen {
    private static final float WIN_W = 440.0f;
    private static final float PAD = 22.0f;
    private static final float TOPBAR = 56.0f;
    private static final float BODY_TOP = 18.0f;
    private static final float ROW = 44.0f;
    private static final float GAP = 8.0f;
    private static final float DIVIDER_PAD = 6.0f;

    private record Item(String label, int iconType, Runnable action) {
    }

    private final List<Item> tiles = new ArrayList<>();
    private final Map<Integer, Spring> tileHovers = new HashMap<>();
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring resumeHover = new Spring(0.0, 900.0, 50.0);
    private final Spring disconnectHover = new Spring(0.0, 900.0, 50.0);

    private long lastFrame = System.nanoTime();
    private LinkenMenuLayout.Rect resumeRect;
    private LinkenMenuLayout.Rect disconnectRect;
    private final List<LinkenMenuLayout.Rect> tileRects = new ArrayList<>();

    public LinkenPauseScreen() {
        super(Text.empty());
        buildTiles();
    }

    private void buildTiles() {
        tiles.clear();
        MinecraftClient mc = MinecraftClient.getInstance();

        // Row 1: Настройки... | Статистика
        tiles.add(new Item("Настройки...", 0, () -> {
            if (mc != null) mc.setScreen(new LinkenSettingsScreen(this));
        }));
        tiles.add(new Item("Статистика", 1, () -> {
            if (mc != null && mc.player != null) {
                mc.setScreen(new StatsScreen(this, mc.player.getStatHandler()));
            }
        }));

        // Row 2: Оставить отзыв | Нашли ошибку?
        tiles.add(new Item("Оставить отзыв", 2, () -> {
            LinkenMenuChrome.openUrl("https://t.me/linkenvisuals");
        }));
        tiles.add(new Item("Нашли ошибку?", 3, () -> {
            LinkenMenuChrome.openUrl("https://t.me/linkenvisualsdev");
        }));

        // Row 3: Достижения | Открыть для сети
        tiles.add(new Item("Достижения", 4, () -> {
            if (mc != null && mc.getNetworkHandler() != null) {
                mc.setScreen(new AdvancementsScreen(mc.getNetworkHandler().getAdvancementHandler(), this));
            }
        }));
        tiles.add(new Item("Открыть для сети", 5, () -> {
            if (mc != null) {
                mc.setScreen(new OpenToLanScreen(this));
            }
        }));

        for (int i = 0; i < tiles.size(); i++) {
            tileHovers.put(i, new Spring(0.0, 900.0, 50.0));
        }
    }

    private float panelHeight() {
        int rows = 3;
        float gridH = rows * ROW + (rows - 1) * GAP;
        return TOPBAR + BODY_TOP + ROW + GAP + gridH + DIVIDER_PAD * 2.0f + 1.0f + GAP + ROW + 20.0f;
    }

    @Override
    protected void init() {
        LinkenFonts.retryIfUnavailable();
        LinkenFonts.body();
        LinkenFonts.bodyMedium();
        LinkenFonts.heading();
        LinkenFonts.headingLight();
        LinkenFonts.iconsFa();
        buildTiles();
        lastFrame = System.nanoTime();
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(null);
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

        // Dim backdrop
        context.fill(0, 0, width, height, 0x88050807);

        float w = WIN_W;
        float h = panelHeight();
        float x = (metrics.width() - w) * 0.5f;
        float y = (metrics.height() - h) * 0.5f;
        float slide = (1.0f - open) * 18.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        context.getMatrices().translate(0.0f, slide);

        // Window background using WindowMaterial
        WindowMaterial.window(context, x, y, w, h, metrics.scale(), open);

        // Topbar: "Меню" and world/server name
        float cy = y + TOPBAR * 0.5f;
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), "Меню",
            x + PAD, cy - 8.0f, 17.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, open));

        String serverName = worldName();
        LinkenMenuChrome.drawRightText(context, LinkenFonts.body(), serverName,
            x + w - PAD, cy - 6.0f, 11.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, open));

        context.fill(Math.round(x + PAD), Math.round(y + TOPBAR - 1.0f),
            Math.round(x + w - PAD), Math.round(y + TOPBAR),
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(18.0f * open)));

        // Primary Resume button
        float contentY = y + TOPBAR + BODY_TOP;
        resumeRect = new LinkenMenuLayout.Rect(x + PAD, contentY, w - PAD * 2.0f, ROW);
        boolean resumeHov = resumeRect.contains(mx, my);
        resumeHover.step(resumeHov ? 1.0 : 0.0, deltaSeconds);
        float rt = (float) resumeHover.get();
        float rty = contentY - rt;

        GlowPipeline.radial(context, resumeRect.x() + resumeRect.width() * 0.5f, rty + ROW * 0.5f,
            resumeRect.width() * 0.5f + 10.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round((45.0f + 25.0f * rt) * open)));
        LinkenMenuChrome.roundedFill(context, resumeRect.x(), rty, resumeRect.width(), ROW, 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF, rt * 0.08f), open), metrics.scale());
        LinkenMenuChrome.roundedFill(context, resumeRect.x() + 8.0f, rty + 1.0f, resumeRect.width() - 16.0f, 1.0f, 0.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(90.0f * open)), metrics.scale());

        int resumeInk = LinkenMenuChrome.rgba(LinkenMenuChrome.INK, open);
        ClickGuiIcons.play(context, resumeRect.x() + resumeRect.width() * 0.5f - 70.0f, rty + ROW * 0.5f, 14.0f, resumeInk);
        LinkenMenuChrome.drawCentered(context, LinkenFonts.bodyMedium(), "Вернуться к игре",
            resumeRect.x() + resumeRect.width() * 0.5f + 8.0f, rty + ROW * 0.5f, 13.0f, resumeInk);

        contentY += ROW + GAP;

        // 2-Column Grid (3 rows x 2 cols)
        tileRects.clear();
        float tileW = (w - PAD * 2.0f - GAP) * 0.5f;
        for (int i = 0; i < tiles.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            float tx = x + PAD + col * (tileW + GAP);
            float ty = contentY + row * (ROW + GAP);
            LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(tx, ty, tileW, ROW);
            tileRects.add(rect);

            boolean hov = rect.contains(mx, my);
            Spring s = tileHovers.get(i);
            if (s != null) s.step(hov ? 1.0 : 0.0, deltaSeconds);
            float t = s != null ? (float) s.get() : 0.0f;
            float rowY = ty - t * 2.0f;

            LinkenMenuChrome.roundedFill(context, tx, rowY, tileW, ROW, 8.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x181E1B, 0x222E26, t), Math.round(220.0f * open)), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, tx, rowY, tileW, ROW, 8.0f, 1.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round((14.0f + 40.0f * t) * open)), metrics.scale());

            int iconColor = LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3, LinkenMenuChrome.ACCENT, t), open);
            drawTileIcon(context, tiles.get(i).iconType(), tx + 20.0f, rowY + ROW * 0.5f, 14.0f, iconColor);

            LinkenMenuChrome.drawText(context, LinkenFonts.body(), tiles.get(i).label(),
                tx + 36.0f, rowY + ROW * 0.5f - 6.0f, 12.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT2, LinkenMenuChrome.TEXT, t), open));
        }

        contentY += 3 * ROW + 2 * GAP;

        // Divider
        contentY += DIVIDER_PAD;
        context.fill(Math.round(x + PAD), Math.round(contentY), Math.round(x + w - PAD), Math.round(contentY + 1.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(18.0f * open)));
        contentY += 1.0f + GAP;

        // Bottom Disconnect button (full width)
        disconnectRect = new LinkenMenuLayout.Rect(x + PAD, contentY, w - PAD * 2.0f, ROW);
        boolean disHov = disconnectRect.contains(mx, my);
        disconnectHover.step(disHov ? 1.0 : 0.0, deltaSeconds);
        float dt = (float) disconnectHover.get();
        float dty = contentY - dt;

        String disconnectText = (client != null && client.isInSingleplayer()) ? "Сохранить и выйти" : "Отключиться";
        LinkenMenuChrome.roundedFill(context, disconnectRect.x(), dty, disconnectRect.width(), ROW, 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x1C1315, 0x2E191C, dt), Math.round(220.0f * open)), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, disconnectRect.x(), dty, disconnectRect.width(), ROW, 8.0f, 1.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.DANGER, Math.round((22.0f + 55.0f * dt) * open)), metrics.scale());

        ClickGuiIcons.signOut(context, disconnectRect.x() + disconnectRect.width() * 0.5f - 68.0f, dty + ROW * 0.5f, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.DANGER, 0xFF6B6B, dt), open));
        LinkenMenuChrome.drawCentered(context, LinkenFonts.bodyMedium(), disconnectText,
            disconnectRect.x() + disconnectRect.width() * 0.5f + 8.0f, dty + ROW * 0.5f, 12.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.DANGER, 0xFF6B6B, dt), open));

        // Geometric mint corner brackets
        WindowMaterial.corners(context, x, y, w, h, metrics.scale(), open);

        context.getMatrices().popMatrix();
    }

    private void drawTileIcon(DrawContext context, int iconType, float cx, float cy, float s, int color) {
        switch (iconType) {
            case 0 -> ClickGuiIcons.gear(context, cx, cy, s, color);
            case 1 -> ClickGuiIcons.chartBar(context, cx, cy, s, color);
            case 2 -> ClickGuiIcons.comment(context, cx, cy, s, color);
            case 3 -> ClickGuiIcons.info(context, cx, cy, s, color);
            case 4 -> ClickGuiIcons.trophy(context, cx, cy, s, color);
            case 5 -> ClickGuiIcons.globe(context, cx, cy, s, color);
            default -> ClickGuiIcons.info(context, cx, cy, s, color);
        }
    }

    private String worldName() {
        MinecraftClient mc = client != null ? client : MinecraftClient.getInstance();
        if (mc == null) return "World";
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return mc.getServer().getSaveProperties().getLevelName();
        }
        if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().name;
        }
        return "New World";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubleClick);
        }
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) click.x() / metrics.scale();
        float my = (float) click.y() / metrics.scale();

        if (resumeRect != null && resumeRect.contains(mx, my)) {
            close();
            return true;
        }

        if (disconnectRect != null && disconnectRect.contains(mx, my)) {
            if (client != null) {
                client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
            }
            return true;
        }

        for (int i = 0; i < tileRects.size() && i < tiles.size(); i++) {
            if (tileRects.get(i).contains(mx, my)) {
                tiles.get(i).action().run();
                return true;
            }
        }

        return super.mouseClicked(click, doubleClick);
    }
}
