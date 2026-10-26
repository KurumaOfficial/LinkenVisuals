package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared LINKEN shell for the vanilla option pages. Values are applied through
 * SimpleOption immediately; this screen only owns layout and interaction.
 */
public final class LinkenSubSettingsScreen extends Screen {
    private static final float WINDOW_W = 1060.0f;
    private static final float TOPBAR_H = 70.0f;
    private static final float FOOTER_H = 64.0f;
    private static final float ROW_H = 52.0f;
    private static final float ROW_GAP = 8.0f;
    private static final float PAD = 20.0f;
    private static final float COL_GAP = 14.0f;

    private float winX;
    private float winY;

    private final Screen parent;
    private final String crumb;
    private final List<LinkenSettingsOption> options;
    private final List<LinkenSettingsOption> filtered = new ArrayList<>();
    private final List<LinkenMenuLayout.Rect> rowRects = new ArrayList<>();
    private final List<LinkenMenuLayout.Rect> sliderRects = new ArrayList<>();
    private final List<LinkenSettingsOption> rowOptions = new ArrayList<>();
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring backSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring doneSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring[] rowHover = new Spring[64];

    private final dev.linkenvisuals.util.input.TextInput query =
        new dev.linkenvisuals.util.input.TextInput().maxLength(40);
    private boolean searchFocused;
    private float scroll;
    private float scrollTarget;
    private int dragging = -1;
    private long lastFrame = System.nanoTime();

    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect searchRect;
    private LinkenMenuLayout.Rect doneRect;

