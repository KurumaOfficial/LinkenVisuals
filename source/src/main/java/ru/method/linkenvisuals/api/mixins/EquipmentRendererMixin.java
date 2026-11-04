package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.method.linkenvisuals.utils.render.ArmorRenderStateHolder;
import ru.method.linkenvisuals.utils.render.HitColorRenderHelper;

@Mixin(net.minecraft.client.render.entity.equipment.EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {

    @WrapOperation(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
            ),
            require = 0
    )
    private void linkenvisuals$tintArmorHitColor(RenderCommandQueue queue, Model<?> model, Object state,
                                                 MatrixStack matrices, RenderLayer layer, int light, int overlay,
                                                 int color, Sprite sprite, int outlineColor,
                                                 ModelCommandRenderer.CrumblingOverlayCommand crumbling,
                                                 Operation<Void> original) {
        LivingEntityRenderState les = ArmorRenderStateHolder.get();
        int finalColor = color;
        int finalOverlay = overlay;
        if (les != null) {
            int tinted = HitColorRenderHelper.tint(les, color, true);
            if (tinted != color) {
                finalColor = tinted;
                finalOverlay = OverlayTexture.DEFAULT_UV;
            }
        }
        original.call(queue, model, state, matrices, layer, light, finalOverlay, finalColor, sprite, outlineColor, crumbling);
    }
}
