package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.rotations.EventTrace;
import ru.method.linkenvisuals.api.events.impl.rotations.EventTravel;
import ru.method.linkenvisuals.utils.Wrapper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin implements Wrapper {

    // @WrapOperation вместо @Redirect — уживаемся с Baritone/мувмент-модами, которые редиректят те же
    // rotation-вызовы (иначе required-@Redirect падает «0 targets» при конфликте). См. LivingEntityMixin.
    @WrapOperation(method = "raycast", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getRotationVec(F)Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d raycast(Entity instance, float tickDelta, Operation<Vec3d> original) {
        if (instance == mc.player) {
            EventTrace event = new EventTrace(instance.getYaw(), instance.getPitch());
            LinkenVisuals.getInstance().getEventHandler().post(event);
            if (event.isCancelled()) return instance.getRotationVector(instance.getPitch(), instance.getYaw());
        }

        return original.call(instance, tickDelta);
    }

    @WrapOperation(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    public float updateVelocity(Entity instance, Operation<Float> original) {
        if (instance == mc.player) {
            EventTravel event = new EventTravel(instance.getYaw(), instance.getPitch());
            LinkenVisuals.getInstance().getEventHandler().post(event);
            return event.getYaw();
        }

        return original.call(instance);
    }
}
