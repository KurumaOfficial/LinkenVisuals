package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.method.linkenvisuals.modules.impl.render.Optimization;

@Mixin(GameOptions.class)
public abstract class OptimizationOptionsMixin {

    @ModifyReturnValue(method = "getClampedViewDistance", at = @At("RETURN"), require = 0)
    private int linkenvisuals$capRenderDistance(int original) {
        return Optimization.capEffectiveRenderDistance(original);
    }
}
