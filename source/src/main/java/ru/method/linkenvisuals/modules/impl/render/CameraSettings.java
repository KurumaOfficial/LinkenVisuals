package ru.method.linkenvisuals.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;

/**
 * Плавное переключение вида F5 (перенос smooth-F5 из Kimiko CameraSettings). При переходе в 3-е лицо
 * дистанция камеры плавно выезжает от близкой к полной за ~0.5с. Масштаб применяет CameraMixin к
 * аргументу Camera.clipToSpace(F). Зум камеры не портирован — для этого есть отдельный модуль Zoom.
 */
public class CameraSettings extends Module {

    private static final long F5_MS = 500L;

    private final BooleanSetting smoothF5 = new BooleanSetting("settings.camerasettings.smooth_f5", true);

    private Perspective lastPerspective = Perspective.FIRST_PERSON;
    private long animStart = -1L;

    public CameraSettings() {
        super("CameraSettings", Category.Visuals);
        getSettings().add(smoothF5);
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.options == null) return;
        Perspective p = mc.options.getPerspective();
        if (p != lastPerspective) {
            if (smoothF5.getValue() && lastPerspective == Perspective.FIRST_PERSON && p != Perspective.FIRST_PERSON) {
                animStart = System.currentTimeMillis();
            } else if (p == Perspective.FIRST_PERSON) {
                animStart = -1L;
            }
            lastPerspective = p;
        }
    }

    /** 1.0 = без эффекта. Пока идёт анимация выезда в 3-е лицо — доля дистанции 0.1→1.0 (cubic ease-out). */
    public float getCameraDistanceScale() {
        if (!isToggle() || !smoothF5.getValue()) return 1.0f;
        if (mc.options == null) return 1.0f;
        Perspective p = mc.options.getPerspective();
        if (p != lastPerspective) {
            if (lastPerspective == Perspective.FIRST_PERSON && p != Perspective.FIRST_PERSON) {
                animStart = System.currentTimeMillis();
            } else if (p == Perspective.FIRST_PERSON) {
                animStart = -1L;
            }
            lastPerspective = p;
        }
        if (animStart < 0L || p == Perspective.FIRST_PERSON) return 1.0f;
        float elapsed = (float) (System.currentTimeMillis() - animStart);
        if (elapsed >= F5_MS) {
            animStart = -1L;
            return 1.0f;
        }
        float t = Math.min(1.0f, elapsed / (float) F5_MS);
        float eased = 1.0f - (float) Math.pow(1.0f - t, 3);
        return 0.1f + 0.9f * eased;
    }
}
