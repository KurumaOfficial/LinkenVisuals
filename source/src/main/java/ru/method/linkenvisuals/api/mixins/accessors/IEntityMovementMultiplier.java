package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface IEntityMovementMultiplier {
    @Accessor("movementMultiplier")
    Vec3d linkenvisuals$getMovementMultiplier();
}
