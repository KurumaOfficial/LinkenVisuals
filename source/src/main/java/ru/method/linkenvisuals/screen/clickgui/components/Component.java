package ru.method.linkenvisuals.screen.clickgui.components;

import ru.method.linkenvisuals.utils.Wrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Supplier;

@RequiredArgsConstructor @Getter @Setter
public abstract class Component implements Wrapper {
    private final String name;
    protected float x, y, width, height;
    protected Supplier<Float> addHeight = () -> 0f;
    protected Supplier<Boolean> visible = () -> true;
    protected static final float MARQUEE_SPEED = 25f;
    protected static final float MARQUEE_GAP = 12f;
    protected static final long MARQUEE_DELAY_MS = 350L;
    private long marqueeHoverStartMs;

    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);
    public abstract void mouseClicked(double mouseX, double mouseY, int button);
    public abstract void mouseReleased(double mouseX, double mouseY, int button);
    public abstract void keyPressed(int keyCode, int scanCode, int modifiers);
    public abstract void keyReleased(int keyCode, int scanCode, int modifiers);
    public abstract void charTyped(char chr, int modifiers);

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return false; }
    public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta) {}
    public boolean isExtendedHovered(double mouseX, double mouseY) { return false; }
    public boolean hasActiveOverlay() { return false; }
    public void onClose() {}
    public boolean isBinding() { return false; }
    public float getLayoutHeight() { return height > 0f ? height : 18f; }

    protected float getMarqueeOffset(float textWidth, float maxWidth, boolean hovered) {
        if (!hovered || textWidth <= maxWidth) {
            marqueeHoverStartMs = 0L;
            return 0f;
        }
        long now = System.currentTimeMillis();
        if (marqueeHoverStartMs == 0L) {
            marqueeHoverStartMs = now;
            return 0f;
        }
        long elapsed = now - marqueeHoverStartMs;
        if (elapsed < MARQUEE_DELAY_MS) {
            return 0f;
        }
        float distance = Math.max(1f, textWidth + MARQUEE_GAP);
        float offset = ((elapsed - MARQUEE_DELAY_MS) / 1000f) * MARQUEE_SPEED;
        return offset % distance;
    }
}
