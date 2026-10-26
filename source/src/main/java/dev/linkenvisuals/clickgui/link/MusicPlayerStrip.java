package dev.linkenvisuals.clickgui.link;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.media.DynamicIslandMedia;
import dev.linkenvisuals.media.DynamicIslandMediaHolder;
import dev.linkenvisuals.media.SystemVolumeController;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.ui.Spring;
import dev.linkenvisuals.ui.linkmenu.ClickGuiIcons;
import dev.linkenvisuals.ui.linkmenu.LinkenFonts;
import dev.linkenvisuals.ui.linkmenu.LinkenMenuChrome;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * 1:1 reproduction of the donor MusicPlayerStrip from LinkGuiScreen.
 * Positioned in the bottom 84 px of the center module panel.
 * Displays SMTC playback track, artist, album art / visualizer,
 * prev / play-pause / next transport controls, seekable progress bar,
 * and system volume slider with mute toggle.
 */
public final class MusicPlayerStrip {
    private static final Identifier ARTWORK_ID = LinkenVisuals.id("media_artwork_strip");
    private static final float ART_SIZE = 40.0f;
    private static final float PROGRESS_ZONE = 24.0f;
    private static final float VOL_BAR_W = 56.0f;
    private static final float VOL_BAR_H = 3.5f;
    private static final float BTN_SIZE = 26.0f;

    private float x;
    private float y;
    private float width;
    private float height = 80.0f;

    private float volume = 0.75f;
    private float displayedVolume = 0.75f;
    private float preMuteVolume = 0.5f;
    private boolean volumeDragging;
    private boolean timelineDragging;
    private long lastVolumePollMs;
    private long lastVolumeSetMs;

    private final Spring prevHover = new Spring(0.0, 900.0, 50.0);
    private final Spring playHover = new Spring(0.0, 900.0, 50.0);
    private final Spring nextHover = new Spring(0.0, 900.0, 50.0);
    private final Spring volumeHover = new Spring(0.0, 900.0, 50.0);
    private final Spring muteHover = new Spring(0.0, 900.0, 50.0);

    private float displayedProgress = 0.0f;

