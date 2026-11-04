package ru.method.linkenvisuals.hud;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.settings.impl.ListSetting;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.windows.Window;
import ru.method.linkenvisuals.hud.windows.components.WindowComponent;
import ru.method.linkenvisuals.hud.windows.components.impl.ActionComponent;
import ru.method.linkenvisuals.hud.impl.island.DynamicIslandNotificationManager;
import ru.method.linkenvisuals.managers.system.HudManager;
import ru.method.linkenvisuals.managers.system.SoundManager;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Position;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.PositionSetting;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import net.minecraft.client.gui.DrawContext;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.hud.impl.DynamicIsland;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.screen.mainmenu.CustomMainMenuScreen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;

@Getter @Setter
public abstract class HudElement extends Module {
    protected static final float HUD_PADDING = 6f;
    protected static final float HUD_FONT_SIZE = 7f;
    protected static final float HUD_TITLE_FONT_SIZE = HUD_FONT_SIZE + 1f;
    protected static final float HUD_ICON_SIZE = 10f;
    protected static final float HUD_LINE_HEIGHT = 13f;
    protected static final float HUD_BASE_MAX_WIDTH = 90f;
    protected static final float HUD_HEADER_BASE_HEIGHT = 18f;
    protected static final float HUD_HEADER_LINE_OFFSET = 15f;
    private static final float CONTEXT_WINDOW_X_OFFSET = 3f;
    private static final float CONTEXT_WINDOW_Y_OFFSET = -10f;
    private static final float HOTBAR_WIDTH = 182f;
    private static final float HOTBAR_HEIGHT = 22f;
    private static final float OFFHAND_SLOT_SIZE = 22f;
    private static final float HOTBAR_PADDING = 2f;
    private final PositionSetting position = new PositionSetting("Position", new Position(0, 0));
    protected final Animation toggledAnimation = new Animation(300, 1f, false, Easing.BOTH_SINE);
    private float dragX, dragY, width, height;
    private boolean dragging, button;
    private final List<Setting<?>> settings = new ArrayList<>();
    private Window window;
    private boolean pendingUnsubscribe;
    private boolean subscribed;
    public HudElement(String name) {
        super(name, Category.Hud);
        settings.add(position);
        // Start HUD elements disabled; they will be toggled via the HUD picker or config.
        toggled = false;
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck()) return;
        boolean inHudEditor = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        if (!inHudEditor && (button || dragging)) {
            
            button = false;
            dragging = false;
            if (LinkenVisuals.getInstance().getHudManager().getCurrentDragging() == this) {
                LinkenVisuals.getInstance().getHudManager().setCurrentDragging(null);
            }
        }
        if (button) {
            if (!dragging && MathUtils.isHovered(getX(), getY(), getWidth(), getHeight(), mouseX(), mouseY())
                    && !ru.method.linkenvisuals.screen.hud.HudEditScreen.overPanel(mouseX(), mouseY())) {
                dragX = mouseX() - getX();
                dragY = mouseY() - getY();
                dragging = true;
                LinkenVisuals.getInstance().getHudManager().setCurrentDragging(this);
            }
            if (dragging) {
                HudManager hudManager = LinkenVisuals.getInstance().getHudManager();
                float windowWidth = mc.getWindow().getScaledWidth();
                float windowHeight = mc.getWindow().getScaledHeight();
                float maxX = windowWidth - width;
                float maxY = windowHeight - height;
                float finalX = Math.min(Math.max(mouseX() - dragX, 0), maxX);
                float finalY = Math.min(Math.max(mouseY() - dragY, 0), maxY);
                if (hudManager != null && hudManager.isGridActive()) {
                    finalX = Math.min(Math.max(hudManager.snapToGridX(finalX, width), 0), maxX);
                    finalY = Math.min(Math.max(hudManager.snapToGridY(finalY, height), 0), maxY);
                }
                float[] clamped = clampHudOverlap(finalX, finalY, width, height, windowWidth, windowHeight);
                finalX = clamped[0];
                finalY = clamped[1];
                position.getValue().setX(finalX / windowWidth);
                position.getValue().setY(finalY / windowHeight);
            }
        } else dragging = false;
        if (window != null) {
            if (!inHudEditor) window.reset();
            if (window.closed()) {
                window = null;
                return;
            }
            window.render(e.getContext(), mouseX(), mouseY());
        }
    }

    @EventHandler
    public void onRender2DX2(EventRender2D e) {
        boolean allowWithoutWorld = mc.currentScreen instanceof CustomMainMenuScreen && this instanceof DynamicIsland;
        if (fullNullCheck() && !allowWithoutWorld) {
            if (pendingUnsubscribe) {
                pendingUnsubscribe = false;
                ensureUnsubscribed();
            }
            return;
        }
        try {
            HudManager hudManager = LinkenVisuals.getInstance().getHudManager();
            BooleanSetting elementSetting = hudManager != null ? hudManager.getElementSetting(this) : null;
            boolean target = allowWithoutWorld || (elementSetting != null ? elementSetting.getValue() : isToggled());
            if (!target && !pendingUnsubscribe
                    && toggledAnimation.finished(false)
                    && toggledAnimation.getValue() <= 0.01f) {
                return;
            }
            toggledAnimation.update(target);
        } catch (Exception ex) {
            toggledAnimation.update(allowWithoutWorld || isToggled());
        }

        if (pendingUnsubscribe && toggledAnimation.finished(false) && toggledAnimation.getValue() <= 0.01f) {
            pendingUnsubscribe = false;
            ensureUnsubscribed();
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (!ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive() || fullNullCheck()) return;
        HudManager hudManager = LinkenVisuals.getInstance().getHudManager();
        if (e.getAction() == 0) {
            boolean wasDragging = dragging;
            button = false;
            dragging = false;
            if (hudManager != null) {
                hudManager.setCurrentDragging(null);
            }
            if (wasDragging) {
                saveHudConfig();
            }
        } else if (e.getAction() == 1) {
            boolean hovered = MathUtils.isHovered(getX(), getY(), getWidth(), getHeight(), mouseX(), mouseY());
            boolean topmostHovered = hudManager == null || hudManager.isTopmostHovered(this, mouseX(), mouseY());

            if (window != null) {
                if (window.isHovered(mouseX(), mouseY())) {
                    window.mouseClicked(mouseX(), mouseY(), e.getButton());
                    return;
                } else {
                    window.reset();
                }
            }
            if (e.getButton() == 0 && hovered && topmostHovered && (hudManager == null || hudManager.getCurrentDragging() == null)) {
                if (onLeftClick(mouseX(), mouseY())) {
                    return;
                }
                button = true;
                if (hudManager != null) {
                    hudManager.focusElement(this);
                }
            }
            if (e.getButton() == 2) {
                if (hovered && topmostHovered && onMiddleClick(mouseX(), mouseY())) {
                    return;
                }
                if (hovered && topmostHovered && hudManager != null) {
                    hudManager.focusElement(this);
                }
            }
            if (e.getButton() == 1) {
                if (hovered && topmostHovered && onRightClick(mouseX(), mouseY())) {
                    return;
                }
                if (hovered && topmostHovered) {
                    if (hudManager != null) {
                        hudManager.focusElement(this);
                    }
                    if (hudManager != null && hudManager.getWindow() != null) {
                        hudManager.getWindow().reset();
                    }
                    for (HudElement element : LinkenVisuals.getInstance().getHudManager().getHudElements()) {
                        if (element.getWindow() == null) continue;
                        element.getWindow().reset();
                    }

                    List<WindowComponent> extraComponents = List.of(new ActionComponent(
                            "Удалить",
                            "W",
                            new Color(255, 70, 70),
                            () -> {
                                HudManager hm = LinkenVisuals.getInstance().getHudManager();
                                if (hm != null) {
                                    BooleanSetting visibility = hm.getElementSetting(HudElement.this);
                                    if (visibility != null) {
                                        visibility.setValue(false);
                                        hm.handleElementToggle(visibility, false, 0f, 0f);
                                    } else {
                                        HudElement.this.setToggled(false);
                                    }
                                } else {
                                    HudElement.this.setToggled(false);
                                }

                                if (HudElement.this.window != null) {
                                    HudElement.this.window.reset();
                                    HudElement.this.window = null;
                                }
                            }
                    ));

                    float windowWidth = measureSettingsWindowWidth(settings, "Удалить", "W");
                    window = new Window(mouseX() + CONTEXT_WINDOW_X_OFFSET, mouseY() + CONTEXT_WINDOW_Y_OFFSET, windowWidth, 12.5f, settings, extraComponents);
                }
            }
        }
    }
    public float getX() {
        return mc.getWindow().getScaledWidth() * position.getValue().getX();
    }
    public float getY() {
        return mc.getWindow().getScaledHeight() * position.getValue().getY();
    }
    public int mouseX() {
        // HiDPI-safe: raw * scaledW / windowW (как в ваниле). Было /getScaleFactor() — на Retina половина.
        return (int) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
    }
    public int mouseY() {
        return (int) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
    }
    public void setBounds(float x, float y, float width, float height) {
        this.width = width;
        this.height = height;
        position.getValue().setX(x / mc.getWindow().getScaledWidth());
        position.getValue().setY(y / mc.getWindow().getScaledHeight());
    }
    protected float getUiScale() {
        return UI.getInterfaceScale();
    }
    protected float beginUiScale(Object matrices, float x, float y) {
        float uiScale = getUiScale();
        if (matrices instanceof org.joml.Matrix3x2fStack m) {
            m.pushMatrix();
            m.translate(x, y);
            m.scale(uiScale, uiScale);
            m.translate(-x, -y);
        } else if (matrices instanceof MatrixStack ms) {
            ms.push();
            ms.translate(x, y, 0f);
            ms.scale(uiScale, uiScale, 1f);
            ms.translate(-x, -y, 0f);
        }
        return uiScale;
    }
    protected void endUiScale(Object matrices) {
        if (matrices instanceof org.joml.Matrix3x2fStack m) {
            m.popMatrix();
        } else if (matrices instanceof MatrixStack ms) {
            ms.pop();
        }
    }
    protected void setUiScaledBounds(float x, float y, float width, float height, float uiScale) {
        setBounds(x, y, width * uiScale, height * uiScale);
    }
    protected float scaleUiX(float originX, float x, float uiScale) {
        return originX + (x - originX) * uiScale;
    }
    protected float scaleUiY(float originY, float y, float uiScale) {
        return originY + (y - originY) * uiScale;
    }
    protected float scaleUiLength(float value, float uiScale) {
        return value * uiScale;
    }
    /**
     * Обрезка строки под доступную ширину с многоточием. Промеряет ТЕМ шрифтом, которым текст
     * будет нарисован (передаётся параметром) — единый метод вместо шести копий fitName/clip по HUD.
     */
    protected static String fitText(Font font, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (font.getWidth(text, size) <= maxWidth) return text;
        String ellipsis = "...";
        float ellipsisWidth = font.getWidth(ellipsis, size);
        if (ellipsisWidth >= maxWidth) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            builder.append(text.charAt(i));
            if (font.getWidth(builder.toString(), size) + ellipsisWidth > maxWidth) {
                builder.deleteCharAt(builder.length() - 1);
                break;
            }
        }
        return builder.append(ellipsis).toString();
    }

    /**
     * Безопасная отрисовка текста заданным шрифтом (guard на пустоту + try/catch от отсутствующих
     * глифов) — единый метод вместо копий drawSafeFont/drawSafeIconFont по HUD.
     */
    protected void drawSafeText(DrawContext context, Font font, float size, String text, float x, float y, Color color) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            Render2D.drawFont(context.getMatrices(), font.getFont(size), text, x, y, color);
        } catch (Exception ignored) {
        }
    }
    // Зарезервированные хуки drag-анимации HUD-элемента: сейчас no-op (масштаб 1, нулевое смещение).
    // Оставлены как единые точки расширения — вызывающие HUD домножают на них transform.
    protected float getDragScale() {
        return 1f;
    }
    protected float[] getDragOffset() {
        return DRAG_OFFSET_NONE;
    }
    private static final float[] DRAG_OFFSET_NONE = {0f, 0f};
    public boolean closed() {
        return toggledAnimation.getValue() <= 0f;
    }

    @Override
    public void onEnable() {
        toggled = true;
        pendingUnsubscribe = false;
        ensureSubscribed();
        boolean suppressFeedback = LinkenVisuals.getInstance().isSuppressToggleFeedback();
        if (!suppressFeedback && !fullNullCheck()) {
            DynamicIslandNotificationManager.getInstance().addNotification(this.getName(), true);
        }
        SoundManager soundManager = LinkenVisuals.getInstance().getSoundManager();
        if (!suppressFeedback && soundManager != null) {
            soundManager.playEnable();
        }
        syncVisibilitySetting(true);
    }

    @Override
    public void onDisable() {
        toggled = false;
        pendingUnsubscribe = true;
        boolean suppressFeedback = LinkenVisuals.getInstance().isSuppressToggleFeedback();
        if (!suppressFeedback && !fullNullCheck()) {
            DynamicIslandNotificationManager.getInstance().addNotification(this.getName(), false);
        }
        SoundManager soundManager = LinkenVisuals.getInstance().getSoundManager();
        if (!suppressFeedback && soundManager != null) {
            soundManager.playDisable();
        }
        syncVisibilitySetting(false);
    }

    private void ensureSubscribed() {
        if (subscribed) return;
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
        subscribed = true;
    }

    protected void ensureSubscribedIfNeeded() {
        ensureSubscribed();
    }

    private void ensureUnsubscribed() {
        if (!subscribed) return;
        LinkenVisuals.getInstance().getEventHandler().unsubscribe(this);
        subscribed = false;
    }

    public void bringToFront() {
        if (!subscribed) return;
        LinkenVisuals.getInstance().getEventHandler().unsubscribe(this);
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);
        subscribed = true;
    }
    /**
     * Allows HUD elements to consume right-clicks before the settings window opens.
     *
     * @return true if the click has been handled and default processing should be skipped.
     */
    protected boolean onRightClick(int mouseX, int mouseY) {
        return false;
    }
    /**
     * Allows HUD elements to consume left-clicks before dragging starts.
     *
     * @return true if the click has been handled and default processing should be skipped.
     */
    protected boolean onLeftClick(int mouseX, int mouseY) {
        return false;
    }
    /**
     * Allows HUD elements to consume middle-clicks before default processing.
     *
     * @return true if the click has been handled and default processing should be skipped.
     */
    protected boolean onMiddleClick(int mouseX, int mouseY) {
        return false;
    }

    public static float measureSettingsWindowWidth(List<Setting<?>> settings, String actionText, String actionIcon) {
        float width = actionText != null && actionIcon != null
                ? ActionComponent.getPreferredWidth(actionText, actionIcon)
                : 60f;
        for (Setting<?> setting : settings) {
            if (!setting.isVisible()) continue;

            if (setting instanceof BooleanSetting booleanSetting) {
                String name = I18n.translate(booleanSetting.getName());
                float nameW = Fonts.ROUNDBOLD.getWidth(name, 8f);
                width = Math.max(width, 5f + nameW + 20f + 5f);
            } else if (setting instanceof EnumSetting<?> enumSetting) {
                String name = I18n.translate(enumSetting.getName());
                float nameW = Fonts.ROUNDBOLD.getWidth(name, 8f);
                float maxValueW = 0f;
                for (Enum<?> constant : enumSetting.getValue().getDeclaringClass().getEnumConstants()) {
                    String valueName = constant instanceof ru.method.linkenvisuals.modules.settings.api.Nameable nameable
                            ? I18n.translate(nameable.getName())
                            : constant.name();
                    maxValueW = Math.max(maxValueW, Fonts.ROUNDBOLD.getWidth(valueName, 8f));
                }
                width = Math.max(width, 5f + nameW + 8f + maxValueW + 18f);
            } else if (setting instanceof ListSetting listSetting) {
                String name = I18n.translate(listSetting.getName());
                String count = "(" + listSetting.getToggled().size() + "/" + listSetting.getValue().size() + ")";
                float nameW = Fonts.REGULAR.getWidth(name, 7.5f);
                float countW = Fonts.REGULAR.getWidth(count, 7.5f);
                width = Math.max(width, 5f + nameW + 5f + countW + 5f);

                float maxItemW = 0f;
                for (BooleanSetting elementSetting : listSetting.getValue()) {
                    String elementName = I18n.translate(elementSetting.getName());
                    maxItemW = Math.max(maxItemW, Fonts.REGULAR.getWidth(elementName, 7.5f));
                }
                width = Math.max(width, 6f + maxItemW + 18f);
            }
        }

        return Math.max(60f, width);
    }

    private float[] clampHudOverlap(float x, float y, float w, float h, float windowWidth, float windowHeight) {
        float hotbarY = windowHeight - HOTBAR_HEIGHT;
        float centerX = windowWidth / 2f;
        float hotbarX = centerX - HOTBAR_WIDTH / 2f;
        float offhandX = hotbarX - OFFHAND_SLOT_SIZE;

        float safeX = x;
        float safeY = y;

        if (intersects(safeX, safeY, w, h, hotbarX - HOTBAR_PADDING, hotbarY - HOTBAR_PADDING, HOTBAR_WIDTH + HOTBAR_PADDING * 2f, HOTBAR_HEIGHT + HOTBAR_PADDING * 2f)
                || intersects(safeX, safeY, w, h, offhandX - HOTBAR_PADDING, hotbarY - HOTBAR_PADDING, OFFHAND_SLOT_SIZE + HOTBAR_PADDING * 2f, OFFHAND_SLOT_SIZE + HOTBAR_PADDING * 2f)) {
            safeY = Math.max(0f, hotbarY - HOTBAR_PADDING - h);
        }

        // Отталкивание от других видимых HUD-элементов, чтобы окна не налезали друг на друга: при
        // пересечении сдвигаем перетаскиваемый элемент по оси наименьшего перекрытия.
        HudManager hudManager = LinkenVisuals.getInstance().getHudManager();
        if (hudManager != null) {
            for (HudElement o : hudManager.getHudElements()) {
                if (o == this || o.closed()) continue;
                float ow = o.getWidth(), oh = o.getHeight();
                if (ow <= 0f || oh <= 0f) continue;
                float ox = o.getX(), oy = o.getY();
                if (!intersects(safeX, safeY, w, h, ox, oy, ow, oh)) continue;
                float dx = Math.abs(ox - (safeX + w)) < Math.abs((ox + ow) - safeX) ? ox - (safeX + w) : (ox + ow) - safeX;
                float dy = Math.abs(oy - (safeY + h)) < Math.abs((oy + oh) - safeY) ? oy - (safeY + h) : (oy + oh) - safeY;
                if (Math.abs(dx) <= Math.abs(dy)) {
                    safeX = Math.min(Math.max(safeX + dx, 0f), windowWidth - w);
                } else {
                    safeY = Math.min(Math.max(safeY + dy, 0f), windowHeight - h);
                }
            }
        }

        return new float[]{safeX, safeY};
    }

    private boolean intersects(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void syncVisibilitySetting(boolean state) {
        try {
            HudManager hudManager = LinkenVisuals.getInstance().getHudManager();
            if (hudManager == null) return;
            BooleanSetting elementSetting = hudManager.getElementSetting(this);
            if (elementSetting != null && elementSetting.getValue() != state) {
                elementSetting.setValue(state);
            }
        } catch (Exception ignored) {}
    }

    private void saveHudConfig() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.isConfigLoading()) return;
        instance.getConfigManager().forceSave();
    }
}
