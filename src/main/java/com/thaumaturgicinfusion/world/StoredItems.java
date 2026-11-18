package com.thaumaturgicinfusion.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * Chunk attachment (server only, saved): the contents of the chunk's disguised inventories, keyed by position.
 * Replaces the original's VacuosTile. Immutable; stacks are copied in and out.
 */
public record StoredItems(Map<Long, List<ItemStack>> slots) {
    public static final StoredItems EMPTY = new StoredItems(Map.of());

    private record Slot(long pos, List<ItemStack> items) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Slot::pos),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Slot::items)
        ).apply(i, Slot::new));
    }

    public static final Codec<StoredItems> CODEC = Slot.CODEC.listOf().xmap(list -> {
        Map<Long, List<ItemStack>> map = new HashMap<>();
        list.forEach(s -> map.put(s.pos, s.items));
        return new StoredItems(map);
    }, s -> s.slots.entrySet().stream().map(e -> new Slot(e.getKey(), e.getValue())).toList());

    public StoredItems {
        slots = Map.copyOf(slots);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public StoredItems with(long pos, List<ItemStack> items) {
        Map<Long, List<ItemStack>> map = new HashMap<>(slots);
        map.put(pos, items.stream().map(ItemStack::copy).toList());
        return new StoredItems(map);
    }

    public StoredItems without(long pos) {
        if (!slots.containsKey(pos))
            return this;
        Map<Long, List<ItemStack>> map = new HashMap<>(slots);
        map.remove(pos);
        return new StoredItems(map);
    }
}
