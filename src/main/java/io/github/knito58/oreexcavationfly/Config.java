package io.github.knito58.oreexcavationfly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON config at config/oreexcavationfly.json.
 * Values are clamped on load; unknown or missing keys fall back to defaults.
 */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- veins ---
    /** Base amount for finite veins (multiplied by each vein's amount multiplier). */
    public int finiteAmountBase = 1000;
    /** Veins are infinite unless a vein says otherwise. */
    public boolean defaultInfinite = true;
    /** Max drills per vein, 0 = unlimited. */
    public int maxDrillsPerVein = 0;

    // --- vein finder (prospecting radar) ---
    /** Maximum scan range in chunks. */
    public int finderRangeChunks = 16;
    /** Within this many chunks the vein type is revealed. */
    public int finderTypeRangeChunks = 8;
    /** Within this many chunks the exact vein chunk is revealed. */
    public int finderExactRangeChunks = 3;
    /** Use cooldown in ticks. */
    public int finderCooldownTicks = 100;

    private void clamp() {
        finiteAmountBase = Math.max(1, finiteAmountBase);
        maxDrillsPerVein = Math.max(0, Math.min(64, maxDrillsPerVein));
        finderRangeChunks = Math.max(1, Math.min(64, finderRangeChunks));
        finderTypeRangeChunks = Math.max(1, Math.min(finderRangeChunks, finderTypeRangeChunks));
        finderExactRangeChunks = Math.max(0, Math.min(finderTypeRangeChunks, finderExactRangeChunks));
        finderCooldownTicks = Math.max(10, Math.min(1000, finderCooldownTicks));
    }

    private static Config instance = new Config();

    public static Config get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(OreExcavationFly.MOD_ID + ".json");
    }

    public static void load() {
        Path file = file();
        Config c = new Config();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Config read = GSON.fromJson(r, Config.class);
                if (read != null) c = read;
            } catch (Exception e) {
                OreExcavationFly.LOGGER.warn("Could not read {}, using defaults", file, e);
            }
        }
        c.clamp();
        instance = c;
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            OreExcavationFly.LOGGER.warn("Could not write config", e);
        }
    }
}
