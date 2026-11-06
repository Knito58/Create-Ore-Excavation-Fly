package io.github.knito58.oreexcavationfly;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JEI info pages: every ore the drill can produce lists the veins it comes from and the drilling time,
 * and the drill and the vein finder get a short how-to. Only loaded when JEI is present.
 */
public class JeiIntegration implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Map<Item, List<Component>> byItem = new LinkedHashMap<>();
        for (Veins.Vein v : Veins.ALL) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(v.item()));
            byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(
                Component.translatable("jei.oreexcavationfly.vein_line",
                    v.displayName(), String.format("%.1f", v.ticksAt32() / 20f)));
        }
        for (Map.Entry<Item, List<Component>> e : byItem.entrySet()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("jei.oreexcavationfly.ore_header"));
            lines.addAll(e.getValue());
            registration.addItemStackInfo(new ItemStack(e.getKey()), lines.toArray(new Component[0]));
        }
        registration.addItemStackInfo(new ItemStack(ModBlocks.DRILL),
            Component.translatable("jei.oreexcavationfly.drill_info"));
        registration.addItemStackInfo(new ItemStack(ModBlocks.VEIN_FINDER),
            Component.translatable("jei.oreexcavationfly.finder_info"));
    }
}
