package ru.method.linkenvisuals.screen.clickgui.components;

import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class SearchBar implements Wrapper {

    @Setter private float x, y, width, height;
    @Getter private String searchText = "";
    private boolean focused = false;
    private boolean selected = false;
    private long startTime = 0;
    // Плавная анимация фокуса: акцентная обводка всплывает/гаснет (раньше поиск был без анимации фокуса).
    private final ru.method.linkenvisuals.utils.animations.Animation focusAnim =
            new ru.method.linkenvisuals.utils.animations.Animation(
                    160, 1f, false, ru.method.linkenvisuals.utils.animations.Easing.EASE_OUT_CUBIC);

    public SearchBar(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Плоский фон вместо liquid-glass шейдера: лёгкая полупрозрачная подложка (как у текстовых полей),
        // акцентная обводка при фокусе — ниже.
        Render2D.drawRoundedRect(context.getMatrices(), x, y, width, height,
                Math.min(14f, height / 2f), new Color(255, 255, 255, 18));

        // Плавная акцентная обводка при фокусе (всплывает/гаснет по focusAnim).
        focusAnim.update(focused);
        float focusT = focusAnim.getValue();
        if (focusT > 0.01f) {
            Color accent = ColorUtils.getPrimaryColor();
            Render2D.drawBorder(context.getMatrices(), x, y, width, height, 14f, 1.2f,
                    new ColorRGBA(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                            Math.max(0, Math.min(255, Math.round(180f * focusT))))));
        }

        String displayText = searchText.isEmpty() ? I18n.translate("clickgui.search.placeholder") : searchText;
        // Цвет текста плавно переходит от плейсхолдера к обычному по фокусу (вместо резкого).
        Color textColor = ColorUtils.getTextColor(255);
        Color placeholderColor = new Color(150, 150, 160);
        Color finalTextColor = searchText.isEmpty()
                ? lerpColor(placeholderColor, textColor, focusT * 0.5f)
                : textColor;

        float textWidth = Fonts.MEDIUM.getWidth(displayText, 8f);
        float textX = x + (width - textWidth) / 2f;
        float textY = y + (height / 2f) - (Fonts.MEDIUM.getHeight(8f) / 2f);

        Render2D.startScissor(context, x + 5, y, width - 10, height);

        if (selected && !searchText.isEmpty()) {
            Render2D.drawRoundedRect(context.getMatrices(),
                    textX,
                    textY,
                    textWidth,
                    Fonts.MEDIUM.getHeight(8f),
                    0f,
                    new Color(0, 120, 215, 150));
        }

        if (!displayText.trim().isEmpty()) {
            Render2D.drawFont(context.getMatrices(),
                    Fonts.MEDIUM.getFont(8f),
                    displayText,
                    textX,
                    textY,
                    finalTextColor);
        }

        if (focused && !selected) {
            if (startTime == 0) {
                startTime = System.currentTimeMillis();
            }

            float cursorX;
            if (searchText.isEmpty()) {
                cursorX = textX;
            } else {
                float actualTextWidth = Fonts.MEDIUM.getWidth(searchText, 8f);
                cursorX = x + (width - actualTextWidth) / 2f + actualTextWidth;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            double cycle = (elapsed % 1000) / 1000.0;
            float cursorAlpha = (float)(0.5 + 0.5 * Math.sin(cycle * Math.PI * 2));

            int alpha = (int)(255 * cursorAlpha);
            Render2D.drawFont(context.getMatrices(),
                    Fonts.MEDIUM.getFont(8f),
                    "|",
                    cursorX,
                    textY,
                    ColorUtils.getTextColor(alpha));
        } else if (!focused) {
            startTime = 0;
        }

        Render2D.stopScissor(context);
    }

    private static Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean wasClicked = MathUtils.isHovered(x - 4f, y - 4f, width + 8f, height + 8f, (float)mouseX, (float)mouseY);
            focused = wasClicked;
            selected = false;
            if (focused) {
                startTime = System.currentTimeMillis();
            }
            return wasClicked;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                if (selected) {
                    searchText = "";
                    selected = false;
                } else if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                }
                if (searchText.isEmpty()) {
                    startTime = System.currentTimeMillis();
                }
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                focused = false;
                selected = false;
                if (!searchText.isEmpty()) {
                    searchText = "";
                }
                startTime = 0;
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (hasControlDown(modifiers)) {
                    String clipboard = GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                    if (clipboard != null) {
                        if (selected) {
                            searchText = clipboard;
                            selected = false;
                        } else {
                            searchText += clipboard;
                        }
                    }
                    return true;
                }
            }
            case GLFW.GLFW_KEY_A -> {
                if (hasControlDown(modifiers) && !searchText.isEmpty()) {
                    selected = true;
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (hasControlDown(modifiers) && selected && !searchText.isEmpty()) {
                    GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), searchText);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (hasControlDown(modifiers) && selected && !searchText.isEmpty()) {
                    GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), searchText);
                    searchText = "";
                    selected = false;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasControlDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                || (mc.getWindow() != null && (
                    net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                    || net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)
                ));
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        if (Character.isISOControl(chr)) return false;

        if (selected) {
            searchText = String.valueOf(chr);
            selected = false;
        } else {
            searchText += chr;
        }
        playTypeSound();
        return true;
    }

    // Тихий «печатный» клик при вводе символа (лёгкая вариация тона — эффект печатной машинки).
    private void playTypeSound() {
        try {
            if (mc.getSoundManager() == null) return;
            float pitch = 1.7f + (float) (Math.random() * 0.35);
            mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
        } catch (Throwable ignored) {
        }
    }

    public boolean isEmpty() {
        return searchText.trim().isEmpty();
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        this.selected = false;
        if (focused) {
            startTime = System.currentTimeMillis();
        }
    }

    public boolean isFocused() {
        return focused;
    }

    public void clear() {
        searchText = "";
        focused = false;
        selected = false;
        startTime = 0;
    }

    public void reset() {
        searchText = "";
        focused = false;
        selected = false;
        startTime = 0;
    }

    /** Восстанавливает сохранённый текст поиска (например, между закрытиями GUI). */
    public void setSearchText(String text) {
        this.searchText = text == null ? "" : text;
        this.selected = false;
    }
}
