package com.thaumaturgicinfusion;

import com.mojang.logging.LogUtils;
import com.thaumaturgicinfusion.world.InfusionEvents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Thaumaturgic Infusion: a port of Thaumic Infusion (DrunkMafia, Thaumcraft 4 addon), ported to Thaumaturge (NeoForge 1.21.1). */
@Mod(ThaumaturgicInfusion.MODID)
public final class ThaumaturgicInfusion {
    public static final String MODID = "thaumaturgicinfusion";
    public static final Logger LOG = LogUtils.getLogger();

    public ThaumaturgicInfusion(IEventBus modBus) {
        TIRegistry.register(modBus);
        InfusionEvents.register(NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
