package com.thaumaturgicinfusion.client;

import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.world.InfusionIndex;
import com.thaumaturgicinfusion.world.Resolved;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block state the section mesher draws for an infused position. Called from the render worker threads, so it
 * only reads the concurrent index and the two flags {@link TIClientTicks} keeps up to date on the main thread.
 */
public final class InfusionLooks {
    /** True while the local player wears something in #thaumaturgicinfusion:reveals_veiled. */
    static volatile boolean revealed;
    /** Tenebrae positions currently lit (so hidden). */
    static final Set<Long> LIT = ConcurrentHashMap.newKeySet();
    private static final Map<Block, BlockState> SLABS = new ConcurrentHashMap<>();

    private InfusionLooks() {
    }

    public static BlockState look(ClientLevel level, BlockPos pos, BlockState state) {
        InfusionIndex index = InfusionIndex.peek(level);
        if (index == null || index.isEmpty())
            return state;
        Resolved r = index.get(pos, state);
        if (r == null)
            return state;
        BlockProps p = r.props();
        if (p.visibility == BlockProps.Visibility.REVEALED && !revealed
                || p.visibility == BlockProps.Visibility.DARK && LIT.contains(pos.asLong()))
            return Blocks.AIR.defaultBlockState();
        if (r.entry().disguise().isPresent())
            return r.entry().disguise().get();
        if (p.halfShape) {
            BlockState slab = SLABS.computeIfAbsent(state.getBlock(), InfusionLooks::slabFor);
            return slab.getBlock() == state.getBlock() ? state : slab;
        }
        return state;
    }

    /** Arbor draws as the matching bottom slab when one exists ("oak_planks" -> "oak_slab", "stone_bricks" -> "stone_brick_slab"). */
    private static BlockState slabFor(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String path = id.getPath();
        for (String candidate : new String[]{path + "_slab", path.replace("_planks", "_slab"),
                path.endsWith("s") ? path.substring(0, path.length() - 1) + "_slab" : null}) {
            if (candidate == null)
                continue;
            Block slab = BuiltInRegistries.BLOCK.getValue(id.withPath(candidate));
            if (slab instanceof SlabBlock)
                return slab.defaultBlockState();
        }
        return block.defaultBlockState();
    }
}
