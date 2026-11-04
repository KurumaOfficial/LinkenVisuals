package ru.method.linkenvisuals.utils.math;

import ru.method.linkenvisuals.utils.Wrapper;
import lombok.Getter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Counter implements Wrapper {

    @Getter private int currentFPS;

    public void updateFPS() {
        // Без сглаживания значения: и EMA 0.1, и адаптивный догон казались «тормозящими» при резких
        // скачках (300→700). mc.getCurrentFps() сам по себе устойчив (ваниль усредняет по окну),
        // а визуальную плавность даёт одометр цифр в вотермарке — числу сглаживание не нужно.
        currentFPS = mc.getCurrentFps();
    }
}
