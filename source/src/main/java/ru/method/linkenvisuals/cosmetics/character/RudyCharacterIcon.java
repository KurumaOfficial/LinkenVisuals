package ru.method.linkenvisuals.cosmetics.character;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.RotationAxis;

public final class RudyCharacterIcon {
    private static PlayerEntityModel model;
    private RudyCharacterIcon() {}
    public static void reset() { model = null; }

    public static void render(DrawContext context, int x, int y, int size) {
        if (!RudyCharacter.available()) return;
        if (model == null) model = new PlayerEntityModel(MinecraftClient.getInstance().getLoadedEntityModels()
                .getModelPart(EntityModelLayers.PLAYER), false);
        model.resetTransforms();
        RudyCharacter.pose(model, new PlayerEntityRenderState());
        net.minecraft.client.util.math.MatrixStack matrices = new net.minecraft.client.util.math.MatrixStack();
        matrices.push();
        try {
            matrices.translate(x + size * .5f, y + size * .2f, 100);
            matrices.scale(size * .48f, size * .48f, size * .48f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(155));
            var buffers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            RudyCharacter.render(model, matrices, buffers, 0xF000F0, OverlayTexture.DEFAULT_UV,
                    0xFFFFFFFF, false, false);
            buffers.draw();
        } finally { matrices.pop(); }
    }
}
