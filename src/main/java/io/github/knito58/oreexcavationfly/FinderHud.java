package io.github.knito58.oreexcavationfly;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/**
 * Client side: a small radar in the top-left corner while the Ore Vein Finder is held.
 * Rings mark the type and exact ranges. The blip is a big faint pulse when far
 * and shrinks to a sharp dot when close. The radar turns with the player (up = facing).
 */
public class FinderHud implements ClientModInitializer {
    private static final int R = 32, MARGIN = 6;
    private static FinderReading last;
    private static long lastTime;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FinderReading.TYPE, (reading, context) -> {
            last = reading;
            lastTime = System.currentTimeMillis();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, "finder_radar"), FinderHud::extract);
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        FinderReading r = last;
        long now = System.currentTimeMillis();
        if (player == null || r == null || mc.options.hideGui || now - lastTime > 1500) return;
        if (!player.getMainHandItem().is(ModBlocks.VEIN_FINDER) && !player.getOffhandItem().is(ModBlocks.VEIN_FINDER)) return;

        int cx = MARGIN + R, cy = MARGIN + R;
        disc(g, cx, cy, R, 0x90000000);
        ring(g, cx, cy, R, 0xC040FF40);
        ring(g, cx, cy, R * r.typeRange() / r.range(), 0x6040FF40);
        if (r.exactRange() > 0) ring(g, cx, cy, R * r.exactRange() / r.range(), 0x6040FF40);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF); // the player
        g.fill(cx, cy - 4, cx + 1, cy - 1, 0xFFFFFFFF);     // facing tick

        double yaw = Math.toRadians(player.getYRot());
        // North marker: world north is angle -PI/2 (towards -z).
        int nx = cx - (int) Math.round(Math.cos(-Math.PI / 2 - yaw) * (R - 5));
        int ny = cy - (int) Math.round(Math.sin(-Math.PI / 2 - yaw) * (R - 5));
        g.text(mc.font, "N", nx - 2, ny - 4, 0xA0FFFFFF, false);

        double pulse = 0.5 + 0.5 * Math.sin(now / 200.0);
        if (r.tier() == FinderReading.HERE) {
            disc(g, cx, cy, 5, alpha(0xFF3030, 0.5 + 0.5 * pulse));
        } else if (r.tier() != FinderReading.NOTHING) {
            double k = Math.min(1.0, r.dist() / r.range()) * R;
            int bx = cx - (int) Math.round(Math.cos(r.angle() - yaw) * k);
            int by = cy - (int) Math.round(Math.sin(r.angle() - yaw) * k);
            switch (r.tier()) {
                case FinderReading.WEAK -> disc(g, bx, by, 9, alpha(0xFFFF60, 0.15 + 0.25 * pulse));
                case FinderReading.MEDIUM -> disc(g, bx, by, 6, alpha(0xFFC040, 0.3 + 0.3 * pulse));
                case FinderReading.TYPE_KNOWN -> disc(g, bx, by, 3, alpha(0xFF9020, 0.7 + 0.3 * pulse));
                default -> disc(g, bx, by, 2, 0xFFFF3030);
            }
        }
        g.text(mc.font, r.text(), MARGIN, MARGIN + 2 * R + 4, 0xFFFFFFFF, true);
    }

    private static int alpha(int rgb, double a) {
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
}
