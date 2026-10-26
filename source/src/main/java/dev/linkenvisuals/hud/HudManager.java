package dev.linkenvisuals.hud;

import dev.linkenvisuals.LinkenVisuals;
import dev.linkenvisuals.render.Fonts;
import dev.linkenvisuals.util.ColorMath;
import dev.linkenvisuals.anim.Animations;
import dev.linkenvisuals.hands.HandEditor;
import dev.linkenvisuals.hud.notify.NotificationManager;
import dev.linkenvisuals.render.GlowPipeline;
import dev.linkenvisuals.render.GlyphAtlas;
import dev.linkenvisuals.render.HudGlassCapture;
import dev.linkenvisuals.render.MsdfText;
import dev.linkenvisuals.render.RoundedRectPipeline;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

public final class HudManager {
    private static final float DOT_SPACING = 26.0f;
    private static final float SNAP_DISTANCE = 5.0f;
    // Reused scratch buffers: the edit-mode path runs every frame, so it must not allocate.
    private static final float[] SNAP_OUT = new float[2];
    private static final double[] CURSOR_X = new double[1];
    private static final double[] CURSOR_Y = new double[1];

    private static final float MENU_W = 78.0f;
    private static final float MENU_ROW_H = 16.0f;
    private static final float MENU_PAD = 4.0f;
    private static final float MENU_GAP = 6.0f;

    private static final List<HudElement> ELEMENTS = List.of(
        new TargetHud(), new CoordinatesHud(), new CooldownsHud(), new ArmorHud(),
        new InventoryHud(), new Watermark(), new KeybindPanel(), new PotionHud(),
        new ScoreboardHud(), new LowArmorNotificationsHud(), new HelperItemsHud());
    private static final Spring gridReveal = new Spring(0.0, 700.0, 32.0);
    private static final Spring dragBlur = new Spring(0.0, 900.0, 40.0);

    private static HudElement dragging;
    private static float dragOffsetX;
    private static float dragOffsetY;
    private static boolean previousMouseDown;
    private static boolean previousRightMouseDown;
    private static long lastFrame = System.nanoTime();
    private static boolean loaded;
    /**
     * Explicit HUD edit-mode flag. Set by {@code ChatScreenMixin} when the chat screen
     * opens or closes; HudManager itself never inspects screen types.
     */
    private static boolean editing;
    /** The vanilla-blur warning is logged once; the drag path below runs every frame. */
    private static boolean blurUnsupportedLogged;

    private static boolean menuOpen;
    private static float menuX;
    private static float menuY;
    private static GlyphAtlas menuFont;

    private HudManager() {
    }

    public static void render(DrawContext context) {
        if (!loaded) {
            // One-time render-thread warm-up; was called on every frame before Ф3.6.
            dev.linkenvisuals.render.Pipelines.warmUp();
            Map<String, float[]> stored = HudLayoutStorage.load();
            for (HudElement element : ELEMENTS) {
                element.loadFraction(stored.get(element.id()));
            }
            loaded = true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null && client.options.hudHidden) {
            return;
        }
        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            return;
        }
        long now = System.nanoTime();
        float deltaSeconds = (float) Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
        lastFrame = now;

        NameTags.render(context);
        dev.linkenvisuals.render.ItemRadiusRenderer.renderLabels(context);
        dev.linkenvisuals.render.FortniteDamageRenderer.render(context);

        boolean editMode = editing;
        gridReveal.step(editMode ? 1.0 : 0.0, deltaSeconds);
        float gridAlpha = (float) gridReveal.get();

        if (editMode) {
            updateDragging(client);
        } else {
            dragging = null;
            menuOpen = false;
        }

