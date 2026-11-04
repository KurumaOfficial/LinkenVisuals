package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderer.class)
public interface IEntityRendererShadow {
    @Accessor("shadowRadius")
    float getShadowRadiusValue();

    @Accessor("shadowOpacity")
    float getShadowOpacityValue();
}
