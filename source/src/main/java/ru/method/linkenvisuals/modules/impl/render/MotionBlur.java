package ru.method.linkenvisuals.modules.impl.render;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.render.MotionBlurRenderer;

/**
 * Мотион-блюр кадровым блендом. Настройка «Сила» — доля предыдущего кадра (persistence): чем выше,
 * тем длиннее смаз при движении/повороте. Смазывается только мир (эффект гоняется в конце
 * renderWorld), HUD/GUI остаются чёткими.
 */
public class MotionBlur extends Module {

    // 5..90 % → 0.05..0.90 бленд-фактор. Дефолт 50% — умеренный смаз.
    private final NumberSetting strength = new NumberSetting("settings.motionblur.strength", 50.0f, 5.0f, 90.0f, 5.0f);

    public MotionBlur() {
        super("MotionBlur", Category.Visuals);
    }

    @Override
    public void onDisable() {
        MotionBlurRenderer.release();
        super.onDisable();
    }

    public float getStrengthValue() {
        return strength.getValue() / 100.0f;
    }

    /** Зовётся из GameRendererMixin в КОНЦЕ renderWorld (после мира, до HUD). */
    public void renderCurrent() {
        MotionBlurRenderer.render(strength.getValue() / 100.0f);
    }
}
