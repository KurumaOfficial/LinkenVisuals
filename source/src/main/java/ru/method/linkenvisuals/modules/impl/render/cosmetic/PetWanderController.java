package ru.method.linkenvisuals.modules.impl.render.cosmetic;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;

/**
 * «Мозг» наземного питомца — двигается как ванильный моб: логика идёт на ФИКСИРОВАННОМ тике 20 Гц, а
 * рендер интерполируется ({@code lerp(prev, cur, tickDelta)}), поэтому нет покадровой дрожи. Ноги
 * (в Raven через {@code limbAnimator.updateLimbs}) гоняются раз в тик с фактической дистанцией шага —
 * частота ног пропорциональна скорости (как у мобов, без «скольжения»). На блок питомец ЗАПРЫГИВАЕТ
 * (баллистическая дуга), а не скользит вверх.
 *
 * <p>Поведение: рядом с игроком спокойно стоит/изредка гуляет; отстал (гистерезис) — догоняет; очень
 * далеко — телепорт к игроку.</p>
 */
public final class PetWanderController {

    private static final double TICK_DT = 0.05; // фиксированный шаг 20 Гц

    private Vec3d pos = null;
    private Vec3d prevPos = null;
    private float bodyYaw = 0f, prevBodyYaw = 0f;
    private float headYaw = 0f, prevHeadYaw = 0f;
    private float speedBps = 0f;
    private float limbPosDelta = 0f; // горизонтальная дистанция за последний тик (для ног)

    private Vec3d wanderTarget = null;
    private int wanderCooldownTicks = 0;
    private boolean following = false;
    private int lastAge = -1;

    // Прыжок на блок.
    private boolean jumping = false;
    private double jumpVy = 0.0;
    private double jumpTargetY = 0.0;

    private final Random rnd = new Random();

    public boolean hasPos() {
        return pos != null;
    }

