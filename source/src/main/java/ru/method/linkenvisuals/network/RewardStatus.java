package ru.method.linkenvisuals.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Результат выдачи награды, которым сервер отвечает в {@link RewardResultS2CPayload}.
 * По проводу кодируется как VarInt (порядковый номер).
 */
public enum RewardStatus {
    /** Награда успешно выдана. */
    SUCCESS,
    /** Игрок уже получал награду ранее. */
    ALREADY_GET,
    /** Этот HWID уже забирал награду под другим ником. */
    HWID_ERROR;

    private static final RewardStatus[] VALUES = values();

    public static RewardStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HWID_ERROR;
    }

    /** Кодек статуса: VarInt(ordinal). */
    public static final PacketCodec<ByteBuf, RewardStatus> PACKET_CODEC =
            PacketCodecs.VAR_INT.xmap(RewardStatus::byId, RewardStatus::ordinal);
}
