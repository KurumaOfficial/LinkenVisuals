package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.MinecraftClient;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;

public final class ClickGuiRenderContext {

    // Не ThreadLocal: begin/end и весь рендер живут на render-потоке, а ThreadLocal.get() дёргался
    // на каждый примитив Render2D (resolveGuiFade). volatile — страховка при чтении с другого потока.
    private static volatile boolean active;
    private static volatile boolean closing;
    private static volatile float fade = 1f;

    private ClickGuiRenderContext() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isClosing() {
        return closing;
    }

    public static float getFadeMultiplier() {
        return fade;
    }

    public static boolean isClickGuiOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof ClickGui;
    }

    public static void begin(float fadeMultiplier, boolean isClosing) {
        active = true;
        closing = isClosing;
        fade = Math.max(0f, Math.min(1f, fadeMultiplier));
    }

    public static void begin(float fadeMultiplier) {
        begin(fadeMultiplier, false);
    }

    public static void begin() {
        begin(1f, false);
    }

    public static void end() {
        active = false;
        closing = false;
        fade = 1f;
    }
}
