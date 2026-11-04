package ru.method.linkenvisuals.managers.system;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventPacket;
import ru.method.linkenvisuals.api.events.impl.EventPopTotem;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.Wrapper;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

@Getter

public class ServerManager implements Wrapper {

    private int serverSlot;
    private float serverYaw, serverPitch, fallDistance;
    private double serverX, serverY, serverZ;
    private boolean serverOnGround, serverSprinting, serverSneaking, serverHorizontalCollision;

    public ServerManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
    }

    private double lastY;

    @EventHandler
    public void onTick(EventTick e) {
        if (Module.fullNullCheck()) return;

        double currentY = mc.player.getY();
        double y = lastY - currentY;
        lastY = currentY;
        if (mc.player.isOnGround()) fallDistance = 0;
        else if (y > 0) fallDistance += (float) y;
    }

    @EventHandler
    public void onPacketSend(EventPacket.Send e) {
        if (Module.fullNullCheck()) return;

        if (e.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesPosition()) {
                serverX = packet.getX(mc.player.getX());
                serverY = packet.getY(mc.player.getY());
                serverZ = packet.getZ(mc.player.getZ());
            }

            if (packet.changesLook()) {
                serverYaw = packet.getYaw(mc.player.getYaw());
                serverPitch = packet.getPitch(mc.player.getPitch());
            }

            serverOnGround = packet.isOnGround();
            serverHorizontalCollision = packet.horizontalCollision();
        }

        if (e.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) serverSlot = packet.getSelectedSlot();

        if (e.getPacket() instanceof ClientCommandC2SPacket packet) {
            switch (packet.getMode()) {
                case START_SPRINTING -> serverSprinting = true;
                case STOP_SPRINTING -> serverSprinting = false;
                default -> {}
            }
        }
    }

    @EventHandler
    public void onPacketReceive(EventPacket.Receive e) {
        if (Module.fullNullCheck()) return;

        if (e.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            if (!(packet.getEntity(mc.world) instanceof PlayerEntity player)) return;
            LinkenVisuals.getInstance().getEventHandler().post(new EventPopTotem(player));
        }
    }
}
