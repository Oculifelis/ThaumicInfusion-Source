package com.thaumaturgicinfusion.world;

import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.effect.Behaviours;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.InfiniteResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * The capability effects: Vacuos (disguised inventory), Permutatio (extension of a linked inventory) and Aqua
 * (infinite water). Capabilities are registered for every block and answer only at infused positions.
 */
public final class StorageEffects {
    private static final MenuType<?>[] MENUS = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};
    private static final Map<Level, Map<Long, Inventory>> OPEN = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private StorageEffects() {
    }

    /** A live inventory over the stored items; every change is written back to the chunk attachment. */
    static final class Inventory extends SimpleContainer {
        private final ServerLevel level;
        private final BlockPos pos;
        private boolean loading;

        Inventory(ServerLevel level, BlockPos pos, int rows) {
            super(rows * 9);
            this.level = level;
            this.pos = pos;
            loading = true;
            List<ItemStack> saved = level.getChunkAt(pos).getData(TIRegistry.STORED_ITEMS).slots().get(pos.asLong());
            if (saved != null)
                for (int i = 0; i < Math.min(saved.size(), getContainerSize()); i++)
                    setItem(i, saved.get(i).copy());
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading)
                return;
            LevelChunk chunk = level.getChunkAt(pos);
            chunk.setData(TIRegistry.STORED_ITEMS, chunk.getData(TIRegistry.STORED_ITEMS).with(pos.asLong(), getItems()));
        }

        @Override
        public boolean stillValid(Player player) {
            Resolved r = InfusionWorld.get(level, pos);
            return r != null && r.find(Behaviours.Storage.class) != null && player.distanceToSqr(pos.getCenter()) < 64;
        }
    }

    private static Inventory inventory(ServerLevel level, BlockPos pos, int rows) {
        Map<Long, Inventory> open = OPEN.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        Inventory inv = open.get(pos.asLong());
        if (inv == null || inv.getContainerSize() != rows * 9) {
            inv = new Inventory(level, pos, rows);
            open.put(pos.asLong(), inv);
        }
        return inv;
    }

    public static void open(ServerLevel level, BlockPos pos, int rows, Player player) {
        Inventory inv = inventory(level, pos, rows);
        Component title = level.getBlockState(pos).getBlock().getName();
        player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> new ChestMenu(MENUS[rows - 1], id, playerInv, inv, rows), title));
    }

    /** Called when an infused block breaks: spill its inventory. */
    static void dropContents(ServerLevel level, BlockPos pos, Resolved r) {
        Behaviours.Storage storage = r == null ? null : r.find(Behaviours.Storage.class);
        if (storage != null)
            Containers.dropContents(level, pos, inventory(level, pos, storage.rows()));
    }

    /** The infusion at pos is gone: forget the live inventory and the stored items. */
    static void forget(Level level, BlockPos pos) {
        Map<Long, Inventory> open = OPEN.get(level);
        if (open != null)
            open.remove(pos.asLong());
        if (level.isClientSide())
            return;
        LevelChunk chunk = level.getChunkAt(pos);
        StoredItems stored = chunk.getExistingDataOrNull(TIRegistry.STORED_ITEMS);
        if (stored != null && stored.slots().containsKey(pos.asLong()))
            chunk.setData(TIRegistry.STORED_ITEMS, stored.without(pos.asLong()));
    }

    static void unloadLevel(Level level) {
        OPEN.remove(level);
    }

    static void unloadChunk(Level level, LevelChunk chunk) {
        Map<Long, Inventory> open = OPEN.get(level);
        if (open != null)
            open.keySet().removeIf(p -> chunk.getPos().equals(net.minecraft.world.level.ChunkPos.containing(BlockPos.of(p))));
    }

    // ---- capabilities -----------------------------------------------------------------------------------------------

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Block[] all = BuiltInRegistries.BLOCK.stream().toArray(Block[]::new);
        event.registerBlock(Capabilities.Item.BLOCK, StorageEffects::itemHandler, all);
        event.registerBlock(Capabilities.Fluid.BLOCK, StorageEffects::fluidHandler, all);
    }

    private static ResourceHandler<ItemResource> itemHandler(Level level, BlockPos pos, BlockState state, BlockEntity be, Direction side) {
        if (!(level instanceof ServerLevel sl))
            return null;
        InfusionIndex index = InfusionIndex.peek(level);
        Resolved r = index == null ? null : index.get(pos, state);
        if (r == null)
            return null;
        Behaviours.Storage storage = r.find(Behaviours.Storage.class);
        if (storage != null)
            return VanillaContainerWrapper.of(inventory(sl, pos, storage.rows()));
        if (r.find(Behaviours.InventoryLink.class) != null && r.entry().link().isPresent() && DEPTH.get() < 4) {
            BlockPos link = r.entry().link().get();
            if (!level.isLoaded(link))
                return null;
            DEPTH.set(DEPTH.get() + 1);
            try {
                return level.getCapability(Capabilities.Item.BLOCK, link, side);
            } finally {
                DEPTH.set(DEPTH.get() - 1);
            }
        }
        return null;
    }

    private static ResourceHandler<FluidResource> fluidHandler(Level level, BlockPos pos, BlockState state, BlockEntity be, Direction side) {
        InfusionIndex index = InfusionIndex.peek(level);
        Resolved r = index == null ? null : index.get(pos, state);
        Behaviours.FluidSource source = r == null ? null : r.find(Behaviours.FluidSource.class);
        return source == null ? null : new InfiniteResourceHandler<>(FluidResource.of(source.fluid()));
    }
}
