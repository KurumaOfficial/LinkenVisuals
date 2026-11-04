package ru.method.linkenvisuals.utils.render;

/**
 * 1.21.11: depth-test state is baked into render pipelines/layers, so manual
 * GL state toggling is no longer available. The scope stays as a no-op to keep
 * the donor call sites unchanged.
 */
public final class DepthRenderScope implements AutoCloseable {

    private DepthRenderScope() {
    }

    public static DepthRenderScope lequal() {
        return new DepthRenderScope();
    }

    @Override
    public void close() {
    }
}
