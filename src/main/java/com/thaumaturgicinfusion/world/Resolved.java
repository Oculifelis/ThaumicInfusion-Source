package com.thaumaturgicinfusion.world;

import com.thaumaturgicinfusion.effect.AspectEffect;
import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.effect.EffectBehaviour;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** An infused position with its effects looked up and its block properties worked out. Immutable. */
public record Resolved(InfusedBlocks.Entry entry, Block block, List<EffectBehaviour> behaviours, BlockProps props, boolean ticks) {

    public static Resolved of(Registry<AspectEffect> registry, InfusedBlocks.Entry entry) {
        List<EffectBehaviour> all = new ArrayList<>();
        for (ResourceLocation id : entry.infusion().effects()) {
            AspectEffect effect = registry.get(id);
            if (effect != null)
                all.addAll(effect.behaviours());
        }
        // Machina: while powered only the toggle itself stays active.
        if (entry.powered() && all.stream().anyMatch(EffectBehaviour::isToggle))
            all.removeIf(b -> !b.isToggle());
        BlockProps props = new BlockProps();
        boolean ticks = false;
        for (EffectBehaviour b : all) {
            b.apply(props);
            ticks |= b.tickInterval() > 0;
        }
        return new Resolved(entry, BuiltInRegistries.BLOCK.get(entry.block()), List.copyOf(all), props, ticks);
    }

    public BlockPos pos() {
        return entry.blockPos();
    }

    public <T extends EffectBehaviour> T find(Class<T> type) {
        for (EffectBehaviour b : behaviours)
            if (type.isInstance(b))
                return type.cast(b);
        return null;
    }
}
