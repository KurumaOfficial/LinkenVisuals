package ru.method.linkenvisuals.managers.system;


import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.misc.LowArmorNotify;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.hud.HudElement;
import ru.method.linkenvisuals.hud.impl.*;
import ru.method.linkenvisuals.hud.windows.Window;
import ru.method.linkenvisuals.modules.settings.SettingsCollector;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.ListSetting;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.math.MathUtils;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import ru.method.linkenvisuals.screen.hud.HudEditScreen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.client.UI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter

public class HudManager implements Wrapper {

    private static final int GRID_LINES = 7;
    private static final float GRID_VISIBILITY_PADDING = 25f;
    private static final float GRID_SNAP_RANGE = 3f;
    private static final Color GRID_COLOR = new Color(255, 255, 255, 30);
    private static final Color GRID_HIGHLIGHT_COLOR = new Color(255, 255, 255, 55);
    private static final String ELEMENT_SETTING_PREFIX = "elements.settings.elements.";

    @Setter private HudElement currentDragging;
    private final List<HudElement> hudElements = new ArrayList<>();
    // Кэш "элемент -> настройка": раньше строка + линейный stream-поиск на каждый элемент каждый кадр.
    private final Map<HudElement, BooleanSetting> settingByElement = new IdentityHashMap<>();
    private final Map<BooleanSetting, HudElement> elementBySetting = new IdentityHashMap<>();

    
    protected final ListSetting elements = new ListSetting("elements.settings.elements",
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "targethud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "coordinateshud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "potionhud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "cooldownshud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "keybindshud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "helperitemshud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "armorhud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "inventoryhud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "lowarmornotificationshud", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "watermark", true),
            new BooleanSetting(ELEMENT_SETTING_PREFIX + "dynamicisland", true)
    );

    @Setter private Window window;

    public HudElement getTopmostHovered(int mouseX, int mouseY) {
        for (int i = hudElements.size() - 1; i >= 0; i--) {
            HudElement element = hudElements.get(i);
            if (element == null) continue;
            if (element.closed()) continue;
            if (MathUtils.isHovered(element.getX(), element.getY(), element.getWidth(), element.getHeight(), mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    public boolean isTopmostHovered(HudElement element, int mouseX, int mouseY) {
        if (element == null) return false;
        return getTopmostHovered(mouseX, mouseY) == element;
    }

    public void focusElement(HudElement element) {
        if (element == null) return;
        if (hudElements.remove(element)) {
            hudElements.add(element);
            LinkenVisuals instance = LinkenVisuals.getInstance();
            if (instance != null && !instance.isConfigLoading()) {
                instance.getConfigManager().forceSave();
            }
        }
        mc.execute(element::bringToFront);
    }

    public HudManager() {
        LinkenVisuals.getInstance().getEventHandler().subscribe(this);

        addElements(
                new TargetHud(),
                new PotionHud(),
                new CoordinatesHud(),
                new CooldownsHud(),
                new KeyBindsHud(),
                new HelperItemsHud(),
                new ArmorHud(),
                new InventoryHud(),
                new ScoreboardHud(),
            new LowArmorNotificationsHud(),
            new Watermark(),
                new DynamicIsland()
        );

        for (HudElement element : hudElements) {
            SettingsCollector.collectDeclaredSettings(element, element.getSettings(), element.getName());
        }

        for (HudElement element : hudElements) {
            BooleanSetting setting = elements.getName(getElementSettingKey(element));
            settingByElement.put(element, setting);
            if (setting != null) {
                elementBySetting.put(setting, element);
                if (setting.getValue() != element.isToggled()) {
                    element.setToggled(setting.getValue());
                }
            }
        }
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (Module.fullNullCheck()) return;

        HudElement gridTarget = getActiveGridTarget();
        if (gridTarget != null && isGridActive()) {
            renderGrid(e.getContext(), gridTarget);
        }

    }

    public void renderWindowOverlay(DrawContext context) {
        if (Module.fullNullCheck() || window == null) return;
        if (!HudEditScreen.editingActive()) window.reset();

        if (window.closed()) {
            window = null;
            return;
        }

        window.render(context, mouseX(), mouseY());
    }

    /** Updates HUD picker visibility animations before element render. */
    public void prepareVisibilityAnimations(EventRender2D event) {
        for (HudElement element : hudElements) {
            element.onRender2DX2(event);
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (!HudEditScreen.editingActive() || Module.fullNullCheck()) return;

        if (e.getAction() == 1) {
            if (window != null) {
                if (window.isHovered(mouseX(), mouseY())) {
                    window.mouseClicked(mouseX(), mouseY(), e.getButton());
                    return;
                } else window.reset();
            }
            // ПКМ-пикер добавления HUD-элементов — ТОЛЬКО в Panel GUI (по открытому чату). В Link GUI
            // добавление остаётся через модуль Elements → чип-строку HudEditScreen, поэтому пикер там
            // не поднимаем (в Link всё как было).
            if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !UI.isLinkGuiStyle()) {
                for (HudElement element : hudElements) {
                    if (element.getWindow() == null) continue;
                    element.getWindow().reset();
                }
                float pickerWidth = HudElement.measureSettingsWindowWidth(List.of(elements), null, null);
                window = new Window(mouseX() + 3, mouseY() + 3, pickerWidth, 12.5f, List.of(elements));
            }
        }
    }

    public boolean isGridActive() {
        return HudEditScreen.editingActive() && !isShiftDown();
    }

    public float snapToGridX(float value, float elementWidth) {
        float windowWidth = mc.getWindow().getScaledWidth();
        float maxX = Math.max(0f, windowWidth - elementWidth);
        float clampedValue = Math.min(Math.max(value, 0f), maxX);

        float spacing = getGridSpacingX();
        if (spacing <= 0f) return clampedValue;

        float left = clampedValue;
        float right = clampedValue + elementWidth;
        float center = clampedValue + elementWidth / 2f;

        float bestPosition = clampedValue;
        float bestDistance = GRID_SNAP_RANGE + 1f;

        float leftLine = getNearestLine(left, spacing);
        float leftDistance = Math.abs(left - leftLine);
        if (leftDistance <= GRID_SNAP_RANGE && leftDistance < bestDistance) {
            bestDistance = leftDistance;
            bestPosition = leftLine;
        }

        float centerLine = getNearestLine(center, spacing);
        float centerDistance = Math.abs(center - centerLine);
        if (centerDistance <= GRID_SNAP_RANGE && centerDistance < bestDistance) {
            bestDistance = centerDistance;
            bestPosition = centerLine - elementWidth / 2f;
        }

        float rightLine = getNearestLine(right, spacing);
        float rightDistance = Math.abs(right - rightLine);
        if (rightDistance <= GRID_SNAP_RANGE && rightDistance < bestDistance) {
            bestPosition = rightLine - elementWidth;
        }

        return Math.min(Math.max(bestPosition, 0f), maxX);
    }

    public float snapToGridY(float value, float elementHeight) {
        float windowHeight = mc.getWindow().getScaledHeight();
        float maxY = Math.max(0f, windowHeight - elementHeight);
        float clampedValue = Math.min(Math.max(value, 0f), maxY);

        float spacing = getGridSpacingY();
        if (spacing <= 0f) return clampedValue;

        float top = clampedValue;
        float bottom = clampedValue + elementHeight;
        float center = clampedValue + elementHeight / 2f;

        float bestPosition = clampedValue;
        float bestDistance = GRID_SNAP_RANGE + 1f;

        float topLine = getNearestLine(top, spacing);
        float topDistance = Math.abs(top - topLine);
        if (topDistance <= GRID_SNAP_RANGE && topDistance < bestDistance) {
            bestDistance = topDistance;
            bestPosition = topLine;
        }

        float centerLine = getNearestLine(center, spacing);
        float centerDistance = Math.abs(center - centerLine);
        if (centerDistance <= GRID_SNAP_RANGE && centerDistance < bestDistance) {
            bestDistance = centerDistance;
            bestPosition = centerLine - elementHeight / 2f;
        }

        float bottomLine = getNearestLine(bottom, spacing);
        float bottomDistance = Math.abs(bottom - bottomLine);
        if (bottomDistance <= GRID_SNAP_RANGE && bottomDistance < bestDistance) {
            bestPosition = bottomLine - elementHeight;
        }

        return Math.min(Math.max(bestPosition, 0f), maxY);
    }

    public int mouseX() {
        // HiDPI-safe: мышь в scaled-GUI-координатах = raw * scaledW / windowW (как в ваниле/AutoSwap).
        // Было /getScaleFactor() — на Retina (fb ×2) давало ПОЛОВИНУ, и HUD-элементы не перетаскивались.
        return (int) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
    }

    public int mouseY() {
        return (int) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
    }

    private boolean isShiftDown() {
        return InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private void renderGrid(DrawContext context, HudElement target) {
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        float spacingX = getGridSpacingX();
        float spacingY = getGridSpacingY();
        if (spacingX <= 0f || spacingY <= 0f) return;

        float areaLeft = Math.max(0, target.getX() - GRID_VISIBILITY_PADDING);
        float areaRight = Math.min(width, target.getX() + target.getWidth() + GRID_VISIBILITY_PADDING);
        float areaTop = Math.max(0, target.getY() - GRID_VISIBILITY_PADDING);
        float areaBottom = Math.min(height, target.getY() + target.getHeight() + GRID_VISIBILITY_PADDING);

        for (int i = 0; i < GRID_LINES; i++) {
            float x = i * spacingX;
            if (x < areaLeft || x > areaRight) continue;
            boolean highlight = i % 2 == 0;
            int color = (highlight ? GRID_HIGHLIGHT_COLOR : GRID_COLOR).getRGB();
            int xi = Math.round(x);
            context.fill(xi, 0, xi + 1, height, color);
        }

        for (int i = 0; i < GRID_LINES; i++) {
            float y = i * spacingY;
            if (y < areaTop || y > areaBottom) continue;
            boolean highlight = i % 2 == 0;
            int color = (highlight ? GRID_HIGHLIGHT_COLOR : GRID_COLOR).getRGB();
            int yi = Math.round(y);
            context.fill(0, yi, width, yi + 1, color);
        }
    }

    private HudElement getActiveGridTarget() {
        if (!HudEditScreen.editingActive() || Module.fullNullCheck()) return null;

        if (currentDragging instanceof DynamicIsland) return null;
        if (currentDragging != null) return currentDragging;

        for (HudElement element : hudElements) {
            if (element instanceof DynamicIsland) continue;
            if (MathUtils.isHovered(element.getX(), element.getY(), element.getWidth(), element.getHeight(), mouseX(), mouseY())) {
                return element;
            }
        }

        return null;
    }

    private float getGridSpacingX() {
        return GRID_LINES <= 1 ? 0f : mc.getWindow().getScaledWidth() / (float) (GRID_LINES - 1);
    }

    private float getGridSpacingY() {
        return GRID_LINES <= 1 ? 0f : mc.getWindow().getScaledHeight() / (float) (GRID_LINES - 1);
    }

    private float getNearestLine(float center, float spacing) {
        int index = Math.round(center / spacing);
        index = Math.max(0, Math.min(GRID_LINES - 1, index));
        return index * spacing;
    }

    public void handleElementToggle(BooleanSetting setting, boolean enabled, float clickX, float clickY) {
        HudElement element = getElementBySetting(setting);
        if (element == null) return;

        element.setToggled(enabled);

        // Обратная связь LowArmorNotify (модуль) <-> LowArmorNotificationsHud (элемент): включение/выключение
        // элемента синхронно врубает/гасит модуль. Меняем только при реальном рассинхроне — иначе петля.
        if (element instanceof LowArmorNotificationsHud) {
            LowArmorNotify module = LinkenVisuals.getInstance().getModuleManager().getModule(LowArmorNotify.class);
            if (module != null && module.isToggled() != enabled) {
                module.setToggled(enabled);
            }
        }

        if (enabled && !LinkenVisuals.getInstance().isConfigLoading()) {
            float currentX = element.getPosition().getValue().getX();
            float currentY = element.getPosition().getValue().getY();
            float windowWidth = Math.max(1f, mc.getWindow().getScaledWidth());
            float windowHeight = Math.max(1f, mc.getWindow().getScaledHeight());
            float elementWidth = Math.max(32f, element.getWidth());
            float elementHeight = Math.max(18f, element.getHeight());

            float currentPixelX = currentX * windowWidth;
            float currentPixelY = currentY * windowHeight;
            float maxX = Math.max(0f, windowWidth - elementWidth);
            float maxY = Math.max(0f, windowHeight - elementHeight);

            boolean isDefaultPosition = currentX == 0f && currentY == 0f;
            boolean isOutOfBounds = currentPixelX < 0f || currentPixelX > maxX || currentPixelY < 0f || currentPixelY > maxY;

            if (isDefaultPosition || isOutOfBounds) {
                // Новый элемент появляется по центру экрана (а не там, где кликнули по чипу внизу).
                float spawnX = MathUtils.clamp((windowWidth - elementWidth) / 2f, 0f, maxX);
                float spawnY = MathUtils.clamp((windowHeight - elementHeight) / 2f, 0f, maxY);
                element.getPosition().getValue().setX(spawnX / windowWidth);
                element.getPosition().getValue().setY(spawnY / windowHeight);
            }
        }
        saveHudConfig();
    }

    private void saveHudConfig() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.isConfigLoading()) return;
        instance.getConfigManager().forceSave();
    }

    public BooleanSetting getElementSetting(HudElement element) {
        return settingByElement.get(element);
    }

    private HudElement getElementBySetting(BooleanSetting setting) {
        return elementBySetting.get(setting);
    }

    private String getElementSettingKey(HudElement element) {
        return ELEMENT_SETTING_PREFIX + element.getName().toLowerCase();
    }

    private void addElements(HudElement... element) {
        this.hudElements.addAll(List.of(element));
    }

    @SuppressWarnings("unchecked")
    public <T extends HudElement> T getHudElement(Class<T> clazz) {
        for (HudElement element : hudElements) {
            if (clazz.isInstance(element)) {
                return (T) element;
            }
        }
        return null;
    }

    public boolean isElementVisible(HudElement element) {
        if (element == null || element.closed()) {
            return false;
        }
        BooleanSetting setting = getElementSetting(element);
        return setting == null || setting.getValue();
    }

    public boolean tryDynamicIslandClick(EventMouse event) {
        if (mc.currentScreen instanceof ChatScreen) {
            return false;
        }
        if (event.getAction() != 1) {
            return false;
        }
        DynamicIsland island = getHudElement(DynamicIsland.class);
        if (island == null || !isElementVisible(island)) {
            return false;
        }
        return island.handleScreenClick(mouseX(), mouseY(), event.getButton());
    }
}
