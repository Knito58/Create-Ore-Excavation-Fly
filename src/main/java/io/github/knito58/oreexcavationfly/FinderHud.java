package io.github.knito58.oreexcavationfly;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * Client side: a round green radar in the top-left corner while a Vein Finder is held.
 * Up is where the player looks. Each light green dot is a possible vein: fuzzy when far,
 * sharp when close. A sweep line turns round and lights the dots up as it passes.
 * The inner rings mark the type and exact ranges. Copper, Iron and Brass finders flicker now and then.
 */
public class FinderHud implements ClientModInitializer {
    private static final int MARGIN = 6;
    /** Radar radius in GUI pixels, from the config (read every frame so changes show at once). */
    private static int R = 64;
    private static final int BG = 0xC0001A00, RING = 0x2AD02A, DOT = 0x90FF90, TEXT = 0xFF90FF90;
    /** Sweep period in ms, and flicker pause range in seconds (0 = never), per tier. */
    private static final int[] SWEEP_MS = {4500, 4000, 3000, 2000};
    private static final int[] FLICKER_MIN = {5, 10, 60, 0}, FLICKER_MAX = {15, 30, 180, 0};

    private static final Random RANDOM = new Random();
    private static FinderReading last;
    private static long lastTime, nextFlicker, flickerEnd;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FinderReading.TYPE, (reading, context) -> {
            last = reading;
            lastTime = System.currentTimeMillis();
        });
        // Goggle tooltip with the drill's stress impact, same as Create's own kinetic blocks.
        com.zurrtum.create.client.AllBlockEntityBehaviours.add(ModBlocks.DRILL_BE,
            com.zurrtum.create.client.foundation.blockEntity.behaviour.tooltip.KineticTooltipBehaviour<DrillBlockEntity>::new);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, "finder_radar"), FinderHud::extract);
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        FinderReading r = last;
        long now = System.currentTimeMillis();
        if (player == null || r == null || mc.gui.hud.isHidden() || now - lastTime > 1500 || VeinFinderItem.held(player).isEmpty()) return;

        R = Config.get().finderRadarRadius;
        int big = R >= 48 ? 1 : 0; // larger dots on a larger radar
        int cx = MARGIN + R, cy = MARGIN + R;
        disc(g, cx, cy, R + 1, 0xFF0A0A0A);
        disc(g, cx, cy, R, BG);
        if (!r.powered()) {
            ring(g, cx, cy, R, argb(RING, 0.3));
            g.text(mc.font, r.text(), MARGIN, MARGIN + 2 * R + 5, 0xFF808080, true);
            return;
        }
        ring(g, cx, cy, R, argb(RING, 1.0));
        ring(g, cx, cy, R * r.typeRange() / r.range(), argb(RING, 0.45));
        if (r.exactRange() > 0) ring(g, cx, cy, R * r.exactRange() / r.range(), argb(RING, 0.45));
        g.fill(cx - R + 1, cy, cx + R, cy + 1, argb(RING, 0.2));
        g.fill(cx, cy - R + 1, cx + 1, cy + R, argb(RING, 0.2));

        double yaw = Math.toRadians(player.getYRot());
        // North marker: world north is angle -PI/2 (towards -z).
        g.text(mc.font, "N", cx - 2 - (int) Math.round(Math.cos(-Math.PI / 2 - yaw) * (R - 6)),
            cy - 4 - (int) Math.round(Math.sin(-Math.PI / 2 - yaw) * (R - 6)), argb(RING, 0.8), false);

        int tier = Math.max(0, Math.min(3, r.tier()));
        boolean flicker = flicker(now, tier);
        double sweep = 2 * Math.PI * (now % SWEEP_MS[tier]) / SWEEP_MS[tier];
        if (!flicker) {
            for (int i = 0; i < 6; i++) line(g, cx, cy, sweep - i * 0.06, argb(RING, 0.7 - i * 0.11));
            for (FinderReading.Dot d : r.dots()) {
                double k = Math.min(1.0, d.dist() / r.range()) * (R - 2);
                int bx = cx - (int) Math.round(Math.cos(d.angle() - yaw) * k);
                int by = cy - (int) Math.round(Math.sin(d.angle() - yaw) * k);
                // How long ago the sweep passed this dot, as a fraction of a turn: fresh dots glow.
                double since = Math.floorMod((long) ((sweep - Math.atan2(by - cy, bx - cx)) * 1000), (long) (2000 * Math.PI)) / (2000 * Math.PI);
                double glow = 1.0 - 0.7 * since;
                switch (d.sharp()) {
                    case 0 -> disc(g, bx, by, 2 + big, argb(DOT, 0.45 * glow));
                    case 1 -> disc(g, bx, by, 1 + big, argb(DOT, 0.85 * glow));
                    default -> {
                        disc(g, bx, by, 1 + big, argb(DOT, glow));
                        g.fill(bx, by, bx + 1, by + 1, 0xFFFFFFFF);
                    }
                }
            }
        } else {
            for (int i = 0; i < R * R / 6; i++) { // snow
                int x = RANDOM.nextInt(2 * R) - R, y = RANDOM.nextInt(2 * R) - R;
                if (x * x + y * y < R * R) g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, argb(DOT, 0.3 + RANDOM.nextDouble() * 0.6));
            }
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF); // the player
        g.fill(cx, cy - 4, cx + 1, cy - 1, 0xFFFFFFFF);     // facing
        g.text(mc.font, r.text(), MARGIN, MARGIN + 2 * R + 5, TEXT, true);
    }

    /** Lower tiers flicker for a moment now and then. */
    private static boolean flicker(long now, int tier) {
        if (FLICKER_MAX[tier] <= 0) return false;
        if (now < flickerEnd) return true;
        if (nextFlicker == 0 || nextFlicker - now > FLICKER_MAX[tier] * 1000L) {
            nextFlicker = now + 1000L * (FLICKER_MIN[tier] + RANDOM.nextInt(FLICKER_MAX[tier] - FLICKER_MIN[tier] + 1));
        } else if (now >= nextFlicker) {
            flickerEnd = now + 300 + RANDOM.nextInt(450);
            nextFlicker = 0;
            return true;
        }
        return false;
    }

    private static int argb(int rgb, double a) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | rgb;
    }

    /** Filled circle, one row at a time. */
    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int w = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        if (radius < 1) return;
        int steps = Math.max(16, (int) (radius * 2 * Math.PI));
        int px = Integer.MIN_VALUE, py = 0;
        for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            if (x != px || y != py) g.fill(x, y, x + 1, y + 1, color);
            px = x;
            py = y;
        }
    }

    /** Sweep line from the centre to the edge, at screen angle a. */
    private static void line(GuiGraphicsExtractor g, int cx, int cy, double a, int color) {
        int px = Integer.MIN_VALUE, py = 0;
        for (int i = 2; i < R; i++) {
            int x = cx + (int) Math.round(Math.cos(a) * i);
            int y = cy + (int) Math.round(Math.sin(a) * i);
            if (x != px || y != py) g.fill(x, y, x + 1, y + 1, color);
            px = x;
            py = y;
        }
    }
}
