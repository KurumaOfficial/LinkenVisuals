package dev.linkenvisuals.pet;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.clickgui.ModuleRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Raven ULT scene (v1.0.70) — port of the donor's 19 s ultimate animation.
 *
 * <p>Triggered by a user-bindable key ("Pet ULT" in the Cosmetics settings) while the Raven pet
 * skin is selected. Timeline (donor constants): 2.4 s ascend, 2.6 s pull-in, 9.2 s hold,
 * 2.8 s break, 3.0 s disperse — the scene cuts at 19 s while the disperse is still fading
 * (donor behaviour). 42 fake parrot "illusions" orbit the player in layered shells, a feather
 * fog and an additive glow are drawn as camera-facing billboards, and the hero raven flies
 * back to the shoulder in an 850 ms return animation when the scene ends.
 */
final class PetUltimate {

    // --- donor timeline constants (Raven.java lines 59-66) ---
    static final long ULT_MS = 19000L;
    static final long ULT_ASCEND_MS = 2400L;
    static final long ULT_IN_MS = 2600L;
    static final long ULT_HOLD_MS = 9200L;
    static final long ULT_BREAK_MS = 2800L;
    static final long ULT_DISPERSE_MS = 3000L;
    static final long RETURN_MS = 850L;
    static final int ULT_RAVENS = 42;
    static final long SPAWN_DELAY_MS = 520L;
    static final float SPAWN_STEP_MS = 14.0f;
    static final float SPAWN_FADE_MS = 860.0f;

    /** Donor feather texture ({@code pero.png}) and the Itachi sound ({@code itachi.ogg}). */
    private static final Identifier PERO_TEXTURE = Identifier.of("linkenvisuals", "textures/pet/pero.png");
    private static final Identifier ULT_SOUND_ID = Identifier.of("linkenvisuals", "pet/ult");

    private static final RenderPipeline FEATHER_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/pet_feather"))
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(LinkenVisuals.id("pipeline/pet_glow"))
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private static final RenderLayer FEATHER_LAYER = RenderLayer.of("linkenvisuals_pet_feather",
        RenderSetup.builder(FEATHER_PIPELINE).texture("Sampler0", PERO_TEXTURE).translucent()
            .expectedBufferSize(65536).build());

    private static final RenderLayer GLOW_LAYER = RenderLayer.of("linkenvisuals_pet_glow",
        RenderSetup.builder(GLOW_PIPELINE).texture("Sampler0", PERO_TEXTURE)
            .expectedBufferSize(8192).build());

    private final List<Illusion> illusions = new ArrayList<>();

    private boolean active;
    private long startMs;
    private long lastMs;

    private boolean returnActive;
    private long returnStartMs;
    private Vec3d returnFrom = Vec3d.ZERO;
    private Vec3d returnPrev = Vec3d.ZERO;
    private boolean returnHasFrom;

    private boolean keyDown;
    private PositionedSoundInstance sound;

    boolean active() {
        return active;
    }

    boolean returnActive() {
        return returnActive;
    }

    /** Immediately drops the scene, the illusions and the sound (disconnect, skin switch, failure). */
    void reset() {
        stopSound();
        active = false;
        returnActive = false;
        startMs = 0L;
        lastMs = 0L;
        returnStartMs = 0L;
        returnFrom = Vec3d.ZERO;
        returnPrev = Vec3d.ZERO;
        returnHasFrom = false;
        illusions.clear();
    }

    // ------------------------------------------------------------------
    // Trigger (donor's configurable raven-scene bind)
    // ------------------------------------------------------------------

    void pollKey(MinecraftClient client, int keyCode) {
        if (client == null || client.getWindow() == null || keyCode < 0) {
            keyDown = false;
            return;
        }
        boolean down = GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
        if (down && !keyDown) {
            trigger(client);
        }
        keyDown = down;
    }

