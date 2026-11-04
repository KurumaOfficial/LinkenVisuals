package ru.method.linkenvisuals.modules.impl.render.cosmetic;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Raven {

    @NonFinal OrderedRenderCommandQueue frameQueue;
    @NonFinal CameraRenderState frameCameraState;
    @NonFinal VertexConsumerProvider frameConsumers;

    private static final class RenderSystem {
        static void enableBlend() { GL11.glEnable(GL11.GL_BLEND); }
        static void disableBlend() { GL11.glDisable(GL11.GL_BLEND); }
        static void defaultBlendFunc() { GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); }
        static void blendFunc(int s, int d) { GL11.glBlendFunc(s, d); }
        static void enableCull() { GL11.glEnable(GL11.GL_CULL_FACE); }
        static void disableCull() { GL11.glDisable(GL11.GL_CULL_FACE); }
        static void enableDepthTest() { GL11.glEnable(GL11.GL_DEPTH_TEST); }
        static void disableDepthTest() { GL11.glDisable(GL11.GL_DEPTH_TEST); }
        static void depthFunc(int f) { GL11.glDepthFunc(f); }
        static void depthMask(boolean m) { GL11.glDepthMask(m); }
        static void setShaderColor(float r, float g, float b, float a) {}
    }

    private static void setPrevPos(Entity e, double x, double y, double z) {
        e.lastRenderX = x;
        e.lastRenderY = y;
        e.lastRenderZ = z;
        e.lastX = x;
        e.lastY = y;
        e.lastZ = z;
    }

    public static final Identifier VORON_TEX = LinkenVisuals.id("cosmetic/textures/voron.png");
    @NonFinal static volatile boolean PREVIEW_RAVEN;

    public static boolean previewRavenActive() {
        return PREVIEW_RAVEN;
    }
    static final Identifier FEATHER_TEX = LinkenVisuals.id("cosmetic/textures/pero.png");

    static final Identifier ITACHI_SOUND_MINECRAFT = LinkenVisuals.id("cosmetic/itachi");
    static final Identifier ITACHI_SOUND_MRE = LinkenVisuals.id("cosmetic/itachi");

    static final long ULT_MS = 19000L;
    static final long ULT_ASCEND_MS = 2400L;
    static final long ULT_IN_MS = 2600L;
    static final long ULT_HOLD_MS = 9200L;
    static final long ULT_BREAK_MS = 2800L;
    static final long ULT_DISPERSE_MS = 3000L;

    static final long RETURN_MS = 850L;

    static final int ULT_RAVENS = 42;

    CosmeticEffects p;

    @NonFinal BatEntity bat;
    @NonFinal ParrotEntity parrot;
    @NonFinal ParrotEntity raven;

    @NonFinal AllayEntity fairy;
    @NonFinal BeeEntity bee;
    @NonFinal VexEntity vex;
    @NonFinal FoxEntity fox;

    @NonFinal PigEntity pig;
    @NonFinal FrogEntity frog;
    @NonFinal PufferfishEntity pufferfish;
    @NonFinal SlimeEntity slime;

    @NonFinal double slimeJumpY = 0.0;
    @NonFinal double slimeJumpVel = 0.0;
    @NonFinal boolean slimeJumpAir = false;
    @NonFinal long slimeLastHopMs = 0L;

    @NonFinal int lastMode = -1;
    @NonFinal long lastMs = 0L;
    @NonFinal long lastBatTickMs = 0L;
    @NonFinal long lastPetTickMs = 0L;
    @NonFinal long lastFoxTickMs = 0L;

    // Игрок в воздухе (полёт/элитра/прыжок) — для наземных питомцев: следовать по высоте или жаться к кромке.
    @NonFinal long lastPlayerGroundedMs = 0L;
    @NonFinal Object lastWorld = null;

    @NonFinal Vec3d smoothPos = Vec3d.ZERO;
    @NonFinal Vec3d prevSmoothPos = Vec3d.ZERO;
    @NonFinal float smoothYaw = 0.0f;
    @NonFinal float smoothPitch = 0.0f;

    @NonFinal float moveSp = 0.0f;
    @NonFinal float wantHoldTime = 0.0f;
    @NonFinal float wantOrbitTime = 0.0f;
    @NonFinal boolean holdMode = false;

    @NonFinal float orbitAng = 0.0f;
    @NonFinal float orbitBlend = 1.0f;

    @NonFinal float baseYawDeg = Float.NaN;

    @NonFinal RenderCompat compat = new RenderCompat();
    @NonFinal ParrotWingCompat parrotWing = new ParrotWingCompat();
    @NonFinal BatRoostCompat batRoost = new BatRoostCompat();

    @NonFinal FoxStateCompat foxState = new FoxStateCompat();
    @NonFinal FoxLimbCompat foxLimb = new FoxLimbCompat();

    @NonFinal WalkerLimbCompat walkerLimb = new WalkerLimbCompat();

    // Автономное блуждание наземных питомцев (ходят сами вокруг игрока, как ванильный моб).
    @NonFinal PetWanderController walkerWander = new PetWanderController();
    @NonFinal PetWanderController foxWander = new PetWanderController();

    @NonFinal Vec3d foxSmoothVel = Vec3d.ZERO;
    @NonFinal double foxGroundY = Double.NaN;
    @NonFinal double foxGroundTargetY = Double.NaN;
    @NonFinal long foxGroundLastSampleMs = 0L;
    @NonFinal Vec3d foxAnimPrevPos = Vec3d.ZERO;
    @NonFinal float foxSpeedSm = 0.0f;

    @NonFinal Vec3d walkerAnimPrevPos = Vec3d.ZERO;
    @NonFinal float walkerSpeedSm = 0.0f;

    @NonFinal PufferStateCompat pufferState = new PufferStateCompat();

    @NonFinal boolean ultActive = false;
    @NonFinal long ultStartMs = 0L;
    @NonFinal long ultLastMs = 0L;
    @NonFinal Vec3d ultStartPos = Vec3d.ZERO;
    @NonFinal float ultStartYaw = 0.0f;
    @NonFinal PositionedSoundInstance ultSound = null;

    @NonFinal boolean returnActive = false;
    @NonFinal long returnStartMs = 0L;
    @NonFinal Vec3d returnFrom = Vec3d.ZERO;
    @NonFinal Vec3d returnPrev = Vec3d.ZERO;
    @NonFinal boolean returnHasFrom = false;

    @NonFinal List<Illusion> illusions = new ArrayList<>();

    @NonFinal static volatile Raven INSTANCE;

    public static Raven instance() {
        return INSTANCE;
    }

    public Raven(CosmeticEffects parent) {
        this.p = parent;
        INSTANCE = this;
    }

    public void deactivate() {
        stopUltSound();
        ultActive = false;
        ultStartMs = 0L;
        ultLastMs = 0L;
        ultStartPos = Vec3d.ZERO;
        ultStartYaw = 0.0f;
        illusions.clear();

        returnActive = false;
        returnStartMs = 0L;
        returnFrom = Vec3d.ZERO;
        returnPrev = Vec3d.ZERO;
        returnHasFrom = false;

        bat = null;
        parrot = null;
        raven = null;

        fairy = null;
        bee = null;
        vex = null;
        fox = null;

        pig = null;
        frog = null;
        pufferfish = null;
        slime = null;

        lastMode = -1;
        lastMs = 0L;
        lastBatTickMs = 0L;
        lastPetTickMs = 0L;
        lastFoxTickMs = 0L;

        resetMotion();

        slimeJumpY = 0.0;
        slimeJumpVel = 0.0;
        slimeJumpAir = false;
        slimeLastHopMs = 0L;
        compat.reset();
        parrotWing.reset();
        batRoost.reset();
        foxState.reset();
        foxLimb.reset();
        walkerLimb.reset();
        pufferState.reset();
    }

    public void triggerUltimate() {
        if (p.mc.world == null || p.mc.player == null) return;
        if (!p.petIsRaven()) return;

        long now = System.currentTimeMillis();
        if (ultActive) return;

        ensureRaven(p.mc.player);

        returnActive = false;
        returnStartMs = 0L;
        returnFrom = Vec3d.ZERO;
        returnPrev = Vec3d.ZERO;
        returnHasFrom = false;

        float td = p.mc.getRenderTickCounter().getTickProgress(false);
        PlayerEntity pl = p.mc.player;

        Vec3d lPos1 = pl.getLerpedPos(td);
        double px = lPos1.x;
        double py = lPos1.y;
        double pz = lPos1.z;

        Vec3d eye = new Vec3d(px, py + pl.getStandingEyeHeight() * 0.82, pz);

        if (smoothPos == Vec3d.ZERO) {
            smoothPos = eye;
            prevSmoothPos = eye;
        }

        ultActive = true;
        ultStartMs = now;
        ultLastMs = now;
        ultStartPos = smoothPos;
        ultStartYaw = Float.isNaN(baseYawDeg) ? pl.bodyYaw : baseYawDeg;

        illusions.clear();

        Random r = new Random(now ^ 0x9E3779B97F4A7C15L);

        for (int i = 0; i < ULT_RAVENS; i++) {
            ParrotEntity e = createParrot();
            setParrotVariantSafe(e, 0);
            try {
                e.setCustomNameVisible(false);
                e.setCustomName(null);
            } catch (Throwable ignored) {
            }

            Illusion il = new Illusion();
            il.entity = e;
            il.seedA = r.nextFloat() * 1000.0f;
            il.seedB = r.nextFloat() * 1000.0f;
            il.phase = r.nextFloat();
            il.spinMul = 0.85f + r.nextFloat() * 1.55f;
            il.u = (r.nextFloat() - 0.5f) * 1.0f;
            il.v = (r.nextFloat() - 0.5f) * 1.0f;
            il.fly = randomDir(r).multiply(0.55 + r.nextDouble() * 1.35);
            il.pos = Vec3d.ZERO;
            il.prevPos = Vec3d.ZERO;
            il.vel = Vec3d.ZERO;
            il.hasPos = false;
            il.burst = false;
            il.burstMs = 0L;
            il.layer = frac((i * 0.6180339887f) + (il.seedA * 0.0007f));
            il.ang0 = (float) (i * (Math.PI * 2.0 / ULT_RAVENS));
            il.lastBodyPoint = Vec3d.ZERO;
            illusions.add(il);
        }

        playUltSound();
    }

    public void onWorldRender(EventRender3D.Game e) {
        if (p.mc.world == null || p.mc.player == null) return;

        frameQueue = e.getCommandQueue();
        frameCameraState = e.getCameraState();
        frameConsumers = e.getConsumers();

        if (p.pollRavenScene()) triggerUltimate();

        if (!ultActive && !CosmeticEffects.bool(p.petFirstPerson) && isFirstPerson(p.mc)) return;

        long now = System.currentTimeMillis();
        float dt = lastMs == 0L ? 0.016f : MathHelper.clamp((now - lastMs) / 1000.0f, 0.0f, 0.06f);
        lastMs = now;

        float pt = e.getTickCounter().getTickProgress(false);
        PlayerEntity pl = p.mc.player;

        // Смена мира (портал/реконнект) — сбрасываем сглаживание, чтобы питомец не «летел» через мир.
        if (lastWorld != p.mc.world) {
            lastWorld = p.mc.world;
            resetMotion();
        }
        if (lastPlayerGroundedMs == 0L) lastPlayerGroundedMs = now;
        if (pl.isOnGround()) lastPlayerGroundedMs = now;

        int mode = modeFromSetting();
        if (mode != lastMode) {
            lastMode = mode;
            resetMotion();
            lastBatTickMs = 0L;
            lastPetTickMs = 0L;
            lastFoxTickMs = 0L;
            if (!ultActive) stopUltSound();
            if (!ultActive) {
                ultStartMs = 0L;
                ultLastMs = 0L;
                ultStartPos = Vec3d.ZERO;
                ultStartYaw = 0.0f;
                illusions.clear();
                returnActive = false;
                returnStartMs = 0L;
                returnFrom = Vec3d.ZERO;
                returnPrev = Vec3d.ZERO;
                returnHasFrom = false;
            }
            foxState.reset();
            foxLimb.reset();
            walkerLimb.reset();
            pufferState.reset();
        }

        Vec3d lPos2 = pl.getLerpedPos(pt);
        double px = lPos2.x;
        double py = lPos2.y;
        double pz = lPos2.z;

        Vec3d pv = pl.getVelocity();
        float rawSp = (float) Math.sqrt(pv.x * pv.x + pv.z * pv.z);
        float spA = 1.0f - (float) Math.exp(-dt / 0.10f);
        moveSp = moveSp + (rawSp - moveSp) * spA;

        float bodyYawDeg = MathHelper.lerpAngleDegrees(pt, pl.lastBodyYaw, pl.bodyYaw);

        if (Float.isNaN(baseYawDeg)) baseYawDeg = bodyYawDeg;
        float want = baseYawDeg;
        float upd;

        if (pl.isSprinting() || moveSp > 0.11f) {
            want = bodyYawDeg;
            upd = 1.0f - (float) Math.exp(-dt / 0.14f);
        } else if (moveSp > 0.06f) {
            want = bodyYawDeg;
            upd = 1.0f - (float) Math.exp(-dt / 0.22f);
        } else {
            upd = 0.0f;
        }

        if (upd > 0.0f) baseYawDeg = lerpAngle(baseYawDeg, want, upd);

        double yawRad = Math.toRadians(baseYawDeg);
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);

        float radiusBase = MathHelper.clamp((float) p.petRadius.getValue(), 0.15f, 2.5f);
        float heightBase = MathHelper.clamp((float) p.petHeight.getValue(), -0.5f, 3.0f);
        float speed = MathHelper.clamp((float) p.petSpeed.getValue(), 0.05f, 5.0f);
        float scale = MathHelper.clamp((float) p.petScale.getValue(), 0.15f, 2.0f);

        VertexConsumerProvider.Immediate vcp = p.mc.getBufferBuilders().getEntityVertexConsumers();
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        if (mode == 2 && returnActive && !ultActive) {
            ensureRaven(pl);
            renderReturn(e, vcp, light, pl, pt, dt, px, py, pz, forward, right, scale, now);
            vcp.draw();
            return;
        }

        if (mode == 2 && ultActive) {
            ensureRaven(pl);
            renderUltimate(e, vcp, light, pl, pt, dt, px, py, pz, forward, right, radiusBase, heightBase, speed, scale, now);
            return;
        }

        if (mode == 0) {
            ensureBat(pl);
            renderBatWithParrotLogic(e, vcp, light, bat, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale, now);
            vcp.draw();
            return;
        }

        if (mode == 2) {
            ensureRaven(pl);
            renderParrotLike(e, vcp, light, raven, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale, now, true);
            vcp.draw();
            return;
        }

        if (mode == 1) {
            ensureParrot(pl);
            renderParrotLike(e, vcp, light, parrot, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale, now, false);
            vcp.draw();
            return;
        }

        if (mode == 3) {
            ensureFairy(pl);
            renderGenericFlyer(e, vcp, light, fairy, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.92f, now, 0.90f, 0.55f);
            vcp.draw();
            return;
        }

        if (mode == 4) {
            ensureBee(pl);
            renderGenericFlyer(e, vcp, light, bee, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.95f, now, 1.10f, 0.70f);
            vcp.draw();
            return;
        }

        if (mode == 5) {
            ensureVex(pl);
            renderGenericFlyer(e, vcp, light, vex, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.98f, now, 1.25f, 0.75f);
            vcp.draw();
            return;
        }

        if (mode == 7) {
            ensurePig(pl);
            renderGroundWalker(e, vcp, light, pig, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.98f, now);
            vcp.draw();
            return;
        }

        if (mode == 8) {
            ensureFrog(pl);
            renderGroundWalker(e, vcp, light, frog, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.98f, now);
            vcp.draw();
            return;
        }

        if (mode == 9) {
            ensurePufferfish(pl);
            renderPufferfishOrbit(e, vcp, light, pufferfish, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.92f, now);
            vcp.draw();
            return;
        }

        if (mode == 10) {
            ensureSlime(pl);
            renderGroundWalker(e, vcp, light, slime, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.72f, now);
            vcp.draw();
            return;
        }

        ensureFox(pl);
        renderFoxAI(e, vcp, light, fox, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.92f, now);
        vcp.draw();
    }


    private void beginReturn(Vec3d from, Vec3d prev, long now) {
        returnActive = true;
        returnStartMs = now;
        returnFrom = from;
        returnPrev = prev;
        returnHasFrom = true;
    }

    private void renderReturn(EventRender3D.Game e,
                              VertexConsumerProvider.Immediate vcp,
                              int light,
                              PlayerEntity pl,
                              float pt,
                              float dt,
                              double px, double py, double pz,
                              Vec3d forward, Vec3d right,
                              float scale,
                              long now) {

        if (raven == null) return;
        if (!returnHasFrom) {
            returnActive = false;
            returnStartMs = 0L;
            returnFrom = Vec3d.ZERO;
            returnPrev = Vec3d.ZERO;
            returnHasFrom = false;
            return;
        }

        double t = now / 1000.0;
        Vec3d target = computeLeftShoulder(px, py, pz, pl, forward, right, t);

        float p0 = (float) MathHelper.clamp((now - returnStartMs) / (float) RETURN_MS, 0.0, 1.0);
        float a = easeInOut(p0);

        Vec3d pos = lerpVec(returnFrom, target, a);
        float arc = (float) Math.sin(a * Math.PI) * 0.14f;
        pos = new Vec3d(pos.x, pos.y + arc, pos.z);

        Vec3d dir = pos.subtract(returnPrev);
        if (dir.lengthSquared() < 1.0e-8) dir = target.subtract(pos);
        if (dir.lengthSquared() < 1.0e-8) dir = new Vec3d(0.0, 0.0, 1.0);

        float yaw = yawFromDir(dir);
        float pitch = pitchFromDir(dir);

        animateParrotWingsConstant(raven, t, 2.35f);
        setEntityPoseAngles(raven, yaw, pitch, pl.age);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(raven);
        if (!(rr instanceof EntityRenderer)) return;

        MatrixStack ms = e.getMatrixStack();
        ms.push();
        linkenvisuals$translateWorld(ms, pos.x, pos.y, pos.z);
        ms.scale(scale * 1.28f, scale * 1.28f, scale * 1.28f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        VertexConsumerProvider useVcp = new TextureSwapVcp(vcp, VORON_TEX);
        renderCompat((EntityRenderer) rr, raven, yaw, pt, ms, useVcp, light);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        ms.pop();

        returnPrev = pos;

        if (p0 >= 0.9995f) {
            returnActive = false;
            returnStartMs = 0L;
            returnFrom = Vec3d.ZERO;
            returnPrev = Vec3d.ZERO;
            returnHasFrom = false;

            smoothPos = target;
            prevSmoothPos = target;
            smoothYaw = yaw;
            smoothPitch = pitch;
        }
    }

    private void renderUltimate(EventRender3D.Game e,
                                VertexConsumerProvider.Immediate vcp,
                                int light,
                                PlayerEntity pl,
                                float pt,
                                float dt,
                                double px, double py, double pz,
                                Vec3d forward, Vec3d right,
                                float radiusBase, float heightBase, float speed, float scale,
                                long now) {

        long elapsed = now - ultStartMs;
        if (elapsed >= ULT_MS) {
            stopUltSound();
            ultActive = false;

            float h = (float) pl.getHeight();
            float headY = (float) (py + h) + 0.10f;

            Vec3d top = new Vec3d(px, headY + 1.65, pz).add(forward.multiply(0.35));

            Vec3d from = top;
            Vec3d prev = top.subtract(new Vec3d(0.0, 0.0, 0.05));
            beginReturn(from, prev, now);

            ultStartMs = 0L;
            ultLastMs = 0L;
            ultStartPos = Vec3d.ZERO;
            ultStartYaw = 0.0f;
            illusions.clear();
            return;
        }

        float dt2 = ultLastMs == 0L ? dt : MathHelper.clamp((now - ultLastMs) / 1000.0f, 0.0f, 0.06f);
        ultLastMs = now;

        double t = now / 1000.0;

        Camera cam = p.mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos();

        float h = (float) pl.getHeight();
        float footY = (float) py - 0.02f;
        float headY = (float) (py + h) + 0.10f;
        Vec3d center = new Vec3d(px, py + h * 0.52, pz);

        float pAsc = MathHelper.clamp(elapsed / (float) ULT_ASCEND_MS, 0.0f, 1.0f);
        float pIn = MathHelper.clamp((elapsed - ULT_ASCEND_MS) / (float) ULT_IN_MS, 0.0f, 1.0f);
        float pHold = MathHelper.clamp((elapsed - (ULT_ASCEND_MS + ULT_IN_MS)) / (float) ULT_HOLD_MS, 0.0f, 1.0f);
        float pBreak = MathHelper.clamp((elapsed - (ULT_ASCEND_MS + ULT_IN_MS + ULT_HOLD_MS)) / (float) ULT_BREAK_MS, 0.0f, 1.0f);
        float pDisp = MathHelper.clamp((elapsed - (ULT_ASCEND_MS + ULT_IN_MS + ULT_HOLD_MS + ULT_BREAK_MS)) / (float) ULT_DISPERSE_MS, 0.0f, 1.0f);

        float eAsc = easeInOut(pAsc);
        float eIn = easeInOut(pIn);
        float eHold = easeInOut(pHold);
        float eBreak = easeInOut(pBreak);
        float eDisp = easeInOut(pDisp);

        float attach = MathHelper.clamp(eIn * 1.10f + eHold, 0.0f, 1.0f);
        float detach = MathHelper.clamp(eBreak * 0.95f + eDisp, 0.0f, 1.0f);

        float yPad = 0.10f;
        float yMin = footY + yPad;
        float yMax = headY - yPad;

        float bodyR = 0.34f + 0.18f * attach;
        float shellR0 = MathHelper.clamp(1.05f + radiusBase * 0.55f, 1.05f, 2.40f);
        float shellR1 = MathHelper.clamp(0.24f + radiusBase * 0.12f, 0.24f, 0.62f);
        float shellR = lerp(shellR0, shellR1, easeInOut(attach));

        float fogAlpha = MathHelper.clamp(attach * (1.0f - detach), 0.0f, 1.0f);
        fogAlpha = fogAlpha * fogAlpha;

        int glowCol = p.ravenColor.getValue().getRGB();
        float glowA = MathHelper.clamp((p.ravenGlow.getValue() / 100.0f) * 0.85f, 0.0f, 1.0f);

        float birdsAlpha = MathHelper.clamp((p.ravenAlpha.getValue() / 255.0f) * (0.85f + 0.25f * attach) * (1.0f - 0.80f * detach), 0.0f, 1.0f);

        MatrixStack ms = e.getMatrixStack();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);

        {
            VertexConsumerProvider useVcp = new TextureSwapVcp(vcp, VORON_TEX);
            renderMainAscendRaven(ms, useVcp, light, pl, pt, forward, right, px, py, pz, headY, eAsc, attach, detach, (float) t, scale, elapsed);
        }

        long spawnDelay = 520L;
        float spawnStep = 14.0f;

        VertexConsumerProvider useVcp = new TextureSwapVcp(vcp, VORON_TEX);

        for (int i = 0; i < illusions.size(); i++) {
            Illusion il = illusions.get(i);

            float aIn = easeInOut(MathHelper.clamp((elapsed - spawnDelay - (long) (i * spawnStep)) / 860.0f, 0.0f, 1.0f));
            float aOut = 1.0f - easeInOut(MathHelper.clamp(eDisp * 1.12f + i * 0.0085f, 0.0f, 1.0f));
            float alpha = aIn * aOut * birdsAlpha;

            if (alpha <= 0.0035f) continue;

            float layerK = (float) Math.sin(il.layer * Math.PI);
            layerK = 0.66f + 0.62f * layerK;

            float rrBase = lerp(shellR, bodyR, easeInOut(attach));
            float rr = rrBase * layerK;

            double ang = il.ang0
                    + (t * (2.35 + speed * 0.85) * il.spinMul)
                    + (Math.sin(t * 0.95 + il.seedA * 0.01) * 0.24);

            float yL = lerp(yMin, yMax, il.layer);
            float yW = (float) (Math.sin(t * 3.35 + il.seedB * 0.01) * (0.08 + 0.12 * (1.0f - detach)));

            float jitter = (float) ((0.040 + 0.085 * (1.0f - attach)) * (1.0f - 0.55f * detach));
            float jx = (float) (Math.sin(t * 6.4 + il.seedA) * jitter);
            float jz = (float) (Math.cos(t * 6.1 + il.seedB) * jitter);

            Vec3d bodyPoint = new Vec3d(
                    center.x + Math.cos(ang) * bodyR,
                    yL,
                    center.z + Math.sin(ang) * bodyR
            );

            Vec3d target = new Vec3d(
                    center.x + Math.cos(ang) * rr + jx,
                    yL + yW,
                    center.z + Math.sin(ang) * rr + jz
            );

            il.lastBodyPoint = bodyPoint;

            if (detach > 0.0f) {
                if (!il.burst) {
                    il.burst = true;
                    il.burstMs = now;
                    Vec3d out = target.subtract(center);
                    double l = out.length();
                    if (l > 1.0e-6) out = out.multiply(1.0 / l);
                    else out = new Vec3d(0.0, 1.0, 0.0);
                    Vec3d kick = out.multiply(2.8 + 7.2 * detach)
                            .add(il.fly.multiply(2.4 + 6.4 * detach))
                            .add(0.0, 0.65 + 1.05 * detach, 0.0);
                    il.vel = kick;
                    il.pos = target;
                    il.prevPos = il.pos;
                    il.hasPos = true;
                }
            }

            if (!il.hasPos) {
                il.pos = target;
                il.prevPos = target;
                il.vel = Vec3d.ZERO;
                il.hasPos = true;
            }

            il.prevPos = il.pos;

            if (!il.burst) {
                Vec3d dv = target.subtract(il.pos);
                Vec3d acc = dv.multiply(0.58 + 0.34 * attach);
                il.vel = il.vel.add(acc.multiply(dt2 * 18.0));
                il.vel = il.vel.multiply(0.84 + 0.10 * attach);
                il.pos = il.pos.add(il.vel.multiply(dt2 * 18.0));
            } else {
                il.vel = il.vel.multiply(0.986);
                il.vel = il.vel.add(0.0, -0.012 * dt2 * 60.0, 0.0);
                il.vel = il.vel.add(
                        Math.sin(t * 1.35 + il.seedA * 0.02) * 0.010 * dt2 * 60.0,
                        Math.sin(t * 1.55 + il.seedB * 0.02) * 0.006 * dt2 * 60.0,
                        Math.cos(t * 1.30 + il.seedB * 0.02) * 0.010 * dt2 * 60.0
                );
                il.pos = il.pos.add(il.vel.multiply(dt2 * (19.5 + 10.5 * detach)));
            }

            Vec3d dir = il.pos.subtract(il.prevPos);
            if (dir.lengthSquared() < 1.0e-8) dir = target.subtract(center);
            float yaw = yawFromDir(dir);
            float pitch = pitchFromDir(dir);

            animateParrotWingsConstant(il.entity, t + il.seedA * 0.004, 1.25f + 0.65f * attach);

            ms.push();
            linkenvisuals$translateWorld(ms, il.pos.x, il.pos.y, il.pos.z);
            ms.scale(scale * 1.22f, scale * 1.22f, scale * 1.22f);

            setEntityPoseAngles(il.entity, yaw, pitch, pl.age);

            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            renderCompat((EntityRenderer) p.mc.getEntityRenderDispatcher().getRenderer(il.entity), il.entity, yaw, pt, ms, useVcp, light);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            ms.pop();
        }

        vcp.draw();

        if (fogAlpha > 0.010f && detach < 0.985f && attach > 0.18f && elapsed > (ULT_ASCEND_MS + 220L)) {
            renderBirdFog(ms, cam, camPos, (float) t, attach, detach, fogAlpha, glowCol, glowA, center, yMin, yMax, bodyR);
        }

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderMainAscendRaven(MatrixStack ms,
                                       VertexConsumerProvider vcp,
                                       int light,
                                       PlayerEntity pl,
                                       float pt,
                                       Vec3d forward, Vec3d right,
                                       double px, double py, double pz,
                                       float headY,
                                       float eAsc,
                                       float attach,
                                       float detach,
                                       float t,
                                       float scale,
                                       long elapsed) {

        if (raven == null) return;

        Vec3d start = computeLeftShoulder(px, py, pz, pl, forward, right, t);
        Vec3d top = new Vec3d(px, headY + 1.65, pz).add(forward.multiply(0.35));

        Vec3d pos = lerpVec(start, top, eAsc);

        float par = MathHelper.clamp((elapsed - ULT_ASCEND_MS) / 220.0f, 0.0f, 1.0f);
        float hover = easeInOut(par) * (1.0f - MathHelper.clamp(detach * 1.10f, 0.0f, 1.0f));

        float wob = 0.06f + 0.05f * attach;
        float ox = (float) (Math.cos(t * 2.2f) * (0.12f + 0.10f * hover));
        float oz = (float) (Math.sin(t * 2.2f) * (0.12f + 0.10f * hover));
        float oy = (float) (Math.sin(t * 3.4f) * (wob + 0.03f * hover));

        pos = new Vec3d(pos.x + ox, pos.y + oy, pos.z + oz);

        Vec3d vel = new Vec3d(-Math.sin(t * 2.2f), 0.0, Math.cos(t * 2.2f));
        float yaw = yawFromDir(vel);
        float pitch = -8.0f + (float) Math.sin(t * 2.8f) * 2.8f;

        float hold = MathHelper.clamp(attach * (1.0f - 0.70f * detach), 0.0f, 1.0f);
        animateParrotWingsConstant(raven, t, 1.75f + 0.85f * hold);
        setEntityPoseAngles(raven, yaw, pitch, pl.age);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(raven);
        if (!(rr instanceof EntityRenderer)) return;

        float a = 1.0f - MathHelper.clamp(detach * 1.10f, 0.0f, 1.0f);
        if (a <= 0.01f) return;

        ms.push();
        linkenvisuals$translateWorld(ms, pos.x, pos.y, pos.z);
        ms.scale(scale * 1.35f, scale * 1.35f, scale * 1.35f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, a);

        renderCompat((EntityRenderer) rr, raven, yaw, pt, ms, vcp, light);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        ms.pop();
    }

    private void renderBirdFog(MatrixStack ms,
                               Camera cam,
                               Vec3d camPos,
                               float t,
                               float attach,
                               float detach,
                               float fogAlpha,
                               int glowCol,
                               float glowA,
                               Vec3d center,
                               float yMin,
                               float yMax,
                               float bodyR) {

        if (fogAlpha <= 0.0035f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        float dens = 1.05f + 1.55f * attach;
        float baseA = MathHelper.clamp(fogAlpha * dens, 0.0f, 1.0f);

        var immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer bb = immediate.getBuffer(RenderLayers.entityTranslucent(FEATHER_TEX));

        int perBird = 7;
        float segNoise = 0.10f + 0.18f * attach;

        for (int i = 0; i < illusions.size(); i++) {
            Illusion il = illusions.get(i);
            if (!il.hasPos) continue;
            Vec3d p0 = il.pos;
            if (p0 == null) continue;
            if (p0 == Vec3d.ZERO) continue;

            Vec3d body = il.lastBodyPoint == null ? center : il.lastBodyPoint;

            float k0 = 0.72f + 0.28f * (float) Math.sin(t * 1.9f + il.seedA * 0.01f);
            float a0 = MathHelper.clamp(baseA * k0, 0.0f, 1.0f);
            if (a0 <= 0.002f) continue;

            for (int s = 0; s < perBird; s++) {
                float w = perBird <= 1 ? 0.0f : (s / (float) (perBird - 1));
                float ww = 1.0f - w;
                ww = ww * ww;

                float nx = (float) Math.sin(t * (3.4f + s * 0.35f) + il.seedA + s * 1.7f) * segNoise;
                float ny = (float) Math.sin(t * (2.7f + s * 0.28f) + il.seedB + s * 2.3f) * (segNoise * 0.55f);
                float nz = (float) Math.cos(t * (3.2f + s * 0.31f) + il.seedB + s * 1.1f) * segNoise;

                Vec3d pp = lerpVec(p0, body, MathHelper.clamp(0.08f + 0.86f * w, 0.0f, 1.0f));
                pp = new Vec3d(pp.x + nx, pp.y + ny, pp.z + nz);

                float sBase = (0.70f + 1.25f * attach) * (1.12f - 0.10f * w);
                float sPulse = 0.90f + 0.20f * (float) Math.sin(t * 2.1f + il.seedB * 0.01f + s * 0.9f);
                float size = sBase * sPulse;
                size = Math.min(size, 2.10f + 1.05f * attach);

                float aa = MathHelper.clamp(a0 * (0.72f * ww), 0.0f, 1.0f);
                float roll = (t * (85.0f - 35.0f * w)) + (il.seedA + il.seedB) * 0.18f + s * 11.0f;

                pushBillboard(bb, ms, cam, pp.x, pp.y, pp.z, size, roll, 0.0f, 0.0f, 0.0f, aa);
            }
        }

        immediate.draw();

        if (glowA > 0.002f && fogAlpha > 0.010f && detach < 0.985f) {
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

            float rr = ((glowCol >>> 16) & 255) / 255.0f;
            float gg = ((glowCol >>> 8) & 255) / 255.0f;
            float bb0 = (glowCol & 255) / 255.0f;

            VertexConsumer bb2 = immediate.getBuffer(RenderLayers.entityTranslucent(FEATHER_TEX));

            float gBase = MathHelper.clamp(glowA * fogAlpha * (1.10f + 0.85f * attach), 0.0f, 1.0f);

            for (int i = 0; i < illusions.size(); i++) {
                Illusion il = illusions.get(i);
                if (!il.hasPos) continue;
                Vec3d p0 = il.pos;
                if (p0 == null) continue;
                if (p0 == Vec3d.ZERO) continue;

                float k = 0.60f + 0.40f * (float) Math.sin(t * 2.05f + il.seedA * 0.01f);
                float a = MathHelper.clamp(gBase * k, 0.0f, 1.0f);
                if (a <= 0.002f) continue;

                float s = (0.72f + 1.00f * attach) * (0.90f + 0.18f * (float) Math.sin(t * 1.25f + il.seedB * 0.01f));
                s = Math.min(s, 1.85f + 0.95f * attach);

                float roll = (t * 80.0f) + il.seedB * 0.2f;

                pushBillboard(bb2, ms, cam, p0.x, p0.y, p0.z, s, roll, rr, gg, bb0, a);
            }

            immediate.draw();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void pushBillboard(VertexConsumer bb,
                               MatrixStack ms,
                               Camera cam,
                               double x, double y, double z,
                               float size,
                               float rollDeg,
                               float r, float g, float b, float a) {

        if (a <= 0.001f || size <= 0.001f) return;

        ms.push();
        Vec3d cp = cam.getCameraPos();
        ms.translate(x - cp.x, y - cp.y, z - cp.z);
        ms.multiply(cam.getRotation());
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));

        Matrix4f m = ms.peek().getPositionMatrix();

        float h = size * 0.5f;

        bb.vertex(m, -h, h, 0).texture(0f, 1f).color(r, g, b, a).light(15728880).overlay(OverlayTexture.DEFAULT_UV).normal(0f, 1f, 0f);
        bb.vertex(m, h, h, 0).texture(1f, 1f).color(r, g, b, a).light(15728880).overlay(OverlayTexture.DEFAULT_UV).normal(0f, 1f, 0f);
        bb.vertex(m, h, -h, 0).texture(1f, 0f).color(r, g, b, a).light(15728880).overlay(OverlayTexture.DEFAULT_UV).normal(0f, 1f, 0f);
        bb.vertex(m, -h, -h, 0).texture(0f, 0f).color(r, g, b, a).light(15728880).overlay(OverlayTexture.DEFAULT_UV).normal(0f, 1f, 0f);

        ms.pop();
    }

    private void playUltSound() {
        stopUltSound();
        if (p.mc.world == null || p.mc.player == null) return;

        PositionedSoundInstance s1 = tryCreateMaster(ITACHI_SOUND_MINECRAFT);
        if (s1 != null) {
            ultSound = s1;
            try {
                p.mc.getSoundManager().play(ultSound);
                return;
            } catch (Throwable ignored) {
                ultSound = null;
            }
        }

        PositionedSoundInstance s2 = tryCreateMaster(ITACHI_SOUND_MRE);
        if (s2 != null) {
            ultSound = s2;
            try {
                p.mc.getSoundManager().play(ultSound);
            } catch (Throwable ignored) {
                ultSound = null;
            }
        }
    }

    private PositionedSoundInstance tryCreateMaster(Identifier id) {
        try {
            SoundEvent ev = SoundEvent.of(id);
            return PositionedSoundInstance.ui(ev, 1.0f);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void stopUltSound() {
        if (ultSound == null) return;
        try {
            p.mc.getSoundManager().stop(ultSound);
        } catch (Throwable ignored) {
        }
        ultSound = null;
    }

    private static Vec3d randomDir(Random r) {
        double x = (r.nextDouble() - 0.5) * 2.0;
        double y = (r.nextDouble() - 0.5) * 2.0;
        double z = (r.nextDouble() - 0.5) * 2.0;
        Vec3d v = new Vec3d(x, y, z);
        double l = v.length();
        if (l < 1.0e-6) return new Vec3d(0.0, 1.0, 0.0);
        return v.multiply(1.0 / l);
    }

    private void setEntityPoseAngles(Entity entity, float yaw, float pitch, int age) {
        setPrevPos(entity, entity.getX(), entity.getY(), entity.getZ());

        entity.lastYaw = entity.getYaw();
        entity.setYaw(yaw);

        if (entity instanceof ParrotEntity pe) {
            pe.bodyYaw = yaw;
            pe.headYaw = yaw;
            pe.lastBodyYaw = yaw;
            pe.lastHeadYaw = yaw;

            pe.lastPitch = pe.getPitch();
            pe.setPitch(pitch);

            pe.setOnGround(false);
            pe.setPose(EntityPose.STANDING);
            pe.age = age;
            return;
        }

        if (entity instanceof BatEntity be) {
            be.bodyYaw = yaw;
            be.headYaw = yaw;
            be.lastBodyYaw = yaw;
            be.lastHeadYaw = yaw;

            be.lastPitch = be.getPitch();
            be.setPitch(pitch);

            be.setOnGround(false);
            be.setPose(EntityPose.STANDING);
            be.age = age;
            return;
        }

        if (entity instanceof LivingEntity le) {
            try {
                le.bodyYaw = yaw;
                le.headYaw = yaw;
                le.lastBodyYaw = yaw;
                le.lastHeadYaw = yaw;
            } catch (Throwable ignored) {
            }

            le.lastPitch = le.getPitch();
            le.setPitch(pitch);
            le.setPose(EntityPose.STANDING);
            le.age = age;
            try {
                le.setOnGround(false);
            } catch (Throwable ignored) {
            }
        }
    }

    private void setEntityPoseAngles(Entity entity, float yaw, float pitch, int age, boolean onGround, EntityPose pose) {
        setPrevPos(entity, entity.getX(), entity.getY(), entity.getZ());

        entity.lastYaw = entity.getYaw();
        entity.setYaw(yaw);

        if (entity instanceof LivingEntity le) {
            try {
                le.bodyYaw = yaw;
                le.headYaw = yaw;
                le.lastBodyYaw = yaw;
                le.lastHeadYaw = yaw;
            } catch (Throwable ignored) {
            }

            le.lastPitch = le.getPitch();
            le.setPitch(pitch);
            le.setPose(pose);
            le.age = age;
            try {
                le.setOnGround(onGround);
            } catch (Throwable ignored) {
            }
        } else {
            entity.setPose(pose);
        }
    }

    private int modeFromSetting() {
        if (p.petIsBat()) return 0;
        if (p.petIsParrot()) return 1;
        if (p.petIsRaven()) return 2;
        if (p.petIsFairy()) return 3;
        if (p.petIsBee()) return 4;
        if (p.petIsVex()) return 5;
        if (p.petIsFox()) return 6;
        if (p.petIsPig()) return 7;
        if (p.petIsFrog()) return 8;
        if (p.petIsPufferfish()) return 9;
        if (p.petIsSlime()) return 10;
        return 1;
    }

    /**
     * Сущность текущего выбранного питомца для GUI-превью (создаёт при необходимости).
     * Возвращает null, если игрока нет.
     */
    public LivingEntity getPreviewPetEntity() {
        PlayerEntity pl = p.mc.player;
        if (pl == null) return null;
        return getPreviewPetEntity(p.petSkin.getValue());
    }

    /** Сущность конкретного скина для иконок в сетке GUI (без проверки включённости питомца). */
    public LivingEntity getPreviewPetEntity(CosmeticEnums.PetSkin skin) {
        PlayerEntity pl = p.mc.player;
        PREVIEW_RAVEN = skin == CosmeticEnums.PetSkin.RAVEN;
        if (pl == null || skin == null) return null;
        // switch-СТАТЕМЕНТ с отдельным return на ветку (а НЕ switch-выражение с yield). У выражения все
        // ветки сливаются в ОДИН areturn с phi-слотом, и обфускатор (ProGuard/J2C) ломал его тип до Object
        // → VerifyError «Bad return type» (краш на старте). С per-case return phi-слияния нет — байткод устойчив.
        switch (skin) {
            case BAT: ensureBat(pl); return bat;
            case PARROT: ensureParrot(pl); return parrot;
            case RAVEN: ensureRaven(pl); return raven;
            case FAIRY: ensureFairy(pl); return fairy;
            case BEE: ensureBee(pl); return bee;
            case VEX: ensureVex(pl); return vex;
            case FOX: ensureFox(pl); return fox;
            case PIG: ensurePig(pl); return pig;
            case FROG: ensureFrog(pl); return frog;
            case PUFFERFISH: ensurePufferfish(pl); return pufferfish;
            case SLIME: ensureSlime(pl); return slime;
            default: return null;
        }
    }

    private void resetMotion() {
        smoothPos = Vec3d.ZERO;
        prevSmoothPos = Vec3d.ZERO;
        smoothYaw = 0.0f;
        smoothPitch = 0.0f;

        moveSp = 0.0f;
        wantHoldTime = 0.0f;
        wantOrbitTime = 0.0f;
        holdMode = false;

        orbitAng = 0.0f;
        orbitBlend = 1.0f;

        baseYawDeg = Float.NaN;

        foxSmoothVel = Vec3d.ZERO;
        foxGroundY = Double.NaN;
        foxGroundTargetY = Double.NaN;
        foxGroundLastSampleMs = 0L;
        foxAnimPrevPos = Vec3d.ZERO;
        foxSpeedSm = 0.0f;

        walkerAnimPrevPos = Vec3d.ZERO;
        walkerSpeedSm = 0.0f;

        walkerWander.reset();
        foxWander.reset();
    }

    private void ensureBat(PlayerEntity pl) {
        if (bat == null || bat.getEntityWorld() != p.mc.world) {
            bat = createBat();
            lastBatTickMs = 0L;
        }
        bat.age = pl.age;
    }

    private void ensureParrot(PlayerEntity pl) {
        // Вариант выставляется ТОЛЬКО при создании: setParrotVariantSafe — рефлексия по getMethods()
        // (копия массива ~500 Method на вызов), на каждом кадре это сотни лишних аллокаций.
        if (parrot == null || parrot.getEntityWorld() != p.mc.world) {
            parrot = createParrot();
            setParrotVariantSafe(parrot, 0);
        }
        parrot.age = pl.age;
    }

    private void ensureRaven(PlayerEntity pl) {
        if (raven == null || raven.getEntityWorld() != p.mc.world) {
            raven = createParrot();
            setParrotVariantSafe(raven, 0);
            try {
                raven.setCustomNameVisible(false);
                raven.setCustomName(null);
            } catch (Throwable ignored) {
            }
        }
        raven.age = pl.age;
    }

    private void ensureFairy(PlayerEntity pl) {
        if (fairy == null || fairy.getEntityWorld() != p.mc.world) fairy = createAllay();
        fairy.age = pl.age;
    }

    private void ensureBee(PlayerEntity pl) {
        if (bee == null || bee.getEntityWorld() != p.mc.world) bee = createBee();
        bee.age = pl.age;
    }

    private void ensureVex(PlayerEntity pl) {
        if (vex == null || vex.getEntityWorld() != p.mc.world) vex = createVex();
        vex.age = pl.age;
    }

    private void ensureFox(PlayerEntity pl) {
        if (fox == null || fox.getEntityWorld() != p.mc.world) {
            fox = createFox();
            foxState.reset();
            foxLimb.reset();
            foxSmoothVel = Vec3d.ZERO;
            foxGroundY = Double.NaN;
            foxGroundTargetY = Double.NaN;
            foxGroundLastSampleMs = 0L;
            foxAnimPrevPos = Vec3d.ZERO;
            foxSpeedSm = 0.0f;
            // Вариант — только при создании (рефлексия по getMethods() дорога на каждом кадре).
            setFoxSnowVariantSafe(fox);
        }
        fox.age = pl.age;
        try {
            fox.setCustomNameVisible(false);
            fox.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensurePig(PlayerEntity pl) {
        if (pig == null || pig.getEntityWorld() != p.mc.world) {
            pig = createPig();
            walkerAnimPrevPos = Vec3d.ZERO;
            walkerSpeedSm = 0.0f;
        }
        pig.age = pl.age;
        try {
            pig.setCustomNameVisible(false);
            pig.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensureFrog(PlayerEntity pl) {
        if (frog == null || frog.getEntityWorld() != p.mc.world) {
            frog = createFrog();
            walkerAnimPrevPos = Vec3d.ZERO;
            walkerSpeedSm = 0.0f;
            // Вариант — только при создании (рефлексия по getMethods() дорога на каждом кадре).
            setFrogColdVariantSafe(frog);
        }
        frog.age = pl.age;
        try {
            frog.setCustomNameVisible(false);
            frog.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensurePufferfish(PlayerEntity pl) {
        if (pufferfish == null || pufferfish.getEntityWorld() != p.mc.world) {
            pufferfish = createPufferfish();
            pufferState.reset();
        }
        pufferfish.age = pl.age;
        try {
            pufferfish.setCustomNameVisible(false);
            pufferfish.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensureSlime(PlayerEntity pl) {
        if (slime == null || slime.getEntityWorld() != p.mc.world) {
            slimeJumpY = 0.0;
            slimeJumpVel = 0.0;
            slimeJumpAir = false;
            slimeLastHopMs = 0L;
            slime = createSlime();
            walkerAnimPrevPos = Vec3d.ZERO;
            walkerSpeedSm = 0.0f;
            // Размер — только при создании (рефлексия по getMethods() дорога на каждом кадре).
            setSlimeSizeSafe(slime, 1);
        }
        slime.age = pl.age;
        try {
            slime.setCustomNameVisible(false);
            slime.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private static void setSlimeSizeSafe(SlimeEntity s, int size) {
        if (s == null) return;
        try {
            s.setSize(MathHelper.clamp(size, 1, 2), false);
        } catch (Throwable ignored) {
        }
    }

    private void renderBatWithParrotLogic(EventRender3D.Game e,
                                          VertexConsumerProvider.Immediate vcp,
                                          int light,
                                          BatEntity b,
                                          PlayerEntity pl,
                                          float pt,
                                          float dt,
                                          double px, double py, double pz,
                                          Vec3d forward, Vec3d right,
                                          float baseYawDeg,
                                          float radiusBase, float heightBase, float speed, float scale,
                                          long now) {

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        setPrevPos(b, b.getX(), b.getY(), b.getZ());
        b.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        b.lastYaw = b.getYaw();
        b.setYaw(smoothYaw);
        b.bodyYaw = smoothYaw;
        b.headYaw = smoothYaw;
        b.lastBodyYaw = smoothYaw;
        b.lastHeadYaw = smoothYaw;

        b.lastPitch = b.getPitch();
        b.setPitch(smoothPitch);

        b.setOnGround(false);
        b.setPose(EntityPose.STANDING);

        batRoost.forceFlying(b);
        tickBatForAnim(b, now);
        b.age = pl.age;

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(b);
        if (!(rr instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getMatrixStack();
        ms.push();
        linkenvisuals$translateWorld(ms, smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, b, smoothYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }


    /**
     * Submits the raven/parrot into the queue that is drawn this frame. The world-render event queue
     * is already closed on 1.21.11, so the model is submitted while the local player itself is being
     * rendered (same pass as vanilla entities / the Rudy character).
     */
    /**
     * Fairy / bee / vex: submitted during the local player's entity pass, same as the raven.
     * The donor prepared these entities in the world event and drew them immediately.
     */
    private void submitFlyerPet(OrderedRenderCommandQueue queue, MatrixStack matrices,
                                net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
                                int light, int mode) {
        Entity ent = switch (mode) {
            case 3 -> fairy;
            case 4 -> bee;
            default -> vex;
        };
        if (ent == null) {
            switch (mode) {
                case 3 -> ensureFairy(p.mc.player);
                case 4 -> ensureBee(p.mc.player);
                default -> ensureVex(p.mc.player);
            }
            ent = switch (mode) {
                case 3 -> fairy;
                case 4 -> bee;
                default -> vex;
            };
            if (ent == null) return;
        }

        long now = System.currentTimeMillis();
        float dt = lastMs == 0L ? 0.016f : MathHelper.clamp((now - lastMs) / 1000.0f, 0.0f, 0.06f);
        double t = now / 1000.0;
        float pt = p.mc.getRenderTickCounter().getTickProgress(true);
        PlayerEntity pl = p.mc.player;
        Vec3d lPos = pl.getLerpedPos(pt);

        float speed = MathHelper.clamp((float) p.petSpeed.getValue(), 0.05f, 5.0f);
        float radiusBase = MathHelper.clamp((float) p.petRadius.getValue(), 0.15f, 2.5f);
        float heightBase = MathHelper.clamp((float) p.petHeight.getValue(), -0.5f, 3.0f);
        float scale = MathHelper.clamp((float) p.petScale.getValue(), 0.15f, 2.0f);
        double yawRad = Math.toRadians(baseYawDeg);
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);

        updateShoulderOrbit(pl, dt, lPos.x, lPos.y, lPos.z, forward, right,
                baseYawDeg, radiusBase, heightBase, speed, t);

        setPrevPos(ent, ent.getX(), ent.getY(), ent.getZ());
        ent.setPos(smoothPos.x, smoothPos.y, smoothPos.z);
        Vec3d vEst = smoothPos.subtract(prevSmoothPos);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
        vEst = vEst.multiply(inv);
        double vMax = 0.22 + 0.25 * speed;
        double vl = Math.sqrt(vEst.x * vEst.x + vEst.z * vEst.z + vEst.y * vEst.y);
        if (vl > vMax && vl > 1.0e-8) vEst = vEst.multiply(vMax / vl);
        try {
            ent.setVelocity(vEst.x, vEst.y * 0.45, vEst.z);
        } catch (Throwable ignored) {
        }

        float tickMul = mode == 3 ? 0.90f : mode == 4 ? 1.10f : 1.25f;
        float pitchMul = mode == 3 ? 0.55f : mode == 4 ? 0.70f : 0.75f;
        float scaleMul = mode == 3 ? 1.55f : mode == 4 ? 0.95f : 0.98f;
        setEntityPoseAngles(ent, smoothYaw, smoothPitch * pitchMul, pl.age);
        tickEntityForAnim(ent, now, tickMul);

        EntityRenderState petState = p.mc.getEntityRenderDispatcher().getAndUpdateRenderState(ent, pt);
        if (petState == null) return;
        petState.invisible = false;
        petState.displayName = null;
        petState.nameLabelPos = null;
        petState.light = light;
        petState.shadowRadius = 0f;
        petState.shadowPieces.clear();

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr instanceof LivingEntityRenderer living)) return;
        var petModel = living.getModel();
        Identifier texture = living.getTexture((net.minecraft.client.render.entity.state.LivingEntityRenderState) petState);
        float s = scale * scaleMul;
        matrices.push();
        matrices.translate(smoothPos.x - state.x, smoothPos.y - state.y + 0.55, smoothPos.z - state.z);
        matrices.scale(-1f, -1f, 1f);
        matrices.scale(s, s, s);
        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(texture), (entry, consumer) -> {
            MatrixStack draw = new MatrixStack();
            draw.peek().copy(entry);
            petModel.setAngles((net.minecraft.client.render.entity.state.LivingEntityRenderState) petState);
            petModel.render(draw, consumer, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        });
        matrices.pop();
    }

    public void submitLocalPet(OrderedRenderCommandQueue queue, MatrixStack matrices,
                               net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
                               CameraRenderState cameraState, int light) {
        if (p == null || p.mc == null || p.mc.world == null || p.mc.player == null) return;
        if (!p.petOn() || smoothPos == Vec3d.ZERO) return;

        int mode = modeFromSetting();
        if (mode == 3 || mode == 4 || mode == 5) {
            submitFlyerPet(queue, matrices, state, light, mode);
            return;
        }
        if (mode != 1 && mode != 2) return;

        ParrotEntity bird = mode == 2 ? raven : parrot;
        if (bird == null) {
            if (mode == 2) ensureRaven(p.mc.player);
            else ensureParrot(p.mc.player);
            bird = mode == 2 ? raven : parrot;
            if (bird == null) return;
        }

        float pt = p.mc.getRenderTickCounter().getTickProgress(true);
        double t = System.currentTimeMillis() / 1000.0;

        setPrevPos(bird, bird.getX(), bird.getY(), bird.getZ());
        bird.setPos(smoothPos.x, smoothPos.y, smoothPos.z);
        bird.lastYaw = bird.getYaw();
        bird.setYaw(smoothYaw);
        bird.bodyYaw = smoothYaw;
        bird.headYaw = smoothYaw;
        bird.lastBodyYaw = smoothYaw;
        bird.lastHeadYaw = smoothYaw;
        bird.lastPitch = bird.getPitch();
        bird.setPitch(smoothPitch);
        bird.setOnGround(false);
        bird.setPose(EntityPose.STANDING);
        animateParrotWingsConstant(bird, t, mode == 2 ? 1.15f : 5.25f);

        EntityRenderState petState = p.mc.getEntityRenderDispatcher().getAndUpdateRenderState(bird, pt);
        if (petState == null) return;
        petState.invisible = false;
        petState.displayName = null;
        petState.nameLabelPos = null;
        petState.light = light;
        petState.shadowRadius = 0f;
        petState.shadowPieces.clear();

        double dx = smoothPos.x - state.x;
        double dy = smoothPos.y - state.y;
        double dz = smoothPos.z - state.z;

        if (mode == 1) {
            p.mc.getEntityRenderDispatcher().render(petState, cameraState, dx, dy, dz, matrices, queue);
            return;
        }

        float s = MathHelper.clamp((float) p.petScale.getValue(), 0.15f, 2.0f) * 1.28f;
        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(bird);
        if (!(rr instanceof LivingEntityRenderer living)) return;
        matrices.push();
        matrices.translate(dx, dy + 0.55, dz);
        matrices.scale(-1f, -1f, 1f);
        matrices.scale(s, s, s);
        var petModel = living.getModel();
        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(VORON_TEX), (entry, consumer) -> {
            MatrixStack draw = new MatrixStack();
            draw.peek().copy(entry);
            petModel.setAngles((net.minecraft.client.render.entity.state.LivingEntityRenderState) petState);
            petModel.render(draw, consumer, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        });
        matrices.pop();
    }

    /** The AFTER_ENTITIES matrix is camera-relative in 1.21.11; absolute world positions must be offset. */
    private void linkenvisuals$translateWorld(MatrixStack ms, double x, double y, double z) {
        Vec3d cp = p.mc.gameRenderer.getCamera().getCameraPos();
        ms.translate(x - cp.x, y - cp.y, z - cp.z);
    }

    private void renderParrotLike(EventRender3D.Game e,
                                  VertexConsumerProvider.Immediate vcp,
                                  int light,
                                  ParrotEntity bird,
                                  PlayerEntity pl,
                                  float pt,
                                  float dt,
                                  double px, double py, double pz,
                                  Vec3d forward, Vec3d right,
                                  float baseYawDeg,
                                  float radiusBase, float heightBase, float speed, float scale,
                                  long now,
                                  boolean ravenMode) {

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        setPrevPos(bird, bird.getX(), bird.getY(), bird.getZ());
        bird.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        bird.lastYaw = bird.getYaw();
        bird.setYaw(smoothYaw);
        bird.bodyYaw = smoothYaw;
        bird.headYaw = smoothYaw;
        bird.lastBodyYaw = smoothYaw;
        bird.lastHeadYaw = smoothYaw;

        bird.lastPitch = bird.getPitch();
        bird.setPitch(smoothPitch);

        bird.setOnGround(false);
        bird.setPose(EntityPose.STANDING);

        float flapSpeed = ravenMode ? 1.15f : 5.25f;
        animateParrotWingsConstant(bird, t, flapSpeed);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(bird);
        if (!(rr instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        float s = ravenMode ? (scale * 1.28f) : scale;

        MatrixStack ms = e.getMatrixStack();
        ms.push();
        linkenvisuals$translateWorld(ms, smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(s, s, s);

        VertexConsumerProvider useVcp = ravenMode ? new TextureSwapVcp(vcp, VORON_TEX) : vcp;
        renderCompat(renderer, bird, smoothYaw, pt, ms, useVcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderGenericFlyer(EventRender3D.Game e,
                                    VertexConsumerProvider.Immediate vcp,
                                    int light,
                                    Entity ent,
                                    PlayerEntity pl,
                                    float pt,
                                    float dt,
                                    double px, double py, double pz,
                                    Vec3d forward, Vec3d right,
                                    float baseYawDeg,
                                    float radiusBase, float heightBase, float speed, float scale,
                                    long now,
                                    float tickMul,
                                    float pitchMul) {

        if (ent == null) return;

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        setPrevPos(ent, ent.getX(), ent.getY(), ent.getZ());
        ent.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        Vec3d vEst = smoothPos.subtract(prevSmoothPos);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
        vEst = vEst.multiply(inv);

        double vMax = 0.22 + 0.25 * speed;
        double vl = Math.sqrt(vEst.x * vEst.x + vEst.z * vEst.z + vEst.y * vEst.y);
        if (vl > vMax && vl > 1.0e-8) vEst = vEst.multiply(vMax / vl);

        try {
            ent.setVelocity(vEst.x, vEst.y * 0.45, vEst.z);
        } catch (Throwable ignored) {
        }

        float p0 = smoothPitch * pitchMul;
        setEntityPoseAngles(ent, smoothYaw, p0, pl.age);

        tickEntityForAnim(ent, now, tickMul);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getMatrixStack();
        ms.push();
        linkenvisuals$translateWorld(ms, smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, smoothYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderGroundWalker(EventRender3D.Game e,
                                    VertexConsumerProvider.Immediate vcp,
                                    int light,
                                    LivingEntity ent,
                                    PlayerEntity pl,
                                    float pt,
                                    float dt,
                                    double px, double py, double pz,
                                    Vec3d forward, Vec3d right,
                                    float baseYawDeg,
                                    float radiusBase, float heightBase, float speed, float scale,
                                    long now) {

        if (ent == null) return;

        // setSlimeSizeSafe убран из кадра — размер задаётся один раз при создании (ensureSlime):
        // рефлексия по getMethods() на каждый кадр дорога.

        // Логика на ФИКСИРОВАННОМ тике 20 Гц (по pl.age), рендер интерполируется lerp(prev,cur,pt) —
        // как настоящий моб: нет покадровой дрожи. Ноги гоняются раз в прошедший тик ванильным способом.
        double leash = MathHelper.clamp(radiusBase + 0.85, 1.2, 1.6);
        int steps = walkerWander.frame(p.mc.world, pl.getX(), pl.getY(), pl.getZ(), speed, leash, pl.age);
        Vec3d wpos = walkerWander.posLerp(pt);
        if (wpos == null) return;

        double yOff = 0.02;
        if (ent instanceof FrogEntity) yOff = 0.00;
        if (ent instanceof SlimeEntity) yOff = 0.01;

        // Слайм подпрыгивает поверх позиции (своя физика).
        if (ent instanceof SlimeEntity) {
            long periodMs = (long) MathHelper.clamp(520.0 - speed * 55.0, 340.0, 520.0);
            if (!slimeJumpAir) {
                if (slimeLastHopMs == 0L) slimeLastHopMs = now;
                if (now - slimeLastHopMs >= periodMs) {
                    slimeLastHopMs = now;
                    double v = 0.34 + 0.08 * MathHelper.clamp(speed, 0.05f, 5.0f);
                    slimeJumpVel = v;
                    slimeJumpAir = true;
                }
            }
            if (slimeJumpAir) {
                double g = -2.10;
                slimeJumpVel = slimeJumpVel + g * dt;
                slimeJumpY = slimeJumpY + slimeJumpVel * dt;
                if (slimeJumpY <= 0.0) {
                    slimeJumpY = 0.0;
                    slimeJumpVel = 0.0;
                    slimeJumpAir = false;
                }
            }
        }

        Vec3d finalPos = new Vec3d(wpos.x, wpos.y + yOff + (ent instanceof SlimeEntity ? slimeJumpY : 0.0), wpos.z);
        float bYaw = walkerWander.bodyYawLerp(pt);
        float hYaw = walkerWander.headYawLerp(pt);
        boolean onGround = !(ent instanceof SlimeEntity) || !slimeJumpAir;

        setPrevPos(ent, finalPos.x, finalPos.y, finalPos.z);
        ent.setPos(finalPos.x, finalPos.y, finalPos.z);
        applyYaws(ent, bYaw, hYaw);
        ent.lastPitch = 0f;
        ent.setPitch(0f);
        ent.setPose(EntityPose.STANDING);
        ent.age = pl.age;
        try {
            ent.setNoGravity(true);
            ent.setOnGround(onGround);
        } catch (Throwable ignored) {
        }

        tickEntityForAnim(ent, now, 1.0f);

        // Ноги — раз в КАЖДЫЙ прошедший тик (не кадр!), ванильным updateLimbs с фактической дистанцией
        // шага: частота ∝ скорости, без «скольжения» и без покадрового ускорения.
        ent.setPos(finalPos.x, finalPos.y, finalPos.z);
        applyYaws(ent, bYaw, hYaw);
        for (int i = 0; i < steps; i++) {
            driveLimbs(ent, walkerWander.limbPosDelta());
        }

        Object rr0 = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr0 instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getMatrixStack();
        ms.push();
        linkenvisuals$translateWorld(ms, finalPos.x, finalPos.y, finalPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, bYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Задаёт тело/голову сущности (раздельный yaw головы — «живой» доворот к игроку). */
    private static void applyYaws(LivingEntity ent, float bodyYaw, float headYaw) {
        ent.lastYaw = ent.getYaw();
        ent.setYaw(bodyYaw);
        try {
            ent.bodyYaw = bodyYaw;
            ent.lastBodyYaw = bodyYaw;
            ent.headYaw = headYaw;
            ent.lastHeadYaw = headYaw;
        } catch (Throwable ignored) {
        }
    }

    /**
     * Ванильная анимация ног: {@code updateLimbs(min(dist*4,1), 0.4, 1.0)} РАЗ В ТИК. dist — гориз.
     * дистанция шага за тик (блоков). Частота свинга ног получается ∝ скорости, как у реальных мобов.
     */
    private static void driveLimbs(LivingEntity ent, float posDeltaPerTick) {
        try {
            float f = Math.min(posDeltaPerTick * 4f, 1f);
            ent.limbAnimator.updateLimbs(f, 0.4f, 1.0f);
        } catch (Throwable ignored) {
        }
    }

    private void renderPufferfishOrbit(EventRender3D.Game e,
                                       VertexConsumerProvider.Immediate vcp,
                                       int light,
                                       PufferfishEntity ent,
                                       PlayerEntity pl,
                                       float pt,
                                       float dt,
                                       double px, double py, double pz,
                                       Vec3d forward, Vec3d right,
                                       float baseYawDeg,
                                       float radiusBase, float heightBase, float speed, float scale,
                                       long now) {

        if (ent == null) return;

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        setPrevPos(ent, ent.getX(), ent.getY(), ent.getZ());
        ent.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        Vec3d vEst = smoothPos.subtract(prevSmoothPos);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
        vEst = vEst.multiply(inv);

        double vMax = 0.20 + 0.22 * speed;
        double vl = Math.sqrt(vEst.x * vEst.x + vEst.z * vEst.z + vEst.y * vEst.y);
        if (vl > vMax && vl > 1.0e-8) vEst = vEst.multiply(vMax / vl);

        try {
            ent.setNoGravity(true);
        } catch (Throwable ignored) {
        }
        try {
            ent.setOnGround(false);
        } catch (Throwable ignored) {
        }
        try {
            ent.setVelocity(vEst.x, vEst.y * 0.40, vEst.z);
        } catch (Throwable ignored) {
        }

        float s = (float) ((Math.sin(t * 1.35) + 1.0) * 0.5);
        int puff;
        if (s < 0.33f) puff = 0;
        else if (s < 0.66f) puff = 1;
        else puff = 2;

        pufferState.apply(ent, puff);

        setEntityPoseAngles(ent, smoothYaw, 0.0f, pl.age, false, EntityPose.STANDING);

        tickEntityForAnim(ent, now, 1.0f);

        Object rr0 = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr0 instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getMatrixStack();
        ms.push();
        linkenvisuals$translateWorld(ms, smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, smoothYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static float dirYaw(Direction dir) {
        if (dir == null) return 0.0f;
        try {
            return dir.getPositiveHorizontalDegrees();
        } catch (Throwable ignored) {
        }
        try {
            Method m = dir.getClass().getMethod("asRotation");
            Object r = m.invoke(dir);
            if (r instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Method m = dir.getClass().getMethod("getHorizontal");
            Object r = m.invoke(dir);
            if (r instanceof Number n) return n.floatValue() * 90.0f;
        } catch (Throwable ignored) {
        }
        switch (dir) {
            case SOUTH: return 0.0f;
            case WEST: return 90.0f;
            case NORTH: return 180.0f;
            case EAST: return 270.0f;
            default: return 0.0f;
        }
    }

    private static float dimHeight(Object dims) {
        if (dims == null) return 0.0f;

        try {
            Field f = dims.getClass().getDeclaredField("height");
            try { f.setAccessible(true); } catch (Throwable ignored) {}
            Object v = f.get(dims);
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }

        try {
            Method m = dims.getClass().getMethod("height");
            Object r = m.invoke(dims);
            if (r instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }

        try {
            Method m = dims.getClass().getMethod("getHeight");
            Object r = m.invoke(dims);
            if (r instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }

        return 0.0f;
    }

    private void renderFoxAI(EventRender3D.Game e,
                             VertexConsumerProvider.Immediate vcp,
                             int light,
                             FoxEntity ent,
                             PlayerEntity pl,
                             float pt,
                             float dt,
                             double px, double py, double pz,
                             Vec3d forward, Vec3d right,
                             float baseYawDeg,
                             float radiusBase, float heightBase, float speed, float scale,
                             long now) {

        if (ent == null) return;

        // setFoxSnowVariantSafe убран из кадра — вариант задаётся один раз при создании (ensureFox).

        double t = now / 1000.0;

        boolean playerMoving = pl.isSprinting() || moveSp > 0.07f;

        foxState.update(pl, playerMoving, now);

        int mode = foxState.mode;

        // Ходьба (mode 0): автономное блуждание на фикс-тике 20 Гц + интерполяция рендера (как моб).
        // Сидение/сон: позиция и повороты ЗАМОРОЖЕНЫ — сырые значения без интерполяции, иначе дрожь.
        double leash = MathHelper.clamp(radiusBase + 0.85, 1.2, 1.6);
        int steps = 0;
        if (mode == 0 || !foxWander.hasPos()) {
            steps = foxWander.frame(p.mc.world, px, py, pz, speed, leash, pl.age);
        }
        Vec3d wpos = (mode == 0) ? foxWander.posLerp(pt) : foxWander.pos();
        if (wpos == null) return;
        double y0 = wpos.y;

        // Сидя (1) / спя (2): позиция wander ЗАМОРОЖЕНА, но земля под ней могла оказаться выше
        // зафиксированного Y — тогда питомец «проваливался» под блок. Каждый кадр переклеиваем его
        // к поверхности под ним (потолок сэмпла от Y игрока — он на поверхности, поэтому восстановление
        // работает, даже если Y уже ушёл в блок). Питомец render-only (в мир не добавлен) — реальной
        // коллизии у него нет; это убирает единственное «врастание» в геометрию.
        if (mode == 1 || mode == 2) {
            double gSit = PetGroundSampler.sampleGroundY(p.mc.world, wpos.x, wpos.z, py);
            if (!Double.isNaN(gSit)) y0 = gSit;
        }

        double yOff = 0.06;

        if (mode == 2) {
            double stick = -0.30;

            float hs = 0.75f;
            float hh = 0.25f;
            try {
                Object dStand = ent.getDimensions(EntityPose.STANDING);
                Object dSleep = ent.getDimensions(EntityPose.SLEEPING);
                hs = dimHeight(dStand);
                hh = dimHeight(dSleep);
            } catch (Throwable ignored) {
            }

            double extra = Math.max(0.0, (hs - hh) * 0.5);

            yOff = 0.06 + extra + stick;
        }

        Vec3d finalPos = new Vec3d(wpos.x, y0 + yOff, wpos.z);
        float bYaw = (mode == 0) ? foxWander.bodyYawLerp(pt) : foxWander.bodyYaw();
        float hYaw = (mode == 0) ? foxWander.headYawLerp(pt) : foxWander.headYaw();

        int qi = MathHelper.floor((bYaw * 4.0f / 360.0f) + 0.5f) & 3;
        Direction dir = switch (qi) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };

        boolean onGround = true;

        setPrevPos(ent, finalPos.x, finalPos.y, finalPos.z);
        ent.setPos(finalPos.x, finalPos.y, finalPos.z);
        ent.lastPitch = 0f;
        ent.setPitch(0f);
        ent.age = pl.age;

        float renderYaw;
        if (mode == 2) {
            float sleepYaw = dirYaw(dir);
            renderYaw = sleepYaw;
            applyYaws(ent, sleepYaw, sleepYaw);
            foxState.forceSleep(ent, dir);
            setEntityPoseAngles(ent, sleepYaw, 0.0f, pl.age, onGround, EntityPose.STANDING);
            try {
                ent.setNoGravity(true);
                ent.setVelocity(0.0, 0.0, 0.0);
            } catch (Throwable ignored) {
            }
            tickFoxForAnim(ent, now);
            applyYaws(ent, sleepYaw, sleepYaw);
            driveLimbs(ent, 0f);
        } else if (mode == 1) {
            renderYaw = bYaw;
            applyYaws(ent, bYaw, hYaw);
            foxState.applySittingFlag(ent);
            setEntityPoseAngles(ent, bYaw, 0.0f, pl.age, onGround, EntityPose.SITTING);
            try {
                ent.setNoGravity(true);
                ent.setVelocity(0.0, 0.0, 0.0);
            } catch (Throwable ignored) {
            }
            tickFoxForAnim(ent, now);
            applyYaws(ent, bYaw, hYaw);
            driveLimbs(ent, 0f);
        } else {
            renderYaw = bYaw;
            applyYaws(ent, bYaw, hYaw);
            foxState.applyMovingFlags(ent);
            setEntityPoseAngles(ent, bYaw, 0.0f, pl.age, onGround, EntityPose.STANDING);
            try {
                ent.setNoGravity(true);
            } catch (Throwable ignored) {
            }
            tickFoxForAnim(ent, now);
            // После tick() восстанавливаем позу/повороты и гоняем ноги раз в КАЖДЫЙ прошедший тик.
            ent.setPos(finalPos.x, finalPos.y, finalPos.z);
            applyYaws(ent, bYaw, hYaw);
            for (int i = 0; i < steps; i++) {
                driveLimbs(ent, foxWander.limbPosDelta());
            }
        }

        Object rr0 = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr0 instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getMatrixStack();
        ms.push();
        linkenvisuals$translateWorld(ms, finalPos.x, finalPos.y, finalPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, renderYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private boolean playerAirborne(PlayerEntity pl, long now) {
        try {
            if (pl.getAbilities().flying) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (pl.isGliding()) return true;
        } catch (Throwable ignored) {
        }
        return (now - lastPlayerGroundedMs) > 450L;
    }

    /**
     * Базовая позиция наземного питомца (без yOff/боба/прыжка): земля под целевой точкой с учётом
     * полёта игрока и обрывов/стен.
     * <ul>
     *   <li>Игрок в воздухе — питомец следует по высоте игрока, а не бегает по далёкой земле внизу.</li>
     *   <li>Игрок на земле, а под целью пропасть/стена (нет земли у уровня игрока) — цель подтягивается
     *       по XZ к игроку до ближайшей валидной земли, чтобы питомец жался к кромке, а не висел.</li>
     * </ul>
     */
    private Vec3d resolveGroundPos(PlayerEntity pl, Vec3d tgtXZ, double px, double py, double pz, boolean airborne) {
        net.minecraft.world.World world = p.mc.world;
        double ground = PetGroundSampler.sampleGroundYSmooth(world, tgtXZ.x, tgtXZ.z, py);

        if (airborne) {
            double y0;
            if (Double.isNaN(ground)) {
                y0 = py;
            } else {
                double above = py - ground;
                double follow = MathHelper.clamp((above - 1.5) / 2.0, 0.0, 1.0);
                y0 = ground + above * follow;
            }
            return new Vec3d(tgtXZ.x, y0, tgtXZ.z);
        }

        // Игрок на земле: цель на нормальной земле рядом с уровнем игрока — ставим туда.
        if (!Double.isNaN(ground) && Math.abs(ground - py) <= 1.5) {
            return new Vec3d(tgtXZ.x, ground, tgtXZ.z);
        }

        // Обрыв/стена под целью: шагаем от цели к игроку, ищем ближайшую землю у уровня игрока.
        for (int i = 1; i <= 5; i++) {
            double f = i / 6.0;
            double sx = tgtXZ.x + (px - tgtXZ.x) * f;
            double sz = tgtXZ.z + (pz - tgtXZ.z) * f;
            double g = PetGroundSampler.sampleGroundYSmooth(world, sx, sz, py);
            if (!Double.isNaN(g) && Math.abs(g - py) <= 1.5) {
                return new Vec3d(sx, g, sz);
            }
        }

        // Даже под игроком земли у его уровня нет (узкий столб) — ставим на уровень ног игрока рядом с ним.
        double gp = PetGroundSampler.sampleGroundYSmooth(world, px, pz, py);
        double yy = (!Double.isNaN(gp) && Math.abs(gp - py) <= 1.5) ? gp : py;
        return new Vec3d(px, yy, pz);
    }

    private void updateShoulderOrbit(PlayerEntity pl,
                                     float dt,
                                     double px, double py, double pz,
                                     Vec3d forward, Vec3d right,
                                     float baseYawDeg,
                                     float radiusBase, float heightBase, float speed,
                                     double t) {

        boolean wantHold = pl.isSprinting() || moveSp > 0.12f;
        boolean wantOrbit = !pl.isSprinting() && moveSp < 0.07f;

        if (wantHold) {
            wantHoldTime += dt;
            wantOrbitTime = 0.0f;
        } else if (wantOrbit) {
            wantOrbitTime += dt;
            wantHoldTime = 0.0f;
        } else {
            wantHoldTime = Math.max(0.0f, wantHoldTime - dt * 0.75f);
            wantOrbitTime = Math.max(0.0f, wantOrbitTime - dt * 0.75f);
        }

        if (!holdMode && wantHoldTime >= 0.16f) holdMode = true;
        if (holdMode && wantOrbitTime >= 0.20f) holdMode = false;

        float blendTau = holdMode ? 0.30f : 0.36f;
        float blendA = 1.0f - (float) Math.exp(-dt / Math.max(0.06f, blendTau));

        float wantBlend = holdMode ? 0.0f : 1.0f;
        orbitBlend = orbitBlend + (wantBlend - orbitBlend) * blendA;
        float b = easeInOut(MathHelper.clamp(orbitBlend, 0.0f, 1.0f));

        float orbitAngVel = speed * 1.25f;
        orbitAng = wrapRad(orbitAng + (orbitAngVel * dt * (0.26f + 0.26f * (1.0f - b))));

        double ox = Math.cos(orbitAng) * radiusBase;
        double oz = Math.sin(orbitAng) * radiusBase;

        double orbitY = py + pl.getStandingEyeHeight() * 0.35 + heightBase + Math.sin(t * 1.6) * 0.055;

        Vec3d orbitTarget = new Vec3d(
                px + right.x * ox + forward.x * oz,
                orbitY,
                pz + right.z * ox + forward.z * oz
        );

        Vec3d shoulderTarget = computeLeftShoulder(px, py, pz, pl, forward, right, t);
        Vec3d target = lerpVec(shoulderTarget, orbitTarget, b);

        float posTau = 0.09f + 0.08f * (1.0f - b);
        float posA2 = 1.0f - (float) Math.exp(-dt / Math.max(0.05f, posTau));

        if (smoothPos == Vec3d.ZERO) {
            prevSmoothPos = target;
            smoothPos = target;
        } else if (smoothPos.squaredDistanceTo(target) > 256.0) {
            // Телепорт/жемчуг/портал — снапаем, не тянемся через мир.
            prevSmoothPos = target;
            smoothPos = target;
        } else {
            prevSmoothPos = smoothPos;
            smoothPos = smoothPos.add(target.subtract(smoothPos).multiply(posA2));
        }

        Vec3d dPos = smoothPos.subtract(prevSmoothPos);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
        Vec3d vEst = dPos.multiply(inv);

        float orbitYaw;
        if ((vEst.x * vEst.x + vEst.z * vEst.z) > 1.0e-8) {
            orbitYaw = (float) (MathHelper.atan2(vEst.z, vEst.x) * (180.0 / Math.PI)) - 90.0f;
        } else {
            orbitYaw = smoothYaw;
        }

        float holdYaw = baseYawDeg;
        float targetYaw = lerpAngle(holdYaw, orbitYaw, b);

        float yawA = 1.0f - (float) Math.exp(-dt / 0.16f);
        smoothYaw = lerpAngle(smoothYaw, targetYaw, yawA);

        float orbitPitch = (float) MathHelper.clamp(-vEst.y * 7.5, -16.0, 16.0);
        float holdPitch = (float) Math.sin(t * 1.45) * 2.0f;
        float targetPitch = holdPitch + (orbitPitch - holdPitch) * b;

        float pitchA = 1.0f - (float) Math.exp(-dt / 0.20f);
        smoothPitch = smoothPitch + (targetPitch - smoothPitch) * pitchA;
    }

    private void tickBatForAnim(BatEntity bat, long now) {
        if (bat == null) return;
        if (lastBatTickMs == 0L) lastBatTickMs = now;
        long step = 50L;
        long elapsed = now - lastBatTickMs;
        if (elapsed < step) return;
        long ticks = Math.min(3L, elapsed / step);
        for (int i = 0; i < (int) ticks; i++) {
            try {
                bat.noClip = true;
                bat.tick();
            } catch (Throwable ignored) {
            }
        }
        lastBatTickMs += ticks * step;
    }

    private void tickFoxForAnim(FoxEntity fox, long now) {
        if (fox == null) return;
        if (lastFoxTickMs == 0L) lastFoxTickMs = now;
        long step = 50L;
        long elapsed = now - lastFoxTickMs;
        if (elapsed < step) return;
        long ticks = Math.min(2L, elapsed / step);
        for (int i = 0; i < (int) ticks; i++) {
            try {
                fox.noClip = true;
                fox.tick();
            } catch (Throwable ignored) {
            }
        }
        lastFoxTickMs += ticks * step;
    }

    private void tickEntityForAnim(Entity e, long now, float mul) {
        if (e == null) return;
        if (lastPetTickMs == 0L) lastPetTickMs = now;
        long step = 50L;
        long elapsed = now - lastPetTickMs;
        if (elapsed < step) return;
        long ticks = Math.min(6L, elapsed / step);
        int k = Math.max(1, Math.min(10, (int) Math.floor(ticks * MathHelper.clamp(mul, 0.35f, 2.35f))));
        for (int i = 0; i < k; i++) {
            try {
                e.noClip = true;
                e.tick();
            } catch (Throwable ignored) {
            }
        }
        lastPetTickMs += ticks * step;
    }

    private Vec3d computeLeftShoulder(double px, double py, double pz,
                                      PlayerEntity pl,
                                      Vec3d forward, Vec3d right,
                                      double t) {

        double baseY = py + pl.getStandingEyeHeight() * 0.74 + 0.08;
        baseY += Math.sin(t * 2.9) * 0.030;

        double lr = -0.34;
        double back = 0.12;
        double up = Math.sin(t * 1.25) * 0.016;

        Vec3d backDir = forward.multiply(-1.0);

        return new Vec3d(
                px + right.x * lr + backDir.x * back,
                baseY + up,
                pz + right.z * lr + backDir.z * back
        );
    }

    private BatEntity createBat() {
        BatEntity b = new BatEntity(EntityType.BAT, p.mc.world);
        b.setNoGravity(true);
        b.setSilent(true);
        b.setInvisible(false);
        b.setOnGround(false);
        b.setPose(EntityPose.STANDING);
        b.setCustomNameVisible(false);
        b.setCustomName(null);
        try {
            b.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            b.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return b;
    }

    private ParrotEntity createParrot() {
        ParrotEntity b = new ParrotEntity(EntityType.PARROT, p.mc.world);
        b.setNoGravity(true);
        b.setSilent(true);
        b.setInvisible(false);
        b.setOnGround(false);
        b.setPose(EntityPose.STANDING);
        b.setCustomNameVisible(false);
        b.setCustomName(null);
        try {
            b.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            b.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return b;
    }

    private AllayEntity createAllay() {
        AllayEntity a = new AllayEntity(EntityType.ALLAY, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private BeeEntity createBee() {
        BeeEntity a = new BeeEntity(EntityType.BEE, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private VexEntity createVex() {
        VexEntity a = new VexEntity(EntityType.VEX, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private FoxEntity createFox() {
        FoxEntity a = new FoxEntity(EntityType.FOX, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        setFoxSnowVariantSafe(a);
        return a;
    }

    private PigEntity createPig() {
        PigEntity a = new PigEntity(EntityType.PIG, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private FrogEntity createFrog() {
        FrogEntity a = new FrogEntity(EntityType.FROG, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        setFrogColdVariantSafe(a);
        return a;
    }

    private PufferfishEntity createPufferfish() {
        PufferfishEntity a = new PufferfishEntity(EntityType.PUFFERFISH, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private SlimeEntity createSlime() {
        SlimeEntity a = new SlimeEntity(EntityType.SLIME, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        setSlimeSizeSafe(a, 1);
        return a;
    }

    // Раньше поля искались рефлексией по yarn-именам — в production-jar (intermediary-имена)
    // ничего не находилось и крылья не махали. В 1.21.4 поля публичные: пишем напрямую,
    // компилятор сам ремапит имена (см. аналогичный фикс в renderCompat).
    private void animateParrotWingsConstant(ParrotEntity b, double t, float flapSpeed) {
        float flap = (float) (Math.sin(t * flapSpeed * 6.2) * 0.5 + 0.5);
        b.lastMaxWingDeviation = b.maxWingDeviation;
        b.maxWingDeviation = 0.10f + flap * 0.24f;
        b.lastFlapProgress = b.flapProgress;
        b.flapProgress = (float) (t * flapSpeed * 3.6);
    }

    private void initParrotWingCompat(Class<?> cls) {
        parrotWing.maxWingDeviation = findFloatField(cls, "maxWingDeviation");
        parrotWing.prevMaxWingDeviation = findFloatField(cls, "prevMaxWingDeviation");
        parrotWing.flapAngle = findFloatField(cls, "flapAngle");
        parrotWing.prevFlapAngle = findFloatField(cls, "prevFlapAngle");
        parrotWing.flapProgress = findFloatField(cls, "flapProgress");
        parrotWing.prevFlapProgress = findFloatField(cls, "prevFlapProgress");

        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() != void.class) continue;
            String n = m.getName().toLowerCase();
            if (n.contains("update") && (n.contains("limb") || n.contains("anim"))) {
                parrotWing.updateLimbs = m;
                break;
            }
        }

        parrotWing.inited = true;
    }

    // Рендер модели-сущности НАПРЯМУЮ через API 1.21.4 (getAndUpdateRenderState + render). Раньше тут
    // была рефлексия, искавшая метод по строкам в именах классов (endsWith("MatrixStack"),
    // contains("renderstate")). В dev-окружении (yarn-имена) это работало, но в production-jar классы MC
    // носят intermediary-имена (class_4587, class_10042...) → строки не совпадали → тело питомца не
    // рисовалось. Прямой вызов компилятор сам ремапит в правильные intermediary-имена.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderCompat(EntityRenderer renderer,
                              Entity entity,
                              float yaw,
                              float tickDelta,
                              Object matrices,
                              Object vcp,
                              int light) {
        try {
            var manager = p.mc.getEntityRenderDispatcher();
            EntityRenderState state = manager.getAndUpdateRenderState(entity, tickDelta);
            if (state == null) return;
            state.invisible = false;
            state.displayName = null;
            state.nameLabelPos = null;
            state.light = light;
            state.shadowRadius = 0f;
            state.shadowPieces.clear();

            // 1.21.11 deferred entity rendering: submit into the world command
            // queue instead of calling the removed 1.21.4 render(...) method.
            if (frameQueue != null && frameCameraState != null && matrices instanceof MatrixStack ms) {
                if (vcp instanceof TextureSwapVcp swap && renderer instanceof LivingEntityRenderer living) {
                    int modelColor = 0xFFFFFFFF;
                    frameQueue.submitModel(living.getModel(), state, ms,
                            RenderLayers.entityCutoutNoCull(swap.tex),
                            light, OverlayTexture.DEFAULT_UV, modelColor, null);
                } else {
                    manager.render(state, frameCameraState, 0.0, 0.0, 0.0, ms, frameQueue);
                }
                return;
            }

            // Legacy fallback (only reached when the event carries no queue).
            for (Method m : renderer.getClass().getMethods()) {
                if (m.getName().equals("render") && m.getParameterCount() == 4) {
                    m.setAccessible(true);
                    try {
                        m.invoke(renderer, state, matrices, vcp, light);
                        return;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            linkenvisuals$debugRender("[PET_DBG] renderCompat: "
                    + c.getClass().getName() + ": " + c.getMessage());
        }
    }

    @NonFinal static long linkenvisuals$lastDbgMs = 0L;
    private static void linkenvisuals$debugRender(String msg) {
        long now = System.currentTimeMillis();
        if (now - linkenvisuals$lastDbgMs < 1500L) return; // не спамим
        linkenvisuals$lastDbgMs = now;
        LinkenVisuals.LOGGER.warn(msg);
    }

    private void initCompat(Class<?> cls) {
        Method render6 = null;
        Method render4 = null;
        Method getState = null;

        for (Method m : cls.getMethods()) {
            if (!m.getName().equals("render")) continue;
            Class<?>[] p0 = m.getParameterTypes();

            if (p0.length == 6
                    && Entity.class.isAssignableFrom(p0[0])
                    && p0[1] == float.class
                    && p0[2] == float.class
                    && p0[3].getName().endsWith("MatrixStack")
                    && p0[4].getName().endsWith("VertexConsumerProvider")
                    && p0[5] == int.class) {
                render6 = m;
                break;
            }

            if (p0.length == 4
                    && p0[1].getName().endsWith("MatrixStack")
                    && p0[2].getName().endsWith("VertexConsumerProvider")
                    && p0[3] == int.class
                    && p0[0].getName().toLowerCase().contains("renderstate")) {
                render4 = m;
            }
        }

        if (render6 != null) {
            compat.render = render6;
            compat.mode = 6;
            compat.inited = true;
            LinkenVisuals.LOGGER.warn("[PET_DBG] initCompat: режим 6 (старая сигнатура) для " + cls.getName());
            return;
        }

        if (render4 != null) {
            for (Method m : cls.getMethods()) {
                Class<?>[] p0 = m.getParameterTypes();
                if (p0.length != 2) continue;
                if (!Entity.class.isAssignableFrom(p0[0])) continue;
                if (p0[1] != float.class) continue;
                if (m.getReturnType() == void.class) continue;
                String rn = m.getReturnType().getName().toLowerCase();
                if (!rn.contains("renderstate")) continue;
                getState = m;
                break;
            }

            if (getState != null) {
                compat.render = render4;
                compat.getState = getState;
                compat.mode = 4;
                compat.inited = true;
                LinkenVisuals.LOGGER.warn("[PET_DBG] initCompat: режим 4 (render-state) для " + cls.getName()
                        + " | render=" + render4.getName() + " getState=" + getState.getName());
                return;
            }
            LinkenVisuals.LOGGER.warn("[PET_DBG] initCompat: render4 найден, но getState НЕ найден для " + cls.getName());
            return;
        }
        LinkenVisuals.LOGGER.warn("[PET_DBG] initCompat: НИ render6 НИ render4 не найдены для " + cls.getName());
    }

    private static void setParrotVariantSafe(ParrotEntity p0, int idx) {
        if (p0 == null) return;
        try {
            for (Method m : ParrotEntity.class.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("variant") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    m.invoke(p0, ParrotEntity.Variant.byIndex(idx));
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setFrogColdVariantSafe(FrogEntity frog) {
        if (frog == null) return;
        try {
            for (Method m : FrogEntity.class.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("variant") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    frog.getEntityWorld().getRegistryManager()
                            .getOptional(net.minecraft.registry.RegistryKeys.FROG_VARIANT)
                            .flatMap(registry -> registry.getEntry(
                                    net.minecraft.util.Identifier.ofVanilla("warm")))
                            .ifPresent(entry -> {
                                try { m.invoke(frog, entry); } catch (Throwable ignored) {}
                            });
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setFoxSnowVariantSafe(FoxEntity fox) {
        if (fox == null) return;
        try {
            for (Method m : FoxEntity.class.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("variant") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    for (Object c : m.getParameterTypes()[0].getEnumConstants()) {
                        if (c.toString().equalsIgnoreCase("SNOW")) {
                            m.invoke(fox, c);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object findEnumConst(Class<?> enumCls, String name) {
        if (enumCls == null || !enumCls.isEnum() || name == null) return null;
        try {
            Object[] cc = enumCls.getEnumConstants();
            if (cc == null) return null;
            for (Object o : cc) {
                if (o == null) continue;
                if (o.toString().equalsIgnoreCase(name)) return o;
                if (o instanceof Enum<?> en) {
                    if (en.name().equalsIgnoreCase(name)) return o;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float getFloat(Object o, Field f) {
        if (o == null || f == null) return 0.0f;
        try {
            return f.getFloat(o);
        } catch (Throwable ignored) {
            try {
                Object v = f.get(o);
                if (v instanceof Number n) return n.floatValue();
            } catch (Throwable ignored2) {
            }
        }
        return 0.0f;
    }

    private static void trySet(Object o, Field f, float v) {
        if (o == null || f == null) return;
        try {
            f.setFloat(o, v);
            return;
        } catch (Throwable ignored) {
        }
        try {
            f.set(o, v);
        } catch (Throwable ignored) {
        }
    }

    private static Field findFloatField(Class<?> cls, String name) {
        if (cls == null || name == null) return null;
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }
                if (f.getType() == float.class || f.getType() == Float.class) return f;
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static void tryInvokeNoArg(Object o, Method m) {
        if (o == null || m == null) return;
        try {
            m.invoke(o);
        } catch (Throwable ignored) {
        }
    }

    // LivingEntity.updateLimbs: в dev-окружении yarn-имя "updateLimbs", в production-jar — intermediary
    // "method_48565". Поиск по подстроке "update"+"limb" работает только в dev; добавляем intermediary-имя,
    // иначе анимация бега (движение лап) у питомцев не запускается в собранном моде.
    private static boolean linkenvisuals$isUpdateLimbsMethod(String name) {
        if (name == null) return false;
        if (name.equals("method_48565")) return true;
        String n = name.toLowerCase();
        return n.contains("update") && n.contains("limb");
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeInOut(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static Vec3d lerpVec(Vec3d a, Vec3d b, float t) {
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    private static float lerpAngle(float a, float b, float t) {
        float d = MathHelper.wrapDegrees(b - a);
        return a + d * t;
    }

    private static float yawFromDir(Vec3d dir) {
        double x = dir.x;
        double z = dir.z;
        if (Math.abs(x) < 1.0e-9 && Math.abs(z) < 1.0e-9) return 0.0f;
        return (float) (MathHelper.atan2(z, x) * (180.0 / Math.PI)) - 90.0f;
    }

    private static float pitchFromDir(Vec3d dir) {
        double x = dir.x;
        double y = dir.y;
        double z = dir.z;
        double h = Math.sqrt(x * x + z * z);
        if (h < 1.0e-9) return y > 0.0 ? -90.0f : 90.0f;
        return (float) (-(MathHelper.atan2(y, h) * (180.0 / Math.PI)));
    }

    private static float wrapRad(float a) {
        float twoPi = (float) (Math.PI * 2.0);
        a = a % twoPi;
        if (a < 0.0f) a += twoPi;
        return a;
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static boolean isFirstPerson(MinecraftClient mc) {
        if (mc == null) return false;
        try {
            return mc.options.getPerspective().isFirstPerson();
        } catch (Throwable ignored) {
        }
        try {
            Object o = mc.options.getClass().getMethod("getPerspective").invoke(mc.options);
            if (o != null) {
                try {
                    Method m = o.getClass().getMethod("isFirstPerson");
                    Object r = m.invoke(o);
                    if (r instanceof Boolean b) return b;
                } catch (Throwable ignored2) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class Illusion {
        ParrotEntity entity;
        float seedA;
        float seedB;
        float phase;
        float spinMul;
        float u;
        float v;
        Vec3d fly;
        Vec3d pos;
        Vec3d prevPos;
        Vec3d vel;
        boolean hasPos;
        boolean burst;
        long burstMs;
        float layer;
        float ang0;
        Vec3d lastBodyPoint;
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class RenderCompat {
        boolean inited = false;
        int mode = 0;
        Class<?> rendererClass = null;
        Method render = null;
        Method getState = null;

        void reset() {
            inited = false;
            mode = 0;
            rendererClass = null;
            render = null;
            getState = null;
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class ParrotWingCompat {
        boolean inited = false;
        Class<?> entityClass = null;
        Field maxWingDeviation = null;
        Field prevMaxWingDeviation = null;
        Field flapAngle = null;
        Field prevFlapAngle = null;
        Field flapProgress = null;
        Field prevFlapProgress = null;
        Method updateLimbs = null;

        void reset() {
            inited = false;
            entityClass = null;
            maxWingDeviation = null;
            prevMaxWingDeviation = null;
            flapAngle = null;
            prevFlapAngle = null;
            flapProgress = null;
            prevFlapProgress = null;
            updateLimbs = null;
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class BatRoostCompat {
        boolean inited = false;
        Class<?> entityClass = null;
        Method setRoosting = null;
        Method setHanging = null;
        Field roosting = null;
        Field hanging = null;

        void reset() {
            inited = false;
            entityClass = null;
            setRoosting = null;
            setHanging = null;
            roosting = null;
            hanging = null;
        }

        // Прямой вызов вместо поиска метода по имени (в production intermediary-имена).
        void forceFlying(BatEntity b) {
            if (b == null) return;
            try {
                b.setRoosting(false);
            } catch (Throwable ignored) {
            }
        }
    }

    // Прямой вызов LivingEntity.updateLimbs(boolean) вместо поиска метода по имени
    // (yarn/intermediary) — работает одинаково в dev и production.
    static final class WalkerLimbCompat {
        void reset() {
        }

        void apply(LivingEntity e, boolean shouldMove, float speedSm) {
            if (e == null) return;
            try {
                e.updateLimbs(shouldMove);
            } catch (Throwable ignored) {
            }
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    // PufferfishEntity.setPuffState(int) публичный — прямой вызов вместо рефлексии.
    static final class PufferStateCompat {
        void reset() {
        }

        void apply(PufferfishEntity p, int state) {
            if (p == null) return;
            try {
                p.setPuffState(MathHelper.clamp(state, 0, 2));
            } catch (Throwable ignored) {
            }
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class FoxStateCompat {
        int mode = 0;
        long modeSinceMs = 0L;

        long lastMoveMs = 0L;
        long lastIdleMs = 0L;

        Vec3d wanderTarget = null;
        long nextWanderMs = 0L;

        Random rnd = new Random(0xC0FFEE);

        // Флаги лисы ставятся напрямую (setSitting/setCrouching/setRollingHead публичные);
        // пакетный setSleeping — через mixin-инвокер IFoxEntity (ремапится в production).
        void forceSleep(FoxEntity fox, Direction dir) {
            if (fox == null) return;
            try {
                fox.setSitting(false);
                setSleeping(fox, true);
                fox.setCrouching(false);
                fox.setRollingHead(false);
            } catch (Throwable ignored) {
            }
        }

        private static void setSleeping(FoxEntity fox, boolean sleeping) {
            try {
                ((ru.method.linkenvisuals.api.mixins.accessors.IFoxEntity) fox)
                        .linkenvisuals$setSleeping(sleeping);
            } catch (Throwable ignored) {
            }
        }

        void reset() {
            mode = 0;
            modeSinceMs = 0L;
            lastMoveMs = 0L;
            lastIdleMs = 0L;
            wanderTarget = null;
            nextWanderMs = 0L;
            rnd = new Random(0xC0FFEE ^ System.nanoTime());
        }

        void update(PlayerEntity pl, boolean playerMoving, long now) {
            if (lastMoveMs == 0L) lastMoveMs = now;
            if (lastIdleMs == 0L) lastIdleMs = now;

            if (playerMoving) lastMoveMs = now;
            else lastIdleMs = now;

            long idleFor = now - lastMoveMs;
            long moveFor = now - lastIdleMs;

            int want;
            if (idleFor > 18000L) want = 2;
            else if (idleFor > 4500L) want = 1;
            else want = 0;

            if (mode == 2 && playerMoving && moveFor > 650L) want = 0;
            if (mode == 1 && playerMoving && moveFor > 450L) want = 0;

            if (modeSinceMs == 0L) modeSinceMs = now;

            long since = now - modeSinceMs;

            if (want != mode) {
                long gate = mode == 2 ? 900L : 650L;
                if (since > gate) {
                    mode = want;
                    modeSinceMs = now;
                    wanderTarget = null;
                    nextWanderMs = 0L;
                }
            }

            if (mode == 1) {
                if (nextWanderMs == 0L) nextWanderMs = now + 12000L + (long) (rnd.nextDouble() * 9000.0);
                if (wanderTarget == null && now > nextWanderMs) nextWanderMs = now;
            }
        }

        void applySittingFlag(FoxEntity fox) {
            if (fox == null) return;
            try {
                fox.setSitting(true);
                fox.setCrouching(false);
                fox.setRollingHead(false);

                long idleFor = System.currentTimeMillis() - lastMoveMs;
                setSleeping(fox, idleFor > 38000L);
            } catch (Throwable ignored) {
            }
        }

        void applyMovingFlags(FoxEntity fox) {
            if (fox == null) return;
            try {
                fox.setSitting(false);
                setSleeping(fox, false);

                boolean crouch = false;
                if (mode == 1) {
                    long since = System.currentTimeMillis() - modeSinceMs;
                    if (since > 8000L && (since % 24000L) < 1300L) crouch = rnd.nextDouble() < 0.35;
                }
                fox.setCrouching(crouch);
                fox.setRollingHead(false);
            } catch (Throwable ignored) {
            }
        }
    }

    // То же, что WalkerLimbCompat, плюс сохранение prev-позиции и onGround-флага лисы.
    static final class FoxLimbCompat {
        void reset() {
        }

        void apply(FoxEntity fox, Vec3d prev, Vec3d cur, float dt, boolean onGround, boolean shouldMove, float speedSm) {
            if (fox == null) return;
            try {
                setPrevPos(fox, prev.x, prev.y, prev.z);
            } catch (Throwable ignored) {
            }
            try {
                fox.setOnGround(onGround);
            } catch (Throwable ignored) {
            }
            try {
                fox.updateLimbs(shouldMove);
            } catch (Throwable ignored) {
            }
        }
    }

    static final class TextureSwapVcp implements VertexConsumerProvider {
        final VertexConsumerProvider parent;
        final Identifier tex;
        final Map<RenderLayer, RenderLayer> cache = new IdentityHashMap<>();

        TextureSwapVcp(VertexConsumerProvider parent, Identifier tex) {
            this.parent = parent;
            this.tex = tex;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            if (layer == null) return parent.getBuffer(null);
            RenderLayer repl = cache.get(layer);
            if (repl == null) {
                repl = trySwap(layer, tex);
                cache.put(layer, repl);
            }
            return parent.getBuffer(repl);
        }

        private static RenderLayer trySwap(RenderLayer layer, Identifier tex) {
            if (layer == null || tex == null) return layer;

            String n;
            try {
                n = layer.toString().toLowerCase();
            } catch (Throwable ignored) {
                n = "";
            }

            boolean looksEntity = n.contains("entity") || n.contains("cutout") || n.contains("translucent") || n.contains("no_cull") || n.contains("nocull");
            if (!looksEntity) return layer;

            try {
                return RenderLayers.entityCutoutNoCull(tex);
            } catch (Throwable ignored) {
            }
            try {
                return RenderLayers.entityCutout(tex);
            } catch (Throwable ignored) {
            }
            try {
                return RenderLayers.entityTranslucent(tex);
            } catch (Throwable ignored) {
            }
            return layer;
        }
    }
}

