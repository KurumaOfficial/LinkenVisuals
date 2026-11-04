package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.modules.impl.render.Drochka;
import ru.method.linkenvisuals.modules.impl.render.cosmetic.Naruto;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.cosmetics.character.RudyCharacter;

@Mixin(PlayerEntityModel.class)
public class CharacterPlayerModelMixin implements ru.method.linkenvisuals.api.accessors.ICharacterModel {
    @Unique private boolean linkenvisuals$rudy;

    public boolean linkenvisuals$isRudyModel() { return linkenvisuals$rudy; }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void linkenvisuals$characterPose(PlayerEntityRenderState state, CallbackInfo ci) {
        linkenvisuals$rudy = RudyCharacter.active(state);
        if (linkenvisuals$rudy) RudyCharacter.pose((PlayerEntityModel) (Object) this, state);

        // Drochka: 1.21.11 no longer calls EntityModel.render directly from
        // LivingEntityRenderer, so the old redirect never fired. Pose the arms here,
        // at the end of vanilla setAngles, which is called for every player render.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || state == null || state.id != client.player.getId()) return;
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        boolean canPose = !state.isSwimming && !state.isGliding && !state.isUsingItem && !state.isInSneakingPose;
        if (!canPose) return;

        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        if (Drochka.active()) {
            float tt = client.player.age + tickDelta;
            float phase = (state.id % 23) * 0.27F;
            float jerk = MathHelper.sin(tt * Drochka.frequency() + phase) * Drochka.amplitude();
            model.rightArm.pitch = -0.30F + jerk;
            model.rightArm.yaw = 0.0F;
            model.rightArm.roll = -0.55F;
            if (Drochka.bothHands()) {
                model.leftArm.pitch = -0.30F - jerk;
                model.leftArm.yaw = 0.0F;
                model.leftArm.roll = 0.55F;
            }
        }

        // Naruto: the 1.21.11 renderer submits the player model to OrderedRenderCommandQueue,
        // so the old EntityModel.render redirect never fires. Pose the arms/head here, at the
        // tail of setAngles, which the renderer calls for the model and the feature pass.
        if (Naruto.enabled()) {
            float speed = (float) client.player.getVelocity().horizontalLength();
            float run = Boolean.getBoolean("linkenvisuals.naruto.force")
                    ? 1.0F : MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);
            if (run < 0.10F) return;
            float pose = run;
            float tt = (client.player.age + tickDelta) * 0.6F;
            float swing = MathHelper.cos(tt) * 0.20F * run;
            float armBack = 1.25F + 0.45F * pose + swing;
            float armRoll = 0.55F * pose;
            float headDown = -0.15F * pose;

            model.rightArm.pitch = armBack;
            model.leftArm.pitch = armBack;
            model.rightArm.roll = armRoll;
            model.leftArm.roll = -armRoll;
            model.rightArm.yaw = model.rightArm.yaw + 0.10F * pose;
            model.leftArm.yaw = model.leftArm.yaw - 0.10F * pose;
            model.head.pitch = model.head.pitch + headDown;
        }
    }

    @Inject(method = "setArmAngle", at = @At("TAIL"))
    private void linkenvisuals$wingGrip(PlayerEntityRenderState state, Arm arm, MatrixStack matrices, CallbackInfo ci) {
        if (linkenvisuals$rudy) {
            // Vanilla grip is 10px below the shoulder; Rudy's wing is only about 6px long.
            matrices.translate(arm == Arm.RIGHT ? .0625f : -.0625f, -.25f, -.10f);
        }
    }
}
