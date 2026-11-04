package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.entity.passive.FoxEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Доступ к пакетному FoxEntity.setSleeping для позы сна питомца-лисы.
 * Mixin сам ремапит имя в intermediary — в отличие от старой рефлексии
 * по yarn-имени, работает и в production-jar.
 */
@Mixin(FoxEntity.class)
public interface IFoxEntity {

    @Invoker("setSleeping") void linkenvisuals$setSleeping(boolean sleeping);
}
