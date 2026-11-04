package ru.method.linkenvisuals.modules.impl.render;


import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import net.minecraft.client.resource.language.I18n;


public class AspectRatio extends Module {

    public final NumberSetting ratio = new NumberSetting(I18n.translate("settings.aspectratio.ratio"), 1.75f, 0.1f, 5.0f, 0.05f);

    public AspectRatio() {
        super("AspectRatio", Category.Visuals);
        getSettings().add(ratio);
    }
}
