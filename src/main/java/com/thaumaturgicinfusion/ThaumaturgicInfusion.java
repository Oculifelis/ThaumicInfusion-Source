package com.thaumaturgicinfusion;

import com.mojang.logging.LogUtils;
import com.thaumaturgicinfusion.world.InfusionEvents;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Thaumaturgic Infusion: a port of Thaumic Infusion (DrunkMafia, Thaumcraft 4 addon), ported to Thaumaturge (NeoForge 26.1.2). */
@Mod(ThaumaturgicInfusion.MODID)
public final class ThaumaturgicInfusion {
    public static final String MODID = "thaumaturgicinfusion";
    public static final Logger LOG = LogUtils.getLogger();

    public ThaumaturgicInfusion(IEventBus modBus) {
        TIRegistry.register(modBus);
        InfusionEvents.register(NeoForge.EVENT_BUS);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
