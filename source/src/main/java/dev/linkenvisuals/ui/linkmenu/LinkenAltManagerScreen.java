package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.audio.LinkenSounds;

import dev.linkenvisuals.account.AccountStorage;
import dev.linkenvisuals.account.AltAccountFilter;
import dev.linkenvisuals.account.StoredAccount;
import dev.linkenvisuals.mixin.MinecraftClientAccessor;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.session.Session;
import dev.linkenvisuals.util.input.TextInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * LINKEN account manager. It keeps the existing offline account storage and
 * session switch, but replaces the vanilla/Beta list with the shared
 * 1920x1080 LINKEN chrome.
 */
public final class LinkenAltManagerScreen extends Screen {
    private enum Modal {
        NONE, ADD, DELETE
    }

    private static final float WINDOW_X = 380.0f;
    private static final float WINDOW_Y = 238.0f;
    private static final float WINDOW_W = 1160.0f;
    private static final float WINDOW_H = 636.0f;
    private static final float LIST_TOP = WINDOW_Y + 96.0f;
    private static final float LIST_BOTTOM = WINDOW_Y + WINDOW_H - 92.0f;
    private static final float ROW_H = 64.0f;
    private static final float ROW_GAP = 8.0f;

    private final Screen parent;
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring backSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring addSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring useSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring deleteSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring modalCancelSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring modalConfirmSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring modalCloseSpring = new Spring(0.0, 900.0, 50.0);
    private final Spring[] rowHover = new Spring[64];

    private final List<StoredAccount> visible = new ArrayList<>();
    private final List<StoredAccount> rowAccounts = new ArrayList<>();
    private final List<LinkenMenuLayout.Rect> rowRects = new ArrayList<>();

    private StoredAccount selected;
    private final TextInput query = new TextInput().maxLength(32);
    private boolean searchFocused;
    private Modal modal = Modal.NONE;
    private final TextInput draft = new TextInput().maxLength(16)
        .filter(codepoint -> Character.isLetterOrDigit(codepoint) || codepoint == '_');
    private String modalError;
    private float scroll;
    private float scrollTarget;
    private long lastFrame = System.nanoTime();

    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect searchRect;
    private LinkenMenuLayout.Rect addRect;
    private LinkenMenuLayout.Rect useRect;
    private LinkenMenuLayout.Rect deleteRect;
    private LinkenMenuLayout.Rect modalBounds;
    private LinkenMenuLayout.Rect modalFieldRect;
    private LinkenMenuLayout.Rect modalCancelRect;
    private LinkenMenuLayout.Rect modalConfirmRect;
    private LinkenMenuLayout.Rect modalCloseRect;

