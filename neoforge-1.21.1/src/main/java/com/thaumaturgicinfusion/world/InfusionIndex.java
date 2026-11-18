package com.thaumaturgicinfusion.world;

import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.effect.AspectEffect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * In-memory mirror of the loaded chunks' {@link InfusedBlocks} attachments, one per level, on both sides. The
 * BlockState mixins read it from the light engine and render threads, so it never touches chunks itself and only
 * uses concurrent maps. The server fills it on chunk load and on every change; the client from the attachment sync.
 */
public final class InfusionIndex {
    private static final Map<Level, InfusionIndex> LEVELS = new ConcurrentHashMap<>();

    /** Set by the client: re-mesh the section holding this position (its look may have changed). */
    public static volatile Consumer<BlockPos> clientRedraw = p -> {
    };

    private final Level level;
    private final Map<Long, Resolved> byPos = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> byChunk = new ConcurrentHashMap<>();

    private InfusionIndex(Level level) {
        this.level = level;
    }

    public static InfusionIndex of(Level level) {
        return LEVELS.computeIfAbsent(level, InfusionIndex::new);
    }

    /** The index for a level, or null if nothing in it was ever infused. */
    public static InfusionIndex peek(Level level) {
        return LEVELS.get(level);
    }

    /** Index for whatever getter a BlockState method was handed: a level or one of its chunks. */
    public static InfusionIndex forGetter(BlockGetter getter) {
        if (LEVELS.isEmpty())
            return null;
        if (getter instanceof Level l)
            return LEVELS.get(l);
        if (getter instanceof LevelChunk c)
            return LEVELS.get(c.getLevel());
        return null;
    }

    public static Level levelOf(BlockGetter getter) {
        if (getter instanceof Level l)
            return l;
        if (getter instanceof LevelChunk c)
            return c.getLevel();
        return null;
    }

    static void drop(Level level) {
        LEVELS.remove(level);
    }

    static void rebuildAll() {
        LEVELS.values().forEach(InfusionIndex::rebuild);
    }

    public Level level() {
        return level;
    }

    public boolean isEmpty() {
        return byPos.isEmpty();
    }

    public Resolved get(long pos) {
        return byPos.get(pos);
    }

    /** The resolved infusion at pos if the block there is still the one that was infused. */
    public Resolved get(BlockPos pos, BlockState state) {
        Resolved r = byPos.get(pos.asLong());
        return r != null && r.block() == state.getBlock() ? r : null;
    }

    public Collection<Resolved> all() {
        return byPos.values();
    }

    private Registry<AspectEffect> registry() {
        return level.registryAccess().registry(TIRegistry.ASPECT_EFFECT_KEY).orElse(null);
    }

    /** Replace everything indexed for a chunk. Returns the positions whose entry changed. */
    Set<Long> loadChunk(ChunkPos chunk, InfusedBlocks data) {
        Registry<AspectEffect> registry = registry();
        Set<Long> changed = new HashSet<>();
        Set<Long> old = byChunk.remove(chunk.toLong());
        if (old != null)
            for (long p : old) {
                Resolved r = byPos.remove(p);
                InfusedBlocks.Entry now = data == null ? null : data.blocks().get(p);
                if (r == null || !Objects.equals(r.entry(), now))
                    changed.add(p);
            }
        if (data == null || data.isEmpty() || registry == null)
            return changed;
        Set<Long> positions = ConcurrentHashMap.newKeySet();
        for (InfusedBlocks.Entry e : data.blocks().values()) {
            if (old == null || !old.contains(e.pos()))
                changed.add(e.pos());
            byPos.put(e.pos(), Resolved.of(registry, e));
            positions.add(e.pos());
        }
        byChunk.put(chunk.toLong(), positions);
        return changed;
    }

    void unloadChunk(ChunkPos chunk) {
        Set<Long> old = byChunk.remove(chunk.toLong());
        if (old != null)
            old.forEach(byPos::remove);
    }

    void put(InfusedBlocks.Entry e) {
        Registry<AspectEffect> registry = registry();
        if (registry == null)
            return;
        byPos.put(e.pos(), Resolved.of(registry, e));
        byChunk.computeIfAbsent(new ChunkPos(e.blockPos()).toLong(), k -> ConcurrentHashMap.newKeySet()).add(e.pos());
    }

    void remove(BlockPos pos) {
        byPos.remove(pos.asLong());
        Set<Long> set = byChunk.get(new ChunkPos(pos).toLong());
        if (set != null)
            set.remove(pos.asLong());
    }

    private void rebuild() {
        Registry<AspectEffect> registry = registry();
        if (registry == null)
            return;
        List<Resolved> current = new ArrayList<>(byPos.values());
        for (Resolved r : current)
            byPos.put(r.entry().pos(), Resolved.of(registry, r.entry()));
    }
}
