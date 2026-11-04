package ru.method.linkenvisuals.screen.linkgui;

import lombok.Getter;
import ru.method.linkenvisuals.utils.Wrapper;

/** Link GUI закрывается в экране (без 3D/мирового оверлея). Заглушка для совместимости вызовов. */
public final class LinkGuiCloseOverlay implements Wrapper {
    @Getter
    private static final LinkGuiCloseOverlay instance = new LinkGuiCloseOverlay();

    private LinkGuiCloseOverlay() {}

    public void start(LinkGuiScreen screen) {
    }

    public void cancel() {
    }

    public boolean isActive() {
        return false;
    }
}
