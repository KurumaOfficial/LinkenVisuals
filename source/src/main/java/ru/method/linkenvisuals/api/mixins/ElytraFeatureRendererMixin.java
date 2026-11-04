package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch;

/**
 * Ванильная элитра (крылья-предмет) рисуется фичей ПОСЛЕ модели, вне «Наруто»-наклона корпуса
 * (наклон применяется матрицей только вокруг рендера модели в {@link LivingEntityRendererMixin} и
 * снимается до фич). Из-за этого при «Наруто» тело наклонено, а элитра остаётся в исходной позе —
 * «висит в воздухе» за спиной. Повторяем тот же наклон вокруг рендера элитры (как HeldItem/Armor),
 * чтобы она сидела на наклонённой спине даже во время анимации. Сигнатура — как у ArmorFeatureRenderer
 * (обе фичи бипеда, render-state = BipedEntityRenderState).
 */
@Mixin(ElytraFeatureRenderer.class)
public class ElytraFeatureRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"))
    private void linkenvisuals$narutoLeanPush(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                              BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!CosmeticPosePatch.active) {
            return;
        }
        matrices.push();
        matrices.translate(0f, CosmeticPosePatch.pivotY, 0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(CosmeticPosePatch.leanRad));
        matrices.translate(0f, -CosmeticPosePatch.pivotY, 0f);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("RETURN"))
    private void linkenvisuals$narutoLeanPop(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                             BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!CosmeticPosePatch.active) {
            return;
        }
        matrices.pop();
    }
}
