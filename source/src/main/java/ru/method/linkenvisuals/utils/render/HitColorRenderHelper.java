package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.accessors.IHitColorRenderState;
import ru.method.linkenvisuals.modules.impl.render.HitColor;

public final class HitColorRenderHelper {

    private HitColorRenderHelper() {
    }

    public static int tint(LivingEntityRenderState state, int originalColor, boolean armorLayer) {
        if (state == null) return originalColor;

        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) return originalColor;

        HitColor module = instance.getModuleManager().getModule(HitColor.class);
        if (module == null || !module.isToggled()) return originalColor;
        if (armorLayer && !module.colorArmor.getValue()) return originalColor;
        // Красим ВСЕ живые сущности (включая мобов), а не только игроков.
        if (!(state instanceof IHitColorRenderState hitColorState)) {
            return originalColor;
        }

        float strength = MathHelper.clamp(hitColorState.linkenvisuals$getHitColorStrength(), 0.0F, 1.0F);
        if (strength <= 0.001F) return originalColor;

        float maxTint = MathHelper.clamp(module.opacity.getValue() / 255.0F, 0.0F, 1.0F);
        java.awt.Color chosenColor = module.color.getValue();
        float colorAlpha = chosenColor.getAlpha() / 255.0F;
        float factor = MathHelper.clamp(strength * maxTint * colorAlpha, 0.0F, 1.0F);
        if (factor <= 0.001F) return originalColor;

        int originalAlpha = originalColor >>> 24;
        if (originalAlpha == 0) return originalColor;

        int originalRed = (originalColor >> 16) & 0xFF;
        int originalGreen = (originalColor >> 8) & 0xFF;
        int originalBlue = originalColor & 0xFF;
        int red = MathHelper.clamp(Math.round(MathHelper.lerp(factor, originalRed, chosenColor.getRed())), 0, 255);
        int green = MathHelper.clamp(Math.round(MathHelper.lerp(factor, originalGreen, chosenColor.getGreen())), 0, 255);
        int blue = MathHelper.clamp(Math.round(MathHelper.lerp(factor, originalBlue, chosenColor.getBlue())), 0, 255);
        return (originalAlpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
