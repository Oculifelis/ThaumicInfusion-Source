package com.thaumaturgicinfusion.compat;

import com.thaumaturgicinfusion.ThaumaturgicInfusion;
import com.thaumaturgicinfusion.client.InfusionLooks;
import com.thaumaturgicinfusion.world.InfusionIndex;
import com.thaumaturgicinfusion.world.Resolved;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: a placed infused block names its effects. Client only, read from the synced infusion index. Disguised blocks
 * and blocks drawn as hidden stay silent, so the tooltip never gives one away. Loaded only by Jade.
 */
@WailaPlugin(ThaumaturgicInfusion.MODID)
public final class TIJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Infused.INSTANCE, Block.class);
    }

    private enum Infused implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ThaumaturgicInfusion.MODID, "infusion");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getLevel() instanceof ClientLevel level))
                return;
            InfusionIndex index = InfusionIndex.peek(level);
            if (index == null || index.isEmpty())
                return;
            BlockState state = accessor.getBlockState();
            Resolved r = index.get(accessor.getPosition(), state);
            if (r == null || r.entry().disguise().isPresent()
                    || InfusionLooks.look(level, accessor.getPosition(), state).isAir())
                return;
            MutableComponent names = Component.empty();
            boolean first = true;
            for (ResourceLocation id : r.entry().infusion().effects()) {
                if (!first)
                    names.append(", ");
                names.append(Component.translatable("aspect_effect." + id.getNamespace() + "." + id.getPath()));
                first = false;
            }
            if (!first)
                tooltip.add(Component.translatable("jade." + ThaumaturgicInfusion.MODID + ".infused", names));
        }
    }
}
