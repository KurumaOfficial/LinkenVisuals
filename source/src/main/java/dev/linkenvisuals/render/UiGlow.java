package dev.linkenvisuals.render;

/**
 * Shared switch for the LinkGUI accent halos. The donor {@code UI} module owns
 * the persisted setting; both the donor and port renderers read this flag so a
 * single toggle disables the neon outline without touching panel borders.
 */
public final class UiGlow {
    private static volatile boolean enabled = true;

    private UiGlow() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
