package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.client.util.Monitor;
import net.minecraft.client.util.VideoMode;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Arm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class SettingsCatalog {

    private SettingsCatalog() {}

    public static List<SettingsOption> graphics() {
        MinecraftClient mc = MinecraftClient.getInstance();
        GameOptions o = mc.options;
        List<SettingsOption> l = new ArrayList<>();

        l.addAll(resolution());
        l.add(enumS("Графика", o.getPreset(), GraphicsMode.values(), SettingsCatalog::graphicsName));
        l.add(intS("Прорисовка", o.getViewDistance(), 2, 32, v -> v + " чанков"));
        l.add(intS("Симуляция", o.getSimulationDistance(), 5, 32, v -> v + " чанков"));
        l.add(enumS("Сборка чанков", o.getChunkBuilderMode(), ChunkBuilderMode.values(), SettingsCatalog::chunkName));
        l.add(intS("Частота кадров", o.getMaxFps(), 10, 260, v -> v >= 260 ? "Максимум" : v + " к/с"));
        l.add(boolT("Мягкое освещение", o.getAo()));
        l.add(boolT("V-Sync", o.getEnableVsync()));
        l.add(intS("Интерфейс", o.getGuiScale(), 0, 4, v -> v == 0 ? "Авто" : String.valueOf(v)));
        l.add(enumS("Индикатор атаки", o.getAttackIndicator(), AttackIndicator.values(), SettingsCatalog::attackName));
        l.add(dblS("Яркость", o.getGamma(), 0.0, 1.0,
                v -> v <= 0.01 ? "Мрачно" : v >= 0.99 ? "Ярко" : Math.round(v * 100) + "%"));
        l.add(enumS("Облака", o.getCloudRenderMode(), CloudRenderMode.values(), SettingsCatalog::cloudName));
        l.add(boolT("Полноэкранный режим", o.getFullscreen()));
        l.add(enumS("Частицы", o.getParticles(), p -> switch (p.name()) {
            case "ALL" -> "Все";
            case "DECREASED" -> "Меньше";
            default -> "Минимум";
        }));
        l.add(boolT("Тени сущностей", o.getEntityShadows()));
        l.add(dblS("До сущности", o.getEntityDistanceScaling(), 0.5, 5.0, v -> Math.round(v * 100) + "%"));
        l.add(dblS("Мерцание чар", o.getGlintSpeed(), 0.0, 1.0, v -> Math.round(v * 100) + "%"));
        l.add(dblS("Заметность чар", o.getGlintStrength(), 0.0, 1.0, v -> Math.round(v * 100) + "%"));
        l.add(intS("Размытие фона", o.getMenuBackgroundBlurriness(), 0, 10, String::valueOf));
        l.add(boolT("Покачивание камеры", o.getBobView()));
        return l;
    }

    public static List<SettingsOption> appearance() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<SettingsOption> l = new ArrayList<>();

        for (PlayerModelPart part : PlayerModelPart.values()) {
            l.add(SettingsOption.toggle(partName(part),
                    () -> o.isPlayerModelPartEnabled(part),
                    () -> {
                        o.setPlayerModelPart(part, !o.isPlayerModelPartEnabled(part));
                        o.sendClientSettings();
                    }));
        }
        l.add(enumS("Основная рука", o.getMainArm(), Arm.values(), a -> a == Arm.LEFT ? "Левая" : "Правая"));
        return l;
    }

    public static List<SettingsOption> sound() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<SettingsOption> l = new ArrayList<>();

        for (SoundCategory c : SoundCategory.values()) {
            l.add(dblS(soundName(c), o.getSoundVolumeOption(c), 0.0, 1.0,
                    v -> v <= 0.005 ? "Выкл" : Math.round(v * 100) + "%"));
        }

        l.add(boolT("Направленный звук", o.getDirectionalAudio()));
        l.add(boolT("Субтитры", o.getShowSubtitles()));
        l.add(SettingsOption.selectDisplay("Устройство вывода", () -> {
            String d = o.getSoundDevice().getValue();
            return d == null || d.isEmpty() ? "Системное" : d;
        }));
        return l;
    }

    public static List<SettingsOption> controls() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<SettingsOption> l = new ArrayList<>();

        // Список клавиш не сводится к трём контролам, поэтому он — строка-переход на свой экран.
        l.add(SettingsOption.action("Клавиши управления", () -> "Настроить", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            // Через transitionTo, если открыты из нашего экрана — тогда переход анимированный.
            if (c.currentScreen instanceof LinkenSettingsScreen s) s.openKeybinds();
            else c.setScreen(new KeybindsListScreen(c.currentScreen));
        }));
        l.add(dblS("Чувствительность мыши", o.getMouseSensitivity(), 0.0, 1.0,
                v -> Math.round(v * 200) + "%"));
        l.add(boolT("Инвертировать мышь", o.getInvertMouseY()));
        l.add(boolT("Автопрыжок", o.getAutoJump()));
        l.add(boolT("Красться переключением", o.getSneakToggled()));
        l.add(boolT("Бежать переключением", o.getSprintToggled()));
        l.add(boolT("Прямой ввод мыши", o.getRawMouseInput()));
        l.add(boolT("Дискретная прокрутка", o.getDiscreteMouseScroll()));
        l.add(boolT("Сенсорный экран", o.getTouchscreen()));
        l.add(boolT("Вкладка предметов оператора", o.getOperatorItemsTab()));
        l.add(dblS("Прокрутка", o.getMouseWheelSensitivity(), 0.01, 10.0,
                v -> String.format(Locale.ROOT, "%.2f", v)));
        return l;
    }

    public static List<SettingsOption> chat() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<SettingsOption> l = new ArrayList<>();

        l.add(enumS("Чат", o.getChatVisibility(), ChatVisibility.values(), SettingsCatalog::chatVisibilityName));
        l.add(boolT("Цвета", o.getChatColors()));
        l.add(boolT("Ссылки", o.getChatLinks()));
        l.add(boolT("Подтверждение ссылок", o.getChatLinksPrompt()));
        l.add(boolT("Подсказка команд", o.getAutoSuggestions()));
        l.add(boolT("Скрывать совпадающие ники", o.getHideMatchedNames()));
        l.add(dblS("Прозрачность", o.getChatOpacity(), 0.0, 1.0, v -> Math.round(v * 90 + 10) + "%"));
        l.add(dblS("Прозрачность фона", o.getTextBackgroundOpacity(), 0.0, 1.0, v -> Math.round(v * 100) + "%"));
        l.add(dblS("Масштаб", o.getChatScale(), 0.0, 1.0, v -> v <= 0.01 ? "Выкл" : Math.round(v * 100) + "%"));
        l.add(dblS("Ширина", o.getChatWidth(), 0.0, 1.0, v -> Math.round(40 + v * 280) + " px"));
        l.add(dblS("Высота (в фокусе)", o.getChatHeightFocused(), 0.0, 1.0, v -> Math.round(20 + v * 160) + " px"));
        l.add(dblS("Высота (без фокуса)", o.getChatHeightUnfocused(), 0.0, 1.0, v -> Math.round(20 + v * 160) + " px"));
        l.add(dblS("Межстрочный интервал", o.getChatLineSpacing(), 0.0, 1.0, v -> Math.round(v * 100) + "%"));
        l.add(dblS("Задержка чата", o.getChatDelay(), 0.0, 6.0,
                v -> v <= 0.01 ? "Нет" : String.format(Locale.ROOT, "%.1f с", v)));
        l.add(enumS("Диктор", o.getNarrator(), NarratorMode.values(), SettingsCatalog::narratorName));
        return l;
    }

    public static List<SettingsOption> accessibility() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<SettingsOption> l = new ArrayList<>();

        l.add(enumS("Диктор", o.getNarrator(), NarratorMode.values(), SettingsCatalog::narratorName));
        l.add(boolT("Субтитры", o.getShowSubtitles()));
        l.add(dblS("Прозрачность фона", o.getTextBackgroundOpacity(), 0.0, 1.0,
                v -> Math.round(v * 100) + "%"));
        l.add(SettingsOption.toggle("Фон текста везде",
                () -> !o.getBackgroundForChatOnly().getValue(),
                () -> o.getBackgroundForChatOnly().setValue(!o.getBackgroundForChatOnly().getValue())));
        l.add(dblS("Тряска от урона", o.getDamageTiltStrength(), 0.0, 1.0,
                v -> v <= 0.001 ? "Выкл" : Math.round(v * 100) + "%"));
        l.add(dblS("Искажение при искривлении", o.getDistortionEffectScale(), 0.0, 1.0,
                v -> v <= 0.001 ? "Выкл" : Math.round(v * 100) + "%"));
        l.add(dblS("Эффекты поля зрения", o.getFovEffectScale(), 0.0, 1.0,
                v -> v <= 0.001 ? "Выкл" : Math.round(v * 100) + "%"));
        l.add(boolT("Монохромный логотип", o.getMonochromeLogo()));
        l.add(boolT("Скрыть заставку", o.getHideSplashTexts()));
        l.add(dblS("Время уведомлений", o.getNotificationDisplayTime(), 0.5, 10.0,
                v -> "x" + String.format(Locale.ROOT, "%.1f", v)));
        l.add(intS("Размытие фона меню", o.getMenuBackgroundBlurriness(), 0, 10,
                v -> v == 0 ? "Выкл" : String.valueOf(v)));
        l.add(dblS("Скорость панорамы", o.getPanoramaSpeed(), 0.0, 1.0,
                v -> Math.round(v * 100) + "%"));
        l.add(boolT("Высокая контрастность", o.getHighContrast()));
        return l;
    }

    /** «Онлайн» — то же, что ванильный {@code OnlineOptionsScreen}. */
    public static List<SettingsOption> online() {
        GameOptions o = MinecraftClient.getInstance().options;
        List<SettingsOption> l = new ArrayList<>();
        l.add(boolT("Уведомления Realms", o.getRealmsNotifications()));
        l.add(boolT("Показывать меня в списке", o.getAllowServerListing()));
        l.add(boolT("Расширенная телеметрия", o.getTelemetryOptInExtra()));
        return l;
    }

    public static List<SettingsOption> resolution() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();
        Monitor monitor = window.getMonitor();

        if (monitor == null) {
            return List.of(SettingsOption.selectDisplay("Разрешение",
                    () -> window.getWidth() + " x " + window.getHeight()));
        }

        Supplier<List<String>> modes = () -> {
            List<String> names = new ArrayList<>();
            names.add("Текущее");
            for (int i = 0; i < monitor.getVideoModeCount(); i++) names.add(videoModeName(monitor.getVideoMode(i)));
            return names;
        };

        return List.of(SettingsOption.select("Разрешение",
                () -> window.getFullscreenVideoMode().map(SettingsCatalog::videoModeName).orElse("Текущее"),
                modes,
                idx -> {
                    if (idx < 0 || idx > monitor.getVideoModeCount()) return;
                    Optional<VideoMode> vm = idx == 0 ? Optional.empty() : Optional.of(monitor.getVideoMode(idx - 1));
                    window.setFullscreenVideoMode(vm);
                    mc.options.fullscreenResolution = vm.map(VideoMode::asString).orElse(GameOptions.EMPTY_STRING);
                    window.applyFullscreenVideoMode();
                }));
    }

    // ---------- Хелперы привязки ----------

    private static SettingsOption boolT(String label, SimpleOption<Boolean> opt) {
        return SettingsOption.toggle(label, opt::getValue, () -> opt.setValue(!opt.getValue()));
    }

    private static SettingsOption intS(String label, SimpleOption<Integer> opt, int min, int max, IntFunction<String> disp) {
        return SettingsOption.slider(label,
                () -> disp.apply(opt.getValue()),
                () -> (opt.getValue() - min) / (double) (max - min),
                v -> opt.setValue((int) Math.round(min + v * (max - min))));
    }

    private static SettingsOption dblS(String label, SimpleOption<Double> opt, double min, double max, DoubleFunction<String> disp) {
        return SettingsOption.slider(label,
                () -> disp.apply(opt.getValue()),
                () -> (opt.getValue() - min) / (max - min),
                v -> opt.setValue(min + v * (max - min)));
    }

    private static <T> SettingsOption enumS(String label, SimpleOption<T> opt, T[] values, Function<T, String> disp) {
        return SettingsOption.select(label,
                () -> disp.apply(opt.getValue()),
                () -> {
                    List<String> names = new ArrayList<>();
                    for (T v : values) names.add(disp.apply(v));
                    return names;
                },
                idx -> { if (idx >= 0 && idx < values.length) opt.setValue(values[idx]); });
    }

    private static <T extends Enum<T>> SettingsOption enumS(String label, SimpleOption<T> opt, Function<T, String> disp) {
        return SettingsOption.select(label,
                () -> disp.apply(opt.getValue()),
                () -> {
                    List<String> names = new ArrayList<>();
                    for (T v : opt.getValue().getDeclaringClass().getEnumConstants()) names.add(disp.apply(v));
                    return names;
                },
                idx -> {
                    T[] vals = opt.getValue().getDeclaringClass().getEnumConstants();
                    if (idx >= 0 && idx < vals.length) opt.setValue(vals[idx]);
                });
    }

    // ---------- Локализованные названия значений ----------

    private static String graphicsName(GraphicsMode m) {
        return switch (m) {
            case FAST -> "Быстро";
            case FANCY -> "Детально";
            case FABULOUS -> "Улучшено";
            case CUSTOM -> "Пользовательский";
        };
    }

    private static String chunkName(ChunkBuilderMode m) {
        return switch (m) {
            case NONE -> "Нет";
            case PLAYER_AFFECTED -> "Потоковая";
            case NEARBY -> "Ближние";
        };
    }

    private static String attackName(AttackIndicator a) {
        return switch (a) {
            case OFF -> "Выкл";
            case CROSSHAIR -> "Прицел";
            case HOTBAR -> "Хотбар";
        };
    }

    private static String cloudName(CloudRenderMode c) {
        return switch (c) {
            case OFF -> "Выкл";
            case FAST -> "Быстрые";
            case FANCY -> "Объёмные";
        };
    }

    private static String partName(PlayerModelPart p) {
        return switch (p) {
            case CAPE -> "Плащ";
            case JACKET -> "Куртка";
            case LEFT_SLEEVE -> "Левый рукав";
            case RIGHT_SLEEVE -> "Правый рукав";
            case LEFT_PANTS_LEG -> "Левая штанина";
            case RIGHT_PANTS_LEG -> "Правая штанина";
            case HAT -> "Шляпа";
        };
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

    private static String chatVisibilityName(ChatVisibility v) {
        return switch (v) {
            case FULL -> "Показать";
            case SYSTEM -> "Только команды";
            case HIDDEN -> "Скрыть";
        };
    }

    private static String narratorName(NarratorMode m) {
        return switch (m) {
            case OFF -> "Выкл";
            case ALL -> "Всё";
            case CHAT -> "Чат";
            case SYSTEM -> "Система";
        };
    }

    private static String videoModeName(VideoMode m) {
        return m.getWidth() + " x " + m.getHeight() + " (" + m.getRefreshRate() + " Гц)";
    }
}
