package dev.linkenvisuals.mixin;

import dev.linkenvisuals.module.Modules;
import dev.linkenvisuals.module.impl.HitColorModule;
import dev.linkenvisuals.module.impl.MineHelperModule;
import dev.linkenvisuals.module.impl.HitSoundModule;
import dev.linkenvisuals.module.impl.KillStreakModule;
import dev.linkenvisuals.module.impl.SoundsModule;
import dev.linkenvisuals.player.FakePlayer;
import dev.linkenvisuals.player.HitEffects;
import dev.linkenvisuals.player.KillEffects;
import dev.linkenvisuals.player.TargetMarks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$hitDummy(PlayerEntity player, Entity target, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean fake = FakePlayer.hit(client, target);
        if (fake) {
            ci.cancel();
            return;
        }

        boolean crit = HitSoundModule.isCrit(player, target);
        HitSoundModule.tryPlay(client, crit);
        dev.linkenvisuals.render.DamageParticlesRenderer.onAttack(target);
        dev.linkenvisuals.render.FortniteDamageRenderer.onAttack(target, crit);
        if (target instanceof LivingEntity living) {
            SoundsModule.noteHit(living);
            KillStreakModule.noteHit(living);
        }

        TargetMarks.noteHit(target);

        HitColorModule.markHit(target.getId());
        ru.method.linkenvisuals.modules.impl.render.HitColor.markHit(target.getId());

        KillEffects.watch(target);

        Vec3d impact = client.crosshairTarget instanceof EntityHitResult hit
            && hit.getEntity() == target ? hit.getPos() : null;
        HitEffects.add(target, player.getAttackCooldownProgress(0.5f), impact);
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$mineHelper(BlockPos pos, Direction direction,
                                            CallbackInfoReturnable<Boolean> cir) {
        MineHelperModule module = Modules.mineHelper();
        if (module != null && module.shouldCancelBreak()) {
            cir.setReturnValue(false);
        }
    }
}
