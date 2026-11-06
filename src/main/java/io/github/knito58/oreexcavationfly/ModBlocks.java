package io.github.knito58.oreexcavationfly;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Set;

public final class ModBlocks {
    private ModBlocks() {}

    public static DrillBlock DRILL;
    public static BlockEntityType<DrillBlockEntity> DRILL_BE;
    /** Iron, Gold and Diamond Vein Finder. */
    public static Item VEIN_FINDER, GOLD_VEIN_FINDER, DIAMOND_VEIN_FINDER;
    /** Ticks of scanning left from the coal burning in a Vein Finder. */
    public static final DataComponentType<Integer> FINDER_FUEL = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE, id("fuel"),
        DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, path);
    }

    private static Item finder(String name, int tier) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new VeinFinderItem(tier, new Item.Properties().setId(key).stacksTo(1)));
    }

    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("drill"));
        DRILL = Registry.register(
            BuiltInRegistries.BLOCK, blockKey,
            new DrillBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE).mapColor(MapColor.PODZOL).setId(blockKey))
        );
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("drill"));
        Registry.register(
            BuiltInRegistries.ITEM, itemKey,
            new BlockItem(DRILL, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
        );
        VEIN_FINDER = finder("vein_finder", 0);
        GOLD_VEIN_FINDER = finder("gold_vein_finder", 1);
        DIAMOND_VEIN_FINDER = finder("diamond_vein_finder", 2);
        DRILL_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("drill"),
            new BlockEntityType<>(DrillBlockEntity::new, Set.of(DRILL))
        );
    }
}
