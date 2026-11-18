package com.thaumaturgicinfusion.world;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** The aspect effects infused into one block, by aspect_effect registry id. Used as item component and chunk data. */
public record Infusion(List<Identifier> effects) {
    public static final Infusion EMPTY = new Infusion(List.of());
    public static final Codec<Infusion> CODEC = Identifier.CODEC.listOf().xmap(Infusion::new, Infusion::effects);
    public static final StreamCodec<ByteBuf, Infusion> STREAM_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Infusion::new, Infusion::effects);

    public Infusion {
        effects = List.copyOf(effects);
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public boolean has(Identifier effect) {
        return effects.contains(effect);
    }

    /** Both lists combined, keeping order and dropping duplicates. */
    public Infusion merge(Infusion other) {
        List<Identifier> out = new ArrayList<>(effects);
        for (Identifier id : other.effects)
            if (!out.contains(id))
                out.add(id);
        return new Infusion(out);
    }
}