    public Vec3d pos() {
        return pos;
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    public float headYaw() {
        return headYaw;
    }

    public Vec3d posLerp(float pt) {
        if (pos == null) return null;
        if (prevPos == null) return pos;
        return new Vec3d(lerp(prevPos.x, pos.x, pt), lerp(prevPos.y, pos.y, pt), lerp(prevPos.z, pos.z, pt));
    }

    public float bodyYawLerp(float pt) {
        return lerpAngle(prevBodyYaw, bodyYaw, pt);
    }

    public float headYawLerp(float pt) {
        return lerpAngle(prevHeadYaw, headYaw, pt);
    }

    public float limbPosDelta() {
        return limbPosDelta;
    }

    public void reset() {
        pos = null;
        prevPos = null;
        wanderTarget = null;
        wanderCooldownTicks = 0;
        speedBps = 0f;
        limbPosDelta = 0f;
        following = false;
        lastAge = -1;
        jumping = false;
        jumpVy = 0.0;
    }

    /**
     * Вызывать КАЖДЫЙ кадр. Продвигает симуляцию на фиксированных тиках (по {@code age} = {@code player.age},
     * инкрементируется 20 Гц). Возвращает число прошедших тиков (0..2) — столько раз надо дёрнуть ноги.
     */
    public int frame(World world, double px, double py, double pz, float baseSpeed, double leash, int age) {
        if (world == null) return 0;
        if (pos == null) {
            seed(world, px, py, pz);
            lastAge = age;
            return 0;
        }
        if (age == lastAge) return 0;
        int steps = lastAge < 0 ? 1 : (age - lastAge);
        if (steps < 1) {
            lastAge = age;
            return 0;
        }
        steps = Math.min(2, steps);
        lastAge = age;
        for (int i = 0; i < steps; i++) {
            tickStep(world, px, py, pz, baseSpeed, leash);
        }
        return steps;
    }

    private void seed(World world, double px, double py, double pz) {
        double g = PetGroundSampler.sampleGroundY(world, px, pz, py);
        pos = new Vec3d(px, Double.isNaN(g) ? py : g, pz);
        prevPos = pos;
        bodyYaw = headYaw = prevBodyYaw = prevHeadYaw = 0f;
        speedBps = 0f;
        limbPosDelta = 0f;
        jumping = false;
    }

    private void tickStep(World world, double px, double py, double pz, float baseSpeed, double leashRadius) {
        prevPos = pos;
        prevBodyYaw = bodyYaw;
        prevHeadYaw = headYaw;

        double dxp = px - pos.x, dzp = pz - pos.z;
        double distToPlayer = Math.sqrt(dxp * dxp + dzp * dzp);

        // Телепорт при большом отдалении.
        if (distToPlayer > 16.0) {
            double g = PetGroundSampler.sampleGroundY(world, px, pz, py);
            pos = new Vec3d(px + (rnd.nextDouble() - 0.5), Double.isNaN(g) ? py : g, pz + (rnd.nextDouble() - 0.5));
            prevPos = pos;
            wanderTarget = null;
            following = false;
            speedBps = 0f;
            jumping = false;
            limbPosDelta = 0f;
            return;
        }

        double baseMove = 1.3 * MathHelper.clamp(baseSpeed, 0.4f, 3.0f);
        double followStart = Math.max(2.4, leashRadius + 1.0);
        double followStop = Math.max(1.1, leashRadius - 0.2);
        if (distToPlayer > followStart) following = true;
        else if (following && distToPlayer < followStop) following = false;

        Vec3d target;
        double moveGoal;
        if (following) {
            double inv = distToPlayer > 1.0e-4 ? 1.0 / distToPlayer : 0.0;
            double stop = followStop * 0.85;
            target = new Vec3d(px - dxp * inv * stop, py, pz - dzp * inv * stop);
            wanderTarget = null;
            // Догон: чем дальше отстал — тем быстрее бежит. Игрок идёт ~4.3 бл/с и спринтит ~5.6, так
            // что фиксированной скорости не хватало (пет отставал и только телепортировался с 16 бл).
            // Скорость растёт с отставанием: трусца ~3.1 у порога → до потолка 7 бл/с, когда отстал
            // на 3+ блока. Равновесие: ~2.3 блока позади идущего игрока, ~3 позади спринтующего.
            double over = Math.max(0.0, distToPlayer - followStop);
            moveGoal = Math.min(baseMove * (2.4 + MathHelper.clamp(over * 1.2, 0.0, 3.6)), 7.0);
        } else {
            if (wanderTarget == null) {
                if (wanderCooldownTicks > 0) wanderCooldownTicks--;
                else pickWander(world, px, py, pz, leashRadius);
            } else if (horizDist(pos, wanderTarget) < 0.4) {
                wanderTarget = null;
                wanderCooldownTicks = 40 + rnd.nextInt(70);
            }
            target = wanderTarget;
            moveGoal = baseMove;
        }

        double dirX = 0, dirZ = 0, desiredSpeed = 0;
        if (target != null) {
            double dx = target.x - pos.x, dz = target.z - pos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.35) {
                dirX = dx / d;
                dirZ = dz / d;
                desiredSpeed = moveGoal;
            }
        }

        float tauSp = desiredSpeed > speedBps ? 0.25f : 0.20f;
        speedBps = approach(speedBps, (float) desiredSpeed, (float) TICK_DT, tauSp);

        double curG = groundOr(world, pos.x, pos.z, py);
        double aheadG = Double.NaN;
        boolean moving = (dirX != 0 || dirZ != 0) && speedBps > 0.05f;

        // Шаг по XZ с проверкой стены/обрыва.
        if (moving) {
            double stepLen = speedBps * TICK_DT;
            double nx = pos.x + dirX * stepLen;
            double nz = pos.z + dirZ * stepLen;
            double ng = PetGroundSampler.sampleGroundY(world, nx, nz, curG);
            aheadG = PetGroundSampler.sampleGroundY(world, pos.x + dirX * 0.6, pos.z + dirZ * 0.6, curG);
            if (!Double.isNaN(ng) && ng - curG <= 1.05) {
                pos = new Vec3d(nx, pos.y, nz);
                bodyYaw = lerpAngle(bodyYaw, dirToYaw(dirX, dirZ), 1f - (float) Math.exp(-TICK_DT / 0.18));
            } else {
                if (!following) {
                    wanderTarget = null;
                    wanderCooldownTicks = 24;
                }
                speedBps = approach(speedBps, 0f, (float) TICK_DT, 0.12f);
            }
        }

        // Высота: ЗАПРЫГИВАНИЕ на блок (баллистика) вместо скольжения; иначе плавно по земле.
        curG = PetGroundSampler.sampleGroundY(world, pos.x, pos.z, py);
        if (!jumping && moving && !Double.isNaN(aheadG) && !Double.isNaN(curG)) {
            double rise = aheadG - curG;
            if (rise >= 0.55 && rise <= 1.2 && Math.abs(pos.y - curG) < 0.25) {
                jumping = true;
                // Дискретная дуга (vy0=0.50, g=0.08) даёт апекс ~1.32 блока — выше макс. подъёма 1.2,
                // поэтому питомец ПЕРЕлетает край и приземляется на блок сверху вниз (без рывка вверх
                // в конце, как было при 0.42 с апексом ~0.90).
                jumpVy = 0.50;
                jumpTargetY = aheadG;
            }
        }
        if (jumping) {
            jumpVy -= 0.08;              // ванильная гравитация (блок/тик^2)
            double ny = pos.y + jumpVy;
            if (jumpVy < 0 && ny <= jumpTargetY) {
                ny = jumpTargetY;
                jumping = false;
                jumpVy = 0.0;
            }
            pos = new Vec3d(pos.x, ny, pos.z);
        } else if (!Double.isNaN(curG)) {
            double tauY = curG > pos.y ? 0.08 : 0.14;
            double ny = pos.y + (curG - pos.y) * (1.0 - Math.exp(-TICK_DT / tauY));
            if (ny < curG - 0.08) ny = curG;
            pos = new Vec3d(pos.x, ny, pos.z);
        }

        // Голова смотрит на игрока; стоя — тело медленно доворачивается к игроку.
        float lookYaw = dirToYaw(px - pos.x, pz - pos.z);
        float rel = MathHelper.clamp(MathHelper.wrapDegrees(lookYaw - bodyYaw), -55f, 55f);
        headYaw = lerpAngle(headYaw, bodyYaw + rel, 1f - (float) Math.exp(-TICK_DT / 0.16));
        if (dirX == 0 && dirZ == 0) {
            float relB = MathHelper.clamp(MathHelper.wrapDegrees(lookYaw - bodyYaw), -35f, 35f);
            bodyYaw = lerpAngle(bodyYaw, bodyYaw + relB, 1f - (float) Math.exp(-TICK_DT / 0.6));
        }

        limbPosDelta = (float) horizDist(prevPos, pos);
    }

