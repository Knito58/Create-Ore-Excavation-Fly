package io.github.knito58.oreexcavationfly;

import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Mines the vein of the chunk it stands in while it is rotated. */
public class DrillBlockEntity extends KineticBlockEntity {
    /** Progress units; a full item needs ticksAt32 * 32 units, speed (RPM) adds |speed| per tick. */
    private float progress;

    public DrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DRILL_BE, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server) || speed == 0 || overStressed) return;
        ChunkPos cp = new ChunkPos(worldPosition);
        Veins.Vein vein = Veins.at(server.getSeed(), cp.x(), cp.z());
        if (vein == null) return;

        progress += Math.abs(speed);
        float needed = vein.ticksAt32() * 32f;
        if (progress < needed) return;
        progress -= needed;
        output(server, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(vein.item()))));
    }

    private void output(ServerLevel server, ItemStack stack) {
        Direction front = getBlockState().getValue(DrillBlock.FACING);
        BlockPos target = worldPosition.relative(front);
        Vec3 at = Vec3.atCenterOf(target);
        ItemEntity item = new ItemEntity(server, at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        server.addFreshEntity(item);
    }
}
