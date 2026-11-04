package ru.method.linkenvisuals.screen.linkgui;

import lombok.Getter;
import lombok.Setter;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemeManager;
import ru.method.linkenvisuals.screen.linkgui.theme.LinkGuiThemePreset;

@Getter
public final class LinkGuiContext {
    @Setter
    private Category selectedCategory = Category.Combat;
    @Setter
    private boolean cursorTracker = false;

    public LinkGuiThemePreset getSelectedTheme() {
        return LinkGuiThemeManager.getSelected();
    }

    public void setSelectedTheme(LinkGuiThemePreset theme) {
        LinkGuiThemeManager.setTheme(theme);
    }

    public void selectDock(LinkGuiDockTab tab) {
        this.selectedCategory = tab.category();
    }
}
