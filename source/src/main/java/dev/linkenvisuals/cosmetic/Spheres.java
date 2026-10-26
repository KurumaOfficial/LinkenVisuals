package dev.linkenvisuals.cosmetic;

import org.joml.Vector3f;

/**
 * Spheres (Phase 5.6): three orbiting spheres around the player. Donor's
 * {@code Spheres} (670 lines: trails + SphereCache + per-player states)
 * simplified to our procedural system: 3 spheres at different phases,
 * solid shading via {@link Material}. No trails (follow-up).
 */
public final class Spheres {
    private static final int COUNT = 3;
    private static final int LAT_SEG = 12;
    private static final int LON_SEG = 16;
    private static final float ORBIT_R = 0.65f;
    private static final float SPHERE_R = 0.09f;

    private static final float[] PHASE = {0.0f, 2.0943951f, 4.1887903f};
    private static final float[] SPEED = {0.9f, -0.7f, 0.5f};
    private static final float[] TILT = {0.3f, -0.4f, 0.2f};

    private static final float[] SIN_THETA = new float[LAT_SEG + 1];
    private static final float[] COS_THETA = new float[LAT_SEG + 1];
    private static final float[] SIN_PHI = new float[LON_SEG + 1];
    private static final float[] COS_PHI = new float[LON_SEG + 1];

    static {
        for (int lat = 0; lat <= LAT_SEG; lat++) {
            double theta = Math.PI * lat / LAT_SEG;
            SIN_THETA[lat] = (float) Math.sin(theta);
            COS_THETA[lat] = (float) Math.cos(theta);
        }
        for (int lon = 0; lon <= LON_SEG; lon++) {
            double phi = (Math.PI * 2.0) * lon / LON_SEG;
            SIN_PHI[lon] = (float) Math.sin(phi);
            COS_PHI[lon] = (float) Math.cos(phi);
        }
    }

    private static final Vector3f a = new Vector3f();
    private static final Vector3f b = new Vector3f();
    private static final Vector3f c = new Vector3f();
    private static final Vector3f d = new Vector3f();
    private static final Vector3f center = new Vector3f();

    private Spheres() {
    }

    public static void render(CosmeticContext ctx) {
        float s = ctx.size;
        float orbitR = ORBIT_R * s;
        float sphereR = SPHERE_R * s;
        float y = ctx.height + 0.1f * s;

        for (int k = 0; k < COUNT; k++) {
            float angle = ctx.time * SPEED[k] + PHASE[k];
            float tilt = TILT[k];
            float cx = (float) Math.cos(angle) * orbitR;
            float cz = (float) Math.sin(angle) * orbitR;
            float cy = y + (float) Math.sin(angle * 0.7f) * tilt * orbitR;
            center.set(cx, cy, cz);

            int base = ctx.color;
            int deep = Material.deepen(base, 0.45f);
            int light = Material.lift(base, 0.3f);

            // Precomputed lat/lon sphere quads
            for (int lat = 0; lat < LAT_SEG; lat++) {
                float st0 = SIN_THETA[lat], ct0 = COS_THETA[lat];
                float st1 = SIN_THETA[lat + 1], ct1 = COS_THETA[lat + 1];
                float y0 = center.y + sphereR * ct0;
                float y1 = center.y + sphereR * ct1;
                float r0 = sphereR * st0;
                float r1 = sphereR * st1;

                float t = lat / (float) LAT_SEG;
                int color = Material.argb(Material.mix(light, deep, t), 0.95f);

                for (int lon = 0; lon < LON_SEG; lon++) {
                    float sp0 = SIN_PHI[lon], cp0 = COS_PHI[lon];
                    float sp1 = SIN_PHI[lon + 1], cp1 = COS_PHI[lon + 1];

                    a.set(center.x + r0 * cp0, y0, center.z + r0 * sp0);
                    b.set(center.x + r0 * cp1, y0, center.z + r0 * sp1);
                    c.set(center.x + r1 * cp1, y1, center.z + r1 * sp1);
                    d.set(center.x + r1 * cp0, y1, center.z + r1 * sp0);

                    Material.bodyQuad(ctx, a, b, c, d, color, color, color, color);
                }
            }
        }
    }
}
