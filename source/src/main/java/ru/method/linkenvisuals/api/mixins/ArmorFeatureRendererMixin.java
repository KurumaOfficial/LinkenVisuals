package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.NoRender;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.Naruto;
import ru.method.linkenvisuals.utils.render.ArmorRenderStateHolder;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> extends FeatureRenderer<S, M> {

    @Unique private boolean linkenvisuals$narutoInvisPushed;

    public ArmorFeatureRendererMixin(FeatureRendererContext<S, M> context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$captureArmorState(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, S state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (ru.method.linkenvisuals.cosmetics.character.RudyCharacter.active(state)) {
            ci.cancel();
            return;
        }
        ArmorRenderStateHolder.set(state);
        linkenvisuals$narutoInvisPushed = false;
        // «Наруто»-наклон корпуса: модель игрока наклонена матрицей, которая снимается до фич —
        // повторяем наклон вокруг рендера брони, чтобы она сидела на наклонённом теле (углы частей
        // бронемодель сама копирует из модели игрока через copyTransforms).
        if (CosmeticPosePatch.active) {
            matrices.push();
            matrices.translate(0f, CosmeticPosePatch.pivotY, 0f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(CosmeticPosePatch.leanRad));
            matrices.translate(0f, -CosmeticPosePatch.pivotY, 0f);
        } else {
            // Наруто при ИНВИЗЕ своего игрока: ваниль не рисует модель → redirect позы (LivingEntityRendererMixin)
            // пропускается → CosmeticPosePatch не выставлен → «висящая» броня без наклона. Наклоняем её сами.
            MinecraftClient m = MinecraftClient.getInstance();
            if (m.player != null && m.player.isInvisible() && Naruto.enabled()
                    && state instanceof PlayerEntityRenderState ps && ps.id == m.player.getId()
                    && !ps.isSwimming && !ps.isGliding && !ps.isUsingItem && !ps.isInSneakingPose) {
                float run = MathHelper.clamp((float) m.player.getVelocity().horizontalLength() * 10f, 0f, 1f);
                if (run >= 0.10f) {
                    // Полная Наруто-поза для «висящей» брони: те же углы рук/головы, что и у видимой модели
                    // (LivingEntityRendererMixin), выставляем на модель ИГРОКА — броня их копирует (copyTransforms).
                    BipedEntityModel<S> pm = getContextModel();
                    float tickDelta = m.getRenderTickCounter().getTickProgress(true);
                    float tt = (m.player.age + tickDelta) * 0.6f;
                    float swing = MathHelper.cos(tt) * 0.20f * run;
                    float armBack = 1.25f + 0.45f * run + swing;
                    float armRoll = 0.55f * run;
                    float headDown = -0.15f * run;
                    pm.rightArm.pitch = armBack;
                    pm.leftArm.pitch = armBack;
                    pm.rightArm.roll = armRoll;
                    pm.leftArm.roll = -armRoll;
                    pm.rightArm.yaw = pm.rightArm.yaw + 0.10f * run;
                    pm.leftArm.yaw = pm.leftArm.yaw - 0.10f * run;
                    pm.head.pitch = pm.head.pitch + headDown;

                    float pivotY = 0.75f, lean = 0.45f * run;
                    matrices.push();
                    matrices.translate(0f, pivotY, 0f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotation(lean));
                    matrices.translate(0f, -pivotY, 0f);
                    linkenvisuals$narutoInvisPushed = true;
                }
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("RETURN"))
    private void linkenvisuals$clearArmorState(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, S state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (CosmeticPosePatch.active || linkenvisuals$narutoInvisPushed) {
            matrices.pop();
        }
        linkenvisuals$narutoInvisPushed = false;
        ArmorRenderStateHolder.clear();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    public void renderArmor(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack, EquipmentSlot slot, int light, S state, CallbackInfo ci) {
        NoRender noRender = LinkenVisuals.getInstance().getModuleManager().getModule(NoRender.class);
        if (noRender != null && noRender.isToggled() && noRender.armor.getValue()) {
            ci.cancel();
        }
    }
}
