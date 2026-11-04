package ru.method.linkenvisuals.screen.clickgui.components.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Locale;
public class ColorComponent extends Component {

    private static final Identifier HUE_TEXTURE = LinkenVisuals.id("client/hue/hue.png");
    private static final Identifier EMPTY_TEXTURE = LinkenVisuals.id("client/hue/empty.png");
    private static final Identifier CLOSE_TEXTURE = LinkenVisuals.id("client/colorpicker/xmark.png");

    private static final int EMPTY_TEXTURE_WIDTH = 200;
    private static final int EMPTY_TEXTURE_HEIGHT = 24;
    private static final float CLOSE_BUTTON_SIZE = 14f;

    private static final float PICKER_PADDING = 10f;
    private static final float PICKER_TITLE_HEIGHT = 18f;
    private static final float SV_SIZE = 110f;
    private static final float HUE_BAR_WIDTH = 14f;
    private static final float HUE_BAR_GAP = 8f;
    private static final float OPACITY_SLIDER_HEIGHT = 11f;
    private static final float OPACITY_SECTION_GAP = 8f;
    private static final float OPACITY_LABEL_GAP = 10f;
    private static final float OPACITY_PREVIEW_WIDTH = 26f;
    private static final float OPACITY_PREVIEW_GAP = 6f;
    private static final float HEX_SECTION_GAP = 8f;
    private static final float HEX_LABEL_GAP = 9f;
    private static final float HEX_FIELD_HEIGHT = 11f;
    private static final float HEX_FIELD_RADIUS = 4f;
    private static final int HEX_MAX_LENGTH = 9;

    private final ColorSetting setting;
    private boolean hovering;
    private boolean editing;

    private float pickerX;
    private float pickerY;
    private float pickerWidth;
    private float pickerHeight;

    private boolean pickerManualPosition;
    private boolean draggingPicker;
    private float pickerDragOffsetX;
    private float pickerDragOffsetY;

    private boolean closing;
    private final Animation pickerAnimation = new Animation(140, 1f, true, Easing.EASE_OUT_EXPO);

    private boolean draggingSv;
    private boolean draggingHue;
    private boolean draggingOpacity;

    private float hue;
    private float saturation;
    private float value;
    private float opacity;
    private boolean hsvInitialized;
    private boolean hexFocused;
    private boolean hexSelected;
    private String hexBuffer = "";
    private float hexFieldX;
    private float hexFieldY;
    private float hexFieldW;
    private float hexFieldH;

    private float previewButtonX;
    private float previewButtonY;
    private float previewButtonSize;

