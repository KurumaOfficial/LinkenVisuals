package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.CustomVitals;

/**
 * 1.21.11 moved the vanilla experience bar out of {@code InGameHud} into
 * {@link ExperienceBar}. This mixin keeps the donor behaviour: the bar is
 * cancelled while the custom vitals HUD is active and shifted up by 2px
 * otherwise.
 */
@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {

    @Unique
    private static boolean linkenvisuals$customVitalsActive() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return false;
        }
        CustomVitals module = instance.getModuleManager().getModule(CustomVitals.class);
        return module != null && module.isToggled();
    }

    @Inject(method = "renderBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void linkenvisuals$renderBarHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (linkenvisuals$customVitalsActive()) {
            ci.cancel();
            return;
        }
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0F, -2.0F);
    }

    @Inject(method = "renderBar", at = @At("RETURN"), require = 0)
    private void linkenvisuals$renderBarReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (linkenvisuals$customVitalsActive()) {
            return;
        }
        context.getMatrices().popMatrix();
    }

    @Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true, require = 0)
    private void linkenvisuals$renderAddons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (linkenvisuals$customVitalsActive()) {
            ci.cancel();
        }
    }
}
