package com.thaumaturgicinfusion.effect;

/**
 * The block-property side of an infusion: what the original effects changed by overriding Block methods on their
 * InfusedBlock wrapper (light, redstone, collision, friction...). Each behaviour adds to it in {@link EffectBehaviour#apply};
 * the BlockState mixins and the events read the result. Built once per infused position when the index resolves it.
 */
public final class BlockProps {
    public static final BlockProps NONE = new BlockProps();

    public int light;
    public int signal;
    public boolean noCollision;
    public boolean halfShape;
    public float friction = Float.NaN;
    public float enchantPower;
    public boolean fireSource;
    public boolean sustainsPlants;
    public boolean transparent;
    public boolean stabiliser;
    public boolean denyMonsterSpawns;
    public Visibility visibility = Visibility.ALWAYS;
    public float breakSpeed = 1f;
    public float fallDamage = 1f;
    public boolean explosionProof;

    public boolean affectsLight() {
        return light > 0 || transparent;
    }

    public enum Visibility {
        ALWAYS,
        /** Only drawn for a player wearing something in #thaumaturgicinfusion:reveals_veiled (original Sensus). */
        REVEALED,
        /** Only drawn and solid while the light above is 8 or less (original Tenebrae). */
        DARK
    }
}
