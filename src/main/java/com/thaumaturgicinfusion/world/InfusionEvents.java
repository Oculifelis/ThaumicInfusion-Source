package com.thaumaturgicinfusion.world;

import com.leclowndu93150.thaumaturge.api.infusion.InfusionCraftedEvent;
import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.effect.Behaviours;
import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.effect.EffectBehaviour;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Replaces the original's InfusedBlock wrapper: the infused item carries an {@link Infusion} component, placing it
 * records the position in the chunk attachment and the index, and each behaviour hook is driven from a NeoForge event
 * (block properties go through the BlockState mixins instead).
 */
public final class InfusionEvents {
    private InfusionEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(InfusionEvents::onInfusionCrafted);
        bus.addListener(InfusionEvents::onPlace);
        bus.addListener(InfusionEvents::onDrops);
        bus.addListener(InfusionEvents::onFall);
        bus.addListener(InfusionEvents::onBreakSpeed);
        bus.addListener(InfusionEvents::onExplosion);
        bus.addListener(InfusionEvents::onLevelTick);
        bus.addListener(InfusionEvents::onEntityTick);
        bus.addListener(InfusionEvents::onPlayerTick);
        bus.addListener(InfusionEvents::onSpawnCheck);
        bus.addListener(InfusionEvents::onNeighborNotify);
        bus.addListener(InfusionEvents::onChunkLoad);
        bus.addListener(InfusionEvents::onChunkUnload);
        bus.addListener(InfusionEvents::onLevelUnload);
        bus.addListener(Interactions::onRightClick);
        bus.addListener(RegisterCommandsEvent.class, e -> TICommand.register(e.getDispatcher(), e.getBuildContext()));
    }

    /** The altar crafted a sigil: hand back the catalyst block with the sigil's effect added. */
    private static void onInfusionCrafted(InfusionCraftedEvent event) {
        ItemStack result = event.getResult();
        Infusion added = result.get(TIRegistry.INFUSION.get());
        if (!result.is(TIRegistry.INFUSION_SIGIL.get()) || added == null)
            return;
        ItemStack catalyst = event.getCatalyst();
        if (!(catalyst.getItem() instanceof BlockItem)) {
            event.setResult(catalyst.copy());
            return;
        }
        event.setResult(infuse(catalyst.copy(), added));
    }

    public static ItemStack infuse(ItemStack stack, Infusion added) {
        Infusion current = stack.getOrDefault(TIRegistry.INFUSION.get(), Infusion.EMPTY);
        stack.set(TIRegistry.INFUSION.get(), current.merge(added));
        return stack;
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        BlockPos pos = event.getPos();
        Block placed = event.getPlacedBlock().getBlock();
        Infusion infusion = null;
        Direction facing = Direction.NORTH;
        if (event.getEntity() instanceof LivingEntity living) {
            facing = living.getDirection();
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = living.getItemInHand(hand);
                if (held.getItem() instanceof BlockItem bi && bi.getBlock() == placed && held.has(TIRegistry.INFUSION.get())) {
                    infusion = held.get(TIRegistry.INFUSION.get());
                    break;
                }
            }
        }
        if (infusion != null && !infusion.isEmpty())
            InfusionWorld.place(level, pos, placed, infusion, facing);
        else
            InfusionWorld.remove(level, pos);
    }

    /** Broken infused blocks drop their item with the infusion still on it, and spill any stored items. */
    private static void onDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        Resolved r = InfusionWorld.get(level, pos);
        if (r == null) {
            InfusionWorld.remove(level, pos);
            return;
        }
        StorageEffects.dropContents(level, pos);
        Infusion infusion = r.entry().infusion();
        InfusionWorld.remove(level, pos);
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() == event.getState().getBlock()) {
                drop.setItem(infuse(stack.copy(), infusion));
                return;
            }
        }
    }

    private static BlockProps propsAt(Level level, BlockPos pos) {
        Resolved r = InfusionWorld.get(level, pos);
        return r == null ? BlockProps.NONE : r.props();
    }

    private static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        float multiplier = propsAt(entity.level(), BlockPos.containing(entity.getX(), entity.getY() - 0.2, entity.getZ())).fallDamage;
        if (multiplier == 1f)
            multiplier = propsAt(entity.level(), entity.blockPosition()).fallDamage;
        if (multiplier != 1f)
            event.setDamageMultiplier(event.getDamageMultiplier() * multiplier);
    }

    /** Runs on both sides so the client's crack animation matches the server. */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.getPosition().ifPresent(pos -> {
            float multiplier = propsAt(event.getEntity().level(), pos).breakSpeed;
            if (multiplier != 1f)
                event.setNewSpeed(event.getNewSpeed() * multiplier);
        });
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!level.isClientSide())
            event.getAffectedBlocks().removeIf(pos -> propsAt(level, pos).explosionProof);
    }

    /** The original's per-block updateBlock/updateTick: runs each ticking behaviour on its interval. */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        InfusionIndex index = InfusionIndex.peek(level);
        if (index == null || index.isEmpty())
            return;
        long time = level.getGameTime();
        for (Resolved r : index.all()) {
            if (!r.ticks())
                continue;
            BlockPos pos = r.pos();
            if (!level.shouldTickBlocksAt(pos) || level.getBlockState(pos).getBlock() != r.block())
                continue;
            long offset = time + (r.entry().pos() & 0xFFL);
            for (EffectBehaviour b : r.behaviours())
                if (b.tickInterval() > 0 && offset % b.tickInterval() == 0)
                    b.tick(level, pos, r, time);
        }
    }

    /** Entities standing in, on or just above an infused block (onEntityWalking/onEntityCollidedWithBlock). */
    private static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.isSpectator())
            return;
        Level level = entity.level();
        InfusionIndex index = InfusionIndex.peek(level);
        if (index == null || index.isEmpty())
            return;
        BlockPos feet = BlockPos.containing(entity.getX(), entity.getY() + 0.001, entity.getZ());
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos pos = feet.below(dy);
            Resolved r = index.get(pos.asLong());
            if (r == null || level.getBlockState(pos).getBlock() != r.block())
                continue;
            for (EffectBehaviour b : r.behaviours())
                b.entityNear(level, pos, r, entity, dy);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide())
            Behaviours.FlightGrants.expire(player, player.level().getGameTime());
    }

    private static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER)
            return;
        BlockPos below = BlockPos.containing(event.getX(), event.getY(), event.getZ()).below();
        if (propsAt(event.getLevel().getLevel(), below).denyMonsterSpawns)
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    /** Machina: watch redstone around toggle blocks. */
    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        InfusionIndex index = InfusionIndex.peek(level);
        if (index == null || index.isEmpty())
            return;
        for (Direction d : Direction.values()) {
            BlockPos pos = event.getPos().relative(d);
            Resolved r = InfusionWorld.get(level, pos);
            if (r == null || r.behaviours().stream().noneMatch(EffectBehaviour::isToggle))
                continue;
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != r.entry().powered())
                InfusionWorld.update(level, pos, e -> e.withPowered(powered));
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        LevelChunk chunk = event.getChunk();
        if (!(event.getLevel() instanceof Level level))
            return;
        InfusedBlocks data = chunk.getExistingDataOrNull(TIRegistry.INFUSED_BLOCKS);
        if (data != null && !data.isEmpty())
            InfusionIndex.of(level).loadChunk(chunk.getPos(), data);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        LevelChunk chunk = event.getChunk();
        if (!(event.getLevel() instanceof Level level))
            return;
        InfusionIndex index = InfusionIndex.peek(level);
        if (index != null)
            index.unloadChunk(chunk.getPos());
        StorageEffects.unloadChunk(level, chunk);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            InfusionIndex.drop(level);
            StorageEffects.unloadLevel(level);
        }
    }

    /** Client attachment sync: mirror the chunk into the index and refresh light and looks where it changed. */
    public static InfusedBlocks onClientSync(LevelChunk chunk, InfusedBlocks data) {
        Level level = chunk.getLevel();
        Set<Long> changed = InfusionIndex.of(level).loadChunk(chunk.getPos(), data);
        for (long p : changed) {
            BlockPos pos = BlockPos.of(p);
            level.getLightEngine().checkBlock(pos);
            InfusionIndex.clientRedraw.accept(pos);
        }
        return data;
    }
}
