package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.accessors.IHitColorRenderState;
import ru.method.linkenvisuals.utils.Wrapper;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.modules.impl.render.SelfNametags;
import ru.method.linkenvisuals.modules.impl.render.HitColor;
import ru.method.linkenvisuals.modules.impl.render.Drochka;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.Naruto;
import ru.method.linkenvisuals.cosmetics.render.CosmeticPosePatch;
import ru.method.linkenvisuals.utils.render.HitColorRenderHelper;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> implements FeatureRendererContext<S, M>, Wrapper {

    @Unique
    private static final ThreadLocal<LivingEntityRenderState> LINKENVISUALS$CURRENT_RENDER_STATE = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<net.minecraft.client.render.command.OrderedRenderCommandQueue> LINKENVISUALS$CURRENT_QUEUE = new ThreadLocal<>();

    // Объёмная грудь (модуль Drochka «Грудь»): два кубоида на груди, текстура берётся из скина игрока.
    // Строится один раз лениво, координаты — в модельных единицах BipedEntityModel (перед body).
    @Unique
    private static ModelPart linkenvisuals$breastModel;

    @Unique
    private static ModelPart linkenvisuals$breast() {
        if (linkenvisuals$breastModel == null) {
            ModelData data = new ModelData();
            ModelPartData root = data.getRoot();
            // ОДИН кубоид на сторону (не ступенчатая стопка) — без видимых «слоёв». Кубоподобные пропорции
            // (глубина ≈ ширине) + наклон вниз-вперёд читаются как гладкий скруглённый холм, а не «полка»
            // и не «пирамида». Идеально круглым модель MC не сделать (это боксы), это ближайший чистый вид.
            root.addChild("left", ModelPartBuilder.create().uv(18, 20)
                            .cuboid(-1.8F, -0.8F, -3.2F, 3.6F, 4.0F, 3.2F),
                    ModelTransform.of(-2.0F, 2.6F, -2.0F, 0.35F, 0.15F, -0.08F));
            root.addChild("right", ModelPartBuilder.create().uv(18, 20).mirrored()
                            .cuboid(-1.8F, -0.8F, -3.2F, 3.6F, 4.0F, 3.2F),
                    ModelTransform.of(2.0F, 2.6F, -2.0F, 0.35F, -0.15F, 0.08F));
            linkenvisuals$breastModel = TexturedModelData.of(data, 64, 64).createModel();
        }
        return linkenvisuals$breastModel;
    }

    @Shadow public abstract Identifier getTexture(S state);

    protected LivingEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void linkenvisuals$updateHitColorState(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            state.displayName = null;
            if (state instanceof PlayerEntityRenderState playerState) {
                playerState.playerName = null;
                playerState.playerName = null;
            }
        }

        if (!(state instanceof IHitColorRenderState hitColorState)) return;

        hitColorState.linkenvisuals$setPlayer(entity instanceof PlayerEntity);

        // Красим ТОЛЬКО тех, кого ударил локальный игрок (а не всех, кто получил урон).
        // Сглаживание считаем ПО id сущности (HitColor.advanceStrength), а не на этом render-state:
        // стейт в 1.21.x переиспользуется на все сущности типа, и сила ударенного «протекала» на
        // соседа через lerp — из-за чего краснели оба рядом стоящих. Значение по id ставим на стейт
        // ровно перед рендером этой сущности, так что и оверлей, и тинт читают верную силу.
        boolean hit = HitColor.wasHitRecently(entity.getId(), 500L) || (entity.hurtTime > 0);
        hitColorState.linkenvisuals$setHitColorStrength(HitColor.advanceStrength(entity.getId(), hit));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void linkenvisuals$captureRenderState(S state, MatrixStack matrices,
                                                  net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                                  net.minecraft.client.render.state.CameraRenderState cameraState,
                                                  CallbackInfo ci) {
        LINKENVISUALS$CURRENT_RENDER_STATE.set(state);
        LINKENVISUALS$CURRENT_QUEUE.set(queue);
        // Сброс косметической позы — БЕЗУСЛОВНО в начале рендера КАЖДОЙ сущности. Раньше clear() был
        // только в redirect на model.render, который ваниль пропускает для невидимых сущностей (зелье
        // невидимости) → стейл Наруто-наклон своего игрока утекал на броню/предмет невидимого игрока,
        // отрендеренного следом. HEAD-инжект выполняется для каждой сущности всегда.
        CosmeticPosePatch.clear();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void linkenvisuals$clearRenderState(S state, MatrixStack matrices,
                                                net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                                net.minecraft.client.render.state.CameraRenderState cameraState,
                                                CallbackInfo ci) {
        LINKENVISUALS$CURRENT_RENDER_STATE.remove();
        LINKENVISUALS$CURRENT_QUEUE.remove();
    }

    /**
     * Raven/parrot pet: submitted during the vanilla entity pass, because the AFTER_ENTITIES queue
     * is no longer drained on 1.21.11. Uses the local player's matrix as the frame origin.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void linkenvisuals$submitPet(S state, MatrixStack matrices,
                                         net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                         net.minecraft.client.render.state.CameraRenderState cameraState,
                                         CallbackInfo ci) {
        if (mc == null || mc.player == null || !(state instanceof PlayerEntityRenderState ps)) return;
        if (ps.id != mc.player.getId()) return;
        ru.method.linkenvisuals.modules.impl.render.cosmetic.Raven pet =
                ru.method.linkenvisuals.modules.impl.render.cosmetic.Raven.instance();
        if (pet != null) pet.submitLocalPet(queue, matrices, ps, cameraState, 15728880);
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
            ),
            index = 3
    )
    private int linkenvisuals$hitColorOverlay(int originalOverlay) {
        @SuppressWarnings("unchecked")
        S state = (S) LINKENVISUALS$CURRENT_RENDER_STATE.get();
        if (state == null) return originalOverlay;

        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return originalOverlay;

        HitColor module = instance.getModuleManager().getModule(HitColor.class);
        if (module == null || !module.isToggled()) return originalOverlay;

        if (!(state instanceof IHitColorRenderState hitColorState)) return originalOverlay;

        float strength = MathHelper.clamp(hitColorState.linkenvisuals$getHitColorStrength(), 0.0F, 1.0F);
        if (strength <= 0.001F) return originalOverlay;

        return OverlayTexture.DEFAULT_UV;
    }

    @Inject(
            method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private void linkenvisuals$hitColorMixColor(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValueI();
        int tinted = HitColorRenderHelper.tint(state, original, false);
        if (tinted != original) {
            cir.setReturnValue(tinted);
        }
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
            ),
            index = 4
    )
    private int linkenvisuals$hitColorTint(int originalColor) {
        @SuppressWarnings("unchecked")
        S state = (S) LINKENVISUALS$CURRENT_RENDER_STATE.get();
        if (state == null) return originalColor;

        return HitColorRenderHelper.tint(state, originalColor, false);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
            )
    )
    private void linkenvisuals$cosmeticPoseRedirect(EntityModel<?> instance,
                                                     MatrixStack matrixStack,
                                                     VertexConsumer vertexConsumer,
                                                     int light,
                                                     int overlay,
                                                     int color,
                                                     @Local(ordinal = 0, argsOnly = true) LivingEntityRenderState renderState) {
        color = HitColorRenderHelper.tint(renderState, color, false);
        // Снимок косметической позы обнуляем для КАЖДОГО рендера сущности. Если ниже применится
        // Наруто-наклон — заполним его, и аттачменты (шапка/крылья) этой сущности повторят наклон.
        CosmeticPosePatch.clear();

        boolean pushed = false;

        // Rudy character replaces the vanilla player mesh. The queue is captured in render HEAD;
        // submit returns false when the mesh is unavailable so the normal model still draws.
        if (instance instanceof PlayerEntityModel rudyModel
                && renderState instanceof PlayerEntityRenderState rudyState
                && dev.linkenvisuals.cosmetic.character.RudyCharacter.active(rudyState)) {
            var queue = LINKENVISUALS$CURRENT_QUEUE.get();
            if (queue != null) {
                dev.linkenvisuals.cosmetic.character.RudyCharacter.submit(
                        queue, matrixStack, rudyModel, rudyState, light, overlay, color);
                return;
            }
        }

        if (mc != null && mc.player != null && instance instanceof PlayerEntityModel model && renderState instanceof PlayerEntityRenderState ps) {
            if (ps.id == mc.player.getId()) {
                // По умолчанию — нет наклона у своего игрока в этом кадре; включим ниже, если поза применится.
                CosmeticPosePatch.clearSelf();

                if (Naruto.enabled()) {
                    float speed = (float) mc.player.getVelocity().horizontalLength();
                    float run = MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);
                    // Даже стоя Naruto-стойка должна читаться: даём минимальный наклон/руки,
                    // а при движении плавно усиливаем до полной анимации бега.
                    float pose = Math.max(run, 0.22F);

                    if (!ps.isSwimming && !ps.isGliding && !ps.isUsingItem && !ps.isInSneakingPose) {
                        PlayerEntityModel pm = model;

                        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
                        float tt = (mc.player.age + tickDelta) * 0.6F;
                        float swing = MathHelper.cos(tt) * 0.20F * run;
                        float armBack = 1.25F + 0.45F * pose + swing;
                        float armRoll = 0.55F * pose;
                        float headDown = -0.15F * pose;
                        float bodyLean = 0.45F * pose;

                        pm.rightArm.pitch = armBack;
                        pm.leftArm.pitch = armBack;
                        pm.rightArm.roll = armRoll;
                        pm.leftArm.roll = -armRoll;
                        pm.rightArm.yaw = pm.rightArm.yaw + 0.10F * pose;
                        pm.leftArm.yaw = pm.leftArm.yaw - 0.10F * pose;
                        pm.head.pitch = pm.head.pitch + headDown;

                        float pivotY = 0.75F;
                        matrixStack.push();
                        pushed = true;
                        matrixStack.translate(0.0F, pivotY, 0.0F);
                        matrixStack.multiply(RotationAxis.POSITIVE_X.rotation(bodyLean));
                        matrixStack.translate(0.0F, -pivotY, 0.0F);
                        // Тот же наклон + доворот головы — для аттачментов (шапка/крылья) и мировых
                        // эффектов (ChinaHat/нимб читают selfHeadDeltaPitch, чтобы следовать за головой).
                        CosmeticPosePatch.set(pivotY, bodyLean, headDown);
                        CosmeticPosePatch.setSelf(pivotY, bodyLean, headDown);
                    }
                }
            }

            // Drochka — на ВСЕХ игроков (в т.ч. чужих): правая рука к паху + быстрое дёрганье кистью.
            // Стоит ВНЕ гейта «только свой игрок», поэтому применяется к каждому рендеру игрока.
            if (Drochka.active() && !ps.isSwimming && !ps.isGliding && !ps.isUsingItem && !ps.isInSneakingPose) {
                PlayerEntityModel pm = model;
                float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
                float tt = mc.player.age + tickDelta;
                float phase = (ps.id % 23) * 0.27F;                 // рассинхрон дёрганья между игроками
                float jerk = MathHelper.sin(tt * Drochka.frequency() + phase) * Drochka.amplitude();
                // правая рука: roll ОТРИЦАТЕЛЬНЫЙ — кисть идёт ВНУТРЬ к центру (к паху), а не наружу;
                // pitch слегка вперёд (кисть перед пахом) + пампинг по pitch (дёрганье).
                pm.rightArm.pitch = -0.30F + jerk;
                pm.rightArm.yaw = 0.0F;
                pm.rightArm.roll = -0.55F;
                if (Drochka.bothHands()) {
                    pm.leftArm.pitch = -0.30F - jerk;
                    pm.leftArm.yaw = 0.0F;
                    pm.leftArm.roll = 0.55F;   // левая рука зеркально — внутрь к центру
                }
            }

            // Объёмная грудь — в локальном пространстве тела (следует за наклоном/присядом), тем же
            // vertexConsumer (скин игрока). Рисуем ДО body — порядок для непрозрачного слоя не важен.
            if (Drochka.breast()) {
                matrixStack.push();
                model.body.applyTransform(matrixStack);
                linkenvisuals$breast().render(matrixStack, vertexConsumer, light, overlay, color);
                matrixStack.pop();
            }
        }

        try {
            instance.render(matrixStack, vertexConsumer, light, overlay, color);
        } finally {
            if (pushed) {
                matrixStack.pop();
            }
            // Углы модели (руки/голову/тело) НЕ восстанавливаем: фичи после модели копируют их —
            // предмет следует за поднятой рукой (ItemInHandFeatureRenderer берёт трансформ руки),
            // броня повторяет позу через BipedEntityModel.copyTransforms (шлем — доворот головы,
            // наручи — Наруто-руки). Наклон корпуса для фич добавляют Item/Armor-миксины по
            // CosmeticPosePatch. Углы перезадаются setAngles каждый кадр — утечки между кадрами нет.
        }
    }

    /**
     * Naruto: 1.21.11 submits the player model to {@link OrderedRenderCommandQueue}; the matrix
     * captured by submitModel is where the body lean has to be applied (the old
     * {@code EntityModel.render} redirect never fires on this renderer).
     */
    @WrapOperation(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
            )
    )
    private void linkenvisuals$narutoPose(OrderedRenderCommandQueue queue, Model<?> model, Object state,
                                          MatrixStack matrices, RenderLayer layer, int light, int overlay,
                                          int color, Sprite sprite, int outlineColor,
                                          ModelCommandRenderer.CrumblingOverlayCommand crumbling,
                                          Operation<Void> original) {
        boolean pushed = false;
        if (mc != null && mc.player != null && state instanceof PlayerEntityRenderState ps
                && ps.id == mc.player.getId()) {
            if (Naruto.enabled() && !ps.isSwimming && !ps.isGliding && !ps.isUsingItem && !ps.isInSneakingPose) {
                float speed = (float) mc.player.getVelocity().horizontalLength();
                float pose = Boolean.getBoolean("linkenvisuals.naruto.force")
                        ? 1.0F : MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);
                if (pose >= 0.10F) {
                    float bodyLean = 0.45F * pose;
                    float headDown = -0.15F * pose;
                    matrices.push();
                    pushed = true;
                    matrices.translate(0.0F, 0.75F, 0.0F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotation(bodyLean));
                    matrices.translate(0.0F, -0.75F, 0.0F);
                    CosmeticPosePatch.set(0.75F, bodyLean, headDown);
                    CosmeticPosePatch.setSelf(0.75F, bodyLean, headDown);
                } else {
                    CosmeticPosePatch.clearSelf();
                }
            } else {
                CosmeticPosePatch.clearSelf();
            }
        }
        int finalOverlay = overlay;
        int finalColor = color;
        if (state instanceof LivingEntityRenderState les) {
            int tinted = HitColorRenderHelper.tint(les, color, false);
            if (tinted != color) {
                finalColor = tinted;
                finalOverlay = OverlayTexture.DEFAULT_UV;
            }
        }
        try {
            original.call(queue, model, state, matrices, layer, light, finalOverlay, finalColor, sprite, outlineColor, crumbling);
        } finally {
            if (pushed) {
                matrices.pop();
            }
        }
    }

    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void viewOwnLabel(LivingEntity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            cir.setReturnValue(false);
            return;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return;
        SelfNametags module = instance.getModuleManager().getModule(SelfNametags.class);
        if (module == null || !module.isToggle()) return;
        if (entity == mc.player) {
            cir.setReturnValue(net.minecraft.client.MinecraftClient.isHudEnabled());
        }
    }
}
