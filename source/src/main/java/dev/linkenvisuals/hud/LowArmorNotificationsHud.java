package dev.linkenvisuals.hud;

import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.clickgui.link.LinkGuiTheme;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.util.ColorMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Compact durability warnings above the hotbar. Armour crossing the 50% or 25%
 * threshold pushes one message; messages live for 3.5 s and fade out.
 */
public final class LowArmorNotificationsHud extends HudElement {
    private static final long DISPLAY_TIME_MS = 3500L;
    private static final int MAX_ENTRIES = 4;
    private static final float PADDING = 7.0f;
    private static final float ROW_HEIGHT = 19.0f;
    private static final float GAP = 5.0f;
    private static final float FONT_SIZE = 8.4f;
    private static final float ICON_SIZE = 10.0f;
    private static final float ICON_GAP = 6.0f;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final int[] LAST_STAGES = new int[4];

    private static boolean lastRenderVisible;

    public LowArmorNotificationsHud() {
        super("lowarmornotificationshud", 0.5f, 0.86f);
    }

    public static boolean lastRenderVisible() {
        return lastRenderVisible;
    }

    public static void push(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        ENTRIES.removeIf(entry -> entry.message.equals(message));
        ENTRIES.add(new Entry(message));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(0);
        }
    }

    @Override
    public float width() {
        if (HudStyle.variant() == HudStyle.Variant.CARD) {
            return 112.0f;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String widest = previewMessage(client);
        for (Entry entry : ENTRIES) {
            if (entry.message.length() > widest.length()) {
                widest = entry.message;
            }
        }
        GlyphAtlas font = font();
        return Math.max(150.0f, Math.min(380.0f,
            font.width(widest, FONT_SIZE) + iconWidth() + ICON_GAP + PADDING * 2.0f));
    }

    @Override
    public float height() {
        int rows = rows();
        return rows <= 0 ? ROW_HEIGHT : rows * ROW_HEIGHT + (rows - 1) * GAP;
    }

    @Override
    public void render(DrawContext context, float x, float y) {
        lastRenderVisible = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        boolean editing = HudManager.isEditing() || client.currentScreen instanceof ChatScreen;
        if (!isEnabled() && !editing) {
            return;
        }
        if (isEnabled()) {
            scanArmor(client.player);
        }

        List<Entry> visibleEntries = new ArrayList<>(ENTRIES);
        if (editing && visibleEntries.isEmpty()) {
            visibleEntries.add(new Entry(previewMessage(client), true));
        }
        if (visibleEntries.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        float fontWidth = 0.0f;
        for (Entry entry : visibleEntries) {
            fontWidth = Math.max(fontWidth, font().width(entry.message, FONT_SIZE));
        }
        float width = Math.max(150.0f, Math.min(380.0f,
            fontWidth + iconWidth() + ICON_GAP + PADDING * 2.0f));
        float screenWidth = client.getWindow().getScaledWidth();
        float clampedX = Math.max(4.0f, Math.min(x, Math.max(4.0f, screenWidth - width - 4.0f)));

        GlyphAtlas font = font();
        GlyphAtlas iconFont = Fonts.titleFont();
        String icon = "!";
        int iconColor = ColorMath.withAlpha(0xFF5656, 255);
        float textX = clampedX + PADDING + iconWidth() + ICON_GAP;
        float textMax = width - (textX - clampedX) - PADDING;

        int index = 0;
        for (Iterator<Entry> iterator = visibleEntries.iterator(); iterator.hasNext(); ) {
            Entry entry = iterator.next();
            boolean active = entry.preview || now - entry.createdAt < DISPLAY_TIME_MS;
            float alpha = entry.preview ? 1.0f : entry.fade.value(active ? 1.0f : 0.0f, 200L);
            if (!active && alpha <= 0.02f) {
                if (!entry.preview) {
                    ENTRIES.remove(entry);
                }
                continue;
            }
            float rowY = y + index * (ROW_HEIGHT + GAP);
            float fa = Math.max(0.0f, Math.min(1.0f, alpha));
            int a = Math.round(255.0f * fa);
            HudSurface.panel(context, id() + "_" + index, clampedX, rowY, width, ROW_HEIGHT, fa);
            MsdfText.drawOn(context, iconFont, icon, clampedX + PADDING,
                rowY + ROW_HEIGHT * 0.5f, ICON_SIZE,
                ColorMath.withAlpha(0xFF5656, a));
            MsdfText.drawOn(context, font, fitText(font, entry.message, FONT_SIZE, textMax),
                textX, rowY + ROW_HEIGHT * 0.5f, FONT_SIZE, textColor(a));
            lastRenderVisible = true;
            index++;
        }
    }

    private static void scanArmor(PlayerEntity player) {
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = ArmorHud.armorStack(player, slot);
            if (stack == null || stack.isEmpty()) {
                LAST_STAGES[slot] = 0;
                continue;
            }
            int percent = ArmorHud.durabilityPercent(stack);
            int stage = stageFor(percent);
            if (stage > LAST_STAGES[slot]) {
                String key = stage >= 2
                    ? "linkenvisuals.hud.low_armor_critical"
                    : "linkenvisuals.hud.low_armor";
                push(I18n.get(key, stack.getName().getString(), percent));
            }
            LAST_STAGES[slot] = stage;
        }
    }

    static int stageFor(int percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent < 25) {
            return 2;
        }
        return percent < 50 ? 1 : 0;
    }

    private static String previewMessage(MinecraftClient client) {
        boolean editing = HudManager.isEditing()
            || (client != null && client.currentScreen instanceof ChatScreen);
        if (!editing) {
            return "";
        }
        return I18n.get("linkenvisuals.hud.low_armor_preview");
    }

    private static int rows() {
        if (!ENTRIES.isEmpty()) {
            return Math.min(MAX_ENTRIES, ENTRIES.size());
        }
        return previewMessage(MinecraftClient.getInstance()).isEmpty() ? 0 : 1;
    }

    private static GlyphAtlas font() {
        return Fonts.textFont();
    }

    private static float iconWidth() {
        return Fonts.titleFont().width("!", ICON_SIZE);
    }

    private static String fitText(GlyphAtlas font, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty() || font.width(text, size) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        float ellipsisWidth = font.width(ellipsis, size);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            builder.append(text.charAt(i));
            if (font.width(builder.toString(), size) + ellipsisWidth > maxWidth) {
                builder.deleteCharAt(builder.length() - 1);
                break;
            }
        }
        return builder.append(ellipsis).toString();
    }

    private static int textColor(int alpha) {
        return ColorMath.withAlpha(LinkGuiTheme.isLight() ? 0x1C1F26 : 0xFFFFFF, alpha);
    }

    private boolean isEnabled() {
        return ModuleRegistry.lowArmorNotifications() == null
            || ModuleRegistry.lowArmorNotifications().value();
    }

    private static final class Entry {
        private final String message;
        private final boolean preview;
        private final long createdAt = System.currentTimeMillis();
        private final HudTween fade = new HudTween(true);

        private Entry(String message) {
            this(message, false);
        }

        private Entry(String message, boolean preview) {
            this.message = message;
            this.preview = preview;
        }
    }
}
