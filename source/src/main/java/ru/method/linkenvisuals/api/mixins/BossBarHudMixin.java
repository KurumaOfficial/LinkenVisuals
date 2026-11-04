package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.hud.LinkHudStyle;
import ru.method.linkenvisuals.hud.impl.DynamicIsland;
import ru.method.linkenvisuals.modules.impl.render.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
    private static final float DYNAMIC_ISLAND_BOSSBAR_OFFSET = 19.0f;

    // Остров реально рисуется (и теснит боссбар) только вне Link-стиля — там он скрыт.
    private static boolean linkenvisuals$islandPushesBossBar() {
        if (LinkHudStyle.active()) return false;
        DynamicIsland dynamicIsland = LinkenVisuals.getInstance().getHudManager().getHudElement(DynamicIsland.class);
        return dynamicIsland != null && dynamicIsland.isToggled() && !dynamicIsland.closed();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void preRender(DrawContext context, CallbackInfo ci) {
        NoRender noRender = LinkenVisuals.getInstance().getModuleManager().getModule(NoRender.class);
        if (noRender != null && noRender.isToggled() && noRender.bossBar.getValue()) {
            ci.cancel();
            return;
        }

        if (linkenvisuals$islandPushesBossBar()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, DYNAMIC_ISLAND_BOSSBAR_OFFSET);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void postRender(DrawContext context, CallbackInfo ci) {
        if (linkenvisuals$islandPushesBossBar()) {
            context.getMatrices().popMatrix();
        }
    }
}