    public LinkenAltManagerScreen(Screen parent) {
        super(Text.literal("Alt Accounts"));
        this.parent = parent;
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0, 900.0, 50.0);
        }
    }

    @Override
    protected void init() {
        refresh();
        selected = activeAccount();
        if (selected == null && !visible.isEmpty()) {
            selected = visible.get(0);
        }
        openSpring.reset(0.0);
        lastFrame = System.nanoTime();
    }

    @Override
    public void tick() {
        if (selected == null && !visible.isEmpty()) {
            StoredAccount active = activeAccount();
            selected = active != null ? active : visible.get(0);
        }
    }

    private void refresh() {
        visible.clear();
        visible.addAll(AltAccountFilter.filter(AccountStorage.accounts(), query.value()));
        if (selected != null && AltAccountFilter.indexOf(visible, selected) < 0) {
            selected = visible.isEmpty() ? null : visible.get(0);
        }
        if (selected == null && !visible.isEmpty()) {
            StoredAccount active = activeAccount();
            selected = active != null ? active : visible.get(0);
        }
        scrollTarget = clampScroll(scrollTarget);
        scroll = clampScroll(scroll);
    }

    private float maxScroll() {
        float content = visible.size() * ROW_H + Math.max(0, visible.size() - 1) * ROW_GAP;
        return Math.max(0.0f, content - (LIST_BOTTOM - LIST_TOP));
    }

    private float clampScroll(float value) {
        return Math.max(0.0f, Math.min(value, maxScroll()));
    }

    private StoredAccount activeAccount() {
        if (client == null || client.getSession() == null) {
            return null;
        }
        String username = client.getSession().getUsername();
        for (StoredAccount account : AccountStorage.accounts()) {
            if (account.username().equalsIgnoreCase(username)) {
                return account;
            }
        }
        return null;
    }

    private boolean isActive(StoredAccount account) {
        return account != null && client != null && client.getSession() != null
            && account.username().equalsIgnoreCase(client.getSession().getUsername());
    }

    private void apply(StoredAccount account) {
        if (client == null || account == null || !AccountStorage.isValidUsername(account.username())) {
            return;
        }
        Session session = new Session(account.username(), account.uuid(), "0", Optional.empty(), Optional.empty());
        ((MinecraftClientAccessor) client).linkenvisuals$setSession(session);
        selected = account;
        playClick();
    }

    private void openModal(Modal type) {
        modal = type;
        modalError = null;
        draft.clear();
        if (type == Modal.ADD) {
            searchFocused = false;
        }
    }

    private void closeModal() {
        modal = Modal.NONE;
        modalError = null;
    }

    private void confirmModal() {
        if (modal == Modal.ADD) {
            String name = draft.value().trim();
            if (!AccountStorage.isValidUsername(name)) {
                modalError = "USERNAME MUST BE 3-16 CHARS (A-Z, 0-9, _).";
                return;
            }
            for (StoredAccount account : AccountStorage.accounts()) {
                if (account.username().equalsIgnoreCase(name)) {
                    modalError = "THIS ACCOUNT IS ALREADY SAVED.";
                    return;
                }
            }
            if (!AccountStorage.add(name)) {
                modalError = "COULD NOT SAVE THIS ACCOUNT.";
                return;
            }
            StoredAccount added = new StoredAccount(name);
            selected = added;
            apply(added);
            closeModal();
            refresh();
            playClick();
            return;
        }
        if (modal == Modal.DELETE && selected != null) {
            AccountStorage.remove(selected);
            if (isActive(selected)) {
                selected = visible.isEmpty() ? null : visible.get(0);
            }
            closeModal();
            refresh();
            playClick();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = Math.min((now - lastFrame) / 1_000_000_000.0f, 0.05f);
        lastFrame = now;
        openSpring.step(modal == Modal.NONE ? 1.0 : 0.88, seconds);
        float fade = (float) openSpring.get();

        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = mouseX / metrics.scale();
        float my = mouseY / metrics.scale();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        LinkenMenuChrome.drawBackground(context, metrics, 0.0f, 0.0f, 1.0f);
        GlowPipeline.radial(context, metrics.width() * 0.50f, 470.0f, 720.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 10, fade));

        context.getMatrices().translate(0.0f, (1.0f - fade) * 18.0f);
        LinkenMenuChrome.drawWordmark(context, fade);
        LinkenMenuChrome.drawClock(context, metrics, fade);

        backRect = new LinkenMenuLayout.Rect(metrics.width() - LinkenMenuLayout.MARGIN - 132.0f,
            104.0f, 132.0f, 44.0f);
        drawChip(context, metrics, backRect, "BACK", LinkenMenuChrome.ICON_POWER, mx, my, fade, backSpring, seconds);

        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), "ACCOUNT SWITCHER",
            LinkenMenuLayout.MARGIN, 146.0f, 8.5f, 1.6f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "ALT ACCOUNTS",
            LinkenMenuLayout.MARGIN, 162.0f, 40.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        String count = AccountStorage.accounts().size() == 1
            ? "1 SAVED ACCOUNT" : AccountStorage.accounts().size() + " SAVED ACCOUNTS";
        if (!query.value().isBlank() && !AccountStorage.accounts().isEmpty()) {
            count += "  /  " + visible.size() + " MATCHING";
        }
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), count,
            LinkenMenuLayout.MARGIN, 218.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        drawWindow(context, metrics, mx, my, fade, seconds);
        context.getMatrices().popMatrix();

        if (modal != Modal.NONE) {
            context.fill(0, 0, width, height, 0xA8050806);
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(metrics.scale(), metrics.scale());
            drawModal(context, metrics, mx, my, fade, seconds);
            context.getMatrices().popMatrix();
        }
    }

    private void drawWindow(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my,
                            float fade, float seconds) {
        LinkenMenuChrome.roundedFill(context, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H, 14.0f,
            LinkenMenuChrome.rgba(0x0E1411, 250, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H, 14.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 28, fade), metrics.scale());

        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), "SAVED ACCOUNTS",
            WINDOW_X + 24.0f, WINDOW_Y + 26.0f, 15.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));

        searchRect = new LinkenMenuLayout.Rect(WINDOW_X + WINDOW_W - 384.0f, WINDOW_Y + 22.0f, 360.0f, 44.0f);
        drawSearch(context, metrics, mx, my, fade);

        float listTop = LIST_TOP;
        float listHeight = LIST_BOTTOM - LIST_TOP;
        scrollTarget = clampScroll(scrollTarget);
        scroll += (scrollTarget - scroll) * (1.0f - (float) Math.exp(-14.0f * seconds));
        if (Math.abs(scrollTarget - scroll) < 0.4f) {
            scroll = scrollTarget;
        }

        rowRects.clear();
        rowAccounts.clear();
        if (visible.isEmpty()) {
            String empty = AccountStorage.accounts().isEmpty()
                ? "NO ACCOUNTS SAVED YET"
                : "NO ACCOUNTS MATCH THIS SEARCH";
            LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), empty,
                WINDOW_X + WINDOW_W * 0.5f, listTop + listHeight * 0.5f - 8.0f, 15.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT3, fade));
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(),
                AccountStorage.accounts().isEmpty() ? "ADD YOUR FIRST OFFLINE ACCOUNT BELOW" : "TRY A SHORTER QUERY",
                WINDOW_X + WINDOW_W * 0.5f, listTop + listHeight * 0.5f + 16.0f, 9.5f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
        } else {
            for (int i = 0; i < visible.size(); i++) {
                float ry = listTop + i * (ROW_H + ROW_GAP) - scroll;
                if (ry + ROW_H < listTop || ry > LIST_BOTTOM) {
                    continue;
                }
                StoredAccount account = visible.get(i);
                LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(WINDOW_X + 20.0f, ry,
                    WINDOW_W - 40.0f, ROW_H);
                rowRects.add(rect);
                rowAccounts.add(account);
                drawRow(context, metrics, rect, account, i, mx, my, fade, seconds);
            }
        }

        addRect = new LinkenMenuLayout.Rect(WINDOW_X + 20.0f, WINDOW_Y + WINDOW_H - 64.0f, 190.0f, 44.0f);
        useRect = new LinkenMenuLayout.Rect(addRect.right() + 12.0f, addRect.y(), 176.0f, 44.0f);
        deleteRect = new LinkenMenuLayout.Rect(useRect.right() + 8.0f, addRect.y(), 146.0f, 44.0f);
        drawButton(context, metrics, addRect, "ADD ACCOUNT", true, true, mx, my, fade, addSpring, seconds);
        drawButton(context, metrics, useRect, "USE SELECTED", selected != null, false, mx, my, fade, useSpring, seconds);
        drawButton(context, metrics, deleteRect, "DELETE", selected != null, false, mx, my, fade, deleteSpring, seconds);
        LinkenMenuChrome.drawRightText(context, LinkenFonts.body(), "ESC  /  BACK",
            WINDOW_X + WINDOW_W - 20.0f, WINDOW_Y + WINDOW_H - 42.0f, 9.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));

        WindowMaterial.corners(context, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H, metrics.scale(), fade);
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
        String shown = queryValue.isBlank() ? "SEARCH ACCOUNTS..." : queryValue;
        LinkenMenuChrome.drawText(context, LinkenFonts.mono(), shown, searchRect.x() + 16.0f,
            searchRect.y() + 15.0f, 9.5f,
            LinkenMenuChrome.rgba(queryValue.isBlank() ? LinkenMenuChrome.MUTED3 : LinkenMenuChrome.TEXT2, fade));
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            String beforeCaret = queryValue.substring(0, Math.min(query.caret(), queryValue.length()));
            float caretX = searchRect.x() + 16.0f + LinkenMenuChrome.textWidth(LinkenFonts.mono(), beforeCaret, 9.5f);
            context.fill(Math.round(caretX), Math.round(searchRect.y() + 14.0f),
                Math.round(caretX + 1.0f), Math.round(searchRect.y() + 32.0f),
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        }
    }

    private void drawRow(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                         StoredAccount account, int index, float mx, float my, float fade, float seconds) {
        boolean hovered = rect.contains(mx, my);
        Spring spring = rowHover[index % rowHover.length];
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        boolean selectedRow = selected != null && selected.username().equalsIgnoreCase(account.username());
        boolean active = isActive(account);

        int fill = LinkenMenuChrome.mix(0x111713, 0x18211B, hover);
        LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            10.0f, LinkenMenuChrome.rgba(fill, 238, fade), metrics.scale());
        int border = active ? LinkenMenuChrome.ACCENT : (selectedRow ? 0xFFFFFF : 0xFFFFFF);
        int alpha = active ? 160 : (selectedRow ? 70 : 20 + Math.round(hover * 40));
        LinkenMenuChrome.roundedOutline(context, rect.x(), rect.y() - hover * 1.5f, rect.width(), rect.height(),
            10.0f, active ? 1.4f : 1.0f, LinkenMenuChrome.rgba(border, alpha, fade), metrics.scale());

        float avatar = 42.0f;
        float ax = rect.x() + 14.0f;
        float ay = rect.y() + (rect.height() - avatar) * 0.5f - hover * 1.5f;
        int avatarFill = active ? LinkenMenuChrome.ACCENT
            : LinkenMenuChrome.mix(0x1C2520, 0x253129, hover);
        LinkenMenuChrome.roundedFill(context, ax, ay, avatar, avatar, 8.0f,
            LinkenMenuChrome.rgba(avatarFill, active ? 220 : 255, fade), metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(),
            account.username().substring(0, 1).toUpperCase(Locale.ROOT),
            ax + avatar * 0.5f, ay + 12.0f, 16.0f,
            LinkenMenuChrome.rgba(active ? LinkenMenuChrome.INK : LinkenMenuChrome.TEXT2, fade));

        String name = LinkenMenuChrome.ellipsize(LinkenFonts.headingLight(), account.username(), 360.0f, 14.0f);
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), name,
            rect.x() + 70.0f, rect.y() + 13.0f - hover * 1.5f, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.body(), "OFFLINE ACCOUNT",
            rect.x() + 70.0f, rect.y() + 35.0f - hover * 1.5f, 8.5f, 1.3f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        String badge = active ? "ACTIVE" : (selectedRow ? "SELECTED" : "CLICK TO SELECT");
        float badgeW = active ? 74.0f : (selectedRow ? 86.0f : 112.0f);
        float badgeX = rect.right() - 18.0f - badgeW;
        float badgeY = rect.y() + 20.0f - hover * 1.5f;
        if (active) {
            LinkenMenuChrome.roundedFill(context, badgeX, badgeY, badgeW, 24.0f, 12.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 28, fade), metrics.scale());
            LinkenMenuChrome.drawCentered(context, LinkenFonts.mono(), badge,
                badgeX + badgeW * 0.5f, badgeY + 8.0f, 8.5f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        } else {
            LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), badge,
                badgeX + badgeW, badgeY + 8.0f, 8.5f,
                LinkenMenuChrome.rgba(selectedRow ? LinkenMenuChrome.TEXT3 : LinkenMenuChrome.MUTED3, fade));
        }
    }

    private void drawModal(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my,
                           float fade, float seconds) {
        boolean delete = modal == Modal.DELETE;
        float width = delete ? 540.0f : 620.0f;
        float height = delete ? 250.0f : 300.0f;
        float x = (metrics.width() - width) * 0.5f;
        float y = (metrics.height() - height) * 0.5f;
        modalBounds = new LinkenMenuLayout.Rect(x, y, width, height);
        LinkenMenuChrome.roundedFill(context, x, y, width, height, 14.0f,
            LinkenMenuChrome.rgba(0x0E1411, 250, fade), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, x, y, width, height, 14.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 32, fade), metrics.scale());

        String title = delete ? "DELETE ACCOUNT" : "ADD ACCOUNT";
        LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), title, x + 30.0f, y + 26.0f, 20.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            delete ? "THIS CANNOT BE UNDONE." : "ENTER AN OFFLINE USERNAME.",
            x + 30.0f, y + 56.0f, 9.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        modalCloseRect = new LinkenMenuLayout.Rect(x + width - 52.0f, y + 22.0f, 30.0f, 30.0f);
        modalCloseSpring.step(modalCloseRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        LinkenMenuChrome.drawSecondary(context, modalCloseRect.x(), modalCloseRect.y(), 30.0f, 30.0f,
            7.0f, (float) modalCloseSpring.get(), false, fade, metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), "x", modalCloseRect.x() + 15.0f,
            modalCloseRect.y() + 9.0f, 10.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));

        modalFieldRect = null;
        if (!delete) {
            modalFieldRect = new LinkenMenuLayout.Rect(x + 30.0f, y + 102.0f, width - 60.0f, 54.0f);
            LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), "USERNAME",
                modalFieldRect.x(), modalFieldRect.y() - 20.0f, 7.5f, 1.4f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
            LinkenMenuChrome.roundedFill(context, modalFieldRect.x(), modalFieldRect.y(),
                modalFieldRect.width(), modalFieldRect.height(), 7.0f,
                LinkenMenuChrome.rgba(0x212C24, 240, fade), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, modalFieldRect.x(), modalFieldRect.y(),
                modalFieldRect.width(), modalFieldRect.height(), 7.0f, 1.3f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 170, fade), metrics.scale());
            String draftValue = draft.value();
            String shown = draftValue.isBlank() ? "USERNAME..." : draftValue;
            LinkenMenuChrome.drawText(context, LinkenFonts.mono(), shown,
                modalFieldRect.x() + 16.0f, modalFieldRect.y() + 18.0f, 10.0f,
                LinkenMenuChrome.rgba(draftValue.isBlank() ? LinkenMenuChrome.MUTED4 : LinkenMenuChrome.TEXT2, fade));
            if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                String beforeCaret = draftValue.substring(0, Math.min(draft.caret(), draftValue.length()));
                float caretX = modalFieldRect.x() + 16.0f
                    + LinkenMenuChrome.textWidth(LinkenFonts.mono(), beforeCaret, 10.0f);
                context.fill(Math.round(caretX), Math.round(modalFieldRect.y() + 17.0f),
                    Math.round(caretX + 1.0f), Math.round(modalFieldRect.y() + 37.0f),
                    LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
            }
        } else {
            String target = selected == null ? "" : selected.username();
            LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), target,
                x + width * 0.5f, y + 112.0f, 18.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));
        }

        if (modalError != null) {
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), modalError, x + 30.0f,
                y + height - 92.0f, 9.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.DANGER, fade));
        }

        modalCancelRect = new LinkenMenuLayout.Rect(x + width - 30.0f - 110.0f - 10.0f - 130.0f,
            y + height - 64.0f, 110.0f, 42.0f);
        modalConfirmRect = new LinkenMenuLayout.Rect(x + width - 30.0f - 130.0f,
            y + height - 64.0f, 130.0f, 42.0f);
        drawModalButton(context, metrics, modalCancelRect, "CANCEL", false, mx, my, fade, seconds, modalCancelSpring);
        drawModalButton(context, metrics, modalConfirmRect, delete ? "DELETE" : "ADD",
            true, mx, my, fade, seconds, modalConfirmSpring);

        WindowMaterial.corners(context, x, y, width, height, metrics.scale(), fade);
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

    private void drawButton(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                            String label, boolean enabled, boolean primary, float mx, float my,
                            float fade, Spring spring, float seconds) {
        boolean hovered = enabled && rect.contains(mx, my);
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        if (enabled && primary) {
            LinkenMenuChrome.roundedFill(context, rect.x() + 4.0f, rect.y() + rect.height() - 6.0f,
                rect.width() - 8.0f, 12.0f, 8.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(55 + 35 * hover), fade), metrics.scale());
            LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 8.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF, hover * 0.07f), fade),
                metrics.scale());
        } else {
            LinkenMenuChrome.drawSecondary(context, rect.x(), rect.y() - hover, rect.width(), rect.height(),
                8.0f, enabled ? hover : 0.0f, false, fade, metrics.scale());
        }
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), label,
            rect.x() + rect.width() * 0.5f, rect.y() + 15.0f - hover, 10.5f,
            LinkenMenuChrome.rgba(enabled
                ? (primary ? LinkenMenuChrome.INK : LinkenMenuChrome.TEXT3)
                : LinkenMenuChrome.MUTED3, fade));
    }

    private void drawModalButton(DrawContext context, LinkenMenuLayout.Metrics metrics, LinkenMenuLayout.Rect rect,
                                 String label, boolean primary, float mx, float my, float fade, float seconds,
                                 Spring spring) {
        boolean hovered = rect.contains(mx, my);
        spring.step(hovered ? 1.0 : 0.0, seconds);
        float hover = (float) spring.get();
        if (primary) {
            LinkenMenuChrome.roundedFill(context, rect.x(), rect.y() - hover, rect.width(), rect.height(), 7.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF, hover * 0.07f), fade),
                metrics.scale());
        } else {
            LinkenMenuChrome.drawSecondary(context, rect.x(), rect.y() - hover, rect.width(), rect.height(),
                7.0f, hover, false, fade, metrics.scale());
        }
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), label,
            rect.x() + rect.width() * 0.5f, rect.y() + 13.0f - hover, 9.5f,
            LinkenMenuChrome.rgba(primary ? LinkenMenuChrome.INK : LinkenMenuChrome.TEXT3, fade));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) click.x() / metrics.scale();
        float my = (float) click.y() / metrics.scale();

        if (modal != Modal.NONE) {
            handleModalClick(mx, my);
            return true;
        }
        if (backRect != null && backRect.contains(mx, my)) {
            playClick();
            close();
            return true;
        }
        if (searchRect != null && searchRect.contains(mx, my)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (addRect != null && addRect.contains(mx, my)) {
            playClick();
            openModal(Modal.ADD);
            return true;
        }
        if (useRect != null && useRect.contains(mx, my)) {
            if (selected != null) {
                apply(selected);
            }
            return true;
        }
        if (deleteRect != null && deleteRect.contains(mx, my)) {
            if (selected != null) {
                playClick();
                openModal(Modal.DELETE);
            }
            return true;
        }
        for (int i = 0; i < rowRects.size(); i++) {
            if (rowRects.get(i).contains(mx, my)) {
                StoredAccount account = rowAccounts.get(i);
                selected = account;
                apply(account);
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    private void handleModalClick(float mx, float my) {
        if (modalCloseRect != null && modalCloseRect.contains(mx, my)
            || modalCancelRect != null && modalCancelRect.contains(mx, my)) {
            playClick();
            closeModal();
            return;
        }
        if (modalConfirmRect != null && modalConfirmRect.contains(mx, my)) {
            confirmModal();
            return;
        }
        if (modalFieldRect != null && modalFieldRect.contains(mx, my)) {
            return;
        }
        if (modalBounds != null && !modalBounds.contains(mx, my)) {
            closeModal();
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (modal == Modal.ADD) {
            return draft.charTyped(input);
        }
        if (modal != Modal.NONE) {
            return true;
        }
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
        if (modal == Modal.ADD) {
            if (draft.keyPressed(input)) {
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeModal();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmModal();
                return true;
            }
            return true;
        }
        if (modal == Modal.DELETE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeModal();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmModal();
                return true;
            }
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
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (selected != null) {
                apply(selected);
            }
            return true;
        }
        if (searchFocused && query.keyPressed(input)) {
            refresh();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN && !visible.isEmpty()) {
            int index = selected == null ? -1 : AltAccountFilter.indexOf(visible, selected);
            selected = visible.get(Math.min(visible.size() - 1, index + 1));
            ensureSelectionVisible();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP && !visible.isEmpty()) {
            int index = selected == null ? 0 : AltAccountFilter.indexOf(visible, selected);
            selected = visible.get(Math.max(0, index - 1));
            ensureSelectionVisible();
            return true;
        }
        return super.keyPressed(input);
    }

    private void ensureSelectionVisible() {
        int index = selected == null ? -1 : AltAccountFilter.indexOf(visible, selected);
        if (index < 0) {
            return;
        }
        float top = index * (ROW_H + ROW_GAP);
        float bottom = top + ROW_H;
        if (top < scrollTarget) {
            scrollTarget = top;
        } else if (bottom > scrollTarget + (LIST_BOTTOM - LIST_TOP)) {
            scrollTarget = bottom - (LIST_BOTTOM - LIST_TOP);
        }
        scrollTarget = clampScroll(scrollTarget);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (modal != Modal.NONE) {
            return true;
        }
        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float mx = (float) mouseX / metrics.scale();
        float my = (float) mouseY / metrics.scale();
        if (mx >= WINDOW_X && mx <= WINDOW_X + WINDOW_W && my >= LIST_TOP && my <= LIST_BOTTOM) {
            scrollTarget = clampScroll(scrollTarget - (float) verticalAmount * 54.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void playClick() {
        if (client != null && client.getSoundManager() != null) {
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
                LinkenSounds.UI_CLICK, 0.8f, 1.0f));
        }
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
