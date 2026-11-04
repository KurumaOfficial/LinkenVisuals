package ru.method.linkenvisuals.utils.world;

import ru.method.linkenvisuals.api.mixins.accessors.IGameRenderer;
import ru.method.linkenvisuals.utils.Wrapper;
import lombok.experimental.UtilityClass;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.concurrent.atomic.AtomicReference;

@UtilityClass
public class WorldUtils implements Wrapper {

    public final AtomicReference<Matrix4f> lastWorld = new AtomicReference<>(new Matrix4f());
    public final AtomicReference<Matrix4f> lastProj = new AtomicReference<>(new Matrix4f());
    public final AtomicReference<Matrix4f> lastModelView = new AtomicReference<>(new Matrix4f());

    public static boolean isBoxVisible(net.minecraft.util.math.Box box) {
        net.minecraft.client.render.Frustum frustum =
                net.minecraft.client.MinecraftClient.getInstance().worldRenderer.getCapturedFrustum();
        return frustum == null || frustum.isVisible(box);
    }

    private static final ThreadLocal<ProjectCache> PROJECT_CACHE = ThreadLocal.withInitial(ProjectCache::new);

    private static final class ProjectCache {
        private final int[] viewport = new int[4];
        private final Vector3f target = new Vector3f();
        private final Vector4f coords = new Vector4f();
        private final Matrix4f combined = new Matrix4f();
        private int viewportWidth = -1;
        private int viewportHeight = -1;
    }

    public Vec3d getPosition(Vec3d pos) {
        RenderSystem.assertOnRenderThread();
        Camera camera = mc.getEntityRenderDispatcher().camera != null ? mc.getEntityRenderDispatcher().camera : mc.gameRenderer.getCamera();
        if (camera == null) {
            return new Vec3d(0, 0, 0);
        }

        ProjectCache cache = PROJECT_CACHE.get();
        int fbWidth = mc.getWindow().getFramebufferWidth();
        int fbHeight = mc.getWindow().getFramebufferHeight();
        cache.viewport[0] = 0;
        cache.viewport[1] = 0;
        cache.viewport[2] = fbWidth;
        cache.viewport[3] = fbHeight;

        double deltaX = pos.x - camera.getCameraPos().x;
        double deltaY = pos.y - camera.getCameraPos().y;
        double deltaZ = pos.z - camera.getCameraPos().z;

        // 1.21.11: the world MatrixStack exposed by Fabric is a fresh identity
        // stack; the camera view rotation lives in RenderSystem's model-view
        // matrix. Using the identity matrix here projected every marker with a
        // missing camera rotation (TargetEsp/waypoints drifted away from their
        // world position), so project through the cached camera model-view.
        Matrix4f modelView = lastModelView.get();
        Matrix4f projMatrix = lastProj.get();

        cache.combined.set(projMatrix).mul(modelView);
        cache.coords.set((float) deltaX, (float) deltaY, (float) deltaZ, 1.0f);
        cache.combined.transform(cache.coords);

        if (cache.coords.w <= 0.05f) {
            return new Vec3d(-1000, -1000, -1);
        }

        float invW = 1.0f / cache.coords.w;
        float ndcX = cache.coords.x * invW;
        float ndcY = cache.coords.y * invW;
        float ndcZ = cache.coords.z * invW;

        double scaledWidth = mc.getWindow().getScaledWidth();
        double scaledHeight = mc.getWindow().getScaledHeight();

        double screenX = (ndcX * 0.5 + 0.5) * scaledWidth;
        double screenY = (0.5 - ndcY * 0.5) * scaledHeight;
        double screenZ = (ndcZ * 0.5 + 0.5);

        return new Vec3d(screenX, screenY, screenZ);
    }

    public double getScale(Vec3d position, double size) {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return 10.0;
        Vec3d cam = camera.getCameraPos();
        double distance = Math.max(0.1, cam.distanceTo(position));
        double fov = ((IGameRenderer) mc.gameRenderer).getFov$drug(camera, mc.getRenderTickCounter().getTickProgress(true), true);
        return Math.max(10f, 1000 / distance) * (size / 30f) / (fov == 70 ? 1 : fov / 70.0f);
    }
}
