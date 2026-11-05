#version 330

#define MAX_PANELS 32

layout(std140) uniform FireData {
    vec4 header;         // x: width, y: height, z: time, w: panel count
    vec4 accent;         // rgb, a: master fade
    vec4 rects[MAX_PANELS];  // center.xy, half-size.xy (top-down physical pixels)
    vec4 shapes[MAX_PANELS]; // x: corner radius, y: halo width (physical pixels)
};

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        amp *= 0.52;
    }
    return sum;
}

void main() {
    // gl_FragCoord is bottom-up, while panel rects are top-down screen pixels.
    vec2 frag = vec2(gl_FragCoord.x, header.y - gl_FragCoord.y);
    float t = header.z;
    float count = header.w;
    if (count < 1.0 || accent.a <= 0.001) {
        discard;
    }

    float glow = 0.0;
    float core = 0.0;
    bool insidePanel = false;

    for (int i = 0; i < MAX_PANELS; i++) {
        if (float(i) >= count) {
            break;
        }

        vec4 rect = rects[i];
        vec4 shape = shapes[i];
        vec2 center = rect.xy;
        vec2 halfSize = rect.zw;
        float spread = max(shape.y, 1.0);
        float visibility = clamp(shape.z, 0.0, 1.0);

        // Cheap bounding-box reject before the rounded-rect SDF.
        if (frag.x < center.x - halfSize.x - spread
            || frag.x > center.x + halfSize.x + spread
            || frag.y < center.y - halfSize.y - spread
            || frag.y > center.y + halfSize.y + spread) {
            continue;
        }

        float radius = clamp(shape.x, 0.0, min(halfSize.x, halfSize.y));
        vec2 d = abs(frag - center) - halfSize + radius;
        float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;

        // Every width below is a fraction of the halo spread, so the rim keeps
        // the same visual thickness at any GUI scale / window size. The old
        // fixed-pixel values made the rim creep inside the panel on small
        // screens, leaving a dark strip at the very edge of the background.
        float bleed = max(2.0, spread * 0.45);
        if (dist < -bleed) {
            insidePanel = true;
            break;
        }
        if (dist > spread) {
            continue;
        }

        // Soft bloom: brightest at the panel boundary, dissolving into the
        // fill on the inside and into the halo on the outside. Using max
        // (instead of adding) keeps the halo equally bright where two panels
        // are close, without a hot seam in the gap between them.
        float inner = smoothstep(-bleed, -bleed * 0.20, dist);
        float outer = 1.0 - smoothstep(0.0, spread, max(dist, 0.0));
        float fall = inner * outer;
        glow = max(glow, fall * visibility);

        // Solid accent rim lying exactly on the panel edge. It is at full
        // strength right at dist == 0 and stays full for a fraction of a pixel
        // on BOTH sides: outward this covers the border/shadow tip, inward it
        // covers the panel's border line. The rim therefore starts at the very
        // edge of the background instead of a little inside or outside it.
        float rim = max(0.6, spread * 0.060);
        float coreOuter = 1.0 - smoothstep(rim * 1.25, rim * 3.60, dist);
        float coreInner = 1.0 - smoothstep(rim * 0.20, rim * 3.20, -dist);
        core = max(core, min(coreOuter, coreInner) * visibility);
    }

    if (insidePanel || (glow <= 0.0005 && core <= 0.0005)) {
        discard;
    }

    // Animated heat field, evaluated only on visible halo pixels. It drifts
    // upward slowly and only modulates colour/brightness a little, so the glow
    // keeps an even thickness and never looks patchy or flickering.
    vec2 flow = vec2(0.0, t * 12.0);
    float n1 = fbm((frag + flow) * 0.011);
    float n2 = fbm((frag + flow * 1.7 + vec2(19.0, 3.0)) * 0.026);
    float heat = clamp(n1 * 0.64 + n2 * 0.36, 0.0, 1.0);
    float breath = 0.95 + 0.05 * sin(t * 1.6 + n1 * 5.0);
    float even = 0.94 + 0.12 * heat;

    vec3 warm = mix(accent.rgb, vec3(1.0, 0.56, 0.30), 0.28);
    vec3 col = mix(accent.rgb, warm, smoothstep(0.35, 0.85, heat) * 0.55);
    col *= 0.96 + 0.10 * heat;

    float body = clamp(glow, 0.0, 1.0);
    float edge = clamp(core, 0.0, 1.0);

    // The edge is a uniform accent rim (no heat flicker), while the animated
    // colour breathing lives in the softer outer halo.
    float bodyAlpha = body * 0.05 * even * breath;
    float edgeAlpha = edge * 0.22;
    float alpha = (bodyAlpha + edgeAlpha) * accent.a;
    if (alpha <= 0.001) {
        discard;
    }
    vec3 mixed = (col * bodyAlpha + accent.rgb * edgeAlpha) / max(bodyAlpha + edgeAlpha, 0.001);
    fragColor = vec4(mixed, alpha);
}
