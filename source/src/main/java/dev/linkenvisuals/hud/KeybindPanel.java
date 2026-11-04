package dev.linkenvisuals.hud;

import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.clickgui.ModuleInfo;
import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.clickgui.link.LinkGuiTheme;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeybindPanel extends HudElement {
    private static final float PADDING = 8.0f;
    private static final float HEADER_H = 18.0f;
    private static final float ROW_H = 14.0f;
    private static final float ROW_PAD = 4.0f;
    private static final float MIN_WIDTH = 90.0f;

    private GlyphAtlas text;
    private GlyphAtlas bold;
    private final List<Entry> entries = new ArrayList<>();
    private float cachedWidth = MIN_WIDTH;
    private float cachedHeight;
    private int cachedRevision = -1;

    public KeybindPanel() {
        super("keybinds", 0.9f, 0.16f);
    }

    private void ensureLoaded() {
        if (text == null) {
            text = Fonts.textFont();
        }
        if (bold == null) {
            bold = Fonts.titleFont();
        }
    }

    private void refresh() {
        int revision = ModuleInfo.revision();
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean editing = mc != null && mc.currentScreen instanceof ChatScreen;

        if (revision == cachedRevision && !entries.isEmpty()) {
            return;
        }
        cachedRevision = revision;

        entries.clear();
        for (ModuleInfo module : ModuleRegistry.all()) {
            if (module.hasKeybind() && !module.binding() && module.enabled()) {
                entries.add(new Entry(module.name(), module.keybind().toUpperCase(Locale.ROOT)));
            }
        }

        if (entries.isEmpty() && editing) {
            entries.add(new Entry("Sprint", "R"));
            entries.add(new Entry("Fullbright", "F"));
            entries.add(new Entry("DiscordRPC", "G"));
        }

        if (entries.isEmpty()) {
            cachedWidth = 0.0f;
            cachedHeight = 0.0f;
            return;
        }

        ensureLoaded();
        String title = I18n.get("elements.settings.elements.keybindshud");
        if (title.equals("elements.settings.elements.keybindshud")) {
            title = "Клавиши";
        }
        float widest = MsdfText.width(bold, title, 7.0f) + 24.0f;
        for (Entry entry : entries) {
            float keyW = MsdfText.width(bold, entry.key, 5.8f);
            float pillW = Math.max(12.0f, keyW + 8.0f);
            float rowWidth = MsdfText.width(bold, entry.name, 6.6f) + 12.0f + pillW;
            widest = Math.max(widest, rowWidth);
        }
        cachedWidth = Math.max(MIN_WIDTH, widest + PADDING * 2.0f);
        cachedHeight = HEADER_H + ROW_PAD + entries.size() * ROW_H + ROW_PAD;
    }

    @Override
    public float width() {
        refresh();
        return cachedWidth;
    }

    @Override
    public float height() {
        return cachedHeight;
    }

    @Override
    public void render(DrawContext context, float x, float y) {
        if (entries.isEmpty()) {
            return;
        }
        HudSurface.panel(context, "keybinds", x, y, cachedWidth, cachedHeight, 1.0f);

        String title = I18n.get("elements.settings.elements.keybindshud");
        if (title.equals("elements.settings.elements.keybindshud")) {
            title = "Клавиши";
        }

        // Title on the left
        float centerY = y + HEADER_H * 0.5f;
        MsdfText.drawOn(context, bold, title, x + PADDING, centerY, 7.0f, ColorMath.withAlpha(0xFFFFFF, 240));

        // Keyboard icon on the right
        GlyphAtlas iconAtlas = LinkenFonts.iconsNew();
        if (iconAtlas != null) {
            float iconX = x + cachedWidth - PADDING - 10.0f;
            MsdfText.drawOn(context, iconAtlas, "S", iconX, centerY, 8.0f, ColorMath.withAlpha(0xFFFFFF, 200));
        }

        // Horizontal divider line
        float dividerY = y + HEADER_H - 1.0f;
        int accent = LinkGuiTheme.ACCENT;
        context.fill(Math.round(x + PADDING), Math.round(dividerY),
            Math.round(x + cachedWidth - PADDING), Math.round(dividerY + 1.0f),
            ColorMath.withAlpha(accent, 90));

        // Keybind rows
        float rowY = y + HEADER_H + ROW_PAD;
        for (Entry entry : entries) {
            float rowCenterY = rowY + ROW_H * 0.5f;

            // Module name on the left
            MsdfText.drawOn(context, bold, entry.name, x + PADDING, rowCenterY, 6.6f,
                ColorMath.withAlpha(0xFFFFFF, 230));

            // Key pill on the right
            float keyW = MsdfText.width(bold, entry.key, 5.8f);
            float pillW = Math.max(12.0f, keyW + 8.0f);
            float pillH = 11.0f;
            float pillX = x + cachedWidth - PADDING - pillW;
            float pillY = rowCenterY - pillH * 0.5f;

            RoundedRectPipeline.draw(context, pillX, pillY, pillW, pillH, 3.5f,
                ColorMath.withAlpha(accent, 42));
            RoundedRectPipeline.drawBorder(context, pillX, pillY, pillW, pillH, 3.5f, 0.0f, 1.0f, 1.0f,
                ColorMath.withAlpha(accent, 110));
            MsdfText.drawCentered(context, bold, entry.key, pillX + pillW * 0.5f, rowCenterY, 5.8f,
                ColorMath.withAlpha(accent, 255));

            rowY += ROW_H;
        }
    }

    private record Entry(String name, String key) {
    }
}
