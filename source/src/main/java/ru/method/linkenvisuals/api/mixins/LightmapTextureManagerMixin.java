package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.Fullbright;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

    @org.spongepowered.asm.mixin.Shadow
    private boolean dirty;

    @Unique
    private static boolean linkenvisuals$wasActive = false;

    @org.spongepowered.asm.mixin.injection.Inject(method = "update", at = @At("HEAD"))
    private void linkenvisuals$forceDirty(float delta, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        boolean active = linkenvisuals$isFullbrightActive();
        if (active != linkenvisuals$wasActive) {
            linkenvisuals$wasActive = active;
            this.dirty = true;
        } else if (active) {
            this.dirty = true;
        }
    }

    @Unique
    private static boolean linkenvisuals$isFullbrightActive() {
        LinkenVisuals ruInst = LinkenVisuals.getInstance();
        if (ruInst != null && ruInst.getModuleManager() != null) {
            Fullbright mod = ruInst.getModuleManager().getModule(Fullbright.class);
            if (mod != null && mod.isToggled()) return true;
        }
        dev.linkenvisuals.module.impl.FullbrightModule devMod =
                dev.linkenvisuals.module.Modules.fullbright();
        return devMod != null && devMod.enabled();
    }

    @WrapOperation(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;ambientLight()F")
    )
    private float modifyAmbientLight(DimensionType instance, Operation<Float> original) {
        if (linkenvisuals$isFullbrightActive()) {
            return 1.0f;
        }
        return original.call(instance);
    }

    @WrapOperation(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;")
    )
    private Object modifyOptionValue(SimpleOption<?> instance, Operation<Object> original) {
        Object val = original.call(instance);
        if (linkenvisuals$isFullbrightActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null) {
                if (instance == client.options.getGamma()) {
                    return 15.0;
                }
                if (instance == client.options.getDarknessEffectScale()) {
                    return 0.0;
                }
            }
        }
        return val;
    }
}
