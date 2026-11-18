package com.thaumaturgicinfusion.content;

import com.leclowndu93150.thaumaturge.api.infusion.IInfusionStabiliser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Infusion Core. In the original it replaced the altar's centre pedestal so block infusion could run; Thaumaturge's
 * own altar now does that job, so the core comes back as what its class also was: an {@link IInfusionStabiliser}, a
 * strong one (worth two candles' stability) for altars that run the costly aspect effects.
 */
public class InfusionCoreBlock extends Block implements IInfusionStabiliser {
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

    public InfusionCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canStabiliseInfusion(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public float getStabilizationAmount(Level level, BlockPos pos) {
        return DEFAULT_STABILIZATION * 2;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0)
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    random.nextGaussian() * 0.6, random.nextDouble() * 0.6, random.nextGaussian() * 0.6);
    }
}