    private void pickWander(World world, double px, double py, double pz, double leash) {
        // В основном стоим (спокойно) — гуляем изредка.
        if (rnd.nextFloat() < 0.6f) {
            wanderTarget = null;
            wanderCooldownTicks = 50 + rnd.nextInt(80);
            return;
        }
        for (int i = 0; i < 12; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double r = 0.9 + rnd.nextDouble() * Math.max(0.4, leash * 0.9);
            double tx = px + Math.cos(ang) * r;
            double tz = pz + Math.sin(ang) * r;
            double ddx = tx - pos.x, ddz = tz - pos.z;
            if (ddx * ddx + ddz * ddz < 0.64) continue; // реальный проход, не микро-шаг
            double g = PetGroundSampler.sampleGroundY(world, tx, tz, py);
            if (!Double.isNaN(g) && Math.abs(g - py) <= 2.0) {
                wanderTarget = new Vec3d(tx, g, tz);
                wanderCooldownTicks = 60 + rnd.nextInt(80);
                return;
            }
        }
        wanderTarget = null;
        wanderCooldownTicks = 30;
    }

    private static double groundOr(World world, double x, double z, double fb) {
        double g = PetGroundSampler.sampleGroundY(world, x, z, fb);
        return Double.isNaN(g) ? fb : g;
    }

    private static double horizDist(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float approach(float cur, float want, float dt, float tau) {
        return cur + (want - cur) * (1f - (float) Math.exp(-dt / Math.max(0.02f, tau)));
    }

    private static float dirToYaw(double dx, double dz) {
        return (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
    }

    private static float lerpAngle(float a, float b, float t) {
        float d = MathHelper.wrapDegrees(b - a);
        return a + d * MathHelper.clamp(t, 0f, 1f);
    }
}
