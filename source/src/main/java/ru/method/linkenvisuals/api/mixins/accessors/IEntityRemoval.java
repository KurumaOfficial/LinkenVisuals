package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface IEntityRemoval {
    @Invoker("unsetRemoved")
    void linkenvisuals$unsetRemoved();
}
