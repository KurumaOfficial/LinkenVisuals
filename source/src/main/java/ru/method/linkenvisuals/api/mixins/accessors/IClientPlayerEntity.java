package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerEntity.class)
public interface IClientPlayerEntity {

    @Invoker("sendSprintingPacket") void sendSprintingPacket$drug();
}
