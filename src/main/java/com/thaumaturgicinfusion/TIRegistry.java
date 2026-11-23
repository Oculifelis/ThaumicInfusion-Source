package com.thaumaturgicinfusion;

import net.minecraft.world.level.block.state.BlockBehaviour;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.thaumaturgicinfusion.content.EssentiaBlock;
import com.thaumaturgicinfusion.content.EssentiaBlockEntity;
import com.thaumaturgicinfusion.content.EssentiaBlockItem;
import com.thaumaturgicinfusion.content.InfusionCoreBlock;
import com.thaumaturgicinfusion.effect.AspectEffect;
import com.thaumaturgicinfusion.effect.Behaviours;
import com.thaumaturgicinfusion.effect.EffectBehaviour;
import com.thaumaturgicinfusion.world.InfusedBlocks;
import com.thaumaturgicinfusion.world.Infusion;
import com.thaumaturgicinfusion.world.InfusionEvents;
import com.thaumaturgicinfusion.world.StorageEffects;
import com.thaumaturgicinfusion.world.StoredItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.jetbrains.annotations.Nullable;

public final class TIRegistry {
    private static final String MODID = ThaumaturgicInfusion.MODID;

    /** Behaviour types: the code an aspect effect can run. Each one is configured from the effect JSON. */
    public static final ResourceKey<Registry<MapCodec<? extends EffectBehaviour>>> BEHAVIOUR_TYPE_KEY =
            ResourceKey.createRegistryKey(ThaumaturgicInfusion.id("behaviour_type"));
    public static final Registry<MapCodec<? extends EffectBehaviour>> BEHAVIOUR_TYPES = new RegistryBuilder<>(BEHAVIOUR_TYPE_KEY).create();

    /** Datapack registry: one entry per aspect effect, data/&lt;ns&gt;/thaumaturgicinfusion/aspect_effect/&lt;name&gt;.json. */
    public static final ResourceKey<Registry<AspectEffect>> ASPECT_EFFECT_KEY = ResourceKey.createRegistryKey(ThaumaturgicInfusion.id("aspect_effect"));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    private static final DeferredRegister<MapCodec<? extends EffectBehaviour>> BEHAVIOURS = DeferredRegister.create(BEHAVIOUR_TYPE_KEY, MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Items that link Iter portals and remember inventories for Permutatio (sneak + right-click). */
    public static final TagKey<Item> LINKING_TOOLS = TagKey.create(Registries.ITEM, ThaumaturgicInfusion.id("linking_tools"));
    /** Head items that show Sensus blocks. */
    public static final TagKey<Item> REVEALS_VEILED = TagKey.create(Registries.ITEM, ThaumaturgicInfusion.id("reveals_veiled"));

    /** The effects infused into a block item (or a placed block's drop). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Infusion>> INFUSION = COMPONENTS.registerComponentType("infusion",
            b -> b.persistent(Infusion.CODEC).networkSynchronized(Infusion.STREAM_CODEC));
    /** The position a linking tool remembered (replaces the original's wand NBT). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINK_TARGET = COMPONENTS.registerComponentType("link_target",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** Infused positions of a chunk, replacing the original's per-block InfusedBlock wrapper and world data. */
    public static final Supplier<AttachmentType<InfusedBlocks>> INFUSED_BLOCKS = ATTACHMENTS.register("infused_blocks",
            () -> AttachmentType.builder(() -> InfusedBlocks.EMPTY)
                    .serialize(InfusedBlocks.CODEC, b -> !b.isEmpty())
                    .sync(new AttachmentSyncHandler<InfusedBlocks>() {
                        @Override
                        public void write(RegistryFriendlyByteBuf buf, InfusedBlocks value, boolean initialSync) {
                            InfusedBlocks.STREAM_CODEC.encode(buf, value);
                        }

                        @Override
                        public InfusedBlocks read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable InfusedBlocks previous) {
                            InfusedBlocks data = InfusedBlocks.STREAM_CODEC.decode(buf);
                            return holder instanceof LevelChunk chunk ? InfusionEvents.onClientSync(chunk, data) : data;
                        }
                    })
                    .build());
    /** Contents of Vacuos blocks, per chunk (server only). */
    public static final Supplier<AttachmentType<StoredItems>> STORED_ITEMS = ATTACHMENTS.register("stored_items",
            () -> AttachmentType.builder(() -> StoredItems.EMPTY)
                    .serialize(StoredItems.CODEC, s -> !s.isEmpty())
                    .build());
    /** Game time until which Volatus keeps a player's flight. */
    public static final Supplier<AttachmentType<Long>> FLIGHT_UNTIL = ATTACHMENTS.register("flight_until",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    /**
     * Recipe output marker: the infusion altar crafts a sigil carrying the effect, and InfusionEvents turns it into the
     * catalyst block with that effect added. Never obtainable on its own.
     */
    public static final DeferredItem<Item> INFUSION_SIGIL = ITEMS.registerSimpleItem("infusion_sigil");

    public static final DeferredBlock<EssentiaBlock> ESSENTIA_BLOCK = essentia("essentia_block");
    public static final DeferredBlock<EssentiaBlock> ESSENTIA_BRICKS = essentia("essentia_bricks");
    public static final DeferredBlock<EssentiaBlock> ESSENTIA_CHISELED = essentia("essentia_chiseled");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssentiaBlockEntity>> ESSENTIA_BE = BLOCK_ENTITIES.register("essentia_block",
            () -> BlockEntityType.Builder.of(EssentiaBlockEntity::new, ESSENTIA_BLOCK.get(), ESSENTIA_BRICKS.get(), ESSENTIA_CHISELED.get()).build(null));

    public static final DeferredBlock<InfusionCoreBlock> INFUSION_CORE = BLOCKS.registerBlock("infusion_core", InfusionCoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2f, 20f).sound(SoundType.AMETHYST).lightLevel(s -> 8).noOcclusion());
    public static final DeferredItem<BlockItem> INFUSION_CORE_ITEM = ITEMS.registerSimpleBlockItem(INFUSION_CORE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thaumaturgicinfusion"))
            .icon(() -> new ItemStack(INFUSION_CORE_ITEM.get()))
            .displayItems(TIRegistry::fillTab)
            .build());

