package ru.method.linkenvisuals.cosmetics.bedrock;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import ru.method.linkenvisuals.cosmetics.model.CosmeticAnchor;

/**
 * Attachment transforms ported from MoreCosmetics {@code ModelCosmeticRenderer}
 * (transformToEntity / transformModel / rotateZ(180)).
 */
public final class MoreCosmeticsTransforms {
    private static final float SCALE = 0.0625f;
    private static final float RAD_TO_DEG = 57.295776f;

    private MoreCosmeticsTransforms() {
    }

    public static void apply(MatrixStack matrices, PlayerEntityModel playerModel, BedrockModel model) {
        apply(matrices, playerModel, model, 0f, 0f);
    }

    public static void apply(MatrixStack matrices, PlayerEntityModel playerModel, BedrockModel model, float headLift) {
        apply(matrices, playerModel, model, headLift, 0f);
    }

    /**
     * {@code headLift} поднимает HEAD-аттачмент (шапку), чтобы она села поверх надетого шлема.
     * {@code extraHeadPitch} (радианы) — дополнительный наклон головы (например при Наруто), чтобы
     * шапка следовала за довёрнутой головой.
     */
    public static void apply(MatrixStack matrices, PlayerEntityModel playerModel, BedrockModel model, float headLift, float extraHeadPitch) {
        apply(matrices, playerModel, model, headLift, extraHeadPitch, null);
    }

    public static void apply(MatrixStack matrices, PlayerEntityModel playerModel, BedrockModel model, float headLift,
                             float extraHeadPitch, ru.method.linkenvisuals.cosmetics.model.CosmeticSlot slot) {
        float anchorYOffset = transformToEntity(model.anchor(), playerModel, matrices, extraHeadPitch);
        if (playerModel instanceof ru.method.linkenvisuals.api.accessors.ICharacterModel character
                && character.linkenvisuals$isRudyModel() && slot != null) {
            // Local coordinates after the animated anchor: broad head, short torso and wings.
            switch (slot) {
                case HAT -> matrices.scale(1.3f, 1f, 1.3f);
                case HEAD -> {
                    matrices.translate(0f, .42f, -.02f);
                    matrices.scale(1.65f, 1f, 1.65f);
                }
                case WINGS -> matrices.translate(0f, -.55f, .35f);
                case BODY -> {
                    matrices.translate(0f, -.60f, 0f);
                    matrices.scale(1.8f, 1.15f, 2.6f);
                }
                case SHIELD -> matrices.translate(0f, -.05f, -.10f);
                default -> { }
            }
        }
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
        if (model.anchor() == CosmeticAnchor.LEFT_ARM) {
            // A small local-space clearance keeps shields on the forearm without intersecting it.
            matrices.translate(-0.10f, 0f, 0f);
        }
        float extra = model.anchor() == CosmeticAnchor.HEAD ? headLift : 0f;
        transformModel(model, anchorYOffset + extra, matrices);
    }

    private static float transformToEntity(CosmeticAnchor anchor, PlayerEntityModel playerModel, MatrixStack matrices, float extraHeadPitch) {
        if (playerModel == null) {
            return 0f;
        }
        return switch (anchor) {
            case HEAD -> {
                transformToModelPart(playerModel.head, matrices, extraHeadPitch);
                yield 0.5f;
            }
            case BODY -> {
                transformToModelPart(playerModel.body, matrices, 0f);
                yield -0.3f;
            }
            case LEFT_ARM -> {
                transformToModelPart(playerModel.leftArm, matrices, 0f);
                yield -0.25f;
            }
            case RIGHT_ARM -> {
                transformToModelPart(playerModel.rightArm, matrices, 0f);
                yield -0.25f;
            }
            case PET -> 0f;
        };
    }

    private static void transformToModelPart(ModelPart part, MatrixStack matrices, float extraPitch) {
        if (part == null) {
            return;
        }
        matrices.translate(part.originX * SCALE, part.originY * SCALE, part.originZ * SCALE);
        rotateDegrees(matrices, (part.pitch + extraPitch) * RAD_TO_DEG, part.yaw * RAD_TO_DEG, part.roll * RAD_TO_DEG);
    }

    private static void transformModel(BedrockModel model, float anchorYOffset, MatrixStack matrices) {
        matrices.translate(
                model.offsetX(),
                model.offsetY() + anchorYOffset,
                model.offsetZ()
        );
        if (model.yaw() != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(model.yaw()));
        }
        if (model.pitch() != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(model.pitch()));
        }
        if (model.roll() != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(model.roll()));
        }
        float scale = model.scale();
        matrices.scale(scale, scale, scale);
    }

    /** MoreCosmetics {@code RenderStack.rotate}: Z, then Y, then X in degrees. */
    private static void rotateDegrees(MatrixStack matrices, float pitch, float yaw, float roll) {
        if (roll != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        }
        if (yaw != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        }
        if (pitch != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        }
    }
}
