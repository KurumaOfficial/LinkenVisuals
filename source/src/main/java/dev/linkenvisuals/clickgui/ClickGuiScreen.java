package dev.linkenvisuals.clickgui;

import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.clickgui.setting.BooleanSetting;
import dev.linkenvisuals.clickgui.setting.ColorSetting;
import dev.linkenvisuals.clickgui.setting.KeybindSetting;
import dev.linkenvisuals.clickgui.setting.ModeSetting;
import dev.linkenvisuals.clickgui.setting.Setting;
import dev.linkenvisuals.clickgui.setting.SliderSetting;
import dev.linkenvisuals.render.CardPipeline;
import dev.linkenvisuals.render.CirclePipeline;
import dev.linkenvisuals.render.ColorFieldPipeline;
import dev.linkenvisuals.render.GlassWindowPipeline;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.NeonEdge;
import dev.linkenvisuals.render.PanelPipeline;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.ui.Spring;
import dev.linkenvisuals.ui.Theme;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClickGuiScreen extends Screen {
    @Override
    public void renderBackground(net.minecraft.client.gui.DrawContext context, int mouseX,
                                 int mouseY, float delta) {
        dev.linkenvisuals.clickgui.link.LinkGuiTheme.refreshCustom();
        if (ModuleRegistry.interfaceBlur()) {
            super.renderBackground(context, mouseX, mouseY, delta);
        } else {
            context.fill(0, 0, width, height, ColorMath.withAlpha(0x000000, 150));
        }
    }

    private static final Identifier LOGO = LinkenVisuals.id("textures/gui/linken_logo.png");
    private static final Identifier SEARCH_ICON = LinkenVisuals.id("textures/gui/ico_search.png");

    private static final float WINDOW_W = 496.0f;
    private static final float WINDOW_H = 271.0f;

    private static final float HEADER_H = 33.0f;
    private static final float EDGE = 6.0f;
    static final float CONTENT_BOTTOM = 265.0f;

    private static final float HEADER_BOX_Y = 4.0f;
    private static final float HEADER_BOX_H = 25.0f;

    private static final float RAIL_BOX_W = 24.0f;
    private static final float RAIL_BOX_X = WINDOW_W - 8.0f - RAIL_BOX_W;
    private static final float RAIL_CENTER_X = RAIL_BOX_X + RAIL_BOX_W * 0.5f;
    static final float RAIL_DIVIDER_X = 455.0f;
    private static final float RAIL_LOGO_Y = 17.0f;
    private static final float RAIL_FIRST_Y = 49.0f;
    private static final float RAIL_SPACING = 25.0f;
    private static final float RAIL_ICON = 11.0f;
    private static final float RAIL_HIT = 22.0f;
    private static final float SEARCH_Y = 243.0f;
    private static final float TICK_W = 6.0f;

    private static final float LIST_X = 6.0f;
    private static final float LIST_W = 182.0f;
    static final float LIST_TOP = 39.0f;
    private static final float CARD_H = 22.0f;
    private static final float CARD_GAP = 4.0f;
    private static final float SEARCH_BAR_H = 22.0f;

    private static final float PANEL_X = 196.0f;
    private static final float PANEL_RIGHT = 447.0f;
    private static final float PANEL_TOP = 42.0f;
    private static final float PANEL_PAD = 6.0f;
    private static final float ROW_FIRST = 72.0f;
    private static final float ROW_GAP = 4.0f;

    private GlyphAtlas text;
    private GlyphAtlas bold;

    private final Spring openSpring = new Spring(0.0, 900.0, 42.0);
    private final Spring scrollSpring = new Spring(0.0, 1100.0, 60.0);
    private final Map<Category, Spring> railHover = new EnumMap<>(Category.class);

    private final Spring railIndicator = new Spring(0.0, 620.0, 42.0);

    private static Category selected = Category.VISUAL;
    private static ModuleInfo focused;
    private static final dev.linkenvisuals.util.input.TextInput search =
        new dev.linkenvisuals.util.input.TextInput().maxLength(64);
    private static boolean searchOpen;
    private boolean closing;
    private long lastFrame = System.nanoTime();
    private static float scrollTarget;
    private float contentHeight;
    private float listViewHeight = 1.0f;

    private SliderSetting draggingSlider;
    private ColorSetting draggingColor;
    private float dragColorX;
    private float dragColorY;
    private float dragColorRight;
    private float dragTrackX;
    private float dragTrackWidth;

    private final List<CardBounds> cardBounds = new ArrayList<>();
    private final List<SettingBounds> settingBounds = new ArrayList<>();
    private float panelBottom;
    private static float panelScrollTarget;
    private final Spring panelScrollSpring = new Spring(0.0, 1100.0, 60.0);
    private float panelContentHeight;
    private float panelViewHeight = 1.0f;

    private final Spring caretSpring = new Spring(0.0, 1400.0, 70.0);
    private float caretTarget;
    private static long lastTyped;

    private final ConfigPanel configPanel = new ConfigPanel();

    private float openAge;
    private float listAge;
    private float panelAge;
    private Category stagedCategory;
    private ModuleInfo stagedModule;

    private float headerSwitchX;
    private float headerSwitchY;

    public ClickGuiScreen() {
        super(Text.empty());
    }

    @Override
    protected void init() {
        ModuleRegistry.syncFromMenuConfig();
        text = Fonts.textFont();
        bold = Fonts.titleFont();
        configPanel.bind(text, bold, this::scrollbar);
        for (Category category : Category.values()) {
            railHover.computeIfAbsent(category, key -> new Spring(0.0, 900.0, 48.0));
        }
        closing = false;
        lastFrame = System.nanoTime();

        scrollSpring.reset(scrollTarget);
        panelScrollSpring.reset(panelScrollTarget);

        Category[] all = Category.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == selected) {
                railIndicator.reset(i);
            }
        }
        if (focused == null) {
            List<ModuleInfo> modules = ModuleRegistry.byCategory(selected);
            focused = modules.isEmpty() ? null : modules.get(0);
        }
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(LinkenVisuals.OPEN_SOUND, 1.0f, 0.85f));
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
        closing = true;

        if (client != null && MenuCloseEffect.start(windowX(), windowY(), WINDOW_W, WINDOW_H)) {
            client.setScreen(null);
        }
    }

    private float windowX() {
        return Math.round((width - WINDOW_W) * 0.5f);
    }

    private float windowY() {
        return Math.round((height - WINDOW_H) * 0.5f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        double deltaSeconds = Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
        lastFrame = now;
        dev.linkenvisuals.clickgui.link.LinkGuiTheme.refreshCustom();
        dev.linkenvisuals.clickgui.link.LinkGuiTheme.tick((float) deltaSeconds);

        if (stagedCategory != selected) {
            stagedCategory = selected;
            listAge = 0.0f;
        }
        if (stagedModule != focused) {
            stagedModule = focused;
            panelAge = 0.0f;
        }
        listAge += (float) deltaSeconds;
        panelAge += (float) deltaSeconds;

        openSpring.step(closing ? 0.0 : 1.0, deltaSeconds);
        float openT = (float) openSpring.get();
        if (closing && openT <= 0.01f) {
            if (client != null) {
                client.setScreen(null);
            }
            return;
        }

        // Vanilla Screen#renderBackground already applies the post-process blur once per
        // frame; calling DrawContext#applyBlur again throws "Can only blur once per frame".
        context.fill(0, 0, width, height, ColorMath.withAlpha(0x000000, Math.round(80 * openT)));

        float slide = Math.round((1.0f - openT) * 14.0f);
        float wx = windowX();
        float wy = windowY();

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(0.0f, slide);

        GlassWindowPipeline.draw(context, wx, wy, WINDOW_W, WINDOW_H, ColorMath.withAlpha(Theme.glassTint(), Math.round(238 * openT)));
        RoundedRectPipeline.draw(context, wx, wy, WINDOW_W, WINDOW_H,
            ColorMath.withAlpha(0x101116, Math.round(232 * openT)));
        renderSheen(context, wx, wy, openT);
        renderFrameGlow(context, wx, wy, openT);

        openAge += (float) deltaSeconds * (closing ? -3.2f : 1.0f);
        openAge = Math.max(0.0f, Math.min(1.4f, openAge));

        region(context, wx + (EDGE + RAIL_DIVIDER_X) * 0.5f, wy + HEADER_H * 0.5f, 0.0f,
            () -> renderHeader(context, wx, wy, mouseX, mouseY, openT * phase(0.0f)));
        if (selected == Category.CONFIG) {
            region(context, wx + (EDGE + RAIL_DIVIDER_X) * 0.5f, wy + (LIST_TOP + CONTENT_BOTTOM) * 0.5f, 0.1f,
                () -> configPanel.render(context, wx, wy, mouseX, mouseY, deltaSeconds, openT * phase(0.1f)));
        } else {
            region(context, wx + LIST_X + LIST_W * 0.5f, wy + (LIST_TOP + CONTENT_BOTTOM) * 0.5f, 0.07f,
                () -> renderList(context, wx, wy, mouseX, mouseY, deltaSeconds, openT * phase(0.07f)));
            region(context, wx + (PANEL_X + PANEL_RIGHT) * 0.5f, wy + (PANEL_TOP + CONTENT_BOTTOM) * 0.5f,
                0.15f,
                () -> renderPanel(context, wx, wy, mouseX, mouseY, deltaSeconds, openT * phase(0.15f)));
        }
        region(context, wx + RAIL_CENTER_X, wy + WINDOW_H * 0.5f, 0.23f,
            () -> renderRail(context, wx, wy, mouseX, mouseY, deltaSeconds, openT * phase(0.23f)));

        matrices.popMatrix();
    }

    private void renderSheen(DrawContext context, float wx, float wy, float openT) {
        float cycle = (System.currentTimeMillis() % 11000L) / 11000.0f;
        if (cycle > 0.26f) {
            return;
        }
        float travel = cycle / 0.26f;
        float strength = (float) Math.sin(travel * Math.PI);
        GlowPipeline.lineVertical(context, wx - 30.0f + travel * (WINDOW_W + 60.0f),
            wy + WINDOW_H * 0.5f, 46.0f, WINDOW_H,
            ColorMath.withAlpha(0xFFFFFF, Math.round(11 * strength * openT)));
    }

    private float phase(float delay) {
        float t = Math.max(0.0f, Math.min(1.0f, (openAge - delay) / 0.3f));
        return t * t * (3.0f - 2.0f * t);
    }

    private void region(DrawContext context, float centerX, float centerY, float delay,
                        Runnable body) {
        float t = phase(delay);
        if (t >= 0.999f) {
            body.run();
            return;
        }
        if (t <= 0.001f) {
            return;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(0.93f + 0.07f * t, 0.82f + 0.18f * t);
        matrices.translate(-centerX, -centerY);
        body.run();
        matrices.popMatrix();
    }

    private void renderFrameGlow(DrawContext context, float wx, float wy, float openT) {
        NeonEdge.frame(context, wx, wy, WINDOW_W, WINDOW_H, 5, openT * 0.55f);
        RoundedRectPipeline.drawOutline(context, wx, wy, WINDOW_W, WINDOW_H,
            ColorMath.withAlpha(0xFFFFFF, Math.round(30 * openT)));
    }

    private void drawContainer(DrawContext context, float x, float y, float w, float h, float openT) {
        RoundedRectPipeline.draw(context, x, y, w, h, ColorMath.withAlpha(0x0A0B0E, Math.round(150 * openT)));
        RoundedRectPipeline.drawOutline(context, x, y, w, h, ColorMath.withAlpha(0xFFFFFF, Math.round(20 * openT)));
    }

    private void drawContainerLight(DrawContext context, float x, float y, float w, float h, float openT) {
        RoundedRectPipeline.draw(context, x, y, w, h, ColorMath.withAlpha(0xFFFFFF, Math.round(9 * openT)));
        RoundedRectPipeline.drawOutline(context, x, y, w, h, ColorMath.withAlpha(0xFFFFFF, Math.round(22 * openT)));
    }

    private void drawTick(DrawContext context, float cx, float y, float openT) {
        context.fill(Math.round(cx - TICK_W * 0.5f), Math.round(y), Math.round(cx + TICK_W * 0.5f), Math.round(y) + 1,
            ColorMath.withAlpha(0xD9D9D9, Math.round(23 * openT)));
    }

    private void renderHeader(DrawContext context, float wx, float wy, int mouseX, int mouseY, float openT) {
        float boxY = wy + HEADER_BOX_Y;
        float boxRight = wx + RAIL_DIVIDER_X - 3.0f;
        float boxMid = boxY + HEADER_BOX_H * 0.5f;
        drawContainerLight(context, wx + EDGE, boxY, boxRight - (wx + EDGE), HEADER_BOX_H, openT);

        float markR = 6.5f;
        float markX = wx + EDGE + 12.0f;
        GlowPipeline.radial(context, markX, boxMid, markR * 2.0f, ColorMath.withAlpha(NeonEdge.cool(), Math.round(70 * openT)));
        CirclePipeline.draw(context, markX, boxMid, markR, ColorMath.withAlpha(0xFFFFFF, Math.round(26 * openT)));
        CirclePipeline.draw(context, markX, boxMid, markR - 1.6f, ColorMath.withAlpha(NeonEdge.cool(), Math.round(200 * openT)));

        String username = client != null && client.getSession() != null ? client.getSession().getUsername() : "Player";
        MsdfText.draw(context, bold, username, markX + markR + 8.0f, boxMid - 3.6f, 7.2f,
            ColorMath.withAlpha(0xFFFFFF, Math.round(205 * openT)));

        String section = selected.label().toUpperCase(Locale.ROOT);
        float sectionW = MsdfText.width(bold, section, 6.0f);
        MsdfText.draw(context, bold, section, boxRight - 11.0f - sectionW,
            boxMid - 3.0f, 6.0f, ColorMath.withAlpha(0xFFFFFF, Math.round(95 * openT)));

        context.fill(Math.round(wx + EDGE), Math.round(wy + HEADER_H), Math.round(boxRight),
            Math.round(wy + HEADER_H) + 1, ColorMath.withAlpha(0xFFFFFF, Math.round(26 * openT)));
        context.fill(Math.round(wx + RAIL_DIVIDER_X), Math.round(wy + EDGE), Math.round(wx + RAIL_DIVIDER_X) + 1,
            Math.round(wy + CONTENT_BOTTOM), ColorMath.withAlpha(0xFFFFFF, Math.round(22 * openT)));
        if (selected != Category.CONFIG) {
            float columnX = wx + (LIST_X + LIST_W + PANEL_X) * 0.5f;
            context.fill(Math.round(columnX), Math.round(wy + LIST_TOP), Math.round(columnX) + 1,
                Math.round(wy + CONTENT_BOTTOM), ColorMath.withAlpha(0xFFFFFF, Math.round(13 * openT)));
        }
    }

    private void renderRail(DrawContext context, float wx, float wy, int mouseX, int mouseY, double deltaSeconds, float openT) {
        Category[] categories = Category.values();
        int selectedIndex = 0;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i] == selected) {
                selectedIndex = i;
            }
        }
        railIndicator.step(selectedIndex, deltaSeconds);

        float boxTop = wy + HEADER_BOX_Y;
        float boxHeight = wy + CONTENT_BOTTOM - boxTop;
        drawContainerLight(context, wx + RAIL_BOX_X, boxTop, RAIL_BOX_W, boxHeight, openT);

        float logoSize = 15.0f;
        float logoCy = wy + RAIL_LOGO_Y;
        GlowPipeline.radial(context, wx + RAIL_CENTER_X, logoCy, logoSize * 1.3f, ColorMath.withAlpha(0xFFFFFF, Math.round(85 * openT)));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO,
            Math.round(wx + RAIL_CENTER_X - logoSize * 0.5f), Math.round(logoCy - logoSize * 0.5f), 0.0f, 0.0f,
            Math.round(logoSize), Math.round(logoSize), 128, 128, 128, 128, ColorMath.withAlpha(0xFFFFFF, Math.round(255 * openT)));

        drawTick(context, wx + RAIL_CENTER_X, (logoCy + wy + RAIL_FIRST_Y) * 0.5f, openT);

        float markerY = wy + RAIL_FIRST_Y + (float) railIndicator.get() * RAIL_SPACING;
        RoundedRectPipeline.draw(context, wx + RAIL_CENTER_X - 9.0f, markerY - 9.0f, 18.0f, 18.0f,
            ColorMath.withAlpha(0xFFFFFF, Math.round(20 * openT)));
        GlowPipeline.radial(context, wx + RAIL_CENTER_X, markerY, 14.0f,
            ColorMath.withAlpha(NeonEdge.cool(), Math.round(70 * openT)));

        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            float cx = wx + RAIL_CENTER_X;
            float cy = wy + RAIL_FIRST_Y + i * RAIL_SPACING;
            boolean hovered = Math.abs(mouseX - cx) <= RAIL_HIT * 0.5f && Math.abs(mouseY - cy) <= RAIL_HIT * 0.5f;
            Spring spring = railHover.get(category);
            spring.step(hovered ? 1.0 : 0.0, deltaSeconds);
            float hover = (float) spring.get();
            boolean active = category == selected;
            float nearness = 1.0f - Math.min(1.0f, Math.abs((float) railIndicator.get() - i));

            int tint = Math.round(105 + hover * 70 + nearness * 150);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, category.icon(),
                Math.round(cx - RAIL_ICON * 0.5f), Math.round(cy - RAIL_ICON * 0.5f), 0.0f, 0.0f,
                Math.round(RAIL_ICON), Math.round(RAIL_ICON), 48, 48, 48, 48,
                ColorMath.withAlpha(ColorMath.mix(0xFFFFFF, NeonEdge.cool(), nearness), Math.round(tint * openT)));

            if (i < categories.length - 1) {
                drawTick(context, cx, cy + RAIL_SPACING * 0.5f, openT);
            }
        }

        float searchCx = wx + RAIL_CENTER_X;
        float searchCy = wy + SEARCH_Y;
        drawTick(context, searchCx, searchCy - RAIL_SPACING * 0.5f, openT);
        if (searchOpen) {
            RoundedRectPipeline.draw(context, searchCx - 9.0f, searchCy - 9.0f, 18.0f, 18.0f, ColorMath.withAlpha(0xFFFFFF, Math.round(20 * openT)));
            GlowPipeline.radial(context, searchCx, searchCy, 13.0f, ColorMath.withAlpha(0xFFFFFF, Math.round(45 * openT)));
        }
        context.drawTexture(RenderPipelines.GUI_TEXTURED, SEARCH_ICON,
            Math.round(searchCx - RAIL_ICON * 0.5f), Math.round(searchCy - RAIL_ICON * 0.5f), 0.0f, 0.0f,
            Math.round(RAIL_ICON), Math.round(RAIL_ICON), 48, 48, 48, 48,
            ColorMath.withAlpha(0xFFFFFF, Math.round((searchOpen ? 245 : 115) * openT)));
    }

    private void renderList(DrawContext context, float wx, float wy, int mouseX, int mouseY, double deltaSeconds, float openT) {
        float listTop = wy + LIST_TOP;
        if (searchOpen) {
            renderSearchBar(context, wx + LIST_X, listTop, openT, deltaSeconds);
            listTop += SEARCH_BAR_H + CARD_GAP;
        }
        float listBottom = wy + CONTENT_BOTTOM;
        listViewHeight = listBottom - listTop;

        List<ModuleInfo> modules = visibleModules();
        contentHeight = Math.max(0.0f, modules.size() * (CARD_H + CARD_GAP) - CARD_GAP);
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget, Math.max(0.0f, contentHeight - listViewHeight)));
        scrollSpring.step(scrollTarget, deltaSeconds);
        float scroll = (float) scrollSpring.get();

        context.enableScissor(Math.round(wx + LIST_X), Math.round(listTop),
            Math.round(wx + LIST_X + LIST_W), Math.round(listBottom));

        cardBounds.clear();
        for (int i = 0; i < modules.size(); i++) {
            ModuleInfo module = modules.get(i);
            float cardY = listTop + i * (CARD_H + CARD_GAP) - scroll;
            cardBounds.add(new CardBounds(module, wx + LIST_X, cardY));
            if (cardY + CARD_H < listTop || cardY > listBottom) {
                continue;
            }
            float deal = stagger(listAge, i);
            renderCard(context, module, wx + LIST_X - (1.0f - deal) * 18.0f, cardY,
                mouseX, mouseY, deltaSeconds, openT * deal);
        }
        context.disableScissor();
        scrollbar(context, wx + LIST_X + LIST_W - 2.5f, listTop, listBottom - listTop,
            contentHeight, scroll, openT);
    }

    private void scrollbar(DrawContext context, float x, float y, float viewHeight,
                           float contentSize, float scroll, float openT) {
        if (contentSize <= viewHeight + 1.0f || viewHeight <= 0.0f) {
            return;
        }
        float share = Math.min(1.0f, viewHeight / contentSize);
        float barHeight = Math.max(14.0f, viewHeight * share);
        float travel = viewHeight - barHeight;
        float atShare = Math.max(0.0f, Math.min(1.0f, scroll / (contentSize - viewHeight)));
        float barY = y + travel * atShare;
        PanelPipeline.draw(context, x, y, 1.5f, viewHeight, ColorMath.withAlpha(0xFFFFFF, Math.round(12 * openT)));
        PanelPipeline.draw(context, x, barY, 1.5f, barHeight, ColorMath.withAlpha(0xFFFFFF, Math.round(70 * openT)));
    }

    private static float stagger(float age, int index) {
        float start = index * 0.038f;
        float t = Math.max(0.0f, Math.min(1.0f, (age - start) / 0.26f));
        return t * t * (3.0f - 2.0f * t);
    }

    private void renderSearchBar(DrawContext context, float x, float y, float openT, double deltaSeconds) {
        drawContainer(context, x, y, LIST_W, SEARCH_BAR_H, openT);

        RoundedRectPipeline.drawOutline(context, x, y, LIST_W, SEARCH_BAR_H,
            ColorMath.withAlpha(NeonEdge.cool(), Math.round(70 * openT)));

        String query = search.value();
        float textX = x + 13.0f;
        float textY = y + (SEARCH_BAR_H - 7.0f) * 0.5f;

        if (query.isEmpty()) {
            MsdfText.draw(context, text, I18n.get("gui.search.placeholder"), textX + 4.0f, textY, 7.0f, ColorMath.withAlpha(0xFFFFFF, Math.round(70 * openT)));
        } else {
            MsdfText.draw(context, bold, query, textX, textY, 7.0f, ColorMath.withAlpha(0xFFFFFF, Math.round(240 * openT)));
        }

        String beforeCaret = query.substring(0, Math.min(search.caret(), query.length()));
        caretTarget = textX + MsdfText.width(bold, beforeCaret, 7.0f) + 1.0f;
        caretSpring.step(caretTarget, deltaSeconds);
        float caretX = (float) caretSpring.get();

        float sinceType = (System.currentTimeMillis() - lastTyped) / 1000.0f;
        float blink = sinceType < 0.6f ? 1.0f
            : 0.45f + 0.55f * (float) Math.sin(System.currentTimeMillis() / 1000.0 * 3.4);

        GlowPipeline.radial(context, caretX, y + SEARCH_BAR_H * 0.5f, 7.0f,
            ColorMath.withAlpha(NeonEdge.cool(), Math.round(70 * blink * openT)));
        PanelPipeline.draw(context, caretX, y + 5.5f, 1.3f, SEARCH_BAR_H - 11.0f,
            ColorMath.withAlpha(Theme.lighten(NeonEdge.cool(), 0.2f), Math.round(240 * blink * openT)));
    }

    private void renderCard(DrawContext context, ModuleInfo module, float x, float y, int mouseX, int mouseY,
                             double deltaSeconds, float openT) {
        boolean hovered = mouseX >= x && mouseX <= x + LIST_W && mouseY >= y && mouseY <= y + CARD_H;
        module.hover.step(hovered ? 1.0 : 0.0, deltaSeconds);
        float hover = (float) module.hover.get();
        module.enableGlow.step(module.enabled() ? 1.0 : 0.0, deltaSeconds);
        float on = (float) module.enableGlow.get();
        boolean isFocused = module == focused;

        float presence = Math.max(on, Math.max(hover * 0.55f, isFocused ? 0.45f : 0.0f));

        RoundedRectPipeline.draw(context, x, y, LIST_W, CARD_H,
            ColorMath.withAlpha(0x16171C, Math.round((118 + hover * 46) * openT)));
        if (isFocused) {
            RoundedRectPipeline.draw(context, x, y, LIST_W, CARD_H,
                ColorMath.withAlpha(0xFFFFFF, Math.round(12 * openT)));
        }
        if (presence > 0.02f) {
            RoundedRectPipeline.drawOutline(context, x, y, LIST_W, CARD_H,
                ColorMath.withAlpha(ColorMath.mix(0xFFFFFF, NeonEdge.cool(), on), Math.round((18 + on * 64) * openT * presence)));
        }

        if (on > 0.02f) {
            PanelPipeline.draw(context, x + 3.0f, y + 5.5f, 1.6f, CARD_H - 11.0f,
                ColorMath.withAlpha(NeonEdge.cool(), Math.round(235 * on * openT)));
        }

        float textX = x + 10.0f + hover * 2.5f;
        int nameAlpha = Math.round((70 + presence * 175) * openT);

        MsdfText.draw(context, bold, module.name(), textX, MsdfText.topFor(y + CARD_H * 0.5f, 7.4f), 7.4f,
            ColorMath.withAlpha(0xFFFFFF, nameAlpha));

        float sinceToggle = (System.currentTimeMillis() - module.lastToggle()) / 420.0f;
        if (sinceToggle >= 0.0f && sinceToggle < 1.0f) {
            float sweepX = x + sinceToggle * (LIST_W + 40.0f) - 20.0f;
            float strength = (1.0f - sinceToggle) * openT;
            GlowPipeline.lineVertical(context, sweepX, y + CARD_H * 0.5f, 26.0f, CARD_H,
                ColorMath.withAlpha(NeonEdge.cool(), Math.round(120 * strength)));
        }

        float pillW = 14.0f;
        float pillH = 8.0f;
        float pillX = x + LIST_W - 8.0f - pillW;
        float pillY = y + (CARD_H - pillH) * 0.5f;
        RoundedRectPipeline.draw(context, pillX, pillY, pillW, pillH,
            ColorMath.withAlpha(ColorMath.mix(0x2A2C33, NeonEdge.cool(), on), Math.round((150 + on * 90) * openT)));
        float knob = pillH - 3.0f;
        CirclePipeline.draw(context, pillX + 1.5f + knob * 0.5f + on * (pillW - 3.0f - knob),
            pillY + pillH * 0.5f, knob * 0.5f,
            ColorMath.withAlpha(ColorMath.mix(0x8A8D96, 0xFFFFFF, on), Math.round((200 + on * 55) * openT)));

        String key = keyLabel(module);
        if (key != null) {
            boolean arming = module.binding();
            float chipW = Math.max(15.0f, MsdfText.width(bold, key, 5.8f) + 8.0f);
            float chipX = pillX - 6.0f - chipW;
            float chipH = 10.0f;
            float chipY = y + (CARD_H - chipH) * 0.5f;
            RoundedRectPipeline.draw(context, chipX, chipY, chipW, chipH,
                ColorMath.withAlpha(arming ? NeonEdge.cool() : 0x000000, Math.round((arming ? 55 : 60) * openT)));
            if (arming) {
                RoundedRectPipeline.drawOutline(context, chipX, chipY, chipW, chipH,
                    ColorMath.withAlpha(NeonEdge.cool(), Math.round(150 * openT)));
            }
            MsdfText.drawCentered(context, bold, key, chipX + chipW * 0.5f,
                MsdfText.topFor(chipY + 5.0f, 5.8f), 5.8f,
                ColorMath.withAlpha(arming ? NeonEdge.cool() : 0xFFFFFF,
                    Math.round((arming ? 235 : 70 + presence * 110) * openT)));
        }
    }

    private void renderPanel(DrawContext context, float wx, float wy, int mouseX, int mouseY, double deltaSeconds, float openT) {
        settingBounds.clear();
        if (focused == null) {
            MsdfText.drawCentered(context, text, "No module selected", wx + (PANEL_X + PANEL_RIGHT) * 0.5f,
                wy + 130.0f, 7.5f, ColorMath.withAlpha(0xFFFFFF, Math.round(70 * openT)));
            return;
        }

        panelBottom = wy + CONTENT_BOTTOM;
        openDropdown = null;
        openPicker = null;
        float left = wx + PANEL_X + PANEL_PAD;
        float right = wx + PANEL_RIGHT;
        float headerY = wy + PANEL_TOP;

        float iconSize = 12.0f;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, selected.icon(),
            Math.round(left), Math.round(headerY), 0.0f, 0.0f,
            Math.round(iconSize), Math.round(iconSize), 128, 128, 128, 128, ColorMath.withAlpha(0xFFFFFF, Math.round(190 * openT)));
        float dividerX = left + iconSize + 7.0f;
        context.fill(Math.round(dividerX), Math.round(headerY + 1.0f), Math.round(dividerX) + 1,
            Math.round(headerY + iconSize - 1.0f), ColorMath.withAlpha(0xFFFFFF, Math.round(38 * openT)));
        MsdfText.draw(context, bold, focused.name(), dividerX + 8.0f, headerY - 1.0f, 8.0f,
            ColorMath.withAlpha(0xFFFFFF, Math.round(225 * openT)));

        MsdfText.draw(context, text, focused.description(), dividerX + 8.0f, headerY + 9.0f, 6.0f,
            ColorMath.withAlpha(0xFFFFFF, Math.round(95 * openT)));

        focused.enableGlow.step(focused.enabled() ? 1.0 : 0.0, deltaSeconds);
        float moduleOn = (float) focused.enableGlow.get();

        headerSwitchX = right - 14.0f;
        headerSwitchY = headerY + (iconSize - 8.0f) * 0.5f;
        drawPill(context, headerSwitchX, headerSwitchY, moduleOn, openT);

        float underline = headerY + iconSize + 8.0f;
        context.fill(Math.round(left), Math.round(underline), Math.round(right), Math.round(underline) + 1,
            ColorMath.withAlpha(0xFFFFFF, Math.round(20 * openT)));

        List<Setting> settings = new ArrayList<>(focused.leftSettings());
        settings.addAll(focused.rightSettings());

        panelViewHeight = wy + CONTENT_BOTTOM - (wy + ROW_FIRST) + 10.0f;
        panelScrollSpring.step(panelScrollTarget, deltaSeconds);
        float panelScroll = (float) panelScrollSpring.get();

        context.enableScissor(Math.round(left - 6.0f), Math.round(underline + 4.0f),
            Math.round(right + 8.0f), Math.round(wy + CONTENT_BOTTOM));

        float rowY = wy + ROW_FIRST - panelScroll;
        float contentTop = rowY;
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            float cardH = rowHeight(setting, right - left);
            float stride = cardH + ROW_GAP;
            if (rowY > wy + CONTENT_BOTTOM - 4.0f || rowY + cardH < wy + ROW_FIRST - 20.0f) {
                rowY += stride;
                continue;
            }
            settingBounds.add(new SettingBounds(setting, left, rowY));
            float deal = stagger(panelAge, i);
            boolean over = mouseX >= left && mouseX <= right && mouseY >= rowY
                && mouseY <= rowY + cardH;
            renderSetting(context, setting, left + (1.0f - deal) * 14.0f, rowY, right,
                deltaSeconds, openT * deal, over);
            rowY += stride;
        }
        panelContentHeight = rowY - contentTop;
        context.disableScissor();
        scrollbar(context, right + 3.0f, wy + ROW_FIRST, wy + CONTENT_BOTTOM - (wy + ROW_FIRST),
            panelContentHeight, panelScroll, openT);

        renderPicker(context, openT);
        renderDropdown(context, openT, mouseX, mouseY);
    }

    private float rowHeight(Setting setting, float width) {
        if (setting instanceof SliderSetting) {
            return 25.0f;
        }
        if (setting instanceof ModeSetting mode) {
            return chipped(mode) ? 15.0f + chipRows(mode, width - 12.0f) * 12.0f + 3.0f : 20.0f;
        }
        return 19.0f;
    }

    private boolean chipped(ModeSetting mode) {
        return mode.options().size() <= 4;
    }

    private float chipWidth(String option) {
        return MsdfText.width(text, option, 6.0f) + 11.0f;
    }

    private int chipRows(ModeSetting mode, float available) {
        int rows = 1;
        float used = 0.0f;
        for (String option : mode.options()) {
            float chip = chipWidth(option);
            if (used + chip > available && used > 0.0f) {
                rows++;
                used = 0.0f;
            }
            used += chip + 3.0f;
        }
        return rows;
    }

    private void renderSetting(DrawContext context, Setting setting, float x, float y, float right,
                                double deltaSeconds, float openT, boolean hovered) {
        float w = right - x;
        float h = rowHeight(setting, w);
        int accent = NeonEdge.cool();

        RoundedRectPipeline.draw(context, x, y, w, h,
            ColorMath.withAlpha(0x1A1B21, Math.round((110 + (hovered ? 34 : 0)) * openT)));
        if (hovered) {
            RoundedRectPipeline.drawOutline(context, x, y, w, h, ColorMath.withAlpha(0xFFFFFF, Math.round(20 * openT)));
        }

        boolean stacked = setting instanceof SliderSetting
            || (setting instanceof ModeSetting stack && chipped(stack));
        float labelCenter = stacked ? y + 8.0f : y + h * 0.5f;
        float labelY = MsdfText.topFor(labelCenter, 6.4f);

        MsdfText.draw(context, text, setting.name(), x + 7.0f, labelY, 6.4f,
            ColorMath.withAlpha(0xFFFFFF, Math.round((hovered ? 195 : 150) * openT)));

        if (setting instanceof dev.linkenvisuals.clickgui.setting.ActionSetting action) {
            PanelPipeline.draw(context, x + 5.0f, y + 2.0f, w - 10.0f, h - 4.0f,
                ColorMath.withAlpha(hovered ? accent : 0x222222, Math.round(200 * openT)));
            MsdfText.draw(context, bold, "Открыть гардероб", x + 10.0f, labelY, 6.4f,
                ColorMath.withAlpha(0xFFFFFF, Math.round(255 * openT)));
            return;
        }

        if (setting instanceof BooleanSetting boolSetting) {
            boolSetting.glow.step(boolSetting.value() ? 1.0 : 0.0, deltaSeconds);
            float on = (float) boolSetting.glow.get();
            drawCheck(context, right - 7.0f - 10.0f, y + (h - 10.0f) * 0.5f, on, openT);
        } else if (setting instanceof SliderSetting slider) {
            String shown = slider.display();
            MsdfText.draw(context, bold, shown, right - 7.0f - MsdfText.width(bold, shown, 6.2f),
                MsdfText.topFor(labelCenter, 6.2f), 6.2f, ColorMath.withAlpha(accent, Math.round((hovered ? 250 : 215) * openT)));

            float trackX = x + 7.0f;
            float trackW = w - 14.0f;
            float trackY = y + 16.5f;
            PanelPipeline.draw(context, trackX, trackY, trackW, 2.5f, ColorMath.withAlpha(0x000000, Math.round(120 * openT)));
            float fillW = Math.max(2.5f, trackW * slider.fraction());
            PanelPipeline.draw(context, trackX, trackY, fillW, 2.5f,
                ColorMath.withAlpha(accent, Math.round((hovered ? 255 : 220) * openT)));
            float knobX = trackX + trackW * slider.fraction();
            boolean held = draggingSlider == slider;
            CirclePipeline.draw(context, knobX, trackY + 1.25f, held ? 3.6f : 3.1f,
                ColorMath.withAlpha(0xFFFFFF, Math.round(250 * openT)));
            CirclePipeline.draw(context, knobX, trackY + 1.25f, held ? 2.1f : 1.7f,
                ColorMath.withAlpha(accent, Math.round(255 * openT)));
        } else if (setting instanceof ModeSetting mode) {
            String current = mode.current();
            if (chipped(mode)) {
                float chipX = x + 7.0f;
                float chipY = y + 15.0f;
                float available = w - 12.0f;
                float used = 0.0f;
                for (String option : mode.options()) {
                    float chip = chipWidth(option);
                    if (used + chip > available && used > 0.0f) {
                        used = 0.0f;
                        chipY += 12.0f;
                    }
                    boolean active = option.equals(current);
                    RoundedRectPipeline.draw(context, chipX + used, chipY, chip, 10.0f,
                        ColorMath.withAlpha(active ? accent : 0x000000, Math.round((active ? 60 : 70) * openT)));
                    if (active) {
                        RoundedRectPipeline.drawOutline(context, chipX + used, chipY, chip, 10.0f,
                            ColorMath.withAlpha(accent, Math.round(145 * openT)));
                    }
                    MsdfText.drawCentered(context, text, option, chipX + used + chip * 0.5f,
                        MsdfText.topFor(chipY + 5.0f, 6.0f), 6.0f,
                        ColorMath.withAlpha(active ? accent : 0xFFFFFF, Math.round((active ? 250 : 115) * openT)));
                    used += chip + 3.0f;
                }
            } else {
                float boxW = Math.max(86.0f, MsdfText.width(text, current, 6.0f) + 24.0f);
                float boxX = right - 7.0f - boxW;
                float boxY = y + (h - 12.0f) * 0.5f;
                RoundedRectPipeline.draw(context, boxX, boxY, boxW, 12.0f, ColorMath.withAlpha(0x000000, Math.round(80 * openT)));
                RoundedRectPipeline.drawOutline(context, boxX, boxY, boxW, 12.0f,
                    ColorMath.withAlpha(0xFFFFFF, Math.round((mode.open() ? 60 : 22) * openT)));
                MsdfText.draw(context, text, current, boxX + 8.0f, MsdfText.topFor(boxY + 6.0f, 6.0f), 6.0f,
                    ColorMath.withAlpha(accent, Math.round(235 * openT)));
                drawChevron(context, boxX + boxW - 7.0f, boxY + 6.0f, mode.open(),
                    ColorMath.withAlpha(0xFFFFFF, Math.round(140 * openT)));
                if (mode.open()) {
                    openDropdown = mode;
                    dropdownX = boxX;
                    dropdownY = boxY;
                    dropdownW = boxW;
                }
            }
        } else if (setting instanceof ColorSetting color) {
            renderColorSetting(context, color, x, y, right, h, openT);
        } else if (setting instanceof KeybindSetting keybind) {
            String label = keybind.label();
            float chip = Math.max(22.0f, MsdfText.width(text, label, 6.0f) + 12.0f);
            float chipX = right - 7.0f - chip;
            float chipY = y + (h - 11.0f) * 0.5f;
            RoundedRectPipeline.draw(context, chipX, chipY, chip, 11.0f, ColorMath.withAlpha(0x000000, Math.round(80 * openT)));
            RoundedRectPipeline.drawOutline(context, chipX, chipY, chip, 11.0f,
                ColorMath.withAlpha(0xFFFFFF, Math.round(22 * openT)));
            MsdfText.drawCentered(context, text, label, chipX + chip * 0.5f,
                MsdfText.topFor(chipY + 5.5f, 6.0f), 6.0f,
                ColorMath.withAlpha(0xFFFFFF, Math.round(195 * openT)));
        }
    }

    private void drawCheck(DrawContext context, float x, float y, float on, float openT) {
        float size = 10.0f;
        RoundedRectPipeline.draw(context, x, y, size, size,
            ColorMath.withAlpha(ColorMath.mix(0x000000, NeonEdge.cool(), on), Math.round((80 + on * 70) * openT)));
        RoundedRectPipeline.drawOutline(context, x, y, size, size,
            ColorMath.withAlpha(ColorMath.mix(0xFFFFFF, NeonEdge.cool(), on), Math.round((28 + on * 150) * openT)));

        if (on <= 0.02f) {
            return;
        }

        int ink = ColorMath.withAlpha(0xFFFFFF, Math.round(250 * openT));

        float cx = x + size * 0.5f - 0.67f;
        float cy = y + size * 0.5f + 1.17f;
        float thick = 1.5f;
        float shortArm = 2.4f * Math.min(1.0f, on / 0.45f);
        float longArm = 4.3f * Math.max(0.0f, (on - 0.35f) / 0.65f);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(cx, cy);
        matrices.rotate((float) Math.toRadians(45.0));
        PanelPipeline.draw(context, -shortArm, -thick * 0.5f, shortArm + thick * 0.5f, thick, ink);
        if (longArm > 0.05f) {
            PanelPipeline.draw(context, -thick * 0.5f, -longArm, thick, longArm + thick * 0.5f, ink);
        }
        matrices.popMatrix();
    }

    private void drawPill(DrawContext context, float x, float y, float on, float openT) {
        float pillW = 14.0f;
        float pillH = 8.0f;
        RoundedRectPipeline.draw(context, x, y, pillW, pillH,
            ColorMath.withAlpha(ColorMath.mix(0x2A2C33, NeonEdge.cool(), on), Math.round((150 + on * 95) * openT)));
        float knob = pillH - 3.0f;
        CirclePipeline.draw(context, x + 1.5f + knob * 0.5f + on * (pillW - 3.0f - knob),
            y + pillH * 0.5f, knob * 0.5f,
            ColorMath.withAlpha(ColorMath.mix(0x8A8D96, 0xFFFFFF, on), Math.round((205 + on * 50) * openT)));
    }

    private static final float SWATCH = 11.0f;
    private static final float SWATCH_GAP = 5.0f;
    private static final float FIELD_H = 52.0f;
    private static final float HUE_H = 8.0f;

    private ColorSetting openPicker;
    private float pickerX;
    private float pickerY;
    private float pickerRight;
    private boolean draggingHue;
    private boolean movingPicker;
    private float pickerGrabX;
    private float pickerGrabY;

    private float colorFieldY(float rowY) {
        float needed = 16.0f + FIELD_H + 7.0f + HUE_H;
        if (rowY + needed <= panelBottom - 4.0f) {
            return rowY + 16.0f;
        }
        return rowY - needed + 4.0f;
    }

    private void renderColorSetting(DrawContext context, ColorSetting color, float x, float y,
                                    float right, float rowH, float openT) {
        int selected = color.rgb();
        float centerY = y + rowH * 0.5f;
        float chip = 9.0f;
        float chipX = right - 7.0f - chip;

        CirclePipeline.draw(context, chipX + chip * 0.5f, centerY, chip * 0.5f,
            ColorMath.withAlpha(0xFFFFFF, Math.round((color.open() ? 90 : 45) * openT)));
        CirclePipeline.draw(context, chipX + chip * 0.5f, centerY, chip * 0.5f - 1.0f,
            ColorMath.withAlpha(selected, Math.round(255 * openT)));

        String hex = String.format(Locale.ROOT, "#%06X", selected & 0xFFFFFF);
        MsdfText.draw(context, text, hex, chipX - 6.0f - MsdfText.width(text, hex, 6.0f),
            MsdfText.topFor(centerY, 6.0f), 6.0f, ColorMath.withAlpha(0xFFFFFF, Math.round(155 * openT)));

        if (!color.open()) {
            return;
        }

        openPicker = color;
        pickerX = x;
        pickerY = y;
        pickerRight = right;
    }

    private static final float PICKER_SIZE = 66.0f;
    private static final float PICKER_PAD = 7.0f;
    private static final float PICKER_HUE_W = 8.0f;

    private static final float PICKER_GRIP = 12.0f;

    private static float pickerDragX = Float.NaN;
    private static float pickerDragY;

    private float pickerPanelX() {
        if (Float.isNaN(pickerDragX)) {
            return windowX() + WINDOW_W + 10.0f;
        }
        float leftLimit = windowX() + WINDOW_W + 6.0f;
        return Math.max(leftLimit, Math.min(pickerDragX, width - panelWidth() - 4.0f));
    }

    private float pickerPanelY() {
        float wanted = Float.isNaN(pickerDragX) ? windowY() + 40.0f : pickerDragY;
        return Math.max(4.0f, Math.min(wanted, height - panelHeight() - 4.0f));
    }

    private float panelWidth() {
        return PICKER_SIZE + PICKER_HUE_W + PICKER_PAD * 3.0f;
    }

    private float panelHeight() {
        return PICKER_SIZE + PICKER_PAD * 3.0f + PICKER_GRIP + SWATCH;
    }

    private float presetY() {
        return pickerPanelY() + PICKER_PAD + PICKER_GRIP + PICKER_SIZE + PICKER_PAD;
    }

    private float presetX(int index) {
        return pickerPanelX() + PICKER_PAD + index * (SWATCH + SWATCH_GAP);
    }

    private void renderPicker(DrawContext context, float openT) {
        if (openPicker == null) {
            return;
        }
        ColorSetting color = openPicker;
        float px = pickerPanelX();
        float py = pickerPanelY();
        float w = panelWidth();
        float h = panelHeight();
        int selected = color.rgb();

        GlowPipeline.radial(context, px + w * 0.5f, py + h * 0.5f, Math.max(w, h) * 0.62f,
            ColorMath.withAlpha(0x000000, Math.round(150 * openT)));
        GlassWindowPipeline.draw(context, px, py, w, h, ColorMath.withAlpha(Theme.glassTint(), Math.round(240 * openT)));
        RoundedRectPipeline.drawOutline(context, px, py, w, h, ColorMath.withAlpha(0xFFFFFF, Math.round(38 * openT)));

        for (int i = 0; i < 3; i++) {
            float gripY = py + 4.0f + i * 2.0f;
            context.fill(Math.round(px + w * 0.5f - 7.0f), Math.round(gripY),
                Math.round(px + w * 0.5f + 7.0f), Math.round(gripY) + 1,
                ColorMath.withAlpha(0xFFFFFF, Math.round(45 * openT)));
        }

        float fieldX = px + PICKER_PAD;
        float fieldY = py + PICKER_PAD + PICKER_GRIP;
        ColorFieldPipeline.field(context, fieldX, fieldY, PICKER_SIZE, PICKER_SIZE,
            ColorMath.withAlpha(color.hueColor(), Math.round(255 * openT)));
        RoundedRectPipeline.drawOutline(context, fieldX, fieldY, PICKER_SIZE, PICKER_SIZE,
            ColorMath.withAlpha(0xFFFFFF, Math.round(26 * openT)));

        float knobX = fieldX + color.saturation() * PICKER_SIZE;
        float knobY = fieldY + (1.0f - color.value()) * PICKER_SIZE;
        GlowPipeline.radial(context, knobX, knobY, 9.0f, ColorMath.withAlpha(selected, Math.round(120 * openT)));
        CirclePipeline.draw(context, knobX, knobY, 3.6f, ColorMath.withAlpha(0xFFFFFF, Math.round(240 * openT)));
        CirclePipeline.draw(context, knobX, knobY, 2.3f, ColorMath.withAlpha(selected, Math.round(255 * openT)));

        float hueX = fieldX + PICKER_SIZE + PICKER_PAD;
        ColorFieldPipeline.hueVertical(context, hueX, fieldY, PICKER_HUE_W, PICKER_SIZE, Math.round(255 * openT));
        float hueY = fieldY + color.hue() * PICKER_SIZE;
        GlowPipeline.radial(context, hueX + PICKER_HUE_W * 0.5f, hueY, 9.0f,
            ColorMath.withAlpha(color.hueColor(), Math.round(110 * openT)));
        CirclePipeline.draw(context, hueX + PICKER_HUE_W * 0.5f, hueY, 3.8f, ColorMath.withAlpha(0xFFFFFF, Math.round(245 * openT)));
        CirclePipeline.draw(context, hueX + PICKER_HUE_W * 0.5f, hueY, 2.5f,
            ColorMath.withAlpha(color.hueColor(), Math.round(255 * openT)));

        float swatchY = presetY();
        for (int i = 0; i < ColorSetting.PRESETS.length; i++) {
            int preset = ColorSetting.PRESETS[i];
            boolean active = preset == selected;
            float cx = presetX(i) + SWATCH * 0.5f;
            float cy = swatchY + SWATCH * 0.5f;
            CirclePipeline.draw(context, cx, cy, SWATCH * 0.5f,
                ColorMath.withAlpha(0xFFFFFF, Math.round((active ? 90 : 38) * openT)));
            CirclePipeline.draw(context, cx, cy, SWATCH * 0.5f - 1.1f, ColorMath.withAlpha(preset, Math.round(255 * openT)));
        }
    }

    private void dragColor(ColorSetting color, float mx, float my) {
        float fieldX = pickerPanelX() + PICKER_PAD;
        float fieldY = pickerPanelY() + PICKER_PAD + PICKER_GRIP;
        if (draggingHue) {
            color.setHue((my - fieldY) / PICKER_SIZE);
            return;
        }
        color.setSaturationValue((mx - fieldX) / PICKER_SIZE, 1.0f - (my - fieldY) / PICKER_SIZE);
    }

    private boolean clickPicker(float mx, float my) {
        if (openPicker == null) {
            return false;
        }
        float px = pickerPanelX();
        float py = pickerPanelY();
        if (mx < px || mx > px + panelWidth() || my < py || my > py + panelHeight()) {
            return false;
        }
        if (my < py + PICKER_GRIP) {
            movingPicker = true;
            pickerGrabX = mx - px;
            pickerGrabY = my - py;
            return true;
        }
        float swatchY = presetY();
        if (my >= swatchY && my <= swatchY + SWATCH) {
            for (int i = 0; i < ColorSetting.PRESETS.length; i++) {
                float sx = presetX(i);
                if (mx >= sx && mx <= sx + SWATCH) {
                    openPicker.set(ColorSetting.PRESETS[i]);
                    return true;
                }
            }
            return true;
        }
        draggingHue = mx >= px + PICKER_PAD + PICKER_SIZE + PICKER_PAD * 0.4f;
        draggingColor = openPicker;
        dragColor(openPicker, mx, my);
        return true;
    }

    private ModeSetting openDropdown;
    private float dropdownX;
    private float dropdownY;
    private float dropdownW;

    private void drawChevron(DrawContext context, float cx, float cy, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            float dy = up ? -i : i;
            int alpha = (color >>> 24) - i * 12;
            if (alpha <= 0) {
                return;
            }
            int shade = alpha << 24 | color & 0xFFFFFF;
            context.fill(Math.round(cx - 3.0f + i), Math.round(cy + dy),
                Math.round(cx - 2.0f + i), Math.round(cy + dy) + 1, shade);
            context.fill(Math.round(cx + 2.0f - i), Math.round(cy + dy),
                Math.round(cx + 3.0f - i), Math.round(cy + dy) + 1, shade);
        }
    }

    private void renderDropdown(DrawContext context, float openT, int mouseX, int mouseY) {
        if (openDropdown == null) {
            return;
        }
        List<String> options = openDropdown.options();
        float rowH = openDropdown.optionRowHeight();
        float listH = options.size() * rowH + 6.0f;
        float listY = dropdownY + 12.0f;
        if (listY + listH > panelBottom) {
            listY = dropdownY - listH - 2.0f;
        }

        GlowPipeline.radial(context, dropdownX + dropdownW * 0.5f, listY + listH * 0.5f,
            Math.max(dropdownW, listH) * 0.6f, ColorMath.withAlpha(0x000000, Math.round(120 * openT)));
        RoundedRectPipeline.draw(context, dropdownX, listY, dropdownW, listH,
            ColorMath.withAlpha(0x101114, Math.round(246 * openT)));
        RoundedRectPipeline.drawOutline(context, dropdownX, listY, dropdownW, listH,
            ColorMath.withAlpha(0xFFFFFF, Math.round(40 * openT)));

        for (int i = 0; i < options.size(); i++) {
            float rowY = listY + 3.0f + i * rowH;
            boolean active = i == openDropdown.index();
            boolean hovered = mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                && mouseY >= rowY && mouseY <= rowY + rowH;

            if (hovered || active) {
                RoundedRectPipeline.draw(context, dropdownX + 3.0f, rowY, dropdownW - 6.0f, rowH,
                    ColorMath.withAlpha(0xFFFFFF, Math.round((active ? 22 : 12) * openT)));
            }
            if (active) {
                PanelPipeline.draw(context, dropdownX + 5.0f, rowY + 3.0f, 1.6f, rowH - 6.0f,
                    ColorMath.withAlpha(NeonEdge.cool(), Math.round(235 * openT)));
            }
            MsdfText.draw(context, text, options.get(i), dropdownX + 11.0f, rowY + rowH * 0.5f - 3.0f, 6.0f,
                ColorMath.withAlpha(0xFFFFFF, Math.round((active ? 240 : 145) * openT)));
        }
    }

    private boolean clickDropdown(float mx, float my) {
        if (openDropdown == null) {
            return false;
        }
        List<String> options = openDropdown.options();
        float rowH = openDropdown.optionRowHeight();
        float listH = options.size() * rowH + 6.0f;
        float listY = dropdownY + 12.0f;
        if (listY + listH > panelBottom) {
            listY = dropdownY - listH - 2.0f;
        }
        if (mx < dropdownX || mx > dropdownX + dropdownW || my < listY || my > listY + listH) {
            openDropdown.close();
            return true;
        }
        int index = (int) ((my - listY - 3.0f) / rowH);
        if (index >= 0 && index < options.size()) {
            openDropdown.select(index);
        }
        return true;
    }

    private String keyLabel(ModuleInfo module) {
        if (module.binding()) {
            return "...";
        }
        return module.hasKeybind() ? module.keybind() : null;
    }

    private List<ModuleInfo> visibleModules() {
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        List<ModuleInfo> result = new ArrayList<>();
        List<ModuleInfo> source = query.isEmpty() ? ModuleRegistry.byCategory(selected) : ModuleRegistry.all();
        for (ModuleInfo module : source) {
            if (query.isEmpty() || module.name().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(module);
            }
        }
        return result;
    }

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
        float wx = windowX();
        float wy = windowY();
        float mx = (float) click.x();
        float my = (float) click.y();
        boolean rightClick = click.button() == 1;

        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            float cx = wx + RAIL_CENTER_X;
            float cy = wy + RAIL_FIRST_Y + i * RAIL_SPACING;
            if (Math.abs(mx - cx) <= RAIL_HIT * 0.5f && Math.abs(my - cy) <= RAIL_HIT * 0.5f) {
                selected = categories[i];
                configPanel.resetInput();
                scrollTarget = 0.0f;
                search.clear();
                List<ModuleInfo> modules = ModuleRegistry.byCategory(selected);
                focused = modules.isEmpty() ? null : modules.get(0);
                return true;
            }
        }

        float searchCx = wx + RAIL_CENTER_X;
        float searchCy = wy + SEARCH_Y;
        if (Math.abs(mx - searchCx) <= RAIL_HIT * 0.5f && Math.abs(my - searchCy) <= RAIL_HIT * 0.5f) {
            searchOpen = !searchOpen;
            if (!searchOpen) {
                search.clear();
            }
            return true;
        }

        if (selected == Category.CONFIG && configPanel.mouseClicked(mx, my)) {
            return true;
        }

        if (focused != null && mx >= headerSwitchX - 4.0f && mx <= headerSwitchX + 18.0f
            && my >= headerSwitchY - 5.0f && my <= headerSwitchY + 13.0f) {
            focused.toggle();
            return true;
        }

        for (CardBounds bounds : cardBounds) {
            if (mx >= bounds.x && mx <= bounds.x + LIST_W && my >= bounds.y && my <= bounds.y + CARD_H) {
                if (click.button() == 0 && mx >= bounds.x + LIST_W - 24.0f) {
                    bounds.module.toggle();
                    return true;
                }
                if (click.button() == 2) {
                    for (ModuleInfo other : ModuleRegistry.all()) {
                        other.cancelBinding();
                    }
                    bounds.module.armBinding();
                } else if (rightClick) {
                    bounds.module.toggle();
                } else {
                    focused = bounds.module;
                }
                return true;
            }
        }

        if (clickDropdown(mx, my)) {
            return true;
        }
        if (clickPicker(mx, my)) {
            return true;
        }

        for (SettingBounds bounds : settingBounds) {
            float right = wx + PANEL_RIGHT;
            float cardH = rowHeight(bounds.setting, right - bounds.x);
            if (my < bounds.y || my > bounds.y + cardH || mx < bounds.x || mx > right) {
                continue;
            }
            handleSettingClick(bounds.setting, bounds.x, bounds.y, right, mx, my, rightClick);
            return true;
        }

        return super.mouseClicked(click, doubleClick);
    }

    private void handleSettingClick(Setting setting, float x, float y, float right, float mx, float my, boolean secondary) {
        float w = right - x;
        if (setting instanceof dev.linkenvisuals.clickgui.setting.ActionSetting action) {
            action.run();
            dev.linkenvisuals.audio.LinkenSounds.playUiClick();
            return;
        }

        if (setting instanceof BooleanSetting boolSetting) {
            boolSetting.toggle();
        } else if (setting instanceof SliderSetting slider) {
            float trackX = x + 7.0f;
            float trackW = w - 14.0f;
            draggingSlider = slider;
            dragTrackX = trackX;
            dragTrackWidth = trackW;
            slider.setFraction((mx - trackX) / trackW);
        } else if (setting instanceof ModeSetting mode) {
            if (chipped(mode)) {
                float chipX = x + 7.0f;
                float chipY = y + 15.0f;
                float available = w - 12.0f;
                float used = 0.0f;
                for (int i = 0; i < mode.options().size(); i++) {
                    float chip = chipWidth(mode.options().get(i));
                    if (used + chip > available && used > 0.0f) {
                        used = 0.0f;
                        chipY += 12.0f;
                    }
                    if (mx >= chipX + used && mx <= chipX + used + chip
                        && my >= chipY && my <= chipY + 10.0f) {
                        mode.select(i);
                        return;
                    }
                    used += chip + 3.0f;
                }

                mode.select((mode.index() + (secondary ? mode.options().size() - 1 : 1))
                    % mode.options().size());
            } else {
                mode.toggleOpen();
            }
        } else if (setting instanceof ColorSetting color) {
            color.toggleOpen();
        } else if (setting instanceof KeybindSetting keybind) {
            keybind.arm();
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (movingPicker) {
            pickerDragX = (float) click.x() - pickerGrabX;
            pickerDragY = (float) click.y() - pickerGrabY;
            return true;
        }
        if (draggingColor != null) {
            dragColor(draggingColor, (float) click.x(), (float) click.y());
            return true;
        }
        if (draggingSlider != null) {
            draggingSlider.setFraction(((float) click.x() - dragTrackX) / dragTrackWidth);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingColor = null;
        movingPicker = false;
        if (draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selected == Category.CONFIG) {
            configPanel.scroll(verticalAmount);
            return true;
        }
        if (mouseX > windowX() + PANEL_X) {
            panelScrollTarget -= (float) verticalAmount * 22.0f;
            panelScrollTarget = Math.max(0.0f, Math.min(panelScrollTarget,
                Math.max(0.0f, panelContentHeight - panelViewHeight)));
            return true;
        }
        scrollTarget -= (float) verticalAmount * 24.0f;
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget, Math.max(0.0f, contentHeight - listViewHeight)));
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (selected == Category.CONFIG && configPanel.charTyped(input)) {
            return true;
        }
        if (searchOpen && search.charTyped(input)) {
            lastTyped = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (selected == Category.CONFIG && configPanel.keyPressed(input)) {
            return true;
        }
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
                listening.resolve(keyName(input.key()));
            }
            return true;
        }
        if (searchOpen) {
            if (search.keyPressed(input)) {
                lastTyped = System.currentTimeMillis();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchOpen = false;
                search.clear();
                return true;
            }
        }
        boolean hasCtrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (hasCtrl && (input.key() == GLFW.GLFW_KEY_LEFT || input.key() == GLFW.GLFW_KEY_RIGHT)) {
            ModuleRegistry.cycleGuiStyle(input.key() == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private KeybindSetting findListeningKeybind() {
        for (SettingBounds bounds : settingBounds) {
            if (bounds.setting instanceof KeybindSetting keybind && keybind.listening()) {
                return keybind;
            }
        }
        return null;
    }

    private static String keyName(int keyCode) {
        return KeyNames.of(keyCode);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private record CardBounds(ModuleInfo module, float x, float y) {
    }

    private record SettingBounds(Setting setting, float x, float y) {
    }
}
