package ru.method.linkenvisuals.modules.impl.render.cosmetic;

public final class Naruto {

    private static volatile Naruto INSTANCE;

    private final CosmeticEffects parent;

    public Naruto(CosmeticEffects parent) {
        this.parent = parent;
        INSTANCE = this;
    }

    public void onDisable() {
    }

    public static boolean enabled() {
        Naruto instance = INSTANCE;
        if (instance == null) {
            return false;
        }
        return instance.isEnabled();
    }

    private boolean isEnabled() {
        return parent != null && parent.narutoOn();
    }
}
