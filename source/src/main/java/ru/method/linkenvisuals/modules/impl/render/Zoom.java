package ru.method.linkenvisuals.modules.impl.render;


import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.math.MathUtils;

public class Zoom extends Module {

    private static final float ZOOM_MULTIPLIER_MIN = 0.05f;
    private static final float ZOOM_MULTIPLIER_MAX = 1.0f;
    private static final float SMOOTHNESS_MIN = 0.01f;
    private static final float SMOOTHNESS_MAX = 1.0f;

    private final BindSetting zoomBind = new BindSetting("settings.zoom.bind", new Bind(GLFW.GLFW_KEY_C, false));
    private final NumberSetting zoomMultiplier = new NumberSetting("settings.zoom.multiplier", 0.80f, ZOOM_MULTIPLIER_MIN, ZOOM_MULTIPLIER_MAX, 0.01f);
    private final NumberSetting smoothSpeed = new NumberSetting("settings.zoom.smooth_speed", 0.89f, SMOOTHNESS_MIN, SMOOTHNESS_MAX, 0.01f);

    private float zoomProgressPrev = 0.0f;
    private float zoomProgress = 0.0f;

    public Zoom() {
        super("Zoom", Category.Visuals);
        getSettings().add(zoomBind);
        getSettings().add(zoomMultiplier);
        getSettings().add(smoothSpeed);
    }

    @Override
    public void onDisable() {
        zoomProgressPrev = 0.0f;
        zoomProgress = 0.0f;
        super.onDisable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) {
            zoomProgressPrev = 0.0f;
            zoomProgress = 0.0f;
            return;
        }

        float target = isZoomKeyDown() ? 1.0f : 0.0f;
        zoomProgressPrev = zoomProgress;
        float smoothness = MathUtils.clamp(smoothSpeed.getValue(), SMOOTHNESS_MIN, SMOOTHNESS_MAX);
        float smoothT = (smoothness - SMOOTHNESS_MIN) / (SMOOTHNESS_MAX - SMOOTHNESS_MIN);
        float step = MathUtils.lerp(SMOOTHNESS_MAX, SMOOTHNESS_MIN, smoothT);
        zoomProgress = MathUtils.getStep(zoomProgress, target, step);
    }

    public float getFovMultiplier(float tickDelta) {
        if (!isToggled() || fullNullCheck()) {
            return 1.0f;
        }

        float clampedTickDelta = MathUtils.clamp(tickDelta, 0.0f, 1.0f);
        float progress = MathUtils.lerp(zoomProgressPrev, zoomProgress, clampedTickDelta);
        float clampedProgress = MathUtils.clamp(progress, 0.0f, 1.0f);

        float zoomSetting = MathUtils.clamp(zoomMultiplier.getValue(), ZOOM_MULTIPLIER_MIN, ZOOM_MULTIPLIER_MAX);
        float zoomT = (zoomSetting - ZOOM_MULTIPLIER_MIN) / (ZOOM_MULTIPLIER_MAX - ZOOM_MULTIPLIER_MIN);
        float targetMultiplier = MathUtils.lerp(ZOOM_MULTIPLIER_MAX, ZOOM_MULTIPLIER_MIN, zoomT);

        return MathUtils.lerp(1.0f, targetMultiplier, clampedProgress);
    }

    private boolean isZoomKeyDown() {
        if (mc.currentScreen != null) {
            return false;
        }

        Bind bind = zoomBind.getValue();
        if (bind == null || bind.isEmpty()) {
            return false;
        }

        long handle = mc.getWindow().getHandle();
        if (bind.isMouse()) {
            return GLFW.glfwGetMouseButton(handle, bind.getKey()) == GLFW.GLFW_PRESS;
        }

        return GLFW.glfwGetKey(handle, bind.getKey()) == GLFW.GLFW_PRESS;
    }
}
