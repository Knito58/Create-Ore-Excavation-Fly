package io.github.knito58.oreexcavationfly;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;

/**
 * Prospecting finder. Far away it only gives a fuzzy direction and a signal strength,
 * closer it names the vein type, and very close it gives the exact chunk.
 */
public class VeinFinderItem extends Item {
    private static final String[] COMPASS = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};

    public VeinFinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            ItemStack stack = player.getItemInHand(hand);
            scan(server, player);
            player.getCooldowns().addCooldown(stack, Config.get().finderCooldownTicks);
        }
        return InteractionResult.SUCCESS;
    }

    private static void scan(ServerLevel server, Player player) {
        Config cfg = Config.get();
        int pcx = player.blockPosition().getX() >> 4;
        int pcz = player.blockPosition().getZ() >> 4;

        Veins.Located best = null;
        double bestDist = Double.MAX_VALUE;
        for (Veins.Located l : Veins.around(server.getSeed(), pcx, pcz, cfg.finderRangeChunks)) {
            double d = Math.hypot(l.chunkX() - pcx, l.chunkZ() - pcz);
            if (d < bestDist) {
                bestDist = d;
                best = l;
            }
        }
        if (best == null) {
            send(player, Component.translatable("message.oreexcavationfly.finder.nothing"));
            return;
        }

        if (bestDist < 0.5) {
            send(player, Component.translatable("message.oreexcavationfly.finder.here", vein(best)));
            return;
        }
        Component dir = Component.translatable("direction.oreexcavationfly." + direction(best, pcx, pcz, bestDist, cfg));
        if (bestDist <= cfg.finderExactRangeChunks) {
            send(player, Component.translatable("message.oreexcavationfly.finder.exact", vein(best), best.chunkX(), best.chunkZ(), dir));
        } else if (bestDist <= cfg.finderTypeRangeChunks) {
            send(player, Component.translatable("message.oreexcavationfly.finder.type", vein(best), dir));
        } else {
            float strength = 1f - (float) (bestDist / cfg.finderRangeChunks);
            String key = strength > 0.4f ? "medium" : "weak";
            send(player, Component.translatable("message.oreexcavationfly.finder.signal." + key, dir));
        }
    }

    private static Component vein(Veins.Located l) {
        return Component.translatable("vein.oreexcavationfly." + l.vein().id());
    }

    /** Compass direction with a fixed per-vein error that grows with distance. */
    private static String direction(Veins.Located l, int pcx, int pcz, double dist, Config cfg) {
        double angle = Math.atan2(l.chunkZ() - pcz, l.chunkX() - pcx);
        double blur = 0;
        if (dist > cfg.finderExactRangeChunks) {
            double t = Math.min(1.0, dist / cfg.finderRangeChunks);
            Random r = new Random(l.chunkX() * 31L + l.chunkZ() * 17L);
            blur = (r.nextDouble() * 2 - 1) * t * Math.toRadians(60);
        }
        double a = angle + blur;
        int idx = (int) Math.floorMod(Math.round(a / (Math.PI / 4)), 8L);
        return COMPASS[idx];
    }

    private static void send(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
