package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch;

/**
 * Наклоняет НАДЕТУЮ ГОЛОВУ-предмет (player_head / mob skull, слот шлема) вместе с корпусом при «Наруто»-беге.
 * Наклон корпуса применяется матрицей вокруг рендера модели и СНИМАЕТСЯ до фич-рендереров, поэтому надетая
 * голова висела на не наклонённом месте (как раньше висела броня — её фиксит ArmorFeatureRendererMixin).
 * Доворот головы (headDown) HeadFeatureRenderer и так берёт из head ModelPart — здесь добавляем только наклон.
 */
@Mixin(HeadFeatureRenderer.class)
public abstract class HeadFeatureRendererMixin<S extends LivingEntityRenderState, M extends EntityModel<S> & ModelWithHead>
        extends FeatureRenderer<S, M> {

    @Unique private boolean linkenvisuals$leanPushed;

    public HeadFeatureRendererMixin(FeatureRendererContext<S, M> context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V", at = @At("HEAD"))
    private void linkenvisuals$leanHead(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                        S state, float limbAngle, float limbDistance, CallbackInfo ci) {
        linkenvisuals$leanPushed = false;
        if (CosmeticPosePatch.active) {
            matrices.push();
            matrices.translate(0f, CosmeticPosePatch.pivotY, 0f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(CosmeticPosePatch.leanRad));
            matrices.translate(0f, -CosmeticPosePatch.pivotY, 0f);
            linkenvisuals$leanPushed = true;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V", at = @At("RETURN"))
    private void linkenvisuals$clearHead(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                         S state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (linkenvisuals$leanPushed) {
            matrices.pop();
            linkenvisuals$leanPushed = false;
        }
    }
}
