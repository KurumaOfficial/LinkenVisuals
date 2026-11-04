package dev.linkenvisuals.audio;

import dev.linkenvisuals.media.MediaWatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;

/**
 * Donor-style main-menu track. The donor ships a 131.3 s streamed OGG and
 * starts it after the welcome animation; the same state feeds the title media
 * card so the card never has to invent metadata.
 */
public final class LinkenMenuMusic {
    public static final String TITLE = "EVILSHIT";
    public static final String ARTIST = "ARLEKIN 40 000";
    public static final long DURATION_MS = 131_300L;
    public static final float VOLUME = 0.4f;

    private static SoundInstance instance;
    private static boolean playing;
    private static boolean paused;
    private static boolean startedOnce;
    private static long startedAt;
    private static long pausedAt;

    private LinkenMenuMusic() {
    }

    public static void autoStartOnce() {
        if (startedOnce) {
            return;
        }
        startedOnce = true;
        start();
    }

    public static void start() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) {
            return;
        }
        stopInternal(false);
        // The custom title track replaces vanilla menu music; world music keeps
        // its normal tracker behaviour once the menu closes.
        client.getMusicTracker().stop();
        instance = PositionedSoundInstance.ui(LinkenSounds.MAINMENU_TRACK, 1.0f, VOLUME);
        client.getSoundManager().play(instance);
        playing = true;
        paused = false;
        startedAt = System.currentTimeMillis();
    }

    public static void pause() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !playing || paused) {
            return;
        }
        client.getSoundManager().pauseAllExcept();
        paused = true;
        pausedAt = System.currentTimeMillis();
    }

    public static void resume() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !paused) {
            return;
        }
        client.getSoundManager().resumeAll();
        startedAt += System.currentTimeMillis() - pausedAt;
        paused = false;
    }

    public static void toggle() {
        if (playing && !paused) {
            pause();
        } else if (paused) {
            resume();
        } else {
            start();
        }
    }

    public static void stop() {
        stopInternal(true);
    }

    private static void stopInternal(boolean resetAutoStart) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSoundManager() != null) {
            if (paused) {
                client.getSoundManager().resumeAll();
            }
            if (instance != null) {
                client.getSoundManager().stop(instance);
            }
        }
        instance = null;
        playing = false;
        paused = false;
        if (resetAutoStart) {
            startedOnce = false;
        }
    }

    /** Keeps the menu track across menu screens and cuts it once a world loads. */
    public static void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }
        if (client.world != null) {
            if (playing) {
                stop();
            }
            return;
        }
        if (playing && !paused && positionMs() >= DURATION_MS) {
            stopInternal(false);
        }
    }

    public static boolean isPlaying() {
        return playing && !paused;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static long positionMs() {
        if (!playing) {
            return 0L;
        }
        long now = paused ? pausedAt : System.currentTimeMillis();
        return Math.max(0L, Math.min(DURATION_MS, now - startedAt));
    }

    public static MediaWatcher.Track track() {
        return new MediaWatcher.Track(TITLE, ARTIST, "VisualsModification",
            isPlaying(), positionMs(), DURATION_MS);
    }
}
