package io.github.knito58.oreexcavationfly;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Prospecting radar. While held (and fuelled with coal) it shows every vein in range as a
 * dot on the radar HUD: far dots are blurred, near ones sharp. The nearest vein is described
 * in words: far only a direction, closer the vein type, very close the exact chunk.
 * Right-click prints that line in the action bar.
 * Four tiers: Copper, Iron, Brass, Diamond. Each sees farther and sharper, flickers less and is
 * fooled by fewer false echoes (scattered ordinary ore that looks like a far vein and fades out
 * when approached). False echoes are placed from the world seed, so they stay put.
 * All blurring happens here on the server, so the client never learns more than it shows.
 */
public class VeinFinderItem extends Item {
    private static final String[] COMPASS = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
    /** How often (ticks) the radar of a player holding a finder is refreshed. */
    private static final int HUD_INTERVAL = 10;
    /** Fuel is burnt in steps of this many ticks, to keep item updates rare. */
    private static final int BURN_STEP = 20;
    /** Per tier (Copper, Iron, Brass, Diamond): range multiplier, direction blur multiplier, false echoes per vein cell. */
    private static final double[] RANGE_FACTOR = {0.75, 1.0, 1.5, 2.0};
    private static final double[] BLUR_FACTOR = {1.3, 1.0, 0.6, 0.3};
    private static final double[] ECHOES = {3.0, 1.5, 0.6, 0.0};
    private static final int MAX_ECHOES_PER_CELL = 3;

    /** 0 = Copper, 1 = Iron, 2 = Brass, 3 = Diamond. */
    public final int tier;

    public VeinFinderItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    // --- fuel -----------------------------------------------------------------

    /** Ticks of scanning left from the coal that is burning now. */
    public static int fuel(ItemStack stack) {
        return stack.getOrDefault(ModBlocks.FINDER_FUEL, 0);
    }

    private static int ticksPerCoal() {
        return Config.get().finderSecondsPerCoal * 20;
    }

    /** Burns fuel; when empty, eats one coal or charcoal from the inventory. Creative is free. */
    private static boolean burn(ServerPlayer player, ItemStack stack) {
        if (player.getAbilities().instabuild) return true;
        int fuel = fuel(stack);
        if (fuel < BURN_STEP) {
            fuel = eatCoal(player.getInventory()) ? ticksPerCoal() : 0;
            stack.set(ModBlocks.FINDER_FUEL, fuel);
            return fuel > 0;
        }
        stack.set(ModBlocks.FINDER_FUEL, fuel - BURN_STEP);
        return true;
    }

