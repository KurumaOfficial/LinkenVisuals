package ru.method.linkenvisuals.utils.world;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class MovementUtils {

    private MovementUtils() {
    }

    public static double getBps(PlayerEntity player) {
        if (player == null) return 0.0;
        double dx = player.getX() - player.lastX;
        double dz = player.getZ() - player.lastZ;
        return Math.hypot(dx, dz) * 20.0;
    }
}
