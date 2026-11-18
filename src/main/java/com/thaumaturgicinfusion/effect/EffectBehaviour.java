package com.thaumaturgicinfusion.effect;

import com.mojang.serialization.MapCodec;
import com.thaumaturgicinfusion.world.Resolved;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What an infused block does. Each hook replaces one Block method the original effect classes overrode through their
 * InfusedBlock wrapper; the index, the events and the BlockState mixins call them. Defaults do nothing.
 */
public interface EffectBehaviour {
    MapCodec<? extends EffectBehaviour> codec();

    /** Block properties (light, signal, collision, friction...). Read on any thread, so keep it pure. */
    default void apply(BlockProps props) {
    }

    /** Ticks between {@link #tick} calls on the server, 0 for none (original updateBlock/updateTick). */
    default int tickInterval() {
        return 0;
    }

    default void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
    }

    /**
     * An entity whose feet are {@code dy} blocks above the infused block (0 = inside it, 1 = standing on it). Runs
     * every tick on both sides, so movement effects work for the client-driven local player; do world changes only
     * on the server (original onEntityWalking/onEntityCollidedWithBlock).
     */
    default void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
    }

    /** Right-click on the block. Runs on both sides; return non-PASS to consume the click (original onBlockActivated). */
    default InteractionResult use(Level level, BlockPos pos, Resolved block, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** Client-only visuals for blocks near the player, every client tick (original Praecantatio sparkles). */
    default void clientTick(Level level, BlockPos pos, Resolved block, RandomSource random) {
    }

    /** True for Machina: a redstone signal switches every other behaviour on the block off. */
    default boolean isToggle() {
        return false;
    }
}
