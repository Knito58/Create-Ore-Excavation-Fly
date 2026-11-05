package io.github.knito58.oreexcavationfly;

import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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

    /** The vein in this drill's chunk, or null (server side only). */
    public Veins.Vein veinHere() {
        if (!(level instanceof ServerLevel server)) return null;
        return Veins.at(server.getSeed(), worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
    }

    /** Current speed in RPM (absolute). */
    public float rpm() {
        return Math.abs(speed);
    }

    public boolean isStalled() {
        return !pending.isEmpty();
    }

    /** Progress to the next item, 0..1. */
    public float progressFraction() {
        Veins.Vein v = veinHere();
        return v == null ? 0f : Math.min(1f, progress / (v.ticksAt32() * 32f));
    }

    /** Inserts into the inventory in front if there is one, else drops the item. Returns what is left over. */
    private ItemStack output(ServerLevel server, ItemStack stack) {
        Direction front = getBlockState().getValue(DrillBlock.FACING);
        BlockPos target = worldPosition.relative(front);
        // Fabric Transfer API: works for vanilla containers and Create's vaults, barrels etc.
        // (Wrapping storages as vanilla Containers loses items on merge, so do not do that.)
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(server, target, front.getOpposite());
        if (storage != null) {
            long inserted;
            try (Transaction tx = Transaction.openOuter()) {
                inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
                tx.commit();
            }
            int left = stack.getCount() - (int) inserted;
            return left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
        }
        Vec3 at = Vec3.atCenterOf(target);
        ItemEntity item = new ItemEntity(server, at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        server.addFreshEntity(item);
        return ItemStack.EMPTY;
    }
}
