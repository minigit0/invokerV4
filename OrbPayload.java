package com.invokermod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: игрок нажал клавишу сферы (0 огонь, 1 вода, 2 воздух). */
public record OrbPayload(int type) implements CustomPayload {
    public static final CustomPayload.Id<OrbPayload> ID =
            new CustomPayload.Id<>(Identifier.of(InvokerMod.MOD_ID, "orb"));
    public static final PacketCodec<ByteBuf, OrbPayload> CODEC =
            PacketCodecs.INTEGER.xmap(OrbPayload::new, OrbPayload::type);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
