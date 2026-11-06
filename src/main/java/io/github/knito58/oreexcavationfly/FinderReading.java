package io.github.knito58.oreexcavationfly;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * One Vein Finder reading, sent from server to client for the radar HUD.
 * Ranges are in chunks. Each dot is a vein: angle is the (already blurred) world direction
 * atan2(dz, dx), dist in chunks (already rounded when far), sharp 0 = far, 1 = type range, 2 = exact range.
 */
public record FinderReading(int tier, boolean powered, int range, int typeRange, int exactRange, Component text, List<Dot> dots)
        implements CustomPacketPayload {
    public record Dot(float angle, float dist, int sharp) {}

    public static final Type<FinderReading> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, "finder_reading"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinderReading> CODEC = CustomPacketPayload.codec(FinderReading::write, FinderReading::read);

    /** A finder without fuel: dark radar, just the message. */
    public static FinderReading off(int tier, Component text) {
        return new FinderReading(tier, false, 1, 1, 0, text, List.of());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(tier);
        buf.writeBoolean(powered);
        buf.writeVarInt(range);
        buf.writeVarInt(typeRange);
        buf.writeVarInt(exactRange);
        ComponentSerialization.STREAM_CODEC.encode(buf, text);
        buf.writeVarInt(dots.size());
        for (Dot d : dots) {
            buf.writeFloat(d.angle());
            buf.writeFloat(d.dist());
            buf.writeVarInt(d.sharp());
        }
    }

    private static FinderReading read(RegistryFriendlyByteBuf buf) {
        int tier = buf.readVarInt();
        boolean powered = buf.readBoolean();
        int range = buf.readVarInt(), typeRange = buf.readVarInt(), exactRange = buf.readVarInt();
        Component text = ComponentSerialization.STREAM_CODEC.decode(buf);
        int n = buf.readVarInt();
        List<Dot> dots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) dots.add(new Dot(buf.readFloat(), buf.readFloat(), buf.readVarInt()));
        return new FinderReading(tier, powered, range, typeRange, exactRange, text, dots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
