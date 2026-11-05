package io.github.knito58.oreexcavationfly;

import java.util.List;
import java.util.Random;

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
     * @param id        short id, also used in the lang key "vein.oreexcavationfly.<id>"
     * @param item      registry id of the item the drill produces
     * @param weight    relative chance to be picked
     * @param ticksAt32 ticks per item at 32 RPM
     */
    public record Vein(String id, String item, int weight, int ticksAt32) {}

    public static final List<Vein> ALL = List.of(
        new Vein("iron", "minecraft:raw_iron", 30, 100),
        new Vein("copper", "minecraft:raw_copper", 30, 80),
        new Vein("gold", "minecraft:raw_gold", 12, 140),
        new Vein("coal", "minecraft:coal", 30, 60),
        new Vein("redstone", "minecraft:redstone", 14, 100),
        new Vein("lapis", "minecraft:lapis_lazuli", 10, 100),
        new Vein("diamond", "minecraft:diamond", 4, 400),
        new Vein("emerald", "minecraft:emerald", 3, 400)
    );

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
        int total = 0;
        for (Vein v : ALL) total += v.weight();
        int pick = r.nextInt(total);
        for (Vein v : ALL) {
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
