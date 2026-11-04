package ru.method.linkenvisuals.utils.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventPacket;

public final class ClientPacketEvents {
    private ClientPacketEvents() {
    }

    public static <T extends PacketListener> void apply(Packet<T> packet, T listener) {
        // Off-thread calls must reach vanilla's forceMainThread first. The queued
        // Packet.apply call returns here on the render thread, before world mutation.
        // Keep login, configuration, keep-alives and integrated-server traffic vanilla.
        if (listener instanceof ClientPlayNetworkHandler && MinecraftClient.getInstance().isOnThread()) {
            EventPacket.Receive event = new EventPacket.Receive(packet);
            LinkenVisuals instance = LinkenVisuals.getInstance();
            if (instance != null && instance.getEventHandler() != null) instance.getEventHandler().post(event);
            if (event.isCancelled()) {
                return;
            }
        }
        packet.apply(listener);
    }
}
