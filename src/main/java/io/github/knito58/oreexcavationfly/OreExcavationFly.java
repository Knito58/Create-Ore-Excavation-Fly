package io.github.knito58.oreexcavationfly;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OreExcavationFly implements ModInitializer {
    public static final String MOD_ID = "oreexcavationfly";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Config.load();
        Veins.load();
        ModBlocks.register();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(FinderReading.TYPE, FinderReading.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(VeinFinderItem::tickHud);
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
            .modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .register(tab -> {
                tab.accept(ModBlocks.DRILL);
                tab.accept(ModBlocks.VEIN_FINDER);
            });
        LOGGER.info("Create: Ore Excavation Fly {} loaded", net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
    }
}
