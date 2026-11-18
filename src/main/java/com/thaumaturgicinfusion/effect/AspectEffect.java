package com.thaumaturgicinfusion.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thaumaturgicinfusion.TIRegistry;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * One infusable effect, loaded from data/&lt;ns&gt;/thaumaturgicinfusion/aspect_effect/&lt;name&gt;.json.
 * The entry name is the original TC4 aspect name (aer, pannus...); {@code aspect} is the Thaumaturge aspect whose
 * phial infuses it. {@code cost} is the original effect cost; the infusion recipe scales its essentia from it.
 */
public record AspectEffect(Identifier aspect, int cost, List<EffectBehaviour> behaviours) {
    public static final Codec<EffectBehaviour> BEHAVIOUR_CODEC = TIRegistry.BEHAVIOUR_TYPES.byNameCodec()
            .dispatch("type", EffectBehaviour::codec, c -> c);

    public static final Codec<AspectEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("aspect").forGetter(AspectEffect::aspect),
            Codec.intRange(1, 64).fieldOf("cost").forGetter(AspectEffect::cost),
            BEHAVIOUR_CODEC.listOf().fieldOf("behaviours").forGetter(AspectEffect::behaviours)
    ).apply(i, AspectEffect::new));
}
