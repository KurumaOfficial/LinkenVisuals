package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.method.linkenvisuals.modules.impl.render.Optimization;

@Mixin(EntityRenderManager.class)
public abstract class OptimizationEntityCullMixin {

    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"), require = 0)
    private boolean linkenvisuals$occlusionCull(boolean original, Entity entity, Frustum frustum, double camX, double camY, double camZ) {
        return Optimization.shouldRenderEntity(original, entity, camX, camY, camZ);
    }
}
