package com.thaumaturgicinfusion.world;

import com.thaumaturgicinfusion.effect.BlockProps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** What the BlockState mixins ask. Cheap when nothing is infused: one empty-map check. */
public final class InfusionHooks {
    public static final VoxelShape HALF = Block.box(0, 0, 0, 16, 8, 16);

    private InfusionHooks() {
    }

    /** Block properties of the infusion at pos, or null. Safe from any thread. */
    public static BlockProps props(BlockGetter getter, BlockPos pos, BlockState state) {
        InfusionIndex index = InfusionIndex.forGetter(getter);
        if (index == null || index.isEmpty())
            return null;
        Resolved r = index.get(pos, state);
        return r == null ? null : r.props();
    }

    /** Tenebrae: hidden and not solid while the light above is over 8 (the original checked the light at the block). */
    public static boolean hiddenByLight(BlockGetter getter, BlockPos pos) {
        Level level = InfusionIndex.levelOf(getter);
        return level != null && level.getMaxLocalRawBrightness(pos.above()) > 8;
    }
}
