package com.invokermod;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент: [сфера1, сфера2, кд0..кд5 в тиках]. */
public record SyncPayload(List<Integer> data) implements CustomPayload {
    public static final CustomPayload.Id<SyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(InvokerMod.MOD_ID, "sync"));
    public static final PacketCodec<ByteBuf, SyncPayload> CODEC =
            PacketCodecs.INTEGER.collect(PacketCodecs.toList()).xmap(SyncPayload::new, SyncPayload::data);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
