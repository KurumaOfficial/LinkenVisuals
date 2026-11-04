package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer.Pack;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LINKEN resource-pack manager. The vanilla pack screen is replaced by two
 * LINKEN columns (available / selected), with search, enable/disable and
 * explicit priority arrows. DONE and ESC apply the selection, persist it and
 * reload the resource manager through the vanilla GameOptions path.
 */
public final class LinkenResourcePacksScreen extends Screen {
    private static final float WINDOW_X = 430.0f;
    private static final float WINDOW_Y = 238.0f;
    private static final float WINDOW_W = 1060.0f;
    private static final float WINDOW_H = 740.0f;
    private static final float PAD = 20.0f;
    private static final float TOPBAR_H = 70.0f;
    private static final float FOOTER_H = 64.0f;
    private static final float COL_GAP = 14.0f;
    private static final float COL_W = (WINDOW_W - PAD * 3.0f - COL_GAP) * 0.5f;
    private static final float LIST_TOP = WINDOW_Y + TOPBAR_H + 40.0f;
    private static final float LIST_BOTTOM = WINDOW_Y + WINDOW_H - FOOTER_H - 8.0f;
    private static final float ROW_H = 64.0f;
    private static final float ROW_GAP = 8.0f;

    private record Row(Pack pack, LinkenMenuLayout.Rect rect,
                       LinkenMenuLayout.Rect up, LinkenMenuLayout.Rect down) {
    }

    private final Screen parent;
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring backSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring openFolderSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring doneSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring[] rowHover = new Spring[128];

    private final List<Pack> available = new ArrayList<>();
    private final List<Pack> selected = new ArrayList<>();
    private final List<Row> availableRows = new ArrayList<>();
    private final List<Row> selectedRows = new ArrayList<>();

    private ResourcePackOrganizer organizer;
    private Pack pendingIncompatible;
    private final dev.linkenvisuals.util.input.TextInput query =
        new dev.linkenvisuals.util.input.TextInput().maxLength(40);
    private boolean searchFocused;
    private float scrollAvailable;
    private float scrollTargetAvailable;
    private float scrollSelected;
    private float scrollTargetSelected;
    private long lastFrame = System.nanoTime();

    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect searchRect;
    private LinkenMenuLayout.Rect openFolderRect;
    private LinkenMenuLayout.Rect doneRect;
    private LinkenMenuLayout.Rect availableColumnRect;
    private LinkenMenuLayout.Rect selectedColumnRect;
    private LinkenMenuLayout.Rect modalCancelRect;
    private LinkenMenuLayout.Rect modalConfirmRect;

