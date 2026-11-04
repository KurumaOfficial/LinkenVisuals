package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.CameraSettings;
import ru.method.linkenvisuals.modules.impl.render.Freelook;

@Mixin(Camera.class)
public abstract class CameraMixin {

    // Плавный F5: масштабируем запрошенную дистанцию 3-го лица (Camera.clipToSpace(F)) на долю анимации.
    // require = 0 — если сигнатура разъедется на другой сборке, фича просто не применится, без краша.
    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"), index = 0, require = 0)
    private float linkenvisuals$smoothF5(float requestedDistance) {
        CameraSettings cs = LinkenVisuals.getInstance().getModuleManager().getModule(CameraSettings.class);
        if (cs == null) return requestedDistance;
        float scale = cs.getCameraDistanceScale();
        return scale >= 1.0f ? requestedDistance : requestedDistance * scale;
    }

    // @WrapOperation вместо @Redirect — камерные getYaw/getPitch редиректят и другие моды (freelook/
    // shoulder-surfing/replaymod); компонуемся вместо конфликта required-@Redirect.
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float linkenvisuals$useFreelookYaw(Entity entity, float tickDelta, Operation<Float> original) {
        Freelook freelook = LinkenVisuals.getInstance().getModuleManager().getModule(Freelook.class);
        if (freelook != null && freelook.shouldOverrideCamera(entity)) {
            return freelook.getCameraYaw();
        }
        return original.call(entity, tickDelta);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float linkenvisuals$useFreelookPitch(Entity entity, float tickDelta, Operation<Float> original) {
        Freelook freelook = LinkenVisuals.getInstance().getModuleManager().getModule(Freelook.class);
        if (freelook != null && freelook.shouldOverrideCamera(entity)) {
            return freelook.getCameraPitch();
        }
        return original.call(entity, tickDelta);
    }
}
