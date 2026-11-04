package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(ClientPlayNetworkHandler.class)
public interface IClientPlayNetworkHandler {
    @Accessor("playerListEntries")
    Map<UUID, PlayerListEntry> linkenvisuals$getPlayerListEntries();
}
