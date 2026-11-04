package ru.method.linkenvisuals.screen.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.ChatOptionsScreen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.MouseOptionsScreen;
import net.minecraft.client.gui.screen.option.OnlineOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;
import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;

/**
 * Единая таблица подмены ванильных окон настроек на экраны редизайна (спека 10c: «ни одного
 * ванильного окна не осталось»).
 *
 * <p>Вызывается из перехвата {@code MinecraftClient#setScreen}, поэтому покрывает все пути открытия
 * сразу — главное меню, игровое ESC-меню, переходы между самими под-экранами настроек.
 *
 * <p>Родителем подставляем текущий экран, если это уже наш экран настроек: тогда «назад»/ESC
 * возвращают по цепочке. Иначе родителя нет — и {@code back()} сам решит, куда выходить
 * (в игру или в главное меню).
 */
public final class SettingsRouter {

    private SettingsRouter() {}

    /** @return экран-замена или {@code null}, если подменять нечего. */
    public static Screen replace(Screen screen) {
        if (screen == null) return null;

        Screen parent = parentFor(screen);

        if (screen instanceof OptionsScreen) return new SettingsHubScreen(parent);
        // Графика — единственный экран, куда моды (Iris «Shader Packs», Sodium «Video Settings»)
        // дорисовывают СВОИ кнопки прямыми детьми экрана. Штатные ванильные опции лежат внутри
        // OptionListWidget и в children() как кнопки НЕ появляются, поэтому harvest не дублирует их —
        // добавляются только мод-кнопки (и «Готово», которую отсекаем). На остальных экранах harvest
        // не нужен: клавиши модов уже полностью покрыты кастомным экраном «Клавиши» (allKeys).
        if (screen instanceof VideoOptionsScreen) {
            Screen sodium = GraphicsSettings.sodium(parent);
            return sodium != null ? sodium : graphics(parent, screen);
        }
        if (screen instanceof SoundOptionsScreen) return sub(parent, "Музыка и звуки", SettingsCatalog.sound());
        if (screen instanceof SkinOptionsScreen) return sub(parent, "Внешний вид", SettingsCatalog.appearance());
        if (screen instanceof ChatOptionsScreen) return sub(parent, "Чат", SettingsCatalog.chat());
        if (screen instanceof AccessibilityOptionsScreen) {
            return sub(parent, "Спец. возможности", SettingsCatalog.accessibility());
        }
        if (screen instanceof MouseOptionsScreen || screen instanceof ControlsOptionsScreen) {
            return sub(parent, "Управление", SettingsCatalog.controls());
        }
        if (screen instanceof OnlineOptionsScreen) return sub(parent, "Онлайн", SettingsCatalog.online());
        if (screen instanceof KeybindsScreen) return new KeybindsListScreen(parent);
        if (screen instanceof LanguageOptionsScreen) return new LanguageListScreen(parent);
        if (screen instanceof CreditsAndAttributionScreen) return new CreditsListScreen(parent);
        return null;
    }

    static Screen graphics(Screen parent, Screen vanilla) {
        return sub(parent, "Графика", withModButtons(vanilla, SettingsCatalog.graphics()));
    }

    private static SubSettingsScreen sub(Screen parent, String title, java.util.List<SettingsOption> options) {
        return new SubSettingsScreen(parent, title, options);
    }

    /**
     * Достраивает каталог кнопками, которые моды дорисовывают на живом ванильном экране (Iris
     * «Shader Packs» и т.п.) — иначе редизайн их «съедает». Инициализируем ванильный экран за кадром
     * (как {@code LinkenPauseScreen.harvest}) и берём его прямые кнопки. Штатные опции лежат в
     * OptionListWidget и сюда не попадают, так что дублирования нет; «Готово»/«Назад» отсекаем.
     * Любой сбой — тихо возвращаем базовый каталог: harvest не должен ронять настройки.
     */
    private static List<SettingsOption> withModButtons(Screen vanilla, List<SettingsOption> base) {
        List<SettingsOption> out = new ArrayList<>(base);
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return out;
            vanilla.init(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            for (Element e : vanilla.children()) {
                if (!(e instanceof ClickableWidget w) || !(e instanceof PressableWidget p) || !w.visible) continue;
                String key = translationKey(w.getMessage());
                if ("gui.done".equals(key) || "gui.back".equals(key) || "options.done".equals(key)) continue;
                String label = w.getMessage().getString().trim();
                if (label.isEmpty()) continue;
                out.add(SettingsOption.action(label, () -> "", () -> p.onPress(new net.minecraft.client.input.MouseInput(0, 0))));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String translationKey(Text text) {
        return text != null && text.getContent() instanceof TranslatableTextContent t ? t.getKey() : "";
    }

    /**
     * Родитель для нового экрана. Берём текущий экран только если он наш — подставлять ванильный
     * экран родителем нельзя: возврат в него снова прошёл бы через подмену и дал бы петлю.
     */
    private static Screen parentFor(Screen incoming) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen current = mc == null ? null : mc.currentScreen;
        if (current instanceof LinkenSettingsScreen && current != incoming) return current;
        return null;
    }
}
