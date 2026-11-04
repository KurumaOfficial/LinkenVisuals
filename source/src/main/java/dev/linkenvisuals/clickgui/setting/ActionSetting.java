package dev.linkenvisuals.clickgui.setting;

public final class ActionSetting extends Setting {
    private final Runnable action;

    public ActionSetting(String name, Runnable action) {
        super(name);
        this.action = action;
    }

    @Override
    public float height() {
        return 32.0f;
    }

    public void run() {
        if (action != null) {
            action.run();
        }
    }
}
