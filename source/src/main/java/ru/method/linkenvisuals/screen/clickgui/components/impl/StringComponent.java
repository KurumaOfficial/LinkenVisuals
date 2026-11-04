package ru.method.linkenvisuals.screen.clickgui.components.impl;

import ru.method.linkenvisuals.modules.settings.impl.StringSetting;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class StringComponent extends Component {

    private static final float H_PADDING = 5f;
    private static final float LABEL_TOP_PADDING = 2f;
    private static final float LABEL_BOTTOM_PADDING = 4f;
    // 7.5f — как у лейблов всех остальных компонентов (Boolean/Enum/Bind/Color), раньше было 6.2f и
    // подписи полей (например в StreamerMode) выглядели мельче и выбивались из общего шрифта.
    private static final float LABEL_FONT_SIZE = 7.5f;
    private static final float FIELD_HEIGHT = 14f;
    private static final float FIELD_RADIUS = 2.5f;

    private final StringSetting setting;
    private boolean focused;
    private boolean selected;
    private final Animation animation = new Animation(180, 1f, false, Easing.BOTH_SINE);
    private float scrollOffset = 0f;
    private String buffer = "";

    public StringComponent(StringSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.height = getPreferredHeight();

        float labelX = x + H_PADDING;
        float labelY = y + LABEL_TOP_PADDING;
        float fieldX = x + H_PADDING;
        float fieldY = y + LABEL_TOP_PADDING + LABEL_FONT_SIZE + LABEL_BOTTOM_PADDING;
        float fieldWidth = Math.max(1f, width - (H_PADDING * 2f));
        float maxTextWidth = Math.max(1f, fieldWidth - 8f);

        animation.update(focused);

        if (!focused) {
            buffer = setting.getValue() == null ? "" : setting.getValue();
        } else {
            setting.setValue(buffer);
            buffer = setting.getValue() == null ? "" : setting.getValue();
        }

        String label = I18n.translate(setting.getName());
        String placeholderKey = setting.getPlaceholder();
        String placeholder = placeholderKey == null || placeholderKey.isEmpty() ? label : I18n.translate(placeholderKey);
        String safeBuffer = buffer == null ? "" : buffer;
        boolean hasValue = !safeBuffer.isEmpty();
        boolean showPlaceholder = !hasValue && !focused;
        String visibleText = showPlaceholder ? placeholder : safeBuffer;

        float bufferWidth = hasValue ? Fonts.ROUNDBOLD.getWidth(safeBuffer, 7.5f) : 0f;
        float textWidth = Fonts.ROUNDBOLD.getWidth(visibleText, 7.5f);

        if (hasValue && bufferWidth > maxTextWidth) {
            if (bufferWidth - scrollOffset > maxTextWidth) scrollOffset = bufferWidth - maxTextWidth;
            if (bufferWidth - scrollOffset < 0) scrollOffset = bufferWidth;
        } else scrollOffset = 0f;

        ctxDrawLabel(context, labelX, labelY);
        drawFieldBackground(context, fieldX, fieldY, fieldWidth);

        float textX = fieldX + 4f;
        float textY = fieldY + 3.1f;

        Render2D.startScissor(context, fieldX + 1f, fieldY, fieldWidth - 2f, FIELD_HEIGHT);

        if (selected && hasValue) {
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    textX,
                    textY,
                    Math.max(1f, bufferWidth),
                    Math.max(1f, Fonts.ROUNDBOLD.getHeight(7.5f)),
                    0f,
                    new Color(0, 0, 255, 150)
            );
        }

        Color textColor = ColorUtils.getTextColor(255);
        if (showPlaceholder) {
            Render2D.drawFont(
                    context.getMatrices(),
                    Fonts.ROUNDBOLD.getFont(7.5f),
                    placeholder,
                    textX,
                    textY,
                    ColorUtils.getTextColor((int) (200 * animation.getReversedValue()))
            );
        } else if (hasValue) {
            Render2D.drawFont(
                    context.getMatrices(),
                    Fonts.ROUNDBOLD.getFont(7.5f),
                    safeBuffer,
                    textX - scrollOffset,
                    textY,
                    textColor
            );
        }

        if (focused) {
            Color pulseColor = ColorUtils.pulse(textColor, 15);
            Render2D.drawFont(
                    context.getMatrices(),
                    Fonts.ROUNDBOLD.getFont(7.5f),
                    "|",
                    textX - scrollOffset + bufferWidth,
                    textY - 0.25f,
                    ColorUtils.alpha(pulseColor, (int) (pulseColor.getAlpha() * animation.getValue()))
            );
        }

        Render2D.stopScissor(context);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;

        float fieldX = x + H_PADDING;
        float fieldY = y + LABEL_TOP_PADDING + LABEL_FONT_SIZE + LABEL_BOTTOM_PADDING;
        float fieldWidth = Math.max(1f, width - (H_PADDING * 2f));

        boolean nextFocused = MathUtils.isHovered(fieldX, fieldY, fieldWidth, FIELD_HEIGHT, (float) mouseX, (float) mouseY);
        if (focused && !nextFocused) {
            commitBuffer();
        }
        focused = nextFocused;
        selected = false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {}

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selected) {
                    buffer = "";
                    selected = false;
                    if (focused) {
                        commitBuffer();
                    }
                }
                if (focused && buffer != null && !buffer.isEmpty()) {
                    buffer = buffer.substring(0, buffer.length() - 1);
                    commitBuffer();
                }
            }
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> {
                if (focused) {
                    commitBuffer();
                    focused = false;
                    selected = false;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (focused) {
                    buffer = "";
                    selected = false;
                    commitBuffer();
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrlDown() && focused && selected && buffer != null && !buffer.isEmpty()) {
                    GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), buffer);
                    selected = false;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrlDown() && focused && GLFW.glfwGetClipboardString(mc.getWindow().getHandle()) != null) {
                    selected = false;
                    buffer = (buffer == null ? "" : buffer) + GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                    commitBuffer();
                }
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrlDown() && focused && buffer != null && !buffer.isEmpty()) selected = true;
            }
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void charTyped(char chr, int modifiers) {
        if (!focused) return;
        if (selected) {
            buffer = "";
        }
        if (buffer != null && buffer.length() >= setting.getMaxLength()) return;
        buffer = (buffer == null ? "" : buffer) + chr;
        selected = false;
        commitBuffer();
    }

    @Override
    public void onClose() {
        commitBuffer();
        focused = false;
        selected = false;
        scrollOffset = 0f;
        buffer = "";
    }

    @Override
    public float getLayoutHeight() {
        return getPreferredHeight();
    }

    private void commitBuffer() {
        setting.setValue(buffer == null ? "" : buffer);
        buffer = setting.getValue() == null ? "" : setting.getValue();
    }

    private float getPreferredHeight() {
        return LABEL_TOP_PADDING + LABEL_FONT_SIZE + LABEL_BOTTOM_PADDING + FIELD_HEIGHT + 4f;
    }

    private void ctxDrawLabel(DrawContext context, float labelX, float labelY) {
        Color labelColor = new Color(230, 230, 230, 235);
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(LABEL_FONT_SIZE), I18n.translate(setting.getName()), labelX, labelY, labelColor);
    }

    private void drawFieldBackground(DrawContext context, float fieldX, float fieldY, float fieldWidth) {
        Color bg = new Color(23, 23, 23, 100);
        Color outline = ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), focused ? 200 : 130);
        Render2D.drawRoundedRect(context.getMatrices(), fieldX, fieldY, fieldWidth, FIELD_HEIGHT, FIELD_RADIUS, bg);
        Render2D.drawRoundedRect(context.getMatrices(), fieldX, fieldY, fieldWidth, FIELD_HEIGHT, FIELD_RADIUS, outline);
    }


    private static boolean ctrlDown() {
        var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        return net.minecraft.client.util.InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                || net.minecraft.client.util.InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
