package ru.method.linkenvisuals.cosmetics.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public class CosmeticAttachmentFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final CosmeticSlot[] ATTACHMENT_SLOTS = {
            CosmeticSlot.HAT,
            CosmeticSlot.HEAD,
            CosmeticSlot.BODY,
            CosmeticSlot.SHIELD,
            CosmeticSlot.WINGS
    };

    public CosmeticAttachmentFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance
    ) {
        if (state == null || state.spectator || state.invisibleToPlayer) {
            return;
        }

        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) {
            return;
        }

        CosmeticsService service = instance.getCosmeticsService();
        if (service == null) {
            return;
        }

        UUID uuid = resolveUuid(state);
        String name = resolveName(state);
        if (!service.shouldRender(uuid, name)) {
            return;
        }

        PlayerEntityModel playerModel = getContextModel();
        PlayerEntity player = resolvePlayer(state);
        boolean entityMoving = isEntityMoving(player);

        // РќР°СЂСѓС‚Рѕ-РЅР°РєР»РѕРЅ РєРѕСЂРїСѓСЃР° (РёР· LivingEntityRendererMixin) СЃРЅРёРјР°РµС‚СЃСЏ РґРѕ С„РёС‡ вЂ” РїРѕРІС‚РѕСЂСЏРµРј РµРіРѕ Р·РґРµСЃСЊ,
        // С‡С‚РѕР±С‹ С€Р°РїРєР°/РєСЂС‹Р»СЊСЏ СЃР»РµРґРѕРІР°Р»Рё Р·Р° РЅР°РєР»РѕРЅС‘РЅРЅРѕР№ РјРѕРґРµР»СЊСЋ, Р° РЅРµ РІРёСЃРµР»Рё РЅР° РёСЃС…РѕРґРЅРѕРј РјРµСЃС‚Рµ.
        boolean lean = CosmeticPosePatch.active;

        for (CosmeticSlot slot : ATTACHMENT_SLOTS) {
            if (!service.hasAttachment(slot, uuid, name)) {
                continue;
            }

            // РўРѕР»СЊРєРѕ С€Р»СЏРїС‹ РїСЂРёРїРѕРґРЅРёРјР°СЋС‚СЃСЏ РЅР°Рґ Р±СЂРѕРЅС‘Р№; РјР°СЃРєРё Рё РѕС‡РєРё РѕСЃС‚Р°СЋС‚СЃСЏ РЅР° Р»РёС†Рµ.
            float headLift = !ru.method.linkenvisuals.cosmetics.character.RudyCharacter.active(state) && slot == CosmeticSlot.HAT
                    && player != null
                    && !player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty()
                    ? 0.17f : 0f;
            if (lean) {
                matrices.push();
                applyLean(matrices);
            }
            CosmeticRenderHelper.renderAttachment(
                    matrices,
                    queue,
                    light,
                    playerModel,
                    service.resolveDefinition(slot, uuid, name),
                    slot,
                    limbDistance,
                    entityMoving,
                    headLift,
                    0f,
                    uuid,
                    name
            );
            if (lean) {
                matrices.pop();
            }
        }
    }

    /** РўРѕС‚ Р¶Рµ РЅР°РєР»РѕРЅ РєРѕСЂРїСѓСЃР°, С‡С‚Рѕ РїСЂРёРјРµРЅСЏРµС‚ РќР°СЂСѓС‚Рѕ-РїРѕР·Р° РІ LivingEntityRendererMixin РІРѕРєСЂСѓРі СЂРµРЅРґРµСЂР° РјРѕРґРµР»Рё. */
    private static void applyLean(MatrixStack matrices) {
        matrices.translate(0f, CosmeticPosePatch.pivotY, 0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(CosmeticPosePatch.leanRad));
        matrices.translate(0f, -CosmeticPosePatch.pivotY, 0f);
    }

    private static String resolveName(PlayerEntityRenderState state) {
        if (state == null) {
            return null;
        }
        if (state.playerName != null) {
            return state.playerName.getString();
        }
        PlayerEntity player = resolvePlayer(state);
        return player == null || player.getGameProfile() == null ? null : player.getGameProfile().name();
    }

    private static boolean isEntityMoving(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return Math.abs(player.getX() - player.lastX) > 0.0001
                || Math.abs(player.getZ() - player.lastZ) > 0.0001;
    }

    @Nullable
    private static PlayerEntity resolvePlayer(PlayerEntityRenderState state) {
        if (state == null) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && state.id == client.player.getId()) {
            return client.player;
        }
        if (client.world == null) {
            return null;
        }
        var entity = client.world.getEntityById(state.id);
        if (entity instanceof PlayerEntity player) {
            return player;
        }
        String stateName = state.playerName == null ? null : state.playerName.getString();
        if (stateName != null) {
            for (PlayerEntity player : client.world.getPlayers()) {
                String playerName = player.getGameProfile() == null ? null : player.getGameProfile().name();
                if (playerName != null && playerName.equalsIgnoreCase(stateName)) {
                    return player;
                }
            }
        }
        return null;
    }

    @Nullable
    private static UUID resolveUuid(PlayerEntityRenderState state) {
        if (state == null) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (CosmeticPreviewState.isActive() && client.player != null) {
            return client.player.getUuid();
        }
        PlayerEntity player = resolvePlayer(state);
        if (player != null) {
            return player.getUuid();
        }
        String stateName = state.playerName == null ? null : state.playerName.getString();
        if (client.getNetworkHandler() == null || stateName == null) {
            return null;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(stateName);
        return entry == null ? null : entry.getProfile().id();
    }
}
