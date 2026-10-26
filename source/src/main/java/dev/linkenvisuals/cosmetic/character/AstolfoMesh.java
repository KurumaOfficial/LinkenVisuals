package dev.linkenvisuals.cosmetic.character;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linkenvisuals.render.state.RudyRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Astolfo rigged 3D character mesh: CC-BY Sketchfab model skinned on the CPU
 * each frame and driven by entity animation state.
 */
public final class AstolfoMesh {

    private static final String BASE = "/assets/linkenvisuals/models/astolfo/";
    private static final String FALLBACK_BASE = "/assets/evaware/models/astolfo/";
    private static final int MAX_JOINTS = 256;

    private static float[] basePos;
    private static float[] baseNrm;
    private static float[] baseUv;
    private static int[] vertJoint;
    private static float[] vertWeight;
    private static int[] matVertOffset;
    private static int[] matVertCount;
    private static Identifier[] textures;

    private static int jointCount;
    private static int[] jointParent;
    private static float[] bindT;
    private static float[] bindR;
    private static float[] bindIbm;
    private static float[] bindParentQuat;
    private static float normScale;
    private static float normOx;
    private static float normOy;
    private static float normOz;
    private static float normYmin;

    private static int jThighL = -1;
    private static int jThighR = -1;
    private static int jCalfL = -1;
    private static int jCalfR = -1;
    private static int jArmL = -1;
    private static int jArmR = -1;
    private static int jForeL = -1;
    private static int jForeR = -1;
    private static int jNeck = -1;
    private static int jHead = -1;
    private static int jSpine = -1;
    private static int jPelvis = -1;

    private static float[] skinMats;
    private static float[] outPos;
    private static float[] outNrm;
    private static float[] rcurCache;
    private static float[][] worldCache;
    private static boolean loaded;
    private static boolean failed;
    private static boolean texturesRegistered;

    private AstolfoMesh() {
    }

    public static boolean active(Object state) {
        return state instanceof RudyRenderState custom && "Astolfo".equals(custom.linkenvisuals$getCustomCharacter());
    }

    public static boolean available() {
        if (failed) {
            return false;
        }
        ensureLoaded();
        return loaded && basePos != null && basePos.length > 0;
    }

    private static synchronized void ensureLoaded() {
        if (loaded || failed) return;
        try {
            loadRig();
            loadMesh();
            loaded = true;
        } catch (Exception exception) {
            failed = true;
            System.err.println("LinkenVisuals: failed to load Astolfo character: " + exception);
            basePos = new float[0];
        }
    }

    private static InputStream openStream(String relativePath) {
        InputStream stream = AstolfoMesh.class.getResourceAsStream(BASE + relativePath);
        if (stream == null) {
            stream = AstolfoMesh.class.getResourceAsStream(FALLBACK_BASE + relativePath);
        }
        return stream;
    }

