package ru.method.linkenvisuals.utils.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import ru.method.linkenvisuals.LinkenVisuals;

@Environment(EnvType.CLIENT)
public class NewYearHatFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final ItemStack NEW_YEAR_HAT_STACK = createHatStack();
    private final ItemRenderState hatState = new ItemRenderState();

    public NewYearHatFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    private static ItemStack createHatStack() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponentTypes.ITEM_MODEL, LinkenVisuals.id("noviygod/hat"));
        return stack;
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.spectator || state.invisibleToPlayer) return;

        matrices.push();
        try {
            PlayerEntityModel model = this.getContextModel();
            model.getRootPart().applyTransform(matrices);
            model.getHead().applyTransform(matrices);
            HeadFeatureRenderer.translate(matrices, HeadFeatureRenderer.HeadTransformation.DEFAULT);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getItemModelManager() != null) {
                client.getItemModelManager().update(hatState, NEW_YEAR_HAT_STACK, ItemDisplayContext.HEAD, null, null, 0);
                hatState.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, 0);
            }
        } finally {
            matrices.pop();
        }
    }
}
