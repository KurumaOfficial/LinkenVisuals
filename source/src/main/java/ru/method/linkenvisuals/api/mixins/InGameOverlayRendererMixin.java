package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.render.FogBlur;
import ru.method.linkenvisuals.modules.impl.render.NoRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void renderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                          net.minecraft.client.texture.Sprite sprite, CallbackInfo ci) {
        NoRender noRender = LinkenVisuals.getInstance().getModuleManager().getModule(NoRender.class);
        if (noRender != null && noRender.isToggled() && noRender.fire.getValue()) {
            ci.cancel();
        }
    }

    // FogBlur больше НЕ вызывается отсюда: он шёл после отрисовки глифов/партиклов мода и мылил их.
    // Теперь вызывается напрямую из GameRendererMixin.onRenderWorld ДО мод-геометрии (см. тот файл).
}
