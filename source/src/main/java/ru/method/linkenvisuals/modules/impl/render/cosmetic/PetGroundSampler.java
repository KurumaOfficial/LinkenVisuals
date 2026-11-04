package ru.method.linkenvisuals.modules.impl.render.cosmetic;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Общий сканер поверхности для наземных питомцев (локальных {@link Raven} и чужих {@link RemotePets}).
 *
 * <p>Сканирует колонку блоков вниз и возвращает Y верхней грани земли. Отличия от «наивного» скана:
 * <ul>
 *   <li>Стены: если первый встреченный сплошной блок выше допустимого потолка ({@code fallbackY+1.25}),
 *       это стена/склон над питомцем — возвращаем NaN, а не «проваливаемся» внутрь колонны.</li>
 *   <li>Вода: поверхность воды считается «землёй» — наземный питомец плывёт по поверхности, а не по дну.</li>
 *   <li>Ступени: {@link #sampleGroundYSmooth} билинейно сглаживает Y между колонками, убирая скачок на блок.</li>
 * </ul>
 */
public final class PetGroundSampler {

    private PetGroundSampler() {
    }

    /** Верх воды считаем чуть ниже полного блока — источник ≈ 8/9. */
    private static final double WATER_SURFACE = 0.90;

    /**
     * Земля под точкой (x,z) с запасными соседними колонками. Возвращает Y верхней грани или NaN,
     * если земли в разумных пределах нет (пропасть/стена). Быстрый путь для «найти хоть какую-то землю рядом».
     */
    public static double sampleGroundY(World world, double x, double z, double fallbackY) {
        if (world == null) return Double.NaN;

        int ix = MathHelper.floor(x);
        int iz = MathHelper.floor(z);

        double maxTopY = fallbackY + 1.25;
        int minY = worldBottom(world);
        int startY = startScanY(world, fallbackY, minY);

        double g0 = column(world, ix, iz, startY, 90, minY, maxTopY);
        if (!Double.isNaN(g0)) return g0;

        double g1 = column(world, ix + 1, iz, startY, 90, minY, maxTopY);
        double g2 = column(world, ix, iz + 1, startY, 90, minY, maxTopY);
        double g3 = column(world, ix + 1, iz + 1, startY, 90, minY, maxTopY);

        double best = Double.NaN;
        if (!Double.isNaN(g1)) best = g1;
        if (!Double.isNaN(g2)) best = Double.isNaN(best) ? g2 : Math.max(best, g2);
        if (!Double.isNaN(g3)) best = Double.isNaN(best) ? g3 : Math.max(best, g3);
        if (!Double.isNaN(best)) return best;

        return column(world, ix, iz, startY, 170, minY, maxTopY);
    }

    /**
     * Высота земли под точкой для позиционирования питомца. Раньше здесь было билинейное усреднение
     * по 4 колонкам — оно топило питомца в блок на ступенях (усредняло верх и низ), поэтому убрано.
     * Возвращаем высоту колонки под самим питомцем (с запасом по соседям), а плавность на ступенях
     * даёт уже существующее сглаживание позиции в рендере.
     */
    public static double sampleGroundYSmooth(World world, double x, double z, double fallbackY) {
        return sampleGroundY(world, x, z, fallbackY);
    }

    private static double column(World world, int x, int z, int startY, int depth, int minY, double maxTopY) {
        if (world == null) return Double.NaN;

        int endY = Math.max(minY + 1, startY - Math.max(12, depth));
        BlockPos.Mutable m = new BlockPos.Mutable();
        double waterTop = Double.NaN;

        for (int y = startY; y >= endY; y--) {
            m.set(x, y, z);

            BlockState st;
            try {
                st = world.getBlockState(m);
            } catch (Throwable ignored) {
                return Double.NaN;
            }
            if (st == null) continue;

            // Вода: запоминаем самую верхнюю поверхность (питомец плывёт по ней).
            if (Double.isNaN(waterTop)) {
                try {
                    FluidState fluid = st.getFluidState();
                    if (fluid != null && !fluid.isEmpty()) {
                        waterTop = y + WATER_SURFACE;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (st.isAir()) continue;

            VoxelShape sh;
            try {
                sh = st.getCollisionShape(world, m);
            } catch (Throwable ignored) {
                sh = null;
            }
            if (sh == null || sh.isEmpty()) continue;

            double top = y + sh.getMax(Direction.Axis.Y);
            double bot = y + sh.getMin(Direction.Axis.Y);

            // Блок целиком выше потолка (навес/потолок над игроком) — это не земля и не стена, ищем ниже.
            if (bot > maxTopY) continue;

            // Стена: поднимается выше допустимого потолка от земли — не сканируем сквозь неё внутрь склона.
            if (top > maxTopY) return Double.NaN;

            // Есть вода над этой землёй в пределах потолка — питомец должен быть на поверхности воды.
            if (!Double.isNaN(waterTop) && waterTop <= maxTopY && waterTop >= top) {
                return waterTop;
            }
            return top + 0.015;
        }

        // Сплошной земли не нашли, но если сверху была вода в пределах потолка — плывём по ней.
        if (!Double.isNaN(waterTop) && waterTop <= maxTopY) return waterTop;
        return Double.NaN;
    }

    private static int worldBottom(World world) {
        try {
            return world.getBottomY();
        } catch (Throwable ignored) {
            return -64;
        }
    }

    private static int startScanY(World world, double fallbackY, int minY) {
        int maxY;
        try {
            maxY = minY + world.getHeight();
        } catch (Throwable ignored) {
            maxY = 320;
        }
        return MathHelper.clamp(MathHelper.floor(fallbackY) + 3, minY + 2, maxY - 2);
    }
}
