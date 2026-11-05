package io.github.knito58.oreexcavationfly;

import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Mines the vein of the chunk it stands in while it is rotated. */
public class DrillBlockEntity extends KineticBlockEntity {
    /** Progress units; a full item needs ticksAt32 * 32 units, speed (RPM) adds |speed| per tick. */
    private float progress;
    /** Ore waiting for room in the target inventory. */
    private ItemStack pending = ItemStack.EMPTY;

    public DrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DRILL_BE, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server) || speed == 0 || overStressed) return;
        if (!pending.isEmpty()) {
            pending = output(server, pending);
            if (!pending.isEmpty()) return; // target full: stall until there is room
        }
        Veins.Vein vein = Veins.at(server.getSeed(), worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (vein == null) return;

        progress += Math.abs(speed);
        float needed = vein.ticksAt32() * 32f;
        if (progress < needed) return;
        progress -= needed;
        pending = output(server, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(vein.item()))));
    }

    /** Inserts into the inventory in front if there is one, else drops the item. Returns what is left over. */
    private ItemStack output(ServerLevel server, ItemStack stack) {
        Direction front = getBlockState().getValue(DrillBlock.FACING);
        BlockPos target = worldPosition.relative(front);
        Container inv = HopperBlockEntity.getContainerAt(server, target);
        if (inv != null) {
            return HopperBlockEntity.addItem(null, inv, stack, front.getOpposite());
        }
        Vec3 at = Vec3.atCenterOf(target);
        ItemEntity item = new ItemEntity(server, at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        server.addFreshEntity(item);
        return ItemStack.EMPTY;
    }
}
