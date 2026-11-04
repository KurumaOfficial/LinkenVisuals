package ru.method.linkenvisuals.modules.settings.api;


import lombok.AllArgsConstructor;
import lombok.Setter;
import ru.method.linkenvisuals.utils.input.KeyNameUtils;

@AllArgsConstructor
@Setter

public class Bind {
    public static final int MOUSE_SCROLL_UP = 1000;
    public static final int MOUSE_SCROLL_DOWN = 1001;

    private int key;
    private boolean mouse;

    private void migrateLegacy() {
        if (!mouse && key <= -95 && key >= -120) {
            int button = key + 100;
            if (button >= 0) {
                key = button;
                mouse = true;
            }
        }
    }

    public int getKey() {
        migrateLegacy();
        return key;
    }

    public boolean isMouse() {
        migrateLegacy();
        return mouse;
    }

    public boolean matchesKey(int key) {
        migrateLegacy();
        if (isEmpty() || mouse || key < 0) return false;
        return this.key == key;
    }

    public boolean matchesMouse(int button) {
        migrateLegacy();
        if (isEmpty() || !mouse || button < 0) return false;
        return this.key == button;
    }

    public boolean matchesMouseScroll(double verticalAmount) {
        migrateLegacy();
        if (isEmpty() || !mouse || verticalAmount == 0.0) return false;
        return this.key == (verticalAmount > 0.0 ? MOUSE_SCROLL_UP : MOUSE_SCROLL_DOWN);
    }

    @Override
    public String toString() {
        migrateLegacy();
        return KeyNameUtils.formatBind(this);
    }

    public boolean isEmpty() {
        return key < 0;
    }
}
