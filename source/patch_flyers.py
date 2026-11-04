from pathlib import Path

p = Path("src/main/java/ru/method/linkenvisuals/modules/impl/render/cosmetic/Raven.java")
t = p.read_text(encoding="utf-8")

# --- extend submitLocalPet with flyer modes ---
old = '''        int mode = modeFromSetting();
        if (mode != 1 && mode != 2) return;

        ParrotEntity bird = mode == 2 ? raven : parrot;'''
new = '''        int mode = modeFromSetting();
        if (mode == 3 || mode == 4 || mode == 5) {
            submitFlyerPet(queue, matrices, state, light, mode);
            return;
        }
        if (mode != 1 && mode != 2) return;

        ParrotEntity bird = mode == 2 ? raven : parrot;'''
assert t.count(old) == 1
t = t.replace(old, new, 1)

# --- add submitFlyerPet before submitLocalPet ---
marker = '''    /**
     * Submits the raven/parrot into the queue that is drawn this frame.'''
helper = '''    /**
     * Fairy / bee / vex: same entity-pass submission as the raven. The donor prepared the entity
     * in the world event and drew it immediately; here the prepared state is submitted while the
     * local player is rendered, which is the queue that actually reaches the screen in 1.21.11.
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
        float scaleMul = mode == 3 ? 0.92f : mode == 4 ? 0.95f : 0.98f;
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
        matrices.translate(smoothPos.x - state.x, smoothPos.y - state.y, smoothPos.z - state.z);
        matrices.scale(s, s, s);
        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(texture), (entry, consumer) -> {
            MatrixStack draw = new MatrixStack();
            draw.peek().copy(entry);
            petModel.setAngles((net.minecraft.client.render.entity.state.LivingEntityRenderState) petState);
            petModel.render(draw, consumer, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        });
        matrices.pop();
    }

''' + marker
assert t.count(marker) == 1
t = t.replace(marker, helper, 1)
p.write_text(t, encoding="utf-8", newline="\n")
print("ok")
