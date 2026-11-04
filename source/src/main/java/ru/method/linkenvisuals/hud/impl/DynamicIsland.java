package ru.method.linkenvisuals.hud.impl;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandLyrics;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotification;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandMedia;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandMediaHolder;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandWaves;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandLyrics.LyricsLine;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.AnimatedTextHelper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.HudRenderFrame;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.server.Server;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;
import ru.method.linkenvisuals.screen.mainmenu.CustomMainMenuScreen;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class DynamicIsland extends HudElement {

    private static final Identifier ICON_PREVIOUS = LinkenVisuals.id("music/previous.png");
    private static final Identifier ICON_PLAY = LinkenVisuals.id("music/play.png");
    private static final Identifier ICON_PAUSE = LinkenVisuals.id("music/pause.png");
    private static final Identifier ICON_NEXT = LinkenVisuals.id("music/next.png");
    private static final float CONTROL_ICON_SIZE = 17f;
    private static final float CONTROL_ICON_GAP = 7f;
    private static final float PROGRESS_BAR_HEIGHT = 4.5f;
    private static final float PROGRESS_BAR_GAP = 3.5f;
    private static final float TIME_FONT_SIZE = 6.0f;
    private static final float EXPANDED_COVER_SCALE = 0.72f;
    private static final float EXPANDED_COVER_GROW = 4f;
    private static final float PROGRESS_BAR_RAISE = 2.0f;

    private final Animation internetAnimation = new Animation(900, 1f, false, Easing.BOTH_SINE);
    private final Animation pvpAnimation = new Animation(900, 1f, false, Easing.BOTH_SINE);
    private final Animation notificationAnimation = new Animation(300, 1f, false, Easing.BOTH_SINE);
    private DynamicIslandNotification lastNotification;
    private final Animation textColorAnimation = new Animation(520, 1f, false, Easing.EASE_IN_OUT_SINE);
    private final Animation musicExpandAnimation = new Animation(320, 1f, false, Easing.EASE_OUT_BACK);
    private final Animation coverExpandAnimation = new Animation(420, 1f, false, Easing.EASE_OUT_BACK);
    private final Animation waveVisibilityAnimation = new Animation(800, 1f, false, Easing.BOTH_SINE);
    private final Animation lyricsExpandAnimation = new Animation(280, 1f, false, Easing.EASE_OUT_BACK);
    private final AnimatedString currentTimeText = new AnimatedString(200, Easing.EASE_OUT_CUBIC, "0:00");
    private final AnimatedString totalTimeText = new AnimatedString(200, Easing.EASE_OUT_CUBIC, "0:00");
    private final AnimatedString titleText = new AnimatedString(380, Easing.EASE_IN_OUT_SINE, "");
    private final AnimatedString artistText = new AnimatedString(380, Easing.EASE_IN_OUT_SINE, "");
    private final AnimatedString lyricsPrevText = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");
    private final AnimatedString lyricsCurrentText = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");
    private final AnimatedString lyricsNextText = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");

    private final AnimatedString notificationText = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");
    private final Animation notificationContentAnimation = new Animation(300, 1f, false, Easing.BOTH_SINE);
    private final Color notificationEnabledColor = new Color(0, 255, 0);
    private final Color notificationDisabledColor = new Color(255, 0, 0);
    private Color notificationStatusFrom = notificationEnabledColor;
    private Color notificationStatusTo = notificationEnabledColor;
    private String lastNotificationText = "";
    private boolean lastNotificationEnabled;
    private boolean notificationStateInitialized;
    private boolean preferWhite = true;
    private float smoothedLuminance = 0.5f;
    private long lastBackgroundSample = 0L;
    private Color cachedIconBackground = new Color(0, 0, 0);
    private Color cachedTimeBackground = new Color(0, 0, 0);
    private boolean mediaExpanded;
    private float lastControlsX, lastControlsY, lastControlsWidth;
    private boolean controlsVisible;
    private boolean lastLyricsLinesBoundsValid;
    private float lastLyricsPrevLineX, lastLyricsPrevLineY, lastLyricsPrevLineW, lastLyricsPrevLineH;
    private float lastLyricsCurrLineX, lastLyricsCurrLineY, lastLyricsCurrLineW, lastLyricsCurrLineH;
    private float lastLyricsNextLineX, lastLyricsNextLineY, lastLyricsNextLineW, lastLyricsNextLineH;
    private String lastLyricsPrevLineDisplay = "";
    private String lastLyricsCurrLineDisplay = "";
    private String lastLyricsNextLineDisplay = "";
    private String lastLyricsPrevLineCopy = "";
    private String lastLyricsCurrLineCopy = "";
    private String lastLyricsNextLineCopy = "";
    private long lastLyricsLineClickTimeMs;
    private int lastLyricsLineClickId;
    private float animatedWidth;
    private float musicHeightValue;
    private float lyricsHeightValue;
    private long lastRenderTimeNs;
    private float lastFrameDeltaSeconds;
    private float lastInteractionUiScale = 1.0f;
    private final Animation mainMenuHoverAnimation = new Animation(220, 1f, false, Easing.BOTH_SINE);

    private final DynamicIslandMedia media = DynamicIslandMediaHolder.shared();
    private final DynamicIslandLyrics lyrics = new DynamicIslandLyrics();
    private final DynamicIslandWaves waves = new DynamicIslandWaves();

    public DynamicIsland() {
        super("DynamicIsland");
    }

    private boolean isMainMenuScreen() {
        return mc != null && mc.currentScreen instanceof CustomMainMenuScreen;
    }

    private float updateFrameDeltaSeconds() {
        long now = System.nanoTime();
        if (lastRenderTimeNs == 0L) {
            lastRenderTimeNs = now;
            lastFrameDeltaSeconds = 1f / 60f;
            return lastFrameDeltaSeconds;
        }
        float dt = (now - lastRenderTimeNs) / 1_000_000_000f;
        lastRenderTimeNs = now;
        lastFrameDeltaSeconds = MathHelper.clamp(dt, 0f, 0.1f);
        return lastFrameDeltaSeconds;
    }

    private float timeBasedLerpFactor(float responsePerSecond, float dtSeconds) {
        float response = Math.max(0f, responsePerSecond);
        float dt = MathHelper.clamp(dtSeconds, 0f, 1f);
        if (response == 0f || dt == 0f) return 0f;
        return 1f - (float) Math.exp(-response * dt);
    }

    public boolean handleScreenClick(int mouseX, int mouseY, int button) {
        if (closed()) return false;
        if (!MathUtils.isHovered(getX(), getY(), getWidth(), getHeight(), mouseX, mouseY)) return false;

        return switch (button) {
            case 0 -> onLeftClick(mouseX, mouseY);
            case 1 -> onRightClick(mouseX, mouseY);
            case 2 -> onMiddleClick(mouseX, mouseY);
            default -> false;
        };
    }

    @Override
    public void onEnable() {
        ensureExecutors();
        animatedWidth = 0f;
        toggledAnimation.snapTo(true);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        animatedWidth = 0f;
        toggledAnimation.snapTo(false);
        DynamicIslandNotificationManager.getInstance().clearAll();
        shutdownExecutors();
        mediaExpanded = false;
        lyrics.reset();
        lyricsExpandAnimation.snapTo(false);
        lyricsCurrentText.update("");
        lyricsNextText.update("");
        super.onDisable();
    }

    private void ensureExecutors() {
        lyrics.ensureExecutor();
        media.ensureExecutor();
    }

    private void shutdownExecutors() {
        lyrics.shutdown();
        media.shutdown();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (LinkenVisuals.getInstance().isPanic()) return;
        if (Module.fullNullCheck() && !isMainMenuScreen()) return;
        // ТОЛЬКО когда остров включён. onTick — event-handler и тикает всегда; раньше updateMediaInfo()
        // безусловно опрашивал системный плеер (нативная MediaPlayerInfo.dll / WinRT SMTC) даже при
        // выключенном HUD, из-за чего натив крашил (0xe06d7363) всех, кто плеером не пользуется.
        if (!isToggled()) return;
        DynamicIslandNotificationManager.getInstance().onTick();
        updateMediaInfo();
    }

    @EventHandler
    @Override
    public void onMouse(EventMouse e) {
        if (!LinkenVisuals.getInstance().isPanic()
                && e.getAction() == 1
                && e.getButton() == 0
                && mc.currentScreen != null
                && !(mc.currentScreen instanceof ChatScreen)
                && !isMainMenuScreen()
                && !fullNullCheck()
                && mediaExpanded
                && controlsVisible
                && handleControlClick(mouseX(), mouseY())) {
            e.consume();
            return;
        }
        super.onMouse(e);
    }

    @Override
    public void onRender2D(EventRender2D e) {
        // При Link-стиле HUD «динамический остров» скрыт (по запросу — чистый Link-вид).
        if (ru.method.linkenvisuals.hud.LinkHudStyle.active()) {
            updateCollapsedInteractionBounds();
            return;
        }
        boolean inMainMenu = isMainMenuScreen();
        HudRenderFrame.State hudFrame = HudRenderFrame.get();
        if (hudFrame != null && hudFrame.shouldDeferDynamicIsland()) {
            updateCollapsedInteractionBounds();
            return;
        }
        if ((fullNullCheck() && !inMainMenu) || (!inMainMenu && closed())) {
            updateCollapsedInteractionBounds();
            return;
        }
        float dtSeconds = updateFrameDeltaSeconds();

        DynamicIslandNotification notification = DynamicIslandNotificationManager.getInstance().getCurrentNotification();
        if (notification != null) {
            lastNotification = notification;
        }
        boolean hasPlayer = mc.player != null;
        boolean isPvp = hasPlayer && Server.isPvp();
        boolean musicVisible = hasMedia();
        boolean musicPlaying = isMusicPlaying();

        boolean notificationActive = notification != null;
        notificationAnimation.update(notificationActive);
        float notificationAlpha = notificationAnimation.getValue();
        DynamicIslandNotification notificationToRender = notification != null ? notification : lastNotification;
        boolean showNotification = notificationToRender != null && notificationAlpha > 0.01f;
        if (notificationToRender != null && showNotification) {
            updateNotificationAnimations(notificationToRender);
        }
        boolean showPvp = !notificationActive && isPvp;
        boolean showNotificationAsMain = showNotification;
        boolean showMusic = musicVisible && !showPvp && !showNotificationAsMain;
        internetAnimation.update(hasPlayer && Server.getPing(mc.player) < 150);
        pvpAnimation.update(isPvp);
        boolean expandedState = mediaExpanded && showMusic;
        musicExpandAnimation.update(expandedState);
        coverExpandAnimation.update(expandedState);
        boolean showWaves = musicPlaying && musicVisible && !showPvp;
        waveVisibilityAnimation.update(showWaves);
        controlsVisible = false;
        lyricsExpandAnimation.update(expandedState && lyrics.isOpen());

        float expandProgress = musicExpandAnimation.getValue();
        float coverProgress = coverExpandAnimation.getValue();
        float padding = 2.3f;
        float height = 19f;
        float endPadding = computeEndPadding(padding, showNotification, showPvp);
        float yOffset = 0f;

        String displayText;
        float defaultIconArea = height;

        if (showNotificationAsMain) {
            displayText = notificationText.getCurrent();
        } else if (showPvp) {
            displayText = I18n.translate("elements.dynamicisland.pvp");
        } else if (showMusic) {
            padding = MathUtils.lerp(2.3f, 2.3f + 1.2f, expandProgress);
            float musicHeight = MathUtils.lerp(19f, 76f, expandProgress);
            float lyricsExtraHeight = lyricsExpandAnimation.getValue() * 36f * expandProgress;
            height = musicHeight + lyricsExtraHeight;
            endPadding = computeEndPadding(padding, false, false);
            defaultIconArea = height;
            yOffset = 6f * expandProgress;
             displayText = getMediaTitle();
             this.musicHeightValue = musicHeight;
             this.lyricsHeightValue = lyricsExtraHeight;
         } else {
            displayText = hasPlayer ? getProtectedPlayerName() : "LinkenVisuals";
         }

        float targetWidth;
        float textAreaWidth = 0f;
        if (showMusic) {
            float collapsedWidth = computeMusicContentWidth(height, padding, 0f);
            float expandedWidth = Math.max(175f, computeMusicContentWidth(height, padding, 1f));
            float maxWidth = Math.max(175f, 175f);
            collapsedWidth = Math.min(collapsedWidth, maxWidth);
            expandedWidth = Math.min(expandedWidth, maxWidth);
            targetWidth = MathUtils.lerp(collapsedWidth, expandedWidth, expandProgress);
        } else {
            textAreaWidth = showNotificationAsMain ? getAnimatedNotificationTextWidth() : Fonts.ROUNDBOLD.getWidth(displayText, 8f);
            float contentWidth = defaultIconArea + textAreaWidth + padding + endPadding;
            targetWidth = contentWidth;
        }
        float lerpSpeed = musicVisible ? 0.12f : 0.08f;
        if (animatedWidth <= 0.01f) {
            animatedWidth = Math.max(1f, targetWidth * 0.6f);
        }
        float widthResponse = musicVisible ? 10.0f : 7.5f;
        float widthLerpFactor = timeBasedLerpFactor(widthResponse, dtSeconds);
        float widthLerp = MathHelper.clamp(Math.max(lerpSpeed, widthLerpFactor), 0f, 1f);
        animatedWidth = MathUtils.lerp(animatedWidth, targetWidth, widthLerp);
        float width = animatedWidth;
        // Пока пилюля догоняет цель — режем текст по фактической анимируемой ширине,
        // чтобы он не торчал из скруглённых краёв.
        if (!showMusic) {
            textAreaWidth = Math.min(textAreaWidth, Math.max(0f, width - defaultIconArea - padding - endPadding));
        }

        float x = mc.getWindow().getScaledWidth() / 2f - width / 2f;
        float y = 1f + yOffset;
        float sideOffset = padding * 2.1f;
        Color contentColor = getIslandTextColor(255);
        Color sideColor = inMainMenu ? Color.WHITE : resolveAdaptiveTextColor(x, y, width, height, sideOffset, displayText);

        float cornerRadius = Render2D.DEFAULT_CORNER_RADIUS;

        float[] dragOffset = getDragOffset();
        float renderScale = MathHelper.clamp((inMainMenu ? 1f : toggledAnimation.getValue()) * getDragScale(), 0f, 1f);
        if (renderScale <= 0.001f) {
            updateCollapsedInteractionBounds();
            return;
        }

        float uiScale = getUiScale();
        lastInteractionUiScale = uiScale;
        float centerX = x + width / 2f + dragOffset[0];
        float centerY = y + height / 2f + dragOffset[1];
        e.getContext().getMatrices().pushMatrix();
        e.getContext().getMatrices().translate(centerX, centerY);
        e.getContext().getMatrices().scale(uiScale * renderScale, uiScale * renderScale);
        e.getContext().getMatrices().translate(-centerX, -centerY);
        x += dragOffset[0];
        y += dragOffset[1];

        Color bgColor = ColorUtils.getGlassColor(255);
        if (inMainMenu) {
            boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX(), mouseY());
            mainMenuHoverAnimation.update(hovered);
            float hoverProgress = MathHelper.clamp(mainMenuHoverAnimation.getValue(), 0f, 1f);
            float easedHover = MathHelper.clamp((float) Math.sin((Math.PI / 2f) * hoverProgress), 0f, 1f);

            float blurRadius = MathHelper.clamp(Math.min(width, height) * 0.12f, 6.0f, 16.0f);
            float distortStrength = 0.02f + 0.018f * easedHover;
            Render2D.drawLiquidGlassRectMainMenu(
                    e.getContext().getMatrices(),
                    x,
                    y,
                    width,
                    height,
                    cornerRadius,
                    bgColor,
                    builder -> {
                        float smooth = 2.0f;
                        builder.cornerSmoothness(smooth);
                        builder.smoothness(smooth);
                        builder.fresnelPower(11.0f);
                        builder.fresnelAlpha(0.5f);
                        builder.baseAlpha(175.0f);
                        builder.fresnelInvert(true);
                        builder.fresnelMix(0f);
                        builder.distortStrength(distortStrength);
                        builder.blurRadius(blurRadius);
                    }
            );
        } else {
            Render2D.drawLiquidRectHud(
                    e.getContext().getMatrices(),
                    x,
                    y,
                    width,
                    height,
                    new BorderRadius(cornerRadius),
                    new ColorRGBA(bgColor),
                    2.5f,
                    22.0f,
                    11f,
                    175.0f,
                    true,
                    0f,
                    0.19f
            );
        }

        if (showNotificationAsMain) {
            renderNotificationContent(e, x, y, height, padding, textAreaWidth, notificationToRender, notificationAlpha);
        } else if (showPvp) {
            renderDefaultContent(e, x, y, height, padding, textAreaWidth, true, 1.0f);
        } else if (showMusic) {
            float wavesAlpha = waveVisibilityAnimation.getValue();
            renderMusicContent(e, x, y, width, height, musicHeightValue, lyricsHeightValue, padding, expandProgress, coverProgress, contentColor, musicPlaying, wavesAlpha);
        } else {
            renderDefaultContent(e, x, y, height, padding, textAreaWidth, false, 1.0f);
        }

        if (!expandedState) {
            renderSideElements(e, x, y, width, height, padding, sideOffset, sideColor);
        }
        float visualX = centerX - width * uiScale / 2f;
        float visualY = centerY - height * uiScale / 2f;
        applyUiScaleToInteractiveBounds(centerX, centerY, uiScale);
        setBounds(visualX, visualY, width * uiScale, height * uiScale);

        e.getContext().getMatrices().popMatrix();
        if (!notificationActive && notificationAlpha <= 0.01f) {
            lastNotification = null;
            resetNotificationAnimations();
        }
        super.onRender2D(e);
    }

    @Override
    protected boolean onRightClick(int mouseX, int mouseY) {
        return false;
    }

    @Override
    protected boolean onLeftClick(int mouseX, int mouseY) {
        if (!hasMedia()) return false;
        if (mediaExpanded && handleControlClick(mouseX, mouseY)) {
            return true;
        }
        if (handleLyricsLineClick(mouseX, mouseY)) {
            return true;
        }
        mediaExpanded = !mediaExpanded;
        return true;
    }

    private float computeMusicContentWidth(float height, float padding, float expandProgress) {
        float progressAlpha = MathHelper.clamp(expandProgress, 0f, 1f);
        float bottomReserved = (PROGRESS_BAR_GAP + PROGRESS_BAR_HEIGHT + CONTROL_ICON_SIZE + 0.0f) * progressAlpha;
        float contentHeight = Math.max(6f, height - padding * 2f - bottomReserved);

        float coverBaseSize = contentHeight * 0.72f;
        float coverSize = MathUtils.lerp(
                coverBaseSize,
                coverBaseSize + Math.min(4f, contentHeight * 0.35f),
                progressAlpha
        );
        coverSize *= MathUtils.lerp(1f, EXPANDED_COVER_SCALE, progressAlpha);
        coverSize += EXPANDED_COVER_GROW * progressAlpha;

        float waveWidthScale = MathUtils.lerp(0.8f, 1f, progressAlpha);
        float titleWidth = Fonts.ROUNDBOLD.getWidth(getMediaTitle(), 8f);
        String artist = getMediaArtist();
        boolean showArtist = progressAlpha > 0.05f && !artist.isEmpty();
        float artistWidth = showArtist ? Fonts.SEMIBOLD.getWidth(artist, 7f) : 0f;
        float dynamicTextWidth = Math.max(titleWidth, artistWidth);
        float collapsedTextWidth = dynamicTextWidth + 4f;
        float textBlockWidth = MathUtils.lerp(collapsedTextWidth, dynamicTextWidth, progressAlpha);
        float wavesAlpha = waveVisibilityAnimation.getValue();
        if (wavesAlpha > 0.05f) {
            textBlockWidth += waves.getWaveBlockWidth(waveWidthScale)
                    + 1.0f * waveWidthScale
                    + 2.8f;
        }

        float controlsWidth = CONTROL_ICON_SIZE * 3f + CONTROL_ICON_GAP * 2f;
        float expandedMinWidth = Math.max(controlsWidth + padding * 2f, 140f * 0.45f);
        float collapsedMinWidth = 140f * 0.25f;
        float minContentWidth = Math.max(collapsedMinWidth, MathUtils.lerp(collapsedMinWidth, expandedMinWidth, progressAlpha));

        float rightPadding = progressAlpha > 0.05f ? padding : 0f;
        float baseWidth = Math.max(
                minContentWidth,
                padding + coverSize + padding + -1.0f + textBlockWidth + rightPadding
        );
        if (progressAlpha > 0.05f) {
            float progressWidth = computeProgressBlockWidth();
            baseWidth = Math.max(baseWidth, progressWidth + padding * 2f);
        }
        return baseWidth + (progressAlpha > 0.05f ? 8f : 0f);
    }

    private void renderMusicContent(EventRender2D e, float x, float y, float width, float totalHeight, float musicHeight, float lyricsHeight, float padding, float expandProgress, float coverProgress, Color textColor, boolean playing, float wavesAlpha) {
        if (!media.hasMedia()) return;

        lastLyricsLinesBoundsValid = false;
        lastLyricsPrevLineDisplay = "";
        lastLyricsCurrLineDisplay = "";
        lastLyricsNextLineDisplay = "";
        lastLyricsPrevLineCopy = "";
        lastLyricsCurrLineCopy = "";
        lastLyricsNextLineCopy = "";

        float progressAlpha = MathHelper.clamp(expandProgress, 0f, 1f);
        float bottomReserved = (PROGRESS_BAR_GAP + PROGRESS_BAR_HEIGHT + CONTROL_ICON_SIZE + 0.0f) * progressAlpha;
        float contentHeight = Math.max(6f, musicHeight - padding * 2f - bottomReserved);

        float coverBaseSize = contentHeight * 0.72f;
        float coverSize = MathUtils.lerp(
                coverBaseSize,
                coverBaseSize + Math.min(4f, contentHeight * 0.35f),
                coverProgress
        );
        coverSize *= MathUtils.lerp(1f, EXPANDED_COVER_SCALE, progressAlpha);
        coverSize += EXPANDED_COVER_GROW * progressAlpha;

        float coverRadius = Math.max(2.5f, coverSize * 0.22f);
        String title = getMediaTitle();
        String artist = getMediaArtist();
        titleText.update(title);
        artistText.update(artist);
        String displayTitle = titleText.getCurrent();
        String displayArtist = artistText.getCurrent();
        boolean showArtist = progressAlpha > 0.05f && !displayArtist.isEmpty();

        float titleHeight = Fonts.ROUNDBOLD.getHeight(8f);
        float artistHeight = Fonts.SEMIBOLD.getHeight(7f);
        float textSpacing = 2f;
        float fullTitleWidth = Fonts.ROUNDBOLD.getWidth(displayTitle, 8f);
        float startX = x + padding;
        float expandedShift = 2f * progressAlpha;
        float coverX = startX + expandedShift;
        float coverY = y + padding + (contentHeight - coverSize) / 2f;
        float coverCenterY = coverY + coverSize / 2f;
        NativeImageBackedTexture artworkTexture = media.getArtworkTexture();
        if (artworkTexture != null) {
            Render2D.drawTexture(
                    e.getContext().getMatrices(),
                    coverX,
                    coverY,
                    coverSize,
                    coverSize,
                    coverRadius,
                    artworkTexture,
                    Color.WHITE
            );
        } else {
            Render2D.drawRoundedRect(
                    e.getContext().getMatrices(),
                    coverX,
                    coverY,
                    coverSize,
                    coverSize,
                    coverRadius,
                    ColorUtils.getGlobalColor((int) (140 * Math.max(expandProgress, 0.35f)))
            );
        }

        float textX = coverX + coverSize + padding + -1.0f;
        Color detailColor = new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), (int) (255 * progressAlpha));



        float waveWidthScale = MathUtils.lerp(0.8f, 1f, progressAlpha);
        float waveHeightScale = MathUtils.lerp(1.5f, 0.65f, progressAlpha);
        boolean expandedText = progressAlpha > 0.05f;
        float rightPadding = expandedText ? padding : 0f;
        float textEdgePadding = expandedText ? 4f : 1f;
        float availableTextSpace = width - (textX - x) - rightPadding - textEdgePadding - -1.0f;
        float desiredWaveWidth = wavesAlpha > 0.02f ? waves.getWaveBlockWidth(waveWidthScale) + 1.0f * waveWidthScale + 2.8f : 0f;
        float waveWidthScaleUsed = waveWidthScale;
        boolean allowWaves;
        if (wavesAlpha <= 0.02f) {
            allowWaves = false;
        } else if (expandedText) {
            allowWaves = availableTextSpace - desiredWaveWidth >= 32f;
        } else {
            float neededWithWaves = fullTitleWidth + desiredWaveWidth + 2.8f;
            if (neededWithWaves <= availableTextSpace) {
                allowWaves = true;
            } else {
                float maxWaveSpace = Math.max(0f, availableTextSpace - 6f);
                if (maxWaveSpace > 2f) {
                    float fit = Math.min(1f, maxWaveSpace / Math.max(1f, desiredWaveWidth));
                    waveWidthScaleUsed = waveWidthScale * Math.max(0.8f, fit);
                    desiredWaveWidth = waves.getWaveBlockWidth(waveWidthScaleUsed) + 1.0f * waveWidthScaleUsed + 2.8f;
                    if (desiredWaveWidth > maxWaveSpace) {
                        desiredWaveWidth = maxWaveSpace;
                    }
                    allowWaves = desiredWaveWidth > 2f;
                } else {
                    allowWaves = false;
                }
            }
        }

        float reservedWaveWidth = allowWaves ? desiredWaveWidth : 0f;
        float effectiveWavesAlpha = allowWaves ? wavesAlpha : 0f;
        float textBoxWidth = Math.max(0f, availableTextSpace - reservedWaveWidth);
        float collapsedClipWidth = Math.min(textBoxWidth, Math.max(fullTitleWidth, 0f) + 4f);
        float artistWidth = showArtist ? Fonts.SEMIBOLD.getWidth(displayArtist, 7f) : 0f;
        float collapsedTitleWidth = fullTitleWidth;
        if (!expandedText) {
            float maxCollapsedWidth = Math.max(0f, textBoxWidth);
            float collapsedEpsilon = 1.25f;
            if (fullTitleWidth <= maxCollapsedWidth + collapsedEpsilon) {
                collapsedTitleWidth = fullTitleWidth;
            } else {
                String clippedTitle = clipTextToWidthNoEllipsis(displayTitle, maxCollapsedWidth, 8f, Fonts.ROUNDBOLD);
                collapsedTitleWidth = Fonts.ROUNDBOLD.getWidth(clippedTitle, 8f);
            }
        }
        float visibleTextWidth = expandedText
                ? Math.min(textBoxWidth, Math.max(fullTitleWidth, artistWidth))
                : Math.min(textBoxWidth, collapsedTitleWidth);
        float clipWidth = expandedText ? textBoxWidth : collapsedClipWidth;
        boolean shouldMarquee = false;
        float marqueeOffset = 0f;

        float totalTextHeight = titleHeight + (showArtist ? artistHeight + textSpacing : 0f);
        float textY = y + padding + (contentHeight - totalTextHeight) / 2f;

        waves.update(playing);

        String safeTitle = clipTextToWidthNoEllipsis(displayTitle, clipWidth, 8f, Fonts.ROUNDBOLD);
        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(8f),
                safeTitle,
                textX - marqueeOffset,
                textY,
                textColor
        );

        if (showArtist) {
            // Артист рисуется SEMIBOLD — меряем им же
            String safeArtist = clipTextToWidthNoEllipsis(displayArtist, clipWidth, 7f, Fonts.SEMIBOLD);
            Render2D.drawFont(
                    e.getContext().getMatrices(),
                    Fonts.SEMIBOLD.getFont(7f),
                    safeArtist,
                    textX,
                    textY + titleHeight + textSpacing,
                    detailColor
            );
        }

        float waveAlpha = effectiveWavesAlpha * (expandedText ? 0.75f : 1f);
        float waveOffset = expandedText ? 4.1f : 4.0f;
        waves.render(e, textColor, textX, textY, padding, musicHeight, width, x, waveAlpha, visibleTextWidth, waveWidthScaleUsed, waveHeightScale, waveOffset, coverCenterY, expandedText);

        float bottomAnchor = y + totalHeight - padding - 0.0f;
        if (progressAlpha > 0.02f) {
        float controlsY = bottomAnchor - CONTROL_ICON_SIZE - 1f;
            float edgePadding = 4f;
            float iconSize = CONTROL_ICON_SIZE;
            float controlsWidth = iconSize * 3f + CONTROL_ICON_GAP * 2f + edgePadding * 2f;
            float controlsX = x + (width - controlsWidth) / 2f;
            float prevX = controlsX + edgePadding;
            float playX = prevX + iconSize + CONTROL_ICON_GAP;
            float nextX = playX + iconSize + CONTROL_ICON_GAP;
            Identifier playIcon = media.isPlaying() ? ICON_PAUSE : ICON_PLAY;
            int tint = detailColor.getRGB();
            Color iconColor = new Color(tint, true);
            drawControlIcon(e.getContext(), ICON_PREVIOUS, prevX, controlsY, iconSize, iconColor, true);
            drawControlIcon(e.getContext(), playIcon, playX, controlsY, iconSize, iconColor, true);
            drawControlIcon(e.getContext(), ICON_NEXT, nextX, controlsY, iconSize, iconColor, true);
            controlsVisible = true;
            lastControlsX = prevX;
            lastControlsY = controlsY;
            lastControlsWidth = controlsWidth;
        } else {
            controlsVisible = false;
        }

        if (progressAlpha > 0.05f) {
            float barX = x + padding;
            float barY = bottomAnchor - CONTROL_ICON_SIZE - PROGRESS_BAR_GAP - PROGRESS_BAR_HEIGHT - PROGRESS_BAR_RAISE - 1f;
            long duration = getDurationMs();
            long position = getPositionMs();
            String currentTimeLabel = formatTime(position);
            String totalTimeLabel = formatTime(duration);
            currentTimeText.update(currentTimeLabel);
            totalTimeText.update(totalTimeLabel);
            float timeFontSize = TIME_FONT_SIZE;
            float currentTextWidth = Fonts.SEMIBOLD.getWidth(currentTimeLabel, timeFontSize);
            float totalTextWidth = Fonts.SEMIBOLD.getWidth(totalTimeLabel, timeFontSize);
            float labelPadding = 1.8f;
            float availableWidth = Math.max(0f, width - padding * 2f - currentTextWidth - totalTextWidth - labelPadding * 4f);
            float barWidth = MathHelper.clamp(availableWidth, 110f, 640f);
            float totalBlockWidth = currentTextWidth + totalTextWidth + labelPadding * 4f + barWidth;
            float blockStartX = x + (width - totalBlockWidth) / 2f;
            float minEdge = x + padding;
            float maxEdge = x + width - padding;
            float blockEndX = blockStartX + totalBlockWidth;
            if (blockStartX < minEdge) {
                float shift = minEdge - blockStartX;
                blockStartX += shift;
                blockEndX += shift;
            }
            if (blockEndX > maxEdge) {
                float shift = blockEndX - maxEdge;
                blockStartX -= shift;
                blockEndX -= shift;
            }
            float barStartX = blockStartX + currentTextWidth + labelPadding * 2f;
            float progress = duration > 0 ? MathUtils.clamp(position / (float) duration, 0f, 1f) : 0f;

            Color barBg = new Color(255, 255, 255, (int) (70 * progressAlpha));
            Color barFill = new Color(255, 255, 255, (int) (200 * progressAlpha));

            Render2D.drawRoundedRect(
                    e.getContext().getMatrices(),
                    barStartX,
                    barY,
                    barWidth,
                    PROGRESS_BAR_HEIGHT,
                    Math.min(1.6f, PROGRESS_BAR_HEIGHT / 2f),
                    barBg
            );
            Render2D.drawRoundedRect(
                    e.getContext().getMatrices(),
                    barStartX,
                    barY,
                    barWidth * progress,
                    PROGRESS_BAR_HEIGHT,
                    Math.min(1.6f, PROGRESS_BAR_HEIGHT / 2f),
                    barFill
            );

            float labelY = barY + (PROGRESS_BAR_HEIGHT - Fonts.SEMIBOLD.getHeight(timeFontSize)) / 2f;
            float shift = Fonts.SEMIBOLD.getHeight(timeFontSize) * 0.55f;
            drawAnimatedText(
                    e.getContext(),
                    currentTimeText,
                    barStartX - labelPadding - currentTextWidth,
                    labelY,
                    detailColor,
                    shift,
                    timeFontSize
            );
            drawAnimatedText(
                    e.getContext(),
                    totalTimeText,
                    barStartX + barWidth + labelPadding,
                    labelY,
                    detailColor,
                    shift,
                    timeFontSize
            );
        }

        float lyricsAlpha = lyricsExpandAnimation.getValue() * progressAlpha;
        if (lyricsAlpha > 0.02f) {
            float lyricsHeightUsed = 36f * lyricsAlpha;
            float areaTop = (y + musicHeight - padding) - lyricsHeightUsed;
            float textPad = 6f;
            float textMaxWidth = Math.max(0f, width - padding * 2f - textPad * 2f);
            float lyricsFontSize = 8f;
            long posMs = getPositionMs();
            LyricsLine currentLine = null;
            LyricsLine nextLine = null;
            LyricsLine nextNextLine = null;
            LyricsLine prevLine = null;
            if (lyrics.isLoading()) {
                currentLine = new LyricsLine(0L, "Loading lyrics...");
            } else if (!lyrics.isLoaded() || lyrics.getLines().isEmpty()) {
                currentLine = new LyricsLine(0L, "No lyrics");
            } else {
                int idx = lyrics.findLyricsIndex(posMs);
                List<LyricsLine> lines = lyrics.getLines();
                synchronized (lines) {
                    if (idx >= 0 && idx < lines.size()) currentLine = lines.get(idx);
                    if (idx - 1 >= 0 && idx - 1 < lines.size()) prevLine = lines.get(idx - 1);
                    if (idx + 1 < lines.size()) nextLine = lines.get(idx + 1);
                    if (idx + 2 < lines.size()) nextNextLine = lines.get(idx + 2);
                }
                if (currentLine == null && !lines.isEmpty()) {
                    currentLine = lines.get(0);
                    if (lines.size() > 1) nextLine = lines.get(1);
                    if (lines.size() > 2) nextNextLine = lines.get(2);
                }
            }
            String prevText = prevLine != null ? clipTextToWidthSafe(prevLine.text(), textMaxWidth, lyricsFontSize) : "";
            String currentRawText = currentLine != null ? currentLine.text() : "";
            String nextRawText = nextLine != null ? nextLine.text() : "";
            String nextNextRawText = nextNextLine != null ? nextNextLine.text() : "";

            List<String> currentLines = wrapTextToWidth(currentRawText, textMaxWidth, lyricsFontSize, 2);
            String currentText = !currentLines.isEmpty() ? currentLines.get(0) : "";
            String currentContinuationText = currentLines.size() > 1 ? currentLines.get(1) : "";
            boolean currentWrapped = !currentContinuationText.isEmpty();

            String nextLineText = !nextRawText.isBlank() ? clipTextToWidthSafe(nextRawText, textMaxWidth, lyricsFontSize) : "";
            boolean atStartLayout = prevText.isEmpty();

            String line2Text;
            String line3Text;
            if (atStartLayout) {
                if (currentWrapped) {
                    line2Text = currentContinuationText;
                    if (!nextLineText.isEmpty()) {
                        line3Text = nextLineText;
                    } else {
                        line3Text = !nextNextRawText.isBlank()
                                ? clipTextToWidthSafe(nextNextRawText, textMaxWidth, lyricsFontSize)
                                : "";
                    }
                } else {
                    List<String> nextLines = wrapTextToWidth(nextRawText, textMaxWidth, lyricsFontSize, 2);
                    if (nextLines.size() > 1) {
                        line2Text = nextLines.get(0);
                        line3Text = nextLines.get(1);
                    } else {
                        line2Text = !nextLines.isEmpty() ? nextLines.get(0) : nextLineText;
                        line3Text = !nextNextRawText.isBlank()
                                ? clipTextToWidthSafe(nextNextRawText, textMaxWidth, lyricsFontSize)
                                : "";
                    }
                }
            } else {
                line2Text = prevText;
                line3Text = currentWrapped ? currentContinuationText : nextLineText;
            }

            lyricsPrevText.update(line2Text);
            lyricsCurrentText.update(currentText);
            lyricsNextText.update(line3Text);

            lastLyricsLinesBoundsValid = true;
            lastLyricsPrevLineDisplay = line2Text;
            lastLyricsCurrLineDisplay = currentText;
            lastLyricsNextLineDisplay = line3Text;
            lastLyricsPrevLineCopy = atStartLayout
                    ? (currentWrapped ? (currentLine != null ? currentLine.text() : currentRawText) : (nextLine != null ? nextLine.text() : line2Text))
                    : (prevLine != null ? prevLine.text() : prevText);
            lastLyricsCurrLineCopy = currentLine != null ? currentLine.text() : currentRawText;
            lastLyricsNextLineCopy = atStartLayout
                    ? (nextLine != null ? nextLine.text() : line3Text)
                    : (currentWrapped ? lastLyricsCurrLineCopy : (nextLine != null ? nextLine.text() : line3Text));

            int lyricAlpha = MathHelper.clamp((int) Math.round(detailColor.getAlpha() * lyricsAlpha), 0, 255);
            Color activeColor = getIslandTextColor(lyricAlpha);
            Color glassAlpha = ColorUtils.alpha(ColorUtils.getGlassBase(), lyricAlpha);
            Color fadedColor = ColorUtils.mix(activeColor, glassAlpha, 0.2f);
            Color inactiveColor = ColorUtils.mix(activeColor, glassAlpha, 0.35f);
            Color nextLineColor = currentWrapped ? activeColor : fadedColor;
            float lyricLineHeight = Fonts.SEMIBOLD.getHeight(lyricsFontSize);
            float lineSpacing = 3f;
            float lyricsBlockHeight = lyricLineHeight * 3f + lineSpacing * 2f;
            float blockStartY = y + musicHeight - padding - lyricsBlockHeight - 0.0f - CONTROL_ICON_SIZE - PROGRESS_BAR_GAP - PROGRESS_BAR_HEIGHT - PROGRESS_BAR_RAISE + 33f;

            float shift = lyricLineHeight * 0.55f;

            float yStep = lyricLineHeight + lineSpacing;
            float firstY = blockStartY;
            float secondY = firstY + yStep;
            float thirdY = secondY + yStep;

            boolean hasLine2Text = !lyricsPrevText.getCurrent().isEmpty();
            boolean hasLine3Text = !lyricsNextText.getCurrent().isEmpty();

            if (atStartLayout) {
                float currWidth = Fonts.SEMIBOLD.getWidth(lyricsCurrentText.getCurrent(), lyricsFontSize);
                float currX = x + (width - currWidth) / 2f;
                lastLyricsCurrLineX = currX;
                lastLyricsCurrLineY = firstY;
                lastLyricsCurrLineW = currWidth;
                lastLyricsCurrLineH = lyricLineHeight;
                drawAnimatedText(e.getContext(), lyricsCurrentText, currX, firstY, activeColor, shift, lyricsFontSize);

                if (hasLine2Text) {
                    float line2Width = Fonts.SEMIBOLD.getWidth(lyricsPrevText.getCurrent(), lyricsFontSize);
                    float line2X = x + (width - line2Width) / 2f;
                    lastLyricsPrevLineX = line2X;
                    lastLyricsPrevLineY = secondY;
                    lastLyricsPrevLineW = line2Width;
                    lastLyricsPrevLineH = lyricLineHeight;
                    Color line2Color = currentWrapped ? activeColor : fadedColor;
                    drawAnimatedText(e.getContext(), lyricsPrevText, line2X, secondY, line2Color, shift, lyricsFontSize);
                }

                if (hasLine3Text) {
                    float line3Width = Fonts.SEMIBOLD.getWidth(lyricsNextText.getCurrent(), lyricsFontSize);
                    float line3X = x + (width - line3Width) / 2f;
                    lastLyricsNextLineX = line3X;
                    lastLyricsNextLineY = thirdY;
                    lastLyricsNextLineW = line3Width;
                    lastLyricsNextLineH = lyricLineHeight;
                    drawAnimatedText(e.getContext(), lyricsNextText, line3X, thirdY, fadedColor, shift, lyricsFontSize);
                }
            } else {
                boolean hasPrevLine = !prevText.isEmpty();
                boolean hasRealNextLine = !nextLineText.isEmpty();

                int currentSlot;
                if (!hasPrevLine) {
                    currentSlot = 1;
                } else if (!hasRealNextLine && !currentWrapped) {
                    currentSlot = 3;
                } else {
                    currentSlot = 2;
                }

                float currY = switch (currentSlot) {
                    case 1 -> firstY;
                    case 3 -> thirdY;
                    default -> secondY;
                };
                float prevY = currY - yStep;
                float nextY = currY + yStep;
                if (currentSlot == 1 && !currentWrapped && hasRealNextLine) {
                    nextY = currY + yStep * 2f;
                }
                if (currentSlot == 3 && !currentWrapped && hasPrevLine) {
                    prevY = currY - yStep * 2f;
                }

                if (hasPrevLine) {
                    float textWidth = Fonts.SEMIBOLD.getWidth(lyricsPrevText.getCurrent(), lyricsFontSize);
                    float lyricX = x + (width - textWidth) / 2f;
                    lastLyricsPrevLineX = lyricX;
                    lastLyricsPrevLineY = prevY;
                    lastLyricsPrevLineW = textWidth;
                    lastLyricsPrevLineH = lyricLineHeight;
                    drawAnimatedText(e.getContext(), lyricsPrevText, lyricX, prevY, inactiveColor, shift, lyricsFontSize);
                }

                float currWidth = Fonts.SEMIBOLD.getWidth(lyricsCurrentText.getCurrent(), lyricsFontSize);
                float currX = x + (width - currWidth) / 2f;
                lastLyricsCurrLineX = currX;
                lastLyricsCurrLineY = currY;
                lastLyricsCurrLineW = currWidth;
                lastLyricsCurrLineH = lyricLineHeight;
                drawAnimatedText(e.getContext(), lyricsCurrentText, currX, currY, activeColor, shift, lyricsFontSize);

                if (hasLine3Text) {
                    float nextWidth = Fonts.SEMIBOLD.getWidth(lyricsNextText.getCurrent(), lyricsFontSize);
                    float nextX = x + (width - nextWidth) / 2f;
                    lastLyricsNextLineX = nextX;
                    lastLyricsNextLineY = nextY;
                    lastLyricsNextLineW = nextWidth;
                    lastLyricsNextLineH = lyricLineHeight;
                    drawAnimatedText(e.getContext(), lyricsNextText, nextX, nextY, nextLineColor, shift, lyricsFontSize);
                }
            }
        }
    }

    private boolean handleLyricsLineClick(int mouseX, int mouseY) {
        if (!mediaExpanded) return false;
        if (!lyrics.isOpen()) return false;
        if (!lastLyricsLinesBoundsValid) return false;

        int hovered = getHoveredLyricsLineId(mouseX, mouseY);
        if (hovered == 0) return false;

        long now = System.currentTimeMillis();
        boolean isDoubleClick = hovered == lastLyricsLineClickId && now - lastLyricsLineClickTimeMs <= 280L;
        lastLyricsLineClickTimeMs = now;
        lastLyricsLineClickId = hovered;

        if (!isDoubleClick) return true;

        String toCopy = switch (hovered) {
            case 1 -> lastLyricsPrevLineCopy;
            case 2 -> lastLyricsCurrLineCopy;
            case 3 -> lastLyricsNextLineCopy;
            default -> "";
        };
        if (toCopy == null || toCopy.isBlank()) return true;

        mc.keyboard.setClipboard(toCopy);
        return true;
    }

    private int getHoveredLyricsLineId(int mouseX, int mouseY) {
        if (!lastLyricsLinesBoundsValid) return 0;
        if (!lastLyricsPrevLineDisplay.isBlank()
                && MathUtils.isHovered(lastLyricsPrevLineX, lastLyricsPrevLineY, lastLyricsPrevLineW, lastLyricsPrevLineH, mouseX, mouseY)) {
            return 1;
        }
        if (!lastLyricsCurrLineDisplay.isBlank()
                && MathUtils.isHovered(lastLyricsCurrLineX, lastLyricsCurrLineY, lastLyricsCurrLineW, lastLyricsCurrLineH, mouseX, mouseY)) {
            return 2;
        }
        if (!lastLyricsNextLineDisplay.isBlank()
                && MathUtils.isHovered(lastLyricsNextLineX, lastLyricsNextLineY, lastLyricsNextLineW, lastLyricsNextLineH, mouseX, mouseY)) {
            return 3;
        }
        return 0;
    }

    private boolean hasMedia() {
        return media.hasMedia();
    }

    private boolean isMusicPlaying() {
        return media.isPlaying();
    }

    private String getMediaTitle() {
        String title = media.getTitle();
        if (isUnknownTrack(title)) {
            return getDisplayPlayerName();
        }
        return title;
    }

    private String getMediaArtist() {
        return media.getArtist();
    }

    private boolean isUnknownTrack(String title) {
        return title == null || title.isBlank() || "Unknown track".equalsIgnoreCase(title.trim());
    }

    private String getDisplayPlayerName() {
        String replacementName = StreamerModeUtils.stripLegacyFormatting(StreamerModeUtils.getReplacementName());
        if (replacementName != null && !replacementName.isBlank()) {
            return replacementName;
        }
        if (mc.player != null && mc.player.getGameProfile() != null) {
            return mc.player.getGameProfile().name();
        }
        return "Player";
    }

    private long getDurationMs() {
        return media.getDurationMs();
    }

    private long getPositionMs() {
        return media.getPositionMs();
    }

    private void updateMediaInfo() {
        DynamicIslandMedia.MediaUpdate update = media.update();
        if (update.cleared()) {
            mediaExpanded = false;
            currentTimeText.update("0:00");
            totalTimeText.update("0:00");
            lyrics.reset();
            lyricsExpandAnimation.snapTo(false);
            lyricsPrevText.update("");
            lyricsCurrentText.update("");
            lyricsNextText.update("");
        }
        if (update.mediaChanged()) {
            mediaExpanded = false;
            lyrics.reset();
            lyricsExpandAnimation.snapTo(false);
            lyricsPrevText.update("");
            lyricsCurrentText.update("");
            lyricsNextText.update("");
            lyrics.startFetch(media.getRawTitle(), media.getRawArtist(), media.getRawDurationMs());
        }
    }

    private float computeEndPadding(float padding, boolean showNotification, boolean showPvp) {
        if (showNotification || showPvp) {
            return padding * 3.3f;
        }
        return padding * 1.35f;
    }

    private boolean handleControlClick(int mouseX, int mouseY) {
        if (!controlsVisible) return false;

        float btnY = lastControlsY;
        float btnH = CONTROL_ICON_SIZE * lastInteractionUiScale;
        float btnW = CONTROL_ICON_SIZE * lastInteractionUiScale;
        float gap = CONTROL_ICON_GAP * lastInteractionUiScale;

        float prevX = lastControlsX;
        float playX = lastControlsX + btnW + gap;
        float nextX = lastControlsX + (btnW + gap) * 2f;

        try {
            if (MathUtils.isHovered(prevX, btnY, btnW, btnH, mouseX, mouseY)) {
                return media.previous();
            }
            if (MathUtils.isHovered(playX, btnY, btnW, btnH, mouseX, mouseY)) {
                return media.playPause();
            }
            if (MathUtils.isHovered(nextX, btnY, btnW, btnH, mouseX, mouseY)) {
                return media.next();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void updateCollapsedInteractionBounds() {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        float width = Math.max(animatedWidth, 72f);
        float height = 19f;
        float uiScale = getUiScale();
        float visualWidth = width * uiScale;
        float visualHeight = height * uiScale;
        float x = mc.getWindow().getScaledWidth() / 2f - visualWidth / 2f;
        float y = 1f;
        setBounds(x, y, visualWidth, visualHeight);
    }

    private void applyUiScaleToInteractiveBounds(float originX, float originY, float uiScale) {
        if (controlsVisible) {
            lastControlsX = scaleUiX(originX, lastControlsX, uiScale);
            lastControlsY = scaleUiY(originY, lastControlsY, uiScale);
            lastControlsWidth *= uiScale;
        }

        if (!lastLyricsLinesBoundsValid) {
            return;
        }

        lastLyricsPrevLineX = scaleUiX(originX, lastLyricsPrevLineX, uiScale);
        lastLyricsPrevLineY = scaleUiY(originY, lastLyricsPrevLineY, uiScale);
        lastLyricsPrevLineW *= uiScale;
        lastLyricsPrevLineH *= uiScale;

        lastLyricsCurrLineX = scaleUiX(originX, lastLyricsCurrLineX, uiScale);
        lastLyricsCurrLineY = scaleUiY(originY, lastLyricsCurrLineY, uiScale);
        lastLyricsCurrLineW *= uiScale;
        lastLyricsCurrLineH *= uiScale;

        lastLyricsNextLineX = scaleUiX(originX, lastLyricsNextLineX, uiScale);
        lastLyricsNextLineY = scaleUiY(originY, lastLyricsNextLineY, uiScale);
        lastLyricsNextLineW *= uiScale;
        lastLyricsNextLineH *= uiScale;
    }

    private String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void renderNotificationContent(EventRender2D e, float x, float y, float height, float padding, float textAreaWidth, DynamicIslandNotification notification, float alpha) {
        if (notification == null || alpha <= 0.01f) return;

        int finalAlpha = MathHelper.clamp((int) Math.round(255 * alpha), 0, 255);
        float transition = MathHelper.clamp(notificationContentAnimation.getValue(), 0f, 1f);
        float pop = (float) Math.sin(Math.PI * transition);

        float baseDiameter = height - padding * 2f;
        float dotScale = 1f + pop * 0.18f;
        float circleDiameter = baseDiameter * dotScale;
        float circleRadius = circleDiameter / 2f;

        float centerX = x + padding + baseDiameter / 2f;
        float centerY = y + height / 2f;
        float circleX = centerX - circleDiameter / 2f;
        float circleY = centerY - circleDiameter / 2f;

        Color blendedStatus = ColorUtils.mix(notificationStatusFrom, notificationStatusTo, transition);
        Color statusColor = new Color(blendedStatus.getRed(), blendedStatus.getGreen(), blendedStatus.getBlue(), finalAlpha);

        Render2D.drawRoundedRect(
                e.getContext().getMatrices(),
                circleX,
                circleY,
                circleDiameter,
                circleDiameter,
                circleRadius,
                statusColor
        );

        Color textColor = getIslandTextColor(finalAlpha);
        float textShift = Fonts.ROUNDBOLD.getHeight(8f) * 0.55f;
        float textY = y + (height - Fonts.ROUNDBOLD.getHeight(8f)) / 2f;
        String currentText = notificationText.getCurrent();
        String clippedText = clipTextToWidthNoEllipsis(currentText, textAreaWidth, 8f, Fonts.ROUNDBOLD);
        if (!clippedText.equals(currentText)) {
            // Текст шире пилюли (ширина ещё догоняет цель) — рисуем обрезанный без анимации
            Render2D.drawFont(
                    e.getContext().getMatrices(),
                    Fonts.ROUNDBOLD.getFont(8f),
                    clippedText,
                    x + height,
                    textY,
                    textColor
            );
            return;
        }
        drawAnimatedText(
                e.getContext(),
                notificationText,
                x + height,
                textY,
                textColor,
                textShift,
                8f,
                Fonts.ROUNDBOLD
        );
    }

    private void updateNotificationAnimations(DynamicIslandNotification notification) {
        String nextText = notification.getDisplayText();
        boolean nextEnabled = notification.isEnabled();
        Color nextStatusColor = notification.getStatusColor() != null
                ? notification.getStatusColor()
                : (nextEnabled ? notificationEnabledColor : notificationDisabledColor);

        boolean textChanged = !Objects.equals(lastNotificationText, nextText);
        boolean enabledChanged = !notificationStateInitialized || lastNotificationEnabled != nextEnabled;
        boolean colorChanged = !notificationStateInitialized || !Objects.equals(notificationStatusTo, nextStatusColor);

        notificationText.update(nextText);

        if (textChanged || enabledChanged || colorChanged) {
            float currentProgress = MathHelper.clamp(notificationContentAnimation.getValue(), 0f, 1f);
            Color current = ColorUtils.mix(notificationStatusFrom, notificationStatusTo, currentProgress);
            notificationStatusFrom = current;
            notificationStatusTo = nextStatusColor;

            notificationContentAnimation.snapTo(false);
            notificationContentAnimation.update(true);
        }

        lastNotificationText = nextText;
        lastNotificationEnabled = nextEnabled;
        notificationStateInitialized = true;
    }

    private void resetNotificationAnimations() {
        notificationStateInitialized = false;
        lastNotificationText = "";
        lastNotificationEnabled = false;
        notificationStatusFrom = notificationEnabledColor;
        notificationStatusTo = notificationEnabledColor;
        notificationContentAnimation.snapTo(false);
    }

    private float getAnimatedNotificationTextWidth() {
        String current = notificationText.getCurrent();
        float currentWidth = Fonts.ROUNDBOLD.getWidth(current, 8f);
        if (!notificationText.isAnimating()) {
            return currentWidth;
        }
        float prevWidth = Fonts.ROUNDBOLD.getWidth(notificationText.getPrevious(), 8f);
        return Math.max(currentWidth, prevWidth);
    }

    private void renderDefaultContent(EventRender2D e, float x, float y, float height, float padding, float textAreaWidth, boolean isPvp, float alpha) {
        if (alpha <= 0.01f) return;

        float circleDiameter = height - padding * 2;
        float circleY = y + (height - circleDiameter) / 2;

        int baseAlpha = (int) (255 * alpha);
        Color textColor = getIslandTextColor(baseAlpha);
        Color pvpTimerColor = getIslandTextColor((int) (255 * pvpAnimation.getValue() * alpha));

        if (!isPvp) {
            float finalAlpha = (int) (255 * alpha);
            if (mc.player instanceof AbstractClientPlayerEntity) {
                float circleRadius = circleDiameter / 2.5f;
                Render2D.drawTextureNearest(
                        e.getContext(), x + padding, circleY, circleDiameter, circleDiameter,
                        circleRadius, 0.125f, 0.125f, 0.25f, 0.25f,
                        ((AbstractClientPlayerEntity) mc.player).getSkin().body().texturePath(),
                        new Color(255, 255, 255, (int) finalAlpha)
                );
            } else {
                Color theme = ColorUtils.getGlobalColor((int) MathHelper.clamp(90f + 80f * alpha, 0f, 255f));
                Render2D.drawRoundedRect(
                        e.getContext().getMatrices(),
                        x + padding,
                        circleY,
                        circleDiameter,
                        circleDiameter,
                        circleDiameter / 2f,
                        theme
                );
            }
            String displayName = clipTextToWidth(mc.player != null ? getProtectedPlayerName() : "LinkenVisuals", textAreaWidth, 8f, Fonts.ROUNDBOLD);
            float textY = y + (height - Fonts.ROUNDBOLD.getHeight(8f)) / 2f;
            Render2D.drawFont(
                    e.getContext().getMatrices(),
                    Fonts.ROUNDBOLD.getFont(8f),
                    displayName,
                    x + height,
                    textY,
                    textColor
            );
          } else {
              float finalAlpha = (int) (255 * pvpAnimation.getValue() * alpha);
              String pvpTimer = Server.getPvpTimer().isEmpty() ? "30" : Server.getPvpTimer();
  
              float circleRadius = circleDiameter / 2f;
              float circleX = x + padding;
              Render2D.drawRoundedRect(
                      e.getContext().getMatrices(),
                      circleX,
                      circleY,
                      circleDiameter,
                      circleDiameter,
                      circleRadius,
                      new Color(255, 0, 0, (int) finalAlpha)
              );
  
              float timerTextWidth = Fonts.SEMIBOLD.getWidth(pvpTimer, 7f);
              float timerX = (x + padding + circleDiameter / 2f) - (timerTextWidth / 2f);
              Render2D.drawFont(
                      e.getContext().getMatrices(),
                    Fonts.SEMIBOLD.getFont(7f),
                    pvpTimer,
                    timerX,
                    y + (height - Fonts.SEMIBOLD.getHeight(7f)) / 2f,
                    pvpTimerColor
            );

            String pvpText = clipTextToWidth(I18n.translate("elements.dynamicisland.pvp"), textAreaWidth, 8f, Fonts.ROUNDBOLD);
            float textY = y + (height - Fonts.ROUNDBOLD.getHeight(8f)) / 2f;
            Render2D.drawFont(
                    e.getContext().getMatrices(),
                    Fonts.ROUNDBOLD.getFont(8f),
                    pvpText,
                    x + height,
                    textY,
                    textColor
            );
        }
    }

    private void renderSideElements(EventRender2D e, float x, float y, float width, float height, float padding, float sideOffset, Color textColor) {
        String currentTime = MathUtils.getCurrentTime();
        float timeTextWidth = Fonts.ROUNDBOLD.getWidth(currentTime, 8f);
        int grayValue = textColor.getRed();

        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ROUNDBOLD.getFont(8f),
                currentTime,
                x - sideOffset - timeTextWidth,
                y + (height - Fonts.ROUNDBOLD.getHeight(8f)) / 2f,
                textColor
        );

        float iconY = y + (height - Fonts.ICONSNEW.getHeight(8f)) / 2f;
        float iconX = x + width + sideOffset;
        Color connectedColor = new Color(grayValue, grayValue, grayValue, (int) (255 * internetAnimation.getValue()));
        Color disconnectedColor = new Color(grayValue, grayValue, grayValue, (int) (255 * internetAnimation.getReversedValue()));

        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ICONSNEW.getFont(8f),
                "O",
                iconX,
                iconY,
                connectedColor
        );
        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.ICONSNEW.getFont(8f),
                "I",
                iconX,
                iconY,
                disconnectedColor
        );
    }

    private Color resolveAdaptiveTextColor(float x, float y, float width, float height, float sideOffset, String displayText) {
        float timeTextWidth = Fonts.ROUNDBOLD.getWidth(MathUtils.getCurrentTime(), 8f);

        float iconGlyphWidth = Fonts.ICONSNEW.getWidth("I", 8f);
        float iconSampleX = x + width + sideOffset + 6.0f + iconGlyphWidth / 2f;
        float iconSampleY = y + height / 2f - 6.0f;

        float timeSampleX = x - sideOffset - timeTextWidth / 2f - 6.0f;
        float timeSampleY = y + height / 2f - 6.0f;

        float windowWidth = mc.getWindow().getScaledWidth();
        float windowHeight = mc.getWindow().getScaledHeight();

        iconSampleX = MathUtils.clamp(iconSampleX, 0f, windowWidth);
        iconSampleY = MathUtils.clamp(iconSampleY, 0f, windowHeight);
        timeSampleX = MathUtils.clamp(timeSampleX, 0f, windowWidth);
        timeSampleY = MathUtils.clamp(timeSampleY, 0f, windowHeight);

        long now = System.currentTimeMillis();
        boolean shouldSample = now - lastBackgroundSample > 250L;

        if (shouldSample) {
            cachedIconBackground = ColorUtils.picker(iconSampleX, iconSampleY);
            cachedTimeBackground = ColorUtils.picker(timeSampleX, timeSampleY);
            lastBackgroundSample = now;
        }

        float luminanceAverage = (computeLuminance(cachedIconBackground) + computeLuminance(cachedTimeBackground)) * 0.5f;
        float targetLuminance = MathHelper.clamp(luminanceAverage, 0f, 1f);
        float smoothFactor = timeBasedLerpFactor(7.5f, lastFrameDeltaSeconds);
        smoothedLuminance = MathHelper.lerp(smoothFactor, smoothedLuminance, targetLuminance);

        if (preferWhite) {
            if (smoothedLuminance > 0.5f + 0.08f) {
                preferWhite = false;
            }
        } else {
            if (smoothedLuminance < 0.5f - 0.08f) {
                preferWhite = true;
            }
        }

        textColorAnimation.update(preferWhite);
        float mix = textColorAnimation.getValue();
        if (mix > 0.99f) mix = 1f;
        else if (mix < 0.01f) mix = 0f;
        int channel = MathHelper.clamp(Math.round(255f * mix), 0, 255);

        return new Color(channel, channel, channel);
    }

    private Color getIslandTextColor(int alpha) {
        int safeAlpha = MathHelper.clamp(alpha, 0, 255);
        if (isMainMenuScreen()) {
            return new Color(255, 255, 255, safeAlpha);
        }
        return ColorUtils.getTextColor(safeAlpha);
    }

    private float computeLuminance(Color color) {
        return (0.299f * color.getRed() + 0.587f * color.getGreen() + 0.114f * color.getBlue()) / 255f;
    }

    private float getAutoScrollOffset(float textWidth, float clipWidth) {
        if (textWidth <= clipWidth) return 0f;
        long now = System.currentTimeMillis();
        float overflow = Math.max(0f, textWidth - clipWidth);
        long cycle = 5000L;
        long pause = 500L;
        long travel = Math.max(1L, (cycle - pause * 2L) / 2L);
        long total = pause + travel + pause + travel;
        long phase = now % total;
        if (phase < pause) return 0f;
        phase -= pause;
        if (phase < travel) {
            return overflow * (phase / (float) travel);
        }
        phase -= travel;
        if (phase < pause) return overflow;
        phase -= pause;
        if (phase < travel) {
            return overflow * (1f - phase / (float) travel);
        }
        return 0f;
    }

    private float computeProgressBlockWidth() {
        float timeFontSize = TIME_FONT_SIZE;
        String currentTimeLabel = currentTimeText.getCurrent();
        String totalTimeLabel = totalTimeText.getCurrent();
        float currentTextWidth = Fonts.SEMIBOLD.getWidth(currentTimeLabel, timeFontSize);
        float totalTextWidth = Fonts.SEMIBOLD.getWidth(totalTimeLabel, timeFontSize);
        return currentTextWidth + totalTextWidth + 90f + 1.8f * 4f;
    }

    private void toggleLyrics() {
        if (lyrics.isUnavailable()) return;
        lyrics.toggle(media.getRawTitle(), media.getRawArtist(), media.getRawDurationMs());
    }

    private void drawControlIcon(DrawContext context, Identifier icon, float x, float y, float size, Color color, boolean smooth) {
        if (context == null || icon == null || size <= 0f) return;
        float sizePx = Math.max(1f, Math.round(size));
        float xPx = Math.round(x);
        float yPx = Math.round(y);
        AbstractTexture texture = mc.getTextureManager().getTexture(icon);
        float radius = MathHelper.clamp(sizePx * 0.22f, 1.6f, 4.2f);
        Render2D.drawTexture(context.getMatrices(), xPx, yPx, sizePx, sizePx, radius, icon, color);
    }

    private void drawAnimatedText(DrawContext context, AnimatedString animation, float x, float y, Color color, float shift, float fontSize) {
        drawAnimatedText(context, animation, x, y, color, shift, fontSize, Fonts.SEMIBOLD);
    }

    private void drawAnimatedText(DrawContext context, AnimatedString animation, float x, float y, Color color, float shift, float fontSize, Font font) {
        if (animation == null) return;
        boolean animating = animation.isAnimating();
        float progress = animation.progress();
        String current = animation.getCurrent();
        if (!animating) {
            Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), current, x, y, color);
            return;
        }

        float easedProgress = Math.max(0f, Math.min(1f, progress));
        int baseAlpha = color.getAlpha();
        int prevAlpha = Math.round(baseAlpha * (1f - easedProgress));
        int nextAlpha = Math.round(baseAlpha * easedProgress);

        Color prevColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), prevAlpha);
        Color nextColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), nextAlpha);

        float prevY = y - shift * easedProgress;
        float nextY = y + shift * (1f - easedProgress);

        String previous = animation.getPrevious();
        String curr = animation.getCurrent();
        int prefixLength = AnimatedTextHelper.getPrefixLength(previous, curr);
        int suffixLength = AnimatedTextHelper.getSuffixLength(previous, curr, prefixLength);

        String prefix = AnimatedTextHelper.substringSafe(curr, 0, prefixLength);
        String prevMid = AnimatedTextHelper.substringSafe(previous, prefixLength, previous.length() - suffixLength);
        String currMid = AnimatedTextHelper.substringSafe(curr, prefixLength, curr.length() - suffixLength);
        String suffix = AnimatedTextHelper.substringSafe(curr, curr.length() - suffixLength, curr.length());

        float prefixWidth = font.getWidth(prefix, fontSize);
        float currMidWidth = font.getWidth(currMid, fontSize);
        float prevMidWidth = font.getWidth(prevMid, fontSize);

        float midStartX = x + prefixWidth;
        float currMidX = midStartX;
        float prevMidX = midStartX + (currMidWidth - prevMidWidth) / 2f;

        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), prefix, x, y, color);
        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), suffix, midStartX + currMidWidth, y, color);

        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), prevMid, prevMidX, prevY, prevColor);
        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), currMid, currMidX, nextY, nextColor);
    }

    // Меряем тем же шрифтом, которым текст реально рисуется.
    // Ширину ведём инкрементально (сумма ширин символов) — раньше на каждый символ
    // делался sb.toString() + полный перемер строки, O(n²) на кадр лирики.
    private String clipTextToWidth(String text, float maxWidth, float fontSize, Font font) {
        if (text == null) return "";
        if (font.getWidth(text, fontSize) <= maxWidth) return text;
        String ellipsis = "...";
        float ellWidth = font.getWidth(ellipsis, fontSize);
        if (ellWidth >= maxWidth) return "";
        StringBuilder sb = new StringBuilder();
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            float chWidth = font.getWidth(String.valueOf(ch), fontSize);
            if (width + chWidth + ellWidth > maxWidth) break;
            sb.append(ch);
            width += chWidth;
        }
        sb.append(ellipsis);
        return sb.toString();
    }

    private String clipTextToWidthSafe(String text, float maxWidth, float fontSize) {
        // Лирика рисуется SEMIBOLD
        String clipped = clipTextToWidth(text, maxWidth, fontSize, Fonts.SEMIBOLD);
        if (!clipped.isEmpty() || text == null || text.isEmpty()) return clipped;
        return clipTextToWidthNoEllipsis(text, maxWidth, fontSize, Fonts.SEMIBOLD);
    }

    // Кэш врапа: строка лирики меняется раз в секунды, а wrap зовётся до 2 раз за кадр.
    // Ключ — текст+ширина+кегль+лимит строк; лимит 64 записей, чтобы не копить старую лирику.
    private final Map<String, List<String>> wrapCache = new java.util.HashMap<>();

    private List<String> wrapTextToWidth(String text, float maxWidth, float fontSize, int maxLines) {
        if (text == null) return List.of("");
        if (maxWidth <= 0f) return List.of("");
        String cacheKey = text + '\0' + maxWidth + '\0' + fontSize + '\0' + maxLines;
        List<String> cached = wrapCache.get(cacheKey);
        if (cached != null) return cached;
        List<String> result = wrapTextToWidthUncached(text, maxWidth, fontSize, maxLines);
        if (wrapCache.size() > 64) wrapCache.clear();
        wrapCache.put(cacheKey, result);
        return result;
    }

    private List<String> wrapTextToWidthUncached(String text, float maxWidth, float fontSize, int maxLines) {
        int safeMaxLines = Math.max(1, maxLines);
        List<String> lines = new java.util.ArrayList<>();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return List.of("");
        StringBuilder line = new StringBuilder();
        float lineWidth = 0f;
        float spaceWidth = Fonts.SEMIBOLD.getWidth(" ", fontSize);
        String[] words = trimmed.split("\\s+");
        for (int w = 0; w < words.length; w++) {
            String word = words[w];
            float wordWidth = Fonts.SEMIBOLD.getWidth(word, fontSize);
            // Ширина строки — инкрементально (строка+пробел+слово), без полного перемера кандидата.
            float candidateWidth = line.length() == 0 ? wordWidth : lineWidth + spaceWidth + wordWidth;
            if (candidateWidth <= maxWidth) {
                if (line.length() > 0) line.append(' ');
                line.append(word);
                lineWidth = candidateWidth;
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                if (lines.size() >= safeMaxLines) {
                    lines.set(lines.size() - 1, clipTextToWidthSafe(lines.get(lines.size() - 1), maxWidth, fontSize));
                    return lines;
                }
                line.setLength(0);
                lineWidth = 0f;
            }
            if (wordWidth <= maxWidth) {
                line.append(word);
                lineWidth = wordWidth;
                continue;
            }
            int idx = 0;
            while (idx < word.length()) {
                StringBuilder chunk = new StringBuilder();
                float chunkWidth = 0f;
                while (idx < word.length()) {
                    char ch = word.charAt(idx);
                    float chWidth = Fonts.SEMIBOLD.getWidth(String.valueOf(ch), fontSize);
                    if (chunkWidth + chWidth > maxWidth) break;
                    chunk.append(ch);
                    chunkWidth += chWidth;
                    idx++;
                }
                if (chunk.length() == 0 && idx < word.length()) {
                    chunk.append(word.charAt(idx));
                    idx++;
                }
                lines.add(chunk.toString());
                if (lines.size() >= safeMaxLines) {
                    lines.set(lines.size() - 1, clipTextToWidthSafe(lines.get(lines.size() - 1), maxWidth, fontSize));
                    return lines;
                }
            }
        }
        String last = line.toString().trim();
        if (!last.isEmpty() && lines.size() < safeMaxLines) {
            lines.add(last);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    // Меряем тем же шрифтом, которым текст реально рисуется (ширина — инкрементально, см. clipTextToWidth)
    private String clipTextToWidthNoEllipsis(String text, float maxWidth, float fontSize, Font font) {
        if (text == null) return "";
        if (font.getWidth(text, fontSize) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            float chWidth = font.getWidth(String.valueOf(ch), fontSize);
            if (width + chWidth > maxWidth) break;
            sb.append(ch);
            width += chWidth;
        }
        return sb.toString();
    }

    private String getProtectedPlayerName() {
        if (mc.player == null) return "LinkenVisuals";
        return StreamerModeUtils.resolvePlainDisplayName(mc.player.getGameProfile().name());
    }

}
