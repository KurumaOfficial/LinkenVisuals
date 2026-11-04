package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.rotations.EventJump;
import ru.method.linkenvisuals.api.events.impl.rotations.EventTravel;
import ru.method.linkenvisuals.utils.Wrapper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements Wrapper {

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    // @WrapOperation вместо @Redirect: тот же getYaw() в jump() редиректит и Baritone (overrideYaw).
    // Два @Redirect на один call-site несовместимы → наш required-редирект падал с «0 targets» (краш у
    // любого с установленным Baritone). WrapOperation КОМПОНУЕТСЯ: наш враппер зовёт original (= редирект
    // Baritone или реальный getYaw), поэтому оба мода работают вместе.
    @WrapOperation(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    public float jump(LivingEntity instance, Operation<Float> original) {
        float yaw = original.call(instance);
        if (instance == mc.player) {
            EventJump event = new EventJump(yaw);
            LinkenVisuals.getInstance().getEventHandler().post(event);
            return event.getYaw();
        }
        return yaw;
    }

    // Тоже WrapOperation — этот вызов редиректит и мод rich; так уживаемся с ним, не ломая его инъекцию.
    @WrapOperation(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d calcGlidingVelocity(LivingEntity instance, Operation<Vec3d> original) {
        if (instance == mc.player) {
            EventTravel event = new EventTravel(instance.getYaw(), instance.getPitch());
            LinkenVisuals.getInstance().getEventHandler().post(event);
            return getRotationVector(event.getPitch(), event.getYaw());
        }
        return original.call(instance);
    }
}
