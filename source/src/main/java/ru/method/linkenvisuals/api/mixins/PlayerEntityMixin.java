package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.rotations.EventTravel;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.modules.impl.render.SelfNametags;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;
import ru.method.linkenvisuals.utils.text.SyncedUserTextUtils;
import ru.method.linkenvisuals.utils.Wrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements Wrapper {

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    // @WrapOperation вместо @Redirect: travel — метод движения, его вызовы редиректит и Baritone. Иначе
    // required-@Redirect падает «0 targets» при конфликте (ровно как было с LivingEntityMixin.jump).
    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d travel(PlayerEntity instance, Operation<Vec3d> original) {
        if (instance == mc.player) {
            EventTravel event = new EventTravel(getYaw(), getPitch());
            LinkenVisuals.getInstance().getEventHandler().post(event);
            return getRotationVector(event.getPitch(), event.getYaw());
        }

        return original.call(instance);
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$streamerDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (CosmeticPreviewState.suppressesEntityLabel() && client != null && player == client.player) {
            cir.setReturnValue(Text.empty());
            return;
        }
        Text result = SyncedUserTextUtils.applyBadgeForPlayer(
                cir.getReturnValue(),
                player.getGameProfile().id(),
                player.getGameProfile().name()
        );
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && client != null && player == client.player) {
            SelfNametags selfNametags = instance.getModuleManager().getModule(SelfNametags.class);
            boolean selfNametagsEnabled = selfNametags != null && selfNametags.isToggle();
            if (selfNametagsEnabled) {
                result = StreamerModeUtils.sanitizeSelfNametag(result);
            }
        }
        cir.setReturnValue(result);
    }
}
