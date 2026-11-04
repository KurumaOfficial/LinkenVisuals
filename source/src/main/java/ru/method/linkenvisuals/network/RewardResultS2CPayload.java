package ru.method.linkenvisuals.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * S2C: СЕРВЕР → КЛИЕНТ. Ответ на {@link RewardClaimC2SPayload} — enum-результат выдачи.
 * Packet id: {@code linkenvisuals:reward_result}
 */
public record RewardResultS2CPayload(RewardStatus status) implements CustomPayload {

    public static final CustomPayload.Id<RewardResultS2CPayload> ID =
            new CustomPayload.Id<>(LinkenVisuals.id("reward_result"));

    public static final PacketCodec<RegistryByteBuf, RewardResultS2CPayload> CODEC = PacketCodec.tuple(
            RewardStatus.PACKET_CODEC, RewardResultS2CPayload::status,
            RewardResultS2CPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
