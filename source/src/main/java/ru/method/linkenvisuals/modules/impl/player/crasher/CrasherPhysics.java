package ru.method.linkenvisuals.modules.impl.player.crasher;

import ru.method.linkenvisuals.modules.impl.player.Crasher;

import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Локальная физика чужих игроков поверх локально изменённого мира.
 *
 * <p>Сервер продолжает присылать их настоящие координаты, но если в НАШЕЙ копии мира под игроком
 * больше нет опоры (или его накрыло локальной геометрией) — мы перехватываем позицию и досимулируем
 * её сами: гравитация + настоящие коллизии клиентского мира. Горизонталь берётся из серверного
 * движения, чтобы игрок не превращался в статую.</p>
 */
public final class CrasherPhysics {

    private static final double GRAVITY = 0.08D;
    private static final double DRAG = 0.98D;
    private static final double TERMINAL_VELOCITY = -3.92D;
    /** Максимальный горизонтальный шаг за тик, взятый из серверного движения. */
    private static final double MAX_HORIZONTAL_STEP = 0.6D;
    /** На сколько блоков вверх выталкиваем игрока, если его залило локальной геометрией. */
    private static final int MAX_PUSH_UP = 4;

    private final Crasher owner;
    private final Map<UUID, Sim> simulated = new HashMap<>();

    public CrasherPhysics(Crasher owner) {
        this.owner = owner;
    }

    private static final class Sim {
        Vec3d pos;
        double velocityY;

        Sim(Vec3d pos) {
            this.pos = pos;
            this.velocityY = 0.0D;
        }
    }

    public void clear() {
        simulated.clear();
    }

    /** Вызывается каждый клиентский тик для каждого чужого игрока. */
    public void tick(OtherClientPlayerEntity entity) {
        World world = entity.getEntityWorld();
        UUID uuid = entity.getUuid();
        Sim sim = simulated.get(uuid);

        if (!canSimulate(entity)) {
            simulated.remove(uuid);
            return;
        }

        if (sim == null) {
            if (owner.pendingEdits() == 0) {
                return;
            }
            Vec3d start = resolveStart(entity, world);
            if (start == null) {
                return;
            }
            sim = new Sim(start);
            simulated.put(uuid, sim);
        }

        // Горизонтальная составляющая настоящего движения игрока за этот тик.
        Vec3d observed = entity.getEntityPos();
        double dx = MathHelper.clamp(observed.x - sim.pos.x, -MAX_HORIZONTAL_STEP, MAX_HORIZONTAL_STEP);
        double dz = MathHelper.clamp(observed.z - sim.pos.z, -MAX_HORIZONTAL_STEP, MAX_HORIZONTAL_STEP);

        entity.setPosition(sim.pos);
        entity.move(MovementType.SELF, new Vec3d(dx, sim.velocityY, dz));
        sim.pos = entity.getEntityPos();

        if (entity.verticalCollision) {
            sim.velocityY = 0.0D;
        } else {
            sim.velocityY = Math.max((sim.velocityY - GRAVITY) * DRAG, TERMINAL_VELOCITY);
        }

        entity.setVelocity(dx, sim.velocityY, dz);
        entity.setOnGround(entity.verticalCollision && sim.velocityY <= 0.0D);
    }

    private static boolean canSimulate(OtherClientPlayerEntity entity) {
        return !entity.isRemoved()
                && !entity.hasVehicle()
                && !entity.isSpectator()
                && !entity.isGliding()
                && !entity.isTouchingWater()
                && !entity.isClimbing();
    }

    /**
     * @return стартовая позиция симуляции, или null если игрок стоит нормально и вмешиваться незачем
     */
    private static Vec3d resolveStart(OtherClientPlayerEntity entity, World world) {
        Box box = entity.getBoundingBox();

        // 1. Игрока залило блоками — выталкиваем наверх.
        if (!world.isSpaceEmpty(entity, box.contract(0.001D, 0.001D, 0.001D))) {
            for (int up = 1; up <= MAX_PUSH_UP; up++) {
                Box lifted = box.offset(0.0D, up, 0.0D);
                if (world.isSpaceEmpty(entity, lifted.contract(0.001D, 0.001D, 0.001D))) {
                    return entity.getEntityPos().add(0.0D, up, 0.0D);
                }
            }
            return null;
        }

        // 2. Из-под игрока убрали опору — начинаем падение с его текущей точки.
        Box floor = new Box(box.minX + 0.001D, box.minY - 0.12D, box.minZ + 0.001D,
                box.maxX - 0.001D, box.minY, box.maxZ - 0.001D);
        if (entity.isOnGround() && world.isSpaceEmpty(entity, floor)) {
            return entity.getEntityPos();
        }

        return null;
    }
}
