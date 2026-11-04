package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.method.linkenvisuals.modules.impl.render.Optimization;

@Mixin(MinecraftClient.class)
public abstract class OptimizationTransparencyMixin {

    @ModifyReturnValue(method = "usesImprovedTransparency", at = @At("RETURN"), require = 0)
    private static boolean linkenvisuals$fastTransparency(boolean original) {
        return Optimization.shaderTransparency(original);
    }
}
