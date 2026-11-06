package io.github.knito58.oreexcavationfly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Deterministic vein placement. A vein is a whole chunk. The world is divided into cells of
 * CELL chunks; each cell may hold one vein at a pseudo-random chunk, picked from the weighted
 * list below using only the world seed and the cell coordinates. Nothing is stored in the world.
 */
public final class Veins {
    private Veins() {}

    /** Cell edge length in chunks. */
    public static final int CELL = 10;
    /** Chance (0..1) that a cell holds a vein. */
    public static final float CELL_CHANCE = 0.7f;

    /**
     * One vein type. Loaded from config/oreexcavationfly/veins/*.json.
     *
     * @param id        short unique id
     * @param name      optional display name; if empty the lang key "vein.oreexcavationfly.<id>" is used
     * @param item      registry id of the item the drill produces
     * @param weight    relative chance to be picked
     * @param ticksAt32 ticks per item at 32 RPM
     */
    public record Vein(String id, String name, String item, int weight, int ticksAt32) {
        /** The item this vein yields, or null if the id is malformed or not registered. */
        public Item itemOrNull() {
            Identifier id = item == null ? null : Identifier.tryParse(item);
            return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
        }

        public Component displayName() {
            return name != null && !name.isBlank() ? Component.literal(name) : Component.translatable("vein.oreexcavationfly." + id);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static List<Vein> defaults() {
        return List.of(
            new Vein("iron", null, "minecraft:raw_iron", 30, 100),
            new Vein("copper", null, "minecraft:raw_copper", 30, 80),
            new Vein("gold", null, "minecraft:raw_gold", 12, 140),
            new Vein("zinc", null, "create:raw_zinc", 24, 90),
            new Vein("coal", null, "minecraft:coal", 30, 60),
            new Vein("redstone", null, "minecraft:redstone", 14, 100),
            new Vein("lapis", null, "minecraft:lapis_lazuli", 10, 100),
            new Vein("diamond", null, "minecraft:diamond", 4, 400),
            new Vein("emerald", null, "minecraft:emerald", 3, 400)
        );
    }

    /** The active vein list. Replaced by {@link #load()}. */
    public static volatile List<Vein> ALL = defaults();

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve(OreExcavationFly.MOD_ID).resolve("veins");
    }

    /** Reads the vein files; writes the default files first if the folder does not exist yet. */
    public static void load() {
        Path dir = dir();
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                for (Vein v : defaults()) {
                    try (Writer w = Files.newBufferedWriter(dir.resolve(v.id() + ".json"))) {
                        GSON.toJson(v, w);
                    }
                }
            }
        } catch (IOException e) {
            OreExcavationFly.LOGGER.warn("Could not create default vein files in {}", dir, e);
            return;
        }
        List<Vein> loaded = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.toString().endsWith(".json")).sorted().forEach(f -> {
                try (Reader r = Files.newBufferedReader(f)) {
                    Vein v = GSON.fromJson(r, Vein.class);
                    if (v == null || v.id() == null || v.item() == null) {
                        OreExcavationFly.LOGGER.warn("Skipping {}: needs at least id and item", f.getFileName());
                    } else if (v.weight() <= 0 || v.ticksAt32() <= 0) {
                        OreExcavationFly.LOGGER.warn("Skipping {}: weight and ticksAt32 must be above 0", f.getFileName());
                    } else {
                        loaded.add(v);
                    }
                } catch (Exception e) {
                    OreExcavationFly.LOGGER.warn("Skipping {}: {}", f.getFileName(), e.toString());
                }
            });
        } catch (IOException e) {
            OreExcavationFly.LOGGER.warn("Could not read vein files from {}", dir, e);
            return;
        }
        if (loaded.isEmpty()) {
            OreExcavationFly.LOGGER.warn("No valid vein files in {}, using built-in defaults", dir);
            ALL = defaults();
        } else {
            ALL = List.copyOf(loaded);
            OreExcavationFly.LOGGER.info("Loaded {} veins", loaded.size());
        }
    }

    /**
     * Drops veins whose item does not exist (typo, or the mod is missing). Called when the server
     * starts, because other mods may register their items after our own init.
     */
    public static void dropUnknownItems() {
        List<Vein> ok = new ArrayList<>();
        for (Vein v : ALL) {
            if (v.itemOrNull() != null) ok.add(v);
            else OreExcavationFly.LOGGER.warn("Vein '{}' disabled: unknown item '{}'", v.id(), v.item());
        }
        if (ok.size() == ALL.size()) return;
        ALL = ok.isEmpty() ? defaults() : List.copyOf(ok);
    }

    /** A vein located at a chunk. */
    public record Located(Vein vein, int chunkX, int chunkZ) {}

    private static Random rng(long seed, int cellX, int cellZ) {
        long h = seed ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L) ^ 0x5DEECE66DL;
        Random r = new Random(h);
        r.nextInt(); // warm up
        return r;
    }

    /** The vein in the given cell, or null. */
    public static Located inCell(long seed, int cellX, int cellZ) {
        Random r = rng(seed, cellX, cellZ);
        if (r.nextFloat() >= CELL_CHANCE) return null;
        int x = cellX * CELL + r.nextInt(CELL);
        int z = cellZ * CELL + r.nextInt(CELL);
        List<Vein> all = ALL;
        int total = 0;
        for (Vein v : all) total += v.weight();
        int pick = r.nextInt(total);
        for (Vein v : all) {
            pick -= v.weight();
            if (pick < 0) return new Located(v, x, z);
        }
        return null;
    }

    /** The vein inside this exact chunk, or null. */
    public static Vein at(long seed, int chunkX, int chunkZ) {
        Located l = inCell(seed, Math.floorDiv(chunkX, CELL), Math.floorDiv(chunkZ, CELL));
        return l != null && l.chunkX() == chunkX && l.chunkZ() == chunkZ ? l.vein() : null;
    }

    /** All veins whose chunk is within range chunks (square) of the given chunk. */
    public static List<Located> around(long seed, int chunkX, int chunkZ, int range) {
        java.util.ArrayList<Located> out = new java.util.ArrayList<>();
        int minCx = Math.floorDiv(chunkX - range, CELL), maxCx = Math.floorDiv(chunkX + range, CELL);
        int minCz = Math.floorDiv(chunkZ - range, CELL), maxCz = Math.floorDiv(chunkZ + range, CELL);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Located l = inCell(seed, cx, cz);
                if (l != null && Math.abs(l.chunkX() - chunkX) <= range && Math.abs(l.chunkZ() - chunkZ) <= range) {
                    out.add(l);
                }
            }
        }
        return out;
    }
}
