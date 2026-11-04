package ru.method.linkenvisuals.modules.impl.client;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiController;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettings;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiSettingsWindow;

public final class UiKeyHandler {
    public static final int DEFAULT_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
    // Скан-код правого Shift на Windows (0x36). Некоторые клавиатуры шлют правый Shift с keycode левого,
    // но со своим скан-кодом — ловим и его, чтобы GUI открывался на правый Shift.
    private static final int RIGHT_SHIFT_SCANCODE = 0x36;
    private static final long TOGGLE_DEBOUNCE_NS = 120_000_000L;
    private static long lastToggleNs;

    private UiKeyHandler() {
    }

    public static boolean tryHandleKey(int key, int scancode, int action) {
        if (action != GLFW.GLFW_PRESS || key < 0) {
            return false;
        }

        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.isPanic() || instance.getModuleManager() == null) {
            return false;
        }

        UI ui = instance.getModuleManager().getModule(UI.class);
        if (ui == null || !matchesUiBind(ui, key, scancode) || !canToggleUi()) {
            return false;
        }

        // Пока идёт назначение клавиши (bind component активен) — не тоглим GUI,
        // иначе нельзя назначить тогл-клавишу (нажатие сразу закрывало бы окно).
        if (isBindingActive(instance)) {
            return false;
        }

        long now = System.nanoTime();
        if (now - lastToggleNs < TOGGLE_DEBOUNCE_NS) {
            return true;
        }
        lastToggleNs = now;
        ui.toggle();
        return true;
    }

    public static boolean matchesUiBind(UI ui, int key) {
        return matchesUiBind(ui, key, -1);
    }

    public static boolean matchesUiBind(UI ui, int key, int scancode) {
        // GUI открывается ТОЛЬКО на забинженную клавишу (без спец-обработки правого Shift).
        Bind bind = ui.getBind();
        return bind != null && bind.matchesKey(key);
    }

    private static boolean isBindingActive(LinkenVisuals instance) {
        // Идёт назначение бинда функции на карточке — не тоглим GUI клавишей-тоглом.
        if (ru.method.linkenvisuals.screen.linkgui.components.ModuleCard.isAnyBinding()) {
            return true;
        }
        // Идёт назначение кейбинда открытия GUI в окне Темы.
        LinkGuiScreen linkScreen = LinkGuiController.getInstance().getScreen();
        if (linkScreen != null && linkScreen.isBindingUiKey()) {
            return true;
        }
        for (LinkGuiSettingsWindow window : LinkGuiSettings.getWindows()) {
            if (window.hasActiveBinding()) {
                return true;
            }
        }
        ClickGui clickGui = instance.getClickGui();
        return clickGui != null && clickGui.isDisplayed() && clickGui.hasActiveBinding();
    }

    public static boolean canToggleUi() {
        if (!UI.isInPlayWorld()) {
            return false;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) {
            return false;
        }
        ClickGui clickGui = instance.getClickGui();
        LinkGuiController linkGui = LinkGuiController.getInstance();
        if (clickGui == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null
                || client.currentScreen == clickGui
                || client.currentScreen == linkGui.getScreen()) {
            return true;
        }
        return false;
    }
}
