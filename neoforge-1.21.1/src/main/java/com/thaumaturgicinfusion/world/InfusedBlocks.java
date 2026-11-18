package com.thaumaturgicinfusion.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chunk attachment: every infused position in the chunk with the block it was placed as, plus the little per-block
 * state some effects keep (the original kept it in each effect object's NBT). An entry only counts while the block
 * there is still that block, so anything that replaces it without a drop (pistons, commands) leaves a stale entry that
 * is ignored and later overwritten. Immutable; changes go through {@link #with} and {@link #without}.
 */
public record InfusedBlocks(Map<Long, Entry> blocks) {
    public static final InfusedBlocks EMPTY = new InfusedBlocks(Map.of());

    /**
     * @param facing   horizontal direction the placer faced (Motus pushes that way)
     * @param link     linked position (Iter partner, Permutatio inventory)
     * @param disguise block state drawn instead of the real one (Superbia)
     * @param powered  last redstone state seen (Machina)
     */
    public record Entry(long pos, ResourceLocation block, Infusion infusion, Optional<Direction> facing, Optional<BlockPos> link,
                        Optional<BlockState> disguise, boolean powered) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                ResourceLocation.CODEC.fieldOf("block").forGetter(Entry::block),
                Infusion.CODEC.fieldOf("effects").forGetter(Entry::infusion),
                Direction.CODEC.optionalFieldOf("facing").forGetter(Entry::facing),
                BlockPos.CODEC.optionalFieldOf("link").forGetter(Entry::link),
                BlockState.CODEC.optionalFieldOf("disguise").forGetter(Entry::disguise),
                Codec.BOOL.optionalFieldOf("powered", false).forGetter(Entry::powered)
        ).apply(i, Entry::new));

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }

        public Entry withLink(Optional<BlockPos> link) {
            return new Entry(pos, block, infusion, facing, link, disguise, powered);
        }

        public Entry withDisguise(Optional<BlockState> disguise) {
            return new Entry(pos, block, infusion, facing, link, disguise, powered);
        }

        public Entry withPowered(boolean powered) {
            return new Entry(pos, block, infusion, facing, link, disguise, powered);
        }
    }

    public static final Codec<InfusedBlocks> CODEC = Entry.CODEC.listOf().xmap(InfusedBlocks::of, b -> List.copyOf(b.blocks.values()));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfusedBlocks> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public InfusedBlocks {
        blocks = Map.copyOf(blocks);
    }

    private static InfusedBlocks of(List<Entry> entries) {
        Map<Long, Entry> map = new HashMap<>();
        for (Entry e : entries)
            map.put(e.pos, e);
        return new InfusedBlocks(map);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public Entry get(BlockPos pos) {
        return blocks.get(pos.asLong());
    }

    public InfusedBlocks with(Entry entry) {
        Map<Long, Entry> map = new HashMap<>(blocks);
        map.put(entry.pos, entry);
        return new InfusedBlocks(map);
    }

    public InfusedBlocks without(BlockPos pos) {
        if (!blocks.containsKey(pos.asLong()))
            return this;
        Map<Long, Entry> map = new HashMap<>(blocks);
        map.remove(pos.asLong());
        return new InfusedBlocks(map);
    }
}
