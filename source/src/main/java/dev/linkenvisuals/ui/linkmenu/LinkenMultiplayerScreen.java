package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.audio.LinkenMenuMusic;
import dev.linkenvisuals.audio.LinkenSounds;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.ui.Spring;
import dev.linkenvisuals.ui.Theme;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.util.input.TextInput;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Donor-style LINKEN server browser. Geometry follows the donor
 * {@code CustomMultiplayerScreen} 4b handoff: a 1000-wide window at y=168,
 * 68 px topbar, 64 px rows, 76 px footer and a search/filter list.
 *
 * <p>The data model stays native to our port ({@link ServerList},
 * {@link MultiplayerServerListPinger}); donor backend-pinned servers and
 * UserSync are intentionally not part of this slice.</p>
 */
public final class LinkenMultiplayerScreen extends Screen {
    private static final float WIN_W = 1000.0f;
    private static final float WIN_TOP = 168.0f;
    private static final float TOPBAR_H = 68.0f;
    private static final float ROW_H = 64.0f;
    private static final float FOOTER_H = 76.0f;
    private static final float PAD_X = 22.0f;
    private static final float TILE = 38.0f;

    private static final int ICON_ARROW_LEFT = 0xF060;
    private static final int ICON_ARROW_RIGHT = 0xF061;
    private static final int ICON_REFRESH = 0xF021;
    private static final int ICON_SEARCH = 0xF002;
    private static final int ICON_GEAR = 0xF013;
    private static final int ICON_TRASH = 0xF1F8;
    private static final int ICON_PLUS = 0xF067;
    private static final int ICON_CLOSE = 0xF00D;

    private enum Modal {
        NONE, ADD, EDIT, DIRECT, DELETE
    }

    private final Screen parent;
    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring backHover = new Spring(0.0, 900.0, 50.0);
    private final Spring changeHover = new Spring(0.0, 900.0, 50.0);
    private final Spring refreshHover = new Spring(0.0, 900.0, 50.0);
    private final Spring searchFocus = new Spring(0.0, 900.0, 50.0);
    private final Spring searchHover = new Spring(0.0, 900.0, 50.0);
    private final Spring editHover = new Spring(0.0, 900.0, 50.0);
    private final Spring deleteHover = new Spring(0.0, 900.0, 50.0);
    private final Spring directHover = new Spring(0.0, 900.0, 50.0);
    private final Spring addHover = new Spring(0.0, 900.0, 50.0);
    private final Spring addEmptyHover = new Spring(0.0, 900.0, 50.0);
    private final Spring joinHover = new Spring(0.0, 900.0, 50.0);
    private final Map<ServerInfo, Spring> rowHover = new IdentityHashMap<>();
    private final Map<ServerInfo, Identifier> faviconIds = new IdentityHashMap<>();
    private final Set<ServerInfo> faviconTried = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<NativeImageBackedTexture> faviconTextures = new ArrayList<>();

    private ServerList serverList;
    private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
    private final List<ServerInfo> servers = new ArrayList<>();
    private final List<ServerInfo> visible = new ArrayList<>();
    private final List<LinkenMenuLayout.Rect> rowRects = new ArrayList<>();

    private int selected = -1;
    private final TextInput query = new TextInput().maxLength(128);
    private boolean searchFocused;
    private float scroll;
    private float scrollTarget;
    private long lastClickMs;
    private int lastClickIndex = -1;
    private int faviconSerial;

    private long openMs;
    private long lastFrameMs = System.nanoTime();
    private float parallaxX;
    private float parallaxY;

    private Modal modal = Modal.NONE;
    private final TextInput nameInput = new TextInput().maxLength(40);
    private final TextInput addressInput = new TextInput().maxLength(128);
    private int focusedField = -1;
    private String modalError;
    private int packPolicy = 1;
    private LinkenMenuLayout.Rect[] packRects = new LinkenMenuLayout.Rect[3];

    private LinkenMenuLayout.Rect backRect;
    private LinkenMenuLayout.Rect changeRect;
    private LinkenMenuLayout.Rect refreshRect;
    private LinkenMenuLayout.Rect searchRect;
    private LinkenMenuLayout.Rect editRect;
    private LinkenMenuLayout.Rect deleteRect;
    private LinkenMenuLayout.Rect directRect;
    private LinkenMenuLayout.Rect addRect;
    private LinkenMenuLayout.Rect addEmptyRect;
    private LinkenMenuLayout.Rect joinRect;
    private LinkenMenuLayout.Rect modalCancelRect;
    private LinkenMenuLayout.Rect modalConfirmRect;
    private LinkenMenuLayout.Rect modalCloseRect;
    private LinkenMenuLayout.Rect nameFieldRect;
    private LinkenMenuLayout.Rect addressFieldRect;
    private LinkenMenuLayout.Rect modalBounds;

