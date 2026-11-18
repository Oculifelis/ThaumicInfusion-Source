package com.thaumaturgicinfusion.world;

import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.effect.BlockProps;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Reads infused positions (through the index, on either side) and changes them (server only: the chunk attachment,
 * the index, and whatever the change affects around the block).
 */
public final class InfusionWorld {
    private InfusionWorld() {
    }

    /** The resolved infusion at pos, or null if there is none or the block there changed since it was placed. */
    public static Resolved get(Level level, BlockPos pos) {
        InfusionIndex index = InfusionIndex.peek(level);
        if (index == null || index.isEmpty() || !level.isLoaded(pos))
            return null;
        return index.get(pos, level.getBlockState(pos));
    }

    public static void place(Level level, BlockPos pos, Block block, Infusion infusion, Direction facing) {
        InfusedBlocks.Entry entry = new InfusedBlocks.Entry(pos.asLong(), BuiltInRegistries.BLOCK.getKey(block), infusion,
                Optional.of(facing), Optional.empty(), Optional.empty(), level.hasNeighborSignal(pos));
        write(level, entry);
    }

    public static void update(Level level, BlockPos pos, UnaryOperator<InfusedBlocks.Entry> change) {
        Resolved r = get(level, pos);
        if (r != null)
            write(level, change.apply(r.entry()));
    }

    private static void write(Level level, InfusedBlocks.Entry entry) {
        BlockPos pos = entry.blockPos();
        LevelChunk chunk = level.getChunkAt(pos);
        InfusionIndex index = InfusionIndex.of(level);
        Resolved old = index.get(pos.asLong());
        chunk.setData(TIRegistry.INFUSED_BLOCKS, chunk.getData(TIRegistry.INFUSED_BLOCKS).with(entry));
        index.put(entry);
        afterChange(level, pos, old, index.get(pos.asLong()));
    }

    public static void remove(Level level, BlockPos pos) {
        if (!level.isLoaded(pos))
            return;
        LevelChunk chunk = level.getChunkAt(pos);
        InfusedBlocks data = chunk.getExistingData(TIRegistry.INFUSED_BLOCKS).orElse(null);
        if (data == null || data.get(pos) == null)
            return;
        InfusionIndex index = InfusionIndex.of(level);
        Resolved old = index.get(pos.asLong());
        chunk.setData(TIRegistry.INFUSED_BLOCKS, data.without(pos));
        index.remove(pos);
        StorageEffects.forget(level, pos);
        afterChange(level, pos, old, null);
    }

    /** Light, redstone and capabilities around a block whose infusion changed. Runs on either side. */
    static void afterChange(Level level, BlockPos pos, Resolved old, Resolved now) {
        BlockProps before = old == null ? BlockProps.NONE : old.props();
        BlockProps after = now == null ? BlockProps.NONE : now.props();
        if (before.affectsLight() || after.affectsLight())
            level.getLightEngine().checkBlock(pos);
        if (before.signal != after.signal && !level.isClientSide())
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        level.invalidateCapabilities(pos);
    }
}
