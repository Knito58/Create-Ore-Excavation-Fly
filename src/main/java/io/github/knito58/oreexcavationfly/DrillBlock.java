package io.github.knito58.oreexcavationfly;

import com.zurrtum.create.content.kinetics.base.DirectionalKineticBlock;
import com.zurrtum.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Single-block ore drill. The shaft is on the back; ore comes out of the front. */
public class DrillBlock extends DirectionalKineticBlock implements IBE<DrillBlockEntity> {
    public DrillBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Class<DrillBlockEntity> getBlockEntityClass() {
        return DrillBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DrillBlockEntity> getBlockEntityType() {
        return ModBlocks.DRILL_BE;
    }
}
