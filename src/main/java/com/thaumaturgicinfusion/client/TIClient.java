package com.thaumaturgicinfusion.client;

import com.thaumaturgicinfusion.ThaumaturgicInfusion;
import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.effect.EffectBehaviour;
import com.thaumaturgicinfusion.world.Infusion;
import com.thaumaturgicinfusion.world.InfusionIndex;
import com.thaumaturgicinfusion.world.Resolved;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client side: infused-item tooltips, the Essentia Block tint, and a per-tick pass that keeps the render looks
 * (Sensus, Tenebrae) current, spawns effect particles and shows a block's effects to a player wearing goggles
 * (the original drew its own highlight box with aspect icons).
 */
@EventBusSubscriber(modid = ThaumaturgicInfusion.MODID, value = Dist.CLIENT)
public final class TIClient {
    private static final int PARTICLE_RANGE = 24;

    private TIClient() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        Infusion infusion = event.getItemStack().get(TIRegistry.INFUSION.get());
        if (infusion == null || infusion.isEmpty())
            return;
        event.getToolTip().add(Component.translatable("tooltip.thaumaturgicinfusion.infused").withStyle(ChatFormatting.DARK_PURPLE));
        for (ResourceLocation id : infusion.effects())
            event.getToolTip().add(Component.literal("  ").append(effectName(id)).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static Component effectName(ResourceLocation id) {
        return Component.translatable("aspect_effect." + id.getNamespace() + "." + id.getPath());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused())
            return;
        InfusionIndex index = InfusionIndex.peek(level);
        boolean goggles = player.getItemBySlot(EquipmentSlot.HEAD).is(TIRegistry.REVEALS_VEILED);
        boolean revealChanged = goggles != InfusionLooks.revealed;
        InfusionLooks.revealed = goggles;
        if (index == null || index.isEmpty())
            return;

        Set<Long> lit = new HashSet<>();
        BlockPos near = player.blockPosition();
        for (Resolved r : index.all()) {
            BlockPos pos = r.pos();
            BlockProps p = r.props();
            if (p.visibility == BlockProps.Visibility.REVEALED && revealChanged)
                InfusionIndex.clientRedraw.accept(pos);
            if (p.visibility == BlockProps.Visibility.DARK && level.getMaxLocalRawBrightness(pos.above()) > 8)
                lit.add(pos.asLong());
            if (pos.closerThan(near, PARTICLE_RANGE) && level.getBlockState(pos).getBlock() == r.block())
                for (EffectBehaviour b : r.behaviours())
                    b.clientTick(level, pos, r, level.getRandom());
        }
        if (!lit.equals(InfusionLooks.LIT)) {
            Set<Long> changed = new HashSet<>(lit);
            changed.addAll(InfusionLooks.LIT);
            Set<Long> both = new HashSet<>(lit);
            both.retainAll(InfusionLooks.LIT);
            changed.removeAll(both);
            InfusionLooks.LIT.retainAll(lit);
            InfusionLooks.LIT.addAll(lit);
            changed.forEach(p -> InfusionIndex.clientRedraw.accept(BlockPos.of(p)));
        }

        if (goggles && level.getGameTime() % 10 == 0 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            Resolved r = index.get(hit.getBlockPos(), level.getBlockState(hit.getBlockPos()));
            if (r != null) {
                MutableComponent line = Component.translatable("tooltip.thaumaturgicinfusion.infused").withStyle(ChatFormatting.DARK_PURPLE);
                for (ResourceLocation id : r.entry().infusion().effects())
                    line.append(Component.literal(" ")).append(effectName(id).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
                mc.gui.setOverlayMessage(line, false);
            }
        }
    }

}