    public LinkenMultiplayerScreen(Screen parent) {
        super(Text.literal(I18n.get("linkenvisuals.multiplayer.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openMs = System.currentTimeMillis();
        serverList = new ServerList(client);
        serverList.loadFile();
        refreshServers();
    }

    @Override
    public void tick() {
        pinger.tick();
    }

    private void refreshServers() {
        pinger.cancel();
        servers.clear();
        if (serverList != null) {
            for (int i = 0; i < serverList.size(); i++) {
                ServerInfo info = serverList.get(i);
                servers.add(info);
                info.setStatus(ServerInfo.Status.INITIAL);
                Util.getIoWorkerExecutor().execute(() -> ping(info));
            }
        }
        rebuildVisible();
    }

    private void ping(ServerInfo info) {
        try {
            NetworkingBackend backend = client != null && client.options != null
                ? NetworkingBackend.remote(client.options.shouldUseNativeTransport())
                : NetworkingBackend.remote(false);
            pinger.add(info, () -> {
                if (client != null) {
                    client.execute(() -> info.setStatus(
                        info.protocolVersion == SharedConstants.getGameVersion().protocolVersion()
                            ? ServerInfo.Status.SUCCESSFUL
                            : ServerInfo.Status.INCOMPATIBLE));
                }
            }, () -> {
                if (client != null) {
                    client.execute(() -> info.setStatus(info.label != null && info.protocolVersion > 0
                        ? ServerInfo.Status.INCOMPATIBLE
                        : ServerInfo.Status.UNREACHABLE));
                }
            }, backend);
        } catch (UnknownHostException | RuntimeException failure) {
            info.setStatus(ServerInfo.Status.UNREACHABLE);
        }
    }

    // ------------------------------------------------------------------ list state

    private void rebuildVisible() {
        visible.clear();
        String needle = query.value().trim().toLowerCase(Locale.ROOT);
        for (ServerInfo info : servers) {
            if (needle.isEmpty() || matches(info, needle)) {
                visible.add(info);
            }
        }
        if (selected >= visible.size()) {
            selected = visible.isEmpty() ? -1 : visible.size() - 1;
        }
        clampScroll();
    }

    private static boolean matches(ServerInfo info, String needle) {
        String name = cleanName(info).toLowerCase(Locale.ROOT);
        String address = info.address == null ? "" : info.address.toLowerCase(Locale.ROOT);
        String motd = motdText(info).toLowerCase(Locale.ROOT);
        return name.contains(needle) || address.contains(needle) || motd.contains(needle);
    }

    private void clampScroll() {
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget,
            Math.max(0.0f, visible.size() * ROW_H - listHeight())));
        scroll = Math.max(0.0f, Math.min(scroll, Math.max(0.0f, visible.size() * ROW_H - listHeight())));
    }

    private int maxRows() {
        float bottomLimit = 1080.0f - 44.0f - 110.0f;
        float available = bottomLimit - WIN_TOP - TOPBAR_H - FOOTER_H;
        return Math.max(1, (int) Math.floor(available / ROW_H));
    }

    private float listHeight() {
        int rows = Math.min(Math.max(visible.size(), 1), maxRows());
        return rows * ROW_H;
    }

    private float windowHeight() {
        return TOPBAR_H + listHeight() + FOOTER_H;
    }

    private float windowX(float virtualWidth) {
        return virtualWidth * 0.5f - WIN_W * 0.5f;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = Math.min((now - lastFrameMs) / 1_000_000_000.0f, 0.05f);
        lastFrameMs = now;
        openSpring.step(1.0, seconds);

        LinkenMenuLayout.Metrics metrics = LinkenMenuLayout.compute(width, height);
        float scale = metrics.scale();
        float vw = metrics.width();
        float vh = 1080.0f;
        float mx = mouseX / scale;
        float my = mouseY / scale;

        float targetParX = Math.max(-1.0f, Math.min(1.0f, (mx - vw * 0.5f) / (vw * 0.5f)));
        float targetParY = Math.max(-1.0f, Math.min(1.0f, (my - vh * 0.5f) / (vh * 0.5f)));
        parallaxX += (targetParX - parallaxX) * 0.08f;
        parallaxY += (targetParY - parallaxY) * 0.08f;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        LinkenMenuChrome.drawBackground(context, metrics, parallaxX, parallaxY, 1.0f);

        drawBackChip(context, mx, my, scale, seconds);
        LinkenMenuChrome.drawWordmark(context, metrics, 1.0f);
        drawAccountPill(context, vw, mx, my, scale, seconds);
        LinkenMenuChrome.drawClock(context, metrics, 1.0f);
        drawVersionLabel(context, vh);
        LinkenMenuChrome.drawMusic(context, metrics.music(), LinkenMenuMusic.track(), 1.0f, scale);

        float open = (float) openSpring.get();
        float openEase = 1.0f - (1.0f - open) * (1.0f - open) * (1.0f - open);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, (1.0f - openEase) * 18.0f);
        drawWindow(context, vw, vh, mx, my, scale, seconds, open);
        context.getMatrices().popMatrix();
        context.getMatrices().popMatrix();

        if (modal != Modal.NONE) {
            context.fill(0, 0, width, height, 0xA8050806);
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scale, scale);
            drawModal(context, metrics, mx, my, 1.0f, seconds);
            context.getMatrices().popMatrix();
        }
    }

    private void drawBackChip(DrawContext context, float mx, float my, float scale, float seconds) {
        String label = I18n.get("linkenvisuals.multiplayer.back");
        float labelWidth = LinkenMenuChrome.textWidth(LinkenFonts.heading(), label, 10.0f);
        float width = 12.0f + 17.0f + 9.0f + labelWidth + 15.0f;
        float x = 56.0f;
        float y = 44.0f;
        backHover.step(backRect != null && backRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        float hover = (float) backHover.get();
        LinkenMenuChrome.drawSecondary(context, x, y - hover, width, 38.0f, 8.0f,
            hover, false, 1.0f, scale);
        IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_ARROW_LEFT, x + 20.5f,
            y - hover + 19.0f, 17.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, 1.0f));
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), label, x + 38.0f,
            y - hover + 19.0f, 10.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, 1.0f));
        backRect = new LinkenMenuLayout.Rect(x, y, width, 38.0f);
    }

    private void drawAccountPill(DrawContext context, float vw, float mx, float my,
                                 float scale, float seconds) {
        String nick = LinkenMenuChrome.username();
        String change = I18n.get("linkenvisuals.multiplayer.change");
        float nickWidth = LinkenMenuChrome.textWidth(LinkenFonts.heading(), nick, 11.0f);
        float changeWidth = LinkenMenuChrome.textWidth(LinkenFonts.body(), change, 11.0f);
        float width = 5.0f + 26.0f + 10.0f + nickWidth + 10.0f + 1.0f + 10.0f + changeWidth + 7.0f;
        float x = vw * 0.5f - width * 0.5f;
        float y = 98.0f;
        LinkenMenuChrome.drawSecondary(context, x, y, width, 34.0f, 17.0f, 0.0f,
            false, 1.0f, scale);

        float ax = x + 5.0f;
        float ay = y + 4.0f;
        RoundedRectPipeline.drawVerticalGradient(context, ax, ay, 26.0f, 26.0f, 13.0f * scale,
            ColorMath.withAlpha(0x2A3830, 255), ColorMath.withAlpha(0x101613, 255));
        RoundedRectPipeline.drawBorder(context, ax, ay, 26.0f, 26.0f, 13.0f * scale,
            1.0f * scale, 1.4f * scale, 1.4f * scale,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 82, 1.0f));
        String initial = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.ROOT);
        LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), initial, ax + 13.0f,
            ay + 13.0f, 9.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 1.0f));

        float tx = ax + 26.0f + 10.0f;
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), nick, tx, y + 17.0f, 11.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, 1.0f));
        tx += nickWidth + 10.0f;
        context.fill(Math.round(tx), Math.round(y + 10.0f), Math.round(tx + 1.0f),
            Math.round(y + 24.0f), LinkenMenuChrome.rgba(0xFFFFFF, 31, 1.0f));
        tx += 11.0f;

        float cx = tx + changeWidth * 0.5f;
        changeHover.step(changeRect != null && changeRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        float hover = (float) changeHover.get();
        LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), change, cx, y + 16.0f,
            11.0f, LinkenMenuChrome.rgba(
                LinkenMenuChrome.mix(LinkenMenuChrome.MUTED2, LinkenMenuChrome.ACCENT, hover), 1.0f));
        changeRect = new LinkenMenuLayout.Rect(tx, y, changeWidth, 34.0f);
    }

    private void drawVersionLabel(DrawContext context, float vh) {
        LinkenMenuChrome.drawText(context, LinkenFonts.mono(), "by @emper0rhero", 56.0f,
            vh - 44.0f - 11.0f, 9.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED4, 1.0f));
    }

    private void drawWindow(DrawContext context, float vw, float vh, float mx, float my,
                            float scale, float seconds, float open) {
        float x = windowX(vw);
        float y = WIN_TOP;
        float w = WIN_W;
        float h = windowHeight();

        windowMaterial(context, x, y, w, h, scale);
        drawTopbar(context, x, y, w, mx, my, scale, seconds);
        float listTop = y + TOPBAR_H;
        drawList(context, x, listTop, w, listHeight(), mx, my, scale, seconds);
        drawFooter(context, x, y + h - FOOTER_H, w, mx, my, scale, seconds);
        WindowMaterial.corners(context, x, y, w, h, scale, open);
    }

    private void windowMaterial(DrawContext context, float x, float y, float w, float h, float scale) {
        for (int i = 6; i >= 1; i--) {
            float spread = i * 2.6f;
            RoundedRectPipeline.draw(context, x - spread, y - spread + 10.0f,
                w + spread * 2.0f, h + spread * 2.0f, (12.0f + spread) * scale,
                ColorMath.withAlpha(0x000000, Math.round(10.0f - i * 0.9f)));
        }
        for (int i = 6; i >= 1; i--) {
            float spread = i * 2.6f;
            int alpha = Math.round(41.0f * (1.0f - (i - 1) / 6.0f) / 6.0f * 2.2f);
            if (alpha <= 0) {
                continue;
            }
            RoundedRectPipeline.draw(context, x - spread, y - spread,
                w + spread * 2.0f, h + spread * 2.0f, (12.0f + spread) * scale,
                ColorMath.withAlpha(LinkenMenuChrome.ACCENT, Math.min(255, alpha)));
        }

        RoundedRectPipeline.drawVerticalGradient(context, x, y, w, h, 12.0f * scale,
            ColorMath.withAlpha(0x111A15, 230), ColorMath.withAlpha(0x050807, 230));
        RoundedRectPipeline.drawVerticalGradient(context, x, y, w, Math.min(240.0f, h), 12.0f * scale,
            ColorMath.withAlpha(LinkenMenuChrome.ACCENT, 18), ColorMath.withAlpha(LinkenMenuChrome.ACCENT, 0));
        hatch(context, x, y, w, h, scale);
        context.fill(Math.round(x + 12.0f), Math.round(y + 1.0f),
            Math.round(x + w - 12.0f), Math.round(y + 2.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 13, 1.0f));
        RoundedRectPipeline.drawBorder(context, x, y, w, h, 12.0f * scale,
            1.0f * scale, 1.4f * scale, 1.4f * scale,
            LinkenMenuChrome.rgba(0xFFFFFF, 23, 1.0f));
    }

    /** Donor repeating-linear-gradient(-45deg, white 0.014, transparent 1px 10px). */
    private void hatch(DrawContext context, float x, float y, float w, float h, float scale) {
        int color = LinkenMenuChrome.rgba(0xFFFFFF, 4, 1.0f);
        float step = 10.0f * 1.4142f;
        var matrices = context.getMatrices();
        for (float d = step; d < w + h; d += step) {
            float x1 = x + Math.max(0.0f, d - h);
            float y1 = y + Math.min(d, h);
            float x2 = x + Math.min(d, w);
            float y2 = y + Math.max(0.0f, d - w);
            float length = (float) Math.hypot(x2 - x1, y2 - y1);
            if (length <= 0.5f) {
                continue;
            }
            float angle = (float) Math.atan2(y2 - y1, x2 - x1);
            matrices.pushMatrix();
            matrices.translate((x1 + x2) * 0.5f, (y1 + y2) * 0.5f);
            matrices.rotate(angle);
            RoundedRectPipeline.draw(context, -length * 0.5f, -0.5f * scale,
                length, 1.0f * scale, 0.5f * scale, color);
            matrices.popMatrix();
        }
    }


    private void drawTopbar(DrawContext context, float x, float y, float w, float mx, float my,
                            float scale, float seconds) {
        float cy = y + TOPBAR_H * 0.5f;
        LinkenMenuChrome.drawOn(context, LinkenFonts.bodyMedium(),
            I18n.get("linkenvisuals.multiplayer.title"), x + PAD_X, cy, 17.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, 1.0f));

        float refreshSize = 36.0f;
        float rx = x + w - PAD_X - refreshSize;
        float ry = cy - refreshSize * 0.5f;
        refreshHover.step(refreshRect != null && refreshRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        float refreshT = (float) refreshHover.get();
        fieldBox(context, rx, ry, refreshSize, refreshSize, refreshT, false, scale);
        IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_REFRESH, rx + refreshSize * 0.5f,
            ry + refreshSize * 0.5f, 18.0f, LinkenMenuChrome.rgba(
                LinkenMenuChrome.mix(LinkenMenuChrome.MUTED, LinkenMenuChrome.ACCENT, refreshT), 1.0f));
        refreshRect = new LinkenMenuLayout.Rect(rx, ry, refreshSize, refreshSize);

        searchFocus.step(searchFocused ? 1.0 : 0.0, seconds);
        float focusT = (float) searchFocus.get();
        float searchWidth = 320.0f + 24.0f * focusT;
        float searchHeight = 36.0f;
        float sx = rx - 12.0f - searchWidth;
        float sy = cy - searchHeight * 0.5f;
        searchHover.step(searchRect != null && searchRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        float searchT = Math.max((float) searchHover.get(), focusT);
        fieldBox(context, sx, sy, searchWidth, searchHeight, searchT, false, scale);
        IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_SEARCH, sx + 20.0f,
            sy + searchHeight * 0.5f, 17.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.MUTED3,
                LinkenMenuChrome.ACCENT, focusT), 1.0f));

        float textX = sx + 37.0f;
        if (query.isEmpty() && !searchFocused) {
            LinkenMenuChrome.drawOn(context, LinkenFonts.body(),
                I18n.get("linkenvisuals.multiplayer.search"), textX, sy + searchHeight * 0.5f,
                12.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, 1.0f));
        } else {
            LinkenMenuChrome.drawOn(context, LinkenFonts.body(), query.value(), textX,
                sy + searchHeight * 0.5f, 12.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, 1.0f));
            if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                String beforeCaret = query.value().substring(0, Math.min(query.caret(), query.value().length()));
                float caretX = textX
                    + LinkenMenuChrome.textWidth(LinkenFonts.body(), beforeCaret, 12.5f) + 1.0f;
                context.fill(Math.round(caretX), Math.round(sy + 12.0f), Math.round(caretX + 1.0f),
                    Math.round(sy + 24.0f), LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, 1.0f));
            }
        }
        searchRect = new LinkenMenuLayout.Rect(sx, sy, searchWidth, searchHeight);

        context.fill(Math.round(x), Math.round(y + TOPBAR_H - 1.0f),
            Math.round(x + w), Math.round(y + TOPBAR_H),
            LinkenMenuChrome.rgba(0xFFFFFF, 18, 1.0f));
    }

    private void fieldBox(DrawContext context, float x, float y, float w, float h,
                          float hover, boolean danger, float scale) {
        RoundedRectPipeline.draw(context, x, y, w, h, 7.0f * scale,
            LinkenMenuChrome.rgba(0xFFFFFF, 10, 1.0f));
        int borderRgb = danger ? LinkenMenuChrome.mix(0xFFFFFF, 0xE06E6E, hover) : 0xFFFFFF;
        int borderAlpha = danger ? Math.round(20.0f + 120.0f * hover) : Math.round(20.0f + 41.0f * hover);
        RoundedRectPipeline.drawBorder(context, x, y, w, h, 7.0f * scale,
            1.0f * scale, 1.4f * scale, 1.4f * scale,
            LinkenMenuChrome.rgba(borderRgb, borderAlpha, 1.0f));
    }

    private void drawList(DrawContext context, float x, float y, float w, float h,
                          float mx, float my, float scale, float seconds) {
        rowRects.clear();
        if (visible.isEmpty()) {
            String label = query.isEmpty()
                ? I18n.get("linkenvisuals.multiplayer.empty")
                : I18n.get("linkenvisuals.multiplayer.not_found");
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), label, x + w * 0.5f,
                y + h * 0.5f - 20.0f, 13.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, 1.0f));
            if (query.isEmpty()) {
                String add = I18n.get("linkenvisuals.multiplayer.add");
                float bw = 18.0f + 15.0f + 10.0f
                    + LinkenMenuChrome.textWidth(LinkenFonts.heading(), add, 11.0f) + 18.0f;
                float bh = 44.0f;
                float bx = x + (w - bw) * 0.5f;
                float by = y + h * 0.5f + 6.0f;
                addEmptyHover.step(addEmptyRect != null && addEmptyRect.contains(mx, my) ? 1.0 : 0.0, seconds);
                fieldBox(context, bx, by, bw, bh, (float) addEmptyHover.get(), false, scale);
                IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_PLUS, bx + 25.5f,
                    by + bh * 0.5f, 18.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, 1.0f));
                LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), add, bx + 43.0f,
                    by + bh * 0.5f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, 1.0f));
                addEmptyRect = new LinkenMenuLayout.Rect(bx, by, bw, bh);
            } else {
                addEmptyRect = null;
            }
            return;
        }
        addEmptyRect = null;

        float dt = Math.max(0.001f, Math.min(0.05f, seconds));

        float maxScroll = Math.max(0.0f, visible.size() * ROW_H - h);
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget, maxScroll));
        scroll += (scrollTarget - scroll) * (1.0f - (float) Math.exp(-13.0f * dt));
        if (Math.abs(scrollTarget - scroll) < 0.4f) {
            scroll = scrollTarget;
        }

        context.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
        for (int i = 0; i < visible.size(); i++) {
            float ry = y + i * ROW_H - scroll;
            if (ry + ROW_H < y - ROW_H || ry > y + h + ROW_H) {
                rowRects.add(null);
                continue;
            }
            LinkenMenuLayout.Rect rect = new LinkenMenuLayout.Rect(x, ry, w, ROW_H);
            rowRects.add(rect);
            drawRow(context, visible.get(i), i, rect, mx, my, y, h, scale, seconds);
        }
        context.disableScissor();
    }

    private void drawRow(DrawContext context, ServerInfo info, int index, LinkenMenuLayout.Rect rect,
                         float mx, float my, float clipY, float clipH, float scale, float seconds) {
        boolean selectedRow = index == selected;
        boolean hoverable = my >= clipY && my <= clipY + clipH;
        Spring hoverSpring = rowHover.computeIfAbsent(info, key -> new Spring(0.0, 900.0, 50.0));
        hoverSpring.step(hoverable && rect.contains(mx, my) && !selectedRow ? 1.0 : 0.0, seconds);
        float hover = (float) hoverSpring.get();

        if (selectedRow) {
            RoundedRectPipeline.draw(context, rect.x(), rect.y(), rect.width(), ROW_H, 0.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 13, 1.0f));
            RoundedRectPipeline.draw(context, rect.x(), rect.y(), 2.0f, ROW_H, 0.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 255, 1.0f));
        } else if (hover > 0.001f) {
            RoundedRectPipeline.draw(context, rect.x(), rect.y(), rect.width(), ROW_H, 0.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(9.0f * hover), 1.0f));
        }
        context.fill(Math.round(rect.x()), Math.round(rect.y() + ROW_H - 1.0f),
            Math.round(rect.x() + rect.width()), Math.round(rect.y() + ROW_H),
            LinkenMenuChrome.rgba(0xFFFFFF, 13, 1.0f));

        float cx = rect.x() + PAD_X;
        float cy = rect.y() + ROW_H * 0.5f;
        float tx = cx;
        float ty = cy - TILE * 0.5f;

        Identifier favicon = favicon(info);
        if (favicon != null) {
            context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, favicon,
                Math.round(tx + 3.0f), Math.round(ty + 3.0f), 0.0f, 0.0f, 32, 32, 32, 32, 32, 32, -1);
            RoundedRectPipeline.drawBorder(context, tx, ty, TILE, TILE, 8.0f * scale,
                1.0f * scale, 1.4f * scale, 1.4f * scale,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(26 * (selectedRow ? 1.4f : 1.0f)), 1.0f));
        } else {
            int tint = tileTint(info);
            RoundedRectPipeline.drawVerticalGradient(context, tx, ty, TILE, TILE, 8.0f * scale,
                ColorMath.withAlpha(tint, 255), ColorMath.withAlpha(darker(tint), 255));
            RoundedRectPipeline.drawBorder(context, tx, ty, TILE, TILE, 8.0f * scale,
                1.0f * scale, 1.4f * scale, 1.4f * scale,
                LinkenMenuChrome.rgba(selectedRow ? LinkenMenuChrome.ACCENT : 0xFFFFFF,
                    Math.round(selectedRow ? 82 : 26), 1.0f));
            String clean = cleanName(info);
            String letter = clean.isEmpty() ? "?" : clean.substring(0, 1).toUpperCase(Locale.ROOT);
            LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), letter,
                tx + TILE * 0.5f, ty + TILE * 0.5f, 12.0f,
                selectedRow ? LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 1.0f)
                    : ColorMath.withAlpha(letterColor(info), 255));
        }

        String players = playersText(info);
        String pingText = pingText(info);
        float barsWidth = 4.0f * 3.0f + 3.0f * 2.0f;
        float pingWidth = barsWidth + (pingText.isEmpty() ? 0.0f
            : 7.0f + LinkenMenuChrome.textWidth(LinkenFonts.mono(), pingText, 10.5f));
        float rightWidth = Math.max(LinkenMenuChrome.textWidth(LinkenFonts.mono(), players, 11.0f), pingWidth);
        float rightX = rect.x() + rect.width() - PAD_X - rightWidth;

        LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), players,
            rect.x() + rect.width() - PAD_X, cy - 12.5f, 11.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, 1.0f));
        float barsX = rect.x() + rect.width() - PAD_X - pingWidth;
        float barsBottom = cy + 19.0f;
        drawPingBars(context, barsX, barsBottom, info, scale);
        if (!pingText.isEmpty()) {
            LinkenMenuChrome.drawOn(context, LinkenFonts.mono(), pingText,
                barsX + barsWidth + 7.0f, barsBottom - 7.0f, 10.5f,
                LinkenMenuChrome.rgba(selectedRow ? LinkenMenuChrome.ACCENT : LinkenMenuChrome.MUTED, 1.0f));
        }

        float colX = tx + TILE + 14.0f;
        float colWidth = rightX - 14.0f - colX;
        String name = cleanName(info);
        String nameClip = LinkenMenuChrome.ellipsize(LinkenFonts.heading(), name, colWidth, 11.5f);
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), nameClip, colX, cy - 3.0f,
            11.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, 1.0f));
        String chip = versionChip(info);
        if (!chip.isEmpty()) {
            float chipX = colX + LinkenMenuChrome.textWidth(LinkenFonts.heading(), nameClip, 11.5f) + 8.0f;
            float chipWidth = LinkenMenuChrome.textWidth(LinkenFonts.mono(), chip, 9.0f) + 12.0f;
            if (chipX + chipWidth <= colX + colWidth) {
                RoundedRectPipeline.drawBorder(context, chipX, cy - 11.0f, chipWidth, 16.0f,
                    4.0f * scale, 1.0f * scale, 1.4f * scale, 1.4f * scale,
                    LinkenMenuChrome.rgba(0xFFFFFF, 31, 1.0f));
                LinkenMenuChrome.drawOn(context, LinkenFonts.mono(), chip, chipX + 6.0f,
                    cy - 3.0f, 9.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, 1.0f));
            }
        }
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            LinkenMenuChrome.ellipsize(LinkenFonts.body(), motdText(info), colWidth, 12.0f),
            colX, cy + 4.0f, 12.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, 1.0f));
    }

    private void drawPingBars(DrawContext context, float x, float bottom, ServerInfo info, float scale) {
        int filled = pingBars(info);
        float[] heights = {5.0f, 8.0f, 11.0f, 14.0f};
        for (int i = 0; i < 4; i++) {
            RoundedRectPipeline.draw(context, x + i * 5.0f, bottom - heights[i], 3.0f, heights[i],
                1.0f * scale, i < filled
                    ? LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 1.0f)
                    : LinkenMenuChrome.rgba(0xFFFFFF, 41, 1.0f));
        }
    }

    private void drawFooter(DrawContext context, float x, float y, float w, float mx, float my,
                            float scale, float seconds) {
        float cy = y + FOOTER_H * 0.5f;
        boolean has = selected >= 0 && selected < visible.size();

        float is = 38.0f;
        float iy = cy - is * 0.5f;
        float ex = x + PAD_X;
        editHover.step(has && editRect != null && editRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        float editT = has ? (float) editHover.get() : 0.0f;
        fieldBox(context, ex, iy, is, is, editT, false, scale);
        IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_GEAR, ex + is * 0.5f,
            iy + is * 0.5f, 17.0f, LinkenMenuChrome.rgba(has
                ? LinkenMenuChrome.mix(LinkenMenuChrome.MUTED, LinkenMenuChrome.TEXT2, editT)
                : LinkenMenuChrome.MUTED4, 1.0f));
        editRect = new LinkenMenuLayout.Rect(ex, iy, is, is);

        float dx = ex + is + 9.0f;
        deleteHover.step(has && deleteRect != null && deleteRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        float deleteT = has ? (float) deleteHover.get() : 0.0f;
        fieldBox(context, dx, iy, is, is, deleteT, true, scale);
        IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_TRASH, dx + is * 0.5f,
            iy + is * 0.5f, 17.0f, LinkenMenuChrome.rgba(has
                ? LinkenMenuChrome.mix(LinkenMenuChrome.MUTED, LinkenMenuChrome.DANGER, deleteT)
                : LinkenMenuChrome.MUTED4, 1.0f));
        deleteRect = new LinkenMenuLayout.Rect(dx, iy, is, is);

        float bh = 44.0f;
        float by = cy - bh * 0.5f;
        String join = I18n.get("linkenvisuals.multiplayer.join");
        float joinWidth = 24.0f + 15.0f + 11.0f
            + LinkenMenuChrome.textWidth(LinkenFonts.heading(), join, 11.0f) + 24.0f;
        float joinX = x + w - PAD_X - joinWidth;
        joinHover.step(joinRect != null && joinRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        drawJoinButton(context, joinX, by - (float) joinHover.get(), joinWidth, bh,
            (float) joinHover.get(), join, scale);
        joinRect = new LinkenMenuLayout.Rect(joinX, by, joinWidth, bh);

        String add = I18n.get("linkenvisuals.multiplayer.add");
        float addWidth = 18.0f + 15.0f + 10.0f
            + LinkenMenuChrome.textWidth(LinkenFonts.heading(), add, 11.0f) + 18.0f;
        float addX = joinX - 9.0f - addWidth;
        addHover.step(addRect != null && addRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        drawFooterButton(context, addX, by, addWidth, bh, (float) addHover.get(), add, true, scale);
        addRect = new LinkenMenuLayout.Rect(addX, by, addWidth, bh);

        String direct = I18n.get("linkenvisuals.multiplayer.direct");
        float directWidth = 20.0f
            + LinkenMenuChrome.textWidth(LinkenFonts.heading(), direct, 11.0f) + 20.0f;
        float directX = addX - 9.0f - directWidth;
        directHover.step(directRect != null && directRect.contains(mx, my) ? 1.0 : 0.0, seconds);
        drawTextButton(context, directX, by, directWidth, bh, (float) directHover.get(), direct, scale);
        directRect = new LinkenMenuLayout.Rect(directX, by, directWidth, bh);
    }

    private void drawFooterButton(DrawContext context, float x, float y, float w, float h,
                                  float hover, String label, boolean plus, float scale) {
        RoundedRectPipeline.draw(context, x, y, w, h, 8.0f * scale,
            LinkenMenuChrome.rgba(0xFFFFFF, 10, 1.0f));
        RoundedRectPipeline.drawBorder(context, x, y, w, h, 8.0f * scale,
            1.0f * scale, 1.4f * scale, 1.4f * scale,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(20.0f + 41.0f * hover), 1.0f));
        if (plus) {
            IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_PLUS, x + 25.5f, y + h * 0.5f,
                18.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, 1.0f));
            LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), label, x + 43.0f,
                y + h * 0.5f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, 1.0f));
        } else {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), label, x + w * 0.5f,
                y + h * 0.5f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, 1.0f));
        }
    }

    private void drawTextButton(DrawContext context, float x, float y, float w, float h,
                                float hover, String label, float scale) {
        RoundedRectPipeline.draw(context, x, y, w, h, 8.0f * scale,
            LinkenMenuChrome.rgba(0xFFFFFF, 10, 1.0f));
        RoundedRectPipeline.drawBorder(context, x, y, w, h, 8.0f * scale,
            1.0f * scale, 1.4f * scale, 1.4f * scale,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(20.0f + 41.0f * hover), 1.0f));
        LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), label, x + w * 0.5f,
            y + h * 0.5f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, 1.0f));
    }

    private void drawJoinButton(DrawContext context, float x, float y, float w, float h,
                                float hover, String label, float scale) {
        int fill = LinkenMenuChrome.mix(LinkenMenuChrome.ACCENT, 0xFFFFFF, hover * 0.07f);
        RoundedRectPipeline.draw(context, x, y, w, h, 8.0f * scale,
            LinkenMenuChrome.rgba(fill, 1.0f));
        context.fill(Math.round(x + 8.0f), Math.round(y + 1.0f),
            Math.round(x + w - 8.0f), Math.round(y + 2.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 89, 1.0f));
        IconPipeline.draw(context, LinkenFonts.iconsFa(), ICON_ARROW_RIGHT, x + 31.5f,
            y + h * 0.5f, 18.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.INK, 1.0f));
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), label, x + 50.0f,
            y + h * 0.5f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.INK, 1.0f));
    }

    // ------------------------------------------------------------------ modal

    private void drawModal(DrawContext context, LinkenMenuLayout.Metrics metrics, float mx, float my,
                           float fade, float seconds) {
        float width = 460.0f;
        float height = modalHeight();
        float x = (metrics.width() - width) * 0.5f;
        float y = (1080.0f - height) * 0.5f;
        modalBounds = new LinkenMenuLayout.Rect(x, y, width, height);
        windowMaterial(context, x, y, width, height, metrics.scale());

        String title = switch (modal) {
            case ADD -> I18n.get("linkenvisuals.multiplayer.add_title");
            case EDIT -> I18n.get("linkenvisuals.multiplayer.edit_title");
            case DIRECT -> I18n.get("linkenvisuals.multiplayer.direct_title");
            case DELETE -> I18n.get("linkenvisuals.multiplayer.delete_title");
            case NONE -> "";
        };
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), title, x + 22.0f, y + 22.0f,
            17.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));

        nameFieldRect = null;
        addressFieldRect = null;
        modalCloseRect = null;
        packRects = new LinkenMenuLayout.Rect[3];
        if (modal == Modal.DELETE) {
            LinkenMenuChrome.drawText(context, LinkenFonts.body(),
                I18n.get("linkenvisuals.multiplayer.delete_prompt"), x + 22.0f, y + 66.0f,
                12.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
            String target = selected >= 0 && selected < visible.size() ? cleanName(visible.get(selected)) : "";
            LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), target, x + 22.0f, y + 96.0f,
                13.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT2, fade));
        } else {
            if (modal == Modal.DIRECT) {
                LinkenMenuChrome.drawText(context, LinkenFonts.body(),
                    I18n.get("linkenvisuals.multiplayer.direct_hint"), x + 22.0f, y + 62.0f,
                    12.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
            }
            float cy = y + (modal == Modal.DIRECT ? 96.0f : 72.0f);
            if (modal != Modal.DIRECT) {
                nameFieldRect = new LinkenMenuLayout.Rect(x + 22.0f, cy + 17.0f, width - 44.0f, 36.0f);
                drawField(context, nameFieldRect, I18n.get("linkenvisuals.multiplayer.name"), nameInput,
                    focusedField == 0, fade, metrics.scale());
                cy += 69.0f;
            }
            addressFieldRect = new LinkenMenuLayout.Rect(x + 22.0f, cy + 17.0f, width - 44.0f, 36.0f);
            drawField(context, addressFieldRect, I18n.get("linkenvisuals.multiplayer.address"), addressInput,
                focusedField == 1, fade, metrics.scale());
            if (modal != Modal.DIRECT) {
                cy += 69.0f;
                LinkenMenuChrome.drawText(context, LinkenFonts.body(),
                    I18n.get("linkenvisuals.multiplayer.resource_pack"), x + 22.0f, cy, 11.0f,
                    LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED3, fade));
                String[] options = {
                    I18n.get("linkenvisuals.multiplayer.pack_disabled"),
                    I18n.get("linkenvisuals.multiplayer.pack_prompt"),
                    I18n.get("linkenvisuals.multiplayer.pack_enabled")
                };
                float gap = 6.0f;
                float segWidth = (width - 44.0f - gap * 2.0f) / 3.0f;
                for (int i = 0; i < 3; i++) {
                    float sx = x + 22.0f + i * (segWidth + gap);
                    boolean active = packPolicy == i;
                    float hover = !active && new LinkenMenuLayout.Rect(sx, cy + 17.0f, segWidth, 34.0f)
                        .contains(mx, my) ? 1.0f : 0.0f;
                    if (active) {
                        RoundedRectPipeline.draw(context, sx, cy + 17.0f, segWidth, 34.0f,
                            7.0f * metrics.scale(), LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
                        context.fill(Math.round(sx + 7.0f), Math.round(cy + 18.0f),
                            Math.round(sx + segWidth - 7.0f), Math.round(cy + 19.0f),
                            LinkenMenuChrome.rgba(0xFFFFFF, 89, fade));
                    } else {
                        RoundedRectPipeline.draw(context, sx, cy + 17.0f, segWidth, 34.0f,
                            7.0f * metrics.scale(), LinkenMenuChrome.rgba(0xFFFFFF,
                                Math.round(8.0f + 8.0f * hover), fade));
                        RoundedRectPipeline.drawBorder(context, sx, cy + 17.0f, segWidth, 34.0f,
                            7.0f * metrics.scale(), 1.0f * metrics.scale(),
                            1.4f * metrics.scale(), 1.4f * metrics.scale(),
                            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(20.0f + 36.0f * hover), fade));
                    }
                    LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), options[i],
                        sx + segWidth * 0.5f, cy + 34.0f, 12.0f, LinkenMenuChrome.rgba(
                            active ? LinkenMenuChrome.INK : LinkenMenuChrome.MUTED2, fade));
                    packRects[i] = new LinkenMenuLayout.Rect(sx, cy + 17.0f, segWidth, 34.0f);
                }
            }
        }

        if (modalError != null) {
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), modalError, x + 22.0f,
                y + height - 64.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.DANGER, fade));
        }

        String cancel = I18n.get("linkenvisuals.multiplayer.cancel");
        String primary = switch (modal) {
            case ADD -> I18n.get("linkenvisuals.multiplayer.add");
            case DIRECT -> I18n.get("linkenvisuals.multiplayer.connect");
            case DELETE -> I18n.get("linkenvisuals.multiplayer.delete_confirm");
            default -> I18n.get("linkenvisuals.multiplayer.save");
        };
        float primaryWidth = 24.0f
            + LinkenMenuChrome.textWidth(LinkenFonts.heading(), primary, 11.0f) + 24.0f;
        float cancelWidth = 18.0f
            + LinkenMenuChrome.textWidth(LinkenFonts.heading(), cancel, 11.0f) + 18.0f;
        float by = y + height - 22.0f - 44.0f;
        modalConfirmRect = new LinkenMenuLayout.Rect(x + width - 22.0f - primaryWidth, by, primaryWidth, 44.0f);
        modalCancelRect = new LinkenMenuLayout.Rect(modalConfirmRect.x() - 9.0f - cancelWidth, by, cancelWidth, 44.0f);
        drawModalButton(context, modalCancelRect, cancel, false, mx, my, fade, metrics.scale());
        drawModalButton(context, modalConfirmRect, primary, true, mx, my, fade, metrics.scale());
        WindowMaterial.corners(context, x, y, width, height, metrics.scale(), fade);
    }

    private float modalHeight() {
        return switch (modal) {
            case DIRECT -> 239.0f;
            case DELETE -> 200.0f;
            default -> 337.0f;
        };
    }

    private void drawField(DrawContext context, LinkenMenuLayout.Rect rect, String label,
                           TextInput input, boolean focused, float fade, float scale) {
        String value = input.value();
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), label, rect.x(), rect.y() - 17.0f,
            11.0f, LinkenMenuChrome.rgba(
                focused ? LinkenMenuChrome.ACCENT : LinkenMenuChrome.MUTED3, fade));
        fieldBox(context, rect.x(), rect.y(), rect.width(), rect.height(), focused ? 1.0f : 0.0f,
            false, scale);
        if (focused) {
            RoundedRectPipeline.drawBorder(context, rect.x() - 0.5f, rect.y() - 0.5f,
                rect.width() + 1.0f, rect.height() + 1.0f, 7.0f * scale,
                1.0f * scale, 1.4f * scale, 1.4f * scale,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, 107, fade));
        }
        String shown = value.isBlank() ? (focused ? "" : I18n.get("linkenvisuals.multiplayer.type_here")) : value;
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), shown, rect.x() + 13.0f, rect.y() + 12.0f,
            12.5f, LinkenMenuChrome.rgba(value.isBlank() && !focused
                ? LinkenMenuChrome.MUTED3 : LinkenMenuChrome.TEXT2, fade));
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            String beforeCaret = value.substring(0, Math.min(input.caret(), value.length()));
            float caretX = rect.x() + 13.0f
                + LinkenMenuChrome.textWidth(LinkenFonts.body(), beforeCaret, 12.5f);
            context.fill(Math.round(caretX), Math.round(rect.y() + 12.0f),
                Math.round(caretX + 1.5f), Math.round(rect.y() + 24.0f),
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));
        }
    }

    private void drawModalButton(DrawContext context, LinkenMenuLayout.Rect rect, String label,
                                 boolean primary, float mx, float my, float fade, float scale) {
        float hover = rect.contains(mx, my) ? 1.0f : 0.0f;
        if (primary) {
            RoundedRectPipeline.draw(context, rect.x(), rect.y() - hover, rect.width(), rect.height(),
                7.0f * scale, LinkenMenuChrome.rgba(LinkenMenuChrome.mix(
                    LinkenMenuChrome.ACCENT, 0xFFFFFF, hover * 0.07f), fade));
        } else {
            LinkenMenuChrome.drawSecondary(context, rect.x(), rect.y() - hover, rect.width(),
                rect.height(), 7.0f, hover, false, fade, scale);
        }
        LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), label,
            rect.x() + rect.width() * 0.5f, rect.y() + rect.height() * 0.5f - hover, 9.5f,
            LinkenMenuChrome.rgba(primary ? LinkenMenuChrome.INK : LinkenMenuChrome.TEXT3, fade));
    }

    // ------------------------------------------------------------------ input

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
        if (changeRect != null && changeRect.contains(mx, my)) {
            playClick();
            client.setScreen(new LinkenAltManagerScreen(this));
            return true;
        }
        if (searchRect != null && searchRect.contains(mx, my)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (refreshRect != null && refreshRect.contains(mx, my)) {
            playClick();
            refreshServers();
            return true;
        }
        if (joinRect != null && joinRect.contains(mx, my)) {
            playClick();
            joinSelected();
            return true;
        }
        if ((addRect != null && addRect.contains(mx, my))
            || (addEmptyRect != null && addEmptyRect.contains(mx, my))) {
            playClick();
            openModal(Modal.ADD);
            return true;
        }
        if (directRect != null && directRect.contains(mx, my)) {
            playClick();
            openModal(Modal.DIRECT);
            return true;
        }
        if (editRect != null && editRect.contains(mx, my)) {
            if (selected >= 0) {
                playClick();
                openModal(Modal.EDIT);
            }
            return true;
        }
        if (deleteRect != null && deleteRect.contains(mx, my)) {
            if (selected >= 0) {
                playClick();
                openModal(Modal.DELETE);
            }
            return true;
        }
        for (int i = 0; i < rowRects.size(); i++) {
            LinkenMenuLayout.Rect rect = rowRects.get(i);
            if (rect == null || !rect.contains(mx, my)) {
                continue;
            }
            long now = Util.getMeasuringTimeMs();
            boolean doubleClickRow = i == lastClickIndex && now - lastClickMs < 300L;
            selected = i;
            lastClickIndex = i;
            lastClickMs = now;
            if (doubleClickRow) {
                joinSelected();
            } else {
                playClick();
            }
            return true;
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
            playClick();
            confirmModal();
            return;
        }
        if (nameFieldRect != null && nameFieldRect.contains(mx, my)) {
            focusedField = 0;
            return;
        }
        if (addressFieldRect != null && addressFieldRect.contains(mx, my)) {
            focusedField = 1;
            return;
        }
        for (int i = 0; i < packRects.length; i++) {
            if (packRects[i] != null && packRects[i].contains(mx, my)) {
                packPolicy = i;
                playClick();
                return;
            }
        }
        if (modalBounds == null || !modalBounds.contains(mx, my)) {
            closeModal();
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (modal != Modal.NONE && focusedField >= 0) {
            TextInput target = focusedField == 0 ? nameInput : addressInput;
            return target.charTyped(input);
        }
        if (searchFocused) {
            boolean handled = query.charTyped(input);
            if (handled) {
                rebuildVisible();
            }
            return handled;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (modal != Modal.NONE) {
            if (focusedField >= 0) {
                TextInput target = focusedField == 0 ? nameInput : addressInput;
                if (target.keyPressed(input)) {
                    return true;
                }
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                playClick();
                closeModal();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                playClick();
                confirmModal();
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                if (nameFieldRect != null) {
                    focusedField = focusedField == 0 ? 1 : 0;
                }
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (searchFocused) {
            if (query.keyPressed(input)) {
                rebuildVisible();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            joinSelected();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            refreshServers();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            if (!visible.isEmpty()) {
                selected = Math.min(visible.size() - 1, selected + 1);
                ensureSelectedVisible();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            if (!visible.isEmpty()) {
                selected = Math.max(0, selected - 1);
                ensureSelectedVisible();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        if (modal != Modal.NONE) {
            return true;
        }
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget - (float) verticalAmount * ROW_H * 0.8f,
            Math.max(0.0f, visible.size() * ROW_H - listHeight())));
        return true;
    }

    private void ensureSelectedVisible() {
        if (selected < 0) {
            return;
        }
        float top = selected * ROW_H;
        if (top < scrollTarget) {
            scrollTarget = top;
        } else if (top + ROW_H > scrollTarget + listHeight()) {
            scrollTarget = top + ROW_H - listHeight();
        }
    }

    // ------------------------------------------------------------------ modal actions

    private void openModal(Modal type) {
        modal = type;
        modalError = null;
        focusedField = type == Modal.DIRECT ? 1 : 0;
        if (type == Modal.ADD) {
            nameInput.clear();
            addressInput.clear();
        } else if (type == Modal.EDIT && selected >= 0 && selected < visible.size()) {
            ServerInfo info = visible.get(selected);
            nameInput.setValue(info.name == null ? "" : info.name);
            addressInput.setValue(info.address == null ? "" : info.address);
            packPolicy = switch (info.getResourcePackPolicy()) {
                case DISABLED -> 0;
                case ENABLED -> 2;
                default -> 1;
            };
        } else if (type == Modal.DIRECT) {
            addressInput.clear();
            nameInput.clear();
            packPolicy = 1;
        } else {
            packPolicy = 1;
        }
    }

    private void closeModal() {
        modal = Modal.NONE;
        focusedField = -1;
        modalError = null;
    }

    private void confirmModal() {
        if (modal == Modal.DELETE) {
            if (selected >= 0 && selected < visible.size() && serverList != null) {
                serverList.remove(visible.get(selected));
                serverList.saveFile();
                selected = -1;
                closeModal();
                refreshServers();
            }
            return;
        }
        String address = addressInput.value().trim();
        if (address.isEmpty() || !ServerAddress.isValid(address)) {
            modalError = I18n.get("linkenvisuals.multiplayer.invalid_address");
            focusedField = 1;
            return;
        }
        if (modal == Modal.DIRECT) {
            closeModal();
            ServerInfo info = new ServerInfo(address, address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(this, client, ServerAddress.parse(address), info, false, null);
            return;
        }
        String name = nameInput.value().trim().isEmpty() ? address : nameInput.value().trim();
        if (modal == Modal.ADD && serverList != null) {
            ServerInfo info = new ServerInfo(name, address, ServerInfo.ServerType.OTHER);
            info.setResourcePackPolicy(policyFor(packPolicy));
            serverList.add(info, false);
            serverList.saveFile();
            closeModal();
            refreshServers();
            return;
        }
        if (modal == Modal.EDIT && serverList != null && selected >= 0 && selected < visible.size()) {
            ServerInfo info = visible.get(selected);
            info.name = name;
            info.address = address;
            info.setResourcePackPolicy(policyFor(packPolicy));
            serverList.saveFile();
            closeModal();
            refreshServers();
        }
    }

    private static ServerInfo.ResourcePackPolicy policyFor(int index) {
        return switch (index) {
            case 0 -> ServerInfo.ResourcePackPolicy.DISABLED;
            case 2 -> ServerInfo.ResourcePackPolicy.ENABLED;
            default -> ServerInfo.ResourcePackPolicy.PROMPT;
        };
    }

    private void joinSelected() {
        if (selected < 0 || selected >= visible.size() || client == null) {
            return;
        }
        ServerInfo info = visible.get(selected);
        if (info.address == null || !ServerAddress.isValid(info.address)) {
            return;
        }
        ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null);
    }

    private Identifier favicon(ServerInfo info) {
        if (faviconIds.containsKey(info)) {
            return faviconIds.get(info);
        }
        if (faviconTried.contains(info)) {
            return null;
        }
        faviconTried.add(info);
        byte[] data = info.getFavicon();
        if (data == null || client == null) {
            return null;
        }
        try {
            NativeImage image = NativeImage.read(data);
            roundIconCorners(image);
            Identifier id = LinkenVisuals.id("dynamic/server_icon_" + faviconSerial++);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
            client.getTextureManager().registerTexture(id, texture);
            faviconTextures.add(texture);
            faviconIds.put(info, id);
            return id;
        } catch (Exception failure) {
            LinkenVisuals.LOGGER.warn("[Multiplayer] bad favicon for {}", info.address);
            return null;
        }
    }

    /** Autotest hook: renders the donor Add Server form without mouse simulation. */
    public void openAddModalForTest() {
        openModal(Modal.ADD);
    }

    /** Donor favicons are drawn as 32×32 with a 6 px radius; mask the source once on upload. */
    private static void roundIconCorners(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;
        float radius = Math.min(width, height) * 6.0f / 32.0f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float dx = Math.abs(x + 0.5f - halfWidth) - halfWidth + radius;
                float dy = Math.abs(y + 0.5f - halfHeight) - halfHeight + radius;
                float distance = (float) (Math.hypot(Math.max(dx, 0.0f), Math.max(dy, 0.0f)))
                    + Math.min(Math.max(dx, dy), 0.0f) - radius;
                if (distance > 0.5f) {
                    image.setColorArgb(x, y, image.getColorArgb(x, y) & 0x00FFFFFF);
                }
            }
        }
    }

    // ------------------------------------------------------------------ donor row helpers

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Za-z]");
    private static final Pattern AD_PAREN = Pattern.compile(
        "(?i)\\s*[\\(\\[](?=[^\\)\\]]*(?:erid|\\d+\\+))[^\\)\\]]*[\\)\\]]");
    private static final Pattern ERID = Pattern.compile("(?i)\\berid[:=]?\\S*");
    private static final Pattern VERSION = Pattern.compile("1\\.(\\d{1,2})(?:\\.\\d{1,2})?");

    private static String stripFormatting(String value) {
        if (value == null) {
            return "";
        }
        return FORMATTING.matcher(value).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private static String cleanName(ServerInfo info) {
        String name = stripFormatting(info.name == null ? "" : info.name);
        name = AD_PAREN.matcher(name).replaceAll("");
        name = ERID.matcher(name).replaceAll("").trim();
        name = name.replaceAll("[\\s,;·|]+$", "").trim();
        if (name.isEmpty()) {
            name = info.address == null ? "" : info.address;
        }
        return name;
    }

    private static String motdText(ServerInfo info) {
        return stripFormatting(info.label == null ? "" : info.label.getString().replace('\n', ' '));
    }

    private static String versionChip(ServerInfo info) {
        String raw = stripFormatting(info.version == null ? "" : info.version.getString());
        if (raw.isEmpty()) {
            return "";
        }
        Matcher matcher = VERSION.matcher(raw);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        while (matcher.find()) {
            int minor = Integer.parseInt(matcher.group(1));
            low = Math.min(low, minor);
            high = Math.max(high, minor);
        }
        if (high < 0) {
            return raw.length() > 12 ? raw.substring(0, 12) : raw;
        }
        return low == high ? "1." + low : "1." + low + "–" + "1." + high;
    }

    private static String playersText(ServerInfo info) {
        if (info.players == null) {
            return "—";
        }
        return group(info.players.online()) + " / " + group(info.players.max());
    }

    private static String group(int value) {
        String digits = Integer.toString(Math.max(0, value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    private static int pingBars(ServerInfo info) {
        if (info.getStatus() != ServerInfo.Status.SUCCESSFUL) {
            return 0;
        }
        long ping = info.ping;
        if (ping < 80) {
            return 4;
        }
        if (ping <= 120) {
            return 3;
        }
        if (ping <= 200) {
            return 2;
        }
        return 1;
    }

    private static String pingText(ServerInfo info) {
        return info.getStatus() == ServerInfo.Status.SUCCESSFUL
            ? info.ping + " " + I18n.get("linkenvisuals.multiplayer.ms") : "";
    }

    private static int tileTint(ServerInfo info) {
        String source = info.address == null ? (info.name == null ? "?" : info.name) : info.address;
        int hash = Math.abs(source.hashCode());
        return Theme.hsvToRgb((hash % 360) / 360.0f, 0.42f, 0.20f);
    }

    private static int letterColor(ServerInfo info) {
        String source = info.address == null ? (info.name == null ? "?" : info.name) : info.address;
        int hash = Math.abs(source.hashCode());
        return Theme.hsvToRgb((hash % 360) / 360.0f, 0.45f, 0.80f);
    }

    private static int darker(int rgb) {
        return ColorMath.mix(rgb, 0x000000, 0.38f);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void close() {
        pinger.cancel();
        if (client != null) {
            Screen target = parent;
            if (target == null || target == this) {
                target = new LinkenMainMenuScreen();
            }
            client.setScreen(target);
        }
    }

    @Override
    public void removed() {
        pinger.cancel();
        for (NativeImageBackedTexture texture : faviconTextures) {
            try {
                texture.close();
            } catch (RuntimeException failure) {
                LinkenVisuals.LOGGER.debug("[Multiplayer] favicon close failed: {}", failure.toString());
            }
        }
        faviconTextures.clear();
        faviconIds.clear();
        faviconTried.clear();
        super.removed();
    }

    private void playClick() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(LinkenSounds.UI_CLICK, 0.8f, 1.0f));
        }
    }
}
