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
        ModBlocks.register();
        LOGGER.info("Create: Ore Excavation Fly loaded");
    }
}
