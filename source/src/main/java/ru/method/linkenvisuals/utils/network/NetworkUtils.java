package ru.method.linkenvisuals.utils.network;

import ru.method.linkenvisuals.utils.Wrapper;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.network.packet.Packet;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class NetworkUtils implements Wrapper {

    @Getter private final Set<Packet<?>> silentPackets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void sendSilentPacket(Packet<?> packet) {
        silentPackets.add(packet);
        mc.getNetworkHandler().sendPacket(packet);
    }

    public void sendPacket(Packet<?> packet) {
        mc.getNetworkHandler().sendPacket(packet);
    }

    public boolean consumeSilentPacket(Packet<?> packet) {
        return silentPackets.remove(packet);
    }
}