    public MusicPlayerStrip() {
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void init() {
        DynamicIslandMediaHolder.shared().ensureExecutor();
        SystemVolumeController.ensureInit();
        SystemVolumeController.refresh();
        Float sys = SystemVolumeController.currentVolume();
        if (sys != null) {
            volume = sys;
            displayedVolume = sys;
        }
    }

    public void shutdown() {
        volumeDragging = false;
        timelineDragging = false;
    }

    public void render(DrawContext context, float mx, float my, float openT, float dt, float scale) {
        if (openT <= 0.001f || width <= 10.0f || height <= 10.0f) {
            return;
        }

        DynamicIslandMedia media = DynamicIslandMediaHolder.shared();
        media.update();
        boolean hasMedia = media.hasMedia();
        boolean isPlaying = media.isPlaying();

        // System volume polling
        long now = System.currentTimeMillis();
        if (!volumeDragging && now - lastVolumePollMs > 300L && now - lastVolumeSetMs > 600L) {
            SystemVolumeController.refresh();
            Float sys = SystemVolumeController.currentVolume();
            if (sys != null) {
                volume = sys;
            }
            lastVolumePollMs = now;
        }

        // 1. Top separator line
        LinkenMenuChrome.roundedFill(context, x + 8.0f, y, width - 16.0f, 1.0f, 0.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(18.0f * openT)), scale);

        float topH = height - PROGRESS_ZONE;
        float artX = x + 14.0f;
        float artY = y + (topH - ART_SIZE) * 0.5f + 1.0f;
        float artCenterY = artY + ART_SIZE * 0.5f;

        // 2. Album artwork / placeholder thumbnail
        NativeImageBackedTexture artTex = media.getArtworkTexture();
        if (hasMedia && artTex != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, DynamicIslandMedia.ARTWORK_ID,
                Math.round(artX), Math.round(artY), 0.0f, 0.0f, Math.round(ART_SIZE), Math.round(ART_SIZE),
                Math.round(ART_SIZE), Math.round(ART_SIZE), LinkenMenuChrome.rgba(0xFFFFFF, openT));
            RoundedRectPipeline.drawBorder(context, artX, artY, ART_SIZE, ART_SIZE, 8.0f, 0.0f, 1.0f, 1.2f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(50.0f * openT)));
        } else {
            RoundedRectPipeline.draw(context, artX, artY, ART_SIZE, ART_SIZE, 8.0f,
                LinkenMenuChrome.rgba(0x131A16, Math.round(230.0f * openT)));
            RoundedRectPipeline.drawBorder(context, artX, artY, ART_SIZE, ART_SIZE, 8.0f, 0.0f, 1.0f, 1.2f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(50.0f * openT)));

            // Animated pulsing music wave lines in thumbnail when playing
            float pulse = isPlaying ? (float) Math.sin(System.currentTimeMillis() * 0.006) * 0.5f + 0.5f : 0.2f;
            float bar1 = 6.0f + pulse * 10.0f;
            float bar2 = 14.0f - pulse * 8.0f;
            float bar3 = 8.0f + pulse * 12.0f;
            int waveColor = LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round((140.0f + 100.0f * pulse) * openT));
            RoundedRectPipeline.draw(context, artX + 11.0f, artCenterY - bar1 * 0.5f, 3.0f, bar1, 1.5f, waveColor);
            RoundedRectPipeline.draw(context, artX + 18.0f, artCenterY - bar2 * 0.5f, 3.0f, bar2, 1.5f, waveColor);
            RoundedRectPipeline.draw(context, artX + 25.0f, artCenterY - bar3 * 0.5f, 3.0f, bar3, 1.5f, waveColor);
        }

        // 3. Track title & artist
        float textX = artX + ART_SIZE + 12.0f;
        float textMaxW = width * 0.32f;
        String title = hasMedia ? media.getTitle() : "Нет медиа";
        String artist = hasMedia ? media.getArtist() : "Запустите плеер на ПК";

        String clippedTitle = LinkenMenuChrome.ellipsize(LinkenFonts.bodyMedium(), title, textMaxW, 11.0f);
        String clippedArtist = LinkenMenuChrome.ellipsize(LinkenFonts.body(), artist, textMaxW, 9.5f);

        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), clippedTitle,
            textX, artCenterY - 10.0f, 11.0f, LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, openT));
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), clippedArtist,
            textX, artCenterY + 3.0f, 9.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, openT));

        // 4. Media transport controls in center
        float centerX = x + width * 0.5f;
        float btnY = artCenterY - BTN_SIZE * 0.5f;
        float prevX = centerX - 36.0f;
        float playX = centerX;
        float nextX = centerX + 36.0f;

        prevHover.step(in(prevX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, mx, my) ? 1.0 : 0.0, dt);
        playHover.step(in(playX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, mx, my) ? 1.0 : 0.0, dt);
        nextHover.step(in(nextX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, mx, my) ? 1.0 : 0.0, dt);

        float pt = (float) prevHover.get();
        float plt = (float) playHover.get();
        float nt = (float) nextHover.get();

        // Prev button |<<
        if (pt > 0.01f) {
            RoundedRectPipeline.draw(context, prevX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, 6.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(22.0f * pt * openT)));
        }
        ClickGuiIcons.backward(context, prevX, artCenterY, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3, LinkenMenuChrome.TEXT, pt), openT));

        // Play / Pause button
        if (plt > 0.01f) {
            RoundedRectPipeline.draw(context, playX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, 6.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(26.0f * plt * openT)));
        }
        if (isPlaying) {
            ClickGuiIcons.pause(context, playX, artCenterY, 15.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT, LinkenMenuChrome.ACCENT, plt), openT));
        } else {
            ClickGuiIcons.play(context, playX, artCenterY, 15.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT, LinkenMenuChrome.ACCENT, plt), openT));
        }

        // Next button >>|
        if (nt > 0.01f) {
            RoundedRectPipeline.draw(context, nextX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, 6.0f,
                LinkenMenuChrome.rgba(0xFFFFFF, Math.round(22.0f * nt * openT)));
        }
        ClickGuiIcons.forward(context, nextX, artCenterY, 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3, LinkenMenuChrome.TEXT, nt), openT));

        // 5. Volume area on right
        float volAreaW = 96.0f;
        float volX = x + width - 18.0f - volAreaW;
        float speakerX = volX + 8.0f;
        float barX = speakerX + 18.0f;
        float barY = artCenterY - VOL_BAR_H * 0.5f;

        boolean muteHov = in(speakerX - 6.0f, artCenterY - 8.0f, 16.0f, 16.0f, mx, my);
        muteHover.step(muteHov ? 1.0 : 0.0, dt);
        boolean volHov = in(barX - 4.0f, barY - 6.0f, VOL_BAR_W + 8.0f, 15.0f, mx, my);
        volumeHover.step((volHov || volumeDragging) ? 1.0 : 0.0, dt);

        if (volumeDragging) {
            applyVolume((mx - barX) / VOL_BAR_W);
        }
        displayedVolume += (volume - displayedVolume) * Math.min(1.0f, dt * 18.0f);

        // Speaker icon
        int iconColor = LinkenMenuChrome.rgba(
            LinkenMenuChrome.mix(LinkenMenuChrome.TEXT3, LinkenMenuChrome.TEXT, (float) muteHover.get()), openT);
        if (displayedVolume < 0.01f) {
            ClickGuiIcons.volumeMute(context, speakerX, artCenterY, 12.0f, iconColor);
        } else {
            ClickGuiIcons.volumeUp(context, speakerX, artCenterY, 12.0f, iconColor);
        }

        // Volume track
        float vt = (float) volumeHover.get();
        RoundedRectPipeline.draw(context, barX, barY, VOL_BAR_W, VOL_BAR_H, 1.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(22.0f * openT)));
        if (displayedVolume > 0.01f) {
            float fillW = VOL_BAR_W * displayedVolume;
            if (vt > 0.05f) {
                GlowPipeline.radial(context, barX + fillW * 0.5f, barY + VOL_BAR_H * 0.5f, fillW * 0.5f + 4.0f,
                    LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(30.0f * vt * openT)));
            }
            RoundedRectPipeline.draw(context, barX, barY, fillW, VOL_BAR_H, 1.5f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.TEXT2, LinkenMenuChrome.ACCENT, vt), openT));
        }

        // 6. Progress / Timeline area at bottom
        long posMs = media.getPositionMs();
        long lenMs = Math.max(1L, media.getDurationMs());
        float targetProg = MathHelper.clamp(posMs / (float) lenMs, 0.0f, 1.0f);
        if (timelineDragging) {
            float pbarX = x + 14.0f;
            float pbarW = width - 28.0f;
            targetProg = MathHelper.clamp((mx - pbarX) / pbarW, 0.0f, 1.0f);
        }
        displayedProgress += (targetProg - displayedProgress) * Math.min(1.0f, dt * 14.0f);

        String curTimeStr = formatTime(timelineDragging ? Math.round(displayedProgress * lenMs) : posMs);
        String totTimeStr = formatTime(lenMs);

        float timelineBarX = x + 14.0f;
        float timelineBarW = width - 28.0f;
        float timelineBarY = y + height - 8.0f;
        float timeLabelY = timelineBarY - 12.0f;

        LinkenMenuChrome.drawText(context, LinkenFonts.body(), curTimeStr,
            timelineBarX, timeLabelY, 8.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, openT));
        LinkenMenuChrome.drawRightText(context, LinkenFonts.body(), totTimeStr,
            timelineBarX + timelineBarW, timeLabelY, 8.5f, LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED, openT));

        // Background progress rail
        RoundedRectPipeline.draw(context, timelineBarX, timelineBarY, timelineBarW, 3.0f, 1.5f,
            LinkenMenuChrome.rgba(0xFFFFFF, Math.round(18.0f * openT)));

        // Filled progress rail
        if (displayedProgress > 0.001f) {
            float filledW = timelineBarW * displayedProgress;
            GlowPipeline.radial(context, timelineBarX + filledW * 0.5f, timelineBarY + 1.5f, filledW * 0.5f + 4.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, Math.round(25.0f * openT)));
            RoundedRectPipeline.draw(context, timelineBarX, timelineBarY, filledW, 3.0f, 1.5f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, openT));
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float mx = (float) mouseX;
        float my = (float) mouseY;
        if (!in(x, y, width, height, mx, my)) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        float topH = height - PROGRESS_ZONE;
        float artY = y + (topH - ART_SIZE) * 0.5f + 1.0f;
        float artCenterY = artY + ART_SIZE * 0.5f;

        // Transport buttons
        float centerX = x + width * 0.5f;
        float btnY = artCenterY - BTN_SIZE * 0.5f;
        float prevX = centerX - 36.0f;
        float playX = centerX;
        float nextX = centerX + 36.0f;

        DynamicIslandMedia media = DynamicIslandMediaHolder.shared();
        if (in(prevX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, mx, my)) {
            if (!media.previous()) {
                LinkGuiMediaKeys.previous();
            }
            return true;
        }
        if (in(playX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, mx, my)) {
            if (!media.playPause()) {
                LinkGuiMediaKeys.playPause();
            }
            return true;
        }
        if (in(nextX - BTN_SIZE * 0.5f, btnY, BTN_SIZE, BTN_SIZE, mx, my)) {
            if (!media.next()) {
                LinkGuiMediaKeys.next();
            }
            return true;
        }

        // Volume area
        float volAreaW = 96.0f;
        float volX = x + width - 18.0f - volAreaW;
        float speakerX = volX + 8.0f;
        float barX = speakerX + 18.0f;
        float barY = artCenterY - VOL_BAR_H * 0.5f;

        if (in(speakerX - 8.0f, artCenterY - 8.0f, 16.0f, 16.0f, mx, my)) {
            toggleMute();
            return true;
        }

        if (in(barX - 4.0f, barY - 8.0f, VOL_BAR_W + 8.0f, 18.0f, mx, my)) {
            volumeDragging = true;
            applyVolume((mx - barX) / VOL_BAR_W);
            return true;
        }

        // Timeline seek bar
        float timelineBarX = x + 14.0f;
        float timelineBarW = width - 28.0f;
        float timelineBarY = y + height - 12.0f;
        if (in(timelineBarX - 4.0f, timelineBarY - 4.0f, timelineBarW + 8.0f, 12.0f, mx, my)) {
            timelineDragging = true;
            displayedProgress = MathHelper.clamp((mx - timelineBarX) / timelineBarW, 0.0f, 1.0f);
            return true;
        }

        return true;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            volumeDragging = false;
            timelineDragging = false;
        }
    }

    public void mouseDragged(double mouseX, double mouseY) {
        if (volumeDragging) {
            float barX = (x + width - 18.0f - 96.0f) + 26.0f;
            applyVolume(((float) mouseX - barX) / VOL_BAR_W);
        }
        if (timelineDragging) {
            float timelineBarX = x + 14.0f;
            float timelineBarW = width - 28.0f;
            displayedProgress = MathHelper.clamp(((float) mouseX - timelineBarX) / timelineBarW, 0.0f, 1.0f);
        }
    }

    private void applyVolume(float raw) {
        volume = MathHelper.clamp(raw, 0.0f, 1.0f);
        SystemVolumeController.setVolume(volume);
        lastVolumeSetMs = System.currentTimeMillis();
    }

    private void toggleMute() {
        if (volume > 0.01f) {
            preMuteVolume = volume;
            applyVolume(0.0f);
        } else {
            applyVolume(preMuteVolume > 0.01f ? preMuteVolume : 0.5f);
        }
    }

    private static boolean in(float rx, float ry, float rw, float rh, float px, float py) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    private static String formatTime(long ms) {
        if (ms <= 0L) return "0:00";
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" + sec : String.valueOf(sec));
    }
}
