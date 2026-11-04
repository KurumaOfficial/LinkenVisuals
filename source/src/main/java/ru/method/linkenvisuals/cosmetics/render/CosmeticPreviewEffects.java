package ru.method.linkenvisuals.cosmetics.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.player.CosmeticsModule;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.CosmeticEffects;

/**
 * Рендер косметических эффектов движка (питомец и т.д.) поверх плеер-превью в GUI (меню косметики и
 * профиль Link GUI). Питомец — реальная сущность, рисуется ванильным {@link InventoryScreen#drawEntity}
 * в под-прямоугольнике рядом с моделью. Нимб/сферы будут добавлены отдельным слоем.
 */
public final class CosmeticPreviewEffects {

    private CosmeticPreviewEffects() {
    }

    /**
     * @param x,y,w,h    прямоугольник области превью (тот же, что у модели)
     * @param modelSize  масштаб модели (size из drawEntity) — питомца делаем пропорционально меньше
     */
    public static void render(DrawContext context, int x, int y, int w, int h, int modelSize) {
        if (w <= 0 || h <= 0) return;

        CosmeticsModule module = LinkenVisuals.getInstance().getModuleManager().getModule(CosmeticsModule.class);
        // Превью показывает эффект даже при выключенном модуле — как и гардероб (см. CosmeticsService.shouldRender).
        if (module == null) return;
        if (!ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState.isActive() && !module.isToggle()) return;

        CosmeticEffects effects = module.getEffects();
        if (effects == null) return;

        LivingEntity pet = effects.getPreviewPetEntity();
        if (pet == null) return;

        // Под-прямоугольник для питомца — нижне-правая часть области, рядом с моделью.
        int pw = Math.max(24, Math.round(w * 0.55f));
        int ph = Math.max(24, Math.round(h * 0.55f));
        int cx = x + Math.round(w * 0.70f);          // центр питомца — правее модели
        int cy = y + Math.round(h * 0.62f);          // и чуть ниже середины
        int px1 = cx - pw / 2;
        int py1 = cy - ph / 2;
        int px2 = px1 + pw;
        int py2 = py1 + ph;

        // Нормализуем размер по росту сущности: мелкие питомцы (рыба-шар ~0.7) иначе выходят крошечными.
        float petHeight = Math.max(0.4f, pet.getHeight());
        int petSize = Math.max(10, Math.round(modelSize * (1.7f / petHeight) * 0.6f));

        float lookX = cx;
        float lookY = py1 + ph * 0.45f;

        InventoryScreen.drawEntity(context, px1, py1, px2, py2, petSize, 0.0625f, lookX, lookY, pet);
    }
}
