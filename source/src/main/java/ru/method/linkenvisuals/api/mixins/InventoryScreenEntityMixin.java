package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;

/**
 * Гарантирует подавление nameplate при любом {@link InventoryScreen#drawEntity} в GUI-превью.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenEntityMixin {

    @Inject(
            method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD")
    )
    private static void linkenvisuals$trackGuiEntityDraw(
            DrawContext context,
            int x1, int y1, int x2, int y2,
            int size, float f, float mouseX, float mouseY,
            LivingEntity entity,
            CallbackInfo ci
    ) {
        if (CosmeticPreviewState.isActive()) {
            entity.setCustomNameVisible(false);
            entity.setCustomName(null);
        }
    }
}
