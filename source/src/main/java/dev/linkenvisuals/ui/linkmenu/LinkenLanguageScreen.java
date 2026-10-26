package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.resource.language.LanguageDefinition;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** LINKEN language list: search, active marker and a single Apply action. */
public final class LinkenLanguageScreen extends Screen {
    private static final float WINDOW_X = 510.0f;
    private static final float WINDOW_Y = 238.0f;
    private static final float WINDOW_W = 900.0f;
    private static final float TOPBAR_H = 70.0f;
    private static final float FOOTER_H = 64.0f;
    private static final float PAD = 20.0f;
    private static final float ROW_H = 52.0f;
    private static final float GAP = 6.0f;

    private record Entry(String code, LanguageDefinition definition, String label, String region) {
    }

    private final Screen parent;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private final List<LinkenMenuLayout.Rect> rowRects = new ArrayList<>();
    private final List<Entry> rowEntries = new ArrayList<>();
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring backSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring applySpring = new Spring(0.0, 900.0, 50.0);
    private final Spring doneSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring[] rowHover = new Spring[96];

    private final dev.linkenvisuals.util.input.TextInput query =
        new dev.linkenvisuals.util.input.TextInput().maxLength(40);
    private boolean searchFocused;
    private String selected;
    private float scroll;
    private float scrollTarget;
    private long lastFrame = System.nanoTime();

    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect searchRect;
    private LinkenMenuLayout.Rect applyRect;
    private LinkenMenuLayout.Rect doneRect;

