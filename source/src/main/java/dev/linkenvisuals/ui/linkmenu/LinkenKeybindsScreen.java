package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LINKEN keybind list. Rebinding uses the vanilla KeyBinding API and writes
 * options.txt immediately; no vanilla list widget is involved.
 */
public final class LinkenKeybindsScreen extends Screen {
    private static final float WINDOW_X = 430.0f;
    private static final float WINDOW_Y = 238.0f;
    private static final float WINDOW_W = 1060.0f;
    private static final float TOPBAR_H = 70.0f;
    private static final float FOOTER_H = 64.0f;
    private static final float PAD = 20.0f;
    private static final float ROW_H = 50.0f;
    private static final float HEADER_H = 30.0f;
    private static final float GAP = 6.0f;

    private static final class Entry {
        final String header;
        final KeyBinding binding;

        Entry(String header, KeyBinding binding) {
            this.header = header;
            this.binding = binding;
        }

        boolean isHeader() {
            return binding == null;
        }

        float height() {
            return isHeader() ? HEADER_H : ROW_H;
        }
    }

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final List<KeyBinding> matches = new ArrayList<>();
    private final List<LinkenMenuLayout.Rect> rowRects = new ArrayList<>();
    private final List<Entry> rowEntries = new ArrayList<>();
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring backSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring resetSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring doneSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring[] rowHover = new Spring[96];

    private final dev.linkenvisuals.util.input.TextInput query =
        new dev.linkenvisuals.util.input.TextInput().maxLength(40);
    private boolean searchFocused;
    private KeyBinding listening;
    private float scroll;
    private float scrollTarget;
    private long lastFrame = System.nanoTime();

    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect searchRect;
    private LinkenMenuLayout.Rect resetRect;
    private LinkenMenuLayout.Rect doneRect;

