package dev.linkenvisuals.clickgui.link;

import dev.linkenvisuals.audio.LinkenSounds;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.clickgui.Category;
import dev.linkenvisuals.clickgui.ConfigManager;
import dev.linkenvisuals.clickgui.KeyNames;
import dev.linkenvisuals.clickgui.ModuleInfo;
import dev.linkenvisuals.clickgui.ModuleRegistry;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.clickgui.link.LinkGuiLayout.Metrics;
import dev.linkenvisuals.clickgui.link.LinkGuiLayout.Rect;
import dev.linkenvisuals.clickgui.setting.BooleanSetting;
import dev.linkenvisuals.clickgui.setting.ColorSetting;
import dev.linkenvisuals.clickgui.setting.KeybindSetting;
import dev.linkenvisuals.clickgui.setting.ModeSetting;
import dev.linkenvisuals.clickgui.setting.Setting;
import dev.linkenvisuals.clickgui.setting.SliderSetting;
import dev.linkenvisuals.clickgui.setting.StringSetting;
import dev.linkenvisuals.hud.HudManager;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.media.MediaWatcher;
import dev.linkenvisuals.render.CirclePipeline;
import dev.linkenvisuals.render.ColorFieldPipeline;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.IconPipeline;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.PanelPipeline;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.ui.AnimatedString;
import dev.linkenvisuals.ui.Spring;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import dev.linkenvisuals.ui.linkmenu.LinkenMenuChrome;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import dev.linkenvisuals.waypoint.Waypoint;
import dev.linkenvisuals.waypoint.WaypointStorage;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * LINKEN ClickGUI, ported from the donor {@code screen/linkgui} layout: a
 * 1090x830 main window with a three-column module grid, a bottom category
 * dock, a callable profile card and a callable Theme/Interface panel. It
 * consumes the same ModuleRegistry/Setting model as the Classic and Beta
 * shells, so modules and configs stay in sync.
 */
public final class LinkGuiScreen extends Screen {
    @Override
    public void renderBackground(net.minecraft.client.gui.DrawContext context, int mouseX,
                                 int mouseY, float delta) {
        if (ModuleRegistry.interfaceBlur()) {
            super.renderBackground(context, mouseX, mouseY, delta);
        } else {
            context.fill(0, 0, width, height, ColorMath.withAlpha(0x000000, 150));
        }
    }

    private static final Identifier BACKGROUND = LinkenVisuals.id("textures/linkmenu/bg-tower.png");
    private static final float BACKGROUND_WIDTH = 1270.0f;
    private static final float BACKGROUND_HEIGHT = 999.0f;
    private final Screen returnScreen;

    private static Category selected = Category.VISUAL;
    private static ModuleInfo focused;
    private static final dev.linkenvisuals.util.input.TextInput search =
        new dev.linkenvisuals.util.input.TextInput().maxLength(64);
    private static final dev.linkenvisuals.util.input.TextInput configName =
        new dev.linkenvisuals.util.input.TextInput().maxLength(32);
    private static float scrollTarget;
    private static float panelScrollTarget;
    private static float configScrollTarget;
    private static float interfaceScrollTarget;
    private static boolean configInputFocused;

    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring scrollSpring = new Spring(0.0, 1100.0, 60.0);
    private final Spring panelScrollSpring = new Spring(0.0, 1100.0, 60.0);
    private final Spring configScrollSpring = new Spring(0.0, 1100.0, 60.0);
    private final Spring interfaceScrollSpring = new Spring(0.0, 1100.0, 60.0);
    private final Map<ModuleInfo, Spring> cardHover = new HashMap<>();
    private final Spring closeHover = new Spring(0.0, 900.0, 50.0);
    private final Spring profileSpring = new Spring(1.0, 900.0, 52.0);
    private final Spring themeSpring = new Spring(1.0, 900.0, 52.0);
    private final Spring sideSpring = new Spring(0.0, 900.0, 52.0);
    private final Spring settingsSpring = new Spring(0.0, 900.0, 52.0);
    private final Map<String, float[]> gridPos = new HashMap<>();
    private final Map<String, ExitCard> gridExit = new HashMap<>();
    private final Map<String, Float> gridAlpha = new HashMap<>();
    private final LinkGuiCursorTrail cursorTrail = new LinkGuiCursorTrail();
    private final MusicPlayerStrip music = new MusicPlayerStrip();

    private long lastFrame = System.nanoTime();
    private boolean closing;
    private String status;
    private long statusUntil;
    private int statusColor = LinkGuiTheme.ACCENT;
    private SkinTextures skin;

    private float contentHeight;
    private float contentView;
    private float panelContentHeight;
    private float panelView;
    private float configContentHeight;
    private float configView;
    private float searchCaret;
    private boolean searchFocused;
    private boolean searchAll;
    private long lastTyped;

    private final List<CardHit> cardHits = new ArrayList<>();
    private final List<SettingHit> interfaceHits = new ArrayList<>();
    private final List<SettingHit> settingHits = new ArrayList<>();
    private final List<SettingHit> pinnedSettingHits = new ArrayList<>();
    private final List<PinHit> pinHits = new ArrayList<>();
    private final List<NavHit> navHits = new ArrayList<>();
    private final List<ConfigHit> configHits = new ArrayList<>();
    private Rect closeHit;
    private final List<ThemeHit> themeHits = new ArrayList<>();
    private Rect searchHit;
    private float interfaceContentHeight;
    private float interfaceViewHeight;
    private Rect allHit;
    private Rect headerToggleHit;
    private Rect moduleBindHit;
    private boolean settingsOpen;
    private boolean settingsDragging;
    private boolean profileOpen = true;
    private boolean themeOpen = true;
    private SidePanel sidePanel = SidePanel.NONE;
    private final dev.linkenvisuals.util.input.TextInput sideInput =
        new dev.linkenvisuals.util.input.TextInput().maxLength(64);
    private boolean sideInputFocused;
    private Rect sideCloseHit;
    private Rect sideAddHit;
    private Rect sideInputHit;
    private final List<SideRemoveHit> sideRemoveHits = new ArrayList<>();
    private final List<UtilityHit> utilityHits = new ArrayList<>();
    private Rect profileCloseHit;
    private Rect headerSkinHit;
    private Rect themeToggleHit;
    private float settingsX = Float.NaN;
    private float settingsY = Float.NaN;
    private float settingsDragX;
    private float settingsDragY;
    private Rect settingsCloseHit;
    private Rect settingsHeaderHit;
    private Rect configInputHit;
    private Rect configCreateHit;
    private Rect configFolderHit;
    private Rect configRefreshHit;
    private Rect configAutoHit;

    private SliderSetting draggingSlider;
    private float dragTrackX;
    private float dragTrackWidth;
    private float activeSliderRaw;
    private ModeSetting openMode;
    private Rect openModeBox;
    private ModeSetting lastOpenMode;
    private Rect lastOpenModeBox;
    private float enumddOpen;
    private final AnimatedString searchTextAnim = new AnimatedString(150L, "");
    private final Map<String, AnimatedString> valueAnims = new HashMap<>();
    private final Map<String, Float> smoothNumbers = new HashMap<>();
    private final Map<String, Float> smoothFractions = new HashMap<>();
    private final Map<String, Float> sliderGrabs = new HashMap<>();
    private final List<OptionHit> optionHits = new ArrayList<>();
    private ColorSetting openColor;
    private boolean draggingColor;
    private boolean draggingHue;
    private float pickerX;
    private float pickerY;
    private float pickerFieldSize;

    public LinkGuiScreen() {
        this(null);
    }

    public LinkGuiScreen(Screen returnScreen) {
        super(Text.empty());
        this.returnScreen = returnScreen;
        ensureState();
    }

    private static void ensureState() {
        if (focused != null) {
            return;
        }
        List<ModuleInfo> modules = ModuleRegistry.byCategory(selected);
        focused = modules.isEmpty() ? null : modules.get(0);
    }

