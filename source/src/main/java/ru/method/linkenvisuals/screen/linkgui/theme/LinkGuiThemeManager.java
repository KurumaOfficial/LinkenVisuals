package ru.method.linkenvisuals.screen.linkgui.theme;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.client.UI;

/**
 * Global Link GUI theme — syncs the full Panel GUI color palette to {@link ru.method.linkenvisuals.utils.render.ColorUtils}.
 * Link GUI chrome keeps its own dark {@code preset.background()} via {@link LinkGuiThemeScope}.
 */
public final class LinkGuiThemeManager {
    private static final long TRANSITION_MS = 340L;
    private static volatile LinkGuiThemePreset selected = LinkGuiThemePreset.MIDNIGHT;
    private static volatile LinkGuiThemePreset previous = LinkGuiThemePreset.MIDNIGHT;
    private static volatile long transitionStartMs;
    private static volatile boolean hudAccentApplied;

    private LinkGuiThemeManager() {
    }

    public static LinkGuiThemePreset getSelected() {
        return selected;
    }

    /** Тема, с которой идёт переход (для плавной анимации смены цвета). */
    public static LinkGuiThemePreset getPrevious() {
        return previous;
    }

    /** Прогресс плавного перехода цвета 0..1 (ease-out). */
    public static float transitionProgress() {
        if (transitionStartMs <= 0L) {
            return 1f;
        }
        float raw = (System.currentTimeMillis() - transitionStartMs) / (float) TRANSITION_MS;
        if (raw >= 1f) {
            return 1f;
        }
        raw = Math.max(0f, raw);
        return (float) (1.0 - Math.pow(1.0 - raw, 3.0));
    }

    public static LinkGuiThemePreset getApplied() {
        return selected;
    }

    /** First HUD frame — apply whatever theme is currently selected (config/default). */
    public static void ensureHudAccent() {
        if (hudAccentApplied) {
            return;
        }
        applyInternal(selected);
        hudAccentApplied = true;
    }

    public static void setTheme(LinkGuiThemePreset preset) {
        if (preset == null) {
            preset = LinkGuiThemePreset.MIDNIGHT;
        }
        if (preset != selected) {
            // Запускаем плавный переход цвета от текущей темы к новой.
            previous = selected;
            transitionStartMs = System.currentTimeMillis();
        }
        selected = preset;
        applyInternal(preset);
        hudAccentApplied = true;
        persistThemeChoice();
    }

    public static void applyForced(LinkGuiThemePreset preset) {
        setTheme(preset);
    }

    public static void apply(LinkGuiThemePreset preset) {
        setTheme(preset);
    }

    public static void loadFromName(String name) {
        LinkGuiThemePreset preset = LinkGuiThemePreset.byName(name);
        if (preset != null) {
            selected = preset;
        }
    }

    public static void reapplyAfterConfigLoad() {
        applyInternal(selected);
        hudAccentApplied = true;
    }

    private static void applyInternal(LinkGuiThemePreset preset) {
        preset.applyToColorUtils();
        syncUiModule(preset);
    }

    private static void syncUiModule(LinkGuiThemePreset preset) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return;
        }
        UI ui = instance.getModuleManager().getModule(UI.class);
        if (ui != null) {
            ui.syncFromLinkTheme(preset);
        }
    }

    private static void persistThemeChoice() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getConfigManager() == null || instance.isConfigLoading()) {
            return;
        }
        instance.getConfigManager().forceSave();
    }
}
