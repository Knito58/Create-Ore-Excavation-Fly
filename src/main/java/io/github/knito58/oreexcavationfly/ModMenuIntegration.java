package io.github.knito58.oreexcavationfly;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/** Mod Menu entry. The config screen needs Cloth Config; without it there is simply no screen. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return parent -> {
            Config c = Config.get();
            ConfigBuilder b = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.oreexcavationfly.title"))
                .setSavingRunnable(Config::save);
            ConfigEntryBuilder e = b.entryBuilder();

            ConfigCategory finder = b.getOrCreateCategory(Component.translatable("config.oreexcavationfly.finder"));
            finder.addEntry(e.startIntField(Component.translatable("config.oreexcavationfly.finderRangeChunks"), c.finderRangeChunks)
                .setDefaultValue(16).setMin(1).setMax(64).setSaveConsumer(v -> c.finderRangeChunks = v).build());
            finder.addEntry(e.startIntField(Component.translatable("config.oreexcavationfly.finderTypeRangeChunks"), c.finderTypeRangeChunks)
                .setDefaultValue(8).setMin(1).setMax(64).setSaveConsumer(v -> c.finderTypeRangeChunks = v).build());
            finder.addEntry(e.startIntField(Component.translatable("config.oreexcavationfly.finderExactRangeChunks"), c.finderExactRangeChunks)
                .setDefaultValue(3).setMin(0).setMax(64).setSaveConsumer(v -> c.finderExactRangeChunks = v).build());
            finder.addEntry(e.startIntField(Component.translatable("config.oreexcavationfly.finderCooldownTicks"), c.finderCooldownTicks)
                .setDefaultValue(100).setMin(10).setMax(1000).setSaveConsumer(v -> c.finderCooldownTicks = v).build());
            finder.addEntry(e.startIntField(Component.translatable("config.oreexcavationfly.finderSecondsPerCoal"), c.finderSecondsPerCoal)
                .setDefaultValue(240).setMin(30).setMax(1200).setSaveConsumer(v -> c.finderSecondsPerCoal = v).build());
            return b.build();
        };
    }
}