    public LinkenKeybindsScreen(Screen parent) {
        super(Text.literal("Keybinds"));
        this.parent = parent;
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
        entries.clear();
        matches.clear();
        if (client == null || client.options == null) {
            return;
        }
        String needle = query.value().trim().toLowerCase(Locale.ROOT);
        Map<KeyBinding.Category, List<KeyBinding>> groups = new LinkedHashMap<>();
        for (KeyBinding binding : client.options.allKeys) {
            if (binding == null) {
                continue;
            }
            String name = displayName(binding);
            String key = binding.getBoundKeyLocalizedText().getString();
            String category = binding.getCategory().getLabel().getString();
            if (!needle.isEmpty()
                && !name.toLowerCase(Locale.ROOT).contains(needle)
                && !key.toLowerCase(Locale.ROOT).contains(needle)
                && !category.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            groups.computeIfAbsent(binding.getCategory(), ignored -> new ArrayList<>()).add(binding);
            matches.add(binding);
        }
        for (Map.Entry<KeyBinding.Category, List<KeyBinding>> group : groups.entrySet()) {
            entries.add(new Entry(group.getKey().getLabel().getString().toUpperCase(Locale.ROOT), null));
            for (KeyBinding binding : group.getValue()) {
                entries.add(new Entry(null, binding));
            }
        }
        scrollTarget = clampScroll(scrollTarget);
        scroll = clampScroll(scroll);
    }

    private String displayName(KeyBinding binding) {
        return Text.translatable(binding.getId()).getString();
    }

    private float windowHeight() {
        float content = PAD * 2.0f;
        for (int i = 0; i < entries.size(); i++) {
            content += entries.get(i).height();
            if (i + 1 < entries.size()) {
                content += GAP;
            }
        }
        return Math.min(TOPBAR_H + Math.max(content, ROW_H) + FOOTER_H, 1006.0f - WINDOW_Y);
    }

    private float listHeight() {
        return Math.max(0.0f, windowHeight() - TOPBAR_H - FOOTER_H);
    }

    private float maxScroll() {
        float content = PAD * 2.0f;
        for (int i = 0; i < entries.size(); i++) {
            content += entries.get(i).height();
            if (i + 1 < entries.size()) {
                content += GAP;
            }
        }
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
        GlowPipeline.radial(context, metrics.width() * 0.54f, 470.0f, 720.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 10, fade));

        context.getMatrices().translate(0.0f, (1.0f - fade) * 18.0f);
        LinkenMenuChrome.drawWordmark(context, fade);
        LinkenMenuChrome.drawClock(context, metrics, fade);

        backRect = new LinkenMenuLayout.Rect(metrics.width() - LinkenMenuLayout.MARGIN - 132.0f,
            104.0f, 132.0f, 44.0f);
        drawChip(context, metrics, backRect, "BACK", LinkenMenuChrome.ICON_POWER, mx, my, fade, backSpring, seconds);

        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), "CONTROLS",
            LinkenMenuLayout.MARGIN, 146.0f, 8.5f, 1.6f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "KEYBINDS",
            LinkenMenuLayout.MARGIN, 162.0f, 40.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        String count = matches.size() == 1 ? "1 BIND" : matches.size() + " BINDS";
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), count,
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

        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "SETTINGS  /  CONTROLS  /  KEYBINDS",
            WINDOW_X + 24.0f, WINDOW_Y + 26.0f, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));
        searchRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - 334.0f, WINDOW_Y + 18.0f, 310.0f, 38.0f);
        drawSearch(context, metrics, mx, my, fade);

        scrollTarget = clampScroll(scrollTarget);
        scroll += (scrollTarget - scroll) * (1.0f - (float) Math.exp(-14.0f * seconds));
        if (Math.abs(scrollTarget - scroll) < 0.4f) {
            scroll = scrollTarget;
        }

        float top = WINDOW_Y + TOPBAR_H;
        float view = listHeight();
        rowRects.clear();
        rowEntries.clear();
        float y = top + PAD - scroll;
        for (Entry entry : entries) {
            LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(WINDOW_X + PAD, y,
                WINDOW_W - PAD * 2.0f, entry.height());
            if (y + entry.height() >= top && y <= top + view) {
                rowRects.add(rect);
                rowEntries.add(entry);
                if (entry.isHeader()) {
                    drawHeader(context, rect, entry.header, fade);
                } else {
                    drawBinding(context, metrics, rect, entry.binding, rowEntries.size() - 1,
                        mx, my, fade, seconds);
                }
            }
            y += entry.height() + GAP;
        }

        if (entries.isEmpty()) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "NO BINDS MATCH THIS SEARCH",
                WINDOW_X + WINDOW_W * 0.5f, top + view * 0.5f, 15.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
        }

        float footerY = WINDOW_Y + height - FOOTER_H;
        context.fill(Math.round(WINDOW_X + 1.0f), Math.round(footerY),
            Math.round(WINDOW_X + WINDOW_W - 1.0f), Math.round(WINDOW_Y + height - 1.0f),
            LinkenMenuChrome.rgba(0x0E1411, 250, fade));

        resetRect = new LinkenMenuLayout.Rect(WINDOW_X + PAD, WINDOW_Y + height - 54.0f, 150.0f, 40.0f);
        doneRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - PAD - 130.0f,
            WINDOW_Y + height - 54.0f, 130.0f, 40.0f);
        drawSecondaryButton(context, metrics, resetRect, "RESET ALL", mx, my, fade, resetSpring, seconds);
        drawDoneButton(context, metrics, doneRect, mx, my, fade, seconds);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            listening != null ? "PRESS A KEY  /  ESC CANCELS" : "RIGHT CLICK  /  RESET BIND",
            WINDOW_X + PAD + 166.0f, WINDOW_Y + height - 40.0f, 9.0f,
            LinkenMenuChrome.rgba(listening != null ? LinkenMenuChrome.ACCENT : LinkenMenuChrome.MUTED3, fade));

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
        String shown = queryValue.isBlank() ? "SEARCH BINDS..." : queryValue;
        LinkenMenuChrome.drawText(context, LinkenFonts.mono(), shown, searchRect.x() + 14.0f,
            searchRect.y() + 13.0f, 9.0f,
            LinkenMenuChrome.rgba(queryValue.isBlank() ? LinkenMenuChrome.MUTED3 : LinkenMenuChrome.TEXT2, fade));
    }

    private void drawHeader(DrawContext context, LinkenMenuLayout.Rect rect, String label, float fade) {
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), label,
            rect.x() + 4.0f, rect.y() + 11.0f, 8.5f, 1.6f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        context.fill(Math.round(rect.x() + 4.0f), Math.round(rect.y() + rect.height() - 2.0f),
            Math.round(rect.right()), Math.round(rect.y() + rect.height() - 1.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 16, fade));
    }

    private void drawBinding(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                             KeyBinding binding, int index, float mx, float my, float fade, float seconds) {
        boolean hovered = rect.contains(mx, my);
        Spring spring = rowHover[index % rowHover.length];
        spring.step(hovered || binding == listening ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        boolean active = binding == listening;
        boolean conflict = hasConflict(binding);

        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            8.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x111713, 0x18211B, hover), 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            8.0f, active ? 1.3f : 1.0f,
            LinkenMenuChrome.rgba(active ? LinkenMenuChrome.ACCENT : (conflict ? LinkenMenuChrome.DANGER : 0xFFFFFF),
                active ? 170 : 20 + Math.round(hover * 35), fade), metrics.scale());

        LinkenMenuChrome.drawText(context, LinkenFonts.body(), displayName(binding),
            rect.x() + 14.0f, rect.y() + 11.0f - hover * 1.5f, 10.5f,
            LinkenMenuChrome.rgba(active ? LinkenMenuChrome.TEXT : LinkenMenuChrome.TEXT2, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.mono(), binding.getCategory().getLabel().getString(),
            rect.x() + 14.0f, rect.y() + 30.0f - hover * 1.5f, 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));

        String key = active ? "PRESS A KEY..." : binding.getBoundKeyLocalizedText().getString();
        float keyW = Math.max(100.0f, Math.min(230.0f,
            LinkenMenuChrome.textWidth(LinkenFonts.mono(), key, 9.0f) + 28.0f));
        float kx = rect.right() - 14.0f - keyW;
        float ky = rect.y() + (rect.height() - 26.0f) * 0.5f - hover * 1.5f;
        LinkenMenuChrome.roundedFill(context, kx, ky, keyW, 26.0f, 7.0f,
            LinkenMenuChrome.rgba(active ? 0x213028 : 0x1B241E, 235, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, kx, ky, keyW, 26.0f, 7.0f, 1.0f,
            LinkenMenuChrome.rgba(active ? LinkenMenuChrome.ACCENT : 0xFFFFFF,
                active ? 150 : 24, fade), metrics.scale());
        LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), key, kx + keyW - 10.0f,
            ky + 8.0f, 9.0f,
            LinkenMenuChrome.rgba(active ? LinkenMenuChrome.ACCENT
                : (conflict ? LinkenMenuChrome.DANGER : LinkenMenuChrome.TEXT3), fade));
        if (conflict && !active) {
            LinkenMenuChrome.drawText(context, LinkenFonts.mono(), "CONFLICT",
                kx - 72.0f, ky + 8.0f, 8.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.DANGER, fade));
        }
    }

    private boolean hasConflict(KeyBinding binding) {
        if (client == null || client.options == null || binding.isUnbound()) {
            return false;
        }
        String key = binding.getBoundKeyTranslationKey();
        if ("key.keyboard.unknown".equals(key) || "key.mouse.unknown".equals(key)) {
            return false;
        }
        for (KeyBinding other : client.options.allKeys) {
            if (other != null && other != binding && key.equals(other.getBoundKeyTranslationKey())) {
                return true;
            }
        }
        return false;
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

        if (listening != null) {
            listening = null;
            return true;
        }
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
        if (resetRect != null && resetRect.contains(mx, my)) {
            resetAll();
            return true;
        }
        for (int i = 0; i < rowRects.size(); i++) {
            if (!rowRects.get(i).contains(mx, my)) {
                continue;
            }
            Entry entry = rowEntries.get(i);
            if (entry.isHeader()) {
                return true;
            }
            if (click.button() == 1) {
                entry.binding.setBoundKey(entry.binding.getDefaultKey());
                if (client != null && client.options != null) {
                    KeyBinding.updateKeysByCode();
                    client.options.write();
                }
                refresh();
                return true;
            }
            listening = entry.binding;
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = null;
                return true;
            }
            listening.setBoundKey(InputUtil.fromKeyCode(input));
            listening = null;
            if (client != null && client.options != null) {
                KeyBinding.updateKeysByCode();
                client.options.write();
            }
            refresh();
            return true;
        }
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
            scrollTarget = clampScroll(scrollTarget + 56.0f);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            scrollTarget = clampScroll(scrollTarget - 56.0f);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused && listening == null) {
            boolean handled = query.charTyped(input);
            if (handled) {
                refresh();
            }
            return handled;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) mouseX / metrics.scale();
        float my = (float) mouseY / metrics.scale();
        if (mx >= WINDOW_X && mx <= WINDOW_X + WINDOW_W
            && my >= WINDOW_Y + TOPBAR_H && my <= WINDOW_Y + windowHeight() - FOOTER_H) {
            scrollTarget = clampScroll(scrollTarget - (float) verticalAmount * 56.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void resetAll() {
        if (client == null || client.options == null) {
            return;
        }
        for (KeyBinding binding : client.options.allKeys) {
            if (binding != null) {
                binding.setBoundKey(binding.getDefaultKey());
            }
        }
        KeyBinding.updateKeysByCode();
        client.options.write();
        refresh();
    }

    @Override
    public void close() {
        if (client != null && client.options != null) {
            client.options.write();
        }
        if (client != null) {
            Screen target = parent;
            if (target == null || target == this) {
                target = client.world != null ? new LinkenPauseScreen() : new LinkenMainMenuScreen();
            }
            client.setScreen(target);
        }
    }
}
