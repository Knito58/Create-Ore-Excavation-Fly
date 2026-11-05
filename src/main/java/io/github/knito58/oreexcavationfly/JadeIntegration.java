package io.github.knito58.oreexcavationfly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade tooltip for the drill: vein, speed, time per item and progress. Only loaded when Jade is present.
 * Since Minecraft 1.21.6 Jade wants the server data provider and the client tooltip provider as separate classes
 * (sharing one uid so the client asks the server for the data).
 */
public class JadeIntegration implements IWailaPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, "drill");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DrillData.INSTANCE, DrillBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(DrillTooltip.INSTANCE, DrillBlock.class);
    }

    /** Server side: collects the values the tooltip shows. */
    public enum DrillData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof DrillBlockEntity drill)) return;
            Veins.Vein vein = drill.veinHere();
            if (vein == null) return;
            float rpm = drill.rpm();
            data.putString("vein", vein.id());
            data.putFloat("rpm", rpm);
            data.putBoolean("stalled", drill.isStalled());
            data.putFloat("progress", drill.progressFraction());
            // ticks per item = ticksAt32 * 32 / rpm; 20 ticks per second
            data.putFloat("seconds", rpm > 0 ? vein.ticksAt32() * 32f / rpm / 20f : 0f);
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    /** Client side: draws the tooltip. */
    public enum DrillTooltip implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("vein")) {
                tooltip.add(Component.translatable("jade.oreexcavationfly.no_vein"));
                return;
            }
            tooltip.add(Component.translatable("jade.oreexcavationfly.vein",
                Component.translatable("vein.oreexcavationfly." + data.getStringOr("vein", ""))));
            float rpm = data.getFloatOr("rpm", 0f);
            tooltip.add(Component.translatable("jade.oreexcavationfly.speed", Math.round(rpm)));
            if (data.getBooleanOr("stalled", false)) {
                tooltip.add(Component.translatable("jade.oreexcavationfly.full"));
            } else if (rpm > 0) {
                tooltip.add(Component.translatable("jade.oreexcavationfly.rate",
                    String.format("%.1f", data.getFloatOr("seconds", 0f))));
                tooltip.add(Component.translatable("jade.oreexcavationfly.progress",
                    Math.round(data.getFloatOr("progress", 0f) * 100)));
            } else {
                tooltip.add(Component.translatable("jade.oreexcavationfly.idle"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
