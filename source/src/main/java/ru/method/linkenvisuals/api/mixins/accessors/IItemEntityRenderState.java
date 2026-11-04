package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntityRenderState.class)
public interface IItemEntityRenderState {
    @Accessor("uniqueOffset")
    float getUniqueOffset();
}
