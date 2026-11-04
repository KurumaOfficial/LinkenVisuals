package ru.method.linkenvisuals.screen.cosmetics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEnums.PetSkin;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEffects;

/**
 * Рендер иконок питомцев в карточках GUI — статичная камера, без nameplate.
 */
public final class CosmeticsPetIconRenderer {
    private CosmeticsPetIconRenderer() {
    }

    public static void render(DrawContext context, CosmeticEffects effects, PetSkin skin,
                              int x, int y, int size) {
        if (effects == null || skin == null || size <= 0) {
            return;
        }
        LivingEntity entity = effects.getPreviewPetEntity(skin);
        if (entity == null) {
            return;
        }

        int pad = Math.max(2, Math.round(size * 0.08f));
        int x1 = x + pad;
        int y1 = y + pad;
        int x2 = x + size - pad;
        int y2 = y + size - pad;

        float petHeight = Math.max(0.4f, entity.getHeight());
        int modelSize = Math.max(12, Math.round(size * (1.6f / petHeight)));

        float cx = x + size * 0.5f;
        float cy = y + size * 0.58f;

        CosmeticPreviewState.begin(new CosmeticPreviewState.Snapshot(
                new CosmeticProfile(), CosmeticSlot.EFFECT, false, 0f, 0f, 1f / 60f, true));
        try {
            InventoryScreen.drawEntity(context, x1, y1, x2, y2, modelSize, 0.0625f, cx, cy - size * 0.04f, entity);
        } finally {
            CosmeticPreviewState.end();
        }
    }
}
