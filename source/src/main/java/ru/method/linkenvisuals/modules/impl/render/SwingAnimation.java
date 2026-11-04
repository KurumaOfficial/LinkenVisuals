package ru.method.linkenvisuals.modules.impl.render;


import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;


public class SwingAnimation extends Module {

    private static final float SCALE = 0.5f;

    private final EnumSetting<Mode> mode = new EnumSetting<>("settings.swinganimation.mode", Mode.CLASSIC);
    private final NumberSetting swingPower = new NumberSetting("settings.swinganimation.power", 5.0f, 1.0f, 10.0f, 0.05f);

    public SwingAnimation() {
        super("SwingAnimation", Category.Visuals);
    }

    public void renderSwordAnimation(MatrixStack matrices, float swingProgress, float equipProgress, Arm arm) {
        Mode currentMode = mode.getValue();
        float power = swingPower.getValue();
        float anim = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        int direction = arm == Arm.RIGHT ? 1 : -1;

        switch (currentMode) {
            case CLASSIC -> {
                matrices.translate(direction * 0.56F, -0.52F, -0.72F);
                float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -60.0F * power / 5.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * g * -30.0F * power / 5.0f));
                return;
            }
            case RIPOSTE -> {
                if (swingProgress > 0) {
                    float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    matrices.translate(direction * 0.56F, equipProgress * -0.2f - 0.5F, -0.7F);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * 45));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -85.0F * power / 5.0f));
                    matrices.translate(direction * -0.1F, 0.28F, 0.2F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-85.0F));
                } else {
                    float offset = -0.4f * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                    float vertical = 0.2f * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI * 2);
                    float depth = -0.2f * MathHelper.sin(swingProgress * (float) Math.PI);
                    matrices.translate(direction * offset, vertical, depth);
                    applyEquipOffset(matrices, arm, equipProgress);
                    applySwingOffset(matrices, arm, swingProgress);
                }
                return;
            }
            case LUNGE -> {
                float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * -60f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(direction * (110f + 20f * g * power / 5.0f)));
                return;
            }
            case CLEAVE -> {
                float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * (-30f * (1f - g) - 30f)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(direction * 110f));
                return;
            }
            case WHIRL -> {
                float swingCurve = MathHelper.sin(swingProgress * (float) Math.PI);
                float boosted = power / 5.0f;

                applyEquipOffset(matrices, arm, equipProgress);
                matrices.translate(0.0f, -0.28f + swingCurve * -0.18f, -0.48f + swingCurve * -0.12f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * (95.0f * swingCurve * boosted)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-110.0f * swingCurve * boosted - 15.0f * equipProgress));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(direction * (35.0f * swingCurve)));
                matrices.scale(0.9f + 0.08f * swingCurve, 0.92f + 0.05f * swingCurve, 0.9f);
                return;
            }
            case CASCADE -> {
                matrices.scale(SCALE, SCALE, SCALE);
                applyEquipOffset(matrices, arm, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * (15 * anim)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-(power * 10) * anim));
                return;
            }
            case ORBIT -> {
                matrices.scale(SCALE + 0.1f, SCALE, SCALE - 0.1f);
                applyEquipOffset(matrices, arm, 0);
                matrices.translate(direction * 0.2f * anim, 0, -0.5f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * (90 * anim * power / 5.0f)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-140 * anim * power / 5.0f));
                return;
            }
        }
    }

    private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }

    private void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress) {
        int i = arm == Arm.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45.0F + f * -20.0F)));
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -20.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45.0F));
    }

    public enum Mode implements Nameable {
        CLASSIC("settings.swinganimation.mode.classic"),
        RIPOSTE("settings.swinganimation.mode.riposte"),
        LUNGE("settings.swinganimation.mode.lunge"),
        CLEAVE("settings.swinganimation.mode.cleave"),
        WHIRL("settings.swinganimation.mode.whirl"),
        CASCADE("settings.swinganimation.mode.cascade"),
        ORBIT("settings.swinganimation.mode.orbit");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
