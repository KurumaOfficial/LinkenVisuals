package ru.method.linkenvisuals.utils.render;

import org.joml.Matrix4f;

/** Batches only consecutive round-ended icon strokes. In 1.21.11, delegated to RoundedRectPipeline. */
public final class RoundedStrokeBatch {
    private RoundedStrokeBatch() {}

    public static void begin() {}

    public static boolean submit(Matrix4f matrix, float x, float y, float width, float height,
                                 float radius, int color) {
        return false;
    }

    public static void flush() {}

    public static void end() {}
}
