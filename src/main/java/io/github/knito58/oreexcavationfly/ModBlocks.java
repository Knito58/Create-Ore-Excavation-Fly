package io.github.knito58.oreexcavationfly;

import net.minecraft.core.Registry;
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OreExcavationFly.MOD_ID, path);
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
        DRILL_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("drill"),
            new BlockEntityType<>(DrillBlockEntity::new, Set.of(DRILL))
        );
    }
}