    public LinkenSubSettingsScreen(Screen parent, String crumb, List<LinkenSettingsOption> options) {
        super(Text.literal("Settings / " + crumb));
        this.parent = parent;
        this.crumb = crumb;
        this.options = List.copyOf(options);
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0, 900.0, 50.0);
        }
    }

    @Override
    protected void init() {
        refresh();
        scroll = 0.0f;
        scrollTarget = 0.0f;
        openSpring.reset(0.0);
        lastFrame = System.nanoTime();
    }

    private void refresh() {
        filtered.clear();
        filtered.addAll(LinkenSettingsFilter.filter(options, query.value()));
        scrollTarget = clampScroll(scrollTarget);
        scroll = clampScroll(scroll);
    }

    private float windowHeight() {
        int rows = Math.max(1, (filtered.size() + 1) / 2);
        float content = PAD * 2.0f + rows * ROW_H + Math.max(0, rows - 1) * ROW_GAP;
        return Math.min(TOPBAR_H + content + FOOTER_H, 840.0f);
    }

    private float listTop() {
        return winY + TOPBAR_H;
    }

    private float listHeight() {
        return Math.max(0.0f, windowHeight() - TOPBAR_H - FOOTER_H);
    }

    private float maxScroll() {
        int rows = Math.max(0, (filtered.size() + 1) / 2);
        float content = PAD * 2.0f + rows * ROW_H + Math.max(0, rows - 1) * ROW_GAP;
        return Math.max(0.0f, content - listHeight());
    }

    private float clampScroll(float value) {
        return Math.max(0.0f, Math.min(value, maxScroll()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = Math.min((now - lastFrame) / 1_000_000_000.0f, 0.05f);
        lastFrame = now;
        openSpring.step(1.0, seconds);
        float fade = (float) openSpring.get();

        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        winX = (metrics.width() - WINDOW_W) * 0.5f;
        winY = Math.max(80.0f, (metrics.height() - windowHeight()) * 0.5f);
        float mx = mouseX / metrics.scale();
        float my = mouseY / metrics.scale();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        LinkenMenuChrome.drawBackground(context, metrics, 0.0f, 0.0f, 1.0f);
        GlowPipeline.radial(context, metrics.width() * 0.54f, 470.0f, 720.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 10, fade));

        context.getMatrices().translate(0.0f, (1.0f - fade) * 18.0f);
        LinkenMenuChrome.drawWordmark(context, fade);
        LinkenMenuChrome.drawClock(context, metrics, fade);

        backRect = new LinkenMenuLayout.Rect(metrics.width() - LinkenMenuLayout.MARGIN - 132.0f,
            104.0f, 132.0f, 44.0f);
        drawChip(context, metrics, backRect, "Назад", LinkenMenuChrome.ICON_POWER, mx, my, fade, backSpring, seconds);

        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), "НАСТРОЙКИ",
            LinkenMenuLayout.MARGIN, 146.0f, 8.5f, 1.6f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), crumb,
            LinkenMenuLayout.MARGIN, 162.0f, 40.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        String count = filtered.size() + " параметров";
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), count,
            LinkenMenuLayout.MARGIN, 218.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        drawWindow(context, metrics, mx, my, fade, seconds);
        context.getMatrices().popMatrix();
    }

    private void drawWindow(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my,
                            float fade, float seconds) {
        float height = windowHeight();
        LinkenMenuChrome.roundedFill(context, winX, winY, WINDOW_W, height, 14.0f,
            LinkenMenuChrome.rgba(0x0E1411, 250, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, winX, winY, WINDOW_W, height, 14.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 28, fade), metrics.scale());

        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "Настройки  /  " + crumb,
            winX + 24.0f, winY + 26.0f, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));

        searchRect = new LinkenMenuLayout.Rect(winX + WINDOW_W - 334.0f, winY + 18.0f, 310.0f, 38.0f);
        drawSearch(context, metrics, mx, my, fade);

        scrollTarget = clampScroll(scrollTarget);
        scroll += (scrollTarget - scroll) * (1.0f - (float) Math.exp(-14.0f * seconds));
        if (Math.abs(scrollTarget - scroll) < 0.4f) {
            scroll = scrollTarget;
        }

        float top = listTop();
        float available = listHeight();
        float colW = (WINDOW_W - PAD * 2.0f - COL_GAP) * 0.5f;
        rowRects.clear();
        sliderRects.clear();
        rowOptions.clear();

        if (filtered.isEmpty()) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "Параметры не найдены",
                winX + WINDOW_W * 0.5f, top + available * 0.5f, 15.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
        } else {
            for (int i = 0; i < filtered.size(); i++) {
                int column = i % 2;
                int row = i / 2;
                float x = winX + PAD + column * (colW + COL_GAP);
                float y = top + PAD + row * (ROW_H + ROW_GAP) - scroll;
                if (y + ROW_H < top || y > top + available) {
                    continue;
                }
                LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(x, y, colW, ROW_H);
                LinkenSettingsOption option = filtered.get(i);
                rowRects.add(rect);
                rowOptions.add(option);
                float trackW = rect.width() - 28.0f;
                sliderRects.add(new LinkenMenuLayout.Rect(rect.x() + 14.0f,
                    rect.y() + rect.height() - 16.0f, trackW, 6.0f));
                drawRow(context, metrics, rect, option, i, mx, my, fade, seconds);
            }
        }

        float footerY = winY + height - FOOTER_H;
        context.fill(Math.round(winX + 1.0f), Math.round(footerY),
            Math.round(winX + WINDOW_W - 1.0f), Math.round(winY + height - 1.0f),
            LinkenMenuChrome.rgba(0x0E1411, 250, fade));

        doneRect = new LinkenMenuLayout.Rect(winX + WINDOW_W - PAD - 130.0f,
            winY + height - 54.0f, 130.0f, 40.0f);
        drawDoneButton(context, metrics, doneRect, mx, my, fade, seconds);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), "ESC  /  Назад",
            winX + PAD, winY + height - 40.0f, 9.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));

        WindowMaterial.corners(context, winX, winY, WINDOW_W, height, metrics.scale(), fade);
    }

    private void drawSearch(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my, float fade) {
        boolean hovered = searchRect.contains(mx, my);
        LinkenMenuChrome.roundedFill(context, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(), 8.0f,
            LinkenMenuChrome.rgba(searchFocused || hovered ? 0x1B241E : 0x151C17, 240, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(), 8.0f,
            searchFocused ? 1.3f : 1.0f,
            LinkenMenuChrome.rgba(searchFocused ? LinkenMenuChrome.ACCENT : 0xFFFFFF,
                searchFocused ? 170 : 24, fade), metrics.scale());
        String queryValue = query.value();
        String shown = queryValue.isBlank() ? "SEARCH OPTIONS..." : queryValue;
        LinkenMenuChrome.drawText(context, LinkenFonts.mono(), shown, searchRect.x() + 14.0f,
            searchRect.y() + 13.0f, 9.0f,
            LinkenMenuChrome.rgba(queryValue.isBlank() ? LinkenMenuChrome.MUTED3 : LinkenMenuChrome.TEXT2, fade));
    }

    private void drawRow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                         LinkenSettingsOption option, int index, float mx, float my, float fade, float seconds) {
        boolean hovered = rect.contains(mx, my);
        Spring spring = rowHover[index % rowHover.length];
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();

        int fill = LinkenMenuChrome.mix(0x111713, 0x18211B, hover);
        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            8.0f, LinkenMenuChrome.rgba(fill, 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            8.0f, 1.0f, LinkenMenuChrome.rgba(0xFFFFFF, 20 + Math.round(hover * 35), fade), metrics.scale());

        LinkenMenuChrome.drawText(context, LinkenFonts.body(), option.label(),
            rect.x() + 14.0f, rect.y() + 13.0f - hover * 1.5f, 10.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));

        switch (option.kind()) {
            case TOGGLE -> {
                float pillW = 42.0f, pillH = 22.0f;
                float px = rect.right() - 14.0f - pillW;
                float py = rect.y() + (rect.height() - pillH) * 0.5f - hover * 1.5f;
                boolean on = option.booleanValue();
                LinkenMenuChrome.roundedFill(context, px, py, pillW, pillH, 11.0f,
                    LinkenMenuChrome.rgba(on ? LinkenMenuChrome.ACCENT : 0x27312A, 245, fade), metrics.scale());
                LinkenMenuChrome.roundedFill(context, on ? px + pillW - 18.0f : px + 4.0f, py + 3.0f,
                    16.0f, 16.0f, 8.0f,
                    LinkenMenuChrome.rgba(on ? LinkenMenuChrome.INK : LinkenMenuChrome.MUTED2, 255, fade),
                    metrics.scale());
            }
            case SLIDER -> {
                LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), option.value(),
                    rect.right() - 14.0f, rect.y() + 11.0f - hover * 1.5f, 9.0f,
                    LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
                float tx = rect.x() + 14.0f;
                float ty = rect.y() + rect.height() - 14.0f - hover * 1.5f;
                float tw = rect.width() - 28.0f;
                LinkenMenuChrome.roundedFill(context, tx, ty, tw, 4.0f, 2.0f,
                    LinkenMenuChrome.rgba(0xFFFFFF, 24, fade), metrics.scale());
                float frac = (float) option.fraction();
                if (frac > 0.001f) {
                    LinkenMenuChrome.roundedFill(context, tx, ty, Math.max(4.0f, tw * frac), 4.0f, 2.0f,
                        LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade), metrics.scale());
                }
                LinkenMenuChrome.roundedFill(context, tx + tw * frac - 3.0f, ty - 3.0f, 6.0f, 10.0f, 3.0f,
                    LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade), metrics.scale());
            }
            case CYCLE, ACTION -> {
                String value = option.value();
                float width = Math.max(70.0f, Math.min(180.0f, LinkenMenuChrome.textWidth(
                    LinkenFonts.mono(), value, 9.0f) + 26.0f));
                float bx = rect.right() - 14.0f - width;
                float by = rect.y() + 13.0f - hover * 1.5f;
                LinkenMenuChrome.roundedFill(context, bx, by, width, 26.0f, 7.0f,
                    LinkenMenuChrome.rgba(0x1B241E, 230, fade), metrics.scale());
                LinkenMenuChrome.roundedOutline(context, bx, by, width, 26.0f, 7.0f, 1.0f,
                    LinkenMenuChrome.rgba(0xFFFFFF, 26, fade), metrics.scale());
                LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), value,
                    bx + width - 10.0f, by + 8.0f, 9.0f,
                    LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
                if (option.kind() == LinkenSettingsOption.Kind.ACTION) {
                    LinkenMenuChrome.drawText(context, LinkenFonts.mono(), ">", bx + 8.0f, by + 7.0f, 10.0f,
                        LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
                }
            }
            case DISPLAY -> LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), option.value(),
                rect.right() - 14.0f, rect.y() + 19.0f - hover * 1.5f, 9.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
        }
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
        if (searchRect != null && searchRect.contains(mx, my)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (doneRect != null && doneRect.contains(mx, my)) {
            close();
            return true;
        }
        for (int i = 0; i < rowRects.size(); i++) {
            LinkenMenuLayout.Rect rect = rowRects.get(i);
            if (!rect.contains(mx, my)) {
                continue;
            }
            LinkenSettingsOption option = rowOptions.get(i);
            switch (option.kind()) {
                case TOGGLE -> option.toggle();
                case CYCLE -> option.cycle();
                case ACTION -> option.run();
                case SLIDER -> {
                    dragging = i;
                    applySlider(i, mx);
                }
                case DISPLAY -> {
                }
            }
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging >= 0 && dragging < sliderRects.size()) {
            LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
            applySlider(dragging, (float) click.x() / metrics.scale());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    private void applySlider(int index, float mouseX) {
        if (index < 0 || index >= sliderRects.size() || index >= rowOptions.size()) {
            return;
        }
        LinkenMenuLayout.Rect track = sliderRects.get(index);
        double fraction = (mouseX - track.x()) / Math.max(1.0f, track.width());
        rowOptions.get(index).setFraction(fraction);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = -1;
        return super.mouseReleased(click);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused) {
            boolean handled = query.charTyped(input);
            if (handled) {
                refresh();
            }
            return handled;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused || !query.value().isBlank()) {
                searchFocused = false;
                query.clear();
                refresh();
            } else {
                close();
            }
            return true;
        }
        if (searchFocused && query.keyPressed(input)) {
            refresh();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            scrollTarget = clampScroll(scrollTarget + 54.0f);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            scrollTarget = clampScroll(scrollTarget - 54.0f);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) mouseX / metrics.scale();
        float my = (float) mouseY / metrics.scale();
        if (mx >= winX && mx <= winX + WINDOW_W
            && my >= listTop() && my <= listTop() + listHeight()) {
            scrollTarget = clampScroll(scrollTarget - (float) verticalAmount * 54.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
