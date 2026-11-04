package ru.method.linkenvisuals.screen.cmddelay;

import net.minecraft.client.input.CharInput;

import net.minecraft.client.input.KeyInput;

import net.minecraft.client.gui.Click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.modules.impl.misc.CMDDelay;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;

public class CMDDelayScreen extends Screen implements Wrapper {
    private static final int MIN_DELAY_SECONDS = 1;
    private static final int MAX_COMMAND_LENGTH = 70;
    private static final int MAX_DELAY_LENGTH = 30;

    private final CMDDelay module;
    private final Screen parent;
    private final Animation openAnimation = new Animation(420, 1f, true, Easing.EASE_OUT_CUBIC);
    private final StyledInput commandInput = new StyledInput("Команда", "/spawn", MAX_COMMAND_LENGTH);
    private final StyledInput delayInput = new StyledInput("Задержка в секундах", "60", MAX_DELAY_LENGTH);
    private int selectedIndex = -1;
    private float scroll;

    public CMDDelayScreen(CMDDelay module, Screen parent) {
        super(Text.literal("CMDDelay"));
        this.module = module;
        this.parent = parent;
    }

    @Override
    protected void init() {
        loadSelected();
        commandInput.focus(); // сразу готов к вводу новой команды (курсор в поле)
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ru.method.linkenvisuals.utils.render.Render2D.prepareHudFrame(context);
        Layout layout = getLayout();
        openAnimation.update(true);
        float open = MathHelper.clamp(openAnimation.getValue(), 0f, 1f);
        float contentOpen = MathHelper.clamp((open - 0.18f) / 0.82f, 0f, 1f);
        int openAlpha = Math.round(255f * open);
        int contentAlpha = Math.round(255f * contentOpen);
        float scale = MathHelper.lerp(open, 0.86f, 1.0f);
        float centerX = layout.x + layout.w / 2f;
        float centerY = layout.y + layout.h / 2f;

        Render2D.drawRoundedRect(context.getMatrices(), 0, 0, layout.screenW, layout.screenH, 0f, new Color(0, 0, 0, Math.round(52f * open)));

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX, centerY + (1f - open) * 24f);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-centerX, -centerY);

        Render2D.drawBlurredRect(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f, 22f, new Color(15, 18, 24, Math.round(150f * open)));
        Color webAccent = ColorUtils.getPrimaryColor();
        Render2D.drawPanelBackground(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f,
                new Color(webAccent.getRed(), webAccent.getGreen(), webAccent.getBlue(), openAlpha));
        Render2D.drawRoundedRect(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f,
                new Color(12, 14, 20, Math.round(166f * open)));
        Render2D.drawBorder(context.getMatrices(), layout.x, layout.y, layout.w, layout.h, 18f, 0.75f, new ColorRGBA(ColorUtils.alpha(Color.WHITE, Math.round(38f * open))));

        float contentOffset = (1f - contentOpen) * 10f;
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(16f), "Command Delay", layout.x + 18f, layout.y + 13f + contentOffset, lightText(contentAlpha));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(7.4f), "Автоматический ввод указанных команд с периодичной цикличностью.", layout.x + 18f, layout.y + 34f + contentOffset, lightText(Math.round(170f * contentOpen)));

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0f, contentOffset * 0.65f);
        drawList(context, layout, mouseX, mouseY);
        drawEditor(context, layout, mouseX, mouseY);
        context.getMatrices().popMatrix();
        context.getMatrices().popMatrix();
    }

    private void drawList(DrawContext context, Layout layout, int mouseX, int mouseY) {
        int x = layout.listX;
        int y = layout.listY;
        int w = layout.listW;
        int h = layout.listH;

        Render2D.drawRoundedRect(context.getMatrices(), x, y, w, h, 12f, new Color(255, 255, 255, 18));
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(8.7f), "Команды", x + 10f, y + 9f, lightText(235));

        if (module.getEntries().isEmpty()) {
            Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(7.2f), "Список пуст", x + 10f, y + 32f, lightText(135));
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(6.8f), "Добавь команду справа", x + 10f, y + 44f, lightText(105));
            return;
        }

        int top = y + 28;
        int viewportH = h - 34;
        int itemH = 31;
        int maxScroll = Math.max(0, module.getEntries().size() * itemH - viewportH);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        Render2D.startScissor(context, x, top, w, viewportH);
        int currentY = top - Math.round(scroll);
        for (int i = 0; i < module.getEntries().size(); i++) {
            CMDDelay.CommandEntry entry = module.getEntries().get(i);
            boolean selected = i == selectedIndex;
            boolean hovered = MathUtils.isHovered(x + 6, currentY, w - 12, itemH - 5, mouseX, mouseY);
            Color bg = selected ? ColorUtils.alpha(ColorUtils.getPrimaryColor(), 96) : hovered ? new Color(255, 255, 255, 32) : new Color(255, 255, 255, 12);

            Render2D.drawRoundedRect(context.getMatrices(), x + 6, currentY, w - 12, itemH - 5, 8f, bg);
            Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(7.5f), clip("/" + entry.getCommand(), w - 28, 7.5f), x + 12f, currentY + 4f, lightText(235));
            Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(6.6f), entry.getDelaySeconds() + " сек.", x + 12f, currentY + 15f, lightText(150));
            currentY += itemH;
        }
        Render2D.stopScissor(context);
    }

    private void drawEditor(DrawContext context, Layout layout, int mouseX, int mouseY) {
        int x = layout.editorX;
        int y = layout.listY;
        int w = layout.editorW;
        int h = layout.listH;
        String title = selectedIndex >= 0 ? "Редактирование" : "Новая команда";

        Render2D.drawRoundedRect(context.getMatrices(), x, y, w, h, 12f, new Color(255, 255, 255, 16));
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(9.4f), title, x + 10f, y + 10f, lightText(238));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(6.8f), "Автоматический ввод указанных команд с периодичной цикличностью.", x + 10f, y + 37f, lightText(116));

        commandInput.render(context, x + 10, y + 58, Math.max(120, w - 20), mouseX, mouseY);
        delayInput.render(context, x + 10, y + 112, Math.min(130, Math.max(90, w - 20)), mouseX, mouseY);

        int buttonsY = y + h - 38;
        int saveW = Math.min(92, Math.max(76, (w - 38) / 3));
        int clearW = Math.min(78, Math.max(62, (w - 38) / 3));
        int deleteW = Math.min(78, Math.max(62, (w - 38) / 3));

        drawButton(context, x + 10, buttonsY, saveW, 23, selectedIndex >= 0 ? "Сохранить" : "Добавить", mouseX, mouseY, ColorUtils.getPrimaryColor());
        drawButton(context, x + 18 + saveW, buttonsY, clearW, 23, "Новая", mouseX, mouseY, new Color(91, 126, 188));
        if (selectedIndex >= 0) {
            drawButton(context, x + 26 + saveW + clearW, buttonsY, deleteW, 23, "Удалить", mouseX, mouseY, new Color(225, 72, 72));
        }
    }

    private void drawButton(DrawContext context, int x, int y, int w, int h, String label, int mouseX, int mouseY, Color color) {
        boolean hovered = MathUtils.isHovered(x, y, w, h, mouseX, mouseY);
        Render2D.drawRoundedRect(context.getMatrices(), x, y, w, h, 8f, ColorUtils.alpha(color, hovered ? 142 : 98));
        Render2D.drawBorder(context.getMatrices(), x, y, w, h, 8f, 0.55f, new ColorRGBA(ColorUtils.alpha(Color.WHITE, hovered ? 36 : 20)));
        float textW = Fonts.ROUNDBOLD.getWidth(label, 7.25f);
        Render2D.drawFont(context.getMatrices(), Fonts.ROUNDBOLD.getFont(7.25f), label, x + (w - textW) / 2f, y + 6.8f, lightText(248));
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == 0) {
            Layout layout = getLayout();
            if (commandInput.mouseClicked(mouseX, mouseY) || delayInput.mouseClicked(mouseX, mouseY)) {
                return true;
            }

            int itemH = 31;
            int currentY = layout.listY + 28 - Math.round(scroll);
            for (int i = 0; i < module.getEntries().size(); i++) {
                if (MathUtils.isHovered(layout.listX + 6, currentY, layout.listW - 12, itemH - 5, (float) mouseX, (float) mouseY)) {
                    selectedIndex = i;
                    loadSelected();
                    return true;
                }
                currentY += itemH;
            }

            int buttonsY = layout.listY + layout.listH - 38;
            int saveW = Math.min(92, Math.max(76, (layout.editorW - 38) / 3));
            int clearW = Math.min(78, Math.max(62, (layout.editorW - 38) / 3));
            int deleteW = Math.min(78, Math.max(62, (layout.editorW - 38) / 3));

            if (MathUtils.isHovered(layout.editorX + 10, buttonsY, saveW, 23, (float) mouseX, (float) mouseY)) {
                saveCurrent();
                return true;
            }
            if (MathUtils.isHovered(layout.editorX + 18 + saveW, buttonsY, clearW, 23, (float) mouseX, (float) mouseY)) {
                newCommand();
                return true;
            }
            if (selectedIndex >= 0 && MathUtils.isHovered(layout.editorX + 26 + saveW + clearW, buttonsY, deleteW, 23, (float) mouseX, (float) mouseY)) {
                module.removeEntry(selectedIndex);
                clearSelection();
                return true;
            }
        }
        commandInput.blur();
        delayInput.blur();
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = getLayout();
        if (MathUtils.isHovered(layout.listX, layout.listY, layout.listW, layout.listH, (float) mouseX, (float) mouseY)) {
            scroll -= (float) verticalAmount * 14f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        // Enter в любом из полей — сразу добавить/сохранить команду (создание одним нажатием).
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (commandInput.isFocused() || delayInput.isFocused())) {
            saveCurrent();
            return true;
        }
        if (commandInput.keyPressed(keyCode) || delayInput.keyPressed(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (commandInput.charTyped(chr) || delayInput.charTyped(chr)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private void loadSelected() {
        if (selectedIndex < 0 || selectedIndex >= module.getEntries().size()) {
            clearSelection();
            return;
        }
        CMDDelay.CommandEntry entry = module.getEntries().get(selectedIndex);
        commandInput.setText("/" + entry.getCommand());
        delayInput.setText(String.valueOf(entry.getDelaySeconds()));
    }

    private void clearSelection() {
        selectedIndex = -1;
        commandInput.setText("");
        delayInput.setText("60");
        commandInput.focus(); // чистая форма + курсор в поле команды (видимая реакция)
    }

    /** Кнопка «Новая»: если в форме введена команда — создаёт её (как «Добавить»); иначе — чистая форма.
     *  Команда выполняется НЕ сразу, а через указанную задержку (цикличный таймер в модуле). */
    private void newCommand() {
        if (selectedIndex < 0 && !commandInput.getText().trim().isEmpty()) {
            saveCurrent(); // addEntry + сброс формы (внутри clearSelection → фокус на новую команду)
        } else {
            clearSelection();
        }
    }

    private void saveCurrent() {
        String command = commandInput.getText();
        int delay = parseDelay(delayInput.getText());
        if (command == null || command.trim().isEmpty()) return;

        if (selectedIndex >= 0 && selectedIndex < module.getEntries().size()) {
            module.updateEntry(selectedIndex, command, delay);
            loadSelected();
        } else {
            module.addEntry(command, delay);
            // После добавления — снова режим «Новая команда» с очищенным полем, чтобы можно было
            // сразу добавлять следующую (раньше кнопка превращалась в «Сохранить» и обновляла ту же запись).
            clearSelection();
        }
    }

    private int parseDelay(String raw) {
        try {
            String digits = raw == null ? "" : raw.trim().replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 60;
            long parsed = Long.parseUnsignedLong(digits.length() > 18 ? digits.substring(0, 18) : digits);
            return (int) Math.min(Integer.MAX_VALUE, Math.max(MIN_DELAY_SECONDS, parsed));
        } catch (Exception ignored) {
            return 60;
        }
    }

    private String clip(String text, float maxWidth, float size) {
        if (Fonts.SEMIBOLD.getWidth(text, size) <= maxWidth) return text;
        String suffix = "...";
        String clipped = text;
        while (!clipped.isEmpty() && Fonts.SEMIBOLD.getWidth(clipped + suffix, size) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + suffix;
    }

    /** Фиксированный светлый текст: панель CMDDelay всегда тёмная, тема-зависимый цвет на ней сливался. */
    private static Color lightText(int alpha) {
        return new Color(236, 239, 246, Math.max(0, Math.min(255, alpha)));
    }

    private Layout getLayout() {
        int screenW = this.width;
        int screenH = this.height;
        if ((screenW <= 1 || screenH <= 1) && mc != null && mc.getWindow() != null) {
            screenW = mc.getWindow().getScaledWidth();
            screenH = mc.getWindow().getScaledHeight();
        }
        screenW = Math.max(1, screenW);
        screenH = Math.max(1, screenH);
        int safeW = Math.max(120, screenW - 16);
        int safeH = Math.max(120, screenH - 16);
        int panelW = Math.min(650, Math.max(120, Math.min(safeW, Math.round(screenW * 0.88f))));
        int panelH = Math.min(405, Math.max(120, Math.min(safeH, Math.round(screenH * 0.82f))));
        int x = Math.round((screenW - panelW) * 0.5f);
        int y = Math.round((screenH - panelH) * 0.5f);
        if (screenW - panelW >= 16) {
            x = MathHelper.clamp(x, 8, screenW - panelW - 8);
        }
        if (screenH - panelH >= 16) {
            y = MathHelper.clamp(y, 8, screenH - panelH - 8);
        }
        int headerH = panelH < 210 ? 42 : 56;
        int contentY = y + headerH;
        int contentH = Math.max(80, panelH - headerH - 16);
        int listW = Math.min(230, Math.max(78, (int) (panelW * 0.34f)));
        int editorX = x + 16 + listW + 12;
        int editorW = Math.max(70, panelW - (editorX - x) - 16);
        return new Layout(screenW, screenH, x, y, panelW, panelH, x + 16, contentY, listW, contentH, editorX, editorW);
    }

    private record Layout(int screenW, int screenH, int x, int y, int w, int h, int listX, int listY, int listW, int listH, int editorX, int editorW) {}

    private static class StyledInput implements Wrapper {
        private static final float LABEL_FONT_SIZE = 6.2f;
        private static final float FIELD_HEIGHT = 14f;
        private static final float FIELD_RADIUS = 2.5f;

        private final String label;
        private final String placeholder;
        private final int maxLength;
        private final Animation animation = new Animation(180, 1f, false, Easing.BOTH_SINE);
        private float x;
        private float y;
        private float width;
        private float scrollOffset;
        private String text = "";
        private boolean focused;
        private boolean selected;

        private StyledInput(String label, String placeholder, int maxLength) {
            this.label = label;
            this.placeholder = placeholder;
            this.maxLength = maxLength;
        }

        private void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY) {
            this.x = x;
            this.y = y;
            this.width = width;
            animation.update(focused);

            float fieldY = y + LABEL_FONT_SIZE + 4f;
            float maxTextWidth = Math.max(1f, width - 8f);
            String safeText = text == null ? "" : text;
            boolean hasValue = !safeText.isEmpty();
            boolean showPlaceholder = !hasValue && !focused;
            String visibleText = showPlaceholder ? placeholder : safeText;
            float textWidth = Fonts.REGULAR.getWidth(visibleText, 7.5f);
            float valueWidth = hasValue ? Fonts.REGULAR.getWidth(safeText, 7.5f) : 0f;

            if (hasValue && valueWidth > maxTextWidth) {
                scrollOffset = Math.max(0f, valueWidth - maxTextWidth);
            } else {
                scrollOffset = 0f;
            }

            Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(LABEL_FONT_SIZE), label, x, y, new Color(230, 230, 230, 235));
            Render2D.drawRoundedRect(context.getMatrices(), x, fieldY, width, FIELD_HEIGHT, FIELD_RADIUS, new Color(23, 23, 23, 100));
            Render2D.drawRoundedRect(context.getMatrices(), x, fieldY, width, FIELD_HEIGHT, FIELD_RADIUS, ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), focused ? 200 : 130));

            float textX = x + 4f;
            float textY = fieldY + 3.1f;
            Render2D.startScissor(context, x + 1f, fieldY, width - 2f, FIELD_HEIGHT);

            if (selected && hasValue) {
                Render2D.drawRoundedRect(context.getMatrices(), textX, textY, Math.max(1f, valueWidth), Math.max(1f, Fonts.REGULAR.getHeight(7.5f)), 0f, new Color(0, 0, 255, 150));
            }

            if (showPlaceholder) {
                Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(7.5f), placeholder, textX, textY, lightText((int) (200 * animation.getReversedValue())));
            } else if (hasValue) {
                Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(7.5f), safeText, textX - scrollOffset, textY, lightText(255));
            }

            if (focused) {
                Color pulseColor = ColorUtils.pulse(lightText(255), 15);
                float cursorX = textX - scrollOffset + (hasValue ? valueWidth : textWidth);
                Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(7.5f), "|", cursorX, textY - 0.25f, ColorUtils.alpha(pulseColor, (int) (pulseColor.getAlpha() * animation.getValue())));
            }

            Render2D.stopScissor(context);
        }

        private boolean mouseClicked(double mouseX, double mouseY) {
            boolean nextFocused = MathUtils.isHovered(x, y + LABEL_FONT_SIZE + 4f, width, FIELD_HEIGHT, (float) mouseX, (float) mouseY);
            focused = nextFocused;
            selected = false;
            return nextFocused;
        }

        private boolean keyPressed(int keyCode) {
            if (!focused) return false;
            boolean handled = true;
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (selected) {
                        text = "";
                        selected = false;
                    } else if (!text.isEmpty()) {
                        text = text.substring(0, text.length() - 1);
                    }
                }
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> {
                    blur();
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    text = "";
                    selected = false;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (ctrlDown() && selected && !text.isEmpty()) {
                        GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), text);
                        selected = false;
                    }
                }
                case GLFW.GLFW_KEY_V -> {
                    String clip = GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                    if (ctrlDown() && clip != null) {
                        if (selected) text = "";
                        text = (text + clip);
                        if (text.length() > maxLength) text = text.substring(0, maxLength);
                        selected = false;
                    }
                }
                case GLFW.GLFW_KEY_A -> {
                    if (ctrlDown() && !text.isEmpty()) selected = true;
                }
                default -> handled = false;
            }
            return handled;
        }

        private boolean charTyped(char chr) {
            if (!focused) return false;
            if (selected) {
                text = "";
                selected = false;
            }
            if (text.length() >= maxLength) return true;
            text += chr;
            return true;
        }

        private void blur() {
            focused = false;
            selected = false;
        }

        private void focus() {
            focused = true;
            selected = false;
        }

        private boolean isFocused() {
            return focused;
        }

        private String getText() {
            return text == null ? "" : text;
        }

        private void setText(String text) {
            this.text = text == null ? "" : text;
            if (this.text.length() > maxLength) {
                this.text = this.text.substring(0, maxLength);
            }
            selected = false;
            scrollOffset = 0f;
        }
    }

    private static boolean ctrlDown() {
        var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        return net.minecraft.client.util.InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                || net.minecraft.client.util.InputUtil.isKeyPressed(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
