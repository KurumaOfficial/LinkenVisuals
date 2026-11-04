package ru.method.linkenvisuals.utils.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** One-shot cursive welcome shown before the main menu controls. */
public final class MainMenuGreeting {
    private static boolean played;
    private static boolean reloadInProgress;
    private static boolean pending;
    private static boolean playing;
    private static long startedAtNs;

    private MainMenuGreeting() {
    }

    public static void onReloadBegin() {
        reloadInProgress = !played;
        pending = false;
        playing = false;
        startedAtNs = 0L;
        CursiveWelcome.dispose();
    }

    public static void onReloadComplete() {
        if (!reloadInProgress) return;
        reloadInProgress = false;
        if (!played) {
            pending = true;
        }
    }

    public static boolean isLottieActive() {
        return pending || playing;
    }

    public static void requestPlayback() {
        if (!played && !pending && !playing) {
            pending = true;
        }
    }

    public static void renderLottie(DrawContext context, int screenWidth, int screenHeight) {
        if (played || context == null) return;
        if (!pending && !playing) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        // Do not start under the splash/loading overlay: the full 8.2 s intro should be visible.
        if (!playing && client.getOverlay() != null) return;

        if (pending) {
            pending = false;
            playing = true;
            startedAtNs = System.nanoTime();
        }
        if (!playing) return;

        float elapsed = (System.nanoTime() - startedAtNs) / 1_000_000_000f;
        float progress = MathHelper.clamp(elapsed / CursiveWelcome.DURATION, 0f, 1f);
        if (progress >= 1f) {
            finishGreeting();
            return;
        }
        CursiveWelcome.render(context, screenWidth, screenHeight, progress);
    }

    private static void finishGreeting() {
        playing = false;
        pending = false;
        CursiveWelcome.dispose();
        played = true;
    }
}
