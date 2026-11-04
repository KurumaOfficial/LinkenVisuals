package ru.method.linkenvisuals.cosmetics.render;

/**
 * Снимок текущей косметической позы игрока (Наруто-наклон корпуса + доворот головы). Заполняется в
 * {@code LivingEntityRendererMixin#linkenvisuals$cosmeticPoseRedirect} ПЕРЕД рендером модели, а
 * аттачменты-фичеры (шапка/крылья) читают его, чтобы следовать за наклоном — иначе при «Наруто»
 * корпус наклонён, а шапка остаётся на исходном месте (наклон/восстановление головы снимаются до фич).
 */
public final class CosmeticPosePatch {
    // Поля per-entity (сбрасываются на каждом рендере сущности) — для аттачментов-фич (шапка/крылья),
    // которые рисуются сразу после рендера модели той же сущности.
    public static boolean active;
    public static float pivotY;
    public static float leanRad;
    public static float headDeltaPitch;

    // Постоянные «self»-поля: мировые эффекты над головой (ChinaHat, нимб) рисуются в ОТДЕЛЬНОМ проходе
    // ПОЗЖЕ рендера модели, когда per-entity снимок уже затёрт другими сущностями. Эти поля держат
    // наклон СВОЕГО игрока на весь кадр (своя модель рендерится каждый кадр и обновляет их).
    public static boolean selfActive;
    public static float selfPivotY;
    public static float selfLeanRad;
    public static float selfHeadDeltaPitch;

    private CosmeticPosePatch() {
    }

    public static void set(float pivotY, float leanRad, float headDeltaPitch) {
        active = true;
        CosmeticPosePatch.pivotY = pivotY;
        CosmeticPosePatch.leanRad = leanRad;
        CosmeticPosePatch.headDeltaPitch = headDeltaPitch;
    }

    public static void clear() {
        active = false;
        pivotY = 0f;
        leanRad = 0f;
        headDeltaPitch = 0f;
    }

    public static void setSelf(float pivotY, float leanRad) {
        setSelf(pivotY, leanRad, 0f);
    }

    public static void setSelf(float pivotY, float leanRad, float headDeltaPitch) {
        selfActive = true;
        selfPivotY = pivotY;
        selfLeanRad = leanRad;
        selfHeadDeltaPitch = headDeltaPitch;
    }

    public static void clearSelf() {
        selfActive = false;
        selfPivotY = 0f;
        selfLeanRad = 0f;
        selfHeadDeltaPitch = 0f;
    }
}
