package ru.method.linkenvisuals.utils.render;

public final class LinkGuiRenderContext {

    // Не ThreadLocal: begin/end и рендер живут на render-потоке, а get() дёргался на каждый
    // примитив Render2D (resolveGuiFade). volatile — страховка при чтении с другого потока.
    private static volatile boolean active;
    // Глобальный множитель прозрачности контента Link GUI (для появления/закрытия).
    private static volatile float fade = 1f;

    private LinkGuiRenderContext() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    public static float getFadeMultiplier() {
        return fade;
    }

    public static void setFade(float fade) {
        // Квантование до 1/64: fade входит (через альфу цветов) в ключи LRU-кэшей SDF-примитивов.
        // Непрерывное значение из анимаций давало новый ключ каждому примитиву каждый кадр — постоянные
        // промахи кэшей и пересборки мешей. Шаг 1/64 визуально неразличим.
        float clamped = Math.max(0f, Math.min(1f, fade));
        LinkGuiRenderContext.fade = Math.round(clamped * 64f) / 64f;
    }

    public static void resetFade() {
        fade = 1f;
    }
}
