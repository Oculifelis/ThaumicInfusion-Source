package com.thaumaturgicinfusion.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.world.InfusionWorld;
import com.thaumaturgicinfusion.world.Resolved;
import com.thaumaturgicinfusion.world.StorageEffects;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Every behaviour type. Registered in TIRegistry under thaumaturgicinfusion:&lt;name&gt; and configured from the
 * aspect_effect JSON, so new effects that reuse a type are data only. The comment on each names the original effects.
 */
public final class Behaviours {
    private Behaviours() {
    }

    private static AABB box(BlockPos pos, double radius) {
        return new AABB(pos).inflate(radius);
    }

    // ---- block properties -----------------------------------------------------------------------------------------

    /** Scales fall damage for anything landing on the block (Pannus). */
    public record FallCushion(float multiplier) implements EffectBehaviour {
        public static final MapCodec<FallCushion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0f, 10f).fieldOf("multiplier").forGetter(FallCushion::multiplier)
        ).apply(i, FallCushion::new));

        @Override
        public MapCodec<FallCushion> codec() {
            return CODEC;
        }

        @Override
        public void apply(BlockProps props) {
            props.fallDamage *= multiplier;
        }
    }

    /** Changes how fast the block is mined and whether explosions break it (Tutamen, Perfodio). */
    public record Hardness(float breakSpeedMultiplier, boolean explosionProof) implements EffectBehaviour {
        public static final MapCodec<Hardness> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0.001f, 100f).fieldOf("break_speed_multiplier").forGetter(Hardness::breakSpeedMultiplier),
                Codec.BOOL.optionalFieldOf("explosion_proof", false).forGetter(Hardness::explosionProof)
        ).apply(i, Hardness::new));

        @Override
        public MapCodec<Hardness> codec() {
            return CODEC;
        }

        @Override
        public void apply(BlockProps props) {
            props.breakSpeed *= breakSpeedMultiplier;
            props.explosionProof |= explosionProof;
        }
    }

    /**
     * One behaviour for every effect that only changed a Block property: Lux (light), Potentia (signal), Spiritus
     * (no collision), Arbor (half shape), Gelum (friction), Cognitio (enchant power), Ignis (fire source), Messis
     * (sustains plants), Vitreus (transparent), Ordo (infusion stabiliser). Read by the BlockState mixins.
     */
    public record BlockProperty(int light, int signal, boolean noCollision, boolean halfShape, Optional<Float> friction,
                                float enchantPower, boolean fireSource, boolean sustainsPlants, boolean transparent,
                                boolean stabiliser) implements EffectBehaviour {
        public static final MapCodec<BlockProperty> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, 15).optionalFieldOf("light", 0).forGetter(BlockProperty::light),
                Codec.intRange(0, 15).optionalFieldOf("signal", 0).forGetter(BlockProperty::signal),
                Codec.BOOL.optionalFieldOf("no_collision", false).forGetter(BlockProperty::noCollision),
                Codec.BOOL.optionalFieldOf("half_shape", false).forGetter(BlockProperty::halfShape),
                Codec.floatRange(0f, 1.2f).optionalFieldOf("friction").forGetter(BlockProperty::friction),
                Codec.floatRange(0f, 15f).optionalFieldOf("enchant_power", 0f).forGetter(BlockProperty::enchantPower),
                Codec.BOOL.optionalFieldOf("fire_source", false).forGetter(BlockProperty::fireSource),
                Codec.BOOL.optionalFieldOf("sustains_plants", false).forGetter(BlockProperty::sustainsPlants),
                Codec.BOOL.optionalFieldOf("transparent", false).forGetter(BlockProperty::transparent),
                Codec.BOOL.optionalFieldOf("stabiliser", false).forGetter(BlockProperty::stabiliser)
        ).apply(i, BlockProperty::new));

        @Override
        public MapCodec<BlockProperty> codec() {
            return CODEC;
        }

        @Override
        public void apply(BlockProps p) {
            p.light = Math.max(p.light, light);
            p.signal = Math.max(p.signal, signal);
            p.noCollision |= noCollision;
            p.halfShape |= halfShape;
            friction.ifPresent(f -> p.friction = f);
            p.enchantPower += enchantPower;
            p.fireSource |= fireSource;
            p.sustainsPlants |= sustainsPlants;
            p.transparent |= transparent;
            p.stabiliser |= stabiliser;
        }
    }

    /** Hides the block (Sensus: unless revealed by goggles; Tenebrae: while lit, when it also loses collision). */
    public record Visibility(BlockProps.Visibility mode) implements EffectBehaviour {
        private static final Codec<BlockProps.Visibility> MODE = Codec.STRING.xmap(
                s -> BlockProps.Visibility.valueOf(s.toUpperCase(java.util.Locale.ROOT)), m -> m.name().toLowerCase(java.util.Locale.ROOT));
        public static final MapCodec<Visibility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                MODE.fieldOf("mode").forGetter(Visibility::mode)
        ).apply(i, Visibility::new));

        @Override
        public MapCodec<Visibility> codec() {
            return CODEC;
        }

        @Override
        public void apply(BlockProps props) {
            props.visibility = mode;
        }
    }

    /** Hostile mobs cannot spawn on top (Bestia), via MobSpawnEvent.PositionCheck. */
    public record DenySpawns() implements EffectBehaviour {
        public static final MapCodec<DenySpawns> CODEC = MapCodec.unit(DenySpawns::new);

        @Override
        public MapCodec<DenySpawns> codec() {
            return CODEC;
        }

        @Override
        public void apply(BlockProps props) {
            props.denyMonsterSpawns = true;
        }
    }

    /** A redstone signal switches every other effect on the block off (Machina). */
    public record Toggle() implements EffectBehaviour {
        public static final MapCodec<Toggle> CODEC = MapCodec.unit(Toggle::new);

        @Override
        public MapCodec<Toggle> codec() {
            return CODEC;
        }

        @Override
        public boolean isToggle() {
            return true;
        }
    }

    // ---- ticking --------------------------------------------------------------------------------------------------

    /** Gives entities around the block a mob effect every {@code interval} ticks (Aer, Venenum). */
    public record MobEffectAura(Holder<MobEffect> effect, int duration, int amplifier, int interval, double radius,
                                boolean playersOnly) implements EffectBehaviour {
        public static final MapCodec<MobEffectAura> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(MobEffectAura::effect),
                Codec.intRange(1, 72000).fieldOf("duration").forGetter(MobEffectAura::duration),
                Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(MobEffectAura::amplifier),
                Codec.intRange(1, 72000).fieldOf("interval").forGetter(MobEffectAura::interval),
                Codec.doubleRange(0, 32).optionalFieldOf("radius", 1.0).forGetter(MobEffectAura::radius),
                Codec.BOOL.optionalFieldOf("players_only", true).forGetter(MobEffectAura::playersOnly)
        ).apply(i, MobEffectAura::new));

        @Override
        public MapCodec<MobEffectAura> codec() {
            return CODEC;
        }

        @Override
        public int tickInterval() {
            return interval;
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
            Class<? extends LivingEntity> type = playersOnly ? Player.class : LivingEntity.class;
            for (LivingEntity e : level.getEntitiesOfClass(type, box(pos, radius)))
                e.addEffect(new MobEffectInstance(effect, duration, amplifier));
        }
    }

    /** Players nearby lose food (Fames) or regain health while fed (Sano). */
    public record FoodAura(double radius, int interval, float chance, int food, float heal) implements EffectBehaviour {
        public static final MapCodec<FoodAura> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(0, 32).fieldOf("radius").forGetter(FoodAura::radius),
                Codec.intRange(1, 72000).fieldOf("interval").forGetter(FoodAura::interval),
                Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(FoodAura::chance),
                Codec.intRange(-20, 20).optionalFieldOf("food", 0).forGetter(FoodAura::food),
                Codec.floatRange(0, 100).optionalFieldOf("heal", 0f).forGetter(FoodAura::heal)
        ).apply(i, FoodAura::new));

        @Override
        public MapCodec<FoodAura> codec() {
            return CODEC;
        }

        @Override
        public int tickInterval() {
            return interval;
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
            for (Player p : level.getEntitiesOfClass(Player.class, box(pos, radius))) {
                if (p.getAbilities().invulnerable || p.getFoodData().getFoodLevel() <= 0 || level.getRandom().nextFloat() >= chance)
                    continue;
                if (food != 0)
                    p.getFoodData().setFoodLevel(Math.clamp(p.getFoodData().getFoodLevel() + food, 0, 20));
                if (heal > 0)
                    p.heal(heal);
            }
        }
    }

    /** Pulls item entities towards the block (Lucrum). */
    public record AttractItems(double radius, double speed) implements EffectBehaviour {
        public static final MapCodec<AttractItems> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(1, 32).fieldOf("radius").forGetter(AttractItems::radius),
                Codec.doubleRange(0, 2).fieldOf("speed").forGetter(AttractItems::speed)
        ).apply(i, AttractItems::new));

        @Override
        public MapCodec<AttractItems> codec() {
            return CODEC;
        }

        @Override
        public int tickInterval() {
            return 1;
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
            Vec3 centre = Vec3.atCenterOf(pos);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(pos, radius))) {
                if (item.blockPosition().closerThan(pos, 1.5))
                    continue;
                Vec3 v = item.getDeltaMovement();
                item.setDeltaMovement(item.getX() > centre.x ? -speed : speed, v.y, item.getZ() > centre.z ? -speed : speed);
            }
        }
    }

    /** Lightning strikes the block during rain (Tempestas). */
    public record Lightning(int interval, float chance) implements EffectBehaviour {
        public static final MapCodec<Lightning> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 72000).fieldOf("interval").forGetter(Lightning::interval),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Lightning::chance)
        ).apply(i, Lightning::new));

        @Override
        public MapCodec<Lightning> codec() {
            return CODEC;
        }

        @Override
        public int tickInterval() {
            return interval;
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
            if (!level.isRainingAt(pos.above()) || level.getRandom().nextFloat() >= chance)
                return;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(pos.above()));
                level.addFreshEntity(bolt);
            }
        }
    }

    /**
     * Players above the block may fly, up to {@code height} blocks, plus that much again for every block with this
     * effect stacked directly below (Volatus).
     */
    public record Flight(int height, int interval) implements EffectBehaviour {
        public static final MapCodec<Flight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 256).fieldOf("height").forGetter(Flight::height),
                Codec.intRange(1, 200).optionalFieldOf("interval", 10).forGetter(Flight::interval)
        ).apply(i, Flight::new));

        @Override
        public MapCodec<Flight> codec() {
            return CODEC;
        }

        @Override
        public int tickInterval() {
            return interval;
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
            if (!level.isEmptyBlock(pos.above()))
                return;
            int size = height;
            BlockPos below = pos.below();
            for (Resolved r; (r = InfusionWorld.get(level, below)) != null && r.find(Flight.class) != null; below = below.below())
                size += height;
            AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 1 + size, pos.getZ() + 1);
            for (Player p : level.getEntitiesOfClass(Player.class, column))
                FlightGrants.grant(p, gameTime + interval * 2L + 5);
        }
    }

    /**
     * Spawns mobs on top now and then: hostiles in the dark (Mortuus), animals in the light (Victus). Skips when
     * {@code max_nearby} of the same kind are already close, which the original did not, so it cannot flood a world.
     */
    public record Spawner(List<EntityType<?>> entities, int interval, float chance, boolean dark, int maxNearby) implements EffectBehaviour {
        public static final MapCodec<Spawner> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().fieldOf("entities").forGetter(Spawner::entities),
                Codec.intRange(1, 72000).fieldOf("interval").forGetter(Spawner::interval),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Spawner::chance),
                Codec.BOOL.fieldOf("dark").forGetter(Spawner::dark),
                Codec.intRange(1, 64).optionalFieldOf("max_nearby", 6).forGetter(Spawner::maxNearby)
        ).apply(i, Spawner::new));

        @Override
        public MapCodec<Spawner> codec() {
            return CODEC;
        }

        @Override
        public int tickInterval() {
            return interval;
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
            BlockPos at = pos.above();
            if (!level.isEmptyBlock(at) || !level.isEmptyBlock(at.above()) || level.getRandom().nextFloat() >= chance)
                return;
            boolean lit = level.getMaxLocalRawBrightness(at) > 8;
            if (lit == dark)
                return;
            EntityType<?> type = entities.get(level.getRandom().nextInt(entities.size()));
            if (level.getEntitiesOfClass(Entity.class, box(pos, 8), n -> n.getType() == type).size() >= maxNearby)
                return;
            Entity e = type.create(level);
            if (e == null)
                return;
            e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
            if (e instanceof Mob mob)
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.SPAWNER, null);
            level.addFreshEntity(e);
        }
    }

    /** Removes matching blocks around the block (Vitium clears flux goo and gas). */
    public record PurgeBlocks(HolderSet<Block> blocks, int radius, int interval) implements EffectBehaviour {
        public static final MapCodec<PurgeBlocks> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks").forGetter(PurgeBlocks::blocks),
                Codec.intRange(1, 8).fieldOf("radius").forGetter(PurgeBlocks::radius),
                Codec.intRange(1, 72000).fieldOf("interval").forGetter(PurgeBlocks::interval)
        ).apply(i, PurgeBlocks::new));

        @Override
        public MapCodec<PurgeBlocks> codec() {
            return CODEC;
        }

        @Override
        public int tickInterval() {
            return interval;
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, Resolved block, long gameTime) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius)))
                if (!p.equals(pos) && level.getBlockState(p).is(blocks))
                    level.removeBlock(p, false);
        }
    }

    // ---- contact (entities on or in the block) --------------------------------------------------------------------

    /** Sets living things touching the block on fire (Infernus). */
    public record Ignite(int seconds) implements EffectBehaviour {
        public static final MapCodec<Ignite> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 600).fieldOf("seconds").forGetter(Ignite::seconds)
        ).apply(i, Ignite::new));

        @Override
        public MapCodec<Ignite> codec() {
            return CODEC;
        }

        @Override
        public void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
            if (dy <= 1 && !level.isClientSide() && entity instanceof LivingEntity && entity.getRemainingFireTicks() < seconds * 20)
                entity.setRemainingFireTicks(seconds * 20);
        }
    }

    /** Holds living things on top in place (Vinculum: everything up to {@code height} blocks above). */
    public record Trap(int height) implements EffectBehaviour {
        public static final MapCodec<Trap> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 3).fieldOf("height").forGetter(Trap::height)
        ).apply(i, Trap::new));

        @Override
        public MapCodec<Trap> codec() {
            return CODEC;
        }

        @Override
        public void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
            if (dy < height && entity instanceof LivingEntity && !(entity instanceof Player p && p.getAbilities().instabuild))
                entity.setDeltaMovement(0, Math.min(0, entity.getDeltaMovement().y), 0);
        }
    }

    /** Pushes entities on top the way the placer faced (Motus). */
    public record Push(double speed) implements EffectBehaviour {
        public static final MapCodec<Push> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(0, 2).fieldOf("speed").forGetter(Push::speed)
        ).apply(i, Push::new));

        @Override
        public MapCodec<Push> codec() {
            return CODEC;
        }

        @Override
        public void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
            if (dy > 1 || entity.isShiftKeyDown())
                return;
            Direction d = block.entry().facing().orElse(Direction.NORTH);
            entity.setDeltaMovement(d.getStepX() * speed, entity.getDeltaMovement().y, d.getStepZ() * speed);
        }
    }

    /** Swimmable, non-solid block: entities inside sink slowly and take no fall damage; sneak to sink (Limus). */
    public record Ooze(double drag) implements EffectBehaviour {
        public static final MapCodec<Ooze> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(0, 1).optionalFieldOf("drag", 0.5).forGetter(Ooze::drag)
        ).apply(i, Ooze::new));

        @Override
        public MapCodec<Ooze> codec() {
            return CODEC;
        }

        @Override
        public void apply(BlockProps props) {
            props.noCollision = true;
            props.fallDamage = 0f;
        }

        @Override
        public void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
            if (dy != 0)
                return;
            Vec3 v = entity.getDeltaMovement();
            double y = entity.isShiftKeyDown() ? Math.max(v.y, -0.1) : Math.max(v.y * drag, entity instanceof Player ? 0.04 : -0.05);
            if (entity.horizontalCollision)
                y = 0.15;
            entity.setDeltaMovement(v.x * drag, y, v.z * drag);
            entity.resetFallDistance();
        }
    }

    /** Teleports living things that touch or use the block to a random safe spot nearby (Alienis). */
    public record RandomTeleport(int radius, int cooldown) implements EffectBehaviour {
        public static final MapCodec<RandomTeleport> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 32).fieldOf("radius").forGetter(RandomTeleport::radius),
                Codec.intRange(0, 1200).optionalFieldOf("cooldown", 40).forGetter(RandomTeleport::cooldown)
        ).apply(i, RandomTeleport::new));

        @Override
        public MapCodec<RandomTeleport> codec() {
            return CODEC;
        }

        @Override
        public void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
            if (dy <= 1 && level instanceof ServerLevel sl && entity instanceof LivingEntity living && !living.isOnPortalCooldown())
                warp(sl, pos, living);
        }

        @Override
        public InteractionResult use(Level level, BlockPos pos, Resolved block, Player player, InteractionHand hand) {
            if (level instanceof ServerLevel sl)
                warp(sl, pos, player);
            return InteractionResult.SUCCESS;
        }

        private void warp(ServerLevel level, BlockPos pos, LivingEntity entity) {
            RandomSource r = level.getRandom();
            for (int tries = 0; tries < 64; tries++) {
                BlockPos p = pos.offset(r.nextInt(radius * 2) - radius, r.nextInt(radius * 2) - radius, r.nextInt(radius * 2) - radius);
                if (!level.isEmptyBlock(p.below()) && level.isEmptyBlock(p) && level.isEmptyBlock(p.above())) {
                    entity.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    entity.setPortalCooldown(cooldown);
                    level.playSound(null, p, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6f, 1.2f);
                    return;
                }
            }
        }
    }

    /** May blow up when stood on or used (Perditio: 1 in 20 by default). */
    public record Volatile(float chance, float power) implements EffectBehaviour {
        public static final MapCodec<Volatile> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Volatile::chance),
                Codec.floatRange(0, 10).fieldOf("power").forGetter(Volatile::power)
        ).apply(i, Volatile::new));

        @Override
        public MapCodec<Volatile> codec() {
            return CODEC;
        }

        @Override
        public void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
            if (dy == 1 && entity.tickCount % 10 == 0 && entity instanceof LivingEntity)
                maybeExplode(level, pos);
        }

        @Override
        public InteractionResult use(Level level, BlockPos pos, Resolved block, Player player, InteractionHand hand) {
            maybeExplode(level, pos);
            return InteractionResult.SUCCESS;
        }

        private void maybeExplode(Level level, BlockPos pos) {
            if (!level.isClientSide() && level.getRandom().nextFloat() < chance)
                level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, Level.ExplosionInteraction.BLOCK);
        }
    }

    /**
     * Linked pair: standing on one moves you on top of the other; use it to travel too. Link with a wand: sneak-use the
     * first block, then sneak-use the second (Iter).
     */
    public record Portal(int cooldown) implements EffectBehaviour {
        public static final MapCodec<Portal> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, 1200).optionalFieldOf("cooldown", 200).forGetter(Portal::cooldown)
        ).apply(i, Portal::new));

        @Override
        public MapCodec<Portal> codec() {
            return CODEC;
        }

        @Override
        public void entityNear(Level level, BlockPos pos, Resolved block, Entity entity, int dy) {
            if (dy == 1 && !level.isClientSide() && entity instanceof LivingEntity living && !living.isOnPortalCooldown())
                travel(level, block, living);
        }

        @Override
        public InteractionResult use(Level level, BlockPos pos, Resolved block, Player player, InteractionHand hand) {
            if (player.isShiftKeyDown() || block.entry().link().isEmpty())
                return InteractionResult.PASS;
            if (!level.isClientSide())
                travel(level, block, player);
            return InteractionResult.SUCCESS;
        }

        private void travel(Level level, Resolved block, LivingEntity entity) {
            BlockPos dest = block.entry().link().orElse(null);
            if (dest == null || !level.isLoaded(dest))
                return;
            Resolved there = InfusionWorld.get(level, dest);
            if (there == null || there.find(Portal.class) == null || !level.isEmptyBlock(dest.above()) || !level.isEmptyBlock(dest.above(2)))
                return;
            entity.teleportTo(dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5);
            entity.setPortalCooldown(cooldown);
            level.playSound(null, dest, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6f, 0.8f);
        }
    }

    // ---- use and capabilities -------------------------------------------------------------------------------------

    /** A disguised infinite fluid source: pipes can drain it and an empty bucket fills from it (Aqua). */
    public record FluidSource(Fluid fluid) implements EffectBehaviour {
        public static final MapCodec<FluidSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidSource::fluid)
        ).apply(i, FluidSource::new));

        @Override
        public MapCodec<FluidSource> codec() {
            return CODEC;
        }

        @Override
        public InteractionResult use(Level level, BlockPos pos, Resolved block, Player player, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Items.BUCKET) || fluid.getBucket() == Items.AIR)
                return InteractionResult.PASS;
            if (!level.isClientSide()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(fluid.getBucket())));
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** A disguised inventory with {@code rows} rows of nine (Vacuos). */
    public record Storage(int rows) implements EffectBehaviour {
        public static final MapCodec<Storage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 6).fieldOf("rows").forGetter(Storage::rows)
        ).apply(i, Storage::new));

        @Override
        public MapCodec<Storage> codec() {
            return CODEC;
        }

        @Override
        public InteractionResult use(Level level, BlockPos pos, Resolved block, Player player, InteractionHand hand) {
            if (player.isShiftKeyDown())
                return InteractionResult.PASS;
            if (level instanceof ServerLevel sl)
                StorageEffects.open(sl, pos, rows, player);
            return InteractionResult.SUCCESS;
        }
    }

    /** Acts as an extension of a linked inventory for pipes and hoppers. Link with a wand (Permutatio). */
    public record InventoryLink() implements EffectBehaviour {
        public static final MapCodec<InventoryLink> CODEC = MapCodec.unit(InventoryLink::new);

        @Override
        public MapCodec<InventoryLink> codec() {
            return CODEC;
        }
    }

    /** Use with a solid block to make this block look like it; sneak-use with an empty hand to undo (Superbia). */
    public record Disguise() implements EffectBehaviour {
        public static final MapCodec<Disguise> CODEC = MapCodec.unit(Disguise::new);

        @Override
        public MapCodec<Disguise> codec() {
            return CODEC;
        }

        @Override
        public InteractionResult use(Level level, BlockPos pos, Resolved block, Player player, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty() && player.isShiftKeyDown() && block.entry().disguise().isPresent()) {
                if (!level.isClientSide())
                    InfusionWorld.update(level, pos, e -> e.withDisguise(Optional.empty()));
                return InteractionResult.SUCCESS;
            }
            if (!(held.getItem() instanceof BlockItem bi) || bi.getBlock() == block.block())
                return InteractionResult.PASS;
            BlockState look = bi.getBlock().defaultBlockState();
            if (!look.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                return InteractionResult.PASS;
            if (!level.isClientSide())
                InfusionWorld.update(level, pos, e -> e.withDisguise(Optional.of(look)));
            return InteractionResult.SUCCESS;
        }
    }

    // ---- client -----------------------------------------------------------------------------------------------------

    /** Particles around the block for players nearby (Praecantatio sparkles). */
    public record Particles(ParticleOptions particle, float chance, int spread) implements EffectBehaviour {
        public static final MapCodec<Particles> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ParticleTypes.CODEC.fieldOf("particle").forGetter(Particles::particle),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Particles::chance),
                Codec.intRange(0, 16).fieldOf("spread").forGetter(Particles::spread)
        ).apply(i, Particles::new));

        @Override
        public MapCodec<Particles> codec() {
            return CODEC;
        }

        @Override
        public void clientTick(Level level, BlockPos pos, Resolved block, RandomSource random) {
            if (random.nextFloat() >= chance)
                return;
            double x = pos.getX() + 0.5 + (random.nextDouble() * 2 - 1) * spread;
            double y = pos.getY() + 0.5 + (random.nextDouble() * 2 - 1) * spread;
            double z = pos.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * spread;
            level.addParticle(particle, x, y, z, 0, 0.02, 0);
        }
    }

    /** Remembers which players a Flight block let fly, so leaving the column takes it away again. */
    public static final class FlightGrants {
        private FlightGrants() {
        }

        static void grant(Player p, long until) {
            if (p.isCreative() || p.isSpectator())
                return;
            boolean had = p.getData(TIRegistry.FLIGHT_UNTIL) > 0;
            p.setData(TIRegistry.FLIGHT_UNTIL, until);
            if (!had || !p.getAbilities().mayfly) {
                p.getAbilities().mayfly = true;
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
            }
        }

        /** From the player tick: take flight away once no Flight block has renewed it. */
        public static void expire(Player p, long gameTime) {
            long until = p.getData(TIRegistry.FLIGHT_UNTIL);
            if (until <= 0 || gameTime <= until)
                return;
            p.setData(TIRegistry.FLIGHT_UNTIL, 0L);
            if (p.isCreative() || p.isSpectator())
                return;
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
    }
}
