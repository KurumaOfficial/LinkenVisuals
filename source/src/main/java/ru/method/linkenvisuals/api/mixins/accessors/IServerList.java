package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ServerList.class)
public interface IServerList {

    @Accessor("servers")
    List<ServerInfo> linkenvisuals$getServers();
}
