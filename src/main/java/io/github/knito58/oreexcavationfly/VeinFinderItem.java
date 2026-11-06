package io.github.knito58.oreexcavationfly;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * Prospecting finder. Far away it only gives a fuzzy direction and a signal strength,
 * closer it names the vein type, and very close it gives the exact chunk.
 * Right-click prints the reading in the action bar; while held, the radar HUD shows it.
 * All fuzzing happens here on the server, so the client never learns more than it shows.
 */
public class VeinFinderItem extends Item {
    private static final String[] COMPASS = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
    /** How often (ticks) the radar HUD of a player holding the finder is refreshed. */
    private static final int HUD_INTERVAL = 10;

    public VeinFinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            ItemStack stack = player.getItemInHand(hand);
            player.sendOverlayMessage(scan(server, player).text());
            player.getCooldowns().addCooldown(stack, Config.get().finderCooldownTicks);
        }
        return InteractionResult.SUCCESS;
    }

    /** Called every server tick: refresh the radar of every player holding a finder. */
    public static void tickHud(MinecraftServer server) {
        if (server.getTickCount() % HUD_INTERVAL != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean held = player.getMainHandItem().is(ModBlocks.VEIN_FINDER) || player.getOffhandItem().is(ModBlocks.VEIN_FINDER);
            if (held && player.level() instanceof ServerLevel level && ServerPlayNetworking.canSend(player, FinderReading.TYPE)) {
                ServerPlayNetworking.send(player, scan(level, player));
            }
        }
    }

    static FinderReading scan(ServerLevel server, Player player) {
        Config cfg = Config.get();
        int pcx = player.blockPosition().getX() >> 4;
        int pcz = player.blockPosition().getZ() >> 4;

        Veins.Located best = null;
        double bestDist = Double.MAX_VALUE;
        for (Veins.Located l : Veins.around(server.getSeed(), pcx, pcz, cfg.finderRangeChunks)) {
            double d = Math.hypot(l.chunkX() - pcx, l.chunkZ() - pcz);
            if (d < bestDist && d <= cfg.finderRangeChunks) {
                bestDist = d;
                best = l;
            }
        }
        if (best == null) {
            return reading(FinderReading.NOTHING, 0, 0, cfg, Component.translatable("message.oreexcavationfly.finder.nothing"));
        }
        if (bestDist < 0.5) {
            return reading(FinderReading.HERE, 0, 0, cfg, Component.translatable("message.oreexcavationfly.finder.here", vein(best)));
        }

        // Radar position: from the player's exact position to the vein chunk's centre, in chunks.
        double dx = best.chunkX() + 0.5 - player.getX() / 16.0;
        double dz = best.chunkZ() + 0.5 - player.getZ() / 16.0;
        double angle = Math.atan2(dz, dx) + blur(best, bestDist, cfg);
        double dist = Math.hypot(dx, dz);
        Component dir = Component.translatable("direction.oreexcavationfly." + compass(angle));

        if (bestDist <= cfg.finderExactRangeChunks) {
            return reading(FinderReading.EXACT, angle, dist, cfg,
                Component.translatable("message.oreexcavationfly.finder.exact", vein(best), best.chunkX(), best.chunkZ(), dir));
        }
        if (bestDist <= cfg.finderTypeRangeChunks) {
            return reading(FinderReading.TYPE_KNOWN, angle, dist, cfg,
                Component.translatable("message.oreexcavationfly.finder.type", vein(best), dir));
        }
        // Far: distance is only known roughly (rounded to 4 chunks).
        double rough = Math.max(cfg.finderTypeRangeChunks, Math.round(dist / 4.0) * 4.0);
        float strength = 1f - (float) (bestDist / cfg.finderRangeChunks);
        boolean medium = strength > 0.4f;
        return reading(medium ? FinderReading.MEDIUM : FinderReading.WEAK, angle, rough, cfg,
            Component.translatable("message.oreexcavationfly.finder.signal." + (medium ? "medium" : "weak"), dir));
    }

    private static FinderReading reading(int tier, double angle, double dist, Config cfg, Component text) {
        return new FinderReading(tier, (float) angle, (float) dist, cfg.finderRangeChunks, cfg.finderTypeRangeChunks, cfg.finderExactRangeChunks, text);
    }

    private static Component vein(Veins.Located l) {
        return l.vein().displayName();
    }

    /** Fixed per-vein direction error that grows with distance; zero within the exact range. */
    private static double blur(Veins.Located l, double dist, Config cfg) {
        if (dist <= cfg.finderExactRangeChunks) return 0;
        double t = Math.min(1.0, dist / cfg.finderRangeChunks);
        Random r = new Random(l.chunkX() * 31L + l.chunkZ() * 17L);
        return (r.nextDouble() * 2 - 1) * t * Math.toRadians(60);
    }

    private static String compass(double angle) {
        int idx = (int) Math.floorMod(Math.round(angle / (Math.PI / 4)), 8L);
        return COMPASS[idx];
    }
}
