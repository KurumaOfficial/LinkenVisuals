package ru.method.linkenvisuals.screen.settings;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Обобщённая опция для шаблона под-окна настроек (9c): один из трёх типов контрола (селект/слайдер/тоггл),
 * привязанный лямбдами к реальному источнику (обычно {@code SimpleOption} из {@code GameOptions}).
 * Селект открывает выпадающий список вариантов ({@link #options}) и задаёт выбранный по индексу
 * ({@link #pick}); если {@code options == null} — селект только отображает значение (не меняется).
 */
public final class SettingsOption {

    public enum Kind { SELECT, SLIDER, TOGGLE, ACTION }

    public final String label;
    public final Kind kind;
    public final Supplier<String> value;         // текст значения (SELECT/SLIDER)
    public final Supplier<List<String>> options; // варианты для дропдауна (SELECT); null = display-only
    public final IntConsumer pick;               // выбрать вариант по индексу (SELECT)
    public final DoubleSupplier frac;            // 0..1 (SLIDER)
    public final DoubleConsumer setFrac;         // задать из 0..1 (SLIDER)
    public final BooleanSupplier state;          // (TOGGLE)
    public final Runnable toggle;                // (TOGGLE)

    private SettingsOption(String label, Kind kind, Supplier<String> value, Supplier<List<String>> options,
                           IntConsumer pick, DoubleSupplier frac, DoubleConsumer setFrac,
                           BooleanSupplier state, Runnable toggle) {
        this.label = label;
        this.kind = kind;
        this.value = value;
        this.options = options;
        this.pick = pick;
        this.frac = frac;
        this.setFrac = setFrac;
        this.state = state;
        this.toggle = toggle;
    }

    /** Селект с выпадающим списком вариантов. */
    public static SettingsOption select(String label, Supplier<String> value, Supplier<List<String>> options, IntConsumer pick) {
        return new SettingsOption(label, Kind.SELECT, value, options, pick, null, null, null, null);
    }

    /** Селект только для отображения (без выбора) — напр. текущее разрешение окна. */
    public static SettingsOption selectDisplay(String label, Supplier<String> value) {
        return new SettingsOption(label, Kind.SELECT, value, null, null, null, null, null, null);
    }

    public static SettingsOption slider(String label, Supplier<String> value, DoubleSupplier frac, DoubleConsumer setFrac) {
        return new SettingsOption(label, Kind.SLIDER, value, null, null, frac, setFrac, null, null);
    }

    public static SettingsOption toggle(String label, BooleanSupplier state, Runnable toggle) {
        return new SettingsOption(label, Kind.TOGGLE, null, null, null, null, null, state, toggle);
    }

    /**
     * Строка-переход: подпись значения справа и шеврон, клик открывает другой экран. Нужна там, где
     * параметр не влезает в три контрола — например список клавиш внутри «Управления».
     * Действие кладём в поле {@code toggle}: это тот же «клик по строке», отдельное поле не нужно.
     */
    public static SettingsOption action(String label, Supplier<String> value, Runnable run) {
        return new SettingsOption(label, Kind.ACTION, value, null, null, null, null, null, run);
    }
}
