package ru.method.linkenvisuals.utils.optimization;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/**
 * Окклюзионный куллинг сущностей: DDA-рейкаст от камеры к сущности через воксели, с кешем и бюджетом
 * проверок на кадр (перенос из Kimiko). Все API стабильны в 1.21.4.
 */
public final class OcclusionCuller {
    private static final int[] BUDGET = {16, 24, 32};
    private static final long[] HIDDEN_TTL = {150L, 120L, 90L};
    private static final long[] VISIBLE_TTL = {450L, 350L, 250L};
    private static final double MIN_DIST_SQ = 64.0;
    private static final double MAX_ENTITY_SIZE = 6.0;
    private static final int MAX_RAY_STEPS = 172;
    private static final Map<Integer, Entry> CACHE = new HashMap<>();
    private static final BlockPos.Mutable POS = new BlockPos.Mutable();
    private static ClientWorld lastLevel;
    private static int checksThisFrame;
    private static long lastSweep;

    private OcclusionCuller() {
    }

    public static boolean isVisible(Entity entity, double camX, double camY, double camZ, int tier) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || entity == mc.player || entity.isGlowing()) {
            return true;
        }
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        if (dx * dx + dy * dy + dz * dz < MIN_DIST_SQ) {
            return true;
        }
        Box box = entity.getBoundingBox();
        if (box.getLengthX() > MAX_ENTITY_SIZE || box.getLengthY() > MAX_ENTITY_SIZE || box.getLengthZ() > MAX_ENTITY_SIZE) {
            return true;
        }
        int t = MathHelper.clamp(tier, 0, 2);
        long now = System.currentTimeMillis();
        Entry entry = CACHE.get(entity.getId());
        if (entry != null && now < entry.next) {
            return entry.visible;
        }
        if (checksThisFrame >= BUDGET[t]) {
            return entry == null || entry.visible;
        }
        checksThisFrame++;
        boolean visible = raycastVisible(world, camX, camY, camZ, box.expand(0.1));
        if (entry == null) {
            entry = new Entry();
            CACHE.put(entity.getId(), entry);
        }
        entry.visible = visible;
        entry.next = now + (visible ? VISIBLE_TTL[t] : HIDDEN_TTL[t]) + (entity.getId() * 31 & 0x3F);
        return visible;
    }

    public static void beginFrame() {
        checksThisFrame = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != lastLevel) {
            CACHE.clear();
            lastLevel = mc.world;
        }
        long now = System.currentTimeMillis();
        if (now - lastSweep > 5000L) {
            lastSweep = now;
            Iterator<Entry> it = CACHE.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().next <= 5000L) continue;
                it.remove();
            }
        }
    }

    private static boolean sightBlocked(ClientWorld world, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dirX = x1 - x0;
        double dirY = y1 - y0;
        double dirZ = z1 - z0;
        int vx = MathHelper.floor(x0);
        int vy = MathHelper.floor(y0);
        int vz = MathHelper.floor(z0);
        int ex = MathHelper.floor(x1);
        int ey = MathHelper.floor(y1);
        int ez = MathHelper.floor(z1);
        int stepX = dirX > 0.0 ? 1 : (dirX < 0.0 ? -1 : 0);
        int stepY = dirY > 0.0 ? 1 : (dirY < 0.0 ? -1 : 0);
        int stepZ = dirZ > 0.0 ? 1 : (dirZ < 0.0 ? -1 : 0);
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirX);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirY);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dirZ);
        double tMaxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? Math.floor(x0) + 1.0 - x0 : x0 - Math.floor(x0)) * deltaX;
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? Math.floor(y0) + 1.0 - y0 : y0 - Math.floor(y0)) * deltaY;
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? Math.floor(z0) + 1.0 - z0 : z0 - Math.floor(z0)) * deltaZ;
        for (int i = 0; i < MAX_RAY_STEPS; i++) {
            if (Math.min(tMaxX, Math.min(tMaxY, tMaxZ)) >= 1.0) {
                return false;
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                vx += stepX;
                tMaxX += deltaX;
            } else if (tMaxY <= tMaxZ) {
                vy += stepY;
                tMaxY += deltaY;
            } else {
                vz += stepZ;
                tMaxZ += deltaZ;
            }
            if (vx == ex && vy == ey && vz == ez) {
                return false;
            }
            BlockState state = world.getBlockState(POS.set(vx, vy, vz));
            if (state.isAir() || !state.isOpaque()) continue;
            return true;
        }
        return false;
    }

    private static boolean raycastVisible(ClientWorld world, double camX, double camY, double camZ, Box box) {
        if (!sightBlocked(world, camX, camY, camZ, (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5)) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? box.minX : box.maxX;
            double cy = (i & 2) == 0 ? box.minY : box.maxY;
            double cz = (i & 4) == 0 ? box.minZ : box.maxZ;
            if (sightBlocked(world, camX, camY, camZ, cx, cy, cz)) continue;
            return true;
        }
        return false;
    }

    public static class Entry {
        public boolean visible;
        public long next;
    }
}
