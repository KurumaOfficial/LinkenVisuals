package ru.method.linkenvisuals.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * C2S: КЛИЕНТ → СЕРВЕР. Запрос на выдачу награды — несёт HWID и ник игрока.
 * Packet id: {@code linkenvisuals:reward_claim}
 */
public record RewardClaimC2SPayload(String hwid, String nick) implements CustomPayload {

    public static final CustomPayload.Id<RewardClaimC2SPayload> ID =
            new CustomPayload.Id<>(LinkenVisuals.id("reward_claim"));

    public static final PacketCodec<RegistryByteBuf, RewardClaimC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RewardClaimC2SPayload::hwid,
            PacketCodecs.STRING, RewardClaimC2SPayload::nick,
            RewardClaimC2SPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
