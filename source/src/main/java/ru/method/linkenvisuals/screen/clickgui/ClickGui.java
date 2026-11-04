package ru.method.linkenvisuals.screen.clickgui;

import lombok.Getter;
import lombok.Setter;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.screen.clickgui.components.SearchBar;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.AnimatedString;
import ru.method.linkenvisuals.utils.animations.AnimatedTextHelper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.ClickGuiRenderContext;
import ru.method.linkenvisuals.utils.render.LiquidGlassReflectionCache;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Font;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ClickGui extends Screen implements Wrapper {
    private final List<Panel> panels = new ArrayList<>();
    private static final long OPEN_ANIMATION_DURATION = 450L;
    private static final long CLOSE_FADE_DURATION = 220L;
    private static final float MIN_VISIBLE_FADE = 0.02f;

    private final Animation openAnimation = new Animation(OPEN_ANIMATION_DURATION, 1f, true, Easing.EASE_OUT_EXPO);
    private final Animation closeFadeAnimation = new Animation(CLOSE_FADE_DURATION, 1f, true, Easing.BOTH_SINE);
    private final Animation hoverAnimation = new Animation(90, 1f, true, Easing.LINEAR);
    private final AnimatedString descriptionText = new AnimatedString(220, Easing.EASE_OUT_CUBIC, "");

    @Getter
    private SearchBar searchBar;
    @Setter
    private String description;

    private boolean close = false;
    private float closeRenderScale = 1f;

    @Getter
    private float guiLeft;
    @Getter
    private float guiRight;

    private final float panelWidth = ClickGuiLayout.PANEL_WIDTH;
    private final float panelGap = ClickGuiLayout.PANEL_GAP;
    private final float panelsYShift = ClickGuiLayout.PANELS_Y_SHIFT;

    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;

    public ClickGui() {
        super(Text.of("linkenvisuals-clickgui"));
        for (Category category : LinkenVisuals.getInstance().getModuleManager().getCategories()) {
            if (category == Category.Hud) continue;
            panels.add(new Panel(-999, -999, panelWidth, 20, category));
        }
        searchBar = new SearchBar(0, 0, ClickGuiLayout.SEARCH_WIDTH, ClickGuiLayout.SEARCH_HEIGHT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // ClickGui draws over the world; skip vanilla dimmed background pass.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc == null || mc.currentScreen != this) {
            return;
        }

        boolean finishAfterRender = false;
        if (close) {
            closeFadeAnimation.update(false);
            if (closeFadeAnimation.getValue() <= MIN_VISIBLE_FADE) {
                // Раньше здесь был ранний return ДО отрисовки панелей: экран рвался (setScreen(null)),
                // но панели этот кадр не рисовались. В мире (GameRendererMixin) проход шёл с ещё живым
                // currentScreen == ClickGui → мод-геометрия (ESP/косметика/партиклы) была подавлена, а
                // FogBlur (после переноса — рисуется ВСЕГДА) размывал мир. Итог: ровно один кадр без панелей
                // И без мировых оверлеев — «пустой размытый мир», после чего оверлеи резко всплывали (артефакт).
                // Теперь рисуем панели последний раз на остаточном фейде и рвём экран ПОСЛЕ отрисовки —
                // тогда исчезновение панелей совпадает с возвратом мировых оверлеев (кадр N: панели, без
                // оверлеев → кадр N+1: без панелей, оверлеи), без пустого промежуточного кадра.
                finishAfterRender = true;
            }
        } else {
            openAnimation.update(true);
        }

        float fade = getUiFade();
        ClickGuiRenderContext.begin(fade, close);
        try {
            Render2D.prepareHudFrame(context);
            LiquidGlassReflectionCache.captureClickGuiBackdrop(true);

            description = null;
            float[] transformedMouse = transformMouse(mouseX, mouseY);
            int logicalMouseX = Math.round(transformedMouse[0]);
            int logicalMouseY = Math.round(transformedMouse[1]);
            float renderScale = getRenderScale();

            context.getMatrices().pushMatrix();
            float cx = mc.getWindow().getScaledWidth() / 2f;
            float cy = mc.getWindow().getScaledHeight() / 2f;
            context.getMatrices().translate(cx, cy);
            context.getMatrices().scale(renderScale, renderScale);
            context.getMatrices().translate(-cx, -cy);

            String searchText = searchBar.getSearchText();

            for (Panel panel : panels) {
                panel.render(context, logicalMouseX, logicalMouseY, delta, searchText);
            }

            searchBar.render(context, logicalMouseX, logicalMouseY, delta);

            for (Panel panel : panels) {
                panel.renderOverlays(context, logicalMouseX, logicalMouseY, delta);
            }

            List<SettingsWindow> windows = LinkenVisuals.getInstance().getSettingsWindows();
            windows.removeIf(SettingsWindow::isFinishedClosing);
            for (SettingsWindow window : windows) {
                window.render(context, logicalMouseX, logicalMouseY, delta);
            }

            descriptionText.update(description);

            hoverAnimation.update(!descriptionIsEmpty());
            if (!descriptionIsEmpty() || descriptionText.isAnimating()) {
                float fontSize = 9f;
                float progress = descriptionText.isAnimating() ? descriptionText.progress() : 1f;
                boolean animating = descriptionText.isAnimating();
                float currentWidth = Fonts.ROUNDBOLD.getWidth(descriptionText.getCurrent(), fontSize);
                float previousWidth = Fonts.ROUNDBOLD.getWidth(descriptionText.getPrevious(), fontSize);
                float textWidth = animating ? MathUtils.lerp(previousWidth, currentWidth, progress) : currentWidth;
                float textHeight = Fonts.ROUNDBOLD.getHeight(fontSize);
                float w = textWidth;
                float dx = mc.getWindow().getScaledWidth() / 2f - w / 2f;
                float dy = mc.getWindow().getScaledHeight() / 2f - ClickGuiLayout.DESCRIPTION_Y_SHIFT;
                Color tx = ColorUtils.getTextColor((int) (255 * hoverAnimation.getValue()));
                float shift = textHeight * 0.6f;
                drawAnimatedText(context, descriptionText, dx, dy, tx, shift, fontSize, Fonts.ROUNDBOLD, progress, animating);
            }

            context.getMatrices().popMatrix();
        } finally {
            ClickGuiRenderContext.end();
        }

        if (finishAfterRender) {
            // Панели этого кадра уже отрисованы на остаточном фейде — теперь безопасно завершаем закрытие
            // (setScreen(null)). Следующий кадр рисует мир с мод-оверлеями и без панелей — плавный переход.
            finishClosing();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (mouseClicked(click.x(), click.y(), click.button())) return true;
        return super.mouseClicked(click, doubleClick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (close || mc.currentScreen != this) return false;
        float[] transformedMouse = transformMouse(mouseX, mouseY);
        double logicalMouseX = transformedMouse[0];
        double logicalMouseY = transformedMouse[1];
        if (searchBar.mouseClicked(logicalMouseX, logicalMouseY, button)) return true;
        List<SettingsWindow> windows = LinkenVisuals.getInstance().getSettingsWindows();
        for (int i = windows.size() - 1; i >= 0; i--) {
            SettingsWindow window = windows.get(i);
            if (window.mouseClicked(logicalMouseX, logicalMouseY, button)) {
                if (windows.contains(window)) {
                    windows.remove(window);
                    windows.add(window);
                }
                return true;
            }
        }
        for (Panel panel : panels) {
            if (panel.handleExtendedClick(logicalMouseX, logicalMouseY, button)) {
                return true;
            }
        }
        for (Panel panel : panels) {
            panel.mouseClicked(logicalMouseX, logicalMouseY, button, searchBar.getSearchText());
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (mouseReleased(click.x(), click.y(), click.button())) return true;
        return super.mouseReleased(click);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (close || mc.currentScreen != this) return false;
        float[] transformedMouse = transformMouse(mouseX, mouseY);
        double logicalMouseX = transformedMouse[0];
        double logicalMouseY = transformedMouse[1];
        for (SettingsWindow window : LinkenVisuals.getInstance().getSettingsWindows()) {
            window.mouseReleased(logicalMouseX, logicalMouseY, button);
        }
        for (Panel panel : panels) {
            panel.mouseReleased(logicalMouseX, logicalMouseY, button, searchBar.getSearchText());
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (close || mc.currentScreen != this) return false;
        float[] transformedMouse = transformMouse(mouseX, mouseY);
        double logicalMouseX = transformedMouse[0];
        double logicalMouseY = transformedMouse[1];
        for (SettingsWindow window : LinkenVisuals.getInstance().getSettingsWindows()) {
            window.mouseScrolled(logicalMouseX, logicalMouseY, horizontalAmount, verticalAmount);
        }
        for (Panel panel : panels) {
            panel.mouseScrolled(logicalMouseX, logicalMouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        return super.keyPressed(input);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (close || mc.currentScreen != this) return false;
        boolean bindingActive = hasActiveBinding();
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == GLFW.GLFW_KEY_F && ctrl) {
            searchBar.setFocused(true);
            return true;
        }
        for (SettingsWindow window : LinkenVisuals.getInstance().getSettingsWindows()) {
            window.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!bindingActive && searchBar.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (!searchBar.isFocused() || bindingActive) {
            for (Panel panel : panels) {
                panel.keyPressed(keyCode, scanCode, modifiers, searchBar.getSearchText());
            }
        }
        if (bindingActive) {
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (keyReleased(input.key(), input.scancode(), input.modifiers())) return true;
        return super.keyReleased(input);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (close || mc.currentScreen != this) return false;
        for (SettingsWindow window : LinkenVisuals.getInstance().getSettingsWindows()) {
            window.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!searchBar.isFocused()) {
            for (Panel panel : panels) {
                panel.keyReleased(keyCode, scanCode, modifiers, searchBar.getSearchText());
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (charTyped((char) input.codepoint(), input.modifiers())) return true;
        return super.charTyped(input);
    }

    public boolean charTyped(char chr, int modifiers) {
        if (close || mc.currentScreen != this) return false;
        for (SettingsWindow window : LinkenVisuals.getInstance().getSettingsWindows()) {
            window.charTyped(chr, modifiers);
        }
        if (searchBar.charTyped(chr, modifiers)) {
            return true;
        }
        if (searchBar.isFocused()) {
            return false;
        }
        for (Panel panel : panels) {
            panel.charTyped(chr, modifiers, searchBar.getSearchText());
        }
        return false;
    }

    @Override
    public void init() {
        prepareForOpen();
        if (LinkenVisuals.getInstance().getSoundManager() != null) {
            LinkenVisuals.getInstance().getSoundManager().playClickGuiOpen();
        }
    }

    @Override
    public void tick() {
        updateLayout();
    }

    @Override
    public void close() {
        if (close) {
            return;
        }
        closeRenderScale = Math.max(0.01f, getLayoutScale(UI.peekInterfaceScale()));
        close = true;
        closeFadeAnimation.snapOpen();
        closeFadeAnimation.update(false);
        for (Panel panel : panels) {
            panel.dismissOverlays();
        }
        List<SettingsWindow> windows = LinkenVisuals.getInstance().getSettingsWindows();
        for (SettingsWindow window : windows) {
            window.resetInputState();
        }
        windows.clear();
        searchBar.reset();
        LiquidGlassReflectionCache.invalidate(LiquidGlassReflectionCache.ReflectionSource.CLICK_GUI);
    }

    @Override
    public void removed() {
        cancelCloseAnimation();
        resetOpenAnimation();
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui != null) {
            ui.syncClosedState();
        }
        super.removed();
    }

    public boolean isOpen() {
        return mc != null && mc.currentScreen == this && !close;
    }

    public boolean isDisplayed() {
        return mc != null && mc.currentScreen == this;
    }

    public boolean isClosing() {
        return close;
    }

    public void open() {
        if (!UI.isInPlayWorld() || mc == null) {
            return;
        }

        prepareForOpen();

        if (mc.currentScreen == this) {
            return;
        }

        mc.setScreen(this);
    }

    public void beginClose() {
        if (mc == null || mc.currentScreen != this) {
            cancelCloseAnimation();
            return;
        }
        close();
    }

    private void prepareForOpen() {
        cancelCloseAnimation();
        resetOpenAnimation();
        searchBar.reset();
        lastLayoutWidth = -1;
        lastLayoutHeight = -1;
        updateLayout();
    }

    private void resetOpenAnimation() {
        openAnimation.setDuration(OPEN_ANIMATION_DURATION);
        // Start the open animation from zero so the scale/fade-in actually plays
        // (snapOpen() jumped straight to fully-open, killing the appear animation).
        openAnimation.snapTo(true);
        closeFadeAnimation.setDuration(CLOSE_FADE_DURATION);
        closeFadeAnimation.snapOpen();
    }

    private void cancelCloseAnimation() {
        close = false;
        closeRenderScale = 1f;
        closeFadeAnimation.snapOpen();
    }

    private void finishClosing() {
        if (!close) {
            return;
        }
        close = false;
        closeRenderScale = 1f;
        closeFadeAnimation.snapOpen();
        resetOpenAnimation();
        UI ui = LinkenVisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui != null) {
            ui.syncClosedState();
        }
        if (mc != null && mc.currentScreen == this) {
            super.close();
        }
    }

    private void updateLayout() {
        if (mc == null || mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        if (screenW == lastLayoutWidth && screenH == lastLayoutHeight) {
            return;
        }
        lastLayoutWidth = screenW;
        lastLayoutHeight = screenH;

        int count = panels.size();
        float totalW = count * panelWidth + (count - 1) * panelGap;
        float startX = screenW / 2f - totalW / 2f;
        float panelsY = screenH / 2f - panelsYShift;

        float x = startX;
        for (Panel panel : panels) {
            panel.setX(x);
            panel.setY(panelsY);
            panel.setWidth(panelWidth);
            panel.setHeight(20f);
            x += panelWidth + panelGap;
        }

        guiLeft = startX;
        guiRight = startX + totalW;

        float searchW = ClickGuiLayout.SEARCH_WIDTH;
        float searchX = screenW / 2f - searchW / 2f;
        float searchY = screenH / 2f + ClickGuiLayout.SEARCH_Y_SHIFT;
        searchBar.setX(searchX);
        searchBar.setY(searchY);
        searchBar.setWidth(searchW);
        searchBar.setHeight(ClickGuiLayout.SEARCH_HEIGHT);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean descriptionIsEmpty() {
        return description == null || description.isEmpty();
    }

    private void drawAnimatedText(DrawContext context, AnimatedString animation, float x, float y, Color color, float shift, float fontSize, Font font, float progress, boolean animating) {
        if (animation == null) return;
        String current = animation.getCurrent();
        if (!animating) {
            Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), current, x, y, color);
            return;
        }

        float easedProgress = Math.max(0f, Math.min(1f, progress));
        int baseAlpha = color.getAlpha();
        int prevAlpha = Math.round(baseAlpha * (1f - easedProgress));
        int nextAlpha = Math.round(baseAlpha * easedProgress);

        Color prevColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), prevAlpha);
        Color nextColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), nextAlpha);

        float prevY = y - shift * easedProgress;
        float nextY = y + shift * (1f - easedProgress);

        String previous = animation.getPrevious();
        String curr = animation.getCurrent();
        int prefixLength = AnimatedTextHelper.getPrefixLength(previous, curr);
        int suffixLength = AnimatedTextHelper.getSuffixLength(previous, curr, prefixLength);

        String prefix = AnimatedTextHelper.substringSafe(curr, 0, prefixLength);
        String prevMid = AnimatedTextHelper.substringSafe(previous, prefixLength, previous.length() - suffixLength);
        String currMid = AnimatedTextHelper.substringSafe(curr, prefixLength, curr.length() - suffixLength);
        String suffix = AnimatedTextHelper.substringSafe(curr, curr.length() - suffixLength, curr.length());

        float prefixWidth = font.getWidth(prefix, fontSize);
        float currMidWidth = font.getWidth(currMid, fontSize);
        float prevMidWidth = font.getWidth(prevMid, fontSize);

        float midStartX = x + prefixWidth;
        float currMidX = midStartX;
        float prevMidX = midStartX + (currMidWidth - prevMidWidth) / 2f;

        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), prefix, x, y, color);
        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), suffix, midStartX + currMidWidth, y, color);

        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), prevMid, prevMidX, prevY, prevColor);
        Render2D.drawFont(context.getMatrices(), font.getFont(fontSize), currMid, currMidX, nextY, nextColor);
    }

    public boolean hasActiveBinding() {
        for (SettingsWindow window : LinkenVisuals.getInstance().getSettingsWindows()) {
            if (window.hasActiveBinding()) return true;
        }
        for (Panel panel : panels) {
            if (panel.hasActiveBinding()) return true;
        }
        return false;
    }

    private float getUiFade() {
        if (close) {
            return closeFadeAnimation.getValue();
        }
        // Fade the panels in alongside the scale-in for a smooth appear.
        return Math.max(MIN_VISIBLE_FADE, openAnimation.getValue());
    }

    private float getRenderScale() {
        if (close) {
            // Subtle shrink-out mirrors the appear pop.
            float closeProgress = Math.max(0f, Math.min(1f, closeFadeAnimation.getValue()));
            return Math.max(0.01f, closeRenderScale * (0.92f + 0.08f * closeProgress));
        }
        float layoutScale = getLayoutScale(UI.getInterfaceScale());
        // Scale-in from 0.9 -> 1.0 as the open animation runs.
        float openProgress = Math.max(0f, Math.min(1f, openAnimation.getValue()));
        float openScale = 0.9f + 0.1f * openProgress;
        return Math.max(0.01f, layoutScale * openScale);
    }

    private float getLayoutScale(float uiScale) {
        return Math.max(0.01f, uiScale * getFitScale(uiScale));
    }

    private float getFitScale(float uiScale) {
        if (mc == null || mc.getWindow() == null) {
            return 1f;
        }
        return ClickGuiLayout.computeFitScale(
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight(),
                panels.size()
        );
    }

    private float[] transformMouse(double mouseX, double mouseY) {
        float centerX = mc.getWindow().getScaledWidth() / 2f;
        float centerY = mc.getWindow().getScaledHeight() / 2f;
        float scale = getRenderScale();
        return new float[] {
                (float) (centerX + (mouseX - centerX) / scale),
                (float) (centerY + (mouseY - centerY) / scale)
        };
    }
}