    public ColorComponent(ColorSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.visible = setting::isVisible;
        this.setAddHeight(() -> 0f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        hovering = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);

        // Правый отступ как у тогглера (его правый край на x+width-4) — ровные отступы в строках настроек.
        previewButtonSize = height - 8f;
        previewButtonX = x + width - previewButtonSize - 4f;
        previewButtonY = y + (height - previewButtonSize) / 2f;

        String translatedName = I18n.translate(setting.getName());
        float labelX = x + 4f;
        float labelMaxWidth = Math.max(0f, previewButtonX - labelX - 4f);
        if (ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.isActive()) {
            // В Link-режиме название рисуем так же, как у остальных строк настроек (тот же шрифт MEDIUM
            // и вертикальное центрирование) — раньше цвет был другим шрифтом и не ровно.
            ru.method.linkenvisuals.screen.linkgui.LinkGuiSettingStyle.drawRowLabel(
                    context.getMatrices(), translatedName, x + 2f, y, labelMaxWidth);
        } else {
            float labelWidth = Fonts.REGULAR.getWidth(translatedName, 7.5f);
            float offset = getMarqueeOffset(labelWidth, labelMaxWidth, hovering);
            Render2D.startScissor(context, labelX, y, labelMaxWidth, height);
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), translatedName, labelX - offset, y + 5f, ColorUtils.getTextColor(255));
            if (offset > 0f && labelWidth > labelMaxWidth) {
                Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), translatedName, labelX - offset + labelWidth + MARQUEE_GAP, y + 5f, ColorUtils.getTextColor(255));
            }
            Render2D.stopScissor(context);
        }

        Color valueColor = setting.getValue();

        float swatchRadius = Math.min(5f, previewButtonSize * 0.5f);
        Render2D.drawRoundedRect(context.getMatrices(), previewButtonX, previewButtonY, previewButtonSize, previewButtonSize, swatchRadius, valueColor);
        Render2D.drawBorder(
                context.getMatrices(),
                previewButtonX,
                previewButtonY,
                previewButtonSize,
                previewButtonSize,
                swatchRadius,
                0.5f,
                new ColorRGBA(ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 170))
        );
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            if (editing) {
                editing = false;
                draggingSv = false;
                draggingHue = false;
                draggingOpacity = false;
            }
            return;
        }

        if (editing) {
            updatePickerBounds();
            updateHexFieldBounds();

            if (isInsideClose(mouseX, mouseY)) {
                requestClose();
                draggingPicker = false;
                draggingSv = false;
                draggingHue = false;
                draggingOpacity = false;
                hexFocused = false;
                hexSelected = false;
                return;
            }

            if (isInsideTitleBar(mouseX, mouseY) && !isInsideClose(mouseX, mouseY)) {
                pickerManualPosition = true;
                draggingPicker = true;
                pickerDragOffsetX = (float) mouseX - pickerX;
                pickerDragOffsetY = (float) mouseY - pickerY;
                hexFocused = false;
                hexSelected = false;
                return;
            }

            if (isInsideSv(mouseX, mouseY)) {
                draggingSv = true;
                hexFocused = false;
                hexSelected = false;
                updateSv(mouseX, mouseY);
                return;
            }

            if (isInsideHueBar(mouseX, mouseY)) {
                draggingHue = true;
                hexFocused = false;
                hexSelected = false;
                updateHue(mouseY);
                return;
            }

            if (isInsideOpacitySlider(mouseX, mouseY)) {
                draggingOpacity = true;
                hexFocused = false;
                hexSelected = false;
                updateOpacity(mouseX);
                return;
            }

            if (isInsideHexField(mouseX, mouseY)) {
                focusHexInput();
                return;
            }
            hexFocused = false;
            hexSelected = false;

            if (hovering || !isInsideExtended(mouseX, mouseY)) {
                requestClose();
                draggingPicker = false;
                draggingSv = false;
                draggingHue = false;
                draggingOpacity = false;
            }
            return;
        }

        if (MathUtils.isHovered(previewButtonX, previewButtonY, previewButtonSize, previewButtonSize, (float) mouseX, (float) mouseY)) {
            editing = true;
            pickerManualPosition = false;
            draggingPicker = false;
            draggingSv = false;
            draggingHue = false;
            draggingOpacity = false;
            hsvInitialized = false;
            closing = false;
            hexFocused = false;
            hexSelected = false;
            hexBuffer = "";
            pickerAnimation.snapTo(true);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingPicker = false;
            draggingSv = false;
            draggingHue = false;
            draggingOpacity = false;
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing || !hexFocused) return;

        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                hexFocused = false;
                hexSelected = false;
                syncHexBuffer();
            }
            case GLFW.GLFW_KEY_ENTER -> {
                applyHexBuffer();
                hexFocused = false;
                hexSelected = false;
                syncHexBuffer();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hexSelected) {
                    hexBuffer = "";
                    hexSelected = false;
                    return;
                }
                if (hexBuffer != null && !hexBuffer.isEmpty()) {
                    hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
                    hexBuffer = normalizeHexInput(hexBuffer);
                    applyHexBuffer();
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                hexBuffer = "";
                hexSelected = false;
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrlDown()) {
                    String copy = formatHex(setting.getValue());
                    GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), copy);
                    hexSelected = false;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrlDown()) {
                    String clipboard = GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                    if (clipboard != null && !clipboard.isBlank()) {
                        hexBuffer = normalizeHexInput(clipboard);
                        hexSelected = false;
                        applyHexBuffer();
                    }
                }
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrlDown()) {
                    hexSelected = hexBuffer != null && !hexBuffer.isEmpty();
                }
            }
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (!editing || !hexFocused) return;
        if (!isHexChar(chr) && chr != '#') return;
        if (hexSelected) {
            hexBuffer = "";
        }
        String next = (hexBuffer == null ? "" : hexBuffer) + chr;
        hexBuffer = normalizeHexInput(next);
        hexSelected = false;
        applyHexBuffer();
    }

    @Override
    public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!editing) return;

        if (hasActiveDrag() && !isLeftMouseDown()) {
            stopDragging();
        }

        pickerAnimation.update(!closing);
        float anim = (float) pickerAnimation.getValue();
        if (closing && anim <= 0.01f) {
            editing = false;
            closing = false;
            stopDragging();
            hexFocused = false;
            hexSelected = false;
            hexBuffer = "";
            return;
        }

        updatePickerBounds();
        syncHsvFromSettingIfNeeded();
        syncHexBuffer();

        if (draggingPicker) {
            pickerManualPosition = true;
            pickerX = mouseX - pickerDragOffsetX;
            pickerY = mouseY - pickerDragOffsetY;
            clampPickerToScreen();
        }

        if (draggingSv) {
            updateSv(mouseX, mouseY);
        }
        if (draggingHue) {
            updateHue(mouseY);
        }
        if (draggingOpacity) {
            updateOpacity(mouseX);
        }

        // Прозрачность пикера синхронизируем с фейдом всего GUI (Link), иначе палитра «отстаёт» при закрытии.
        float guiFade = ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.isActive()
                ? ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.getFadeMultiplier() : 1f;
        int globalAlpha = Math.max(0, Math.min(255, Math.round(255f * anim * guiFade)));

        context.getMatrices().pushMatrix();
        float centerX = pickerX + pickerWidth / 2f;
        float centerY = pickerY + pickerHeight / 2f;
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(anim, anim);
        context.getMatrices().translate(-centerX, -centerY);

        // Панель пикера: в Link GUI — цвета Link-темы (chrome/accent); в Panel GUI — классическое
        // стекло ColorUtils (как остальной ClickGui), иначе палитра выглядела «из Link GUI» в панель-режиме.
        if (ru.method.linkenvisuals.utils.render.LinkGuiRenderContext.isActive()) {
            Color linkBg = ru.method.linkenvisuals.screen.linkgui.LinkGuiChrome.panelFill();
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    pickerX, pickerY, pickerWidth, pickerHeight,
                    Render2D.DEFAULT_CORNER_RADIUS,
                    new Color(linkBg.getRed(), linkBg.getGreen(), linkBg.getBlue(), globalAlpha)
            );
            Color linkOutline = ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope.accent();
            Render2D.drawBorder(
                    context.getMatrices(),
                    pickerX, pickerY, pickerWidth, pickerHeight,
                    Render2D.DEFAULT_CORNER_RADIUS,
                    0.9f,
                    new ColorRGBA(new Color(linkOutline.getRed(), linkOutline.getGreen(), linkOutline.getBlue(),
                            Math.round(globalAlpha * 0.6f)))
            );
        } else {
            Color glassBg = ColorUtils.getGlassBase();
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    pickerX, pickerY, pickerWidth, pickerHeight,
                    Render2D.DEFAULT_CORNER_RADIUS,
                    new Color(glassBg.getRed(), glassBg.getGreen(), glassBg.getBlue(), globalAlpha)
            );
            Color classicOutline = ColorUtils.getPrimaryColor();
            Render2D.drawBorder(
                    context.getMatrices(),
                    pickerX, pickerY, pickerWidth, pickerHeight,
                    Render2D.DEFAULT_CORNER_RADIUS,
                    0.9f,
                    new ColorRGBA(new Color(classicOutline.getRed(), classicOutline.getGreen(), classicOutline.getBlue(),
                            Math.round(globalAlpha * 0.6f)))
            );
        }

        String title = I18n.translate(setting.getName());
        float titleFontSize = 8.5f;
        float titleW = Fonts.SEMIBOLD.getWidth(title, titleFontSize);
        float titleX = pickerX + (pickerWidth - titleW) / 2f;
        float titleY = pickerY + 5f;
        float closeSize = CLOSE_BUTTON_SIZE;
        float closeX = getCloseX();
        float maxTitleX = closeX - 2f - titleW;
        titleX = MathUtils.clamp(titleX, pickerX + PICKER_PADDING, Math.max(pickerX + PICKER_PADDING, maxTitleX));
        Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(titleFontSize), title, titleX, titleY, ColorUtils.getTextColor(globalAlpha));

        float closeY = getCloseY();
        boolean closeHovered = MathUtils.isHovered(closeX, closeY, closeSize, closeSize, mouseX, mouseY);
        Render2D.drawRoundedRect(
                context.getMatrices(),
                closeX,
                closeY,
                closeSize,
                closeSize,
                4f,
                ColorUtils.getGlassColor(closeHovered ? Math.min(255, globalAlpha) : Math.min(220, globalAlpha))
        );
        float iconPad = 0.8f;
        drawSmoothTexture(
                context,
                CLOSE_TEXTURE,
                closeX + iconPad,
                closeY + iconPad,
                closeSize - iconPad * 2f,
                closeSize - iconPad * 2f,
                0f,
                ColorUtils.getTextColor(globalAlpha),
                true
        );

        float svX = pickerX + PICKER_PADDING;
        float svY = pickerY + PICKER_TITLE_HEIGHT;
        float contentWidth = getContentWidth();

        drawSvSquare(context, svX, svY, SV_SIZE);

        float hueX = svX + SV_SIZE + HUE_BAR_GAP;
        float hueY = svY;
        drawHueBar(context, hueX, hueY, HUE_BAR_WIDTH, SV_SIZE);

        float opacityLabelY = svY + SV_SIZE + OPACITY_SECTION_GAP;
        String opacityLabel = I18n.translate("linkenvisuals.colorpicker.opacity");
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(7f), opacityLabel, svX, opacityLabelY, new Color(230, 232, 240, globalAlpha));
        String percent = Math.round(opacity * 100f) + "%";
        float percentW = Fonts.ROUNDBOLD.getWidth(percent, 7f);
        float opacitySliderW = getOpacitySliderWidth();
        float opacitySliderX = getOpacitySliderX();
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(7f), percent, opacitySliderX + opacitySliderW - percentW, opacityLabelY, new Color(230, 232, 240, globalAlpha));

        float opacitySliderY = opacityLabelY + OPACITY_LABEL_GAP;
        drawOpacityPreview(context, svX, opacitySliderY, OPACITY_PREVIEW_WIDTH, OPACITY_SLIDER_HEIGHT);
        drawOpacitySlider(context, opacitySliderX, opacitySliderY, opacitySliderW, OPACITY_SLIDER_HEIGHT);

        float hexLabelY = opacitySliderY + OPACITY_SLIDER_HEIGHT + HEX_SECTION_GAP;
        float hexFieldY = hexLabelY + HEX_LABEL_GAP;
        float hexFieldX = svX;
        float hexFieldW = contentWidth;
        float hexFieldH = HEX_FIELD_HEIGHT;
        this.hexFieldX = hexFieldX;
        this.hexFieldY = hexFieldY;
        this.hexFieldW = hexFieldW;
        this.hexFieldH = hexFieldH;

        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(7f), "HEX", hexFieldX, hexLabelY, new Color(230, 232, 240, globalAlpha));

        int fieldAlpha = Math.min(100, globalAlpha);
        int outlineAlpha = Math.min(hexFocused ? 200 : 130, globalAlpha);
        Color fieldBg = new Color(23, 23, 23, fieldAlpha);
        Color fieldOutline = ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), outlineAlpha);
        Render2D.drawRoundedRect(context.getMatrices(), hexFieldX, hexFieldY, hexFieldW, hexFieldH, HEX_FIELD_RADIUS, fieldBg);
        Render2D.drawRoundedRect(context.getMatrices(), hexFieldX, hexFieldY, hexFieldW, hexFieldH, HEX_FIELD_RADIUS, fieldOutline);

        String hexText = getHexDisplayText();
        float hexTextSize = 7.5f;
        float hexTextWidth = Fonts.REGULAR.getWidth(hexText, hexTextSize);
        float hexTextX = hexFieldX + 4f;
        float hexTextHeight = Fonts.REGULAR.getHeight(hexTextSize);
        float hexTextY = hexFieldY + (hexFieldH - hexTextHeight) / 2f;
        Render2D.startScissor(context, hexFieldX + 1f, hexFieldY + 1f, hexFieldW - 2f, hexFieldH - 2f);

        if (hexFocused && hexSelected && !hexText.isEmpty()) {
            Render2D.drawRoundedRect(
                    context.getMatrices(),
                    hexTextX,
                    hexTextY,
                    Math.max(1f, hexTextWidth),
                    Math.max(1f, Fonts.REGULAR.getHeight(hexTextSize)),
                    0f,
                    new Color(0, 120, 215, Math.min(150, globalAlpha))
            );
        }

        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(hexTextSize), hexText, hexTextX, hexTextY, ColorUtils.getTextColor(globalAlpha));

        if (hexFocused) {
            Color pulseColor = ColorUtils.pulse(ColorUtils.getTextColor(globalAlpha), 15);
            Render2D.drawFont(
                    context.getMatrices(),
                    Fonts.REGULAR.getFont(hexTextSize),
                    "|",
                    hexTextX + hexTextWidth,
                    hexTextY,
                    ColorUtils.alpha(pulseColor, Math.min(globalAlpha, pulseColor.getAlpha()))
            );
        }

        Render2D.stopScissor(context);

        context.getMatrices().popMatrix();
    }

    private void updatePickerBounds() {
        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();
        float margin = 6f;

        pickerWidth = getContentWidth() + PICKER_PADDING * 2f;
        pickerHeight = PICKER_TITLE_HEIGHT
                + SV_SIZE
                + OPACITY_SECTION_GAP
                + OPACITY_LABEL_GAP
                + OPACITY_SLIDER_HEIGHT
                + HEX_SECTION_GAP
                + HEX_LABEL_GAP
                + HEX_FIELD_HEIGHT
                + PICKER_PADDING;

        if (pickerManualPosition) {
            clampPickerToScreen();
            return;
        }

        float desiredX = x + width + margin;

        ClickGui clickGui = LinkenVisuals.getInstance().getClickGui();
        float guiLeft = clickGui != null ? clickGui.getGuiLeft() : 0f;
        float guiRight = clickGui != null ? clickGui.getGuiRight() : 0f;
        boolean boundsReady = guiRight > guiLeft;
        float componentRight = x + width;

        if (boundsReady) {
            if (x >= guiRight) {
                desiredX = x + width + margin;
            } else if (componentRight <= guiLeft) {
                desiredX = x - margin - pickerWidth;
            } else {
                desiredX = x + width + margin;
            }
        }

        if (desiredX + pickerWidth + margin > screenW) {
            desiredX = x - margin - pickerWidth;
        }
        if (desiredX < margin) {
            desiredX = margin;
        }
        pickerX = desiredX;

        float desiredY = y;
        if (desiredY + pickerHeight + margin > screenH) {
            desiredY = screenH - pickerHeight - margin;
        }
        if (desiredY < margin) {
            desiredY = margin;
        }
        pickerY = desiredY;
    }

    private void clampPickerToScreen() {
        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();
        float margin = 6f;
        pickerX = MathUtils.clamp(pickerX, margin, Math.max(margin, screenW - pickerWidth - margin));
        pickerY = MathUtils.clamp(pickerY, margin, Math.max(margin, screenH - pickerHeight - margin));
    }

    private float getContentWidth() {
        return SV_SIZE + HUE_BAR_GAP + HUE_BAR_WIDTH;
    }

    private float getCloseX() {
        float closeSize = CLOSE_BUTTON_SIZE;
        return pickerX + pickerWidth - PICKER_PADDING - closeSize;
    }

    private float getCloseY() {
        return pickerY + 3f;
    }

    private boolean isInsideClose(double mouseX, double mouseY) {
        float closeSize = CLOSE_BUTTON_SIZE;
        float closeX = getCloseX();
        float closeY = getCloseY();
        return mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize;
    }

    private boolean isInsideTitleBar(double mouseX, double mouseY) {
        return mouseX >= pickerX && mouseX <= pickerX + pickerWidth
                && mouseY >= pickerY && mouseY <= pickerY + PICKER_TITLE_HEIGHT;
    }

    private boolean isInsideExtended(double mouseX, double mouseY) {
        return mouseX >= pickerX && mouseX <= pickerX + pickerWidth
                && mouseY >= pickerY && mouseY <= pickerY + pickerHeight;
    }

    private boolean isInsideSv(double mouseX, double mouseY) {
        float svX = pickerX + PICKER_PADDING;
        float svY = pickerY + PICKER_TITLE_HEIGHT;
        return mouseX >= svX && mouseX <= svX + SV_SIZE && mouseY >= svY && mouseY <= svY + SV_SIZE;
    }

    private boolean isInsideHueBar(double mouseX, double mouseY) {
        float svX = pickerX + PICKER_PADDING;
        float svY = pickerY + PICKER_TITLE_HEIGHT;
        float hueX = svX + SV_SIZE + HUE_BAR_GAP;
        float hueY = svY;
        return mouseX >= hueX && mouseX <= hueX + HUE_BAR_WIDTH && mouseY >= hueY && mouseY <= hueY + SV_SIZE;
    }

    private boolean isInsideOpacitySlider(double mouseX, double mouseY) {
        float svY = pickerY + PICKER_TITLE_HEIGHT;
        float opacityLabelY = svY + SV_SIZE + OPACITY_SECTION_GAP;
        float opacitySliderY = opacityLabelY + OPACITY_LABEL_GAP;
        float sliderX = getOpacitySliderX();
        float sliderW = getOpacitySliderWidth();
        return mouseX >= sliderX && mouseX <= sliderX + sliderW
                && mouseY >= opacitySliderY && mouseY <= opacitySliderY + OPACITY_SLIDER_HEIGHT;
    }

    private void syncHsvFromSettingIfNeeded() {
        if (hsvInitialized) return;
        Color current = setting.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        value = hsb[2];
        opacity = current.getAlpha() / 255.0f;
        hsvInitialized = true;
    }

    private void applyCurrentColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, value);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = clampInt(Math.round(opacity * 255.0f), 0, 255);
        setting.setValue(new Color(r, g, b, a));
    }

    private void updateSv(double mouseX, double mouseY) {
        float svX = pickerX + PICKER_PADDING;
        float svY = pickerY + PICKER_TITLE_HEIGHT;
        float sx = (float) ((mouseX - svX) / SV_SIZE);
        float vy = (float) ((mouseY - svY) / SV_SIZE);
        saturation = MathUtils.clamp(sx, 0f, 1f);
        value = 1f - MathUtils.clamp(vy, 0f, 1f);
        applyCurrentColor();
    }

    private void updateHue(double mouseY) {
        float svY = pickerY + PICKER_TITLE_HEIGHT;
        float ratio = (float) ((mouseY - svY) / SV_SIZE);
        hue = MathUtils.clamp(ratio, 0f, 1f);
        applyCurrentColor();
    }

    private void updateOpacity(double mouseX) {
        float sliderX = getOpacitySliderX();
        float sliderW = getOpacitySliderWidth();
        float ratio = (float) ((mouseX - sliderX) / sliderW);
        opacity = MathUtils.clamp(ratio, 0f, 1f);
        applyCurrentColor();
    }

    public boolean isEditing() {
        return editing && !closing;
    }

    @Override
    public void onClose() {
        stopDragging();
        hexFocused = false;
        hexSelected = false;
    }

    @Override
    public boolean hasActiveOverlay() {
        return editing;
    }

    @Override
    public boolean isExtendedHovered(double mouseX, double mouseY) {
        return editing && isInsideExtended(mouseX, mouseY);
    }

    private void drawSvSquare(DrawContext context, float x, float y, float size) {
        Color hueColor = Color.getHSBColor(hue, 1f, 1f);
        float radius = Math.min(6f, size * 0.5f);
        Render2D.drawRoundedHorizontalGradient(context.getMatrices(), x, y, size, size, radius, Color.WHITE, hueColor);
        Render2D.drawRoundedVerticalGradient(context.getMatrices(), x, y, size, size, radius, new Color(0, 0, 0, 0), new Color(0, 0, 0, 255));

        float markerX = x + saturation * size;
        float markerY = y + (1f - value) * size;
        float handleSize = 8.0f;
        float handleHalf = handleSize / 2f;
        markerX = MathUtils.clamp(markerX, x + handleHalf, x + size - handleHalf);
        markerY = MathUtils.clamp(markerY, y + handleHalf, y + size - handleHalf);
        markerX = Math.round(markerX);
        markerY = Math.round(markerY);

        float handleRadius = handleSize * 0.5f;
        // Тёмный ореол под белым кольцом — ручка видна на любом фоне и теме (на белом углу SV/светлой теме).
        Render2D.drawRoundedRect(
                context.getMatrices(),
                markerX - handleSize / 2f - 1f,
                markerY - handleSize / 2f - 1f,
                handleSize + 2f,
                handleSize + 2f,
                handleRadius + 1f,
                new Color(0, 0, 0, 150)
        );
        Render2D.drawRoundedRect(
                context.getMatrices(),
                markerX - handleSize / 2f,
                markerY - handleSize / 2f,
                handleSize,
                handleSize,
                handleRadius,
                new Color(255, 255, 255, 225)
        );
        Color rgb = currentRgb();
        float innerSize = 6.2f;
        float innerRadius = innerSize * 0.5f;
        Render2D.drawRoundedRect(
                context.getMatrices(),
                markerX - innerSize / 2f,
                markerY - innerSize / 2f,
                innerSize,
                innerSize,
                innerRadius,
                new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255)
        );
    }

    private void drawHueBar(DrawContext context, float x, float y, float width, float height) {
        float radius = Math.min(6f, Math.min(width, height) * 0.5f);
        Render2D.drawTexture(context.getMatrices(), x, y, width, height, radius, HUE_TEXTURE, Color.WHITE);

        float markerY = y + hue * height;
        markerY = MathUtils.clamp(markerY, y + 2f, y + height - 2f);
        float barW = Math.max(1f, width - 4.0f);
        float barH = 1.6f;
        float barX = x + 2.0f;
        float barY = markerY - barH / 2f;
        Render2D.drawRoundedRect(context.getMatrices(), barX - 0.6f, barY - 0.9f, barW + 1.2f, barH + 1.8f, (barH + 1.8f) * 0.5f, new Color(0, 0, 0, 140));
        Render2D.drawRoundedRect(context.getMatrices(), barX, barY, barW, barH, barH * 0.5f, new Color(255, 255, 255, 235));
    }

    private void drawOpacitySlider(DrawContext context, float x, float y, float width, float height) {
        float wPx = Math.max(1f, Math.round(width));
        float hPx = Math.max(1f, Math.round(height));
        float xPx = Math.round(x);
        float yPx = Math.round(y);

        float radius = Math.min(6f, Math.min(wPx, hPx) * 0.5f);
        float innerW = wPx;
        float innerH = hPx;

        float checkerX = xPx;
        float checkerY = yPx;
        float checkerW = innerW;
        float checkerH = innerH;
        float checkerRadius = radius;
        drawEmptyGrid(context, checkerX, checkerY, checkerW, checkerH, checkerRadius);

        Color rgb = currentRgb();
        Render2D.drawRoundedHorizontalGradient(
                context.getMatrices(),
                checkerX,
                checkerY,
                checkerW,
                checkerH,
                checkerRadius,
                new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 0),
                new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255)
        );

        float markerX = xPx + opacity * innerW;
        markerX = MathUtils.clamp(markerX, xPx + 2f, xPx + innerW - 2f);
        float markerY = yPx + innerH / 2f;

        float barW = 1.6f;
        float barH = Math.max(1f, innerH - 4.0f);
        float barX = MathUtils.clamp(markerX - barW / 2f, xPx + 2f, xPx + innerW - 2f - barW);
        float barY = yPx + (innerH - barH) / 2f;
        Render2D.drawRoundedRect(context.getMatrices(), barX - 0.9f, barY - 0.6f, barW + 1.8f, barH + 1.2f, (barW + 1.8f) * 0.5f, new Color(0, 0, 0, 140));
        Render2D.drawRoundedRect(context.getMatrices(), barX, barY, barW, barH, barW * 0.5f, new Color(255, 255, 255, 235));
    }

    private void requestClose() {
        if (!editing) return;
        closing = true;
        hexFocused = false;
        hexSelected = false;
        stopDragging();
        pickerAnimation.update(false);
    }

    private boolean hasActiveDrag() {
        return draggingPicker || draggingSv || draggingHue || draggingOpacity;
    }

    private void stopDragging() {
        draggingPicker = false;
        draggingSv = false;
        draggingHue = false;
        draggingOpacity = false;
    }

    private boolean isLeftMouseDown() {
        if (mc == null || mc.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void drawSmoothTexture(DrawContext context, Identifier textureId, float x, float y, float width, float height, float radius, Color color, boolean smooth) {
        if (context == null || textureId == null || width <= 0f || height <= 0f) return;

        float wPx = Math.max(1f, Math.round(width));
        float hPx = Math.max(1f, Math.round(height));
        float xPx = Math.round(x);
        float yPx = Math.round(y);

        var texture = mc.getTextureManager().getTexture(textureId);
        Render2D.drawTexture(context.getMatrices(), xPx, yPx, wPx, hPx, radius, texture, color);
    }

    private void drawEmptyGrid(DrawContext context, float x, float y, float width, float height, float radius) {
        if (width <= 0f || height <= 0f) return;
        drawSmoothTexture(context, EMPTY_TEXTURE, x, y, width, height, radius, Color.WHITE, true);
    }

    private float getOpacitySliderWidth() {
        float contentWidth = getContentWidth();
        float maxW = Math.max(1f, contentWidth - OPACITY_PREVIEW_WIDTH - OPACITY_PREVIEW_GAP);
        float scale = OPACITY_SLIDER_HEIGHT / (float) EMPTY_TEXTURE_HEIGHT;
        float w = Math.round(EMPTY_TEXTURE_WIDTH * scale);
        return Math.min(maxW, Math.max(1f, w));
    }

    private float getOpacitySliderX() {
        float sliderW = getOpacitySliderWidth();
        float svX = pickerX + PICKER_PADDING;
        return svX + OPACITY_PREVIEW_WIDTH + OPACITY_PREVIEW_GAP;
    }

    private void drawOpacityPreview(DrawContext context, float x, float y, float width, float height) {
        float radius = Math.min(6f, Math.min(width, height) * 0.5f);
        Render2D.drawRoundedRect(context.getMatrices(), x, y, width, height, radius, setting.getValue());
    }

    private void updateHexFieldBounds() {
        float svX = pickerX + PICKER_PADDING;
        float svY = pickerY + PICKER_TITLE_HEIGHT;
        float opacityLabelY = svY + SV_SIZE + OPACITY_SECTION_GAP;
        float opacitySliderY = opacityLabelY + OPACITY_LABEL_GAP;
        float hexLabelY = opacitySliderY + OPACITY_SLIDER_HEIGHT + HEX_SECTION_GAP;
        float hexFieldY = hexLabelY + HEX_LABEL_GAP;

        hexFieldX = svX;
        this.hexFieldY = hexFieldY;
        hexFieldW = getContentWidth();
        hexFieldH = HEX_FIELD_HEIGHT;
    }

    private boolean isInsideHexField(double mouseX, double mouseY) {
        return mouseX >= hexFieldX && mouseX <= hexFieldX + hexFieldW
                && mouseY >= hexFieldY && mouseY <= hexFieldY + hexFieldH;
    }

    private void focusHexInput() {
        hexFocused = true;
        hexSelected = true;
        if (hexBuffer == null || hexBuffer.isEmpty()) {
            hexBuffer = formatHex(setting.getValue());
        }
    }

    private void syncHexBuffer() {
        if (!hexFocused) {
            hexBuffer = formatHex(setting.getValue());
        }
    }

    private void applyHexBuffer() {
        Color parsed = parseHex(hexBuffer);
        if (parsed != null) {
            setColorFromHex(parsed);
        }
    }

    private String normalizeHexInput(String input) {
        if (input == null) return "";
        String cleaned = input.trim().toUpperCase(Locale.ROOT);
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);

        StringBuilder builder = new StringBuilder();
        int maxDigits = HEX_MAX_LENGTH - 1;
        for (int i = 0; i < cleaned.length() && builder.length() < maxDigits; i++) {
            char chr = cleaned.charAt(i);
            if (isHexChar(chr)) {
                builder.append(chr);
            }
        }

        if (builder.length() == 0) {
            return "";
        }
        return "#" + builder;
    }

    private String getHexDisplayText() {
        if (hexBuffer == null || hexBuffer.isEmpty()) {
            return "#";
        }
        return hexBuffer;
    }

    private boolean isHexChar(char chr) {
        return (chr >= '0' && chr <= '9')
                || (chr >= 'a' && chr <= 'f')
                || (chr >= 'A' && chr <= 'F');
    }

    private Color parseHex(String input) {
        if (input == null) return null;
        String cleaned = input.trim();
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
        if (cleaned.length() != 6 && cleaned.length() != 8) return null;
        for (int i = 0; i < cleaned.length(); i++) {
            if (!isHexChar(cleaned.charAt(i))) return null;
        }
        try {
            int r = Integer.parseInt(cleaned.substring(0, 2), 16);
            int g = Integer.parseInt(cleaned.substring(2, 4), 16);
            int b = Integer.parseInt(cleaned.substring(4, 6), 16);
            int a = cleaned.length() == 8 ? Integer.parseInt(cleaned.substring(6, 8), 16) : 255;
            return new Color(r, g, b, a);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatHex(Color color) {
        if (color == null) {
            return "#FFFFFF";
        }
        String rgb = String.format(Locale.ROOT, "%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        if (color.getAlpha() < 255) {
            return "#" + rgb + String.format(Locale.ROOT, "%02X", color.getAlpha());
        }
        return "#" + rgb;
    }

    private void setColorFromHex(Color color) {
        if (color == null) return;
        setting.setValue(color);
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        value = hsb[2];
        opacity = color.getAlpha() / 255.0f;
        hsvInitialized = true;
    }

    private Color currentRgb() {
        int rgb = Color.HSBtoRGB(hue, saturation, value);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private int clampInt(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }


    private static boolean ctrlDown() {
        var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