    static {
        BEHAVIOURS.register("mob_effect_aura", () -> Behaviours.MobEffectAura.CODEC);
        BEHAVIOURS.register("fall_cushion", () -> Behaviours.FallCushion.CODEC);
        BEHAVIOURS.register("hardness", () -> Behaviours.Hardness.CODEC);
        BEHAVIOURS.register("block_property", () -> Behaviours.BlockProperty.CODEC);
        BEHAVIOURS.register("visibility", () -> Behaviours.Visibility.CODEC);
        BEHAVIOURS.register("deny_spawns", () -> Behaviours.DenySpawns.CODEC);
        BEHAVIOURS.register("toggle", () -> Behaviours.Toggle.CODEC);
        BEHAVIOURS.register("food_aura", () -> Behaviours.FoodAura.CODEC);
        BEHAVIOURS.register("attract_items", () -> Behaviours.AttractItems.CODEC);
        BEHAVIOURS.register("lightning", () -> Behaviours.Lightning.CODEC);
        BEHAVIOURS.register("flight", () -> Behaviours.Flight.CODEC);
        BEHAVIOURS.register("spawner", () -> Behaviours.Spawner.CODEC);
        BEHAVIOURS.register("purge_blocks", () -> Behaviours.PurgeBlocks.CODEC);
        BEHAVIOURS.register("ignite", () -> Behaviours.Ignite.CODEC);
        BEHAVIOURS.register("trap", () -> Behaviours.Trap.CODEC);
        BEHAVIOURS.register("push", () -> Behaviours.Push.CODEC);
        BEHAVIOURS.register("ooze", () -> Behaviours.Ooze.CODEC);
        BEHAVIOURS.register("random_teleport", () -> Behaviours.RandomTeleport.CODEC);
        BEHAVIOURS.register("volatile", () -> Behaviours.Volatile.CODEC);
        BEHAVIOURS.register("portal", () -> Behaviours.Portal.CODEC);
        BEHAVIOURS.register("fluid_source", () -> Behaviours.FluidSource.CODEC);
        BEHAVIOURS.register("storage", () -> Behaviours.Storage.CODEC);
        BEHAVIOURS.register("inventory_link", () -> Behaviours.InventoryLink.CODEC);
        BEHAVIOURS.register("disguise", () -> Behaviours.Disguise.CODEC);
        BEHAVIOURS.register("particles", () -> Behaviours.Particles.CODEC);
    }

    private TIRegistry() {
    }

    private static DeferredBlock<EssentiaBlock> essentia(String name) {
        DeferredBlock<EssentiaBlock> block = BLOCKS.registerBlock(name, EssentiaBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.5f, 10f).sound(SoundType.STONE).lightLevel(s -> 15));
        ITEMS.registerItem(name, p -> new EssentiaBlockItem(block.get(), p));
        return block;
    }

    /** One stack per aspect, read from Thaumaturge's aspect registry so datapack aspects get blocks too. */
    public static ItemStack essentiaStack(Item item, Holder<IAspect> aspect) {
        ItemStack stack = new ItemStack(item);
        stack.set(TTDataComponents.ASPECTS.get(), AspectList.of(new AspectInstance(aspect, 1)));
        return stack;
    }

    private static void fillTab(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output out) {
        out.accept(INFUSION_CORE_ITEM.get());
        HolderLookup.Provider holders = params.holders();
        List<Holder.Reference<IAspect>> aspects = holders.lookup(IAspect.REGISTRY_KEY)
                .map(l -> l.listElements().sorted((a, b) -> a.key().location().compareTo(b.key().location())).toList())
                .orElse(List.of());
        for (Item item : List.of(ESSENTIA_BLOCK.asItem(), ESSENTIA_BRICKS.asItem(), ESSENTIA_CHISELED.asItem()))
            for (Holder.Reference<IAspect> aspect : aspects)
                out.accept(essentiaStack(item, aspect));
        // Ready-made infused stone, one per aspect effect, so the effects can be tried without an altar.
        holders.lookup(ASPECT_EFFECT_KEY).ifPresent(l -> l.listElements()
                .sorted((a, b) -> a.key().location().compareTo(b.key().location()))
                .forEach(e -> {
                    ItemStack stack = new ItemStack(Items.STONE_BRICKS);
                    stack.set(INFUSION.get(), new Infusion(List.of(e.key().location())));
                    out.accept(stack);
                }));
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        COMPONENTS.register(bus);
        ATTACHMENTS.register(bus);
        BEHAVIOURS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        bus.addListener(NewRegistryEvent.class, e -> e.register(BEHAVIOUR_TYPES));
        bus.addListener(DataPackRegistryEvent.NewRegistry.class,
                e -> e.dataPackRegistry(ASPECT_EFFECT_KEY, AspectEffect.CODEC, AspectEffect.CODEC));
        bus.addListener(StorageEffects::registerCapabilities);
    }
}