    public LinkenLanguageScreen(Screen parent) {
        super(Text.literal("Language"));
        this.parent = parent;
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0, 900.0, 50.0);
        }
    }

    @Override
    protected void init() {
        all.clear();
        if (client != null && client.getLanguageManager() != null) {
            for (Map.Entry<String, LanguageDefinition> entry
                : client.getLanguageManager().getAllLanguages().entrySet()) {
                LanguageDefinition definition = entry.getValue();
                all.add(new Entry(entry.getKey(), definition,
                    definition.getDisplayText().getString(), definition.region()));
            }
        }
        selected = client != null && client.getLanguageManager() != null
            ? client.getLanguageManager().getLanguage() : "en_us";
        refresh();
        ensureSelectedVisible();
        openSpring.reset(0.0);
        lastFrame = System.nanoTime();
    }

    private void refresh() {
        visible.clear();
        String needle = query.value().trim().toLowerCase(Locale.ROOT);
        for (Entry entry : all) {
            if (needle.isEmpty()
                || entry.label().toLowerCase(Locale.ROOT).contains(needle)
                || entry.code().toLowerCase(Locale.ROOT).contains(needle)) {
                visible.add(entry);
            }
        }
        if (selected == null && !visible.isEmpty()) {
            selected = visible.get(0).code();
        }
        scrollTarget = clampScroll(scrollTarget);
        scroll = clampScroll(scroll);
    }

    private float windowHeight() {
        float content = PAD * 2.0f + visible.size() * ROW_H + Math.max(0, visible.size() - 1) * GAP;
        return Math.min(TOPBAR_H + Math.max(content, ROW_H) + FOOTER_H, 1006.0f - WINDOW_Y);
    }

    private float listTop() {
        return WINDOW_Y + TOPBAR_H;
    }

    private float listHeight() {
        return Math.max(0.0f, windowHeight() - TOPBAR_H - FOOTER_H);
    }

    private float maxScroll() {
        float content = PAD * 2.0f + visible.size() * ROW_H + Math.max(0, visible.size() - 1) * GAP;
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
        float mx = mouseX / metrics.scale();
        float my = mouseY / metrics.scale();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        LinkenMenuChrome.drawBackground(context, metrics, 0.0f, 0.0f, 1.0f);
        GlowPipeline.radial(context, metrics.width() * 0.52f, 470.0f, 720.0f,
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
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "LANGUAGE",
            LinkenMenuLayout.MARGIN, 162.0f, 40.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            (visible.size() == 1 ? "1 LANGUAGE" : visible.size() + " LANGUAGES") + "  /  CLICK TO SELECT",
            LinkenMenuLayout.MARGIN, 218.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        drawWindow(context, metrics, mx, my, fade, seconds);
        context.getMatrices().popMatrix();
    }

    private void drawWindow(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my,
                            float fade, float seconds) {
        float height = windowHeight();
        LinkenMenuChrome.roundedFill(context, WINDOW_X, WINDOW_Y, WINDOW_W, height, 14.0f,
            LinkenMenuChrome.rgba(0x0E1411, 250, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, WINDOW_X, WINDOW_Y, WINDOW_W, height, 14.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 28, fade), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "SETTINGS  /  LANGUAGE",
            WINDOW_X + 24.0f, WINDOW_Y + 26.0f, 14.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));
        searchRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - 334.0f, WINDOW_Y + 18.0f, 310.0f, 38.0f);
        drawSearch(context, metrics, mx, my, fade);

        scrollTarget = clampScroll(scrollTarget);
        scroll += (scrollTarget - scroll) * (1.0f - (float) Math.exp(-14.0f * seconds));
        if (Math.abs(scrollTarget - scroll) < 0.4f) {
            scroll = scrollTarget;
        }

        float top = listTop();
        float view = listHeight();
        rowRects.clear();
        rowEntries.clear();
        for (int i = 0; i < visible.size(); i++) {
            float y = top + PAD + i * (ROW_H + GAP) - scroll;
            if (y + ROW_H < top || y > top + view) {
                continue;
            }
            LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(WINDOW_X + PAD, y,
                WINDOW_W - PAD * 2.0f, ROW_H);
            rowRects.add(rect);
            rowEntries.add(visible.get(i));
            drawRow(context, metrics, rect, visible.get(i), i, mx, my, fade, seconds);
        }
        if (visible.isEmpty()) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "NO LANGUAGES MATCH",
                WINDOW_X + WINDOW_W * 0.5f, top + view * 0.5f, 15.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
        }

        float footerY = WINDOW_Y + height - FOOTER_H;
        context.fill(Math.round(WINDOW_X + 1.0f), Math.round(footerY),
            Math.round(WINDOW_X + WINDOW_W - 1.0f), Math.round(WINDOW_Y + height - 1.0f),
            LinkenMenuChrome.rgba(0x0E1411, 250, fade));

        doneRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - PAD - 130.0f,
            WINDOW_Y + height - 54.0f, 130.0f, 40.0f);
        applyRect = new LinkenMenuLayout.Rect(doneRect.x() - 12.0f - 150.0f,
            doneRect.y(), 150.0f, 40.0f);
        drawSecondaryButton(context, metrics, applyRect, "APPLY", mx, my, fade, applySpring, seconds);
        drawDoneButton(context, metrics, doneRect, mx, my, fade, seconds);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), "ESC  /  BACK",
            WINDOW_X + PAD, WINDOW_Y + height - 40.0f, 9.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));

        WindowMaterial.corners(context, WINDOW_X, WINDOW_Y, WINDOW_W, height, metrics.scale(), fade);
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
        String shown = queryValue.isBlank() ? "SEARCH LANGUAGES..." : queryValue;
        LinkenMenuChrome.drawText(context, LinkenFonts.mono(), shown, searchRect.x() + 14.0f,
            searchRect.y() + 13.0f, 9.0f,
            LinkenMenuChrome.rgba(queryValue.isBlank() ? LinkenMenuChrome.MUTED3 : LinkenMenuChrome.TEXT2, fade));
    }

    private void drawRow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                         Entry entry, int index, float mx, float my, float fade, float seconds) {
        boolean hovered = rect.contains(mx, my);
        Spring spring = rowHover[index % rowHover.length];
        spring.step(hovered || entry.code().equals(selected) ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        boolean active = entry.code().equals(selected);
        boolean current = client != null && client.getLanguageManager() != null
            && entry.code().equals(client.getLanguageManager().getLanguage());

        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            8.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x111713, 0x18211B, hover), 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            8.0f, active ? 1.3f : 1.0f,
            LinkenMenuChrome.rgba(active ? LinkenMenuChrome.ACCENT : 0xFFFFFF,
                active ? 160 : 20 + Math.round(hover * 35), fade), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), entry.label(),
            rect.x() + 14.0f, rect.y() + 11.0f - hover * 1.5f, 12.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.mono(), entry.code(),
            rect.x() + 14.0f, rect.y() + 30.0f - hover * 1.5f, 8.0f, 1.2f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));
        if (current) {
            float bw = 74.0f;
            float bx = rect.right() - 14.0f - bw;
            LinkenMenuChrome.roundedFill(context, bx, rect.y() + 14.0f - hover * 1.5f, bw, 24.0f, 12.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 28, fade), metrics.scale());
            LinkenMenuChrome.drawCentered(context, LinkenFonts.mono(), "ACTIVE",
                bx + bw * 0.5f, rect.y() + 21.0f - hover * 1.5f, 8.5f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
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

    private void drawSecondaryButton(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                                     String label, float mx, float my, float fade, Spring spring, float seconds) {
        boolean hovered = rect.contains(mx, my);
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        LinkenMenuChrome.drawSecondary(context, rect.x(), rect.y() - hover, rect.width(), rect.height(),
            8.0f, hover, false, fade, metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), label,
            rect.x() + rect.width() * 0.5f, rect.y() + 13.0f - hover, 10.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
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
        if (applyRect != null && applyRect.contains(mx, my)) {
            apply();
            return true;
        }
        for (int i = 0; i < rowRects.size(); i++) {
            if (rowRects.get(i).contains(mx, my)) {
                selected = rowEntries.get(i).code();
                if (doubleClick) {
                    apply();
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    private void apply() {
        if (client == null || client.getLanguageManager() == null || selected == null) {
            return;
        }
        client.getLanguageManager().setLanguage(selected);
        client.options.language = selected;
        client.options.write();
        client.reloadResources();
        close();
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
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            apply();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN && !visible.isEmpty()) {
            int index = indexOfSelected();
            selected = visible.get(Math.min(visible.size() - 1, index + 1)).code();
            ensureSelectedVisible();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP && !visible.isEmpty()) {
            int index = indexOfSelected();
            selected = visible.get(Math.max(0, index - 1)).code();
            ensureSelectedVisible();
            return true;
        }
        return super.keyPressed(input);
    }

    private int indexOfSelected() {
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).code().equals(selected)) {
                return i;
            }
        }
        return 0;
    }

    private void ensureSelectedVisible() {
        int index = indexOfSelected();
        float top = index * (ROW_H + GAP);
        if (top < scrollTarget) {
            scrollTarget = top;
        } else if (top + ROW_H > scrollTarget + listHeight() - PAD * 2.0f) {
            scrollTarget = top + ROW_H - listHeight() + PAD * 2.0f;
        }
        scrollTarget = clampScroll(scrollTarget);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) mouseX / metrics.scale();
        float my = (float) mouseY / metrics.scale();
        if (mx >= WINDOW_X && mx <= WINDOW_X + WINDOW_W
            && my >= listTop() && my <= listTop() + listHeight()) {
            scrollTarget = clampScroll(scrollTarget - (float) verticalAmount * 56.0f);
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
