package ru.method.linkenvisuals.modules.impl.render;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;

/**
 * Drochka — накладывает ВСЕМ игрокам позу «рука у паха» с быстрым дёрганьем кистью.
 * Сама поза применяется в {@link ru.method.linkenvisuals.api.mixins.LivingEntityRendererMixin}
 * (по {@link #active()}), вне гейта «только свой игрок» — поэтому затрагивает КАЖДОГО игрока.
 */
public class Drochka extends Module {
    private static Drochka INSTANCE;

    private final NumberSetting speedSet = new NumberSetting("modules.settings.drochka.speed", 0.9f, 0.3f, 4.0f, 0.1f);
    private final NumberSetting ampSet = new NumberSetting("modules.settings.drochka.amplitude", 0.4f, 0.1f, 0.9f, 0.05f);
    private final BooleanSetting bothSet = new BooleanSetting("modules.settings.drochka.both_hands", false);
    private final BooleanSetting breastSet = new BooleanSetting("modules.settings.drochka.breast", false);

    public Drochka() {
        super("Drochka", Category.Visuals);
        INSTANCE = this;
    }

    /** Включён ли модуль (для миксина, применяющего позу каждому игроку). */
    public static boolean active() {
        Drochka i = INSTANCE;
        return i != null && i.isToggle();
    }

    /** Частота дёрганья (рад/тик). */
    public static float frequency() {
        Drochka i = INSTANCE;
        return i != null ? i.speedSet.getValue() : 0.9f;
    }

    /** Амплитуда дёрганья кистью (рад). */
    public static float amplitude() {
        Drochka i = INSTANCE;
        return i != null ? i.ampSet.getValue() : 0.4f;
    }

    /** Применять к обеим рукам. */
    public static boolean bothHands() {
        Drochka i = INSTANCE;
        return i != null && i.bothSet.getValue();
    }

    /** Рисовать объёмную грудь на модели игрока (для миксина рендера). */
    public static boolean breast() {
        Drochka i = INSTANCE;
        return i != null && i.isToggle() && i.breastSet.getValue();
    }
}
