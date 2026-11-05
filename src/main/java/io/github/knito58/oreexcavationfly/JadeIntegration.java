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

/** Jade tooltip for the drill: vein, speed, time per item and progress. Only loaded when Jade is present. */
public class JadeIntegration implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        OreExcavationFly.LOGGER.info("Jade plugin: registering server data provider");
        registration.registerBlockDataProvider(DrillProvider.INSTANCE, DrillBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        OreExcavationFly.LOGGER.info("Jade plugin: registering drill tooltip");
        registration.registerBlockComponent(DrillProvider.INSTANCE, DrillBlock.class);
    }

    public enum DrillProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private boolean loggedOnce;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!loggedOnce) {
                loggedOnce = true;
                OreExcavationFly.LOGGER.info("Jade plugin: drill tooltip called, server data keys: {}", data.keySet());
            }
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
            return Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, "drill");
        }
    }
}