        dragBlur.step(dragging != null ? 1.0 : 0.0, deltaSeconds);
        float blurAmount = (float) dragBlur.get();
        if (blurAmount > 0.05f) {
            try {
                context.applyBlur();
            } catch (IllegalStateException e) {
                // Vanilla post-process blur is disabled or unsupported in the current device
                // context. Logged once: this path runs every frame while an element is dragged.
                if (!blurUnsupportedLogged) {
                    blurUnsupportedLogged = true;
                    LinkenVisuals.LOGGER.warn("[Visuals Modification] HUD drag blur unavailable: {}", e.toString());
                }
            }
            int veilAlpha = Math.round(45 * blurAmount);
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), ColorMath.withAlpha(0x000000, veilAlpha));
        }

        HudGlassCapture.refresh();

        if (gridAlpha > 0.01f) {
            renderGrid(context, gridAlpha);
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        for (HudElement element : ELEMENTS) {
            if (isPotionElement(element) && !dev.linkenvisuals.ui.editor.MainMenuConfig.get().customStatusEffects) {
                continue;
            }
            if (element instanceof ArmorHud armor && armor.v2()) {
                element.render(context, 0.0f, 0.0f);
                continue;
            }
            float width = element.width();
            float height = element.height();
            float x = element.centerX(screenWidth) - width * 0.5f;
            float y = element.centerY(screenHeight) - height * 0.5f;
            x = Math.max(0.0f, Math.min(x, Math.max(0.0f, screenWidth - width)));
            y = Math.max(0.0f, Math.min(y, Math.max(0.0f, screenHeight - height)));
            element.render(context, x, y);

            if (editMode && element == dragging) {
                renderGuides(context, element, x, y, width, height, gridAlpha);
            }
        }

        Animations.tick();
        HandEditor.tick();
        LyricsHud.render(context);
        if (dev.linkenvisuals.ui.editor.MainMenuConfig.get().customHotbar) {
            HotbarHud.render(context);
        }
        CustomVitalsHud.render(context);
        NotificationManager.render(context);
    }

    public static void setEditing(boolean editing) {
        HudManager.editing = editing;
    }

    public static boolean isEditing() {
        return editing;
    }

    private static boolean isPotionElement(HudElement element) {
        return element instanceof PotionHud;
    }

    private static void updateDragging(MinecraftClient client) {
        if (client.getWindow() == null) {
            return;
        }
        long handle = client.getWindow().getHandle();
        boolean mouseDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightMouseDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean freeMove = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        GLFW.glfwGetCursorPos(handle, CURSOR_X, CURSOR_Y);
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        float mouseX = (float) (CURSOR_X[0] * screenWidth / client.getWindow().getWidth());
        float mouseY = (float) (CURSOR_Y[0] * screenHeight / client.getWindow().getHeight());

        if (rightMouseDown && !previousRightMouseDown) {
            menuOpen = false;
        }
        previousRightMouseDown = rightMouseDown;

        if (mouseDown && !previousMouseDown) {
            dragging = findElementAt(mouseX, mouseY, screenWidth, screenHeight);
            if (dragging != null) {
                dragOffsetX = mouseX - dragging.centerX(screenWidth);
                dragOffsetY = mouseY - dragging.centerY(screenHeight);
            }
        }

        if (!mouseDown) {
            if (dragging != null) {
                persist();
            }
            dragging = null;
        } else if (dragging != null) {
            float targetX = mouseX - dragOffsetX;
            float targetY = mouseY - dragOffsetY;
            if (freeMove) {
                dragging.moveTo(targetX, targetY, screenWidth, screenHeight);
            } else {
                float halfW = dragging.width() * 0.5f;
                float halfH = dragging.height() * 0.5f;
                targetX = Math.max(halfW, Math.min(screenWidth - halfW, targetX));
                targetY = Math.max(halfH, Math.min(screenHeight - halfH, targetY));
                snap(dragging, targetX, targetY, screenWidth, screenHeight);
                SNAP_OUT[0] = Math.max(halfW, Math.min(screenWidth - halfW, SNAP_OUT[0]));
                SNAP_OUT[1] = Math.max(halfH, Math.min(screenHeight - halfH, SNAP_OUT[1]));
                dragging.moveTo(SNAP_OUT[0], SNAP_OUT[1], screenWidth, screenHeight);
            }
        }

        previousMouseDown = mouseDown;
    }

    private static HudElement findElementAt(float mouseX, float mouseY, int screenWidth, int screenHeight) {
        for (int i = ELEMENTS.size() - 1; i >= 0; i--) {
            HudElement element = ELEMENTS.get(i);
            if (element instanceof ArmorHud armor && armor.v2()) {
                continue;
            }
            float halfW = element.width() * 0.5f;
            float halfH = element.height() * 0.5f;
            float cx = element.centerX(screenWidth);
            float cy = element.centerY(screenHeight);
            if (mouseX >= cx - halfW && mouseX <= cx + halfW && mouseY >= cy - halfH && mouseY <= cy + halfH) {
                return element;
            }
        }
        return null;
    }

    /** Computes the snapped drag position into {@link #SNAP_OUT} (called per frame while dragging). */
    private static void snap(HudElement element, float centerX, float centerY, int screenWidth, int screenHeight) {
        float snappedX = centerX;
        float snappedY = centerY;
        float gridX = Math.round(centerX / DOT_SPACING) * DOT_SPACING;
        float gridY = Math.round(centerY / DOT_SPACING) * DOT_SPACING;
        if (Math.abs(centerX - gridX) < SNAP_DISTANCE) {
            snappedX = gridX;
        }
        if (Math.abs(centerY - gridY) < SNAP_DISTANCE) {
            snappedY = gridY;
        }

        float screenCenterX = screenWidth * 0.5f;
        float screenCenterY = screenHeight * 0.5f;
        if (Math.abs(centerX - screenCenterX) < SNAP_DISTANCE) {
            snappedX = screenCenterX;
        }
        if (Math.abs(centerY - screenCenterY) < SNAP_DISTANCE) {
            snappedY = screenCenterY;
        }

        for (HudElement other : ELEMENTS) {
            if (other == element) {
                continue;
            }
            float otherX = other.centerX(screenWidth);
            float otherY = other.centerY(screenHeight);
            if (Math.abs(centerX - otherX) < SNAP_DISTANCE) {
                snappedX = otherX;
            }
            if (Math.abs(centerY - otherY) < SNAP_DISTANCE) {
                snappedY = otherY;
            }
        }
        SNAP_OUT[0] = snappedX;
        SNAP_OUT[1] = snappedY;
    }

    private static void renderGrid(DrawContext context, float alpha) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        int dotAlpha = Math.round(16 * alpha);
        for (float dy = DOT_SPACING * 0.5f; dy < height; dy += DOT_SPACING) {
            for (float dx = DOT_SPACING * 0.5f; dx < width; dx += DOT_SPACING) {
                context.fill(Math.round(dx) - 1, Math.round(dy) - 1, Math.round(dx) + 1, Math.round(dy) + 1,
                    ColorMath.withAlpha(0xFFFFFF, dotAlpha));
            }
        }
    }

    private static final float GUIDE_REACH = 34.0f;

    private static void renderGuides(DrawContext context, HudElement element, float x, float y, float width, float height, float alpha) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        float centerX = x + width * 0.5f;
        float centerY = y + height * 0.5f;

        boolean onScreenCenterX = Math.abs(centerX - screenWidth * 0.5f) < 0.6f;
        boolean onScreenCenterY = Math.abs(centerY - screenHeight * 0.5f) < 0.6f;

        if (onScreenCenterX) {
            tick(context, centerX, centerY, false, ColorMath.withAlpha(0xFFFFFF, Math.round(220 * alpha)));
        }
        if (onScreenCenterY) {
            tick(context, centerX, centerY, true, ColorMath.withAlpha(0xFFFFFF, Math.round(220 * alpha)));
        }

        for (HudElement other : ELEMENTS) {
            if (other == element) {
                continue;
            }
            float otherX = other.centerX(screenWidth);
            float otherY = other.centerY(screenHeight);
            if (Math.abs(centerX - otherX) < 0.6f) {
                tick(context, centerX, centerY, false, ColorMath.withAlpha(0xFFFFFF, Math.round(190 * alpha)));
            }
            if (Math.abs(centerY - otherY) < 0.6f) {
                tick(context, centerX, centerY, true, ColorMath.withAlpha(0xFFFFFF, Math.round(190 * alpha)));
            }
        }
    }

    private static void tick(DrawContext context, float centerX, float centerY, boolean vertical, int color) {
        if (vertical) {
            GlowPipeline.line(context, centerX, centerY, 2.0f, GUIDE_REACH * 2.0f, color);
        } else {
            GlowPipeline.line(context, centerX, centerY, GUIDE_REACH * 2.0f, 2.0f, color);
        }
    }

    private static void persist() {
        Map<String, float[]> positions = new java.util.HashMap<>();
        for (HudElement element : ELEMENTS) {
            positions.put(element.id(), element.toStoredFraction());
        }
        HudLayoutStorage.save(positions);
    }

}