    private static boolean eatCoal(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(ItemTags.COALS)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean powered(Player player, ItemStack stack) {
        return player.getAbilities().instabuild || fuel(stack) > 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(13, Math.round(13f * fuel(stack) / ticksPerCoal()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x55FF55;
    }

    /** The fuel changes every second; without this the hand would bob each time. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    // --- use and HUD ---------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            ItemStack stack = player.getItemInHand(hand);
            boolean on = powered(player, stack) || (player instanceof ServerPlayer sp && burn(sp, stack));
            player.sendOverlayMessage(on ? scan(server, player, tier).text() : Component.translatable("message.oreexcavationfly.finder.no_fuel"));
            player.getCooldowns().addCooldown(stack, Config.get().finderCooldownTicks);
        }
        return InteractionResult.SUCCESS;
    }

    /** The finder in the player's hands (main hand first), or empty. */
    public static ItemStack held(Player player) {
        if (player.getMainHandItem().getItem() instanceof VeinFinderItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof VeinFinderItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** Called every server tick: burn fuel and refresh the radar of every player holding a finder. */
    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack stack = held(player);
            if (stack.isEmpty()) continue;
            boolean on = now % BURN_STEP == 0 ? burn(player, stack) : powered(player, stack);
            if (now % HUD_INTERVAL != 0 || !(player.level() instanceof ServerLevel level)
                    || !ServerPlayNetworking.canSend(player, FinderReading.TYPE)) continue;
            int tier = ((VeinFinderItem) stack.getItem()).tier;
            ServerPlayNetworking.send(player, on ? scan(level, player, tier)
                : FinderReading.off(tier, Component.translatable("message.oreexcavationfly.finder.no_fuel")));
        }
    }

    static FinderReading scan(ServerLevel server, Player player, int tier) {
        Config cfg = Config.get();
        int range = (int) Math.round(cfg.finderRangeChunks * RANGE_FACTOR[tier]);
        int typeRange = (int) Math.round(cfg.finderTypeRangeChunks * RANGE_FACTOR[tier]);
        int exactRange = (int) Math.round(cfg.finderExactRangeChunks * RANGE_FACTOR[tier]);
        int pcx = player.blockPosition().getX() >> 4;
        int pcz = player.blockPosition().getZ() >> 4;

        List<FinderReading.Dot> dots = new ArrayList<>();
        Veins.Located best = null;
        double bestDist = Double.MAX_VALUE, bestAngle = 0;
        for (Veins.Located l : Veins.around(server.getSeed(), pcx, pcz, range)) {
            double d = Math.hypot(l.chunkX() - pcx, l.chunkZ() - pcz);
            if (d > range) continue;
            // Radar position: from the player's exact position to the vein chunk's centre, in chunks.
            double dx = l.chunkX() + 0.5 - player.getX() / 16.0;
            double dz = l.chunkZ() + 0.5 - player.getZ() / 16.0;
            double angle = Math.atan2(dz, dx) + blur(l.chunkX(), l.chunkZ(), d, range, exactRange) * BLUR_FACTOR[tier];
            double dist = Math.hypot(dx, dz);
            int sharp = d <= exactRange ? 2 : d <= typeRange ? 1 : 0;
            if (sharp == 0) dist = Math.max(typeRange, Math.round(dist / 4.0) * 4.0); // far: rough distance only
            dots.add(new FinderReading.Dot((float) angle, (float) dist, sharp));
            if (d < bestDist) {
                bestDist = d;
                best = l;
                bestAngle = angle;
            }
        }

        // False echoes only show while far (outside the type ring); the nearest one may fool the text too.
        for (int[] e : echoes(server.getSeed(), pcx, pcz, range, ECHOES[tier])) {
            double d = Math.hypot(e[0] - pcx, e[1] - pcz);
            if (d > range || d <= typeRange) continue;
            double dx = e[0] + 0.5 - player.getX() / 16.0;
            double dz = e[1] + 0.5 - player.getZ() / 16.0;
            double angle = Math.atan2(dz, dx) + blur(e[0], e[1], d, range, exactRange) * BLUR_FACTOR[tier];
            dots.add(new FinderReading.Dot((float) angle, (float) Math.max(typeRange, Math.round(Math.hypot(dx, dz) / 4.0) * 4.0), 0));
            if (d < bestDist && bestDist > typeRange) { // a real vein inside the type ring always wins
                bestDist = d;
                best = new Veins.Located(null, e[0], e[1]);
                bestAngle = angle;
            }
        }

        Component text;
        if (best == null) {
            text = Component.translatable("message.oreexcavationfly.finder.nothing");
        } else if (bestDist < 0.5) {
            text = Component.translatable("message.oreexcavationfly.finder.here", vein(best));
        } else {
            Component dir = Component.translatable("direction.oreexcavationfly." + compass(bestAngle));
            if (bestDist <= exactRange) {
                text = Component.translatable("message.oreexcavationfly.finder.exact", vein(best), best.chunkX(), best.chunkZ(), dir);
            } else if (bestDist <= typeRange) {
                text = Component.translatable("message.oreexcavationfly.finder.type", vein(best), dir);
            } else {
                boolean medium = bestDist < range * 0.6;
                text = Component.translatable("message.oreexcavationfly.finder.signal." + (medium ? "medium" : "weak"), dir);
            }
        }
        return new FinderReading(tier, true, range, typeRange, exactRange, text, dots);
    }

    private static Component vein(Veins.Located l) {
        return l.vein().displayName();
    }

    /** Fixed per-spot direction error that grows with distance; zero within the exact range. */
    private static double blur(int chunkX, int chunkZ, double dist, int range, int exactRange) {
        if (dist <= exactRange) return 0;
        double t = Math.min(1.0, dist / range);
        Random r = new Random(chunkX * 31L + chunkZ * 17L);
        return (r.nextDouble() * 2 - 1) * t * Math.toRadians(60);
    }

    /**
     * False echo chunks around the player: up to MAX_ECHOES_PER_CELL per vein cell, each kept with
     * a fixed chance so that a cell has perCell echoes on average. Lower tiers keep more of the same
     * echoes, so upgrading only removes echoes, it never moves them.
     */
    private static List<int[]> echoes(long seed, int chunkX, int chunkZ, int range, double perCell) {
        List<int[]> out = new ArrayList<>();
        if (perCell <= 0) return out;
        double keep = perCell / MAX_ECHOES_PER_CELL;
        for (int cx = Math.floorDiv(chunkX - range, Veins.CELL); cx <= Math.floorDiv(chunkX + range, Veins.CELL); cx++) {
            for (int cz = Math.floorDiv(chunkZ - range, Veins.CELL); cz <= Math.floorDiv(chunkZ + range, Veins.CELL); cz++) {
                Random r = new Random(seed ^ (cx * 73428767L) ^ (cz * 912931L) ^ 0xEC40EC40L);
                for (int i = 0; i < MAX_ECHOES_PER_CELL; i++) {
                    int x = cx * Veins.CELL + r.nextInt(Veins.CELL), z = cz * Veins.CELL + r.nextInt(Veins.CELL);
                    if (r.nextDouble() < keep && Veins.at(seed, x, z) == null) out.add(new int[] {x, z});
                }
            }
        }
        return out;
    }

    private static String compass(double angle) {
        int idx = (int) Math.floorMod(Math.round(angle / (Math.PI / 4)), 8L);
        return COMPASS[idx];
    }
}