    private void trigger(MinecraftClient client) {
        if (active || client.world == null || client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        returnActive = false;
        returnStartMs = 0L;
        returnFrom = Vec3d.ZERO;
        returnPrev = Vec3d.ZERO;
        returnHasFrom = false;

        active = true;
        startMs = now;
        lastMs = now;
        illusions.clear();

        Random r = new Random(now ^ 0x9E3779B97F4A7C15L);
        for (int i = 0; i < ULT_RAVENS; i++) {
            ParrotEntity e = new ParrotEntity(EntityType.PARROT, client.world);
            e.setNoGravity(true);
            e.setSilent(true);
            e.setInvisible(false);
            e.setInvulnerable(true);
            e.setCustomNameVisible(false);
            e.setPose(EntityPose.STANDING);
            e.setOnGround(false);
            e.noClip = true;
            if (e instanceof MobEntity mob) {
                mob.setAiDisabled(true);
            }

            Illusion il = new Illusion();
            il.entity = e;
            il.seedA = r.nextFloat() * 1000.0f;
            il.seedB = r.nextFloat() * 1000.0f;
            il.phase = r.nextFloat();
            il.spinMul = 0.85f + r.nextFloat() * 1.55f;
            il.u = r.nextFloat() - 0.5f;
            il.v = r.nextFloat() - 0.5f;
            il.fly = randomDir(r).multiply(0.55 + r.nextDouble() * 1.35);
            il.pos = Vec3d.ZERO;
            il.prevPos = Vec3d.ZERO;
            il.vel = Vec3d.ZERO;
            il.hasPos = false;
            il.burst = false;
            il.burstMs = 0L;
            il.layer = frac((i * 0.6180339887f) + (il.seedA * 0.0007f));
            il.ang0 = (float) (i * (Math.PI * 2.0 / ULT_RAVENS));
            illusions.add(il);
        }

        playSound(client);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Returns {@code true} when the scene (or the return flight) draws the raven this frame. */
    boolean render(PetRig rig, VertexConsumerProvider consumers, EntityRenderManager manager,
                   OrderedRenderCommandQueue queue, CameraRenderState cameraState, MatrixStack matrices,
                   LivingEntity raven, PlayerEntity player, float pt, float dt,
                   double px, double py, double pz, Vec3d forward, Vec3d side,
                   float radiusBase, float speed, float scale, double t, long nowMs, Vec3d eye) {
        if (active) {
            renderScene(rig, consumers, manager, queue, matrices, raven, player, pt, dt,
                px, py, pz, forward, side, radiusBase, speed, scale, t, nowMs, eye);
            return true;
        }
        if (returnActive) {
            renderReturn(rig, manager, queue, matrices, raven, player, pt, px, py, pz, forward, side,
                scale, nowMs, eye);
            return true;
        }
        return false;
    }

    private void renderScene(PetRig rig, VertexConsumerProvider consumers, EntityRenderManager manager,
                             OrderedRenderCommandQueue queue, MatrixStack matrices, LivingEntity raven,
                             PlayerEntity player, float pt, float dt, double px, double py, double pz,
                             Vec3d forward, Vec3d side, float radiusBase, float speed, float scale,
                             double t, long nowMs, Vec3d eye) {
        long elapsed = nowMs - startMs;
        float h = player.getDimensions(EntityPose.STANDING).height();
        float headY = (float) (py + h) + 0.10f;

        if (elapsed >= ULT_MS) {
            // Scene over: cut the sound, hand the raven back to the return flight.
            stopSound();
            active = false;
            Vec3d top = new Vec3d(px, headY + 1.65, pz).add(forward.multiply(0.35));
            beginReturn(top, top.subtract(new Vec3d(0.0, 0.0, 0.05)), nowMs);
            startMs = 0L;
            lastMs = 0L;
            illusions.clear();
            return;
        }

        float dt2 = lastMs == 0L ? dt : MathHelper.clamp((nowMs - lastMs) / 1000.0f, 0.0f, 0.06f);
        lastMs = nowMs;

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        float footY = (float) py - 0.02f;
        Vec3d center = new Vec3d(px, py + h * 0.52, pz);

        float pAsc = phaseProgress(elapsed, 0L, ULT_ASCEND_MS);
        float pIn = phaseProgress(elapsed, ULT_ASCEND_MS, ULT_IN_MS);
        float pHold = phaseProgress(elapsed, ULT_ASCEND_MS + ULT_IN_MS, ULT_HOLD_MS);
        float pBreak = phaseProgress(elapsed, ULT_ASCEND_MS + ULT_IN_MS + ULT_HOLD_MS, ULT_BREAK_MS);
        float pDisp = phaseProgress(elapsed,
            ULT_ASCEND_MS + ULT_IN_MS + ULT_HOLD_MS + ULT_BREAK_MS, ULT_DISPERSE_MS);

        float eAsc = easeInOut(pAsc);
        float eIn = easeInOut(pIn);
        float eHold = easeInOut(pHold);
        float eBreak = easeInOut(pBreak);
        float eDisp = easeInOut(pDisp);

        float attach = attachOf(eIn, eHold);
        float detach = detachOf(eBreak, eDisp);

        float yPad = 0.10f;
        float yMin = footY + yPad;
        float yMax = headY - yPad;

        float bodyR = 0.34f + 0.18f * attach;
        float shellR0 = MathHelper.clamp(1.05f + radiusBase * 0.55f, 1.05f, 2.40f);
        float shellR1 = MathHelper.clamp(0.24f + radiusBase * 0.12f, 0.24f, 0.62f);
        float shellR = lerp(shellR0, shellR1, easeInOut(attach));

        float fogAlpha = MathHelper.clamp(attach * (1.0f - detach), 0.0f, 1.0f);
        fogAlpha *= fogAlpha;

        float glowP = MathHelper.clamp(ModuleRegistry.cosmeticPetGlow().value(), 0.0f, 100.0f);
        float alphaP = MathHelper.clamp(ModuleRegistry.cosmeticPetAlpha().value(), 0.0f, 100.0f);
        int glowCol = ModuleRegistry.cosmeticPetGlowColor().rgb();
        float glowA = MathHelper.clamp((glowP / 100.0f) * 0.85f, 0.0f, 1.0f);
        float birdsAlpha = MathHelper.clamp((alphaP / 100.0f) * (0.85f + 0.25f * attach)
            * (1.0f - 0.80f * detach), 0.0f, 1.0f);

        float tf = (float) t;

        renderMainAscendRaven(rig, manager, queue, matrices, raven, player, pt, forward, side,
            px, py, pz, headY, eAsc, attach, detach, tf, scale, elapsed, eye);

        for (int i = 0; i < illusions.size(); i++) {
            Illusion il = illusions.get(i);

            float alpha = birdAlpha(i, elapsed, eDisp, birdsAlpha);
            if (alpha <= 0.0035f) {
                continue;
            }

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

            // This loop runs 42 times per frame while the scene is active: the hot math stays on
            // primitives (v1.0.71 allocation audit); Vec3d objects appear only on state changes.
            double cosA = Math.cos(ang);
            double sinA = Math.sin(ang);

            double bx = center.x + cosA * bodyR;
            double by = yL;
            double bz = center.z + sinA * bodyR;

            double tx = center.x + cosA * rr + jx;
            double ty = yL + yW;
            double tz = center.z + sinA * rr + jz;

            il.lastBodyX = bx;
            il.lastBodyY = by;
            il.lastBodyZ = bz;

            if (detach > 0.0f && !il.burst) {
                il.burst = true;
                il.burstMs = nowMs;
                double ox = tx - center.x;
                double oy = ty - center.y;
                double oz = tz - center.z;
                double l = Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (l > 1.0e-6) {
                    double inv = 1.0 / l;
                    ox *= inv;
                    oy *= inv;
                    oz *= inv;
                } else {
                    ox = 0.0;
                    oy = 1.0;
                    oz = 0.0;
                }
                il.vel = new Vec3d(ox, oy, oz).multiply(2.8 + 7.2 * detach)
                    .add(il.fly.multiply(2.4 + 6.4 * detach))
                    .add(0.0, 0.65 + 1.05 * detach, 0.0);
                il.pos = new Vec3d(tx, ty, tz);
                il.prevPos = il.pos;
                il.hasPos = true;
            }

            if (!il.hasPos) {
                il.pos = new Vec3d(tx, ty, tz);
                il.prevPos = il.pos;
                il.vel = Vec3d.ZERO;
                il.hasPos = true;
            }

            il.prevPos = il.pos;

            if (!il.burst) {
                double k = 0.58 + 0.34 * attach;
                double s = dt2 * 18.0;
                double f = 0.84 + 0.10 * attach;
                double vx = (il.vel.x + (tx - il.pos.x) * k * s) * f;
                double vy = (il.vel.y + (ty - il.pos.y) * k * s) * f;
                double vz = (il.vel.z + (tz - il.pos.z) * k * s) * f;
                il.vel = new Vec3d(vx, vy, vz);
                il.pos = new Vec3d(il.pos.x + vx * s, il.pos.y + vy * s, il.pos.z + vz * s);
            } else {
                double vx = il.vel.x * 0.986 + Math.sin(t * 1.35 + il.seedA * 0.02) * 0.010 * dt2 * 60.0;
                double vy = il.vel.y * 0.986 + (-0.012 * dt2 * 60.0)
                    + Math.sin(t * 1.55 + il.seedB * 0.02) * 0.006 * dt2 * 60.0;
                double vz = il.vel.z * 0.986 + Math.cos(t * 1.30 + il.seedB * 0.02) * 0.010 * dt2 * 60.0;
                il.vel = new Vec3d(vx, vy, vz);
                double ps = dt2 * (19.5 + 10.5 * detach);
                il.pos = new Vec3d(il.pos.x + vx * ps, il.pos.y + vy * ps, il.pos.z + vz * ps);
            }

            double dx = il.pos.x - il.prevPos.x;
            double dy = il.pos.y - il.prevPos.y;
            double dz = il.pos.z - il.prevPos.z;
            if (dx * dx + dy * dy + dz * dz < 1.0e-8) {
                dx = tx - center.x;
                dy = ty - center.y;
                dz = tz - center.z;
            }
            float yaw = yawFromDir(dx, dz);
            float pitch = pitchFromDir(dx, dy, dz);

            PetRig.animateParrotWings((ParrotEntity) il.entity, t + il.seedA * 0.004, 1.25f + 0.65f * attach);
            PetRig.applyPose(il.entity, il.pos, yaw, yaw, pitch, player.age, false, EntityPose.STANDING);

            rig.renderUltRaven(manager, queue, matrices, il.entity, il.pos, pt, scale * 1.22f, eye, alpha);
        }

        if (fogAlpha > 0.010f && detach < 0.985f && attach > 0.18f && elapsed > (ULT_ASCEND_MS + 220L)) {
            renderBirdFog(consumers, matrices, camera, camPos, tf, attach, detach, fogAlpha,
                glowCol, glowA, yMin, yMax, bodyR);
        }
    }

    private void renderMainAscendRaven(PetRig rig, EntityRenderManager manager, OrderedRenderCommandQueue queue,
                                       MatrixStack matrices, LivingEntity raven, PlayerEntity player, float pt,
                                       Vec3d forward, Vec3d side, double px, double py, double pz, float headY,
                                       float eAsc, float attach, float detach, float t, float scale,
                                       long elapsed, Vec3d eye) {
        if (raven == null) {
            return;
        }

        Vec3d start = PetRig.computeLeftShoulder(px, py, pz, player, forward, side, t);
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
        PetRig.animateParrotWings((ParrotEntity) raven, t, 1.75f + 0.85f * hold);
        PetRig.applyPose(raven, pos, yaw, yaw, pitch, player.age, false, EntityPose.STANDING);

        float a = 1.0f - MathHelper.clamp(detach * 1.10f, 0.0f, 1.0f);
        if (a <= 0.01f) {
            return;
        }

        rig.renderUltRaven(manager, queue, matrices, raven, pos, pt, scale * 1.35f, eye, a);
    }

    private void renderReturn(PetRig rig, EntityRenderManager manager, OrderedRenderCommandQueue queue,
                              MatrixStack matrices, LivingEntity raven, PlayerEntity player, float pt,
                              double px, double py, double pz, Vec3d forward, Vec3d side, float scale,
                              long nowMs, Vec3d eye) {
        if (raven == null) {
            return;
        }
        if (!returnHasFrom) {
            returnActive = false;
            returnStartMs = 0L;
            returnFrom = Vec3d.ZERO;
            returnPrev = Vec3d.ZERO;
            returnHasFrom = false;
            return;
        }

        double t = nowMs / 1000.0;
        Vec3d target = PetRig.computeLeftShoulder(px, py, pz, player, forward, side, t);

        float p0 = MathHelper.clamp((nowMs - returnStartMs) / (float) RETURN_MS, 0.0f, 1.0f);
        float a = easeInOut(p0);

        Vec3d pos = lerpVec(returnFrom, target, a);
        pos = new Vec3d(pos.x, pos.y + Math.sin(a * Math.PI) * 0.14, pos.z);

        Vec3d dir = pos.subtract(returnPrev);
        if (dir.lengthSquared() < 1.0e-8) {
            dir = target.subtract(pos);
        }
        if (dir.lengthSquared() < 1.0e-8) {
            dir = new Vec3d(0.0, 0.0, 1.0);
        }

        float yaw = yawFromDir(dir);
        float pitch = pitchFromDir(dir);

        PetRig.animateParrotWings((ParrotEntity) raven, t, 2.35f);
        PetRig.applyPose(raven, pos, yaw, yaw, pitch, player.age, false, EntityPose.STANDING);

        rig.renderUltRaven(manager, queue, matrices, raven, pos, pt, scale * 1.28f, eye, 1.0f);

        returnPrev = pos;

        if (p0 >= 0.9995f) {
            returnActive = false;
            returnStartMs = 0L;
            returnFrom = Vec3d.ZERO;
            returnPrev = Vec3d.ZERO;
            returnHasFrom = false;
            rig.snapFlyTo(target, yaw, pitch);
        }
    }

    private void renderBirdFog(VertexConsumerProvider consumers, MatrixStack matrices, Camera camera,
                               Vec3d camPos, float t, float attach, float detach, float fogAlpha,
                               int glowCol, float glowA, float yMin, float yMax, float bodyR) {
        if (fogAlpha <= 0.0035f) {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // Camera.horizontalPlane is the forward vector, right = forward x up.
        Vector3fc forwardAxis = camera.getHorizontalPlane();
        Vector3fc up = camera.getVerticalPlane();
        Vector3fc right = new Vector3f(forwardAxis).cross(up);

        float dens = 1.05f + 1.55f * attach;
        float baseA = MathHelper.clamp(fogAlpha * dens, 0.0f, 1.0f);

        VertexConsumer feathers = consumers.getBuffer(FEATHER_LAYER);

        int perBird = 7;
        float segNoise = 0.10f + 0.18f * attach;

        for (int i = 0; i < illusions.size(); i++) {
            Illusion il = illusions.get(i);
            if (!il.hasPos) {
                continue;
            }
            Vec3d p0 = il.pos;
            if (p0 == null || p0 == Vec3d.ZERO) {
                continue;
            }

            double bodyX = il.lastBodyX;
            double bodyY = il.lastBodyY;
            double bodyZ = il.lastBodyZ;

            float k0 = 0.72f + 0.28f * (float) Math.sin(t * 1.9f + il.seedA * 0.01f);
            float a0 = MathHelper.clamp(baseA * k0, 0.0f, 1.0f);
            if (a0 <= 0.002f) {
                continue;
            }

            for (int s = 0; s < perBird; s++) {
                float w = perBird <= 1 ? 0.0f : (s / (float) (perBird - 1));
                float ww = 1.0f - w;
                ww = ww * ww;

                float nx = (float) Math.sin(t * (3.4f + s * 0.35f) + il.seedA + s * 1.7f) * segNoise;
                float ny = (float) Math.sin(t * (2.7f + s * 0.28f) + il.seedB + s * 2.3f) * (segNoise * 0.55f);
                float nz = (float) Math.cos(t * (3.2f + s * 0.31f) + il.seedB + s * 1.1f) * segNoise;

                double kk = MathHelper.clamp(0.08f + 0.86f * w, 0.0f, 1.0f);
                double fx = p0.x + (bodyX - p0.x) * kk + nx;
                double fy = p0.y + (bodyY - p0.y) * kk + ny;
                double fz = p0.z + (bodyZ - p0.z) * kk + nz;

                float sBase = (0.70f + 1.25f * attach) * (1.12f - 0.10f * w);
                float sPulse = 0.90f + 0.20f * (float) Math.sin(t * 2.1f + il.seedB * 0.01f + s * 0.9f);
                float size = sBase * sPulse;
                size = Math.min(size, 2.10f + 1.05f * attach);

                float aa = MathHelper.clamp(a0 * (0.72f * ww), 0.0f, 1.0f);
                float roll = (t * (85.0f - 35.0f * w)) + (il.seedA + il.seedB) * 0.18f + s * 11.0f;

                pushBillboard(feathers, matrix, right, up, camPos, fx, fy, fz, size, roll,
                    0.0f, 0.0f, 0.0f, aa);
            }
        }

        if (glowA > 0.002f && fogAlpha > 0.010f && detach < 0.985f) {
            VertexConsumer glow = consumers.getBuffer(GLOW_LAYER);

            float cr = ((glowCol >>> 16) & 255) / 255.0f;
            float cg = ((glowCol >>> 8) & 255) / 255.0f;
            float cb = (glowCol & 255) / 255.0f;

            float gBase = MathHelper.clamp(glowA * fogAlpha * (1.10f + 0.85f * attach), 0.0f, 1.0f);

            for (int i = 0; i < illusions.size(); i++) {
                Illusion il = illusions.get(i);
                if (!il.hasPos) {
                    continue;
                }
                Vec3d p0 = il.pos;
                if (p0 == null || p0 == Vec3d.ZERO) {
                    continue;
                }

                float k = 0.60f + 0.40f * (float) Math.sin(t * 2.05f + il.seedA * 0.01f);
                float a = MathHelper.clamp(gBase * k, 0.0f, 1.0f);
                if (a <= 0.002f) {
                    continue;
                }

                float s = (0.72f + 1.00f * attach)
                    * (0.90f + 0.18f * (float) Math.sin(t * 1.25f + il.seedB * 0.01f));
                s = Math.min(s, 1.85f + 0.95f * attach);

                float roll = (t * 80.0f) + il.seedB * 0.2f;

                pushBillboard(glow, matrix, right, up, camPos, p0.x, p0.y, p0.z, s, roll, cr, cg, cb, a);
            }
        }
    }

    private static void pushBillboard(VertexConsumer vc, Matrix4f matrix, Vector3fc right, Vector3fc up,
                                      Vec3d eye, double x, double y, double z, float size, float rollDeg,
                                      float r, float g, float b, float a) {
        if (a <= 0.001f || size <= 0.001f) {
            return;
        }

        float half = size * 0.5f;
        float roll = (float) Math.toRadians(rollDeg);
        float cos = (float) Math.cos(roll);
        float sin = (float) Math.sin(roll);

        float cx = (float) (x - eye.x);
        float cy = (float) (y - eye.y);
        float cz = (float) (z - eye.z);

        corner(vc, matrix, right, up, cx, cy, cz, -half, half, cos, sin, 0.0f, 1.0f, r, g, b, a);
        corner(vc, matrix, right, up, cx, cy, cz, half, half, cos, sin, 1.0f, 1.0f, r, g, b, a);
        corner(vc, matrix, right, up, cx, cy, cz, half, -half, cos, sin, 1.0f, 0.0f, r, g, b, a);
        corner(vc, matrix, right, up, cx, cy, cz, -half, -half, cos, sin, 0.0f, 0.0f, r, g, b, a);
    }

    private static void corner(VertexConsumer vc, Matrix4f matrix, Vector3fc right, Vector3fc up,
                               float cx, float cy, float cz, float lx, float ly, float cos, float sin,
                               float u, float v, float r, float g, float b, float a) {
        float rx = lx * cos - ly * sin;
        float ry = lx * sin + ly * cos;
        vc.vertex(matrix,
                cx + right.x() * rx + up.x() * ry,
                cy + right.y() * rx + up.y() * ry,
                cz + right.z() * rx + up.z() * ry)
            .texture(u, v)
            .color(r, g, b, a);
    }

    // ------------------------------------------------------------------
    // Sound (donor's master-positioned Itachi theme)
    // ------------------------------------------------------------------

    private void playSound(MinecraftClient client) {
        stopSound();
        try {
            SoundEvent event = SoundEvent.of(ULT_SOUND_ID);
            // Donor used PositionedSoundInstance.master(...); yarn build.4 renamed the same
            // master-category / no-attenuation factory to ui(...).
            PositionedSoundInstance instance = PositionedSoundInstance.ui(event, 1.0f);
            client.getSoundManager().play(instance);
            sound = instance;
        } catch (Exception e) {
            // Cosmetic cue only: a torn-down sound system (disconnect / shutdown)
            // must not break the scene — log so the failure is not silent.
            LinkenVisuals.LOGGER.debug("Pet ULT sound play failed: {}", e.toString());
            sound = null;
        }
    }

    private void stopSound() {
        if (sound == null) {
            return;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.getSoundManager().stop(sound);
            }
        } catch (Exception e) {
            // Sound system already gone (disconnect / shutdown) — nothing to stop.
            LinkenVisuals.LOGGER.debug("Pet ULT sound stop failed: {}", e.toString());
        }
        sound = null;
    }

    private void beginReturn(Vec3d from, Vec3d prev, long now) {
        returnActive = true;
        returnStartMs = now;
        returnFrom = from;
        returnPrev = prev;
        returnHasFrom = true;
    }

    // ------------------------------------------------------------------
    // Donor scene math (kept as pure statics: unit-tested in PetUltimateTest)
    // ------------------------------------------------------------------

    static float easeInOut(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Clamped phase progress in {@code [0, 1]} for a window starting at {@code offsetMs}. */
    static float phaseProgress(long elapsed, long offsetMs, long durMs) {
        return MathHelper.clamp((elapsed - offsetMs) / (float) durMs, 0.0f, 1.0f);
    }

    static float attachOf(float eIn, float eHold) {
        return MathHelper.clamp(eIn * 1.10f + eHold, 0.0f, 1.0f);
    }

    static float detachOf(float eBreak, float eDisp) {
        return MathHelper.clamp(eBreak * 0.95f + eDisp, 0.0f, 1.0f);
    }

    /** Per-bird spawn fade-in (staggered 14 ms apart, 520 ms lead-in, 860 ms fade). */
    static float spawnIn(int index, long elapsed) {
        return easeInOut(MathHelper.clamp(
            (elapsed - SPAWN_DELAY_MS - (long) (index * SPAWN_STEP_MS)) / SPAWN_FADE_MS, 0.0f, 1.0f));
    }

    /** Per-bird disperse fade-out (later birds leave first, donor offset 0.0085 per index). */
    static float fadeOut(int index, float eDisp) {
        return 1.0f - easeInOut(MathHelper.clamp(eDisp * 1.12f + index * 0.0085f, 0.0f, 1.0f));
    }

    /** Combined per-bird alpha; the render loop skips anything at or below 0.0035. */
    static float birdAlpha(int index, long elapsed, float eDisp, float birdsAlpha) {
        return spawnIn(index, elapsed) * fadeOut(index, eDisp) * birdsAlpha;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static Vec3d lerpVec(Vec3d a, Vec3d b, float t) {
        return new Vec3d(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t);
    }

    private static float yawFromDir(double x, double z) {
        if (Math.abs(x) < 1.0e-9 && Math.abs(z) < 1.0e-9) {
            return 0.0f;
        }
        return (float) (MathHelper.atan2(z, x) * (180.0 / Math.PI)) - 90.0f;
    }

    private static float yawFromDir(Vec3d dir) {
        return yawFromDir(dir.x, dir.z);
    }

    private static float pitchFromDir(double x, double y, double z) {
        double h = Math.sqrt(x * x + z * z);
        if (h < 1.0e-9) {
            return y > 0.0 ? -90.0f : 90.0f;
        }
        return (float) (-(MathHelper.atan2(y, h) * (180.0 / Math.PI)));
    }

    private static float pitchFromDir(Vec3d dir) {
        return pitchFromDir(dir.x, dir.y, dir.z);
    }

    private static Vec3d randomDir(Random r) {
        double x = (r.nextDouble() - 0.5) * 2.0;
        double y = (r.nextDouble() - 0.5) * 2.0;
        double z = (r.nextDouble() - 0.5) * 2.0;
        Vec3d v = new Vec3d(x, y, z);
        double l = v.length();
        if (l < 1.0e-6) {
            return new Vec3d(0.0, 1.0, 0.0);
        }
        return v.multiply(1.0 / l);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static final class Illusion {
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
        // Feather-fog anchor, kept as primitives: the update loop runs per frame per bird.
        double lastBodyX;
        double lastBodyY;
        double lastBodyZ;
    }
}
