package ru.method.linkenvisuals.cosmetics.wave;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Верёвочная физика плаща: цепочка из {@link #SEGMENTS} точек, связанных «палками» единичной длины.
 * Точка 0 приколочена к игроку (её ведёт отставание плаща от тела), остальные падают под гравитацией
 * и подтягиваются итерациями ограничений — плащ ведёт себя как ткань, а не как жёсткая пластина.
 *
 * <p>Математика перенесена 1:1 из проекта пользователя (WaveCapes): те же гравитация/итерации/лимит
 * изгиба/множитель высоты, тот же порядок проходов. Отличие только в реализации векторов — вместо
 * {@code clone()} на каждую операцию считаем скаляром: за тик это экономит ~2000 аллокаций на игрока
 * при идентичном результате.</p>
 *
 * <p>Тикается на клиентском потоке из {@code PlayerEntity.updateCapeAngles} (см. PlayerEntityCapeMixin),
 * читается на рендер-треде через лерп {@link #lerpX}/{@link #lerpY} — как ванильные prev/cur поля.</p>
 */
public final class CapeSimulation {

    /** Сегментов в плаще: 16 px текстуры → 16 звеньев, по 1 px на звено. */
    public static final int SEGMENTS = 16;

    private static final float GRAVITY = 25f;          // config.gravity
    private static final int ITERATIONS = 30;          // numIterations
    private static final float MAX_BEND = 5f;          // maxBend
    private static final float HEIGHT_MULTIPLIER = 6f; // config.heightMultiplier
    private static final float DELTA_TIME = 50f / 1000f;
    private static final float STICK_LENGTH = 1f;
    private static final float RAD_TO_DEG = 57.2958f;

    private final float[] x = new float[SEGMENTS];
    private final float[] y = new float[SEGMENTS];
    private final float[] prevX = new float[SEGMENTS];
    private final float[] prevY = new float[SEGMENTS];
    private boolean built;
    private double lastPlayerX;
    private double lastPlayerZ;
    private boolean hasLastPlayerPos;

    /** Готова ли цепочка (иначе рендерить нечего — рисуем ванильный плащ). */
    public boolean isBuilt() {
        return built;
    }

    /** Собрать цепочку и «утрясти» её несколькими шагами, чтобы она сразу висела правильно. */
    public void build(PlayerEntity player) {
        if (built) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            x[i] = 0f;
            y[i] = -i;
            prevX[i] = 0f;
            prevY[i] = -i;
        }
        built = true;
        for (int i = 0; i < 10; i++) {
            tick(player);
        }
    }

    /** Позиция точки между тиками — как ванильные prevCapeX/capeX. */
    public float lerpX(int index, float tickDelta) {
        return MathHelper.lerp(tickDelta, prevX[index], x[index]);
    }

    public float lerpY(int index, float tickDelta) {
        return MathHelper.lerp(tickDelta, prevY[index], y[index]);
    }

    /** Шаг симуляции: тянем корень за игроком и решаем ограничения. Вызывать раз в тик. */
    public void tick(PlayerEntity player) {
        if (!built) {
            return;
        }
        prevX[0] = x[0];
        prevY[0] = y[0];

        // Отставание плаща от игрока, спроецированное на направление взгляда тела: положительное
        // значение = игрок ушёл вперёд, плащ должен отлететь назад.
        double dx = 0.0;
        double dz = 0.0;
        if (hasLastPlayerPos) {
            dx = lastPlayerX - player.getX();
            dz = lastPlayerZ - player.getZ();
        }
        lastPlayerX = player.getX();
        lastPlayerZ = player.getZ();
        hasLastPlayerPos = true;
        float bodyYaw = player.getBodyYaw();
        double sin = MathHelper.sin(bodyYaw * 0.017453292f);
        double cos = -MathHelper.cos(bodyYaw * 0.017453292f);
        // Небольшой мах при прыжке/падении — чтобы плащ не складывался сам в себя (воздух под ним).
        double fallHack = MathHelper.clamp(y[0] - (player.getY() * HEIGHT_MULTIPLIER), 0.0, 1.0);

        x[0] += (float) ((dx * sin + dz * cos) + fallHack);
        y[0] = (float) (player.getY() * HEIGHT_MULTIPLIER + (player.isInSneakingPose() ? -4f : 0f));

        step();
    }

    private void step() {
        float gravityStep = GRAVITY * DELTA_TIME;

        // 1) Гравитация (точка 0 закреплена).
        for (int i = 1; i < SEGMENTS; i++) {
            float px = x[i];
            float py = y[i];
            y[i] = py - gravityStep;
            prevX[i] = px;
            prevY[i] = py;
        }

        // 2) Не даём плащу провалиться в игрока: все точки строго «за» корневой.
        for (int i = 1; i < SEGMENTS; i++) {
            if (x[i] - x[0] > 0f) {
                x[i] = x[0] - 0.1f;
            }
        }

        // 3) Ограничение изгиба — не идеально, но спасает от складывания плаща внутрь себя.
        for (int i = SEGMENTS - 2; i >= 1; i--) {
            double angle = angle(x[i], y[i], x[i - 1], y[i - 1], x[i + 1], y[i + 1]) * RAD_TO_DEG;
            if (angle > 360.0) {
                angle -= 360.0;
            }
            if (angle < -360.0) {
                angle += 360.0;
            }
            double abs = Math.abs(angle);
            if (abs < 180.0 - MAX_BEND) {
                replace(i, angle, 180.0 - MAX_BEND + 1.0);
            } else if (abs > 180.0 + MAX_BEND) {
                replace(i, angle, 180.0 + MAX_BEND - 1.0);
            }
        }

        // 4) Итерации ограничений длины: тянем концы «палок» к их середине.
        float half = STICK_LENGTH / 2f;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            for (int s = SEGMENTS - 2; s >= 0; s--) {
                int a = s;
                int b = s + 1;
                float centreX = (x[a] + x[b]) / 2f;
                float centreY = (y[a] + y[b]) / 2f;
                float dirX = x[a] - x[b];
                float dirY = y[a] - y[b];
                float len = MathHelper.sqrt(dirX * dirX + dirY * dirY);
                if (len < 1.0E-4f) {
                    dirX = 0f;
                    dirY = 0f;
                } else {
                    dirX /= len;
                    dirY /= len;
                }
                if (a != 0) {                       // точка 0 закреплена
                    x[a] = centreX + dirX * half;
                    y[a] = centreY + dirY * half;
                }
                x[b] = centreX - dirX * half;
                y[b] = centreY - dirY * half;
            }
        }

        // 5) Финальная фиксация длины — иначе цепочка «пружинит» и растягивается.
        for (int s = 0; s < SEGMENTS - 1; s++) {
            int a = s;
            int b = s + 1;
            float dirX = x[a] - x[b];
            float dirY = y[a] - y[b];
            float len = MathHelper.sqrt(dirX * dirX + dirY * dirY);
            if (len < 1.0E-4f) {
                dirX = 0f;
                dirY = 0f;
            } else {
                dirX /= len;
                dirY /= len;
            }
            x[b] = x[a] - dirX * STICK_LENGTH;
            y[b] = y[a] - dirY * STICK_LENGTH;
        }
    }

    /** Угол между «предыдущая — текущая — следующая» точками (в радианах). */
    private static double angle(float midX, float midY, float prevPX, float prevPY, float nextX, float nextY) {
        return Math.atan2(nextY - midY, nextX - midX) - Math.atan2(prevPY - midY, prevPX - midX);
    }

    /** Поворачивает точку i+1 вокруг i так, чтобы угол изгиба стал target (в градусах). */
    private void replace(int i, double angle, double target) {
        double theta = target / RAD_TO_DEG;
        if (angle < 0.0) {
            theta = -theta;
        }
        float dx = x[i - 1] - x[i];
        float dy = y[i - 1] - y[i];
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        x[i + 1] = (float) ((dx * cos) - (dy * sin) + x[i]);
        y[i + 1] = (float) ((dx * sin) + (dy * cos) + y[i]);
    }
}
