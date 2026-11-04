package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.managers.system.SoundManager;
import ru.method.linkenvisuals.modules.settings.impl.*;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.screen.clickgui.Panel;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.Render2D;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModuleComponent extends Component {
    private static final Identifier UNSAFE_ICON_TEXTURE = LinkenVisuals.id("client/warning/unsafe.png");

    private final Module module;
    private final String lowerName;
    private final boolean hasSettings;
    private Panel parentPanel;
    private boolean binding;
    private long lastBindTimeMs;
    @Getter
    private final List<Component> components = new ArrayList<>();
    private final Animation alphaAnimation = new Animation(180, 1f, false, Easing.BOTH_SINE);
    private final Animation toggleAnimation = new Animation(90, 1f, false, Easing.BOTH_SINE);
    private final Animation showingAnimation = new Animation(180, 1f, false, Easing.BOTH_SINE);
    @Getter
    private final Animation openAnimation = new Animation(180, 1f, false, Easing.BOTH_SINE);

    public ModuleComponent(Module module) {
        super(module.getName());
        this.module = module;
        this.lowerName = module.getName().toLowerCase();
        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting) components.add(new BooleanComponent((BooleanSetting) setting));
            else if (setting instanceof NumberSetting) components.add(new SliderComponent((NumberSetting) setting));
            else if (setting instanceof EnumSetting) {
                EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
                if ("settings.hitsound.sound".equals(enumSetting.getName())) {
                    components.add(new EnumButtonComponent(enumSetting));
                } else {
                    components.add(new EnumComponent(enumSetting));
                }
            }
            else if (setting instanceof StringSetting) components.add(new StringComponent((StringSetting) setting));
            else if (setting instanceof ListSetting) components.add(new ListComponent((ListSetting) setting));
            else if (setting instanceof BindSetting) components.add(new BindComponent((BindSetting) setting));
            else if (setting instanceof ColorSetting) components.add(new ColorComponent((ColorSetting) setting));
            else if (setting instanceof ActionSetting) components.add(new ActionButtonComponent((ActionSetting) setting));
        }
        if (module instanceof UI uiModule) {
            components.add(new DualActionButtonComponent(
                    "settings.ui.apply",
                    uiModule::applyScaleSetting,
                    "settings.ui.default",
                    uiModule::resetScaleSetting
            ));
        }
        this.hasSettings = !components.isEmpty();
    }

    private boolean isAltDown() {
        if (mc == null || mc.getWindow() == null) return false;
        return net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                || net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean showing = isAltDown()
                && !binding
                && !module.getBind().isEmpty()
                && !(module instanceof UI)
                && (System.currentTimeMillis() - lastBindTimeMs) > 300L;
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
        boolean unsafe = module.isUnsafe();
        float warningX = x + 9.1f;
        float warningY = y + 9.0f;
        boolean warningHovered = unsafe && MathUtils.isHovered(warningX - 5.8f, warningY - 5.8f, 11.6f, 11.6f, mouseX, mouseY);
        String bindName = module.getBind().toString().replace("_", " ");
        String bindLabel = "Key: " + bindName;

        if (!showing) {
            alphaAnimation.update(hovered || module.isToggled());
        }

        toggleAnimation.update(module.isToggled());
        showingAnimation.update(showing);

        int idleAlpha = (int) (200 + (55 * alphaAnimation.getValue()));
        Color baseTextColor = ColorUtils.getTextColor(idleAlpha);
        Color textColor = ColorUtils.getTextColor(255);
        Color iconColor = ColorUtils.getTextColor((int) (255 * toggleAnimation.getValue()));
        int fadedNameAlpha = Math.max(170, (int) (baseTextColor.getAlpha() * showingAnimation.getReversedValue()));
        Color finalTextColor = showingAnimation.getValue() > 0.2f
                ? new Color(baseTextColor.getRed(), baseTextColor.getGreen(), baseTextColor.getBlue(), fadedNameAlpha)
                : baseTextColor;
        SoundManager soundManager = LinkenVisuals.getInstance().getSoundManager();
        float shakeOffset = soundManager != null ? soundManager.getCriticalShakeOffset(module) : 0f;

        context.getMatrices().pushMatrix();
        if (shakeOffset != 0f) {
            context.getMatrices().translate(shakeOffset, 0f);
        }

        if (binding) {
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(8f), bindLabel, x + 7f, y + 5f, baseTextColor);
        } else {
            Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(5.5f), "H", x + width - 20f, y + 6.5f, iconColor);
            float nameX = x + 7f;
            if (unsafe) {
                drawUnsafeIcon(context, warningX, warningY);
                nameX += 15f;
            }
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(8f), module.getDisplayName(), nameX, y + 5f, finalTextColor);

            if (hasSettings) {
                Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(8f), "A", x + width - 9.5f, y + 5f, finalTextColor);
            }
        }

        Render2D.drawFont(context.getMatrices(),
                Fonts.REGULAR.getFont(8f),
                bindName,
                x + 7f - ((Fonts.REGULAR.getWidth(bindLabel, 8f) + 10f) * showingAnimation.getReversedValue()),
                y + 5f,
                textColor
        );

        if (hovered && parentPanel != null && parentPanel.isMouseOverContentArea(mouseX, mouseY) && !parentPanel.isSettingsViewActive()) {
            String descriptionKey = warningHovered ? module.getUnsafeDescriptionKey() : module.getDescription();
            LinkenVisuals.getInstance().getClickGui().setDescription(I18n.translate(descriptionKey));
        }

        context.getMatrices().popMatrix();
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (binding) {
            module.setBind(new Bind(button, true));
            binding = false;
            return;
        }

        boolean hovered = MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY);
        if (!hovered) return;

        boolean overSettingsIcon = MathUtils.isHovered(x + width - 12f, y, 12f, height, (float) mouseX, (float) mouseY);

        if (overSettingsIcon && button == 0 && hasSettings) {
            openSettingsView();
            return;
        }

        if (button == 0) {
            module.toggle();
        } else if (button == 1 && hasSettings) {
            openSettingsView();
        } else if (button == 2) {
            binding = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {}

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) module.setBind(new Bind(-1, false));
            else module.setBind(new Bind(keyCode, false));
            binding = false;
            lastBindTimeMs = System.currentTimeMillis();
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void charTyped(char chr, int modifiers) {}

    @Override
    public void onClose() {
        binding = false;
    }

    @Override
    public boolean isBinding() {
        return binding;
    }

    private void openSettingsView() {
        if (components.isEmpty()) return;
        if (parentPanel != null) {
            parentPanel.openModuleSettings(this);
        }
    }

    public void setParentPanel(Panel parentPanel) {
        this.parentPanel = parentPanel;
    }

    public Module getModule() {
        return module;
    }

    public boolean matchesSearch(String normalizedSearchQuery) {
        if (normalizedSearchQuery == null || normalizedSearchQuery.isEmpty()) {
            return true;
        }
        return lowerName.contains(normalizedSearchQuery)
                || module.getDisplayName().toLowerCase(Locale.ROOT).contains(normalizedSearchQuery);
    }

    private void drawUnsafeIcon(DrawContext context, float centerX, float centerY) {
        float size = 11.4f;
        Render2D.drawCircle(context.getMatrices(), centerX, centerY, 5.65f, 28, new Color(255, 54, 54, 28));
        Render2D.drawTexture(
                context.getMatrices(),
                centerX - size / 2f,
                centerY - size / 2f,
                size,
                size,
                0f,
                UNSAFE_ICON_TEXTURE,
                Color.WHITE
        );
    }

}