    private static void loadRig() throws Exception {
        JsonObject root;
        try (InputStream stream = openStream("rig.json")) {
            if (stream == null) throw new java.io.IOException("rig.json missing");
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonArray parents = root.getAsJsonArray("parents");
        JsonArray t = root.getAsJsonArray("t");
        JsonArray r = root.getAsJsonArray("r");
        JsonArray ibm = root.getAsJsonArray("ibm");
        JsonArray names = root.getAsJsonArray("names");
        jointCount = parents.size();
        jointParent = new int[jointCount];
        bindT = new float[jointCount * 3];
        bindR = new float[jointCount * 4];
        bindIbm = new float[jointCount * 16];
        for (int i = 0; i < jointCount; i++) {
            jointParent[i] = parents.get(i).getAsInt();
            JsonArray tv = t.get(i).getAsJsonArray();
            bindT[i * 3] = tv.get(0).getAsFloat();
            bindT[i * 3 + 1] = tv.get(1).getAsFloat();
            bindT[i * 3 + 2] = tv.get(2).getAsFloat();
            JsonArray rv = r.get(i).getAsJsonArray();
            bindR[i * 4] = rv.get(0).getAsFloat();
            bindR[i * 4 + 1] = rv.get(1).getAsFloat();
            bindR[i * 4 + 2] = rv.get(2).getAsFloat();
            bindR[i * 4 + 3] = rv.get(3).getAsFloat();
            float[] flat = bindIbm;
            int o = i * 16;
            List<Float> row = new ArrayList<>(16);
            for (JsonElement e : ibm.get(i).getAsJsonArray()) row.add(e.getAsFloat());
            for (int k = 0; k < 16; k++) flat[o + k] = row.get(k);
        }
        String[] jointNames = new String[jointCount];
        for (int i = 0; i < jointCount; i++) jointNames[i] = names.get(i).getAsString();
        JsonObject norm = root.getAsJsonObject("norm");
        normScale = norm.get("s").getAsFloat();
        normOx = norm.get("ox").getAsFloat();
        normOz = norm.get("oz").getAsFloat();
        normYmin = norm.get("ymin").getAsFloat();
        float normFeet = norm.get("feet").getAsFloat();
        normOy = normFeet;
        jThighL = find(jointNames, "L_Thigh");
        jThighR = find(jointNames, "R_Thigh");
        jCalfL = find(jointNames, "L_Calf");
        jCalfR = find(jointNames, "R_Calf");
        jArmL = find(jointNames, "L_UpperArm");
        jArmR = find(jointNames, "R_UpperArm");
        jForeL = find(jointNames, "L_Forearm");
        jForeR = find(jointNames, "R_Forearm");
        jNeck = find(jointNames, "Neck1");
        jHead = find(jointNames, "Head1");
        jSpine = find(jointNames, "Spine2");
        jPelvis = find(jointNames, "Pelvis");

        bindParentQuat = new float[jointCount * 4];
        for (int i = 0; i < jointCount; i++) {
            float[] q = new float[]{0, 0, 0, 1};
            int p = jointParent[i];
            List<float[]> chain = new ArrayList<>();
            while (p >= 0) {
                chain.add(0, new float[]{bindR[p * 4], bindR[p * 4 + 1], bindR[p * 4 + 2], bindR[p * 4 + 3]});
                p = jointParent[p];
            }
            for (float[] rq : chain) q = qmul(q, rq);
            bindParentQuat[i * 4] = q[0];
            bindParentQuat[i * 4 + 1] = q[1];
            bindParentQuat[i * 4 + 2] = q[2];
            bindParentQuat[i * 4 + 3] = q[3];
        }
        skinMats = new float[jointCount * 16];
        rcurCache = new float[jointCount * 4];
        worldCache = new float[jointCount][16];
    }

    private static int find(String[] names, String part) {
        for (int i = 0; i < names.length; i++) {
            if (names[i] != null && names[i].contains(part)) return i;
        }
        return -1;
    }

    private static void loadMesh() throws Exception {
        List<Float> pos = new ArrayList<>();
        List<Float> nrm = new ArrayList<>();
        List<Float> uv = new ArrayList<>();
        List<Integer> jn = new ArrayList<>();
        List<Float> wt = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<Identifier> tex = new ArrayList<>();
        try (InputStream raw = openStream("mesh_astolfo.bin")) {
            if (raw == null) throw new java.io.IOException("mesh_astolfo.bin missing");
            try (DataInputStream in = new DataInputStream(raw)) {
                int mats = in.readInt();
                for (int m = 0; m < mats; m++) {
                    int tris = in.readInt();
                    offsets.add(pos.size() / 3);
                    counts.add(tris * 3);
                    tex.add(Identifier.of("linkenvisuals", "models/astolfo/tex" + m + ".png"));
                    for (int t = 0; t < tris * 3; t++) {
                        float x = in.readFloat();
                        float y = in.readFloat();
                        float z = in.readFloat();
                        float u = in.readFloat();
                        float v = in.readFloat();
                        float nx = in.readFloat();
                        float ny = in.readFloat();
                        float nz = in.readFloat();
                        int j0 = in.readUnsignedByte();
                        int j1 = in.readUnsignedByte();
                        int j2 = in.readUnsignedByte();
                        int j3 = in.readUnsignedByte();
                        float w0 = in.readFloat();
                        float w1 = in.readFloat();
                        float w2 = in.readFloat();
                        float w3 = in.readFloat();
                        pos.add(x);
                        pos.add(y);
                        pos.add(z);
                        uv.add(u);
                        uv.add(v);
                        nrm.add(nx);
                        nrm.add(ny);
                        nrm.add(nz);
                        jn.add(j0);
                        jn.add(j1);
                        jn.add(j2);
                        jn.add(j3);
                        wt.add(w0);
                        wt.add(w1);
                        wt.add(w2);
                        wt.add(w3);
                    }
                }
            }
        }
        int verts = pos.size() / 3;
        basePos = toArray(pos);
        baseUv = toArray(uv);
        baseNrm = toArray(nrm);
        vertJoint = jn.stream().mapToInt(Integer::intValue).toArray();
        vertWeight = toArray(wt);
        matVertOffset = offsets.stream().mapToInt(Integer::intValue).toArray();
        matVertCount = counts.stream().mapToInt(Integer::intValue).toArray();
        textures = tex.toArray(new Identifier[0]);
        outPos = new float[verts * 3];
        outNrm = new float[verts * 3];
    }

    private static float[] toArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static void registerTextures() {
        if (texturesRegistered) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return;
        for (int m = 0; m < textures.length; m++) {
            Identifier tex = textures[m];
            try (InputStream stream = openStream("tex" + m + ".png")) {
                if (stream != null) {
                    NativeImage img = NativeImage.read(stream);
                    client.getTextureManager().registerTexture(tex, new NativeImageBackedTexture(tex::toString, img));
                }
            } catch (Exception ignored) {
            }
        }
        texturesRegistered = true;
    }

    public static void submit(OrderedRenderCommandQueue queue, MatrixStack matrices, PlayerEntityModel model,
                              PlayerEntityRenderState state, int light, int overlay, int color) {
        ensureLoaded();
        if (basePos == null || basePos.length == 0) return;
        registerTextures();

        model.setAngles(state);
        pose(model, state);

        boolean translucent = state.invisible && !state.invisibleToPlayer;

        for (int m = 0; m < matVertOffset.length; m++) {
            int from = matVertOffset[m];
            int count = matVertCount[m];
            Identifier tex = textures[m];
            RenderLayer layer = translucent
                    ? RenderLayers.entityTranslucent(tex)
                    : RenderLayers.entityCutoutNoCull(tex);
            queue.submitCustom(matrices, layer, (entry, consumer) -> {
                for (int v = from; v < from + count; v += 3) {
                    vertex(consumer, entry, v, light, overlay, color);
                    vertex(consumer, entry, v + 1, light, overlay, color);
                    vertex(consumer, entry, v + 2, light, overlay, color);
                    vertex(consumer, entry, v + 2, light, overlay, color);
                }
            });
        }
    }

    private static void pose(PlayerEntityModel vanilla, PlayerEntityRenderState state) {
        float stride = Math.min(state.limbSwingAmplitude, 1.0f);
        float swing = (float) Math.cos(state.limbSwingAnimationProgress * 0.6662f) * 1.2f * stride;
        float[] rcur = rcurCache;
        System.arraycopy(bindR, 0, rcur, 0, bindR.length);

        applyRotX(jThighL, swing * 0.55f, rcur);
        applyRotX(jThighR, -swing * 0.55f, rcur);
        applyRotX(jCalfL, -Math.max(0f, (float) Math.cos(state.limbSwingAnimationProgress * 0.6662f + 0.7f)) * 0.8f * stride - 0.06f, rcur);
        applyRotX(jCalfR, -Math.max(0f, (float) Math.cos(state.limbSwingAnimationProgress * 0.6662f + Math.PI + 0.7f)) * 0.8f * stride - 0.06f, rcur);

        float armL = -swing * 0.45f;
        float armR = swing * 0.45f;
        if (state.handSwingProgress > 0) {
            float hit = (float) Math.sin(Math.sqrt(state.handSwingProgress) * Math.PI) * 1.1f;
            if (state.preferredArm == Arm.RIGHT) armL += hit;
            else armR += hit;
        }
        applyRotX(jArmL, armL, rcur);
        applyRotX(jArmR, armR, rcur);
        applyRotX(jForeL, 0.28f + Math.abs(armL) * 0.35f, rcur);
        applyRotX(jForeR, 0.28f + Math.abs(armR) * 0.35f, rcur);

        float headYaw = -(vanilla.head.yaw) * 0.8f;
        float headPitch = (vanilla.head.pitch) * 0.8f;
        applyRotHead(jHead, headYaw, headPitch, rcur);
        applyRotHead(jNeck, headYaw * 0.3f, headPitch * 0.3f, rcur);
        double time = System.nanoTime() / 1_000_000_000.0;
        applyRotX(jSpine, (float) (0.02 * Math.sin(time * 1.6) * (1f - stride) + 0.03 * Math.sin(state.limbSwingAnimationProgress * 0.6662f) * stride), rcur);
        PELVIS_T[0] = 0f;
        PELVIS_T[1] = (float) (-0.035 * Math.abs(Math.cos(state.limbSwingAnimationProgress * 0.6662f)) * stride);
        PELVIS_T[2] = 0f;

        // world + skin matrices
        for (int k = 0; k < jointCount; k++) {
            float[] m = worldCache[k];
            compose(m, k == jPelvis ? PELVIS_T : null, rcur, k);
            int p = jointParent[k];
            if (p >= 0) {
                float[] parentM = worldCache[p];
                matMulInPlace(parentM, m);
            }
            skinMats(k, m);
        }

        int verts = basePos.length / 3;
        for (int v = 0; v < verts; v++) {
            float px = basePos[v * 3], py = basePos[v * 3 + 1], pz = basePos[v * 3 + 2];
            float nx = baseNrm[v * 3], ny = baseNrm[v * 3 + 1], nz = baseNrm[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, rx = 0, ry = 0, rz = 0;
            for (int a = 0; a < 4; a++) {
                float w = vertWeight[v * 4 + a];
                if (w == 0) continue;
                int jk = vertJoint[v * 4 + a];
                if (jk < 0 || jk >= jointCount) continue;
                int o = jk * 16;
                ox += w * (skinMats[o] * px + skinMats[o + 1] * py + skinMats[o + 2] * pz + skinMats[o + 3]);
                oy += w * (skinMats[o + 4] * px + skinMats[o + 5] * py + skinMats[o + 6] * pz + skinMats[o + 7]);
                oz += w * (skinMats[o + 8] * px + skinMats[o + 9] * py + skinMats[o + 10] * pz + skinMats[o + 11]);
                rx += w * (skinMats[o] * nx + skinMats[o + 1] * ny + skinMats[o + 2] * nz);
                ry += w * (skinMats[o + 4] * nx + skinMats[o + 5] * ny + skinMats[o + 6] * nz);
                rz += w * (skinMats[o + 8] * nx + skinMats[o + 9] * ny + skinMats[o + 10] * nz);
            }
            outPos[v * 3] = -(ox * normScale + normOx);
            outPos[v * 3 + 1] = normOy - (oy - normYmin) * normScale;
            outPos[v * 3 + 2] = -(oz * normScale + normOz);
            float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl < 1e-6f) {
                rx = 0;
                ry = -1;
                rz = 0;
                rl = 1f;
            }
            outNrm[v * 3] = -rx / rl;
            outNrm[v * 3 + 1] = -ry / rl;
            outNrm[v * 3 + 2] = -rz / rl;
        }
    }

    private static final float[] PELVIS_T = new float[3];

    private static void applyRotX(int joint, float angle, float[] rcur) {
        float s = (float) Math.sin(angle * 0.5f);
        float c = (float) Math.cos(angle * 0.5f);
        applyRot(joint, s, 0f, 0f, c, rcur);
    }

    private static void applyRotHead(int joint, float yaw, float pitch, float[] rcur) {
        float sy = (float) Math.sin(yaw * 0.5f);
        float cy = (float) Math.cos(yaw * 0.5f);
        float sx = (float) Math.sin(pitch * 0.5f);
        float cx = (float) Math.cos(pitch * 0.5f);
        float qx = cy * sx;
        float qy = sy * cx;
        float qz = -sy * sx;
        float qw = cy * cx;
        applyRot(joint, qx, qy, qz, qw, rcur);
    }

    private static void applyRot(int joint, float qw0, float qw1, float qw2, float qw3, float[] rcur) {
        if (joint < 0) return;
        int o4 = joint * 4;
        float p0 = bindParentQuat[o4];
        float p1 = bindParentQuat[o4 + 1];
        float p2 = bindParentQuat[o4 + 2];
        float p3 = bindParentQuat[o4 + 3];

        // p_conj = (-p0, -p1, -p2, p3)
        // d = p_conj * qworld
        float dx = p3 * qw0 + (-p0) * qw3 + (-p1) * qw2 - (-p2) * qw1;
        float dy = p3 * qw1 - (-p0) * qw2 + (-p1) * qw3 + (-p2) * qw0;
        float dz = p3 * qw2 + (-p0) * qw1 - (-p1) * qw0 + (-p2) * qw3;
        float dw = p3 * qw3 - (-p0) * qw0 - (-p1) * qw1 - (-p2) * qw2;

        // d2 = d * p
        float d2x = dw * p0 + dx * p3 + dy * p2 - dz * p1;
        float d2y = dw * p1 - dx * p2 + dy * p3 + dz * p0;
        float d2z = dw * p2 + dx * p1 - dy * p0 + dz * p3;
        float d2w = dw * p3 - dx * p0 - dy * p1 - dz * p2;

        // out = d2 * bindR
        float r0 = bindR[o4];
        float r1 = bindR[o4 + 1];
        float r2 = bindR[o4 + 2];
        float r3 = bindR[o4 + 3];

        rcur[o4] = d2w * r0 + d2x * r3 + d2y * r2 - d2z * r1;
        rcur[o4 + 1] = d2w * r1 - d2x * r2 + d2y * r3 + d2z * r0;
        rcur[o4 + 2] = d2w * r2 + d2x * r1 - d2y * r0 + d2z * r3;
        rcur[o4 + 3] = d2w * r3 - d2x * r0 - d2y * r1 - d2z * r2;
    }

    private static void compose(float[] m, float[] extraT, float[] rcur, int k) {
        float[] r = new float[]{rcur[k * 4], rcur[k * 4 + 1], rcur[k * 4 + 2], rcur[k * 4 + 3]};
        float x = r[0], y = r[1], z = r[2], w = r[3];
        m[0] = 1 - 2 * (y * y + z * z);
        m[1] = 2 * (x * y - z * w);
        m[2] = 2 * (x * z + y * w);
        m[3] = bindT[k * 3] + (extraT != null ? extraT[0] : 0);
        m[4] = 2 * (x * y + z * w);
        m[5] = 1 - 2 * (x * x + z * z);
        m[6] = 2 * (y * z - x * w);
        m[7] = bindT[k * 3 + 1] + (extraT != null ? extraT[1] : 0);
        m[8] = 2 * (x * z - y * w);
        m[9] = 2 * (y * z + x * w);
        m[10] = 1 - 2 * (x * x + y * y);
        m[11] = bindT[k * 3 + 2] + (extraT != null ? extraT[2] : 0);
        m[12] = 0;
        m[13] = 0;
        m[14] = 0;
        m[15] = 1;
    }

    private static final float[] MAT_TEMP = new float[16];

    private static void matMulInPlace(float[] a, float[] b) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                MAT_TEMP[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j] + a[i * 4 + 3] * b[12 + j];
            }
        }
        System.arraycopy(MAT_TEMP, 0, b, 0, 16);
    }

    private static void skinMats(int k, float[] world) {
        float[] o = skinMats;
        int s = k * 16;
        float[] ib = bindIbm;
        int b = k * 16;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                o[s + i * 4 + j] = world[i * 4] * ib[b + j] + world[i * 4 + 1] * ib[b + 4 + j]
                        + world[i * 4 + 2] * ib[b + 8 + j] + world[i * 4 + 3] * ib[b + 12 + j];
            }
        }
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, int v, int light, int overlay, int color) {
        consumer.vertex(entry, outPos[v * 3], outPos[v * 3 + 1], outPos[v * 3 + 2])
                .color(color)
                .texture(baseUv[v * 2], baseUv[v * 2 + 1])
                .overlay(overlay)
                .light(light)
                .normal(entry, outNrm[v * 3], outNrm[v * 3 + 1], outNrm[v * 3 + 2]);
    }

    private static float[] qmul(float[] a, float[] b) {
        return new float[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }
}