    public LinkenResourcePacksScreen(Screen parent) {
        super(Text.literal("Resource Packs"));
        this.parent = parent;
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0, 900.0, 50.0);
        }
    }

    @Override
    protected void init() {
        if (client != null) {
            organizer = new ResourcePackOrganizer(
                pack -> refreshLists(),
                profile -> (Identifier) null,
                client.getResourcePackManager(),
                manager -> {
                    client.options.refreshResourcePacks(manager);
                    client.setScreen(parent);
                });
            organizer.refresh();
        }
        refreshLists();
        pendingIncompatible = null;
        openSpring.reset(0.0);
        lastFrame = System.nanoTime();
    }

    private void refreshLists() {
        available.clear();
        selected.clear();
        if (organizer == null) {
            return;
        }
        String needle = query.value().trim().toLowerCase(Locale.ROOT);
        for (Pack pack : organizer.getDisabledPacks().toList()) {
            if (matches(pack)) {
                available.add(pack);
            }
        }
        for (Pack pack : organizer.getEnabledPacks().toList()) {
            if (matches(pack)) {
                selected.add(pack);
            }
        }
        scrollTargetAvailable = clampScroll(scrollTargetAvailable, available.size(), listHeight());
        scrollTargetSelected = clampScroll(scrollTargetSelected, selected.size(), listHeight());
        scrollAvailable = clampScroll(scrollAvailable, available.size(), listHeight());
        scrollSelected = clampScroll(scrollSelected, selected.size(), listHeight());
    }

    private boolean matches(Pack pack) {
        return LinkenPackSearch.matches(
            pack.getDisplayName().getString(),
            pack.getDescription().getString(),
            pack.getName(),
            query.value());
    }

    private static float listHeight() {
        return Math.max(1.0f, LIST_BOTTOM - LIST_TOP);
    }

    private static float contentHeight(int rows) {
        if (rows <= 0) {
            return 0.0f;
        }
        return rows * ROW_H + (rows - 1) * ROW_GAP;
    }

    private static float maxScroll(int rows) {
        return Math.max(0.0f, contentHeight(rows) - listHeight());
    }

    private static float clampScroll(float value, int rows, float height) {
        return Math.max(0.0f, Math.min(value, maxScroll(rows)));
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
            I18n.get("linkenvisuals.linkmenu.packs.title"),
            LinkenMenuLayout.MARGIN, 162.0f, 40.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.packs.subtitle"),
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

        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "SETTINGS  /  RESOURCE PACKS",
            WINDOW_X + 24.0f, WINDOW_Y + 26.0f, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));
        searchRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - 334.0f, WINDOW_Y + 18.0f, 310.0f, 38.0f);
        drawSearch(context, metrics, mx, my, fade);

        scrollTargetAvailable = clampScroll(scrollTargetAvailable, available.size(), listHeight());
        scrollTargetSelected = clampScroll(scrollTargetSelected, selected.size(), listHeight());
        scrollAvailable += (scrollTargetAvailable - scrollAvailable)
            * (1.0f - (float) Math.exp(-14.0f * seconds));
        scrollSelected += (scrollTargetSelected - scrollSelected)
            * (1.0f - (float) Math.exp(-14.0f * seconds));
        if (Math.abs(scrollTargetAvailable - scrollAvailable) < 0.4f) {
            scrollAvailable = scrollTargetAvailable;
        }
        if (Math.abs(scrollTargetSelected - scrollSelected) < 0.4f) {
            scrollSelected = scrollTargetSelected;
        }

        float leftX = WINDOW_X + PAD;
        float rightX = leftX + COL_W + COL_GAP;
        availableColumnRect = new LinkenMenuLayout.Rect(leftX, LIST_TOP, COL_W, LIST_BOTTOM - LIST_TOP);
        selectedColumnRect = new LinkenMenuLayout.Rect(rightX, LIST_TOP, COL_W, LIST_BOTTOM - LIST_TOP);

        drawColumn(context, metrics, availableColumnRect, available, availableRows, scrollAvailable,
            false, mx, my, fade, seconds);
        drawColumn(context, metrics, selectedColumnRect, selected, selectedRows, scrollSelected,
            true, mx, my, fade, seconds);

        float footerY = WINDOW_Y + WINDOW_H - FOOTER_H;
        context.fill(Math.round(WINDOW_X + 1.0f), Math.round(footerY),
            Math.round(WINDOW_X + WINDOW_W - 1.0f), Math.round(WINDOW_Y + WINDOW_H - 1.0f),
            LinkenMenuChrome.rgba(0x0E1411, 250, fade));

        openFolderRect = new LinkenMenuLayout.Rect(WINDOW_X + PAD, WINDOW_Y + WINDOW_H - 54.0f, 150.0f, 40.0f);
        drawSecondaryButton(context, metrics, openFolderRect,
            I18n.get("linkenvisuals.linkmenu.packs.open_folder"), mx, my, fade, openFolderSpring, seconds);
        doneRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - PAD - 130.0f,
            WINDOW_Y + WINDOW_H - 54.0f, 130.0f, 40.0f);
        drawDoneButton(context, metrics, doneRect, mx, my, fade, seconds);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.packs.hint"),
            openFolderRect.right() + 16.0f, WINDOW_Y + WINDOW_H - 38.0f, 8.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));

        WindowMaterial.corners(context, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H, metrics.scale(), fade);

        if (pendingIncompatible != null) {
            drawIncompatibleModal(context, metrics, mx, my, fade, seconds);
        }
    }

    private void drawIncompatibleModal(DrawContext context, LinkenMenuLayout.Metrics metrics,
                                       float mx, float my, float fade, float seconds) {
        context.fill(0, 0, Math.round(metrics.width()), Math.round(metrics.height()),
            LinkenMenuChrome.rgba(0x040706, 165, fade));

        float width = 560.0f;
        float height = 250.0f;
        float x = (metrics.width() - width) * 0.5f;
        float y = 390.0f;
        LinkenMenuChrome.roundedFill(context, x, y, width, height, 14.0f,
            LinkenMenuChrome.rgba(0x0E1411, 252, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, x, y, width, height, 14.0f, 1.2f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.DANGER, 150, fade), metrics.scale());

        LinkenMenuChrome.drawSpaced(context, LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.packs.incompatible_title"),
            x + 24.0f, y + 22.0f, 9.0f, 1.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.DANGER, fade));
        String name = oneLine(pendingIncompatible.getDisplayName().getString());
        LinkenMenuChrome.drawText(context, LinkenFonts.heading(),
            LinkenMenuChrome.ellipsize(LinkenFonts.heading(), name, width - 48.0f, 15.0f),
            x + 24.0f, y + 44.0f, 15.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));

        List<String> lines = wrap(LinkenFonts.body(),
            I18n.get("linkenvisuals.linkmenu.packs.incompatible_body"), width - 48.0f, 10.0f);
        float lineY = y + 78.0f;
        for (String line : lines) {
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), line,
                x + 24.0f, lineY, 10.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
            lineY += 16.0f;
        }

        modalCancelRect = new LinkenMenuLayout.Rect(x + width - 24.0f - 150.0f - 12.0f - 180.0f,
            y + height - 64.0f, 150.0f, 40.0f);
        modalConfirmRect = new LinkenMenuLayout.Rect(x + width - 24.0f - 180.0f,
            y + height - 64.0f, 180.0f, 40.0f);
        drawSecondaryButton(context, metrics, modalCancelRect,
            I18n.get("linkenvisuals.linkmenu.packs.cancel"), mx, my, fade, backSpring, seconds);

        boolean hovered = modalConfirmRect.contains(mx, my);
        doneSpring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) doneSpring.get();
        LinkenMenuChrome.roundedFill(context, modalConfirmRect.x(), modalConfirmRect.y() - hover,
            modalConfirmRect.width(), modalConfirmRect.height(), 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.DANGER, 0xFFFFFF, hover * 0.08f), fade),
            metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(),
            I18n.get("linkenvisuals.linkmenu.packs.enable_anyway"),
            modalConfirmRect.x() + modalConfirmRect.width() * 0.5f,
            modalConfirmRect.y() + 13.0f - hover, 9.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.INK, fade));
    }

    private static List<String> wrap(dev.linkenvisuals.render.GlyphAtlas font, String text,
                                     float maxWidth, float size) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && LinkenMenuChrome.textWidth(font, candidate, size) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
                if (lines.size() == 2) {
                    break;
                }
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty() && lines.size() < 2) {
            lines.add(line.toString());
        } else if (!line.isEmpty() && !lines.isEmpty()) {
            lines.set(1, LinkenMenuChrome.ellipsize(font, lines.get(1) + " " + line, maxWidth, size));
        }
        return lines;
    }

    private void drawColumn(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect column,
                            List<Pack> packs, List<Row> rows, float scroll, boolean selectedColumn,
                            float mx, float my, float fade, float seconds) {
        rows.clear();
        String header = I18n.get(selectedColumn
            ? "linkenvisuals.linkmenu.packs.selected"
            : "linkenvisuals.linkmenu.packs.available");
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.body(), header,
            column.x() + 4.0f, column.y() - 26.0f, 8.5f, 1.5f,
            LinkenMenuChrome.rgba(selectedColumn ? LinkenMenuChrome.ACCENT : LinkenMenuChrome.MUTED2, fade));
        String count = packs.size() == 1 ? "1 PACK" : packs.size() + " PACKS";
        LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), count,
            column.right() - 4.0f, column.y() - 23.0f, 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));
        context.fill(Math.round(column.x()), Math.round(column.y() - 4.0f),
            Math.round(column.right()), Math.round(column.bottom()),
            LinkenMenuChrome.rgba(0x111713, 190, fade));

        for (int i = 0; i < packs.size(); i++) {
            float y = column.y() + i * (ROW_H + ROW_GAP) - scroll;
            if (y + ROW_H < column.y() || y > column.bottom()) {
                continue;
            }
            Row row = drawRow(context, metrics, column, packs.get(i), i, y, selectedColumn,
                mx, my, fade, seconds);
            rows.add(row);
        }
        if (packs.isEmpty()) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(),
                I18n.get("linkenvisuals.linkmenu.packs.none"),
                column.x() + column.width() * 0.5f, column.y() + column.height() * 0.5f, 12.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
        }
    }

    private Row drawRow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect column,
                        Pack pack, int index, float y, boolean selectedColumn,
                        float mx, float my, float fade, float seconds) {
        final float x = column.x() + 4.0f;
        final float width = column.width() - 24.0f;
        LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(x, y, width, ROW_H);
        boolean hovered = rect.contains(mx, my);
        Spring spring = rowHover[(selectedColumn ? 64 : 0) + index % 64];
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        boolean enabled = selectedColumn;
        boolean compatible = pack.getCompatibility().isCompatible();
        boolean interactive = enabled ? pack.canBeDisabled() : pack.canBeEnabled();

        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0x111713, 0x18211B, hover), 238, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
            1.0f, LinkenMenuChrome.rgba(compatible ? 0xFFFFFF : LinkenMenuChrome.DANGER,
                18 + Math.round(hover * 34), fade), metrics.scale());

        float iconSize = 40.0f;
        float iconX = rect.x() + 12.0f;
        float iconY = rect.y() + 12.0f - hover;
        drawPackIcon(context, metrics, pack, iconX, iconY, iconSize, fade);

        float textX = iconX + iconSize + 12.0f;
        float rightLimit = rect.right() - 14.0f;
        float textWidth = Math.max(40.0f, rightLimit - textX - 74.0f);
        String name = oneLine(pack.getDisplayName().getString());
        String description = oneLine(pack.getDescription().getString());
        LinkenMenuChrome.drawText(context, LinkenFonts.heading(),
            LinkenMenuChrome.ellipsize(LinkenFonts.heading(), name, textWidth, 11.5f),
            textX, rect.y() + 10.0f - hover, 11.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            LinkenMenuChrome.ellipsize(LinkenFonts.body(), description, textWidth, 9.0f),
            textX, rect.y() + 27.0f - hover, 9.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        String note = !compatible
            ? I18n.get("linkenvisuals.linkmenu.packs.conflict")
            : pack.isPinned() ? I18n.get("linkenvisuals.linkmenu.packs.pinned")
            : pack.isAlwaysEnabled() ? I18n.get("linkenvisuals.linkmenu.packs.locked")
            : "";
        if (!note.isEmpty()) {
            LinkenMenuChrome.drawSpaced(context, LinkenFonts.mono(), note,
                textX, rect.y() + 46.0f - hover, 7.5f, 1.1f,
                LinkenMenuChrome.rgba(!compatible ? LinkenMenuChrome.DANGER : LinkenMenuChrome.MUTED3, fade));
        }

        if (selectedColumn) {
            String order = String.format(Locale.ROOT, "%02d", index + 1);
            LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), order,
                rect.right() - 48.0f, rect.y() + 9.0f - hover, 8.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));
            LinkenMenuLayout.Rect up = new LinkenMenuLayout.Rect(rect.right() - 40.0f,
                rect.y() + 6.0f - hover, 24.0f, 24.0f);
            LinkenMenuLayout.Rect down = new LinkenMenuLayout.Rect(rect.right() - 40.0f,
                rect.y() + 34.0f - hover, 24.0f, 24.0f);
            drawArrow(context, metrics, up, true, pack.canMoveTowardStart() && interactive,
                up.contains(mx, my), fade);
            drawArrow(context, metrics, down, false, pack.canMoveTowardEnd() && interactive,
                down.contains(mx, my), fade);
            return new Row(pack, rect, up, down);
        }

        float plusX = rect.right() - 44.0f;
        float plusY = rect.y() + 20.0f - hover;
        LinkenMenuChrome.roundedFill(context, plusX, plusY, 28.0f, 24.0f, 12.0f,
            LinkenMenuChrome.rgba(interactive ? LinkenMenuChrome.ACCENT : 0x222B25,
                interactive ? 22 : 180, fade), metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.mono(), "+",
            plusX + 14.0f, plusY + 4.0f, 12.0f,
            LinkenMenuChrome.rgba(interactive ? LinkenMenuChrome.ACCENT : LinkenMenuChrome.MUTED3, fade));
        return new Row(pack, rect, null, null);
    }

    private void drawPackIcon(DrawContext context, LinkenMenuLayout.Metrics metrics, Pack pack,
                              float x, float y, float size, float fade) {
        LinkenMenuChrome.roundedFill(context, x, y, size, size, 8.0f,
            LinkenMenuChrome.rgba(0x1B241E, 240, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, x, y, size, size, 8.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 18, fade), metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(),
            LinkenPackSearch.initials(pack.getDisplayName().getString()),
            x + size * 0.5f, y + 13.0f, 10.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
    }

    private void drawArrow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                           boolean up, boolean enabled, boolean hovered, float fade) {
        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y(), rect.width(), rect.height(), 6.0f,
            LinkenMenuChrome.rgba(hovered && enabled ? 0x1B241E : 0x151C17, 220, fade), metrics.scale());
        String glyph = up ? "^" : "v";
        LinkenMenuChrome.drawCentered(context, LinkenFonts.mono(), glyph,
            rect.x() + rect.width() * 0.5f, rect.y() + 7.0f, 9.0f,
            LinkenMenuChrome.rgba(enabled ? (hovered ? LinkenMenuChrome.ACCENT : LinkenMenuChrome.TEXT3)
                : LinkenMenuChrome.MUTED4, fade));
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
        String shown = queryValue.isBlank() ? "SEARCH PACKS..." : queryValue;
        LinkenMenuChrome.drawText(context, LinkenFonts.mono(), shown, searchRect.x() + 14.0f,
            searchRect.y() + 13.0f, 9.0f,
            LinkenMenuChrome.rgba(queryValue.isBlank() ? LinkenMenuChrome.MUTED3 : LinkenMenuChrome.TEXT2, fade));
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
            rect.x() + rect.width() * 0.5f, rect.y() + 13.0f - hover, 9.5f,
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
        if (client == null) {
            return super.mouseClicked(click, doubleClick);
        }
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) click.x() / metrics.scale();
        float my = (float) click.y() / metrics.scale();
        if (pendingIncompatible != null) {
            if (modalCancelRect != null && modalCancelRect.contains(mx, my)) {
                pendingIncompatible = null;
                return true;
            }
            if (modalConfirmRect != null && modalConfirmRect.contains(mx, my)) {
                Pack pack = pendingIncompatible;
                pendingIncompatible = null;
                if (pack.canBeEnabled()) {
                    pack.enable();
                    refreshLists();
                }
            }
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
        if (openFolderRect != null && openFolderRect.contains(mx, my) && client != null) {
            Util.getOperatingSystem().open(client.getResourcePackDir());
            return true;
        }
        for (Row row : selectedRows) {
            if (row.up() != null && row.up().contains(mx, my)) {
                if (row.pack().canMoveTowardStart() && row.pack().canBeDisabled()) {
                    row.pack().moveTowardStart();
                    refreshLists();
                }
                return true;
            }
            if (row.down() != null && row.down().contains(mx, my)) {
                if (row.pack().canMoveTowardEnd() && row.pack().canBeDisabled()) {
                    row.pack().moveTowardEnd();
                    refreshLists();
                }
                return true;
            }
            if (row.rect().contains(mx, my)) {
                if (row.pack().canBeDisabled()) {
                    row.pack().disable();
                    refreshLists();
                }
                return true;
            }
        }
        for (Row row : availableRows) {
            if (row.rect().contains(mx, my)) {
                if (row.pack().canBeEnabled()) {
                    if (row.pack().getCompatibility().isCompatible()) {
                        row.pack().enable();
                        refreshLists();
                    } else {
                        pendingIncompatible = row.pack();
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused) {
            boolean handled = query.charTyped(input);
            if (handled) {
                refreshLists();
            }
            return handled;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (pendingIncompatible != null) {
                pendingIncompatible = null;
                return true;
            }
            if (searchFocused || !query.value().isBlank()) {
                searchFocused = false;
                query.clear();
                refreshLists();
            } else {
                close();
            }
            return true;
        }
        if (searchFocused && query.keyPressed(input)) {
            refreshLists();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) mouseX / metrics.scale();
        float my = (float) mouseY / metrics.scale();
        if (availableColumnRect != null && availableColumnRect.contains(mx, my)) {
            scrollTargetAvailable = clampScroll(scrollTargetAvailable
                - (float) verticalAmount * 56.0f, available.size(), listHeight());
            return true;
        }
        if (selectedColumnRect != null && selectedColumnRect.contains(mx, my)) {
            scrollTargetSelected = clampScroll(scrollTargetSelected
                - (float) verticalAmount * 56.0f, selected.size(), listHeight());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (client == null) {
            return;
        }
        Screen target = parent;
        if (target == null || target == this) {
            target = client.world != null ? new LinkenPauseScreen() : new LinkenMainMenuScreen();
        }
        if (organizer != null) {
            organizer.apply();
            if (client.currentScreen == this) {
                client.setScreen(target);
            }
        } else {
            client.setScreen(target);
        }
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
