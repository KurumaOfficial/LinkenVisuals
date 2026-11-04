package ru.method.linkenvisuals.modules.impl.render;

import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;

/**
 * Клиентски подменяет скин ВСЕХ игроков на один заданный (когда модуль включён).
 * Применяется в {@code PlayerEntityRendererMixin} через подмену {@code state.skinTextures.texture()}.
 */
public class AllSkins extends Module {
    public static final Identifier SKIN = LinkenVisuals.id("textures/allskins/rudi.png");

    public AllSkins() {
        super("AllSkins", Category.Visuals);
    }

    /** Identifier скина-оверрайда, если модуль включён; иначе {@code null}. */
    public static Identifier skinOverride() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return null;
        }
        AllSkins module = instance.getModuleManager().getModule(AllSkins.class);
        return (module != null && module.isToggle()) ? SKIN : null;
    }
}
