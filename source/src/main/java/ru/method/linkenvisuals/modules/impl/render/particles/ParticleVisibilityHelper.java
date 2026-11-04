package ru.method.linkenvisuals.modules.impl.render.particles;

import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import ru.method.linkenvisuals.utils.Wrapper;

public final class ParticleVisibilityHelper implements Wrapper {
    private static final double MIN_DISTANCE_SQ = 1.0E-5;
    private static final double FULL_BLOCK_EPSILON = 0.98;
    private static final int MAX_STEPS = 512;

    private ParticleVisibilityHelper() {}

    public static boolean hasLineOfSight(Vec3d from, Vec3d to) {
        if (mc.world == null || from == null || to == null) {
            return true;
        }
        if (from.squaredDistanceTo(to) <= MIN_DISTANCE_SQ) {
            return true;
        }

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        int x = MathHelper.floor(from.x);
        int y = MathHelper.floor(from.y);
        int z = MathHelper.floor(from.z);
        int endX = MathHelper.floor(to.x);
        int endY = MathHelper.floor(to.y);
        int endZ = MathHelper.floor(to.z);

        int stepX = Integer.compare(endX, x);
        int stepY = Integer.compare(endY, y);
        int stepZ = Integer.compare(endZ, z);

        double tMaxX = intBound(from.x, dx);
        double tMaxY = intBound(from.y, dy);
        double tMaxZ = intBound(from.z, dz);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        boolean skipFirstCell = true;

        for (int step = 0; step < MAX_STEPS; step++) {
            mutablePos.set(x, y, z);
            if (!skipFirstCell && isOccludingBlock(mutablePos, from, to)) {
                return false;
            }
            skipFirstCell = false;

            if (x == endX && y == endY && z == endZ) {
                return true;
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        return true;
    }

    private static boolean isOccludingBlock(BlockPos pos, Vec3d from, Vec3d to) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        VoxelShape collisionShape = state.getCollisionShape(mc.world, pos);
        if (collisionShape.isEmpty()) {
            return false;
        }

        if (state.isIn(BlockTags.LEAVES)) {
            return intersectsShape(collisionShape, pos, from, to);
        }

        if (state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof WallBlock) {
            return intersectsShape(collisionShape, pos, from, to);
        }

        if (!state.isOpaqueFullCube()) {
            return false;
        }

        Box box = collisionShape.getBoundingBox();
        boolean fullBlock = box.getLengthX() >= FULL_BLOCK_EPSILON
                && box.getLengthY() >= FULL_BLOCK_EPSILON
                && box.getLengthZ() >= FULL_BLOCK_EPSILON;
        return fullBlock && intersectsShape(collisionShape, pos, from, to);
    }

    private static boolean intersectsShape(VoxelShape shape, BlockPos pos, Vec3d from, Vec3d to) {
        if (shape.isEmpty()) {
            return false;
        }

        Vec3d localFrom = from.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3d localTo = to.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3d delta = localTo.subtract(localFrom);

        for (Box box : shape.getBoundingBoxes()) {
            Box expanded = box.expand(1.0E-4);
            if (intersectsBox(expanded, localFrom, delta)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsBox(Box box, Vec3d origin, Vec3d delta) {
        double min = 0.0;
        double max = 1.0;

        double[] resultX = clipAxis(origin.x, delta.x, box.minX, box.maxX, min, max);
        if (resultX == null) return false;
        min = resultX[0];
        max = resultX[1];

        double[] resultY = clipAxis(origin.y, delta.y, box.minY, box.maxY, min, max);
        if (resultY == null) return false;
        min = resultY[0];
        max = resultY[1];

        double[] resultZ = clipAxis(origin.z, delta.z, box.minZ, box.maxZ, min, max);
        return resultZ != null;
    }

    private static double[] clipAxis(double origin, double delta, double minBound, double maxBound, double min, double max) {
        if (Math.abs(delta) < 1.0E-8) {
            return origin >= minBound && origin <= maxBound ? new double[]{min, max} : null;
        }

        double inv = 1.0 / delta;
        double t1 = (minBound - origin) * inv;
        double t2 = (maxBound - origin) * inv;
        if (t1 > t2) {
            double swap = t1;
            t1 = t2;
            t2 = swap;
        }

        min = Math.max(min, t1);
        max = Math.min(max, t2);
        return min <= max ? new double[]{min, max} : null;
    }

    private static double intBound(double start, double delta) {
        if (delta > 0.0) {
            return (Math.floor(start) + 1.0 - start) / delta;
        }
        if (delta < 0.0) {
            return (start - Math.floor(start)) / -delta;
        }
        return Double.POSITIVE_INFINITY;
    }
}
