package ru.method.linkenvisuals.screen.clickgui.redesign;

import net.fabricmc.loader.api.FabricLoader;
import ru.method.linkenvisuals.LinkenVisuals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Хранилище закреплённых модулей нового ClickGUI (левая колонка «закреплённые карточки»).
 * Пин-системы в клиенте не было — это самостоятельный лёгкий стор: имена модулей (в порядке закрепления),
 * персист в конфиг-директорию (переживает перезапуск). Действие «⋯ → закрепить/открепить» на карточке модуля.
 */
public final class ClickGuiPins {
    private static final Set<String> PINNED = new LinkedHashSet<>();
    private static boolean loaded;

    private ClickGuiPins() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("linkenvisuals_clickgui_pins.txt");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = file();
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p)) {
                    String name = line.trim();
                    if (!name.isEmpty()) PINNED.add(name);
                }
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("[ClickGui] failed to load pins", e);
        }
    }

    private static void save() {
        try {
            Files.write(file(), new ArrayList<>(PINNED));
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("[ClickGui] failed to save pins", e);
        }
    }

    public static boolean isPinned(String moduleName) {
        ensureLoaded();
        return moduleName != null && PINNED.contains(moduleName);
    }

    public static void toggle(String moduleName) {
        if (moduleName == null) return;
        ensureLoaded();
        if (!PINNED.remove(moduleName)) PINNED.add(moduleName);
        save();
    }

    public static void unpin(String moduleName) {
        if (moduleName == null) return;
        ensureLoaded();
        if (PINNED.remove(moduleName)) save();
    }

    /** Имена закреплённых модулей в порядке закрепления. */
    public static List<String> names() {
        ensureLoaded();
        return new ArrayList<>(PINNED);
    }
}
