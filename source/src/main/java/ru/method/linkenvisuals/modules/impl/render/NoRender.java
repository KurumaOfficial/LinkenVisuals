package ru.method.linkenvisuals.modules.impl.render;


import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.BooleanSetting;
import ru.method.linkenvisuals.modules.settings.impl.PaddedBooleanSetting;
import net.minecraft.client.resource.language.I18n;


public class NoRender extends Module {

    public BooleanSetting hurtCam = new PaddedBooleanSetting(I18n.translate("settings.norender.hurtcam"), true, 2f);
    public BooleanSetting fire = new PaddedBooleanSetting(I18n.translate("settings.norender.fire"), true, 2f);
    public BooleanSetting totem = new PaddedBooleanSetting(I18n.translate("settings.norender.totem"), true, 2f);
    public BooleanSetting scoreboard = new PaddedBooleanSetting(I18n.translate("settings.norender.scoreboard"), false, 2f);
    public BooleanSetting bossBar = new PaddedBooleanSetting(I18n.translate("settings.norender.bossbar"), false, 2f);
    public BooleanSetting particles = new PaddedBooleanSetting(I18n.translate("settings.norender.particles"), true, 2f);
    public BooleanSetting armor = new PaddedBooleanSetting(I18n.translate("settings.norender.armor"), false, 2f);
    public BooleanSetting inventoryBackground = new PaddedBooleanSetting(I18n.translate("settings.norender.inventory_background"), false, 2f);

    public NoRender() {
        super("NoRender", Category.Visuals);
        getSettings().add(hurtCam);
        getSettings().add(fire);
        getSettings().add(totem);
        getSettings().add(scoreboard);
        getSettings().add(bossBar);
        getSettings().add(particles);
        getSettings().add(armor);
        getSettings().add(inventoryBackground);
    }
}
