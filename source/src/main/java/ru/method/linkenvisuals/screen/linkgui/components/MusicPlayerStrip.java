package ru.method.linkenvisuals.screen.linkgui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.impl.DynamicIsland;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandMedia;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandMediaHolder;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.mediaplayer.SystemVolumeController;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public class MusicPlayerStrip {
    private static final float ART = 40f;
    private static final float PROGRESS_ZONE = 24f;
    private static final float TITLE_SIZE = 10.2f;
    private static final float ARTIST_SIZE = 8.4f;
    private static final float CONTROL_ICON = 13.8f;
    private static final float VOL_ICON = 9.2f;
    private static final float VOL_BAR_W = 52f;
    private static final float VOL_BAR_H = 3f;
    private static final float CONTROL_BTN = 26.4f;
    private static final float MEDIA_BASELINE = 1.08984375f;

    private final DynamicIslandMedia media = DynamicIslandMediaHolder.shared();
    private final Animation[] controlHover = {
            new Animation(240, 1f, false, Easing.EASE_OUT_CUBIC),
            new Animation(240, 1f, false, Easing.EASE_OUT_CUBIC),
            new Animation(240, 1f, false, Easing.EASE_OUT_CUBIC)
    };
    private final Animation volumeHover = new Animation(220, 1f, false, Easing.EASE_OUT_CUBIC);
    // Плавная смена иконки громкости: 0 = выкл (mute), 1 = есть звук (sound).
    private final Animation volumeIconAnim = new Animation(220, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation playPauseAnim = new Animation(260, 1f, false, Easing.EASE_OUT_CUBIC);
    // Hover значка mute анимирован, как у остальных кнопок плеера (альфа скакала 215→245 за кадр).
    private final Animation muteHoverAnim = new Animation(220, 1f, false, Easing.EASE_OUT_CUBIC);

    private float x, y, width, height = 76f;
    private float volume = 0.72f;
    private boolean volumeDragging;
    private long lastVolumePollMs;
    private long lastVolumeSetMs;
    // Сглаживание ползунка громкости.
    private float displayedVolume = 0.72f;
    private boolean volumeInitialized;
    private long lastVolumeFrameMs;
    // Громкость до mute — чтобы повторный клик по значку вернул прежнее значение.
    private float preMuteVolume = 0.5f;
    // Сглаживание прогресса трека — чтобы полоска ехала плавно, без рывков.
    private float displayedProgress;
    private boolean progressInitialized;
    private long lastProgressFrameMs;
    // Кэш обрезки названия/исполнителя: clip() промеряет ширину строки в цикле — незачем каждый кадр.
    private String clipTitleSrc;
    private float clipTitleMax;
    private String clipTitleResult;
    private String clipArtistSrc;
    private float clipArtistMax;
    private String clipArtistResult;
    // Кэш строк времени — текст меняется раз в секунду, а не раз в кадр.
    private long cachedPosSec = -1L;
    private String cachedPosStr = "0:00";
    private long cachedDurSec = -1L;
    private String cachedDurStr = "0:00";

    public void init() {
        media.ensureExecutor();
        // Громкость — через выделенный COM-поток (не на render-потоке): чинит зависание при выходе из игры.
        SystemVolumeController.ensureInit();
        SystemVolumeController.refresh();
        Float sys = SystemVolumeController.currentVolume();
        if (sys != null) volume = sys;
        volumeIconAnim.snapTo(volume >= 0.01f);
        // Состояние play/pause на момент открытия не анимируем — иначе глиф «попает» при каждом открытии GUI.
        if (media.isPlaying()) playPauseAnim.snapOpen();
        else playPauseAnim.snapTo(false);
    }

    public void shutdown() {
        volumeDragging = false;
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void tick() {
        if (!isDynamicIslandUpdating()) {
            media.update();
        }
        long now = System.currentTimeMillis();
        // Опрашиваем системную громкость, только если её не тянет пользователь и прошёл кулдаун после
        // его изменения — иначе ползунок «отскакивал» к старому значению при медленном системном апдейте.
        if (!volumeDragging && now - lastVolumePollMs > 250L && now - lastVolumeSetMs > 600L) {
            SystemVolumeController.refresh();
            Float sys = SystemVolumeController.currentVolume();
            if (sys != null) volume = sys;
            lastVolumePollMs = now;
        }
        playPauseAnim.update(media.isPlaying());
    }

    private float artY(float topH) {
        return y + (topH - ART) / 2f + 2f;
    }

    private record ControlsLayout(float[] btnCenterX, float btnCenterY) {}

    private ControlsLayout controlsLayout(float topH) {
        float centerX = x + width / 2f;
        float btnCenterY = artY(topH) + ART / 2f + 5f;
        float[] btnCenterX = {centerX - 29f, centerX, centerX + 29f};
        return new ControlsLayout(btnCenterX, btnCenterY);
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        LinkGuiChrome.drawMusicStrip(context.getMatrices(), x, y, width, height);

        float topH = height - PROGRESS_ZONE;
        float artX = x + 14f;
        float artY = artY(topH);
        float artCenterY = artY + ART / 2f;
        ControlsLayout controls = controlsLayout(topH);

        if (!media.hasMedia()) {
            Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(9.2f), "No media playing",
                    artX + ART + 10f, artCenterY - 4f, LinkGuiThemeScope.text(110));
            drawControlsArea(context, controls, mouseX, mouseY, false);
            progressInitialized = false;
            displayedProgress = 0f;
            // Пустой плеер притушен: полнояркие бар и «0:00» читались как играющий трек.
            drawProgressArea(context, 0f, "0:00", "0:00", 0.55f);
            drawVolumeArea(context, mouseX, mouseY, artCenterY);
            return;
        }

        NativeImageBackedTexture artwork = media.getArtworkTexture();
        if (artwork != null) {
            // Обложка гаснет вместе с GUI (раньше Color.WHITE игнорировал затухание и она «выскакивала»).
            int artAlpha = Math.max(0, Math.min(255, Math.round(255f
                    * ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier())));
            Render2D.drawTexture(context.getMatrices(), artX, artY, ART, ART, 7f, artwork,
                    new Color(255, 255, 255, artAlpha));
        } else {
            Render2D.drawRoundedRect(context.getMatrices(), artX, artY, ART, ART, 7f,
                    new Color(LinkGuiThemeScope.accent().getRed(), LinkGuiThemeScope.accent().getGreen(),
                            LinkGuiThemeScope.accent().getBlue(), 120));
        }

        String title = media.getTitle();
        if (title == null || title.isBlank()) title = "Unknown";
        String artist = media.getArtist();
        // Координаты текста округляем до пикселя — иначе строка трека субпиксельно «дрожит».
        float textX = Math.round(artX + ART + 10f);
        float textMaxW = width * 0.22f;
        float titleH = Fonts.ROUNDBOLD.getHeight(TITLE_SIZE);
        float artistH = Fonts.MEDIUM.getHeight(ARTIST_SIZE);
        boolean hasArtist = artist != null && !artist.isBlank();
        float blockH = titleH + (hasArtist ? 2f + artistH : 0f);
        float textStartY = Math.round(artCenterY - blockH / 2f);

        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(TITLE_SIZE),
                clipTitleCached(title, textMaxW), textX, textStartY, LinkGuiThemeScope.text(255));
        if (hasArtist) {
            Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(ARTIST_SIZE),
                    clipArtistCached(artist, textMaxW), textX, textStartY + titleH + 2f, LinkGuiThemeScope.text(145));
        }

        long dur = Math.max(1L, media.getDurationMs());
        long pos = media.getPositionMs();
        float progress = smoothProgress(Math.min(1f, pos / (float) dur), media.isPlaying(), dur);
        drawProgressArea(context, progress, formatPosCached(pos), formatDurCached(dur), 1f);

        drawControlsArea(context, controls, mouseX, mouseY, true);
        drawVolumeArea(context, mouseX, mouseY, artCenterY);
    }

    private void drawControlsArea(DrawContext context, ControlsLayout controls, int mouseX, int mouseY, boolean interactive) {
        if (!interactive) {
            return;
        }

        // Смена play/pause — scale-pop по playPauseAnim: глиф сжимается, в точке минимума подменяется,
        // затем вырастает. Кроссфейд двоил «палочки» паузы (наложение двух глифов), pop наложения не даёт.
        float ppT = playPauseAnim.getValue();
        String[] icons = {"A", ppT >= 0.5f ? "D" : "C", "B"};
        for (int i = 0; i < 3; i++) {
            float btnX = controls.btnCenterX[i] - CONTROL_BTN / 2f;
            float btnY = controls.btnCenterY - CONTROL_BTN / 2f;
            boolean hover = MathUtils.isHovered(btnX, btnY, CONTROL_BTN, CONTROL_BTN, mouseX, mouseY);
            controlHover[i].update(hover);
            float hoverT = controlHover[i].getValue();
            if (hoverT > 0.04f) {
                LinkGuiChrome.drawPillButton(context.getMatrices(), btnX, btnY,
                        CONTROL_BTN, CONTROL_BTN, 6f, hoverT);
            }
            int baseAlpha = (int) (210 + 45 * hoverT);
            float pop = i == 1 ? 0.75f + 0.25f * Math.abs(2f * ppT - 1f) : 1f;
            drawMediaIcon(context, icons[i], controls.btnCenterX[i], controls.btnCenterY,
                    LinkGuiThemeScope.text(baseAlpha), pop);
        }
    }

    private void drawMediaIcon(DrawContext context, String glyph, float centerX, float centerY, Color color, float scale) {
        float size = CONTROL_ICON * scale;
        float planeLeft;
        float planeRight;
        float planeBottom;
        float planeTop;
        switch (glyph) {
            case "A", "B" -> {
                planeLeft = -0.078125f;
                planeRight = 1.078125f;
                planeBottom = -0.078125f;
                planeTop = 1.078125f;
            }
            case "C", "D" -> {
                planeLeft = 0.046875f;
                planeRight = 0.953125f;
                planeBottom = -0.078125f;
                planeTop = 1.078125f;
            }
            default -> {
                planeLeft = -0.0771484375f;
                planeRight = 1.0791015625f;
                planeBottom = 0.02392578125f;
                planeTop = 0.97705078125f;
            }
        }

        float planeCenterX = (planeLeft + planeRight) * 0.5f;
        float planeCenterY = (planeBottom + planeTop) * 0.5f;
        float drawX = centerX - (planeCenterX - planeLeft) * size;
        float drawY = centerY - (MEDIA_BASELINE - planeCenterY) * size;

        // Play triangle sits visually left in the glyph box.
        if ("C".equals(glyph)) {
            drawX += size * 0.12f;
        }

        Render2D.drawFont(context.getMatrices(), Fonts.MEDIA_PLAYER.getFont(size), glyph, drawX, drawY, color);
    }

    private void drawVolumeArea(DrawContext context, int mouseX, int mouseY, float artCenterY) {
        float volX = x + width - 92f;
        float volY = artCenterY - VOL_ICON / 2f - 1f;
        float barX = volX + 14f;
        float barY = volY + 5f;

        boolean hover = MathUtils.isHovered(barX - 6f, barY - 8f, VOL_BAR_W + 12f, 16f, mouseX, mouseY);
        volumeHover.update(hover || volumeDragging);

        if (volumeDragging) {
            applyVolume((mouseX - barX) / VOL_BAR_W);
        }

        // Иконка громкости — кнопка mute, по центру по вертикали со слайдером, слева от него.
        volumeIconAnim.update(volume >= 0.01f);
        float vt = volumeIconAnim.getValue();
        float barCenterY = barY + VOL_BAR_H / 2f;
        float iconSz = 12f;
        float iconCx = volX + iconSz / 2f;
        float iconCy = barCenterY;
        boolean iconHover = MathUtils.isHovered(iconCx - iconSz / 2f - 2f, iconCy - iconSz / 2f - 2f, iconSz + 4f, iconSz + 4f, mouseX, mouseY);
        muteHoverAnim.update(iconHover);
        Color iconBase = LinkGuiThemeScope.text(Math.round(215 + 30 * muteHoverAnim.getValue()));
        if (vt < 0.996f) {
            GuiIcons.draw(context.getMatrices(), GuiIcons.MUTE, iconCx, iconCy, iconSz, withIconAlpha(iconBase, 1f - vt));
        }
        if (vt > 0.004f) {
            GuiIcons.draw(context.getMatrices(), GuiIcons.SOUND, iconCx, iconCy, iconSz, withIconAlpha(iconBase, vt));
        }
        LinkGuiChrome.drawProgressBar(context.getMatrices(), barX, barY, VOL_BAR_W, VOL_BAR_H, smoothVolume(), volumeHover.getValue());
    }

    private void applyVolume(float t) {
        volume = MathHelper.clamp(t, 0f, 1f);
        SystemVolumeController.setVolume(volume);
        lastVolumeSetMs = System.currentTimeMillis();
    }

    /** Клик по значку: если звук есть — в 0 (запоминаем прежнее), если 0 — вернуть прежнее. */
    private void toggleMute() {
        if (volume > 0.001f) {
            preMuteVolume = volume;
            applyVolume(0f);
        } else {
            applyVolume(preMuteVolume > 0.01f ? preMuteVolume : 0.5f);
        }
    }

    /** Плавно подтягивает отображаемую громкость к целевой (при перетаскивании — быстро). */
    private float smoothVolume() {
        long now = System.currentTimeMillis();
        if (!volumeInitialized) {
            displayedVolume = volume;
            volumeInitialized = true;
            lastVolumeFrameMs = now;
            return displayedVolume;
        }
        float dt = Math.min(0.1f, Math.max(0f, (now - lastVolumeFrameMs) / 1000f));
        lastVolumeFrameMs = now;
        float rate = volumeDragging ? 32f : 13f;
        displayedVolume += (volume - displayedVolume) * (1f - (float) Math.exp(-rate * dt));
        if (Math.abs(volume - displayedVolume) < 0.001f) {
            displayedVolume = volume;
        }
        return displayedVolume;
    }

    private void drawProgressArea(DrawContext context, float progress, String curTime, String totalTime, float alphaMul) {
        float barX = x + 14f;
        float barY = y + height - 9f;
        float barW = width - 28f;
        // Время трека крупнее и жирнее (ROUNDBOLD 8.4 вместо MEDIUM 6.8).
        float timeSize = 8.4f;
        float timeY = barY - 13f;
        int timeAlpha = Math.round(170 * alphaMul);
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(timeSize), curTime, barX, timeY, LinkGuiThemeScope.text(timeAlpha));
        float totalW = Fonts.ROUNDBOLD.getWidth(totalTime, timeSize);
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(timeSize), totalTime,
                barX + barW - totalW, timeY, LinkGuiThemeScope.text(timeAlpha));
        LinkGuiChrome.drawDivider(context.getMatrices(), x + 12f, y + height - PROGRESS_ZONE, width - 24f);
        // Прогресс трека не интерактивен (нет перемотки) — рисуем в спокойном состоянии, а не в hover:
        // hover-вид у того же примитива зарезервирован за баром громкости, который реально тянется.
        LinkGuiChrome.drawProgressBar(context.getMatrices(), barX, barY, barW, 3.1f, progress, 0f);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) {
            return false;
        }
        if (button != 0) {
            return true;
        }

        float topH = height - PROGRESS_ZONE;
        float artCenterY = artY(topH) + ART / 2f;
        ControlsLayout controls = controlsLayout(topH);
        // Клик по значку громкости — mute / возврат прежней громкости.
        float volX = x + width - 92f;
        float volBarCenterY = (artCenterY - VOL_ICON / 2f - 1f) + 5f + VOL_BAR_H / 2f;
        if (MathUtils.isHovered(volX - 2f, volBarCenterY - 8f, 16f, 16f, (float) mouseX, (float) mouseY)) {
            toggleMute();
            return true;
        }

        float volBarX = volX + 14f;
        float volBarY = artCenterY - 1f;
        if (MathUtils.isHovered(volBarX - 6f, volBarY - 8f, VOL_BAR_W + 12f, 16f, (float) mouseX, (float) mouseY)) {
            volumeDragging = true;
            applyVolume(((float) mouseX - volBarX) / VOL_BAR_W);
            return true;
        }

        if (!media.hasMedia()) {
            return true;
        }

        for (int i = 0; i < 3; i++) {
            float btnX = controls.btnCenterX[i] - CONTROL_BTN / 2f;
            float btnY = controls.btnCenterY - CONTROL_BTN / 2f;
            if (!MathUtils.isHovered(btnX, btnY, CONTROL_BTN, CONTROL_BTN, (float) mouseX, (float) mouseY)) {
                continue;
            }
            if (i == 0) {
                media.previous();
            } else if (i == 1) {
                media.playPause();
            } else {
                media.next();
            }
            return true;
        }
        return true;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            volumeDragging = false;
        }
    }

    public void mouseDragged(double mouseX, double mouseY) {
        if (!volumeDragging) {
            return;
        }
        float volBarX = x + width - 92f + 14f;
        applyVolume(((float) mouseX - volBarX) / VOL_BAR_W);
    }

    private boolean isDynamicIslandUpdating() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getHudManager() == null) {
            return false;
        }
        DynamicIsland island = instance.getHudManager().getHudElement(DynamicIsland.class);
        return island != null && island.isToggled();
    }

    /**
     * Плавный прогресс: при проигрывании идём по РЕАЛЬНОМУ времени кадра (непрерывно, без рывков),
     * мягко подтягиваясь к настоящей позиции трека. Перемотка/смена трека — мгновенно.
     */
    private float smoothProgress(float target, boolean playing, long durationMs) {
        long now = System.currentTimeMillis();
        if (!progressInitialized) {
            displayedProgress = target;
            progressInitialized = true;
            lastProgressFrameMs = now;
            return displayedProgress;
        }
        float dt = Math.min(0.25f, Math.max(0f, (now - lastProgressFrameMs) / 1000f));
        lastProgressFrameMs = now;

        // Резкий скачок (перемотка, смена трека, большой рассинхрон) — мгновенно.
        if (Math.abs(target - displayedProgress) > 0.04f) {
            displayedProgress = target;
            return displayedProgress;
        }

        // Бар движется ПО ЧАСАМ кадра — идеально гладко, не завися от ступенчатого опроса позиции.
        if (playing && durationMs > 0L) {
            displayedProgress += dt * 1000f / durationMs;
        }
        // Очень мягкий ресинк к настоящей позиции (почти не тянется за «ступеньками» опроса).
        displayedProgress += (target - displayedProgress) * (1f - (float) Math.exp(-0.8f * dt));
        displayedProgress = Math.max(0f, Math.min(1f, displayedProgress));
        return displayedProgress;
    }

    private static Color withIconAlpha(Color color, float factor) {
        int a = Math.max(0, Math.min(255, Math.round(color.getAlpha() * factor)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private String clipTitleCached(String text, float max) {
        if (max == clipTitleMax && text.equals(clipTitleSrc)) return clipTitleResult;
        clipTitleSrc = text;
        clipTitleMax = max;
        clipTitleResult = clipBold(text, max, TITLE_SIZE);
        return clipTitleResult;
    }

    private String clipArtistCached(String text, float max) {
        if (max == clipArtistMax && text.equals(clipArtistSrc)) return clipArtistResult;
        clipArtistSrc = text;
        clipArtistMax = max;
        clipArtistResult = clip(text, max, ARTIST_SIZE);
        return clipArtistResult;
    }

    private String formatPosCached(long ms) {
        long sec = ms / 1000L;
        if (sec != cachedPosSec) {
            cachedPosSec = sec;
            cachedPosStr = formatTime(ms);
        }
        return cachedPosStr;
    }

    private String formatDurCached(long ms) {
        long sec = ms / 1000L;
        if (sec != cachedDurSec) {
            cachedDurSec = sec;
            cachedDurStr = formatTime(ms);
        }
        return cachedDurStr;
    }

    private static String clip(String text, float max, float size) {
        if (Fonts.MEDIUM.getWidth(text, size) <= max) return text;
        while (text.length() > 3 && Fonts.MEDIUM.getWidth(text + "...", size) > max) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private static String clipBold(String text, float max, float size) {
        if (Fonts.ROUNDBOLD.getWidth(text, size) <= max) return text;
        while (text.length() > 3 && Fonts.ROUNDBOLD.getWidth(text + "...", size) > max) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}
