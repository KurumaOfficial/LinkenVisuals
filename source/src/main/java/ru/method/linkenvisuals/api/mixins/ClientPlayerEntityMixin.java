package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.rotations.EventMotion;
import ru.method.linkenvisuals.api.events.impl.rotations.EventTrace;
import ru.method.linkenvisuals.api.events.impl.EventPlayerTick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Unique private final EventMotion event = new EventMotion(0f, 0f);

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        EventPlayerTick event = new EventPlayerTick();
        LinkenVisuals.getInstance().getEventHandler().post(event);
    }

    

    @ModifyExpressionValue(method = "shouldStopSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    public boolean shouldStopSprinting(boolean original) {
        
        return original;
    }

    /**
     * 1.21.11 moved the crosshair raycast from {@code GameRenderer} into
     * {@code ClientPlayerEntity.getCrosshairTarget}. The rotation event is
     * applied to the ray vector exactly like the donor did.
     */
    @WrapOperation(
            method = "getCrosshairTarget(Lnet/minecraft/entity/Entity;DDF)Lnet/minecraft/util/hit/HitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getRotationVec(F)Lnet/minecraft/util/math/Vec3d;"),
            require = 0
    )
    private static Vec3d linkenvisuals$traceRotation(Entity instance, float tickDelta, Operation<Vec3d> original) {
        if (instance == MinecraftClient.getInstance().player) {
            EventTrace event = new EventTrace(instance.getYaw(), instance.getPitch());
            LinkenVisuals.getInstance().getEventHandler().post(event);
            if (event.isCancelled()) {
                return instance.getRotationVector(event.getPitch(), event.getYaw());
            }
        }
        return original.call(instance, tickDelta);
    }
}
