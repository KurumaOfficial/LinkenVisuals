package ru.method.linkenvisuals.cosmetics.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.bedrock.MoreCosmeticsTransforms;
import ru.method.linkenvisuals.cosmetics.bedrock.BedrockBonePose;
import ru.method.linkenvisuals.cosmetics.bedrock.BedrockModel;
import ru.method.linkenvisuals.cosmetics.bedrock.BedrockModelRenderer;
import ru.method.linkenvisuals.cosmetics.bedrock.CosmeticModelRepository;
import ru.method.linkenvisuals.cosmetics.bedrock.CosmeticMotionSmoother;
import ru.method.linkenvisuals.cosmetics.bedrock.MoreCosmeticsAnimationPlayer;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import net.minecraft.util.math.MathHelper;

public final class CosmeticRenderHelper {
    private static final CosmeticMotionSmoother MOTION_SMOOTHER = new CosmeticMotionSmoother();
    private static long lastSmoothNanos;
    // База отсчёта анимации. Нужна, чтобы время анимации было МАЛЕНЬКИМ числом: float имеет ~7 значащих
    // цифр, и если кормить в анимацию System.currentTimeMillis()/1000 (~1.7e9), прибавка 1/60 с теряется
    // → анимация «застывает». Считаем секунды от старта мода — точность высокая, анимация плавная.
    private static final long ANIM_EPOCH_NANOS = System.nanoTime();
    // Множитель скорости анимации косметики (1.0 = реальное время). Уменьшаем — крылья машут спокойнее.
    private static final float ANIM_SPEED = 0.5f;

    private CosmeticRenderHelper() {
    }

    public static void renderAttachment(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            PlayerEntityModel playerModel,
            CosmeticDefinition definition,
            CosmeticSlot slot,
            float limbDistance,
            boolean entityMoving,
            java.util.UUID ownerUuid,
            String ownerName
    ) {
        renderAttachment(matrices, vertexConsumers, light, playerModel, definition, slot, limbDistance, entityMoving, 0f, 0f, ownerUuid, ownerName);
    }

    public static void renderAttachment(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            PlayerEntityModel playerModel,
            CosmeticDefinition definition,
            CosmeticSlot slot,
            float limbDistance,
            boolean entityMoving,
            float headLift,
            float extraHeadPitch,
            java.util.UUID ownerUuid,
            String ownerName
    ) {
        if (definition == null || !definition.hasModel()) {
            return;
        }

        BedrockModel model = CosmeticModelRepository.get(definition.modelAsset());
        if (model == null || model.isEmpty()) {
            return;
        }

        float deltaSeconds = resolveDeltaSeconds();
        float animationTime = resolveAnimationTime();
        float movingBlend = resolveMovingBlend(limbDistance, deltaSeconds, entityMoving);

        BedrockBonePose bonePose = new BedrockBonePose();
        MoreCosmeticsAnimationPlayer.apply(bonePose, model, animationTime, movingBlend);

        int tint = resolveTint(slot, ownerUuid, ownerName);
        matrices.push();
        try {
            MoreCosmeticsTransforms.apply(matrices, playerModel, model, headLift, extraHeadPitch, slot);
            BedrockModelRenderer.render(model, matrices, vertexConsumers, light, tint, bonePose);
        } finally {
            matrices.pop();
        }
    }

    public static void renderAttachment(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityModel playerModel,
            CosmeticDefinition definition,
            CosmeticSlot slot,
            float limbDistance,
            boolean entityMoving,
            float headLift,
            float extraHeadPitch,
            java.util.UUID ownerUuid,
            String ownerName
    ) {
        if (definition == null || !definition.hasModel() || queue == null) {
            return;
        }

        BedrockModel model = CosmeticModelRepository.get(definition.modelAsset());
        if (model == null || model.isEmpty()) {
            return;
        }

        float deltaSeconds = resolveDeltaSeconds();
        float animationTime = resolveAnimationTime();
        float movingBlend = resolveMovingBlend(limbDistance, deltaSeconds, entityMoving);

        BedrockBonePose bonePose = new BedrockBonePose();
        MoreCosmeticsAnimationPlayer.apply(bonePose, model, animationTime, movingBlend);

        int tint = resolveTint(slot, ownerUuid, ownerName);
        matrices.push();
        try {
            MoreCosmeticsTransforms.apply(matrices, playerModel, model, headLift, extraHeadPitch, slot);
            BedrockModelRenderer.render(model, matrices, queue, light, tint, bonePose);
        } finally {
            matrices.pop();
        }
    }

    private static float resolveDeltaSeconds() {
        CosmeticPreviewState.Snapshot preview = CosmeticPreviewState.current();
        if (preview != null) {
            return preview.deltaSeconds();
        }
        long now = System.nanoTime();
        if (lastSmoothNanos == 0L) {
            lastSmoothNanos = now;
            return 1f / 60f;
        }
        float delta = (now - lastSmoothNanos) / 1_000_000_000f;
        lastSmoothNanos = now;
        return MathHelper.clamp(delta, 1f / 240f, 0.1f);
    }

    private static float resolveMovingBlend(float limbDistance, float deltaSeconds, boolean entityMoving) {
        CosmeticPreviewState.Snapshot preview = CosmeticPreviewState.current();
        if (preview != null) {
            return preview.movingBlend();
        }
        MOTION_SMOOTHER.update(entityMoving, limbDistance, deltaSeconds, false);
        return MOTION_SMOOTHER.movingBlend();
    }

    private static float resolveAnimationTime() {
        CosmeticPreviewState.Snapshot preview = CosmeticPreviewState.current();
        if (preview != null) {
            return preview.animationTimeSeconds();
        }
        // Анимация по монотонным часам ОТ СТАРТА МОДА (не по world.getTime() — на серверах время мира
        // приходит рывками → дёрганье; и не по абсолютному millis — теряется точность float). Маленькое
        // число секунд от старта → высокая точность float → плавная анимация при любом FPS и лаге сервера.
        return (System.nanoTime() - ANIM_EPOCH_NANOS) / 1_000_000_000f * ANIM_SPEED;
    }

    /** Тинт слота ПО ВЛАДЕЛЬЦУ косметики (не по локальному игроку — иначе чужие крылья
     *  красились цветом смотрящего, и у всех игроков цвет выглядел одинаковым). */
    private static int resolveTint(CosmeticSlot slot, java.util.UUID ownerUuid, String ownerName) {
        if (slot != CosmeticSlot.WINGS && slot != CosmeticSlot.EFFECT && slot != CosmeticSlot.HAT) {
            // Models with authored textures (weapons, pets, masks, body cosmetics) must retain
            // their native palette; the theme tint only applies to tint-enabled slots.
            return 0xFFFFFFFF;
        }
        CosmeticPreviewState.Snapshot preview = CosmeticPreviewState.current();
        if (preview != null) {
            return preview.profile().getTint(slot);
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) {
            return CosmeticProfile.DEFAULT_TINT;
        }
        CosmeticsService service = instance.getCosmeticsService();
        if (service == null) {
            return CosmeticProfile.DEFAULT_TINT;
        }
        if (ownerUuid == null && (ownerName == null || ownerName.isBlank())) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                return CosmeticProfile.DEFAULT_TINT;
            }
            return service.resolveTint(slot, client.player.getUuid(), client.player.getGameProfile().name());
        }
        return service.resolveTint(slot, ownerUuid, ownerName);
    }
}
