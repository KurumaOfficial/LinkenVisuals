package ru.method.linkenvisuals.cosmetics.preview;

import org.jetbrains.annotations.Nullable;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;

public final class CosmeticPreviewState {
    private static final ThreadLocal<Snapshot> ACTIVE = new ThreadLocal<>();

    private CosmeticPreviewState() {
    }

    public record Snapshot(
            CosmeticProfile profile,
            CosmeticSlot focusSlot,
            boolean walking,
            float animationTimeSeconds,
            float movingBlend,
            float deltaSeconds,
            boolean suppressEntityLabel
    ) {
        public Snapshot(CosmeticProfile profile, CosmeticSlot focusSlot, boolean walking,
                        float animationTimeSeconds, float movingBlend, float deltaSeconds) {
            this(profile, focusSlot, walking, animationTimeSeconds, movingBlend, deltaSeconds, false);
        }
    }

    public static void begin(Snapshot snapshot) {
        ACTIVE.set(snapshot);
    }

    public static void end() {
        ACTIVE.remove();
    }

    @Nullable
    public static Snapshot current() {
        return ACTIVE.get();
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static boolean suppressesEntityLabel() {
        Snapshot snapshot = ACTIVE.get();
        return snapshot != null && snapshot.suppressEntityLabel();
    }
}
