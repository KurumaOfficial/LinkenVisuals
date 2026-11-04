package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch;

/**
 * Предмет в руке (вид от 3-го лица / F5). При «Наруто»-анимации корпус наклоняется матрицей вокруг
 * рендера модели, но этот наклон снимается до фич — поэтому предмет оставался без наклона. Повторяем
 * тот же наклон вокруг рендера предмета (углы рук уже оставлены в Наруто-позе в LivingEntityRendererMixin,
 * так что предмет уже следует за поднятой рукой; здесь добавляем наклон корпуса).
 */
@Mixin(HeldItemFeatureRenderer.class)
public class ItemInHandFeatureRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void lvNarutoLeanPush(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                  ArmedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!CosmeticPosePatch.active) {
            return;
        }
        matrices.push();
        matrices.translate(0f, CosmeticPosePatch.pivotY, 0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(CosmeticPosePatch.leanRad));
        matrices.translate(0f, -CosmeticPosePatch.pivotY, 0f);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lvNarutoLeanPop(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                 ArmedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!CosmeticPosePatch.active) {
            return;
        }
        matrices.pop();
    }
}
