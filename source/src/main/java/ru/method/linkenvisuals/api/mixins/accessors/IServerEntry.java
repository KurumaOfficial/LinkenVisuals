package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public interface IServerEntry {

    @Accessor("server")
    ServerInfo linkenvisuals$getServer();
}
