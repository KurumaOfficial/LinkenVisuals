package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.gl.Framebuffer;

/**
 * 1.21.11 port shim. The donor captured the main framebuffer into downsampled FBOs and
 * re-used it for the liquid-glass reflection/backdrop. In 1.21.11 framebuffer capture and
 * raw blits are not exposed to mods; glass panels now use the engine blur post-effect via
 * {@link Render2D#drawBlurredRect}. The API is kept so the ClickGUI/menu call sites compile.
 */
public final class LiquidGlassReflectionCache {

    public enum ReflectionSource {
        MAIN_MENU,
        GAMEPLAY,
        CLICK_GUI
    }

    private LiquidGlassReflectionCache() {
    }

    public static void captureMainMenu() {
    }

    public static void captureMainMenu(boolean force) {
    }

    public static void captureGameplay() {
    }

    public static void captureGameplay(boolean force) {
    }

    public static void ensureGameplayCapturedForFrame() {
    }

    public static void captureGameplayForHudFrame() {
    }

    public static void markGameplayReflectionUsed() {
    }

    public static void ensureMainMenuCapturedForFrame() {
    }

    public static void captureIfRequested(ReflectionSource source) {
    }

    public static void captureClickGuiBackdrop() {
    }

    public static void captureClickGuiBackdrop(boolean force) {
    }

    public static void invalidate(ReflectionSource source) {
    }

    public static Framebuffer getFramebuffer(ReflectionSource source, Framebuffer fallback) {
        return fallback;
    }

    public static void requestCapture(ReflectionSource source) {
    }

    public static boolean needsCapture(ReflectionSource source) {
        return false;
    }

    public static boolean isCapturedThisFrame(ReflectionSource source) {
        return false;
    }

    public static void clear() {
    }

    public static Framebuffer getMainMenuFramebuffer(Framebuffer fallback) {
        return fallback;
    }

    public static Framebuffer getGameplayFramebuffer(Framebuffer fallback) {
        return fallback;
    }

    public static void blitColor(Framebuffer source, Framebuffer target) {
        // No raw blits in 1.21.11; callers use engine blur instead.
    }
}
