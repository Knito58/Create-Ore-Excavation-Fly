package io.github.knito58.oreexcavationfly;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One Vein Finder reading, sent from server to client for the radar HUD.
 * angle is the (already blurred) world direction atan2(dz, dx); dist is in chunks (already rounded when far).
 */
public record FinderReading(int tier, float angle, float dist, int range, int typeRange, int exactRange, Component text)
        implements CustomPacketPayload {
    public static final int NOTHING = 0, WEAK = 1, MEDIUM = 2, TYPE_KNOWN = 3, EXACT = 4, HERE = 5;

    public static final Type<FinderReading> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, "finder_reading"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinderReading> CODEC = CustomPacketPayload.codec(FinderReading::write, FinderReading::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(tier);
        buf.writeFloat(angle);
        buf.writeFloat(dist);
        buf.writeVarInt(range);
        buf.writeVarInt(typeRange);
        buf.writeVarInt(exactRange);
        ComponentSerialization.STREAM_CODEC.encode(buf, text);
    }

    private static FinderReading read(RegistryFriendlyByteBuf buf) {
        return new FinderReading(buf.readVarInt(), buf.readFloat(), buf.readFloat(),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
