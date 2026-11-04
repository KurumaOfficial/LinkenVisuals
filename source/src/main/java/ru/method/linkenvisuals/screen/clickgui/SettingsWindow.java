package ru.method.linkenvisuals.screen.clickgui;

import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.impl.*;
import ru.method.linkenvisuals.screen.clickgui.components.impl.*;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SettingsWindow implements Wrapper {
    private static final long SCROLL_ANIMATION_MS = 260L;
    private float x, y, width, height;
    private final Module module;
    private final List<Component> components = new ArrayList<>();
    private final List<Component> visibleComponents = new ArrayList<>();
    private float dragX, dragY;
    private boolean dragging;
    private float scroll;
    private final InfinityAnimation scrollAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private static final float WINDOW_HEIGHT = 150f;
    public static final float WINDOW_WIDTH = 120f;
    public static final float HEADER_HEIGHT = 20f;

    private final Animation openAnimation = new Animation(130, 1f, true, Easing.EASE_OUT_EXPO);
    private final Animation toggleAnimation = new Animation(180, 1f, true, Easing.linkenvisuals);
    private boolean closing = false;

    public SettingsWindow(Module module, float x, float y) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.width = WINDOW_WIDTH;
        this.height = HEADER_HEIGHT;

        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting) components.add(new BooleanComponent((BooleanSetting) setting));
            else if (setting instanceof NumberSetting) components.add(new SliderComponent((NumberSetting) setting));
            else if (setting instanceof EnumSetting) components.add(new EnumComponent((EnumSetting<?>) setting));
            else if (setting instanceof StringSetting) components.add(new StringComponent((StringSetting) setting));
            else if (setting instanceof ListSetting) components.add(new ListComponent((ListSetting) setting));
            else if (setting instanceof BindSetting) components.add(new BindComponent((BindSetting) setting));
            else if (setting instanceof ColorSetting) components.add(new ColorComponent((ColorSetting) setting));
            else if (setting instanceof ActionSetting) components.add(new ActionButtonComponent((ActionSetting) setting));
        }
        if (module instanceof ru.method.linkenvisuals.modules.impl.client.UI uiModule) {
            components.add(new DualActionButtonComponent(
                    "settings.ui.apply",
                    uiModule::applyScaleSetting,
                    "settings.ui.default",
                    uiModule::resetScaleSetting
            ));
        }

        this.openAnimation.reset();
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        rebuildVisibleComponents();
        openAnimation.update(openAnimation.isForward());
        toggleAnimation.update(module.isToggled());

        if (dragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
        }

        context.getMatrices().pushMatrix();
        float scale = (float) openAnimation.getValue();
        float cx = x + width / 2f;
        float cy = y + (height + WINDOW_HEIGHT) / 2f;
        context.getMatrices().translate(cx, cy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-cx, -cy);

        int globalAlpha = (int)(255 * scale);

        Color baseBgColor = ColorUtils.getGlassColor(255);
        Render2D.drawLiquidRectClickGuiSettings(
                context.getMatrices(),
                x,
                y,
                width,
                height + WINDOW_HEIGHT,
                new BorderRadius(14f),
                new ColorRGBA(baseBgColor),
                2.5f,
                22.0f,
                11f,
                175.0f,
                true,
                0f,
                0.19f
        );

        Color textColor = ColorUtils.getTextColor(globalAlpha);
        Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(9f), module.getDisplayName(), x + 6f, y + 5f, textColor);
        Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(10f), "L", x + width - 15f, y + 5f, textColor);

        float switchX = this.x + this.width - 38f;
        float switchY = this.y + (this.height - 8f) / 2f;
        float switchWidth = 16f;
        float switchHeight = 8f;

        Color globalColor = ColorUtils.getGlobalColor();
        Color switchBg = new Color(200, 206, 220, (int)(120 * (globalAlpha / 255f)));
        Color switchOnColor = ColorUtils.getGlobalColor((int) (globalAlpha * toggleAnimation.getLinear()));

        Render2D.drawRoundedRect(context.getMatrices(), switchX, switchY, switchWidth, switchHeight, 2.5f, switchBg);
        Render2D.drawRoundedRect(context.getMatrices(), switchX, switchY, switchWidth * (float)toggleAnimation.getValue(), switchHeight, 2.5f, switchOnColor);

        float knobSize = 7f;
        float knobY = switchY + (switchHeight - knobSize) / 2f;
        float knobX = switchX + (switchHeight - knobSize) / 2f;
        Color knobColor = new Color(245, 247, 255, globalAlpha);
        Render2D.drawRoundedRect(context.getMatrices(), knobX + ((switchWidth - knobSize) * (float)toggleAnimation.getValue()), knobY, knobSize, knobSize, 2.5f, knobColor);

        Render2D.startScissor(context, x, y + height, width, WINDOW_HEIGHT);

        float maxScroll = Math.max(0f, getVisibleComponentsHeight() - WINDOW_HEIGHT);
        scroll = MathHelper.clamp(scroll, 0f, maxScroll);
        float animatedScroll = MathHelper.clamp(scrollAnimation.animate(scroll, SCROLL_ANIMATION_MS), 0f, maxScroll);
        float currentY = y + height - animatedScroll;
        float contentTop = y + height;
        float contentBottom = contentTop + WINDOW_HEIGHT;

        for (Component component : visibleComponents) {
            component.setX(x + 2f);
            component.setY(currentY);
            component.setWidth(width - 4f);
            float componentBaseHeight = component.getLayoutHeight();
            component.setHeight(componentBaseHeight);
            float componentHeight = componentBaseHeight + component.getAddHeight().get();
            if (intersectsVerticalRange(currentY, componentHeight, contentTop, contentBottom)) {
                component.render(context, mouseX, mouseY, delta);
            }
            currentY += componentHeight;
        }

        Render2D.stopScissor(context);

        context.getMatrices().popMatrix();

        for (Component component : visibleComponents) {
            if (!component.hasActiveOverlay()) continue;
            component.renderOverlay(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return false;

        float[] localMouse = toLocalMouse(mouseX, mouseY);
        float scaledMouseX = localMouse[0];
        float scaledMouseY = localMouse[1];

        if (handleExtendedClick(mouseX, mouseY, button)) {
            return true;
        }

        boolean isHoveredAnywhere = MathUtils.isHovered(x, y, width, height + WINDOW_HEIGHT, scaledMouseX, scaledMouseY);
        if (!isHoveredAnywhere) {
            return false;
        }

        if (MathUtils.isHovered(x + width - 18, y, 18, height, scaledMouseX, scaledMouseY) && button == 0) {
            close();
        }
        else if (MathUtils.isHovered(x + width - 38, y, 20, height, scaledMouseX, scaledMouseY) && button == 0) {
            module.toggle();
        }
        else if (MathUtils.isHovered(x, y, width - 38, height, scaledMouseX, scaledMouseY) && button == 0) {
            dragging = true;
            dragX = (float)mouseX - x;
            dragY = (float)mouseY - y;
        }
        else if (MathUtils.isHovered(x, y + height, width, WINDOW_HEIGHT, scaledMouseX, scaledMouseY)) {
            for (Component component : visibleComponents) {
                component.mouseClicked(scaledMouseX, scaledMouseY, button);
            }
        }

        return true;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        if (closing) return;

        float[] localMouse = toLocalMouse(mouseX, mouseY);
        rebuildVisibleComponents();
        for (Component component : visibleComponents) {
            component.mouseReleased(localMouse[0], localMouse[1], button);
        }
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing) return;
        float[] localMouse = toLocalMouse(mouseX, mouseY);
        rebuildVisibleComponents();
        for (Component component : visibleComponents) {
            if (component.mouseScrolled(localMouse[0], localMouse[1], horizontalAmount, verticalAmount)) return;
        }
        if (MathUtils.isHovered(x, y + height, width, WINDOW_HEIGHT, localMouse[0], localMouse[1])) {
            scroll -= (float) (verticalAmount * 10);
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, getVisibleComponentsHeight() - WINDOW_HEIGHT));
        }
    }
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return;
        rebuildVisibleComponents();
        for(Component c : visibleComponents) {
            c.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public void charTyped(char chr, int modifiers) {
        if (closing) return;
        rebuildVisibleComponents();
        for(Component c : visibleComponents) {
            c.charTyped(chr, modifiers);
        }
    }

    private void close() {
        if (!closing) {
            resetInputState();
            closing = true;
            openAnimation.update(false);
        }
    }

    public void resetInputState() {
        for (Component component : components) {
            component.onClose();
        }
    }

    public boolean isFinishedClosing() {
        return closing && openAnimation.getValue() <= 0.01;
    }

    public Module getModule() {
        return module;
    }

    public boolean hasActiveBinding() {
        rebuildVisibleComponents();
        for (Component component : visibleComponents) {
            if (component.isBinding()) return true;
        }
        return false;
    }

    public static float getWindowHeight() {
        return WINDOW_HEIGHT;
    }

    private boolean handleExtendedClick(double mouseX, double mouseY, int button) {
        boolean handled = false;
        rebuildVisibleComponents();
        for (Component component : visibleComponents) {
            boolean shouldHandle = component.isExtendedHovered(mouseX, mouseY);
            if (!shouldHandle && component instanceof ColorComponent colorComponent && colorComponent.isEditing()) {
                shouldHandle = true;
            }
            if (shouldHandle) {
                component.mouseClicked(mouseX, mouseY, button);
                handled = true;
            }
        }
        return handled;
    }

    private void rebuildVisibleComponents() {
        visibleComponents.clear();
        for (Component component : components) {
            if (!component.getVisible().get()) continue;
            visibleComponents.add(component);
        }
    }

    private float getVisibleComponentsHeight() {
        float totalHeight = 0f;
        for (Component component : visibleComponents) {
            float baseHeight = component.getLayoutHeight();
            totalHeight += baseHeight + component.getAddHeight().get();
        }
        return totalHeight;
    }

    private boolean intersectsVerticalRange(float componentY, float componentHeight, float viewportTop, float viewportBottom) {
        return componentY + componentHeight >= viewportTop && componentY <= viewportBottom;
    }

    private float[] toLocalMouse(double mouseX, double mouseY) {
        float scale = (float) openAnimation.getValue();
        float centerX = x + width / 2f;
        float centerY = y + (height + WINDOW_HEIGHT) / 2f;
        return new float[] {
                (float) (centerX + (mouseX - centerX) / scale),
                (float) (centerY + (mouseY - centerY) / scale)
        };
    }
}
