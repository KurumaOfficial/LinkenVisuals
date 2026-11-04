package ru.method.linkenvisuals.screen.linkgui;

import dev.linkenvisuals.render.ClickGuiFirePass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.Setting;
import ru.method.linkenvisuals.modules.settings.impl.*;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.screen.clickgui.components.impl.*;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeScope;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiColors;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiRender;
import ru.method.linkenvisuals.screen.windowgui.WindowGuiTheme;
import ru.method.linkenvisuals.utils.render.GuiIcons;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.LinkGuiRenderContext;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class LinkGuiSettingsWindow implements Wrapper {
    private static final long SCROLL_ANIMATION_MS = 280L;
    // Высота контента подстраивается под количество настроек (нет пустоты у коротких окон),
    // с ограничением сверху (дальше — скролл).
    private static final float MIN_CONTENT = 24f;
    private static final float MAX_CONTENT = 300f;
    private static final float RADIUS = 10f;

    public static final float WINDOW_WIDTH = 258f; // было 172 (под старый 1280); ×1.5 под редизайн 1920
    public static final float HEADER_HEIGHT = 32f;

    private float x;
    private float y;
    private final float width = WINDOW_WIDTH;
    private final float height = HEADER_HEIGHT;
    private final Module module;
    // Индекс колонки (0 = слева от главного окна, 1 = справа). Меняется при переполнении колонки.
    @lombok.Getter
    @lombok.Setter
    private int slot;
    private final List<Component> components = new ArrayList<>();
    private final List<Component> visibleComponents = new ArrayList<>();

    private float dragX;
    private float dragY;
    private boolean dragging;
    @lombok.Getter
    private boolean userMoved; // окно перетащили вручную — автораскладка его больше не трогает
    // Порядок открытия (монотонный): новее = больше. Для стабильной раскладки (новые окна сверху, в приоритете).
    @lombok.Getter
    private final long openOrder = NEXT_OPEN_ORDER++;
    private static long NEXT_OPEN_ORDER = 0L;
    private float scroll;
    private float dynamicContentHeight = MIN_CONTENT;
    private long lastHeightFrameMs;
    // Целевая позиция — окно плавно к ней едет (перестроение стопки при открытии/закрытии соседей).
    private float targetX;
    private float targetY;
    private final InfinityAnimation scrollAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);

    // Анимация открытия/закрытия — как у окна «Тема»: масштаб из центра + затухание (360мс, EASE_OUT_CUBIC).
    private final Animation openAnimation = new Animation(360, 1f, true, Easing.EASE_OUT_CUBIC);
    // Заливка иконки модуля — 1:1 как в карточке: enable (300) + «филлер» (520), берём максимум.
    private final Animation iconEnableAnim = new Animation(300, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation iconStripeAnim = new Animation(520, 1f, false, Easing.EASE_OUT_CUBIC);
    // Hover шапки: крестик подсвечивается плавно (раньше скачком); кликабельный бейдж модуля
    // получает акцентную рамку — без неё тумблер-по-клику на бейдже недискаверим.
    private final Animation closeHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
    private final Animation badgeHoverAnim = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);

    /** Прогресс заливки 1:1 с ModuleCard.moduleStripeFill. */
    private static float moduleStripeFill(Animation anim) {
        if (anim.isForward() && anim.finished()) return 1f;
        float raw = anim.getLinear();
        if (anim.isForward()) {
            float filled = raw * raw * (3f - 2f * raw);
            return raw >= 0.985f ? 1f : filled;
        }
        return raw;
    }
    private boolean closing;

    public LinkGuiSettingsWindow(Module module, float x, float y) {
        this(module, x, y, 0);
    }

    public LinkGuiSettingsWindow(Module module, float x, float y, int slot) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
        this.slot = slot;

        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting s) components.add(new BooleanComponent(s));
            else if (setting instanceof NumberSetting s) components.add(new SliderComponent(s));
            else if (setting instanceof EnumSetting<?> s) components.add(new EnumComponent(s));
            else if (setting instanceof StringSetting s) components.add(new StringComponent(s));
            else if (setting instanceof ListSetting s) components.add(new ListComponent(s));
            else if (setting instanceof BindSetting s) components.add(new BindComponent(s));
            else if (setting instanceof ColorSetting s) components.add(new ColorComponent(s));
            else if (setting instanceof ActionSetting s) components.add(new ActionButtonComponent(s));
        }
        if (module instanceof ru.method.linkenvisuals.modules.impl.client.UI uiModule) {
            components.add(new DualActionButtonComponent(
                    "settings.ui.apply",
                    uiModule::applyScaleSetting,
                    "settings.ui.default",
                    uiModule::resetScaleSetting
            ));
        }
        // Высота компонентов как при рендере (ROW_HEIGHT + Link-контекст) — чтобы расчёт высоты окна
        // совпадал с реальной отрисовкой (иначе переполнение колонок считается неверно).
        for (Component component : components) {
            component.setHeight(LinkGuiSettingStyle.ROW_HEIGHT);
        }
        rebuildVisibleComponents();
        LinkGuiRenderContext.begin();
        try {
            dynamicContentHeight = computeContentHeight();
        } finally {
            LinkGuiRenderContext.end();
        }
        openAnimation.reset();
        // new Animation(forward=false) стартует со значения 1 и затухает к 0 (таймер «только что
        // запущен»); окно создаётся заново при каждом открытии — без снапа крестик и рамка бейджа
        // «вспыхивают» hover-подсветкой первые ~180 мс.
        closeHoverAnim.snapTo(false);
        badgeHoverAnim.snapTo(false);
    }

    /** Высота контента под число настроек, ограниченная сверху (дальше — скролл). */
    private float computeContentHeight() {
        // Кап высоты — минимум из MAX_CONTENT и того, что реально влезает на экран (иначе окно с «Сферами»
        // вылезало за низ и не листалось). Лишние настройки уходят в скролл внутри влезающего окна.
        float cap = Math.min(MAX_CONTENT, LinkGuiSettings.maxContentHeight(HEADER_HEIGHT));
        // +16: запас под верхний отступ раскладки (+4) и scissor (−6), чтобы последняя строка не обрезалась.
        return MathHelper.clamp(getVisibleComponentsHeight() + 16f, MIN_CONTENT, cap);
    }

    public float getCurrentHeight() {
        return height + dynamicContentHeight;
    }

    public float getY() {
        return y;
    }

    public float getX() {
        return x;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setTarget(float targetX, float targetY) {
        this.targetX = targetX;
        this.targetY = targetY;
    }

    public float getTargetY() {
        return targetY;
    }

    /** Мгновенно поставить окно на целевую позицию (при открытии — без проезда). */
    public void snapPosition() {
        this.x = targetX;
        this.y = targetY;
    }

    /** Smoothstep, как LuckyAnimations.sidePanelEase в PrimeVisuals. */
    private static float sidePanelEase(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return c * c * (3f - 2f * c);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        rebuildVisibleComponents();
        // Плавное растягивание окна при смене набора настроек (например, режим в AutoSwap).
        float targetContent = computeContentHeight();
        long heightNow = System.currentTimeMillis();
        float heightDt = lastHeightFrameMs == 0L ? 0f : Math.min(0.1f, (heightNow - lastHeightFrameMs) / 1000f);
        lastHeightFrameMs = heightNow;
        dynamicContentHeight += (targetContent - dynamicContentHeight) * (1f - (float) Math.exp(-14f * heightDt));
        if (Math.abs(targetContent - dynamicContentHeight) < 0.5f) {
            dynamicContentHeight = targetContent;
        }
        // Перетаскивание двигает окно в mouseDragged(). Здесь только автораскладка (когда не тащим и не двигали вручную).
        if (!dragging && !userMoved) {
            // Плавный переезд к целевой позиции (автораскладка). Перетащенные окна остаются на месте.
            float posF = 1f - (float) Math.exp(-16f * heightDt);
            x += (targetX - x) * posF;
            y += (targetY - y) * posF;
            if (Math.abs(targetX - x) < 0.3f) x = targetX;
            if (Math.abs(targetY - y) < 0.3f) y = targetY;
        }
        openAnimation.update(openAnimation.isForward());

        float totalH = height + dynamicContentHeight;
        // Каждый кадр держим окно внутри видимой области (драг + смена разрешения окна) — не уходит за сейфзону.
        x = LinkGuiSettings.clampX(x, width);
        y = LinkGuiSettings.clampY(y, totalH);
        // Анимация как у окна «Тема»: масштаб из центра (0.94→1) + затухание.
        float ct = Math.max(0f, Math.min(1f, (float) openAnimation.getValue()));
        float scale = 0.94f + 0.06f * ct;
        float cx = x + width / 2f;
        float cy = y + totalH / 2f;
        float prevFade = LinkGuiRenderContext.getFadeMultiplier();
        LinkGuiRenderContext.setFade(prevFade * ct);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-cx, -cy);

        ClickGuiFirePass.addPanel(context, x, y, width, totalH, RADIUS, ct);
        LinkGuiChrome.drawGlassPanel(context.getMatrices(), x, y, width, totalH, RADIUS);

        float badgeX = x + 10f;
        float badgeY = y + 6f;
        float badgeSize = 20f;
        // Заливка иконки 1:1 с карточкой: badgeT = max(enableAnim, stripeFill); цвет иконки анимируется
        // lerp от тусклого TEXT_SECOND к accent (поэтому при выкл иконка НЕ залита, а плавно заливается).
        boolean enabled = module.isToggled();
        iconEnableAnim.update(enabled);
        iconStripeAnim.update(enabled);
        float badgeT = Math.max(iconEnableAnim.getValue(), moduleStripeFill(iconStripeAnim));
        Color accent = LinkGuiThemeScope.accent();
        LinkGuiChrome.drawIconBadge(context.getMatrices(), badgeX, badgeY, badgeSize, 5f, enabled, badgeT);
        Color iconColor = WindowGuiColors.lerp(
                WindowGuiColors.withAlpha(WindowGuiTheme.TEXT_SECOND, 0.35f), accent, badgeT);
        Identifier catTex = GuiIcons.forCategory(module.getCategory());
        if (catTex != null) {
            GuiIcons.draw(context.getMatrices(), catTex, badgeX + badgeSize / 2f, badgeY + badgeSize / 2f, 10f, iconColor);
        } else {
            LinkGuiChrome.drawIconInBadge(context.getMatrices(), badgeX, badgeY, badgeSize,
                    module.getCategory().getIcon(), iconColor, 8.5f);
        }
        badgeHoverAnim.update(MathUtils.isHovered(badgeX, badgeY, badgeSize, badgeSize, mouseX, mouseY));
        float badgeHoverT = badgeHoverAnim.getValue();
        if (badgeHoverT > 0.01f) {
            WindowGuiRender.drawRoundedOutlineRect(context.getMatrices(), badgeX, badgeY, badgeSize, badgeSize, 5f, 1f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(160 * badgeHoverT)));
        }

        float titleX = badgeX + 26f;
        // Типографика редизайна 3a: имя модуля — Unbounded, категория — Golos Text.
        WindowGuiRender.drawText(context.getMatrices(), Fonts.UNBOUNDED, module.getDisplayName(),
                titleX, y + 7f, ColorUtils.getTextColor(255), 9.2f);
        String category = module.getCategory().name();
        WindowGuiRender.drawText(context.getMatrices(), Fonts.GOLOS, category,
                titleX, y + 18f, ColorUtils.getTextColor(120), 6.8f);

        float closeX = x + width - 22f;
        float closeY = y + 6f;
        boolean closeHover = MathUtils.isHovered(closeX, closeY, 16f, 16f, mouseX, mouseY);
        closeHoverAnim.update(closeHover);
        LinkGuiChrome.drawPillButton(context.getMatrices(), closeX, closeY, 16f, 16f, 5f, closeHoverAnim.getValue());
        // Центрируем крестик и по вертикали (раньше Y был фиксированным → крестик стоял неровно в квадрате).
        float closeGlyphH = Fonts.ICONSNEW.getHeight(7f);
        WindowGuiRender.drawCenteredText(context.getMatrices(), Fonts.ICONSNEW, "L",
                closeX + 8f, closeY + (16f - closeGlyphH) / 2f, ColorUtils.getTextColor(220), 7f);

        LinkGuiChrome.drawDivider(context.getMatrices(), x + 10f, y + height - 1f, width - 20f);

        float contentX = x + 8f;
        float contentY = y + height + 2f;
        float contentW = width - 16f;
        LinkGuiChrome.drawInset(context.getMatrices(), contentX, contentY, contentW, dynamicContentHeight - 4f, 7f);

        LinkGuiRenderContext.begin();
        try {
            Render2D.startScissor(context, contentX + 1f, contentY + 1f, contentW - 2f, dynamicContentHeight - 6f);

            float maxScroll = Math.max(0f, getVisibleComponentsHeight() - dynamicContentHeight + 12f);
            scroll = MathHelper.clamp(scroll, 0f, maxScroll);
            float animatedScroll = MathHelper.clamp(scrollAnimation.animate(scroll, SCROLL_ANIMATION_MS), 0f, maxScroll);
            float currentY = contentY + 4f - animatedScroll;
            float viewportTop = contentY + 2f;
            float viewportBottom = contentY + dynamicContentHeight - 6f;

            for (int i = 0; i < visibleComponents.size(); i++) {
                Component component = visibleComponents.get(i);
                component.setX(contentX + 6f);
                component.setY(currentY);
                component.setWidth(contentW - 12f);
                component.setHeight(LinkGuiSettingStyle.ROW_HEIGHT);
                float componentBaseHeight = component.getLayoutHeight();
                float componentHeight = componentBaseHeight + component.getAddHeight().get();

                if (intersectsVerticalRange(currentY, componentHeight, viewportTop, viewportBottom)) {
                    if (i > 0) {
                        LinkGuiChrome.drawDivider(context.getMatrices(), contentX + 5f, currentY - 1f, contentW - 10f);
                    }
                    component.render(context, mouseX, mouseY, delta);
                }
                currentY += componentHeight;
            }

            Render2D.stopScissor(context);
        } finally {
            LinkGuiRenderContext.end();
        }

        context.getMatrices().popMatrix();

        // Scissor is already closed inside the try block; a second clear would underflow.
        // Попап (палитра колорпикера) рисуется вне scissor окна, но должен тускнуть/исчезать СИНХРОННО
        // с содержимым окна — тем же множителем prevFade*ct. Раньше использовался только prevFade, поэтому
        // при закрытии окна настроек (ct→0) палитра оставалась видимой и пропадала рассинхронно.
        LinkGuiRenderContext.setFade(prevFade * ct);
        LinkGuiRenderContext.begin();
        try {
            for (Component component : visibleComponents) {
                if (!component.hasActiveOverlay()) continue;
                component.renderOverlay(context, mouseX, mouseY, delta);
            }
        } finally {
            LinkGuiRenderContext.end();
        }
        LinkGuiRenderContext.setFade(prevFade);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return false;

        // Ввод обрабатываем в Link-контексте — иначе компоненты (тумблер, слайдер) думают, что
        // они НЕ в Link-режиме, и берут зоны клика, не совпадающие с отрисовкой → клики мимо.
        LinkGuiRenderContext.begin();
        try {
            float[] localMouse = toLocalMouse(mouseX, mouseY);
            float scaledMouseX = localMouse[0];
            float scaledMouseY = localMouse[1];

            if (handleExtendedClick(mouseX, mouseY, button)) {
                return true;
            }

            float totalH = height + dynamicContentHeight;
            if (!MathUtils.isHovered(x, y, width, totalH, scaledMouseX, scaledMouseY)) {
                return false;
            }

            float closeX = x + width - 22f;
            float closeY = y + 6f;
            float badgeX = x + 10f;
            float badgeY = y + 6f;
            if (MathUtils.isHovered(closeX, closeY, 16f, 16f, scaledMouseX, scaledMouseY) && button == 0) {
                close();
            } else if (MathUtils.isHovered(badgeX, badgeY, 20f, 20f, scaledMouseX, scaledMouseY) && button == 0) {
                // Клик по иконке модуля в шапке настроек — включает/выключает модуль.
                module.toggle();
            } else if (MathUtils.isHovered(x, y, width - 28f, height, scaledMouseX, scaledMouseY) && button == 0) {
                // Перетаскивание за шапку.
                dragging = true;
                userMoved = true;
                dragX = (float) mouseX - x;
                dragY = (float) mouseY - y;
            } else if (MathUtils.isHovered(x + 8f, y + height, width - 16f, dynamicContentHeight, scaledMouseX, scaledMouseY)) {
                for (Component component : visibleComponents) {
                    component.mouseClicked(scaledMouseX, scaledMouseY, button);
                }
            }
            return true;
        } finally {
            LinkGuiRenderContext.end();
        }
    }

    /** Явное перетаскивание окна (канонический drag): двигаем за мышью, пока удерживается шапка. */
    public void mouseDragged(double mouseX, double mouseY, int button) {
        if (closing || !dragging || button != 0) return;
        x = (float) mouseX - dragX;
        y = (float) mouseY - dragY;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        if (closing) return;
        LinkGuiRenderContext.begin();
        try {
            float[] localMouse = toLocalMouse(mouseX, mouseY);
            rebuildVisibleComponents();
            for (Component component : visibleComponents) {
                component.mouseReleased(localMouse[0], localMouse[1], button);
            }
        } finally {
            LinkGuiRenderContext.end();
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing) return false;
        LinkGuiRenderContext.begin();
        try {
            float[] localMouse = toLocalMouse(mouseX, mouseY);
            rebuildVisibleComponents();
            for (Component component : visibleComponents) {
                if (component.mouseScrolled(localMouse[0], localMouse[1], horizontalAmount, verticalAmount)) return true;
            }
            if (MathUtils.isHovered(x + 8f, y + height, width - 16f, dynamicContentHeight, localMouse[0], localMouse[1])) {
                float maxScroll = Math.max(0f, getVisibleComponentsHeight() - dynamicContentHeight + 14f);
                if (maxScroll <= 0.01f) return false;
                scroll -= (float) (verticalAmount * 12);
                scroll = MathHelper.clamp(scroll, 0f, maxScroll);
                return true;
            }
            return false;
        } finally {
            LinkGuiRenderContext.end();
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return;
        LinkGuiRenderContext.begin();
        try {
            rebuildVisibleComponents();
            for (Component component : visibleComponents) {
                component.keyPressed(keyCode, scanCode, modifiers);
            }
        } finally {
            LinkGuiRenderContext.end();
        }
    }

    public void charTyped(char chr, int modifiers) {
        if (closing) return;
        LinkGuiRenderContext.begin();
        try {
            rebuildVisibleComponents();
            for (Component component : visibleComponents) {
                component.charTyped(chr, modifiers);
            }
        } finally {
            LinkGuiRenderContext.end();
        }
    }

    public void resetInputState() {
        for (Component component : components) {
            component.onClose();
        }
    }

    public boolean isFinishedClosing() {
        return closing && openAnimation.getValue() <= 0.01f;
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

    public static float getContentHeight() {
        return MAX_CONTENT;
    }

    /** Программно закрыть окно с анимацией (для замены самого старого при лимите). */
    public void requestClose() {
        close();
    }

    public boolean isClosing() {
        return closing;
    }

    private void close() {
        if (!closing) {
            resetInputState();
            closing = true;
            openAnimation.update(false);
        }
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
            totalHeight += component.getLayoutHeight() + component.getAddHeight().get() + 1f;
        }
        return totalHeight;
    }

    private boolean intersectsVerticalRange(float componentY, float componentHeight, float viewportTop, float viewportBottom) {
        return componentY + componentHeight >= viewportTop && componentY <= viewportBottom;
    }

    private float[] toLocalMouse(double mouseX, double mouseY) {
        // Анимация теперь масштабом из центра (как «Тема») — позиции кликов не сдвигаем.
        return new float[] {(float) mouseX, (float) mouseY};
    }
}
