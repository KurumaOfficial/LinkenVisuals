package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.utils.network.NetworkUtils;
import ru.method.linkenvisuals.utils.network.ClientPacketEvents;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Redirect(method = "handlePacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private static <T extends PacketListener> void linkenvisuals$receive(Packet<T> packet, T listener) {
        ClientPacketEvents.apply(packet, listener);
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void send(Packet<?> packet, CallbackInfo ci) {
        if (NetworkUtils.consumeSilentPacket(packet)) {
            return;
        }

        // Status/login/configuration and off-thread keep-alives must never enter module callbacks.
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (client == null || !client.isOnThread() || client.getNetworkHandler() == null
                || client.getNetworkHandler().getConnection() != (Object) this
                || !(((net.minecraft.network.ClientConnection) (Object) this).getPacketListener() instanceof net.minecraft.client.network.ClientPlayNetworkHandler)
                || instance == null || instance.getEventHandler() == null) return;
        EventPacket.Send eventSend = new EventPacket.Send(packet);
        LinkenVisuals.getInstance().getEventHandler().post(eventSend);
        if (eventSend.isCancelled()) ci.cancel();
    }
}
