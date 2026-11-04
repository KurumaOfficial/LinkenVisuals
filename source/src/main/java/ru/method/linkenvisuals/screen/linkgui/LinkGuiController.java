package ru.method.linkenvisuals.screen.linkgui;

import lombok.Getter;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.modules.impl.client.UI;

public final class LinkGuiController implements Wrapper {
    @Getter
    private static final LinkGuiController instance = new LinkGuiController();

    private final LinkGuiScreen screen = new LinkGuiScreen();

    private LinkGuiController() {}

    public void open() {
        if (!UI.isInPlayWorld() || mc == null) return;
        LinkGuiCloseOverlay.getInstance().cancel();
        screen.resetOpenAnimation();
        if (mc.currentScreen == screen) return;
        mc.setScreen(screen);
    }

    public void beginClose() {
        if (mc == null || mc.currentScreen != screen || screen.isClosing()) return;
        screen.beginClose();
    }

    void markClosed() {
    }

    public boolean isOpen() {
        return mc != null && mc.currentScreen == screen && !screen.isClosing();
    }

    public boolean isDisplayed() {
        return mc != null && mc.currentScreen == screen;
    }

    public boolean isClosing() {
        return screen.isClosing();
    }

    public LinkGuiScreen getScreen() {
        return screen;
    }
}
