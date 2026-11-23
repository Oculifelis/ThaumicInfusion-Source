package com.thaumaturgicinfusion.content;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.mojang.serialization.Codec;
import com.thaumaturgicinfusion.TIRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the aspect of an Essentia Block (the original kept it in its world data as "aspectTag"). The item carries the
 * same aspect in Thaumaturge's {@code thaumaturge:aspects} component, so tinting, tooltips and drops reuse it.
 */
public class EssentiaBlockEntity extends BlockEntity {
    private static final Codec<Holder<IAspect>> ASPECT_CODEC = RegistryFixedCodec.create(IAspect.REGISTRY_KEY);

    private @Nullable Holder<IAspect> aspect;

    public EssentiaBlockEntity(BlockPos pos, BlockState state) {
        super(TIRegistry.ESSENTIA_BE.get(), pos, state);
    }

    public @Nullable Holder<IAspect> aspect() {
        return aspect;
    }

    /** Colour of the aspect, or white before it is known. */
    public int color() {
        return aspect == null ? 0xFFFFFF : aspect.value().color();
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        aspect = input.contains("aspect")
                ? ASPECT_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), input.get("aspect")).result().orElse(null)
                : null;
        if (level != null && level.isClientSide())
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        if (aspect != null)
            ASPECT_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), aspect).result()
                    .ifPresent(t -> output.put("aspect", t));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        AspectList list = components.get(TTDataComponents.ASPECTS.get());
        if (list != null && !list.isEmpty())
            aspect = list.entries().getFirst().aspect();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (aspect != null)
            components.set(TTDataComponents.ASPECTS.get(), AspectList.of(new AspectInstance(aspect, 1)));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag output) {
        output.remove("aspect");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
