package ru.method.linkenvisuals.cosmetics.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;

/**
 * Превью-модель в стиле инвентаря. Ракурс задаётся полями {@link #yawDeg}/{@link #pitchDeg} — их крутит
 * драг мыши в CosmeticsScreen. Раньше было авто-покачивание (lookX += sin(time)); теперь персонажа можно
 * вращать вручную на все 360° (посмотреть со спины), через кватернион-оверлоуд drawEntity.
 */
public final class CosmeticEntityPreviewRenderer {
    private static final float ENTITY_Y_OFFSET_SCALE = 0.0625f;

    /** Ракурс превью: yaw — вокруг вертикали (0 — лицом к зрителю), pitch — наклон камеры вверх/вниз. */
    public static float yawDeg = 0f;
    public static float pitchDeg = 0f;

    private CosmeticEntityPreviewRenderer() {
    }

    public static void draw(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            LivingEntity entity,
            CosmeticSlot slot
    ) {
        if (width <= 0 || height <= 0 || entity == null) {
            return;
        }

        int size = Math.max(20, Math.round(Math.min(width, height) / 2.3f));
        switch (slot) {
            // Высокие уборы (корона/рога) обрезались сверху — уменьшаем модель, чтобы над головой был запас.
            case HAT -> size = Math.max(20, Math.round(Math.min(width, height) * 0.34f));
            // Крылья широкие — заметно уменьшаем модель, чтобы не вылезали за кадр.
            case WINGS -> size = Math.max(14, Math.round(Math.min(width, height) * 0.24f));
            default -> {
            }
        }

        drawRotated(context, x, y, x + width, y + height, size, ENTITY_Y_OFFSET_SCALE, entity);

        // Косметические эффекты движка (питомец и т.д.) поверх модели.
        CosmeticPreviewEffects.render(context, x, y, width, height, size);
    }

    /**
     * Рисует сущность в прямоугольнике [x1,y1..x2,y2] с ракурсом {@link #yawDeg}/{@link #pitchDeg} —
     * полный оборот вокруг вертикали. Использует кватернион-оверлоуд {@code drawEntity} (в отличие от
     * look-at варианта он не ограничивает поворот ~±63° и не режет модель ножницами по краям кадра).
     * bodyYaw/головы/pitch сущности временно подменяем на время рендера и возвращаем.
     */
    public static void drawRotated(DrawContext context, int x1, int y1, int x2, int y2, int size, float yOffset, LivingEntity entity) {
        float cx = (x1 + x2) / 2f;
        // InventoryScreen.drawEntity derives the body yaw from the look-at point, not from the
        // entity yaw. Shift the look point for the cape/wings tabs so the preview shows the back.
        float lookX = cx - (yawDeg / 180f) * 18f;
        InventoryScreen.drawEntity(context, x1, y1, x2, y2, Math.max(2, size),
                0.0625f, lookX, y1 + (y2 - y1) * 0.80f, entity);
    }
}
