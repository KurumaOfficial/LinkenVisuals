package dev.linkenvisuals.ui.linkmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Arm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * 1:1 Linken settings catalog ported from donor SettingsCatalog.java.
 * Full Russian translations, correct values, and SimpleOption bindings.
 */
public final class LinkenSettingsCatalog {
    private LinkenSettingsCatalog() {
    }

    public static List<LinkenSettingsOption> graphics() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<LinkenSettingsOption> out = new ArrayList<>();
        out.add(cycle("Графика", o.getPreset(), GraphicsMode.values(), m -> switch (m) {
            case FAST -> "Быстро";
            case FANCY -> "Детально";
            case FABULOUS -> "Улучшено";
            case CUSTOM -> "Своя";
        }));
        out.add(intSlider("Прорисовка", o.getViewDistance(), 2, 32, v -> v + " чанков"));
        out.add(intSlider("Симуляция", o.getSimulationDistance(), 5, 32, v -> v + " чанков"));
        out.add(cycle("Сборка чанков", o.getChunkBuilderMode(), ChunkBuilderMode.values(), m -> switch (m) {
            case NONE -> "Нет";
            case PLAYER_AFFECTED -> "Потоковая";
            case NEARBY -> "Ближние";
        }));
        out.add(intSlider("Частота кадров", o.getMaxFps(), 10, 260, v -> v >= 260 ? "Максимум" : v + " к/с"));
        out.add(toggle("Мягкое освещение", o.getAo()));
        out.add(toggle("V-Sync", o.getEnableVsync()));
        out.add(intSlider("Интерфейс", o.getGuiScale(), 0, 4, v -> v == 0 ? "Авто" : String.valueOf(v)));
        out.add(cycle("Индикатор атаки", o.getAttackIndicator(), AttackIndicator.values(), a -> switch (a) {
            case OFF -> "Выкл";
            case CROSSHAIR -> "Прицел";
            case HOTBAR -> "Хотбар";
        }));
        out.add(doubleSlider("Яркость", o.getGamma(), 0.0, 1.0,
            v -> v <= 0.01 ? "Мрачно" : v >= 0.99 ? "Ярко" : Math.round(v * 100) + "%"));
        out.add(cycle("Облака", o.getCloudRenderMode(), CloudRenderMode.values(), c -> switch (c) {
            case OFF -> "Выкл";
            case FAST -> "Быстрые";
            case FANCY -> "Объёмные";
        }));
        out.add(toggle("Полноэкранный режим", o.getFullscreen()));
        out.add(cycle("Частицы", o.getParticles(), ParticlesMode.values(), p -> switch (p.name()) {
            case "ALL" -> "Все";
            case "DECREASED" -> "Меньше";
            default -> "Минимум";
        }));
        out.add(toggle("Тени сущностей", o.getEntityShadows()));
        out.add(doubleSlider("До сущности", o.getEntityDistanceScaling(), 0.5, 5.0,
            v -> Math.round(v * 100) + "%"));
        out.add(doubleSlider("Мерцание чар", o.getGlintSpeed(), 0.0, 1.0,
            v -> Math.round(v * 100) + "%"));
        out.add(doubleSlider("Заметность чар", o.getGlintStrength(), 0.0, 1.0,
            v -> Math.round(v * 100) + "%"));
        out.add(intSlider("Размытие фона", o.getMenuBackgroundBlurriness(), 0, 10, String::valueOf));
        out.add(toggle("Покачивание камеры", o.getBobView()));
        return out;
    }

    public static List<LinkenSettingsOption> sound() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<LinkenSettingsOption> out = new ArrayList<>();
        for (SoundCategory category : SoundCategory.values()) {
            String label = soundName(category);
            out.add(doubleSlider(label, o.getSoundVolumeOption(category), 0.0, 1.0,
                v -> v <= 0.005 ? "Выкл" : Math.round(v * 100) + "%"));
        }
        out.add(toggle("Направленный звук", o.getDirectionalAudio()));
        out.add(toggle("Субтитры", o.getShowSubtitles()));
        out.add(LinkenSettingsOption.display("Устройство вывода", () -> {
            String device = o.getSoundDevice().getValue();
            return device == null || device.isBlank() ? "Системное" : device;
        }));
        return out;
    }

    public static List<LinkenSettingsOption> chat() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<LinkenSettingsOption> out = new ArrayList<>();
        out.add(cycle("Чат", o.getChatVisibility(), ChatVisibility.values(), v -> switch (v) {
            case FULL -> "Показать";
            case SYSTEM -> "Только команды";
            case HIDDEN -> "Скрыть";
        }));
        out.add(toggle("Цвета", o.getChatColors()));
        out.add(toggle("Ссылки", o.getChatLinks()));
        out.add(toggle("Подтверждение ссылок", o.getChatLinksPrompt()));
        out.add(toggle("Подсказка команд", o.getAutoSuggestions()));
        out.add(toggle("Скрывать совпадающие ники", o.getHideMatchedNames()));
        out.add(doubleSlider("Прозрачность", o.getChatOpacity(), 0.0, 1.0,
            v -> Math.round(v * 90 + 10) + "%"));
        out.add(doubleSlider("Прозрачность фона", o.getTextBackgroundOpacity(), 0.0, 1.0,
            v -> Math.round(v * 100) + "%"));
        out.add(doubleSlider("Масштаб", o.getChatScale(), 0.0, 1.0,
            v -> v <= 0.01 ? "Выкл" : Math.round(v * 100) + "%"));
        out.add(doubleSlider("Ширина", o.getChatWidth(), 0.0, 1.0,
            v -> Math.round(40 + v * 280) + " px"));
        out.add(doubleSlider("Высота (в фокусе)", o.getChatHeightFocused(), 0.0, 1.0,
            v -> Math.round(20 + v * 160) + " px"));
        out.add(doubleSlider("Высота (без фокуса)", o.getChatHeightUnfocused(), 0.0, 1.0,
            v -> Math.round(20 + v * 160) + " px"));
        out.add(doubleSlider("Межстрочный интервал", o.getChatLineSpacing(), 0.0, 1.0,
            v -> Math.round(v * 100) + "%"));
        out.add(doubleSlider("Задержка чата", o.getChatDelay(), 0.0, 6.0,
            v -> v <= 0.01 ? "Нет" : String.format(Locale.ROOT, "%.1f с", v)));
        out.add(cycle("Диктор", o.getNarrator(), NarratorMode.values(), m -> switch (m) {
            case OFF -> "Выкл";
            case ALL -> "Всё";
            case CHAT -> "Чат";
            case SYSTEM -> "Система";
        }));
        return out;
    }

    public static List<LinkenSettingsOption> controls() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<LinkenSettingsOption> out = new ArrayList<>();
        out.add(LinkenSettingsOption.action("Клавиши управления", () -> "Настроить", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen != null) {
                mc.setScreen(new LinkenKeybindsScreen(mc.currentScreen));
            }
        }));
        out.add(doubleSlider("Чувствительность мыши", o.getMouseSensitivity(), 0.0, 1.0,
            v -> Math.round(v * 200) + "%"));
        out.add(toggle("Инвертировать мышь", o.getInvertMouseY()));
        out.add(toggle("Автопрыжок", o.getAutoJump()));
        out.add(toggle("Красться переключением", o.getSneakToggled()));
        out.add(toggle("Бежать переключением", o.getSprintToggled()));
        out.add(toggle("Прямой ввод мыши", o.getRawMouseInput()));
        out.add(toggle("Дискретная прокрутка", o.getDiscreteMouseScroll()));
        out.add(toggle("Сенсорный экран", o.getTouchscreen()));
        out.add(toggle("Вкладка предметов оператора", o.getOperatorItemsTab()));
        out.add(doubleSlider("Прокрутка", o.getMouseWheelSensitivity(), 0.01, 10.0,
            v -> String.format(Locale.ROOT, "%.2f", v)));
        out.add(cycle("Основная рука", o.getMainArm(), Arm.values(),
            a -> a == Arm.LEFT ? "Левая" : "Правая"));
        return out;
    }

    public static List<LinkenSettingsOption> accessibility() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<LinkenSettingsOption> out = new ArrayList<>();
        out.add(cycle("Диктор", o.getNarrator(), NarratorMode.values(), m -> switch (m) {
            case OFF -> "Выкл";
            case ALL -> "Всё";
            case CHAT -> "Чат";
            case SYSTEM -> "Система";
        }));
        out.add(toggle("Субтитры", o.getShowSubtitles()));
        out.add(doubleSlider("Прозрачность фона", o.getTextBackgroundOpacity(), 0.0, 1.0,
            v -> Math.round(v * 100) + "%"));
        out.add(toggle("Фон текста везде", () -> !o.getBackgroundForChatOnly().getValue(),
            () -> o.getBackgroundForChatOnly().setValue(!o.getBackgroundForChatOnly().getValue())));
        out.add(doubleSlider("Тряска от урона", o.getDamageTiltStrength(), 0.0, 1.0,
            v -> v <= 0.001 ? "Выкл" : Math.round(v * 100) + "%"));
        out.add(doubleSlider("Искажение при искривлении", o.getDistortionEffectScale(), 0.0, 1.0,
            v -> v <= 0.001 ? "Выкл" : Math.round(v * 100) + "%"));
        out.add(doubleSlider("Эффекты поля зрения", o.getFovEffectScale(), 0.0, 1.0,
            v -> v <= 0.001 ? "Выкл" : Math.round(v * 100) + "%"));
        out.add(toggle("Монохромный логотип", o.getMonochromeLogo()));
        out.add(toggle("Скрыть заставку", o.getHideSplashTexts()));
        out.add(doubleSlider("Время уведомлений", o.getNotificationDisplayTime(), 0.5, 10.0,
            v -> "x" + String.format(Locale.ROOT, "%.1f", v)));
        out.add(intSlider("Размытие фона меню", o.getMenuBackgroundBlurriness(), 0, 10,
            v -> v == 0 ? "Выкл" : String.valueOf(v)));
        out.add(doubleSlider("Скорость панорамы", o.getPanoramaSpeed(), 0.0, 1.0,
            v -> Math.round(v * 100) + "%"));
        out.add(toggle("Высокая контрастность", o.getHighContrast()));
        return out;
    }

    private static String soundName(SoundCategory c) {
        return switch (c) {
            case MASTER -> "Общая громкость";
            case MUSIC -> "Музыка";
            case RECORDS -> "Проигрыватель";
            case WEATHER -> "Погода";
            case BLOCKS -> "Блоки";
            case HOSTILE -> "Враждебные";
            case NEUTRAL -> "Дружелюбные";
            case PLAYERS -> "Игроки";
            case AMBIENT -> "Окружение";
            case VOICE -> "Голос";
            case UI -> "Интерфейс";
        };
    }

    private static LinkenSettingsOption toggle(String label, SimpleOption<Boolean> option) {
        return LinkenSettingsOption.toggle(label, option::getValue,
            () -> option.setValue(!option.getValue()));
    }

    private static LinkenSettingsOption toggle(String label,
                                               java.util.function.BooleanSupplier get, Runnable toggle) {
        return LinkenSettingsOption.toggle(label, get, toggle);
    }

    private static LinkenSettingsOption intSlider(String label, SimpleOption<Integer> option,
                                                  int min, int max, IntFunction<String> display) {
        return LinkenSettingsOption.slider(label,
            () -> display.apply(option.getValue()),
            () -> (option.getValue() - min) / (double) (max - min),
            value -> option.setValue((int) Math.round(min + value * (max - min))));
    }

    private static LinkenSettingsOption doubleSlider(String label, SimpleOption<Double> option,
                                                     double min, double max, DoubleFunction<String> display) {
        return LinkenSettingsOption.slider(label,
            () -> display.apply(option.getValue()),
            () -> (option.getValue() - min) / (max - min),
            value -> option.setValue(min + value * (max - min)));
    }

    private static <T> LinkenSettingsOption cycle(String label, SimpleOption<T> option,
                                                  T[] values, Function<T, String> display) {
        return LinkenSettingsOption.cycle(label,
            () -> display.apply(option.getValue()),
            () -> {
                T current = option.getValue();
                int index = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] == current) {
                        index = i;
                        break;
                    }
                }
                option.setValue(values[(index + 1) % values.length]);
            });
    }
}
