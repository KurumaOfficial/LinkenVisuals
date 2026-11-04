package ru.method.linkenvisuals.screen.clickgui;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.screen.clickgui.ClickGuiLayout;
import ru.method.linkenvisuals.screen.clickgui.components.Component;
import ru.method.linkenvisuals.screen.clickgui.components.impl.ColorComponent;
import ru.method.linkenvisuals.screen.clickgui.components.impl.ModuleComponent;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.utils.Wrapper;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.animations.infinity.InfinityAnimation;
import ru.method.linkenvisuals.utils.math.MathUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import ru.method.linkenvisuals.utils.render.Render2D;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class Panel implements Wrapper {
    private static final float CONTENT_VIEW_HEIGHT = ClickGuiLayout.PANEL_OPEN_HEIGHT - ClickGuiLayout.PANEL_HEADER_HEIGHT;
    private static final float SETTINGS_BOTTOM_GAP = 10f;
    private static final long SCROLL_ANIMATION_MS = 260L;
    @Setter private float x, y, width, height;
    private final Category category;
    @Getter private final List<ModuleComponent> components = new ArrayList<>();
    private ModuleComponent activeModule;
    private ModuleComponent lastSettingsModule;
    private final Animation openAnimation = new Animation(70, 1f, true, Easing.BOTH_SINE);
    private final Animation pageAnimation = new Animation(240, 1f, false, Easing.EASE_OUT_EXPO);
    private final Animation moduleHeaderAnimation = new Animation(130, 1f, false, Easing.EASE_OUT_EXPO);
    private final InfinityAnimation scrollAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final InfinityAnimation settingsScrollAnimation = new InfinityAnimation(Easing.EASE_OUT_CUBIC);
    private final List<ModuleComponent> filteredComponents = new ArrayList<>();
    private final List<Component> visibleSettingsComponents = new ArrayList<>();
    private float scroll;
    private float settingsScroll;
    private boolean open = true;
    private String cachedSearchQuery = "";
    private float cachedFilteredTotalHeight = -1f;
    private ModuleComponent cachedVisibleSettingsModule;
    private float cachedVisibleSettingsHeight = -1f;

    private int cachedModuleCount = -1;

    public Panel(float x, float y, float width, float height, Category category) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.category = category;
        for (Module module : panelModules(category)) {
            ModuleComponent component = new ModuleComponent(module);
            component.setParentPanel(this);
            components.add(component);
        }
        components.sort(Comparator.comparing(ModuleComponent::getName));
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta, String searchQuery) {
        syncModuleComponentsIfNeeded();
        ensureFilteredComponents(searchQuery);
        openAnimation.update(open);
        boolean hasSettingsView = activeModule != null || (lastSettingsModule != null && pageAnimation.getValue() > 0.01f);
        pageAnimation.update(activeModule != null);
        ModuleComponent settingsModule = activeModule != null ? activeModule : lastSettingsModule;
        ensureVisibleSettingsComponents(settingsModule);
        boolean headerForward = activeModule != null || pageAnimation.getValue() > 0.01f;
        moduleHeaderAnimation.update(headerForward);
        boolean renderSettingsPage = settingsModule != null && (pageAnimation.getValue() > 0.01f || activeModule != null);

        Color panelColor = ColorUtils.getGlassColor(255);
        Color textColor = ColorUtils.getTextColor(255);
        Color separatorColor = ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 70);
        float slide = (float) pageAnimation.getValue();
        float headerProgress = moduleHeaderAnimation.getValue();
        float headerSpacing = 12f * headerProgress;
        boolean headerVisible = settingsModule != null && headerProgress > 0.01f;

        float contentTop = y + height + headerSpacing;
        float panelHeight = (ClickGuiLayout.PANEL_OPEN_HEIGHT * openAnimation.getValue()) + (height * openAnimation.getReversedValue());
        float contentHeight = Math.max(0f, CONTENT_VIEW_HEIGHT * openAnimation.getValue() - (contentTop - (y + height)));
        float viewportHeight = hasSettingsView ? Math.max(0f, contentHeight - SETTINGS_BOTTOM_GAP) : contentHeight;
        float maxViewportHeight = Math.max(0f, panelHeight - (contentTop - y) - 1f);
        viewportHeight = Math.min(viewportHeight, maxViewportHeight);

        float maxListScroll = Math.max(0f, cachedFilteredTotalHeight - viewportHeight);
        float maxSettingsScroll = Math.max(0f, cachedVisibleSettingsHeight - viewportHeight);
        if (!hasSettingsView) {
            scroll = MathHelper.clamp(scroll, 0f, maxListScroll);
        } else {
            settingsScroll = MathHelper.clamp(settingsScroll, 0f, maxSettingsScroll);
        }

        panelHeight = (ClickGuiLayout.PANEL_OPEN_HEIGHT * openAnimation.getValue()) + (height * openAnimation.getReversedValue());
        Render2D.drawLiquidRectClickGuiPanel(
                context.getMatrices(),
                x,
                y,
                width,
                panelHeight,
                new BorderRadius(14f),
                new ColorRGBA(panelColor),
                2.5f,
                22.0f,
                11f,
                175.0f,
                true,
                0f,
                0.19f
        );
        float headerFontSize = 9f;
        float headerTextWidth = Fonts.SEMIBOLD.getWidth(category.name(), headerFontSize);
        float headerTextX = x + (width - headerTextWidth) / 2f - 4f;
        float headerTextY = y + (height - Fonts.SEMIBOLD.getHeight(headerFontSize)) / 2f;
        Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(headerFontSize), category.name(), headerTextX, headerTextY, textColor);
        float iconOffset = category == Category.Other ? -2f : 0f;
        Render2D.drawFont(context.getMatrices(), Fonts.ICONSNEW.getFont(10f), category.getIcon(), x + width - 19f + iconOffset, y + 5f, textColor);

        if (headerVisible) {
            String moduleName = settingsModule.getModule().getDisplayName();
            float arrowWidth = Fonts.SEMIBOLD.getWidth("<", 7.5f);
            float moduleWidth = Fonts.SEMIBOLD.getWidth(moduleName, 8f);
            float backWidth = arrowWidth + 4f + moduleWidth;
            float moduleY = y + height;
            boolean hoveredBack = MathUtils.isHovered(x + 6f, moduleY - 2f, backWidth, 11f, mouseX, mouseY);
            float headerOffset = (1f - headerProgress) * 6f;
            Color moduleColor = hoveredBack ? ColorUtils.alpha(textColor, (int) (255 * headerProgress)) : ColorUtils.alpha(textColor, (int) (220 * headerProgress));
            Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(7.5f), "<", x + 6f + headerOffset, moduleY, moduleColor);
            Render2D.drawFont(context.getMatrices(), Fonts.SEMIBOLD.getFont(8f), moduleName, x + 6f + headerOffset + arrowWidth + 4f, moduleY, moduleColor);
        }

        Render2D.startScissor(context, x, contentTop, width, viewportHeight);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(-width * slide, 0f);
        float animatedListScroll = MathHelper.clamp(scrollAnimation.animate(scroll, SCROLL_ANIMATION_MS), 0f, maxListScroll);
        float listCurrentY = contentTop - animatedListScroll;
        float listBottom = contentTop + viewportHeight;

        if (openAnimation.getValue() > 0f) {
            for (int i = 0; i < filteredComponents.size(); i++) {
                ModuleComponent component = filteredComponents.get(i);
                component.setX(x);
                component.setY(listCurrentY);
                component.setWidth(width);
                component.setHeight(height);
                boolean visible = intersectsVerticalRange(listCurrentY, component.getHeight(), contentTop, listBottom);
                if (visible) {
                    component.render(context, mouseX, mouseY, delta);

                    if (i < filteredComponents.size() - 1 && listCurrentY + component.getHeight() <= listBottom) {
                        Render2D.drawRoundedRect(
                                context.getMatrices(),
                                x + 4f,
                                listCurrentY + component.getHeight() - 0.5f,
                                width - 8f,
                                0.5f,
                                0.35f,
                                separatorColor
                        );
                    }
                }

                listCurrentY += component.getHeight();
            }
        }
        context.getMatrices().popMatrix();

        if (renderSettingsPage) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(width * (1f - slide), 0f);
            float animatedSettingsScroll = MathHelper.clamp(settingsScrollAnimation.animate(settingsScroll, SCROLL_ANIMATION_MS), 0f, maxSettingsScroll);
            float currentY = contentTop - animatedSettingsScroll;
            float settingsBottom = contentTop + viewportHeight;
            for (Component component : visibleSettingsComponents) {
                component.setX(x + 2f);
                component.setY(currentY);
                component.setWidth(width - 4f);
                float componentBaseHeight = component.getLayoutHeight();
                component.setHeight(componentBaseHeight);
                float componentHeight = componentBaseHeight + component.getAddHeight().get();
                if (intersectsVerticalRange(currentY, componentHeight, contentTop, settingsBottom)) {
                    component.render(context, mouseX, mouseY, delta);
                }
                currentY += componentHeight;
            }
            context.getMatrices().popMatrix();
        }

        Render2D.stopScissor(context);

        if (pageAnimation.getValue() <= 0.01f && activeModule == null) lastSettingsModule = null;
    }

    public void renderOverlays(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean renderSettingsPage = cachedVisibleSettingsModule != null && (pageAnimation.getValue() > 0.01f || activeModule != null);
        if (!renderSettingsPage) return;

        float slide = (float) pageAnimation.getValue();
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(width * (1f - slide), 0f);
        for (Component component : visibleSettingsComponents) {
            if (!component.hasActiveOverlay()) continue;
            component.renderOverlay(context, mouseX, mouseY, delta);
        }
        context.getMatrices().popMatrix();
    }

    public boolean handleExtendedClick(double mouseX, double mouseY, int button) {
        ModuleComponent settingsModule = activeModule != null ? activeModule : lastSettingsModule;
        boolean renderSettingsPage = settingsModule != null && (pageAnimation.getValue() > 0.01f || activeModule != null);
        if (!renderSettingsPage) return false;

        for (Component component : settingsModule.getComponents()) {
            if (!component.getVisible().get()) continue;
            boolean shouldHandle = component.isExtendedHovered(mouseX, mouseY);
            if (!shouldHandle && component instanceof ColorComponent colorComponent && colorComponent.isEditing()) {
                shouldHandle = true;
            }
            if (shouldHandle) {
                component.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        return false;
    }

    private float getTotalHeight(String searchQuery) {
        ensureFilteredComponents(searchQuery);
        return cachedFilteredTotalHeight;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) { render(context, mouseX, mouseY, delta, ""); }
    public void mouseClicked(double mouseX, double mouseY, int button) { mouseClicked(mouseX, mouseY, button, ""); }
    public void mouseReleased(double mouseX, double mouseY, int button) { mouseReleased(mouseX, mouseY, button, ""); }
    public void keyPressed(int keyCode, int scanCode, int modifiers) { keyPressed(keyCode, scanCode, modifiers, ""); }
    public void keyReleased(int keyCode, int scanCode, int modifiers) { keyReleased(keyCode, scanCode, modifiers, ""); }
    public void charTyped(char chr, int modifiers) { charTyped(chr, modifiers, ""); }
    private float getTotalHeight() { return getTotalHeight(""); }

    public void mouseClicked(double mouseX, double mouseY, int button, String searchQuery) {
        if (MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY) && button == 1) return;

        float headerProgress = moduleHeaderAnimation.getValue();
        float contentTop = y + height + (12f * headerProgress);
        float contentHeight = Math.max(0f, CONTENT_VIEW_HEIGHT * openAnimation.getValue() - (contentTop - (y + height)));
        float viewportHeight = isSettingsViewActive() ? Math.max(0f, contentHeight - SETTINGS_BOTTOM_GAP) : contentHeight;

        if (activeModule != null) {
            String moduleName = activeModule.getModule().getDisplayName();
            float arrowWidth = Fonts.SEMIBOLD.getWidth("<", 7.5f);
            float moduleWidth = Fonts.SEMIBOLD.getWidth(moduleName, 8f);
            float backWidth = arrowWidth + 4f + moduleWidth;
            float moduleY = y + height;
            float headerOffset = (1f - moduleHeaderAnimation.getValue()) * 6f;
            float backStartX = x + 6f + headerOffset;
            if (moduleHeaderAnimation.getValue() > 0.01f && MathUtils.isHovered(backStartX, moduleY - 2f, backWidth, 11f, (float) mouseX, (float) mouseY) && button == 0) {
                closeActiveModule();
                return;
            }

            if (open && MathUtils.isHovered(x, contentTop, width, viewportHeight, (float) mouseX, (float) mouseY)) {
                for (Component component : activeModule.getComponents()) {
                    if (!component.getVisible().get()) continue;
                    component.mouseClicked(mouseX, mouseY, button);
                }
            }
            return;
        }

        if (open && MathUtils.isHovered(x, contentTop, width, viewportHeight, (float) mouseX, (float) mouseY)) {
            ensureFilteredComponents(searchQuery);
            for (ModuleComponent component : filteredComponents) {
                component.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button, String searchQuery) {
        if (open) {
            if (activeModule != null) {
                for (Component component : activeModule.getComponents()) {
                    if (!component.getVisible().get()) continue;
                    component.mouseReleased(mouseX, mouseY, button);
                }
                return;
            }
            ensureFilteredComponents(searchQuery);
            for (ModuleComponent component : filteredComponents) {
                component.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float headerProgress = moduleHeaderAnimation.getValue();
        float contentTop = y + height + (12f * headerProgress);
        float contentHeight = Math.max(0f, CONTENT_VIEW_HEIGHT * openAnimation.getValue() - (contentTop - (y + height)));
        float viewportHeight = isSettingsViewActive() ? Math.max(0f, contentHeight - SETTINGS_BOTTOM_GAP) : contentHeight;
        if (open && MathUtils.isHovered(x, contentTop, width, viewportHeight, (float) mouseX, (float) mouseY)) {
            if (activeModule != null) {
                for (Component component : activeModule.getComponents()) {
                    if (!component.getVisible().get()) continue;
                    if (component.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return;
                }
                settingsScroll -= (float) (verticalAmount * 20);
                settingsScroll = MathHelper.clamp(settingsScroll, 0f, Math.max(0f, getSettingsHeight(activeModule) - viewportHeight));
            } else {
                scroll -= (float) (verticalAmount * 20);
                float totalHeight = cachedFilteredTotalHeight >= 0f ? cachedFilteredTotalHeight : getTotalHeight();
                scroll = MathHelper.clamp(scroll, 0f, Math.max(0f, totalHeight - viewportHeight));
            }
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers, String searchQuery) {
        if (open) {
            if (activeModule != null) {
                for (Component component : activeModule.getComponents()) {
                    if (!component.getVisible().get()) continue;
                    component.keyPressed(keyCode, scanCode, modifiers);
                }
                return;
            }
            ensureFilteredComponents(searchQuery);
            for (ModuleComponent component : filteredComponents) {
                component.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    public void keyReleased(int keyCode, int scanCode, int modifiers, String searchQuery) {
        if (open) {
            if (activeModule != null) {
                for (Component component : activeModule.getComponents()) {
                    if (!component.getVisible().get()) continue;
                    component.keyReleased(keyCode, scanCode, modifiers);
                }
                return;
            }
            ensureFilteredComponents(searchQuery);
            for (ModuleComponent component : filteredComponents) {
                component.keyReleased(keyCode, scanCode, modifiers);
            }
        }
    }

    public void charTyped(char chr, int modifiers, String searchQuery) {
        if (open) {
            if (activeModule != null) {
                for (Component component : activeModule.getComponents()) {
                    if (!component.getVisible().get()) continue;
                    component.charTyped(chr, modifiers);
                }
                return;
            }
            ensureFilteredComponents(searchQuery);
            for (ModuleComponent component : filteredComponents) {
                component.charTyped(chr, modifiers);
            }
        }
    }

    public void openModuleSettings(ModuleComponent moduleComponent) {
        if (moduleComponent == null) return;
        this.activeModule = moduleComponent;
        this.lastSettingsModule = moduleComponent;
        this.settingsScroll = 0f;
        settingsScrollAnimation.animate(settingsScroll, SCROLL_ANIMATION_MS);
        moduleHeaderAnimation.update(true);
        if (LinkenVisuals.getInstance().getSoundManager() != null) {
            LinkenVisuals.getInstance().getSoundManager().playClickGuiOpen();
        }
    }

    public boolean isSettingsViewActive() {
        return activeModule != null || (lastSettingsModule != null && pageAnimation.getValue() > 0.01f);
    }

    public boolean isMouseOverContentArea(float mouseX, float mouseY) {
        float headerProgress = moduleHeaderAnimation.getValue();
        float contentTop = y + height + (12f * headerProgress);
        float contentHeight = Math.max(0f, CONTENT_VIEW_HEIGHT * openAnimation.getValue() - (contentTop - (y + height)));
        float viewportHeight = isSettingsViewActive() ? Math.max(0f, contentHeight - SETTINGS_BOTTOM_GAP) : contentHeight;
        return open && MathUtils.isHovered(x, contentTop, width, viewportHeight, mouseX, mouseY);
    }

    public boolean hasActiveBinding() {
        if (activeModule != null) {
            for (Component component : activeModule.getComponents()) {
                if (component.isBinding()) return true;
            }
            return activeModule.isBinding();
        }
        for (ModuleComponent component : components) {
            if (component.isBinding()) return true;
        }
        return false;
    }

    public void dismissOverlays() {
        closeActiveModule();
        for (ModuleComponent component : components) {
            component.onClose();
        }
    }

    private void closeActiveModule() {
        if (this.activeModule != null) {
            for (Component component : this.activeModule.getComponents()) {
                component.onClose();
            }
        }
        this.activeModule = null;
    }

    private float getSettingsHeight(ModuleComponent moduleComponent) {
        ensureVisibleSettingsComponents(moduleComponent);
        return cachedVisibleSettingsHeight;
    }

    private void ensureFilteredComponents(String searchQuery) {
        String normalizedQuery = normalizeSearchQuery(searchQuery);
        if (normalizedQuery.equals(cachedSearchQuery) && cachedFilteredTotalHeight >= 0f) {
            return;
        }

        cachedSearchQuery = normalizedQuery;
        cachedFilteredTotalHeight = 0f;
        filteredComponents.clear();
        for (ModuleComponent component : components) {
            if (!component.matchesSearch(normalizedQuery)) continue;
            filteredComponents.add(component);
            float baseHeight = component.getHeight() > 0f ? component.getHeight() : height;
            cachedFilteredTotalHeight += baseHeight;
        }
    }

    /** Модули категории для Panel GUI. Elements исключён: в Panel-режиме HUD правится ПКМ по открытому
     *  чату (см. {@link ru.method.linkenvisuals.managers.system.HudManager}), а не через этот модуль
     *  (он доступен только в Link GUI). В Link GUI список модулей строится отдельно и Elements остаётся. */
    private static List<Module> panelModules(Category category) {
        List<Module> src = LinkenVisuals.getInstance().getModuleManager().getModules(category);
        List<Module> out = new ArrayList<>(src.size());
        for (Module m : src) {
            if (m instanceof ru.method.linkenvisuals.modules.impl.client.Elements) continue;
            out.add(m);
        }
        return out;
    }

    private void syncModuleComponentsIfNeeded() {
        int moduleCount = panelModules(category).size();
        if (moduleCount == cachedModuleCount && moduleCount == components.size()) {
            return;
        }
        cachedModuleCount = moduleCount;
        syncModuleComponents();
    }

    private void syncModuleComponents() {
        boolean changed = false;
        for (Module module : panelModules(category)) {
            boolean exists = false;
            for (ModuleComponent component : components) {
                if (component.getModule() == module) {
                    exists = true;
                    break;
                }
            }
            if (exists) continue;

            ModuleComponent component = new ModuleComponent(module);
            component.setParentPanel(this);
            components.add(component);
            changed = true;
        }

        if (!changed) return;
        components.sort(Comparator.comparing(ModuleComponent::getName));
        cachedSearchQuery = "";
        cachedFilteredTotalHeight = -1f;
    }

    private void ensureVisibleSettingsComponents(ModuleComponent moduleComponent) {
        if (moduleComponent == cachedVisibleSettingsModule && !visibleSettingsComponents.isEmpty()) {
            if (!needsSettingsLayoutRefresh(moduleComponent)) {
                return;
            }
        }

        cachedVisibleSettingsModule = moduleComponent;
        visibleSettingsComponents.clear();
        cachedVisibleSettingsHeight = 0f;
        if (moduleComponent == null) {
            return;
        }

        for (Component component : moduleComponent.getComponents()) {
            if (!component.getVisible().get()) continue;
            visibleSettingsComponents.add(component);
            float baseHeight = component.getLayoutHeight();
            cachedVisibleSettingsHeight += baseHeight + component.getAddHeight().get();
        }
    }

    private boolean needsSettingsLayoutRefresh(ModuleComponent moduleComponent) {
        if (moduleComponent == null) {
            return cachedVisibleSettingsHeight > 0f;
        }
        float currentHeight = 0f;
        for (Component component : moduleComponent.getComponents()) {
            if (!component.getVisible().get()) continue;
            currentHeight += component.getLayoutHeight() + component.getAddHeight().get();
        }
        return Math.abs(currentHeight - cachedVisibleSettingsHeight) > 0.01f;
    }

    private String normalizeSearchQuery(String searchQuery) {
        return searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
    }

    private boolean intersectsVerticalRange(float y, float height, float viewTop, float viewBottom) {
        return y + height >= viewTop && y <= viewBottom;
    }

}
