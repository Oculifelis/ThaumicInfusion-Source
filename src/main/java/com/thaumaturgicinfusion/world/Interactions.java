package com.thaumaturgicinfusion.world;

import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.effect.Behaviours;
import com.thaumaturgicinfusion.effect.EffectBehaviour;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Right-clicks on infused blocks (the original's onBlockActivated) and wand linking for Iter and Permutatio, which
 * replaces the original's wand NBT ("id"/"Iter"/"Perm" + coordinates) with the {@code link_target} item component.
 * Runs on both sides with the same decisions, so the client does not also place a block; changes happen server side.
 */
final class Interactions {
    private Interactions() {
    }

    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        InteractionResult result = InteractionResult.PASS;
        if (player.isShiftKeyDown() && held.is(TIRegistry.LINKING_TOOLS))
            result = link(level, pos, player, held);
        if (result == InteractionResult.PASS) {
            Resolved r = InfusionWorld.get(level, pos);
            if (r != null)
                for (EffectBehaviour b : r.behaviours()) {
                    result = b.use(level, pos, r, player, event.getHand());
                    if (result != InteractionResult.PASS)
                        break;
                }
        }
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static InteractionResult link(Level level, BlockPos pos, Player player, ItemStack wand) {
        Resolved r = InfusionWorld.get(level, pos);
        boolean portal = r != null && r.find(Behaviours.Portal.class) != null;
        boolean inventoryLink = r != null && r.find(Behaviours.InventoryLink.class) != null;
        GlobalPos target = wand.get(TIRegistry.LINK_TARGET.get());
        boolean server = !level.isClientSide();

        if (portal || inventoryLink) {
            if (target != null && target.dimension() == level.dimension() && !target.pos().equals(pos)) {
                if (server) {
                    BlockPos other = target.pos();
                    Resolved partner = InfusionWorld.get(level, other);
                    if (portal && (partner == null || partner.find(Behaviours.Portal.class) == null)) {
                        player.sendOverlayMessage(Component.translatable("message.thaumaturgicinfusion.link_failed"));
                    } else {
                        InfusionWorld.update(level, pos, e -> e.withLink(Optional.of(other)));
                        if (portal)
                            InfusionWorld.update(level, other, e -> e.withLink(Optional.of(pos)));
                        player.sendOverlayMessage(Component.translatable("message.thaumaturgicinfusion.linked"));
                    }
                    wand.remove(TIRegistry.LINK_TARGET.get());
                }
                return InteractionResult.SUCCESS;
            }
            if (target == null && r.entry().link().isPresent()) {
                if (server) {
                    BlockPos other = r.entry().link().get();
                    InfusionWorld.update(level, pos, e -> e.withLink(Optional.empty()));
                    if (portal)
                        InfusionWorld.update(level, other, e -> e.link().equals(Optional.of(pos)) ? e.withLink(Optional.empty()) : e);
                    player.sendOverlayMessage(Component.translatable("message.thaumaturgicinfusion.unlinked"));
                }
                return InteractionResult.SUCCESS;
            }
            if (portal) {
                remember(level, pos, player, wand);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        // Not infused: remember an inventory for Permutatio. The client only guesses from the block entity.
        if (level.getBlockEntity(pos) == null)
            return InteractionResult.PASS;
        if (server && level.getCapability(Capabilities.Item.BLOCK, pos, null) != null)
            remember(level, pos, player, wand);
        return InteractionResult.SUCCESS;
    }

    private static void remember(Level level, BlockPos pos, Player player, ItemStack wand) {
        if (level.isClientSide())
            return;
        wand.set(TIRegistry.LINK_TARGET.get(), new GlobalPos(level.dimension(), pos.immutable()));
        player.sendOverlayMessage(Component.translatable("message.thaumaturgicinfusion.remembered"));
    }
}
