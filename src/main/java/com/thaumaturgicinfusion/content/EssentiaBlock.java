package com.thaumaturgicinfusion.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The original's Essentia Block (plain, brick and chiseled metadata), tinted with its aspect's colour and glowing.
 * One block per style; the aspect lives in the block entity and on the item, so every aspect in Thaumaturge's
 * datapack registry gets blocks without a table here.
 */
public class EssentiaBlock extends BaseEntityBlock {
    public static final MapCodec<EssentiaBlock> CODEC = simpleCodec(EssentiaBlock::new);

    public EssentiaBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssentiaBlockEntity(pos, state);
    }

    /** Pick block keeps the aspect even without the include-data key, like the original's getPickBlock. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof EssentiaBlockEntity be)
            stack.applyComponents(be.collectComponents());
        return stack;
    }
}
