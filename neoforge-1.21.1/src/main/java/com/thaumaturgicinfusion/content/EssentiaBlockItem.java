package com.thaumaturgicinfusion.content;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Names the stack after its aspect ("Aer Bricks"), like the original's display names. */
public class EssentiaBlockItem extends BlockItem {
    public EssentiaBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        AspectList aspects = stack.get(TTDataComponents.ASPECTS.get());
        if (aspects == null || aspects.isEmpty())
            return super.getName(stack);
        ResourceLocation id = aspects.entries().getFirst().aspect().unwrapKey().map(k -> k.location()).orElse(null);
        if (id == null)
            return super.getName(stack);
        return Component.translatable(getDescriptionId() + ".named",
                Component.translatable("aspect." + id.getNamespace() + "." + id.getPath()));
    }
}
