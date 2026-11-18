package com.thaumaturgicinfusion.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.thaumaturgicinfusion.TIRegistry;
import com.thaumaturgicinfusion.effect.AspectEffect;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * /thaumaturgicinfusion infuse &lt;effect&gt; adds an effect to the held block item; /thaumaturgicinfusion list shows the loaded
 * effects. Stands in for the original's /ti addinfusion test command (operators only).
 */
final class TICommand {
    private static final SimpleCommandExceptionType NOT_BLOCK = new SimpleCommandExceptionType(Component.translatable("commands.thaumaturgicinfusion.not_block"));
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.translatable("commands.thaumaturgicinfusion.unknown_effect"));

    private TICommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("thaumaturgicinfusion")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(ctx -> {
                    List<Identifier> ids = effects(ctx.getSource()).keySet().stream().sorted().toList();
                    ctx.getSource().sendSuccess(() -> Component.literal(ids.size() + " effects: " + ids), false);
                    return ids.size();
                }))
                .then(Commands.literal("infuse").then(Commands.argument("effect", IdentifierArgument.id())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(effects(ctx.getSource()).keySet(), b))
                        .executes(ctx -> {
                            Identifier id = IdentifierArgument.getId(ctx, "effect");
                            if (!effects(ctx.getSource()).containsKey(id))
                                throw UNKNOWN.create();
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (!(held.getItem() instanceof BlockItem))
                                throw NOT_BLOCK.create();
                            InfusionEvents.infuse(held, new Infusion(List.of(id)));
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.thaumaturgicinfusion.infused", id.toString()), true);
                            return 1;
                        }))));
    }

    private static Registry<AspectEffect> effects(CommandSourceStack source) {
        return source.registryAccess().lookupOrThrow(TIRegistry.ASPECT_EFFECT_KEY);
    }
}