    @Override
    protected void init() {
        ModuleRegistry.syncFromMenuConfig();
        LinkGuiTheme.apply(LinkGuiConfig.theme());
        ensureState();
        skin = loadSkin();
        cursorTrail.clear();
        profileOpen = true;
        themeOpen = true;
        closing = false;
        MediaWatcher.ensureRunning();
        music.init();
        lastFrame = System.nanoTime();
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(LinkenVisuals.OPEN_SOUND, 1.0f, 0.86f));
        }
    }

    private SkinTextures loadSkin() {
        if (client == null || client.getGameProfile() == null) {
            return null;
        }
        try {
            Supplier<SkinTextures> lookup = client.getSkinProvider()
                .supplySkinTextures(client.getGameProfile(), false);
            SkinTextures textures = lookup.get();
            if (textures != null) {
                return textures;
            }
        } catch (RuntimeException unavailable) {
            LinkenVisuals.LOGGER.debug("[Visuals Modification] ClickGUI skin lookup failed: {}", unavailable.getMessage());
        }
        try {
            return DefaultSkinHelper.getSkinTextures(client.getGameProfile());
        } catch (RuntimeException unavailable) {
            LinkenVisuals.LOGGER.debug("[Visuals Modification] ClickGUI default skin failed: {}", unavailable.getMessage());
            return null;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (closing) {
            return;
        }
        if (ModuleRegistry.configAutoSave() != null && ModuleRegistry.configAutoSave().value()) {
            ConfigManager.save();
        }
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(LinkenVisuals.BACK_SOUND, 1.0f, 0.9f));
        }
        music.shutdown();
        closing = true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float deltaSeconds = Math.min((now - lastFrame) / 1_000_000_000.0f, 0.05f);
        lastFrame = now;
        LinkGuiTheme.refreshCustom();
        LinkGuiTheme.tick(deltaSeconds);

        Metrics metrics = LinkGuiLayout.compute(width, height, ModuleRegistry.interfaceScale());
        openSpring.step(closing ? 0.0 : 1.0, deltaSeconds);
        float openT = (float) openSpring.get();
        if (closing && openT <= 0.01f) {
            if (client != null) {
                client.setScreen(returnScreen);
            }
            return;
        }

        float mx = (float) (mouseX - metrics.offsetX()) / metrics.scale();
        float my = (float) (mouseY - metrics.offsetY()) / metrics.scale();

        float uiFade = openT * ModuleRegistry.interfaceTransparency();
        drawBackdrop(context, metrics, uiFade);
        profileSpring.step(profileOpen ? 1.0 : 0.0, deltaSeconds);
        themeSpring.step(themeOpen && sidePanel == SidePanel.NONE ? 1.0 : 0.0, deltaSeconds);
        sideSpring.step(sidePanel == SidePanel.NONE ? 0.0 : 1.0, deltaSeconds);
        settingsSpring.step(settingsOpen && focused != null ? 1.0 : 0.0, deltaSeconds);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(metrics.offsetX(), metrics.offsetY());
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        context.getMatrices().translate(0.0f, (1.0f - openT) * 90.0f);
        if (selected == Category.CONFIG) {
            drawConfigPanel(context, metrics, mx, my, uiFade, deltaSeconds);
            drawHeader(context, metrics, mx, my, uiFade, deltaSeconds);
        } else {
            drawModulePanel(context, metrics, mx, my, uiFade, deltaSeconds);
        }
        drawNav(context, metrics, mx, my, uiFade, deltaSeconds);
        float profileT = (float) profileSpring.get();
        if (profileT > 0.01f) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(-(1.0f - profileT) * 26.0f, 0.0f);
            drawProfilePanel(context, metrics, mx, my, uiFade * profileT, deltaSeconds);
            context.getMatrices().popMatrix();
        } else {
            profileCloseHit = null;
        }
        float themeT = (float) themeSpring.get();
        float sideT = (float) sideSpring.get();
        if (themeT > 0.01f) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((1.0f - themeT) * 24.0f, 0.0f);
            drawThemePanel(context, metrics, mx, my, uiFade * themeT);
            context.getMatrices().popMatrix();
        } else {
            themeHits.clear();
            interfaceHits.clear();
        }
        if (sideT > 0.01f) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((1.0f - sideT) * 24.0f, 0.0f);
            drawSidePanel(context, metrics, mx, my, uiFade * sideT);
            context.getMatrices().popMatrix();
        } else {
            sideCloseHit = null;
            sideAddHit = null;
            sideInputHit = null;
            sideRemoveHits.clear();
        }
        float settingsT = (float) settingsSpring.get();
        if (settingsT > 0.01f) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, (1.0f - settingsT) * 18.0f);
            drawSettingsPanel(context, metrics, mx, my, uiFade * settingsT, deltaSeconds);
            context.getMatrices().popMatrix();
        } else {
            settingsCloseHit = null;
            settingsHeaderHit = null;
            settingHits.clear();
            pinnedSettingHits.clear();
        }
        drawPinnedCards(context, metrics, mx, my, uiFade, deltaSeconds);
        drawPopups(context, metrics, mx, my, uiFade, deltaSeconds);
        if (ModuleRegistry.cursorTracker()) {
            cursorTrail.update(mx, my);
            cursorTrail.render(context);
        } else {
            cursorTrail.clear();
        }
        context.getMatrices().popMatrix();
    }

    // ------------------------------------------------------------------ backdrop

    private void drawBackdrop(DrawContext context, Metrics metrics, float openT) {
        if (client != null && client.world != null) {
            if (ModuleRegistry.interfaceBlur()) {
                try {
                    context.applyBlur();
                } catch (RuntimeException unavailable) {
                    LinkenVisuals.LOGGER.debug("[Visuals Modification] ClickGUI blur unavailable: {}", unavailable.getMessage());
                }
            }
            context.fill(0, 0, width, height,
                LinkenMenuChrome.rgba(LinkGuiTheme.isLight() ? 0xE9EDF2 : 0x050706,
                    LinkGuiTheme.isLight() ? 170 : 150, openT));
            GlowPipeline.radial(context, width * 0.5f, height * 0.42f,
                560.0f * metrics.scale(), LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 8, openT));
            return;
        }
        if (LinkGuiTheme.isLight()) {
            context.fillGradient(0, 0, width, height,
                LinkenMenuChrome.rgba(LinkGuiTheme.BACKDROP_TOP, openT),
                LinkenMenuChrome.rgba(LinkGuiTheme.BACKDROP_BOTTOM, openT));
            return;
        }
        float cover = Math.max(width / BACKGROUND_WIDTH, height / BACKGROUND_HEIGHT) * 1.05f;
        float drawWidth = BACKGROUND_WIDTH * cover;
        float drawHeight = BACKGROUND_HEIGHT * cover;
        float x = (width - drawWidth) * 0.5f;
        float y = (height - drawHeight) * 0.5f;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND,
            Math.round(x), Math.round(y), 0.0f, 0.0f,
            Math.round(drawWidth), Math.round(drawHeight),
            Math.round(BACKGROUND_WIDTH), Math.round(BACKGROUND_HEIGHT),
            Math.round(BACKGROUND_WIDTH), Math.round(BACKGROUND_HEIGHT),
            LinkenMenuChrome.rgba(0xFFFFFF, 58, openT));
        context.fill(0, 0, width, height, LinkenMenuChrome.rgba(0x030504, 232, openT));
    }

    // ------------------------------------------------------------------ header

    private void drawHeader(DrawContext context, Metrics metrics, float mx, float my,
                            float openT, float deltaSeconds) {
        Rect main = metrics.center();
        float centerY = main.y() + 31.0f;
        float bx = main.x() + 20.0f;

        GlyphAtlas logo = LinkenFonts.bodyMedium();
        float logoSize = 16.0f;
        float tracking = -0.56f;
        float lvSize = 24.0f;
        LinkenMenuChrome.drawLogo(context, bx, centerY - lvSize * 0.5f, lvSize, openT);
        float wordX = bx + lvSize + 9.0f;
        LinkenMenuChrome.drawSpaced(context, logo, "linken", wordX, centerY - 8.0f,
            logoSize, tracking, LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));
        float wordWidth = LinkenMenuChrome.textWidth(logo, "linken", logoSize) + tracking * 5.0f;
        float dotWidth = LinkenMenuChrome.textWidth(logo, ".", logoSize);
        LinkenMenuChrome.drawText(context, logo, ".", wordX + wordWidth, centerY - 8.0f,
            logoSize, LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));

        float cursorX = wordX + wordWidth + dotWidth + 12.0f;
        context.fill(Math.round(cursorX), Math.round(centerY - 11.0f),
            Math.round(cursorX + 1.0f), Math.round(centerY + 11.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 26, openT));
        cursorX += 13.0f;

        headerSkinHit = new Rect(cursorX, centerY - 17.0f, 34.0f, 34.0f);
        boolean skinHover = headerSkinHit.contains(mx, my);
        LinkenMenuChrome.roundedFill(context, cursorX, centerY - 17.0f, 34.0f, 34.0f, 7.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ROW, 230, openT), metrics.scale());
        if (profileOpen || skinHover) {
            LinkenMenuChrome.roundedOutline(context, cursorX, centerY - 17.0f, 34.0f, 34.0f, 7.0f, 1.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, profileOpen ? 120 : 80, openT), metrics.scale());
        }
        if (skin != null) {
            PlayerSkinDrawer.draw(context, skin, Math.round(cursorX + 2.0f), Math.round(centerY - 15.0f), 30);
        }
        cursorX += 34.0f + 12.0f;

        String allLabel = I18n.get("gui.header.all");
        float allW = LinkenMenuChrome.textWidth(LinkenFonts.heading(), allLabel, 10.5f) + 28.0f;
        Rect all = new Rect(cursorX, centerY - 17.0f, allW, 34.0f);
        allHit = all;
        boolean allHover = all.contains(mx, my);
        LinkenMenuChrome.roundedFill(context, all.x(), all.y(), all.width(), all.height(), 7.0f,
            LinkenMenuChrome.rgba(searchAll ? LinkGuiTheme.ACCENT
                : LinkenMenuChrome.mix(LinkGuiTheme.FIELD, LinkGuiTheme.HOVER, allHover ? 0.6f : 0.0f),
                searchAll ? 235 : 205, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, all.x(), all.y(), all.width(), all.height(), 7.0f, 1.0f,
            LinkenMenuChrome.rgba(searchAll ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                searchAll ? 180 : (allHover ? 52 : 26), openT), metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), allLabel,
            all.x() + all.width() * 0.5f, all.y() + 16.0f, 10.5f,
            LinkenMenuChrome.rgba(searchAll ? LinkGuiTheme.INK : LinkGuiTheme.TEXT3, openT));
        cursorX += allW + 12.0f;

        float closeX = main.right() - 20.0f - 34.0f;
        float slidersX = closeX - 8.0f - 34.0f;
        float searchWidth = Math.max(120.0f, slidersX - 8.0f - cursorX);
        Rect searchRect = new Rect(cursorX, centerY - 17.0f, searchWidth, 34.0f);
        searchHit = searchRect;
        boolean searchOver = searchRect.contains(mx, my);
        LinkenMenuChrome.roundedFill(context, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(),
            7.0f, LinkenMenuChrome.rgba(LinkGuiTheme.FIELD, 205, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(),
            7.0f, 0.9f, LinkenMenuChrome.rgba(searchFocused ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                searchFocused ? 150 : (searchOver ? 52 : 24), openT), metrics.scale());
        if (LinkenFonts.icons() != null) {
            IconPipeline.draw(context, LinkenFonts.iconsFa(), 0xF002, searchRect.x() + 15.0f, centerY, 13.0f,
                LinkenMenuChrome.rgba(searchFocused ? LinkGuiTheme.ACCENT : LinkGuiTheme.MUTED, openT));
        }
        String query = search.value();
        searchTextAnim.update(query);
        GlyphAtlas searchFont = LinkenFonts.body();
        String placeholder = I18n.get("gui.search.placeholder");
        if (query.isEmpty() && !searchFocused && !searchTextAnim.isAnimating()) {
            LinkenMenuChrome.drawOn(context, searchFont, placeholder,
                searchRect.x() + 30.0f, centerY, 12.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
        } else {
            drawSearchText(context, searchFont, searchRect.x() + 30.0f, centerY, 12.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));
            if (searchFocused) {
                String beforeCaret = query.substring(0, Math.min(search.caret(), query.length()));
                float caret = searchRect.x() + 30.0f
                    + LinkenMenuChrome.textWidth(searchFont, beforeCaret, 12.5f);
                float blink = (System.currentTimeMillis() - lastTyped) < 700L
                    ? 1.0f : 0.35f + 0.65f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 1000.0 * 3.1));
                context.fill(Math.round(caret), Math.round(centerY - 7.0f),
                    Math.round(caret + 1.2f), Math.round(centerY + 7.0f),
                    LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, Math.round(220 * blink * openT)));
            }
        }

        themeToggleHit = new Rect(slidersX, centerY - 17.0f, 34.0f, 34.0f);
        boolean slidersHover = themeToggleHit.contains(mx, my);
        LinkenMenuChrome.roundedFill(context, slidersX, centerY - 17.0f, 34.0f, 34.0f, 7.0f,
            LinkenMenuChrome.rgba(themeOpen ? LinkGuiTheme.ACTIVE : LinkGuiTheme.FIELD, 220, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, slidersX, centerY - 17.0f, 34.0f, 34.0f, 7.0f, 1.0f,
            LinkenMenuChrome.rgba(themeOpen ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                themeOpen ? 100 : (slidersHover ? 52 : 24), openT), metrics.scale());
        int sliderColor = LinkenMenuChrome.rgba(themeOpen ? LinkGuiTheme.ACCENT : LinkGuiTheme.TEXT3, openT);
        for (int i = 0; i < 3; i++) {
            float ly = centerY - 6.0f + i * 6.0f;
            context.fill(Math.round(slidersX + 10.0f), Math.round(ly),
                Math.round(slidersX + 24.0f), Math.round(ly + 1.2f), sliderColor);
            CirclePipeline.draw(context, slidersX + 12.0f + i * 5.0f, ly + 0.6f, 2.2f, sliderColor);
        }

        Rect close = new Rect(closeX, centerY - 17.0f, 34.0f, 34.0f);
        closeHit = close;
        boolean closeOver = close.contains(mx, my);
        closeHover.step(closeOver ? 1.0 : 0.0, deltaSeconds);
        float hover = (float) closeHover.get();
        LinkenMenuChrome.roundedFill(context, close.x(), close.y(), close.width(), close.height(), 7.0f,
            LinkenMenuChrome.rgba(closeOver ? 0x2A1A1A : LinkGuiTheme.FIELD, 220, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, close.x(), close.y(), close.width(), close.height(), 7.0f, 1.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0xFFFFFF, LinkGuiTheme.DANGER, hover),
                Math.round(24 + 130 * hover), openT), metrics.scale());
        int color = LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkGuiTheme.TEXT3, LinkGuiTheme.DANGER, hover), openT);
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(close.x() + 17.0f, close.y() + 17.0f);
        matrices.rotate((float) Math.toRadians(45.0));
        PanelPipeline.draw(context, -7.0f, -0.7f, 14.0f, 1.4f, color);
        matrices.rotate((float) Math.toRadians(90.0));
        PanelPipeline.draw(context, -7.0f, -0.7f, 14.0f, 1.4f, color);
        matrices.popMatrix();

        context.fill(Math.round(main.x()), Math.round(main.y() + LinkGuiLayout.HEADER_HEIGHT),
            Math.round(main.right()), Math.round(main.y() + LinkGuiLayout.HEADER_HEIGHT + 1.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 18, openT));
    }

    // ------------------------------------------------------------------ navigation rail

    private void drawNav(DrawContext context, Metrics metrics, float mx, float my,
                         float openT, float deltaSeconds) {
        Category[] categories = {Category.COMBAT, Category.MOVEMENT, Category.VISUAL,
            Category.PLAYER, Category.MISC};
        float tile = LinkGuiLayout.DOCK_TILE;
        float gap = LinkGuiLayout.DOCK_GAP;
        String[] utilityLabels = {
            I18n.get("gui.dock.configs"), I18n.get("gui.dock.friends"),
            I18n.get("gui.dock.waypoints"), I18n.get("gui.dock.elements")
        };
        boolean[] utilityActive = {
            selected == Category.CONFIG && sidePanel == SidePanel.NONE,
            sidePanel == SidePanel.FRIENDS,
            sidePanel == SidePanel.WAYPOINTS,
            false
        };
        float labelSize = 10.5f;
        float total = 0.0f;
        for (Category category : categories) {
            boolean active = category == selected && sidePanel == SidePanel.NONE && !searchAll;
            if (active) {
                total += 12.0f + LinkenMenuChrome.textWidth(LinkenFonts.heading(),
                    I18n.get("gui.category." + category.name().toLowerCase(Locale.ROOT)), labelSize) + 18.0f;
            }
            total += tile + gap;
        }
        for (int i = 0; i < utilityLabels.length; i++) {
            if (utilityActive[i]) {
                total += 12.0f + LinkenMenuChrome.textWidth(LinkenFonts.heading(), utilityLabels[i], labelSize) + 18.0f;
            }
            total += tile + gap;
        }
        total -= gap;

        float x = metrics.center().x() + (metrics.center().width() - total) * 0.5f;
        float y = metrics.height() - LinkGuiLayout.DOCK_BOTTOM - LinkGuiLayout.DOCK_HEIGHT;
        navHits.clear();
        utilityHits.clear();

        for (Category category : categories) {
            boolean active = category == selected && sidePanel == SidePanel.NONE && !searchAll;
            String label = I18n.get("gui.category." + category.name().toLowerCase(Locale.ROOT));
            float width = tile + (active
                ? 12.0f + LinkenMenuChrome.textWidth(LinkenFonts.heading(), label, labelSize) + 18.0f : 0.0f);
            Rect item = new Rect(x, y, width, tile);
            boolean hovered = item.contains(mx, my);
            drawDockTile(context, metrics, item, label, active, hovered, openT, deltaSeconds,
                null, categoryGlyph(category));
            navHits.add(new NavHit(category, item));
            x += width + gap;
        }

        int[] utilityGlyphs = {0xF013, 0xF0C0, 0xF3C5, 0xF1DE};
        Runnable[] utilityActions = {
            () -> {
                sidePanel = SidePanel.NONE;
                themeOpen = false;
                selectCategory(Category.CONFIG);
            },
            () -> {
                sidePanel = sidePanel == SidePanel.FRIENDS ? SidePanel.NONE : SidePanel.FRIENDS;
                themeOpen = false;
                sideInput.clear();
                sideInputFocused = false;
                playClick();
            },
            () -> {
                sidePanel = sidePanel == SidePanel.WAYPOINTS ? SidePanel.NONE : SidePanel.WAYPOINTS;
                themeOpen = false;
                sideInput.clear();
                sideInputFocused = false;
                playClick();
            },
            () -> {
                if (client != null) {
                    HudManager.setEditing(true);
                    client.setScreen(new ChatScreen("", false));
                }
            }
        };
        for (int i = 0; i < utilityLabels.length; i++) {
            String label = utilityLabels[i];
            boolean active = utilityActive[i];
            float width = tile + (active
                ? 12.0f + LinkenMenuChrome.textWidth(LinkenFonts.heading(), label, labelSize) + 18.0f : 0.0f);
            Rect item = new Rect(x, y, width, tile);
            boolean hovered = item.contains(mx, my);
            drawDockTile(context, metrics, item, label, active, hovered, openT, deltaSeconds,
                null, utilityGlyphs[i]);
            utilityHits.add(new UtilityHit(utilityActions[i], item));
            x += width + gap;
        }
    }

    private void drawDockTile(DrawContext context, Metrics metrics, Rect item, String label,
                              boolean active, boolean hovered, float openT, float deltaSeconds,
                              Identifier texture, int glyph) {
        float hover = hovered && !active ? 0.55f : 0.0f;
        float lift = hovered && !active ? 2.0f : 0.0f;
        if (active) {
            GlowPipeline.radial(context, item.x() + item.width() * 0.5f, item.y() + item.height() * 0.5f - 3.0f,
                Math.max(item.width(), item.height()) * 0.7f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 54, openT));
            LinkenMenuChrome.roundedFill(context, item.x(), item.y() - lift, item.width(), item.height(), 8.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 255, openT), metrics.scale());
        } else {
            LinkenMenuChrome.roundedFill(context, item.x(), item.y() - lift, item.width(), item.height(), 8.0f,
                LinkenMenuChrome.rgba(hovered ? LinkGuiTheme.HOVER : LinkGuiTheme.PANEL,
                    Math.round((210 + 18 * hover) * openT)), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, item.x(), item.y() - lift, item.width(), item.height(), 8.0f,
                1.0f, LinkenMenuChrome.rgba(0xFFFFFF, Math.round((18 + 34 * hover) * openT)), metrics.scale());
        }
        float iconSize = 17.0f;
        float iconX = item.x() + 18.0f;
        float iconY = item.y() + item.height() * 0.5f - lift;
        int iconColor = active ? LinkGuiTheme.INK
            : LinkenMenuChrome.mix(LinkGuiTheme.MUTED, LinkGuiTheme.TEXT2, hover);
        if (texture != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                Math.round(iconX), Math.round(iconY - iconSize * 0.5f), 0.0f, 0.0f,
                Math.round(iconSize), Math.round(iconSize), 48, 48, 48, 48,
                LinkenMenuChrome.rgba(iconColor, openT));
        } else {
            GlyphAtlas iconAtlas = glyph >= 0xF000 ? LinkenFonts.iconsFa() : LinkenFonts.iconsNew();
            if (iconAtlas != null) {
                IconPipeline.draw(context, iconAtlas, glyph, iconX + iconSize * 0.5f, iconY,
                    iconSize, LinkenMenuChrome.rgba(iconColor, openT));
            }
        }
        if (active) {
            LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), label,
                iconX + iconSize + 12.0f, iconY, 10.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.INK, openT));
        }
    }

    // ------------------------------------------------------------------ module grid

    private void drawModulePanel(DrawContext context, Metrics metrics, float mx, float my,
                                 float openT, float deltaSeconds) {
        Rect panel = metrics.center();
        LinkenMenuChrome.roundedFill(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL, 245, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 23, openT), metrics.scale());
        drawHeader(context, metrics, mx, my, openT, deltaSeconds);

        List<ModuleInfo> modules = visibleModules();
        Rect area = metrics.cards();
        float contentHeight = Math.max(0.0f, (float) Math.ceil(modules.size() / (float) LinkGuiLayout.CARD_COLUMNS)
            * (LinkGuiLayout.CARD_HEIGHT + LinkGuiLayout.CARD_GAP) - LinkGuiLayout.CARD_GAP);
        this.contentHeight = contentHeight;
        contentView = area.height();
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget, Math.max(0.0f, contentHeight - area.height())));
        scrollSpring.step(scrollTarget, deltaSeconds);
        float scroll = (float) scrollSpring.get();

        context.enableScissor(Math.round(area.x() - 14.0f), Math.round(area.y() - 16.0f),
            Math.round(area.right() + 14.0f), Math.round(area.bottom() + 16.0f));
        cardHits.clear();
        if (modules.isEmpty()) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), I18n.get("gui.empty.category_title"),
                area.x() + area.width() * 0.5f, area.y() + area.height() * 0.42f, 16.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), I18n.get("gui.empty.category_subtitle"),
                area.x() + area.width() * 0.5f, area.y() + area.height() * 0.42f + 28.0f, 11.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
        }
        updateGridAnimation(modules, metrics, deltaSeconds);
        drawExitingCards(context, metrics, area, scroll, openT, deltaSeconds);
        for (int i = 0; i < modules.size(); i++) {
            ModuleInfo module = modules.get(i);
            float[] pos = gridPos.get(module.name());
            if (pos == null) {
                continue;
            }
            Rect card = new Rect(pos[0], pos[1] - scroll,
                cardWidth(metrics), LinkGuiLayout.CARD_HEIGHT);
            float alpha = gridAlpha.getOrDefault(module.name(), 1.0f);
            if (alpha < 0.05f) {
                continue;
            }
            if (card.bottom() < area.y() || card.y() > area.bottom()) {
                cardHits.add(new CardHit(module, card, false, null));
                continue;
            }
            boolean hovered = card.contains(mx, my);
            Spring spring = cardHover.computeIfAbsent(module, key -> new Spring(0.0, 900.0, 55.0));
            spring.step(hovered ? 1.0 : 0.0, deltaSeconds);
            drawModuleCard(context, metrics, module, card, (float) spring.get(), openT * alpha);
            Rect more = LinkGuiLayout.cardMetrics(card).more();
            cardHits.add(new CardHit(module, card, true, more));
        }
        context.disableScissor();
        scrollbar(context, metrics, area.right() - 0.0f, area.y(), area.height(), contentHeight, scroll, openT);
        drawMediaStrip(context, metrics, panel, mx, my, openT, deltaSeconds);
        dev.linkenvisuals.ui.linkmenu.WindowMaterial.corners(context, panel.x(), panel.y(), panel.width(), panel.height(), metrics.scale(), openT);
    }

    private float cardWidth(Metrics metrics) {
        return (metrics.cards().width() - LinkGuiLayout.CARD_GAP * (LinkGuiLayout.CARD_COLUMNS - 1))
            / LinkGuiLayout.CARD_COLUMNS;
    }

    /** Donor-style grid reflow: cards move to new slots and fade instead of jumping. */
    private void updateGridAnimation(List<ModuleInfo> modules, Metrics metrics, float deltaSeconds) {
        Set<String> visible = new HashSet<>();
        float moveK = 1.0f - (float) Math.exp(-18.0f * deltaSeconds);
        float fadeK = 1.0f - (float) Math.exp(-14.0f * deltaSeconds);
        for (int i = 0; i < modules.size(); i++) {
            ModuleInfo module = modules.get(i);
            String name = module.name();
            visible.add(name);
            Rect base = LinkGuiLayout.card(metrics, i);
            float[] pos = gridPos.computeIfAbsent(name, key -> new float[]{base.x(), base.y()});
            pos[0] += (base.x() - pos[0]) * moveK;
            pos[1] += (base.y() - pos[1]) * moveK;
            float alpha = gridAlpha.getOrDefault(name, 0.0f);
            alpha += (1.0f - alpha) * fadeK;
            gridAlpha.put(name, alpha);
        }
        Iterator<Map.Entry<String, float[]>> iterator = gridPos.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, float[]> entry = iterator.next();
            if (visible.contains(entry.getKey())) {
                continue;
            }
            ModuleInfo module = moduleByName(entry.getKey());
            if (module != null) {
                float[] pos = entry.getValue();
                gridExit.put(entry.getKey(), new ExitCard(module, pos[0], pos[1],
                    gridAlpha.getOrDefault(entry.getKey(), 1.0f)));
            }
            iterator.remove();
            gridAlpha.remove(entry.getKey());
        }
    }

    private static ModuleInfo moduleByName(String name) {
        for (ModuleInfo module : ModuleRegistry.all()) {
            if (module.name().equals(name)) {
                return module;
            }
        }
        return null;
    }

    private void drawExitingCards(DrawContext context, Metrics metrics, Rect area, float scroll,
                                  float openT, float deltaSeconds) {
        Iterator<Map.Entry<String, ExitCard>> iterator = gridExit.entrySet().iterator();
        while (iterator.hasNext()) {
            ExitCard exit = iterator.next().getValue();
            exit.alpha -= deltaSeconds * 3.6f;
            if (exit.alpha <= 0.01f) {
                iterator.remove();
                continue;
            }
            Rect card = new Rect(exit.x, exit.y - scroll, cardWidth(metrics), LinkGuiLayout.CARD_HEIGHT);
            if (card.bottom() < area.y() || card.y() > area.bottom()) {
                continue;
            }
            drawModuleCard(context, metrics, exit.module, card, 0.0f, openT * exit.alpha);
        }
    }

    private void drawMediaStrip(DrawContext context, Metrics metrics, Rect panel, float mx, float my,
                                float openT, float deltaSeconds) {
        music.setBounds(panel.x() + 20.0f, panel.bottom() - 92.0f, panel.width() - 40.0f, 84.0f);
        music.render(context, mx, my, openT, deltaSeconds, metrics.scale());
    }

    private static int categoryGlyph(Category category) {
        return switch (category) {
            case COMBAT -> 'P';
            case MOVEMENT -> 'S';
            case VISUAL -> 'R';
            case PLAYER -> 'V';
            case MISC -> 'H';
            case CONFIG -> 0xF013;
        };
    }

    private static void drawCategoryGlyph(DrawContext context, Category category,
                                          float centerX, float centerY, float size, int color) {
        int glyph = categoryGlyph(category);
        GlyphAtlas atlas = glyph >= 0xF000 ? LinkenFonts.iconsFa() : LinkenFonts.iconsNew();
        if (atlas != null) {
            IconPipeline.draw(context, atlas, glyph, centerX, centerY, size, color);
        }
    }

    private void drawModuleCard(DrawContext context, Metrics metrics, ModuleInfo module, Rect card,
                                float hover, float openT) {
        boolean on = module.enabled();
        module.enableGlow.step(on ? 1.0 : 0.0, 0.05);
        float onT = (float) module.enableGlow.get();
        LinkGuiLayout.CardMetrics cm = LinkGuiLayout.cardMetrics(card);

        if (onT > 0.02f) {
            LinkenMenuChrome.roundedOutline(context, card.x(), card.y(), card.width(), card.height(),
                LinkGuiLayout.CARD_RADIUS, 1.4f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, Math.round(54 * onT * openT)), metrics.scale());
        }
        int fill = on ? LinkGuiTheme.ACTIVE : LinkGuiTheme.ROW;
        LinkenMenuChrome.roundedFill(context, card.x(), card.y(), card.width(), card.height(),
            LinkGuiLayout.CARD_RADIUS,
            LinkenMenuChrome.rgba(fill, Math.round((205 + hover * 28) * openT)), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, card.x(), card.y(), card.width(), card.height(),
            LinkGuiLayout.CARD_RADIUS, 1.0f,
            LinkenMenuChrome.rgba(on ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                Math.round((on ? 62 : 20 + hover * 34) * openT)), metrics.scale());

        float iconSize = LinkGuiLayout.CARD_ICON_SIZE;
        drawCategoryGlyph(context, module.category(),
            cm.icon().x() + iconSize * 0.5f, cm.icon().y() + iconSize * 0.5f, iconSize,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkGuiTheme.MUTED, LinkGuiTheme.ACCENT, onT), openT));

        LinkenMenuChrome.drawOn(context, LinkenFonts.headingLight(),
            LinkenMenuChrome.ellipsize(LinkenFonts.headingLight(), module.name(),
                cm.title().width(), 11.5f),
            cm.title().x(), cm.title().y() + cm.title().height() * 0.5f, 11.5f,
            LinkenMenuChrome.rgba(on ? LinkGuiTheme.TEXT : LinkGuiTheme.TEXT2, openT));

        List<String> lines = wrapTwoLines(LinkenFonts.body(), module.description(),
            cm.description().width(), 11.0f);
        for (int i = 0; i < lines.size(); i++) {
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), lines.get(i),
                cm.description().x(), cm.description().y() + i * 15.0f, 11.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        }

        boolean listening = module.binding();
        String bind = listening ? "..." : (module.hasKeybind() ? module.keybind() : "NONE");
        String bindId = "cardbind:" + module.name();
        AnimatedString bindAnim = valueAnims.computeIfAbsent(bindId, key -> new AnimatedString(160L, bind));
        bindAnim.update(bind);
        float bindTextW = LinkenMenuChrome.textWidth(LinkenFonts.mono(), bind, 8.0f);
        if (bindAnim.isAnimating()) {
            bindTextW = Math.max(bindTextW,
                LinkenMenuChrome.textWidth(LinkenFonts.mono(), bindAnim.previous(), 8.0f));
        }
        float bindW = Math.min(cm.bind().width(), Math.max(44.0f, bindTextW + 26.0f));
        Rect bindRect = new Rect(cm.bind().x(), cm.bind().y(), bindW, cm.bind().height());
        LinkenMenuChrome.roundedFill(context, bindRect.x(), bindRect.y(), bindRect.width(), bindRect.height(),
            5.0f, LinkenMenuChrome.rgba(0xFFFFFF, 16, openT), metrics.scale());
        LinkenMenuChrome.roundedFill(context, bindRect.x() + 1.5f, bindRect.bottom() - 2.0f,
            bindRect.width() - 3.0f, 2.0f, 1.0f, LinkenMenuChrome.rgba(0xFFFFFF, 40, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, bindRect.x(), bindRect.y(), bindRect.width(), bindRect.height(),
            5.0f, 0.8f, LinkenMenuChrome.rgba(listening ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                listening ? 160 : 28, openT), metrics.scale());
        drawMorphValue(context, LinkenFonts.mono(), bindId, bind,
            bindRect.x() + 9.0f, MsdfText.topFor(bindRect.y() + bindRect.height() * 0.5f, 8.0f), 8.0f,
            LinkenMenuChrome.rgba(listening ? LinkGuiTheme.ACCENT : LinkGuiTheme.MUTED, openT));

        drawSwitch(context, cm.toggle().x(), cm.toggle().y(),
            cm.toggle().width(), cm.toggle().height(), onT, openT, metrics.scale());

        float dotX = cm.more().x() + cm.more().width() * 0.5f;
        float dotY = cm.more().y() + cm.more().height() * 0.5f;
        for (int i = -1; i <= 1; i++) {
            CirclePipeline.draw(context, dotX + i * 5.0f, dotY, 1.6f,
                LinkenMenuChrome.rgba(hover > 0.3f ? LinkGuiTheme.TEXT2 : LinkGuiTheme.MUTED3, openT));
        }
    }

    // ------------------------------------------------------------------ settings

    private ModuleInfo clickGuiModule() {
        for (ModuleInfo module : ModuleRegistry.byCategory(Category.VISUAL)) {
            if ("ClickGUI".equalsIgnoreCase(module.name())) {
                return module;
            }
        }
        return null;
    }

    private Rect settingsWindowRect(Metrics metrics) {
        float w = 258.0f;
        float h = settingsWindowHeight();
        if (Float.isNaN(settingsX) || Float.isNaN(settingsY)) {
            settingsX = metrics.center().x() + 60.0f;
            settingsY = metrics.center().y() + 80.0f;
        }
        settingsX = Math.max(20.0f, Math.min(settingsX, metrics.width() - w - 20.0f));
        settingsY = Math.max(20.0f, Math.min(settingsY, metrics.height() - h - 20.0f));
        return new Rect(settingsX, settingsY, w, h);
    }

    private float settingsWindowHeight() {
        if (focused == null) {
            return 120.0f;
        }
        List<Setting> settings = new ArrayList<>(focused.leftSettings());
        settings.addAll(focused.rightSettings());
        if (ModuleRegistry.isGuiStyleLocked()) {
            settings.removeIf(setting -> "GUI Style".equalsIgnoreCase(setting.name()));
        }
        float content = 0.0f;
        for (Setting setting : settings) {
            content += rowHeight(setting) + 6.0f;
        }
        return Math.max(300.0f, Math.min(440.0f, 88.0f + content + 12.0f));
    }

    private void openSettingsWindow(ModuleInfo module, Rect card) {
        focused = module;
        settingsOpen = true;
        settingsX = card.x() + 40.0f;
        settingsY = card.y() + 20.0f;
        panelScrollTarget = 0.0f;
    }    private void drawProfilePanel(DrawContext context, Metrics metrics, float mx, float my,
                                  float openT, float deltaSeconds) {
        Rect panel = metrics.profile();
        GlowPipeline.radial(context, panel.x() + panel.width() * 0.5f, panel.y() + 40.0f,
            panel.width() * 0.7f, LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 24, openT));
        LinkenMenuChrome.roundedFill(context, panel.x(), panel.y(), panel.width(), panel.height(), 8.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL, 245, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, panel.x(), panel.y(), panel.width(), panel.height(), 8.0f,
            1.0f, LinkenMenuChrome.rgba(0xFFFFFF, 24, openT), metrics.scale());

        float cx = panel.x() + 16.0f;
        float cy = panel.y() + 16.0f;
        float cw = panel.width() - 32.0f;
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), I18n.get("gui.profile.title"),
            cx + 18.0f, cy + 6.0f, 13.0f, LinkenMenuChrome.rgba(LinkGuiTheme.TEXT2, openT));
        if (LinkenFonts.icons() != null) {
            IconPipeline.draw(context, LinkenFonts.iconsFa(), 0xF007,
                cx + 6.0f, cy + 6.0f, 12.0f, LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
        }
        context.fill(Math.round(cx + 100.0f), Math.round(cy + 6.0f),
            Math.round(cx + cw), Math.round(cy + 7.0f), LinkenMenuChrome.rgba(0xFFFFFF, 18, openT));
        profileCloseHit = new Rect(panel.right() - 30.0f, panel.y() + 8.0f, 22.0f, 22.0f);
        boolean closeHover = profileCloseHit.contains(mx, my);
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "X",
            profileCloseHit.x() + 11.0f, profileCloseHit.y() + 6.0f, 9.0f,
            LinkenMenuChrome.rgba(closeHover ? LinkGuiTheme.DANGER : LinkGuiTheme.MUTED2, openT));

        float rowY = cy + 24.0f;
        LinkenMenuChrome.roundedFill(context, cx, rowY, 40.0f, 40.0f, 8.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ROW, 230, openT), metrics.scale());
        if (skin != null) {
            PlayerSkinDrawer.draw(context, skin, Math.round(cx + 2.0f), Math.round(rowY + 2.0f), 36);
        }
        String nick = LinkenMenuChrome.username();
        LinkenMenuChrome.drawText(context, LinkenFonts.heading(), nick, cx + 52.0f, rowY + 3.0f, 13.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), I18n.get("gui.profile.plan"),
            cx + 52.0f, rowY + 25.0f, 11.5f, LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), "Free",
            cx + 52.0f + LinkenMenuChrome.textWidth(LinkenFonts.body(),
                I18n.get("gui.profile.plan"), 11.5f), rowY + 25.0f, 11.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));

        float viewerY = rowY + 52.0f;
        float viewerH = 238.0f;
        LinkenMenuChrome.roundedFill(context, cx, viewerY, cw, viewerH, 8.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.BACKDROP_BOTTOM, 180, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, cx, viewerY, cw, viewerH, 8.0f, 0.8f,
            LinkenMenuChrome.rgba(0xFFFFFF, 16, openT), metrics.scale());
        LinkenMenuChrome.roundedFill(context, cx, viewerY + viewerH - 72.0f, cw, 72.0f, 8.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 32, openT), metrics.scale());
        if (client != null && client.player != null) {
            float s = metrics.scale();
            int ex = Math.round(metrics.offsetX() + (cx + 16.0f) * s);
            int ey = Math.round(metrics.offsetY() + (viewerY + 12.0f) * s);
            int ew = Math.round((cw - 32.0f) * s);
            int eh = Math.round((viewerH - 22.0f) * s);
            int size = Math.max(2, Math.round(eh / 3.2f));
            context.enableScissor(Math.round(cx + 16.0f), Math.round(viewerY + 12.0f),
                Math.round(cx + cw - 16.0f), Math.round(viewerY + viewerH - 10.0f));
            net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(context, ex, ey,
                ex + ew, ey + eh, size, 0.0625f, ex + ew * 0.5f, ey + eh * 0.80f, client.player);
            context.disableScissor();
        } else if (skin != null) {
            float preview = 96.0f;
            float previewX = cx + (cw - preview) * 0.5f;
            float previewY = viewerY + (viewerH - preview) * 0.5f + 8.0f;
            PlayerSkinDrawer.draw(context, skin, Math.round(previewX), Math.round(previewY),
                Math.round(preview));
        } else {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), "No player preview",
                cx + cw * 0.5f, viewerY + viewerH * 0.5f, 11.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        }
        context.fill(Math.round(cx + 24.0f), Math.round(viewerY + viewerH - 12.0f),
            Math.round(cx + cw - 24.0f), Math.round(viewerY + viewerH - 11.0f),
            LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 90, openT));
    }

    private void drawSettingsPanel(DrawContext context, Metrics metrics, float mx, float my,
                                   float openT, float deltaSeconds) {
        if (focused == null) {
            return;
        }
        Rect panel = settingsWindowRect(metrics);
        LinkenMenuChrome.roundedFill(context, panel.x(), panel.y(), panel.width(), panel.height(), 10.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL, 248, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, panel.x(), panel.y(), panel.width(), panel.height(), 10.0f,
            1.0f, LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 70, openT), metrics.scale());
        settingsHeaderHit = new Rect(panel.x(), panel.y(), panel.width() - 34.0f, 32.0f);
        settingsCloseHit = new Rect(panel.right() - 28.0f, panel.y() + 6.0f, 20.0f, 20.0f);
        boolean closeHover = settingsCloseHit.contains(mx, my);
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "X",
            settingsCloseHit.x() + 10.0f, settingsCloseHit.y() + 5.0f, 9.0f,
            LinkenMenuChrome.rgba(closeHover ? LinkGuiTheme.DANGER : LinkGuiTheme.MUTED2, openT));

        LinkenMenuChrome.drawOn(context, LinkenFonts.bodyMedium(),
            LinkenMenuChrome.ellipsize(LinkenFonts.bodyMedium(), focused.name(), panel.width() - 80.0f, 12.5f),
            panel.x() + 12.0f, panel.y() + 15.0f, 12.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));

        float toggleW = 32.0f;
        float toggleH = 18.0f;
        float toggleX = panel.right() - 40.0f;
        float toggleY = panel.y() + 7.0f;
        focused.enableGlow.step(focused.enabled() ? 1.0 : 0.0, deltaSeconds);
        drawSwitch(context, toggleX, toggleY, toggleW, toggleH, (float) focused.enableGlow.get(),
            openT, metrics.scale());
        headerToggleHit = new Rect(toggleX - 4.0f, toggleY - 4.0f, toggleW + 8.0f, toggleH + 8.0f);

        context.fill(Math.round(panel.x()), Math.round(panel.y() + 32.0f),
            Math.round(panel.right()), Math.round(panel.y() + 33.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 18, openT));

        Rect bindRow = new Rect(panel.x() + 10.0f, panel.y() + 38.0f, panel.width() - 20.0f, 32.0f);
        drawBindRow(context, metrics, bindRow, mx, my, openT, deltaSeconds);
        moduleBindHit = bindRow;

        Rect area = new Rect(panel.x() + 10.0f, panel.y() + 78.0f,
            panel.width() - 20.0f, panel.height() - 88.0f);
        List<Setting> settings = new ArrayList<>(focused.leftSettings());
        settings.addAll(focused.rightSettings());
        if (ModuleRegistry.isGuiStyleLocked()) {
            settings.removeIf(setting -> "GUI Style".equalsIgnoreCase(setting.name()));
        }
        float contentHeight = 0.0f;
        for (Setting setting : settings) {
            contentHeight += rowHeight(setting) + 6.0f;
        }
        contentHeight = Math.max(0.0f, contentHeight - 6.0f);
        panelContentHeight = contentHeight;
        panelView = area.height();
        panelScrollTarget = Math.max(0.0f, Math.min(panelScrollTarget,
            Math.max(0.0f, contentHeight - area.height())));
        panelScrollSpring.step(panelScrollTarget, deltaSeconds);
        float scroll = (float) panelScrollSpring.get();

        context.enableScissor(Math.round(area.x() - 4.0f), Math.round(area.y()),
            Math.round(area.right() + 4.0f), Math.round(area.bottom()));
        float y = area.y() - scroll;
        settingHits.clear();
        if (settings.isEmpty()) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), "NO SETTINGS",
                area.x() + area.width() * 0.5f, area.y() + area.height() * 0.4f, 11.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
        }
        for (Setting setting : settings) {
            float h = rowHeight(setting);
            Rect row = new Rect(area.x(), y, area.width(), h);
            if (row.bottom() >= area.y() - 6.0f && row.y() <= area.bottom() + 6.0f) {
                drawSetting(context, metrics, setting, row, mx, my, openT, deltaSeconds, focused.name());
                settingHits.add(new SettingHit(setting, row));
            }
            y += h + 6.0f;
        }
        context.disableScissor();
        scrollbar(context, metrics, area.right() + 4.0f, area.y(), area.height(), contentHeight, scroll, openT);
    }

    /**
     * Pinned modules are drawn as compact settings cards outside the main
     * window, matching the donor {@code drawPinnedCard}. The left column starts
     * below the profile card; the right column is available only when the Theme
     * panel is closed, so the two never overlap.
     */
    private void drawPinnedCards(DrawContext context, Metrics metrics, float mx, float my,
                                 float openT, float deltaSeconds) {
        pinHits.clear();
        pinnedSettingHits.clear();
        if (sidePanel != SidePanel.NONE) {
            return;
        }
        List<ModuleInfo> modules = new ArrayList<>();
        for (String name : LinkGuiConfig.pins()) {
            for (ModuleInfo module : ModuleRegistry.all()) {
                if (module.name().equals(name)) {
                    modules.add(module);
                    break;
                }
            }
        }
        if (modules.isEmpty()) {
            return;
        }

        final float bottomLimit = LinkGuiLayout.PINNED_BOTTOM;
        final float gap = LinkGuiLayout.PINNED_GAP;
        final float headerHeight = LinkGuiLayout.PINNED_HEADER;
        float leftY = profileOpen ? metrics.profile().bottom() + 18.0f : metrics.profile().y();
        float rightY = metrics.theme().y();
        boolean leftColumn = true;

        for (ModuleInfo module : modules) {
            float width = LinkGuiLayout.PINNED_WIDTH;
            float x = leftColumn ? metrics.profile().x() : metrics.theme().x();
            float y = leftColumn ? leftY : rightY;
            if ((!leftColumn && themeOpen) || y + 74.0f > bottomLimit) {
                if (!leftColumn) {
                    break;
                }
                leftColumn = false;
                if (themeOpen) {
                    break;
                }
                width = LinkGuiLayout.PINNED_WIDTH;
                x = metrics.theme().x();
                y = rightY;
                if (y + 74.0f > bottomLimit) {
                    break;
                }
            }

            List<Setting> settings = new ArrayList<>(module.leftSettings());
            settings.addAll(module.rightSettings());
            if (ModuleRegistry.isGuiStyleLocked()) {
                settings.removeIf(setting -> "GUI Style".equalsIgnoreCase(setting.name()));
            }
            float available = bottomLimit - y;
            List<Setting> visible = new ArrayList<>();
            float contentHeight = 0.0f;
            for (Setting setting : settings) {
                float h = rowHeight(setting);
                if (headerHeight + 12.0f + contentHeight + h + 10.0f > available) {
                    break;
                }
                visible.add(setting);
                contentHeight += h + 6.0f;
            }
            boolean truncated = visible.size() < settings.size();
            float cardHeight = Math.min(available, headerHeight + 12.0f + contentHeight
                + (truncated ? 18.0f : 0.0f) + 8.0f);

            LinkenMenuChrome.roundedFill(context, x, y, width, cardHeight, 10.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.PANEL, Math.round(242 * openT)), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, x, y, width, cardHeight, 10.0f, 1.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, Math.round(46 * openT)), metrics.scale());
            LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(),
                LinkenMenuChrome.ellipsize(LinkenFonts.bodyMedium(), module.name(), width - 94.0f, 13.0f),
                x + 14.0f, y + 15.0f, 13.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));

            Rect unpin = new Rect(x + width - 34.0f, y + 12.0f, 22.0f, 22.0f);
            Rect toggle = new Rect(unpin.x() - 42.0f, y + 14.0f, 32.0f, 18.0f);
            drawSwitch(context, toggle.x(), toggle.y(), toggle.width(), toggle.height(),
                module.enabled() ? 1.0f : 0.0f, openT, metrics.scale());
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), "x",
                unpin.x() + unpin.width() * 0.5f, unpin.y() + unpin.height() * 0.5f, 11.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
            pinHits.add(new PinHit(module, toggle, unpin));

            context.fill(Math.round(x + 12.0f), Math.round(y + 43.0f),
                Math.round(x + width - 12.0f), Math.round(y + 44.0f),
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(12 * openT)));

            float rowY = y + headerHeight + 6.0f;
            if (visible.isEmpty()) {
                LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), "NO SETTINGS",
                    x + width * 0.5f, rowY + 9.0f, 10.0f,
                    LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
            }
            for (Setting setting : visible) {
                float h = rowHeight(setting);
                Rect row = new Rect(x + 12.0f, rowY, width - 24.0f, h);
                drawSetting(context, metrics, setting, row, mx, my, openT, deltaSeconds, module.name());
                pinnedSettingHits.add(new SettingHit(setting, row));
                rowY += h + 6.0f;
            }
            if (truncated) {
                LinkenMenuChrome.drawCentered(context, LinkenFonts.mono(), "...",
                    x + width * 0.5f, rowY + 4.0f, 9.0f,
                    LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
            }

            if (leftColumn) {
                leftY = y + cardHeight + gap;
                if (leftY + 74.0f > bottomLimit) {
                    leftColumn = false;
                }
            } else {
                rightY = y + cardHeight + gap;
            }
        }
    }

    private void drawBindRow(DrawContext context, Metrics metrics, Rect row, float mx, float my,
                             float openT, float deltaSeconds) {
        boolean hovered = row.contains(mx, my);
        LinkenMenuChrome.roundedFill(context, row.x(), row.y(), row.width(), row.height(), 6.0f,
            LinkenMenuChrome.rgba(hovered ? LinkGuiTheme.HOVER : LinkGuiTheme.ROW,
                Math.round(205 * openT)), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, row.x(), row.y(), row.width(), row.height(), 6.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round((16 + (hovered ? 22 : 0)) * openT)), metrics.scale());
        LinkenMenuChrome.drawOn(context, LinkenFonts.body(), "Keybind",
            row.x() + 9.0f, row.y() + row.height() * 0.5f, 9.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        String label = focused.binding() ? "..." : (focused.hasKeybind() ? focused.keybind() : "NONE");
        String bindId = "bindrow:" + focused.name();
        AnimatedString bindAnim = valueAnims.computeIfAbsent(bindId, key -> new AnimatedString(160L, label));
        bindAnim.update(label);
        float labelW = LinkenMenuChrome.textWidth(LinkenFonts.mono(), label, 8.0f);
        if (bindAnim.isAnimating()) {
            labelW = Math.max(labelW,
                LinkenMenuChrome.textWidth(LinkenFonts.mono(), bindAnim.previous(), 8.0f));
        }
        float chipW = Math.max(48.0f, labelW + 16.0f);
        float chipX = row.right() - 8.0f - chipW;
        LinkenMenuChrome.roundedFill(context, chipX, row.y() + 7.0f, chipW, 18.0f, 4.0f,
            LinkenMenuChrome.rgba(0x000000, 92, openT), metrics.scale());
        drawMorphValueCentered(context, LinkenFonts.mono(), bindId, label,
            chipX + chipW * 0.5f, row.y() + 16.0f, 8.0f,
            LinkenMenuChrome.rgba(focused.binding() ? LinkGuiTheme.ACCENT : LinkGuiTheme.TEXT3, openT));
    }

    /** Donor search crossfade: common prefix stays, the new tail rises while the removed tail fades. */
    private void drawSearchText(DrawContext context, GlyphAtlas font, float x, float centerY,
                                float size, int color) {
        String current = searchTextAnim.current();
        if (!searchTextAnim.isAnimating()) {
            LinkenMenuChrome.drawOn(context, font, current, x, centerY, size, color);
            return;
        }
        String previous = searchTextAnim.previous();
        float p = searchTextAnim.progress();
        int common = 0;
        int limit = Math.min(current.length(), previous.length());
        while (common < limit && current.charAt(common) == previous.charAt(common)) {
            common++;
        }
        String prefix = current.substring(0, common);
        String currentTail = current.substring(common);
        String previousTail = previous.substring(common);
        LinkenMenuChrome.drawOn(context, font, prefix, x, centerY, size, color);
        float tailX = x + LinkenMenuChrome.textWidth(font, prefix, size);
        if (!previousTail.isEmpty()) {
            LinkenMenuChrome.drawOn(context, font, previousTail, tailX, centerY, size,
                fadeColor(color, 1.0f - p));
        }
        if (!currentTail.isEmpty()) {
            LinkenMenuChrome.drawOn(context, font, currentTail, tailX, centerY + (1.0f - p) * 3.0f,
                size, fadeColor(color, p));
        }
    }

    /** Donor drawMorphValue: 160 ms EASE_OUT_CUBIC old/new crossfade. */
    private void drawMorphValue(DrawContext context, GlyphAtlas font, String id, String value,
                                float x, float top, float size, int color) {
        AnimatedString anim = valueAnims.computeIfAbsent(id, key -> new AnimatedString(160L, value));
        anim.update(value);
        if (!anim.isAnimating()) {
            LinkenMenuChrome.drawText(context, font, anim.current(), x, top, size, color);
            return;
        }
        float p = anim.progress();
        LinkenMenuChrome.drawText(context, font, anim.previous(), x, top - p * 2.0f, size,
            fadeColor(color, 1.0f - p));
        LinkenMenuChrome.drawText(context, font, anim.current(), x, top + (1.0f - p) * 2.0f, size,
            fadeColor(color, p));
    }

    /** Same crossfade for the centred keybind chip, where the donor would otherwise shift the text. */
    private void drawMorphValueCentered(DrawContext context, GlyphAtlas font, String id, String value,
                                        float centerX, float centerY, float size, int color) {
        AnimatedString anim = valueAnims.computeIfAbsent(id, key -> new AnimatedString(160L, value));
        anim.update(value);
        if (!anim.isAnimating()) {
            LinkenMenuChrome.drawCentered(context, font, anim.current(), centerX, centerY, size, color);
            return;
        }
        float p = anim.progress();
        LinkenMenuChrome.drawCentered(context, font, anim.previous(), centerX, centerY - p * 2.0f,
            size, fadeColor(color, 1.0f - p));
        LinkenMenuChrome.drawCentered(context, font, anim.current(), centerX, centerY + (1.0f - p) * 2.0f,
            size, fadeColor(color, p));
    }

    private static int fadeColor(int argb, float factor) {
        float clamped = Math.max(0.0f, Math.min(1.0f, factor));
        int alpha = Math.round(((argb >>> 24) & 0xFF) * clamped);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Donor time-based easing: frame-rate independent, snaps once the target is close enough. */
    private static float smoothValue(Map<String, Float> values, String id, float target, float rate,
                                     float snap, float deltaSeconds) {
        float current = values.getOrDefault(id, target);
        float alpha = 1.0f - (float) Math.exp(-rate * Math.max(0.0f, deltaSeconds));
        current += (target - current) * alpha;
        if (Math.abs(target - current) < snap) {
            current = target;
        }
        values.put(id, current);
        return current;
    }

    /** Donor smoothNumStr: the slider badge rolls toward the real value instead of jumping. */
    private String smoothSliderText(String id, SliderSetting slider, float deltaSeconds) {
        float value = smoothValue(smoothNumbers, id, slider.value(), 14.0f,
            Math.max(0.001f, slider.step() * 0.25f), deltaSeconds);
        return slider.format(value);
    }

    private void drawSetting(DrawContext context, Metrics metrics, Setting setting, Rect row,
                             float mx, float my, float openT, float deltaSeconds, String owner) {
        boolean hovered = row.contains(mx, my);
        LinkenMenuChrome.roundedFill(context, row.x(), row.y(), row.width(), row.height(), 7.0f,
            LinkenMenuChrome.rgba(hovered ? LinkGuiTheme.HOVER : LinkGuiTheme.ROW,
                Math.round((198 + (hovered ? 20 : 0)) * openT)), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, row.x(), row.y(), row.width(), row.height(), 7.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round((14 + (hovered ? 20 : 0)) * openT)), metrics.scale());

        float reserve = setting instanceof BooleanSetting ? 54.0f
            : setting instanceof SliderSetting ? 62.0f
            : setting instanceof ModeSetting ? 100.0f
            : setting instanceof ColorSetting ? 100.0f
            : setting instanceof KeybindSetting ? 108.0f
            : 80.0f;
        float labelMax = Math.max(24.0f, row.width() - 32.0f - reserve);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            LinkenMenuChrome.ellipsize(LinkenFonts.body(), setting.name(), labelMax, 11.5f),
            row.x() + 16.0f, row.y() + 12.0f, 11.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.TEXT3, openT));

        if (setting instanceof BooleanSetting bool) {
            bool.glow.step(bool.value() ? 1.0 : 0.0, deltaSeconds);
            float switchW = 38.0f;
            float switchH = 20.0f;
            drawSwitch(context, row.right() - 16.0f - switchW, row.y() + (row.height() - switchH) * 0.5f,
                switchW, switchH, (float) bool.glow.get(), openT, metrics.scale());
        } else if (setting instanceof SliderSetting slider) {
            String sliderId = "sld:" + owner + ":" + setting.name();
            String value = smoothSliderText(sliderId, slider, deltaSeconds);
            LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), value,
                row.right() - 16.0f, row.y() + 13.0f, 10.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
            float trackX = row.x() + 16.0f;
            float trackW = row.width() - 32.0f;
            float trackY = row.bottom() - 23.0f;
            LinkenMenuChrome.roundedFill(context, trackX, trackY, trackW, 3.0f, 1.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.TRACK, openT), metrics.scale());
            float target = Math.max(0.0f, Math.min(1.0f, slider.fraction()));
            boolean active = slider == draggingSlider;
            float position;
            if (active) {
                // Donor: while dragging the knob follows the raw cursor fraction, so it does not
                // jump between quantized steps. On release it eases back to the real value.
                position = Math.max(0.0f, Math.min(1.0f, activeSliderRaw));
                smoothFractions.put(sliderId, position);
            } else {
                position = smoothValue(smoothFractions, sliderId, target, 20.0f, 0.001f, deltaSeconds);
            }
            float fill = Math.max(3.0f, trackW * position);
            LinkenMenuChrome.roundedFill(context, trackX, trackY, fill, 3.0f, 1.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT), metrics.scale());
            float knobX = trackX + trackW * position;
            float grab = smoothValue(sliderGrabs, sliderId, active ? 1.0f : 0.0f, 20.0f, 0.001f, deltaSeconds);
            float knobY = trackY + 1.5f;
            float knobR = 5.0f + 1.7f * grab;
            GlowPipeline.radial(context, knobX, knobY, 10.0f + 4.0f * grab,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, Math.round(42 + 120 * grab), openT));
            if (grab > 0.01f) {
                // Donor drawSliderKnob: an accent halo appears and the knob grows while in use.
                CirclePipeline.draw(context, knobX, knobY, knobR + 2.4f,
                    LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, Math.round(210 * grab), openT));
            }
            CirclePipeline.draw(context, knobX, knobY, knobR,
                LinkenMenuChrome.rgba(0xFFFFFF, openT));
            CirclePipeline.draw(context, knobX, knobY, Math.max(2.0f, knobR - 2.0f),
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
        } else if (setting instanceof ModeSetting mode) {
            String current = mode.current();
            float boxW = Math.min(100.0f, Math.max(78.0f, LinkenMenuChrome.textWidth(LinkenFonts.body(), current, 10.0f) + 28.0f));
            Rect box = new Rect(row.right() - 16.0f - boxW, row.y() + 10.0f, boxW, 24.0f);
            LinkenMenuChrome.roundedFill(context, box.x(), box.y(), box.width(), box.height(), 5.0f,
                LinkenMenuChrome.rgba(0x000000, 90, openT), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, box.x(), box.y(), box.width(), box.height(), 5.0f, 1.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, mode.open() ? 100 : 34, openT), metrics.scale());
            drawMorphValue(context, LinkenFonts.body(), "modev:" + owner + ":" + setting.name(),
                LinkenMenuChrome.ellipsize(LinkenFonts.body(), current, box.width() - 26.0f, 10.0f),
                box.x() + 9.0f, box.y() + 7.0f, 10.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
            drawChevron(context, box.right() - 13.0f, box.y() + box.height() * 0.5f, mode.open(), openT);
            if (mode.open()) {
                openMode = mode;
                openModeBox = box;
                lastOpenMode = mode;
                lastOpenModeBox = box;
            }
            if (row.height() >= 56.0f) {
                String options = mode.options().size() + " OPTIONS";
                LinkenMenuChrome.drawText(context, LinkenFonts.mono(), options,
                    row.x() + 16.0f, row.bottom() - 20.0f, 8.0f,
                    LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
            }
        } else if (setting instanceof ColorSetting color) {
            float chip = 18.0f;
            float chipX = row.right() - 16.0f - chip;
            float chipY = row.y() + (row.height() - chip) * 0.5f;
            CirclePipeline.draw(context, chipX + chip * 0.5f, chipY + chip * 0.5f, chip * 0.5f,
                LinkenMenuChrome.rgba(0xFFFFFF, 40, openT));
            CirclePipeline.draw(context, chipX + chip * 0.5f, chipY + chip * 0.5f, chip * 0.5f - 1.5f,
                LinkenMenuChrome.rgba(color.rgb(), openT));
            String hex = String.format(Locale.ROOT, "#%06X", color.rgb() & 0xFFFFFF);
            LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), hex,
                chipX - 10.0f, row.y() + 13.0f, 9.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        } else if (setting instanceof KeybindSetting keybind) {
            String label = keybind.label();
            float chipW = Math.max(64.0f, LinkenMenuChrome.textWidth(LinkenFonts.mono(), label, 9.0f) + 20.0f);
            float chipX = row.right() - 16.0f - chipW;
            float chipY = row.y() + (row.height() - 22.0f) * 0.5f;
            LinkenMenuChrome.roundedFill(context, chipX, chipY, chipW, 22.0f, 5.0f,
                LinkenMenuChrome.rgba(0x000000, 90, openT), metrics.scale());
            drawMorphValueCentered(context, LinkenFonts.mono(), "keybindv:" + owner + ":" + setting.name(), label,
                chipX + chipW * 0.5f, chipY + 11.0f, 9.0f,
                LinkenMenuChrome.rgba(keybind.listening() ? LinkGuiTheme.ACCENT : LinkGuiTheme.TEXT3, openT));
        } else if (setting instanceof StringSetting string) {
            LinkenMenuChrome.drawRightText(context, LinkenFonts.body(), string.value(),
                row.right() - 16.0f, row.y() + 12.0f, 10.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        }

        // Hover highlight edge.
        if (hovered) {
            LinkenMenuChrome.roundedOutline(context, row.x(), row.y(), row.width(), row.height(), 7.0f, 1.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 28, openT), metrics.scale());
        }
    }

    // ------------------------------------------------------------------ config profiles

    private void drawConfigPanel(DrawContext context, Metrics metrics, float mx, float my,
                                 float openT, float deltaSeconds) {
        Rect panel = metrics.config();
        LinkenMenuChrome.roundedFill(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL, 224, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 17, openT), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), I18n.get("gui.config.title"),
            panel.x() + 24.0f, panel.y() + 84.0f, 27.0f, LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), I18n.get("gui.config.description"),
            panel.x() + 26.0f, panel.y() + 124.0f, 8.5f, 1.4f,
            LinkenMenuChrome.rgba(LinkGuiTheme.MUTED, openT));

        float controlY = panel.y() + 158.0f;
        float left = panel.x() + 28.0f;
        float right = panel.right() - 28.0f;
        Rect input = new Rect(left, controlY, 330.0f, 42.0f);
        boolean inputHover = input.contains(mx, my);
        int inputBorder = configInputFocused ? LinkGuiTheme.ACCENT : 0xFFFFFF;
        LinkenMenuChrome.roundedFill(context, input.x(), input.y(), input.width(), input.height(), 7.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.FIELD, 205, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, input.x(), input.y(), input.width(), input.height(), 7.0f, 1.0f,
            LinkenMenuChrome.rgba(inputBorder, configInputFocused ? 120 : (inputHover ? 50 : 25), openT),
            metrics.scale());
        String name = configName.value();
        if (name.isEmpty() && !configInputFocused) {
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), I18n.get("gui.config.input_placeholder"),
                input.x() + 15.0f, input.y() + 12.0f, 11.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        } else {
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), name,
                input.x() + 15.0f, input.y() + 12.0f, 11.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));
            if (configInputFocused) {
                String beforeCaret = name.substring(0, Math.min(configName.caret(), name.length()));
                float caret = input.x() + 15.0f
                    + LinkenMenuChrome.textWidth(LinkenFonts.body(), beforeCaret, 11.5f);
                context.fill(Math.round(caret), Math.round(input.y() + 10.0f),
                    Math.round(caret + 1.2f), Math.round(input.bottom() - 10.0f),
                    LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 220, openT));
            }
        }
        configInputHit = input;

        Rect create = new Rect(input.right() + 12.0f, controlY, 132.0f, 42.0f);
        drawPillButton(context, metrics, create, I18n.get("gui.config.create"), mx, my, true, openT);
        configCreateHit = create;
        Rect folder = new Rect(create.right() + 10.0f, controlY, 112.0f, 42.0f);
        drawPillButton(context, metrics, folder, I18n.get("gui.config.folder"), mx, my, false, openT);
        configFolderHit = folder;
        Rect refresh = new Rect(folder.right() + 10.0f, controlY, 112.0f, 42.0f);
        drawPillButton(context, metrics, refresh, I18n.get("gui.config.refresh"), mx, my, false, openT);
        configRefreshHit = refresh;
        boolean auto = ModuleRegistry.configAutoSave() != null && ModuleRegistry.configAutoSave().value();
        float autoWidth = 190.0f;
        Rect autoRect = new Rect(right - autoWidth, controlY, autoWidth, 42.0f);
        drawPillButton(context, metrics, autoRect,
            I18n.get(auto ? "gui.config.autosave_on" : "gui.config.autosave_off"), mx, my, auto, openT);
        configAutoHit = autoRect;

        String active = I18n.get("gui.config.active_prefix") + ConfigManager.getActiveProfile();
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), active, left, controlY + 60.0f, 10.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        if (status != null && System.currentTimeMillis() < statusUntil) {
            LinkenMenuChrome.drawRightText(context, LinkenFonts.body(), status, right, controlY + 60.0f, 10.5f,
                LinkenMenuChrome.rgba(statusColor, openT));
        }

        Rect list = new Rect(left, controlY + 86.0f, right - left, panel.bottom() - (controlY + 86.0f) - 26.0f);
        List<String> profiles = ConfigManager.getProfiles();
        float rowH = 68.0f;
        float gap = 10.0f;
        float contentHeight = Math.max(0.0f, profiles.size() * (rowH + gap) - gap);
        this.configContentHeight = contentHeight;
        configView = list.height();
        configScrollTarget = Math.max(0.0f, Math.min(configScrollTarget,
            Math.max(0.0f, contentHeight - list.height())));
        configScrollSpring.step(configScrollTarget, deltaSeconds);
        float scroll = (float) configScrollSpring.get();

        context.enableScissor(Math.round(list.x() - 4.0f), Math.round(list.y()),
            Math.round(list.right() + 4.0f), Math.round(list.bottom()));
        configHits.clear();
        if (profiles.isEmpty()) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(), I18n.get("gui.config.empty"),
                list.x() + list.width() * 0.5f, list.y() + 60.0f, 14.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        }
        for (int i = 0; i < profiles.size(); i++) {
            String profile = profiles.get(i);
            float y = list.y() + i * (rowH + gap) - scroll;
            Rect row = new Rect(list.x(), y, list.width(), rowH);
            if (row.bottom() < list.y() || row.y() > list.bottom()) {
                continue;
            }
            boolean activeProfile = profile.equalsIgnoreCase(ConfigManager.getActiveProfile());
            boolean hovered = row.contains(mx, my);
            LinkenMenuChrome.roundedFill(context, row.x(), row.y(), row.width(), row.height(), 8.0f,
                LinkenMenuChrome.rgba(activeProfile ? LinkGuiTheme.ACTIVE : (hovered ? LinkGuiTheme.HOVER : LinkGuiTheme.ROW),
                    Math.round(210 * openT)), metrics.scale());
            LinkenMenuChrome.roundedOutline(context, row.x(), row.y(), row.width(), row.height(), 8.0f, 1.0f,
                LinkenMenuChrome.rgba(activeProfile ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                    Math.round((activeProfile ? 70 : 18) * openT)), metrics.scale());
            if (activeProfile) {
                LinkenMenuChrome.roundedFill(context, row.x(), row.y() + 12.0f, 2.5f, row.height() - 24.0f, 1.2f,
                    LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT), metrics.scale());
            }
            LinkenMenuChrome.drawText(context, LinkenFonts.headingLight(), profile,
                row.x() + 18.0f, row.y() + 17.0f, 16.0f,
                LinkenMenuChrome.rgba(activeProfile ? LinkGuiTheme.TEXT : LinkGuiTheme.TEXT3, openT));
            LinkenMenuChrome.drawText(context, LinkenFonts.mono(), profile + ".json",
                row.x() + 18.0f, row.y() + 43.0f, 8.5f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
            if (activeProfile) {
                LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(), "ACTIVE",
                    row.x() + 18.0f + LinkenMenuChrome.textWidth(LinkenFonts.headingLight(), profile, 16.0f) + 14.0f,
                    row.y() + 21.0f, 7.5f, 1.5f,
                    LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
            }

            float loadW = 82.0f;
            Rect load = new Rect(row.right() - 18.0f - 82.0f - 8.0f - 82.0f - 8.0f - 24.0f,
                row.y() + 19.0f, 82.0f, 30.0f);
            Rect save = new Rect(load.right() + 8.0f, load.y(), 82.0f, 30.0f);
            Rect delete = new Rect(save.right() + 8.0f, load.y(), 24.0f, 30.0f);
            drawPillButton(context, metrics, load, I18n.get("gui.config.load"), mx, my, false, openT);
            drawPillButton(context, metrics, save, I18n.get("gui.config.save"), mx, my, false, openT);
            drawDeleteButton(context, metrics, delete, profiles.size() > 1, mx, my, openT);
            configHits.add(new ConfigHit(profile, row, load, save, delete));
        }
        context.disableScissor();
        scrollbar(context, metrics, list.right() + 4.0f, list.y(), list.height(), contentHeight, scroll, openT);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(),
            "Profiles are stored in config/linkenvisuals/configs", left, panel.bottom() - 28.0f, 9.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
    }

    private void drawPillButton(DrawContext context, Metrics metrics, Rect rect, String label,
                                float mx, float my, boolean active, float openT) {
        boolean hovered = rect.contains(mx, my);
        LinkenMenuChrome.drawSecondary(context, rect.x(), rect.y(), rect.width(), rect.height(), 6.0f,
            hovered ? 1.0f : (active ? 0.35f : 0.0f), false, openT, metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), label,
            rect.x() + rect.width() * 0.5f, rect.y() + rect.height() * 0.5f, 9.5f,
            LinkenMenuChrome.rgba(active ? LinkGuiTheme.ACCENT : LinkGuiTheme.ON_DARK, openT));
    }

    private void drawDeleteButton(DrawContext context, Metrics metrics, Rect rect, boolean enabled,
                                  float mx, float my, float openT) {
        boolean hovered = enabled && rect.contains(mx, my);
        LinkenMenuChrome.drawSecondary(context, rect.x(), rect.y(), rect.width(), rect.height(), 6.0f,
            hovered ? 1.0f : 0.0f, true, openT, metrics.scale());
        int color = LinkenMenuChrome.rgba(enabled ? (hovered ? 0xFF8C8C : LinkGuiTheme.DANGER) : 0x6E757F, openT);
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(rect.x() + rect.width() * 0.5f, rect.y() + rect.height() * 0.5f);
        matrices.rotate((float) Math.toRadians(45.0));
        PanelPipeline.draw(context, -7.0f, -0.7f, 14.0f, 1.4f, color);
        matrices.rotate((float) Math.toRadians(90.0));
        PanelPipeline.draw(context, -7.0f, -0.7f, 14.0f, 1.4f, color);
        matrices.popMatrix();
    }

    // ------------------------------------------------------------------ popups

    private void drawPopups(DrawContext context, Metrics metrics, float mx, float my,
                            float openT, float deltaSeconds) {
        optionHits.clear();
        // Donor enum dropdown: exponential open/close smoothing (rate 20/s), keeping the
        // last setting while the list shrinks so closing is animated, not a hard cut.
        float enumddTarget = openMode != null ? 1.0f : 0.0f;
        enumddOpen += (enumddTarget - enumddOpen)
            * (1.0f - (float) Math.exp(-20.0f * deltaSeconds));
        if (Math.abs(enumddTarget - enumddOpen) < 0.001f) {
            enumddOpen = enumddTarget;
        }
        if (openMode != null) {
            lastOpenMode = openMode;
            lastOpenModeBox = openModeBox;
        }
        if (lastOpenMode != null && lastOpenModeBox != null && enumddOpen > 0.01f) {
            drawEnumDropdown(context, metrics, mx, my, openT);
        }

        if (openColor == null) {
            return;
        }
        float field = 220.0f;
        float hueW = 20.0f;
        float pad = 18.0f;
        float swatch = 26.0f;
        float popupW = field + hueW + pad * 3.0f;
        float popupH = field + pad * 4.0f + swatch + 28.0f;
        float px = Math.max(20.0f, Math.min(width - popupW - 20.0f, metrics.settings().x() - popupW - 18.0f));
        float py = Math.max(120.0f, Math.min(height - popupH - 20.0f, 250.0f));
        pickerX = px + pad;
        pickerY = py + pad + 24.0f;
        pickerFieldSize = field;

        GlowPipeline.radial(context, px + popupW * 0.5f, py + popupH * 0.5f,
            Math.max(popupW, popupH) * 0.58f, LinkenMenuChrome.rgba(0x000000, 160, openT));
        LinkenMenuChrome.roundedFill(context, px, py, popupW, popupH, 10.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL_LIGHT, 249, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, px, py, popupW, popupH, 10.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 40, openT), metrics.scale());
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), openColor.name(),
            px + pad, py + 14.0f, 12.0f, LinkenMenuChrome.rgba(LinkGuiTheme.TEXT, openT));
        String hex = String.format(Locale.ROOT, "#%06X", openColor.rgb() & 0xFFFFFF);
        LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(), hex,
            px + popupW - pad, py + 15.0f, 10.0f, LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));

        ColorFieldPipeline.field(context, pickerX, pickerY, field, field,
            LinkenMenuChrome.rgba(openColor.hueColor(), openT));
        LinkenMenuChrome.roundedOutline(context, pickerX, pickerY, field, field, 1.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, 32, openT), metrics.scale());
        float knobX = pickerX + openColor.saturation() * field;
        float knobY = pickerY + (1.0f - openColor.value()) * field;
        GlowPipeline.radial(context, knobX, knobY, 18.0f, LinkenMenuChrome.rgba(openColor.rgb(), 120, openT));
        CirclePipeline.draw(context, knobX, knobY, 7.0f, LinkenMenuChrome.rgba(0xFFFFFF, openT));
        CirclePipeline.draw(context, knobX, knobY, 4.4f, LinkenMenuChrome.rgba(openColor.rgb(), openT));

        float hueX = pickerX + field + pad;
        ColorFieldPipeline.hueVertical(context, hueX, pickerY, hueW, field, Math.round(255 * openT));
        float hueY = pickerY + openColor.hue() * field;
        GlowPipeline.radial(context, hueX + hueW * 0.5f, hueY, 18.0f,
            LinkenMenuChrome.rgba(openColor.hueColor(), 120, openT));
        CirclePipeline.draw(context, hueX + hueW * 0.5f, hueY, 7.0f, LinkenMenuChrome.rgba(0xFFFFFF, openT));
        CirclePipeline.draw(context, hueX + hueW * 0.5f, hueY, 4.4f,
            LinkenMenuChrome.rgba(openColor.hueColor(), openT));

        float swatchY = py + popupH - pad - swatch;
        for (int i = 0; i < ColorSetting.PRESETS.length; i++) {
            float sx = pickerX + i * (swatch + 11.0f);
            CirclePipeline.draw(context, sx + swatch * 0.5f, swatchY + swatch * 0.5f, swatch * 0.5f,
                LinkenMenuChrome.rgba(0xFFFFFF, 44, openT));
            CirclePipeline.draw(context, sx + swatch * 0.5f, swatchY + swatch * 0.5f, swatch * 0.5f - 2.0f,
                LinkenMenuChrome.rgba(ColorSetting.PRESETS[i], openT));
        }

        // Popup bounds are remembered through the primitive fields above.
        popupBounds = new Rect(px, py, popupW, popupH);
    }

    /**
     * Donor enum dropdown: the panel height grows with an eased factor, rows cascade
     * in from the top, and closing keeps the last setting so the list visibly shrinks.
     */
    private void drawEnumDropdown(DrawContext context, Metrics metrics, float mx, float my, float openT) {
        ModeSetting setting = lastOpenMode;
        Rect anchor = lastOpenModeBox;
        if (setting == null || anchor == null) {
            return;
        }
        List<String> options = setting.options();
        if (options.isEmpty()) {
            return;
        }
        float rowH = 30.0f;
        float listH = options.size() * rowH + 12.0f;
        float listX = anchor.x();
        float listY = anchor.bottom() + 4.0f;
        if (listY + listH > height - 30.0f) {
            listY = Math.max(0.0f, anchor.y() - listH - 4.0f);
        }
        float listW = Math.max(anchor.width(), 190.0f);

        // Donor ease-out over the exponentially smoothed open factor.
        float inverse = 1.0f - enumddOpen;
        float ease = 1.0f - inverse * inverse * inverse;
        float revealH = Math.max(1.0f, listH * ease);

        GlowPipeline.radial(context, listX + listW * 0.5f, listY + revealH * 0.5f,
            Math.max(listW, revealH) * 0.6f, LinkenMenuChrome.rgba(0x000000, 150, openT));
        LinkenMenuChrome.roundedFill(context, listX, listY, listW, revealH, 8.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL_LIGHT, 248, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, listX, listY, listW, revealH, 8.0f, 1.0f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(38 * ease), openT), metrics.scale());

        boolean interactive = openMode == setting && enumddOpen > 0.99f;
        for (int i = 0; i < options.size(); i++) {
            float rowY = listY + 6.0f + i * rowH;
            float rowAlpha = Math.max(0.0f, Math.min(1.0f, (listY + revealH - rowY) / rowH));
            if (rowAlpha <= 0.02f) {
                continue;
            }
            boolean hovered = interactive && mx >= listX && mx <= listX + listW
                && my >= rowY && my <= rowY + rowH;
            boolean active = i == setting.index();
            if (hovered || active) {
                LinkenMenuChrome.roundedFill(context, listX + 5.0f, rowY, listW - 10.0f, rowH, 5.0f,
                    LinkenMenuChrome.rgba(active ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                        Math.round((active ? 24 : 10) * rowAlpha), openT), metrics.scale());
            }
            LinkenMenuChrome.drawText(context, LinkenFonts.body(), options.get(i),
                listX + 16.0f, rowY + 8.0f, 10.0f,
                LinkenMenuChrome.rgba(active ? LinkGuiTheme.ACCENT : LinkGuiTheme.ON_DARK,
                    Math.round(255 * rowAlpha), openT));
            if (interactive) {
                optionHits.add(new OptionHit(setting, i, new Rect(listX + 5.0f, rowY, listW - 10.0f, rowH)));
            }
        }
    }

    private Rect popupBounds;

    // ------------------------------------------------------------------ helpers

    private void drawSwitch(DrawContext context, float x, float y, float width, float height,
                            float on, float openT, float scale) {
        float radius = height * 0.5f;
        LinkenMenuChrome.roundedFill(context, x, y, width, height, radius,
            LinkenMenuChrome.rgba(LinkGuiTheme.TRACK, Math.round(210 * openT)), scale);
        if (on > 0.001f) {
            LinkenMenuChrome.roundedFill(context, x, y, width, height, radius,
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, Math.round(235 * on * openT)), scale);
            context.fill(Math.round(x + 3.0f), Math.round(y + 1.0f),
                Math.round(x + width - 3.0f), Math.round(y + 2.0f),
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(110 * on * openT)));
        }
        LinkenMenuChrome.roundedOutline(context, x, y, width, height, radius, 1.0f,
            LinkenMenuChrome.rgba(on > 0.5f ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                Math.round((on > 0.5f ? 120 : 34) * openT)), scale);
        float knob = 12.0f;
        float knobX = x + 3.0f + (width - 6.0f - knob) * on;
        LinkenMenuChrome.roundedFill(context, knobX, y + 4.0f, knob, knob, knob * 0.5f,
            LinkenMenuChrome.rgba(0x000000, Math.round(90 * openT)), scale);
        LinkenMenuChrome.roundedFill(context, knobX, y + 3.0f, knob, knob, knob * 0.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(0xC3CCC7, LinkGuiTheme.INK, on), openT), scale);
    }

    private void drawChevron(DrawContext context, float cx, float cy, boolean up, float openT) {
        int color = LinkenMenuChrome.rgba(LinkGuiTheme.MUTED, openT);
        for (int i = 0; i < 3; i++) {
            float dy = up ? -i : i;
            context.fill(Math.round(cx - 4.0f + i), Math.round(cy + dy),
                Math.round(cx - 3.0f + i), Math.round(cy + dy) + 1, color);
            context.fill(Math.round(cx + 3.0f - i), Math.round(cy + dy),
                Math.round(cx + 4.0f - i), Math.round(cy + dy) + 1, color);
        }
    }

    private void scrollbar(DrawContext context, Metrics metrics, float x, float y, float viewHeight,
                           float contentSize, float scroll, float openT) {
        if (contentSize <= viewHeight + 1.0f || viewHeight <= 0.0f) {
            return;
        }
        float share = Math.min(1.0f, viewHeight / contentSize);
        float barHeight = Math.max(18.0f, viewHeight * share);
        float travel = viewHeight - barHeight;
        float at = Math.max(0.0f, Math.min(1.0f, scroll / Math.max(1.0f, contentSize - viewHeight)));
        LinkenMenuChrome.roundedFill(context, x, y, 3.0f, viewHeight, 1.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, 10, openT), metrics.scale());
        LinkenMenuChrome.roundedFill(context, x, y + travel * at, 3.0f, barHeight, 1.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 80, openT), metrics.scale());
    }

    private static List<String> wrapTwoLines(GlyphAtlas font, String text, float maxWidth, float size) {
        if (text == null || text.isBlank() || maxWidth <= 0.0f) {
            return List.of();
        }
        String[] words = text.trim().split("\\s+");
        List<String> lines = new ArrayList<>(2);
        StringBuilder line = new StringBuilder();
        int index = 0;
        while (index < words.length && lines.size() < 2) {
            String candidate = line.isEmpty() ? words[index] : line + " " + words[index];
            if (!line.isEmpty() && LinkenMenuChrome.textWidth(font, candidate, size) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            line.setLength(0);
            line.append(candidate);
            index++;
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (index < words.length && !lines.isEmpty()) {
            StringBuilder rest = new StringBuilder(lines.get(lines.size() - 1));
            while (index < words.length) {
                rest.append(' ').append(words[index++]);
            }
            lines.set(lines.size() - 1, LinkenMenuChrome.ellipsize(font, rest.toString(), maxWidth, size));
        }
        return lines;
    }

    private float rowHeight(Setting setting) {
        if (setting instanceof SliderSetting) {
            return 50.0f;
        }
        if (setting instanceof ModeSetting) {
            return 42.0f;
        }
        return 34.0f;
    }

    private List<ModuleInfo> visibleModules() {
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        List<ModuleInfo> source = query.isEmpty() && !searchAll
            ? ModuleRegistry.byCategory(selected) : ModuleRegistry.all();
        List<ModuleInfo> result = new ArrayList<>();
        // Pinned modules float to the top of their category/search result. They
        // keep registry order inside the pinned and unpinned groups.
        for (ModuleInfo module : source) {
            if (LinkGuiConfig.pinned(module.name()) && matches(module, query)) {
                result.add(module);
            }
        }
        for (ModuleInfo module : source) {
            if (!LinkGuiConfig.pinned(module.name()) && matches(module, query)) {
                result.add(module);
            }
        }
        return result;
    }

    private static boolean matches(ModuleInfo module, String query) {
        return query.isEmpty() || module.name().toLowerCase(Locale.ROOT).contains(query)
            || module.description().toLowerCase(Locale.ROOT).contains(query);
    }

    // ------------------------------------------------------------------ theme window

    private void drawSidePanel(DrawContext context, Metrics metrics, float mx, float my, float openT) {
        Rect panel = new Rect(metrics.theme().x(), metrics.theme().y(), 340.0f, 600.0f);
        LinkenMenuChrome.roundedFill(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL, 245, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f,
            1.0f, LinkenMenuChrome.rgba(0xFFFFFF, 23, openT), metrics.scale());

        boolean friends = sidePanel == SidePanel.FRIENDS;
        List<String> names = friends ? LinkGuiConfig.friends() : null;
        List<Waypoint> waypoints = friends ? null : WaypointStorage.store().sorted();
        int count = friends ? names.size() : waypoints.size();
        String title = I18n.get(friends ? "gui.friends.title" : "gui.waypoints.title");
        String subtitle = I18n.get(friends ? "gui.friends.count" : "gui.waypoints.count", count);

        float px = panel.x() + 16.0f;
        float cw = panel.width() - 32.0f;
        float headerY = panel.y() + 16.0f;
        if (LinkenFonts.icons() != null) {
            IconPipeline.draw(context, LinkenFonts.iconsFa(), friends ? 0xF0C0 : 0xF3C5,
                px + 7.0f, headerY + 7.0f, 14.0f, LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
        }
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), title,
            px + 22.0f, headerY + 7.0f, 13.0f, LinkenMenuChrome.rgba(LinkGuiTheme.TEXT2, openT));
        LinkenMenuChrome.drawRightText(context, LinkenFonts.body(), subtitle,
            panel.right() - 42.0f, headerY + 1.0f, 9.5f, LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
        sideCloseHit = new Rect(panel.right() - 30.0f, panel.y() + 8.0f, 22.0f, 22.0f);
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(), "X",
            sideCloseHit.x() + 11.0f, sideCloseHit.y() + 6.0f, 9.0f,
            LinkenMenuChrome.rgba(sideCloseHit.contains(mx, my) ? LinkGuiTheme.DANGER : LinkGuiTheme.MUTED2, openT));

        float inputY = panel.y() + 46.0f;
        Rect input = new Rect(px, inputY, cw - 86.0f, 32.0f);
        sideInputHit = input;
        LinkenMenuChrome.roundedFill(context, input.x(), input.y(), input.width(), input.height(), 7.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.FIELD, 220, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, input.x(), input.y(), input.width(), input.height(), 7.0f, 1.0f,
            LinkenMenuChrome.rgba(sideInputFocused ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                sideInputFocused ? 120 : 24, openT), metrics.scale());
        String text = sideInput.value();
        String placeholder = I18n.get(friends ? "gui.friends.placeholder" : "gui.waypoints.placeholder");
        LinkenMenuChrome.drawOn(context, LinkenFonts.body(), text.isEmpty() ? placeholder : text,
            input.x() + 10.0f, input.y() + 16.0f, 10.5f,
            LinkenMenuChrome.rgba(text.isEmpty() ? LinkGuiTheme.MUTED3 : LinkGuiTheme.TEXT, openT));
        if (sideInputFocused) {
            String beforeCaret = text.substring(0, Math.min(sideInput.caret(), text.length()));
            float caret = input.x() + 10.0f
                + LinkenMenuChrome.textWidth(LinkenFonts.body(), beforeCaret, 10.5f);
            context.fill(Math.round(caret), Math.round(input.y() + 9.0f),
                Math.round(caret + 1.0f), Math.round(input.bottom() - 9.0f),
                LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
        }
        Rect add = new Rect(input.right() + 8.0f, inputY, 78.0f, 32.0f);
        sideAddHit = add;
        boolean canAdd = !text.trim().isEmpty();
        LinkenMenuChrome.roundedFill(context, add.x(), add.y(), add.width(), add.height(), 7.0f,
            LinkenMenuChrome.rgba(canAdd ? LinkGuiTheme.ACCENT : LinkGuiTheme.TRACK, canAdd ? 235 : 180, openT),
            metrics.scale());
        LinkenMenuChrome.drawCentered(context, LinkenFonts.headingLight(),
            I18n.get(friends ? "gui.friends.add" : "gui.waypoints.add"), add.x() + add.width() * 0.5f,
            add.y() + 16.0f, 8.5f,
            LinkenMenuChrome.rgba(canAdd ? LinkGuiTheme.INK : LinkGuiTheme.MUTED3, openT));

        float listTop = inputY + 44.0f;
        float listBottom = panel.bottom() - 16.0f;
        context.enableScissor(Math.round(px - 4.0f), Math.round(listTop),
            Math.round(px + cw + 4.0f), Math.round(listBottom));
        sideRemoveHits.clear();
        if (count == 0) {
            LinkenMenuChrome.drawCentered(context, LinkenFonts.body(),
                I18n.get(friends ? "gui.friends.empty" : "gui.waypoints.empty"),
                px + cw * 0.5f, listTop + 22.0f, 11.0f,
                LinkenMenuChrome.rgba(LinkGuiTheme.MUTED3, openT));
        } else {
            for (int i = 0; i < count; i++) {
                String name = friends ? names.get(i) : waypoints.get(i).name();
                String detail = friends ? null
                    : (int) waypoints.get(i).x() + ", " + (int) waypoints.get(i).y()
                        + ", " + (int) waypoints.get(i).z();
                float rowY = listTop + i * 44.0f;
                if (rowY > listBottom) {
                    break;
                }
                Rect row = new Rect(px, rowY, cw, 38.0f);
                LinkenMenuChrome.roundedFill(context, row.x(), row.y(), row.width(), row.height(), 7.0f,
                    LinkenMenuChrome.rgba(row.contains(mx, my) ? LinkGuiTheme.HOVER : LinkGuiTheme.ROW,
                        210, openT), metrics.scale());
                LinkenMenuChrome.drawOn(context, LinkenFonts.body(), name, px + 10.0f,
                    row.y() + (detail == null ? row.height() * 0.5f : 12.0f), 10.5f,
                    LinkenMenuChrome.rgba(LinkGuiTheme.TEXT2, openT));
                if (detail != null) {
                    LinkenMenuChrome.drawText(context, LinkenFonts.mono(), detail, px + 10.0f,
                        row.y() + 21.0f, 8.0f, LinkenMenuChrome.rgba(LinkGuiTheme.MUTED2, openT));
                }
                Rect remove = new Rect(row.right() - 30.0f, row.y() + 8.0f, 22.0f, 22.0f);
                LinkenMenuChrome.drawCentered(context, LinkenFonts.mono(), "x", remove.x() + 11.0f,
                    remove.y() + 7.0f, 9.0f,
                    LinkenMenuChrome.rgba(remove.contains(mx, my) ? LinkGuiTheme.DANGER : LinkGuiTheme.MUTED2, openT));
                sideRemoveHits.add(new SideRemoveHit(name, remove));
            }
        }
        context.disableScissor();
    }

    private void drawThemePanel(DrawContext context, Metrics metrics, float mx, float my, float openT) {
        Rect panel = metrics.theme();
        GlowPipeline.radial(context, panel.x() + panel.width() * 0.5f, panel.y() + 22.0f,
            panel.width() * 0.75f, LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, 16, openT));
        LinkenMenuChrome.roundedFill(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.PANEL, 245, openT), metrics.scale());
        LinkenMenuChrome.roundedOutline(context, panel.x(), panel.y(), panel.width(), panel.height(), 12.0f,
            1.0f, LinkenMenuChrome.rgba(0xFFFFFF, 23, openT), metrics.scale());

        float px = panel.x() + 20.0f;
        float cw = panel.width() - 40.0f;
        float headerY = panel.y() + 20.0f;
        IconPipeline.draw(context, LinkenFonts.iconsFa(), 0xF043, px + 6.0f, headerY + 6.0f, 13.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), I18n.get("gui.theme.title"),
            px + 18.0f, headerY + 6.0f, 13.0f, LinkenMenuChrome.rgba(LinkGuiTheme.TEXT2, openT));
        float hairX = px + 18.0f + LinkenMenuChrome.textWidth(LinkenFonts.heading(),
            I18n.get("gui.theme.title"), 13.0f) + 10.0f;
        context.fill(Math.round(hairX), Math.round(headerY + 6.0f),
            Math.round(px + cw), Math.round(headerY + 7.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 18, openT));

        themeHits.clear();
        LinkGuiTheme.Preset[] presets = LinkGuiTheme.Preset.values();
        float gap = 8.0f;
        float chipW = (cw - gap) * 0.5f;
        float chipH = 28.0f;
        float chipTop = panel.y() + 48.0f;
        for (int i = 0; i < presets.length; i++) {
            LinkGuiTheme.Preset preset = presets[i];
            float cx = px + (i % 2) * (chipW + gap);
            float cy = chipTop + (i / 2) * (chipH + gap);
            Rect chip = new Rect(cx, cy, chipW, chipH);
            boolean hovered = chip.contains(mx, my);
            boolean active = LinkGuiTheme.preset() == preset;
            LinkenMenuChrome.roundedFill(context, cx, cy, chipW, chipH, 10.0f,
                LinkenMenuChrome.rgba(preset.background(), 255, openT), metrics.scale());
            LinkenMenuChrome.roundedFill(context, cx + 13.0f, cy + chipH * 0.5f - 4.0f, 8.0f, 8.0f, 4.0f,
                LinkenMenuChrome.rgba(preset.accent(), openT), metrics.scale());
            int nameColor = preset.light() ? 0x1A1C22 : 0xDFE5E1;
            LinkenMenuChrome.drawOn(context, LinkenFonts.body(), preset.label(),
                cx + 26.0f, cy + chipH * 0.5f, 10.8f,
                LinkenMenuChrome.rgba(nameColor, openT));
            if (hovered && !active) {
                LinkenMenuChrome.roundedFill(context, cx + 1.0f, cy + 1.0f, chipW - 2.0f, chipH - 2.0f,
                    9.0f, LinkenMenuChrome.rgba(0xFFFFFF, 14, openT), metrics.scale());
            }
            LinkenMenuChrome.roundedOutline(context, cx, cy, chipW, chipH, 10.0f,
                active ? 1.8f : 0.9f,
                LinkenMenuChrome.rgba(active ? LinkGuiTheme.ACCENT : 0xFFFFFF,
                    active ? 200 : Math.round(20 + 40 * (hovered ? 1 : 0)), openT), metrics.scale());
            themeHits.add(new ThemeHit(preset, chip));
        }

        float interfaceY = chipTop + 4.0f * (chipH + gap) + 4.0f;
        IconPipeline.draw(context, LinkenFonts.iconsFa(), 0xF1DE, px + 6.0f, interfaceY + 6.0f, 13.0f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
        LinkenMenuChrome.drawOn(context, LinkenFonts.heading(), I18n.get("gui.theme.interface"),
            px + 18.0f, interfaceY + 6.0f, 13.0f, LinkenMenuChrome.rgba(LinkGuiTheme.TEXT2, openT));
        float ihx = px + 18.0f + LinkenMenuChrome.textWidth(LinkenFonts.heading(),
            I18n.get("gui.theme.interface"), 13.0f) + 10.0f;
        context.fill(Math.round(ihx), Math.round(interfaceY + 6.0f),
            Math.round(px + cw), Math.round(interfaceY + 7.0f),
            LinkenMenuChrome.rgba(0xFFFFFF, 18, openT));

        interfaceHits.clear();
        ModuleInfo ui = clickGuiModule();
        List<Setting> interfaceSettings = new ArrayList<>();
        if (ui != null) {
            interfaceSettings.addAll(ui.leftSettings());
            interfaceSettings.addAll(ui.rightSettings());
            interfaceSettings.removeIf(setting -> "GUI Style".equalsIgnoreCase(setting.name()));
        }
        float interfaceTop = interfaceY + 24.0f;
        float interfaceBottom = panel.bottom() - 50.0f;
        interfaceViewHeight = Math.max(1.0f, interfaceBottom - interfaceTop);
        interfaceContentHeight = 0.0f;
        for (Setting setting : interfaceSettings) {
            interfaceContentHeight += Math.min(rowHeight(setting), 64.0f) + 6.0f;
        }
        interfaceScrollTarget = Math.max(0.0f, Math.min(interfaceScrollTarget,
            Math.max(0.0f, interfaceContentHeight - interfaceViewHeight)));
        interfaceScrollSpring.step(interfaceScrollTarget, 0.05f);
        float interfaceScroll = (float) interfaceScrollSpring.get();

        context.enableScissor(Math.round(px - 4.0f), Math.round(interfaceTop),
            Math.round(px + cw + 4.0f), Math.round(interfaceBottom));
        float rowY = interfaceTop - interfaceScroll;
        for (Setting setting : interfaceSettings) {
            float height = Math.min(rowHeight(setting), 64.0f);
            Rect row = new Rect(px, rowY, cw, height);
            if (row.bottom() >= interfaceTop - 6.0f && row.y() <= interfaceBottom + 6.0f) {
                drawSetting(context, metrics, setting, row, mx, my, openT, 0.05f, ui != null ? ui.name() : "ClickGUI");
                interfaceHits.add(new SettingHit(setting, row));
            }
            rowY += height + 6.0f;
        }
        context.disableScissor();
        scrollbar(context, metrics, panel.right() - 10.0f, interfaceTop, interfaceViewHeight,
            interfaceContentHeight, interfaceScroll, openT);

        GlyphAtlas footerFont = LinkenFonts.bodyMedium();
        float footerY = panel.bottom() - 20.0f - 10.0f;
        float footerLogo = 12.0f;
        LinkenMenuChrome.drawLogo(context, px, footerY - 1.0f, footerLogo, openT);
        float footerX = px + footerLogo + 4.0f;
        LinkenMenuChrome.drawSpaced(context, footerFont, "linken", footerX, footerY, 11.5f, -0.35f,
            LinkenMenuChrome.rgba(LinkGuiTheme.MUTED, openT));
        float flw = LinkenMenuChrome.textWidth(footerFont, "linken", 11.5f) - 0.35f * 5.0f;
        LinkenMenuChrome.drawText(context, footerFont, ".", footerX + flw, footerY, 11.5f,
            LinkenMenuChrome.rgba(LinkGuiTheme.ACCENT, openT));
        LinkenMenuChrome.drawRightText(context, LinkenFonts.mono(),
            LinkenMenuChrome.version(), px + cw, footerY + 1.0f, 9.5f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED4, openT));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (closing) {
            return super.mouseClicked(click, doubleClick);
        }
        KeybindSetting listening = findListeningKeybind();
        if (listening != null && listening.mouseAllowed()) {
            listening.resolveMouse(click.button());
            return true;
        }
        Metrics metrics = LinkGuiLayout.compute(width, height);
        float mx = (float) (click.x() - metrics.offsetX()) / metrics.scale();
        float my = (float) (click.y() - metrics.offsetY()) / metrics.scale();
        boolean rightClick = click.button() == 1;

        if (headerSkinHit != null && headerSkinHit.contains(mx, my)) {
            profileOpen = !profileOpen;
            playClick();
            return true;
        }
        if (themeToggleHit != null && themeToggleHit.contains(mx, my)) {
            themeOpen = !themeOpen;
            sidePanel = SidePanel.NONE;
            playClick();
            return true;
        }
        if (profileOpen) {
            if (profileCloseHit != null && profileCloseHit.contains(mx, my)) {
                profileOpen = false;
                return true;
            }
            if (metrics.profile().contains(mx, my)) {
                return true;
            }
            profileOpen = false;
        }

        if (sidePanel != SidePanel.NONE) {
            if (sideCloseHit != null && sideCloseHit.contains(mx, my)) {
                sidePanel = SidePanel.NONE;
                sideInputFocused = false;
                playClick();
                return true;
            }
            if (sideInputHit != null && sideInputHit.contains(mx, my)) {
                sideInputFocused = true;
                lastTyped = System.currentTimeMillis();
                return true;
            }
            if (sideAddHit != null && sideAddHit.contains(mx, my) && !sideInput.value().trim().isEmpty()) {
                String name = sideInput.value().trim();
                boolean added;
                if (sidePanel == SidePanel.FRIENDS) {
                    added = LinkGuiConfig.addFriend(name);
                } else if (client != null && client.player != null) {
                    added = WaypointStorage.store().add(name, client.player.getX(),
                        client.player.getY(), client.player.getZ());
                    if (added) {
                        WaypointStorage.save();
                    }
                } else {
                    added = false;
                }
                if (added) {
                    sideInput.clear();
                }
                playClick();
                return true;
            }
            for (SideRemoveHit hit : sideRemoveHits) {
                if (hit.bounds().contains(mx, my)) {
                    if (sidePanel == SidePanel.FRIENDS) {
                        LinkGuiConfig.removeFriend(hit.name());
                    } else {
                        WaypointStorage.store().remove(hit.name());
                        WaypointStorage.save();
                    }
                    playClick();
                    return true;
                }
            }
            if (metrics.theme().contains(mx, my)) {
                return true;
            }
            sideInputFocused = false;
        }

        for (UtilityHit hit : utilityHits) {
            if (hit.bounds().contains(mx, my)) {
                hit.action().run();
                return true;
            }
        }

        if (themeOpen) {
            for (ThemeHit hit : themeHits) {
                if (hit.bounds().contains(mx, my)) {
                    LinkGuiTheme.apply(hit.preset().name());
                    LinkGuiConfig.setTheme(hit.preset().name());
                    playClick();
                    return true;
                }
            }
        }

        if (closeHit != null && closeHit.contains(mx, my)) {
            close();
            return true;
        }
        // Popup capture comes first so a click cannot accidentally toggle a row
        // underneath an open dropdown or colour picker.
        if (openMode != null) {
            for (OptionHit hit : optionHits) {
                if (hit.bounds.contains(mx, my)) {
                    hit.mode.select(hit.index);
                    openMode = null;
                    playClick();
                    return true;
                }
            }
            openMode = null;
            return true;
        }
        if (openColor != null) {
            if (popupBounds != null && popupBounds.contains(mx, my)) {
                if (my < pickerY) {
                    return true;
                }
                if (my >= pickerY + pickerFieldSize) {
                    for (int i = 0; i < ColorSetting.PRESETS.length; i++) {
                        float sx = pickerX + i * (26.0f + 11.0f);
                        if (mx >= sx && mx <= sx + 26.0f) {
                            openColor.set(ColorSetting.PRESETS[i]);
                            return true;
                        }
                    }
                    return true;
                }
                draggingHue = mx >= pickerX + pickerFieldSize + 6.0f;
                draggingColor = true;
                applyColorDrag(mx, my);
                return true;
            }
            openColor = null;
            return true;
        }

        if (settingsOpen) {
            Rect window = settingsWindowRect(metrics);
            if (settingsCloseHit != null && settingsCloseHit.contains(mx, my)) {
                settingsOpen = false;
                return true;
            }
            if (settingsHeaderHit != null && settingsHeaderHit.contains(mx, my)) {
                settingsDragging = true;
                settingsDragX = mx - window.x();
                settingsDragY = my - window.y();
                return true;
            }
            if (window.contains(mx, my)) {
                if (headerToggleHit != null && headerToggleHit.contains(mx, my) && focused != null) {
                    focused.toggle();
                    playToggle(focused.enabled());
                    return true;
                }
                if (moduleBindHit != null && moduleBindHit.contains(mx, my) && focused != null) {
                    focused.armBinding();
                    playClick();
                    return true;
                }
                for (SettingHit hit : settingHits) {
                    if (hit.bounds.contains(mx, my)) {
                        handleSettingClick(hit.setting, hit.bounds, mx, my, rightClick);
                        return true;
                    }
                }
                return true;
            }
        }

        for (PinHit hit : pinHits) {
            if (hit.toggle.contains(mx, my)) {
                hit.module.toggle();
                playToggle(hit.module.enabled());
                return true;
            }
            if (hit.unpin.contains(mx, my)) {
                LinkGuiConfig.togglePin(hit.module.name());
                playClick();
                return true;
            }
        }
        for (SettingHit hit : pinnedSettingHits) {
            if (hit.bounds.contains(mx, my)) {
                handleSettingClick(hit.setting, hit.bounds, mx, my, rightClick);
                return true;
            }
        }

        if (music.mouseClicked(mx, my, click.button())) {
            playClick();
            return true;
        }

        for (NavHit hit : navHits) {
            if (hit.bounds.contains(mx, my)) {
                selectCategory(hit.category);
                return true;
            }
        }
        if (allHit != null && allHit.contains(mx, my)) {
            searchAll = !searchAll;
            scrollTarget = 0.0f;
            playClick();
            return true;
        }
        if (searchHit != null && searchHit.contains(mx, my)) {
            searchFocused = true;
            configInputFocused = false;
            lastTyped = System.currentTimeMillis();
            return true;
        }
        if (selected == Category.CONFIG) {
            return clickConfig(metrics, mx, my);
        }

        if (headerToggleHit != null && headerToggleHit.contains(mx, my) && focused != null) {
            focused.toggle();
            playToggle(focused.enabled());
            return true;
        }
        if (moduleBindHit != null && moduleBindHit.contains(mx, my) && focused != null) {
            focused.armBinding();
            playClick();
            return true;
        }

        for (CardHit hit : cardHits) {
            if (!hit.visible || !hit.bounds.contains(mx, my)) {
                continue;
            }
            if (hit.pin != null && hit.pin.contains(mx, my)) {
                openSettingsWindow(hit.module, hit.bounds);
                playClick();
                return true;
            }
            if (rightClick) {
                focused = hit.module;
                LinkGuiConfig.togglePin(hit.module.name());
                playClick();
                return true;
            }
            focused = hit.module;
            hit.module.toggle();
            playToggle(hit.module.enabled());
            return true;
        }

        for (SettingHit hit : interfaceHits) {
            if (!hit.bounds.contains(mx, my)) {
                continue;
            }
            handleSettingClick(hit.setting, hit.bounds, mx, my, rightClick);
            return true;
        }
        for (SettingHit hit : settingHits) {
            if (!hit.bounds.contains(mx, my)) {
                continue;
            }
            handleSettingClick(hit.setting, hit.bounds, mx, my, rightClick);
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    private boolean clickConfig(Metrics metrics, float mx, float my) {
        if (configInputHit != null && configInputHit.contains(mx, my)) {
            configInputFocused = true;
            searchFocused = false;
            lastTyped = System.currentTimeMillis();
            return true;
        }
        configInputFocused = false;
        if (configCreateHit != null && configCreateHit.contains(mx, my)) {
            if (configName.isEmpty()) {
                setStatus("Enter a profile name", LinkGuiTheme.DANGER);
                return true;
            }
            boolean created = ConfigManager.createProfile(configName.value());
            setStatus(I18n.get(created ? "gui.config.status.created" : "gui.config.status.exists",
                configName.value()), created ? LinkGuiTheme.ACCENT : LinkGuiTheme.DANGER);
            if (created) {
                configName.clear();
            }
            playClick();
            return true;
        }
        if (configFolderHit != null && configFolderHit.contains(mx, my)) {
            ConfigManager.openFolder();
            playClick();
            return true;
        }
        if (configRefreshHit != null && configRefreshHit.contains(mx, my)) {
            ConfigManager.reloadProfiles();
            setStatus(I18n.get("gui.config.status.refreshed"), LinkGuiTheme.ACCENT);
            playClick();
            return true;
        }
        if (configAutoHit != null && configAutoHit.contains(mx, my)) {
            if (ModuleRegistry.configAutoSave() != null) {
                ModuleRegistry.configAutoSave().toggle();
                playClick();
            }
            return true;
        }
        for (ConfigHit hit : configHits) {
            if (hit.load.contains(mx, my)) {
                if (ConfigManager.loadProfile(hit.profile)) {
                    setStatus(I18n.get("gui.config.status.loaded", hit.profile), LinkGuiTheme.ACCENT);
                    playClick();
                }
                return true;
            }
            if (hit.save.contains(mx, my)) {
                if (ConfigManager.saveProfile(hit.profile)) {
                    setStatus(I18n.get("gui.config.status.saved", hit.profile), LinkGuiTheme.ACCENT);
                    playClick();
                }
                return true;
            }
            if (hit.delete.contains(mx, my) && ConfigManager.getProfiles().size() > 1) {
                if (ConfigManager.deleteProfile(hit.profile)) {
                    setStatus(I18n.get("gui.config.status.deleted", hit.profile), LinkGuiTheme.ACCENT);
                    playClick();
                }
                return true;
            }
        }
        return true;
    }

    private void handleSettingClick(Setting setting, Rect row, float mx, float my, boolean rightClick) {
        if (setting instanceof BooleanSetting bool) {
            bool.toggle();
            playToggle(bool.value());
        } else if (setting instanceof SliderSetting slider) {
            draggingSlider = slider;
            dragTrackX = row.x() + 16.0f;
            dragTrackWidth = row.width() - 32.0f;
            activeSliderRaw = Math.max(0.0f, Math.min(1.0f, (mx - dragTrackX) / dragTrackWidth));
            slider.setFraction(activeSliderRaw);
        } else if (setting instanceof ModeSetting mode) {
            if (rightClick) {
                mode.select((mode.index() + mode.options().size() - 1) % mode.options().size());
            } else {
                mode.toggleOpen();
                openMode = mode.open() ? mode : null;
            }
            playClick();
        } else if (setting instanceof ColorSetting color) {
            openColor = color;
            if (!color.open()) {
                color.toggleOpen();
            }
            playClick();
        } else if (setting instanceof KeybindSetting keybind) {
            keybind.arm();
            playClick();
        }
    }

    private void applyColorDrag(float mx, float my) {
        if (openColor == null) {
            return;
        }
        if (draggingHue) {
            openColor.setHue((my - pickerY) / pickerFieldSize);
        } else {
            openColor.setSaturationValue((mx - pickerX) / pickerFieldSize,
                1.0f - (my - pickerY) / pickerFieldSize);
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        Metrics metrics = LinkGuiLayout.compute(width, height);
        float mx = (float) (click.x() - metrics.offsetX()) / metrics.scale();
        float my = (float) (click.y() - metrics.offsetY()) / metrics.scale();
        music.mouseDragged(mx, my);
        if (settingsDragging) {
            settingsX = mx - settingsDragX;
            settingsY = my - settingsDragY;
            return true;
        }
        if (draggingColor) {
            applyColorDrag(mx, my);
            return true;
        }
        if (draggingSlider != null) {
            activeSliderRaw = Math.max(0.0f, Math.min(1.0f, (mx - dragTrackX) / dragTrackWidth));
            draggingSlider.setFraction(activeSliderRaw);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (settingsDragging) {
            settingsDragging = false;
            return true;
        }
        draggingColor = false;
        draggingHue = false;
        if (draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        Metrics metrics = LinkGuiLayout.compute(width, height);
        float mx = (float) (click.x() - metrics.offsetX()) / metrics.scale();
        float my = (float) (click.y() - metrics.offsetY()) / metrics.scale();
        music.mouseReleased(mx, my, click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Metrics metrics = LinkGuiLayout.compute(width, height);
        float mx = (float) (mouseX - metrics.offsetX()) / metrics.scale();
        float my = (float) (mouseY - metrics.offsetY()) / metrics.scale();
        if (selected == Category.CONFIG) {
            configScrollTarget -= (float) verticalAmount * 34.0f;
            configScrollTarget = Math.max(0.0f, Math.min(configScrollTarget,
                Math.max(0.0f, configContentHeight - configView)));
            return true;
        }
        if (metrics.theme().contains(mx, my) && themeOpen && sidePanel == SidePanel.NONE) {
            interfaceScrollTarget -= (float) verticalAmount * 34.0f;
            interfaceScrollTarget = Math.max(0.0f, Math.min(interfaceScrollTarget,
                Math.max(0.0f, interfaceContentHeight - interfaceViewHeight)));
            return true;
        }
        if (metrics.profile().contains(mx, my)) {
            panelScrollTarget -= (float) verticalAmount * 34.0f;
            panelScrollTarget = Math.max(0.0f, Math.min(panelScrollTarget,
                Math.max(0.0f, panelContentHeight - panelView)));
            return true;
        }
        if (metrics.center().contains(mx, my)) {
            scrollTarget -= (float) verticalAmount * 34.0f;
            scrollTarget = Math.max(0.0f, Math.min(scrollTarget,
                Math.max(0.0f, contentHeight - contentView)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (configInputFocused) {
            if (configName.charTyped(input)) {
                lastTyped = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        if (sideInputFocused && sideInput.charTyped(input)) {
            lastTyped = System.currentTimeMillis();
            return true;
        }
        if (searchFocused && search.charTyped(input)) {
            lastTyped = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        for (ModuleInfo module : ModuleRegistry.all()) {
            if (!module.binding()) {
                continue;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                module.clearBinding();
            } else {
                module.resolveBinding(input.key());
            }
            return true;
        }
        KeybindSetting listening = findListeningKeybind();
        if (listening != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                listening.clear();
            } else {
                listening.resolve(KeyNames.of(input.key()));
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (sideInputFocused) {
                sideInputFocused = false;
                return true;
            }
            if (settingsOpen) {
                settingsOpen = false;
                return true;
            }
            if (profileOpen) {
                profileOpen = false;
                return true;
            }
            if (themeOpen) {
                themeOpen = false;
                return true;
            }
            if (openColor != null) {
                if (openColor.open()) {
                    openColor.toggleOpen();
                }
                openColor = null;
                return true;
            }
            if (openMode != null) {
                openMode.close();
                openMode = null;
                return true;
            }
            if (configInputFocused) {
                configInputFocused = false;
                return true;
            }
            if (searchFocused) {
                searchFocused = false;
                search.clear();
                return true;
            }
            close();
            return true;
        }

        if (sideInputFocused && sideInput.keyPressed(input)) {
            lastTyped = System.currentTimeMillis();
            return true;
        }
        if (configInputFocused && configName.keyPressed(input)) {
            lastTyped = System.currentTimeMillis();
            return true;
        }
        if (searchFocused && search.keyPressed(input)) {
            lastTyped = System.currentTimeMillis();
            return true;
        }

        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && (input.key() == GLFW.GLFW_KEY_LEFT || input.key() == GLFW.GLFW_KEY_RIGHT)) {
            ModuleRegistry.cycleGuiStyle(input.key() == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(input);
    }

    private KeybindSetting findListeningKeybind() {
        if (focused == null) {
            return null;
        }
        for (Setting setting : focused.leftSettings()) {
            if (setting instanceof KeybindSetting key && key.listening()) {
                return key;
            }
        }
        for (Setting setting : focused.rightSettings()) {
            if (setting instanceof KeybindSetting key && key.listening()) {
                return key;
            }
        }
        return null;
    }

    private void selectCategory(Category category) {
        selected = category;
        sidePanel = SidePanel.NONE;
        focused = null;
        if (category != Category.CONFIG) {
            List<ModuleInfo> modules = ModuleRegistry.byCategory(category);
            focused = modules.isEmpty() ? null : modules.get(0);
        }
        search.clear();
        searchFocused = false;
        searchAll = false;
        scrollTarget = 0.0f;
        panelScrollTarget = 0.0f;
        openMode = null;
        openColor = null;
        playClick();
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 3500L;
    }

    private void playClick() {
        LinkenSounds.playUiClick();
    }

    private void playToggle(boolean enabled) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(
                enabled ? LinkenVisuals.TOGGLE_ON_SOUND : LinkenVisuals.TOGGLE_OFF_SOUND, 1.0f, 1.0f));
        }
    }

    private record NavHit(Category category, Rect bounds) {
    }

    private record CardHit(ModuleInfo module, Rect bounds, boolean visible, Rect pin) {
    }

    private record SettingHit(Setting setting, Rect bounds) {
    }

    private record PinHit(ModuleInfo module, Rect toggle, Rect unpin) {
    }

    private record ConfigHit(String profile, Rect row, Rect load, Rect save, Rect delete) {
    }

    private record OptionHit(ModeSetting mode, int index, Rect bounds) {
    }
    private record ThemeHit(LinkGuiTheme.Preset preset, Rect bounds) {
    }

    private enum SidePanel { NONE, FRIENDS, WAYPOINTS }

    private record UtilityHit(Runnable action, Rect bounds) {
    }

    private static final class ExitCard {
        private final ModuleInfo module;
        private final float x;
        private final float y;
        private float alpha;

        private ExitCard(ModuleInfo module, float x, float y, float alpha) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.alpha = alpha;
        }
    }

    private record SideRemoveHit(String name, Rect bounds) {
    }
}
