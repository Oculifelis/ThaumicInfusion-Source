package com.thaumaturgicinfusion.client;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.ThaumaturgicInfusion;
import com.thaumaturgicinfusion.content.EssentiaBlockEntity;
import com.thaumaturgicinfusion.world.InfusionIndex;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Mod-bus client setup: the redraw hook and the Essentia Block tints (block from its entity, item from its aspect). */
@EventBusSubscriber(modid = ThaumaturgicInfusion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TIClientMod {
    private TIClientMod() {
    }

    @SubscribeEvent
    static void onSetup(FMLClientSetupEvent event) {
        InfusionIndex.clientRedraw = pos -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer != null)
                mc.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        });
    }

    @SubscribeEvent
    static void onBlockTints(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level != null && pos != null
                && level.getBlockEntity(pos) instanceof EssentiaBlockEntity be ? 0xFF000000 | be.color() : 0xFFFFFFFF,
                TIRegistry.ESSENTIA_BLOCK.get(), TIRegistry.ESSENTIA_BRICKS.get(), TIRegistry.ESSENTIA_CHISELED.get());
    }

    @SubscribeEvent
    static void onItemTints(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            AspectList list = stack.get(TTDataComponents.ASPECTS.get());
            return list == null || list.isEmpty() ? 0xFFFFFFFF : 0xFF000000 | list.entries().get(0).aspect().value().color();
        }, TIRegistry.ESSENTIA_BLOCK.get(), TIRegistry.ESSENTIA_BRICKS.get(), TIRegistry.ESSENTIA_CHISELED.get());
    }
}
