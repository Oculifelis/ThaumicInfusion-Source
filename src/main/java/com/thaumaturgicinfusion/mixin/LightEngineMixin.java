package com.thaumaturgicinfusion.mixin;

import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.world.InfusionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vitreus: the light engine sees an infused block as air (or as a light block when it also carries Lux), so light
 * passes through it like glass. Runs on the light thread; the index is a concurrent map.
 */
@Mixin(LightEngine.class)
public abstract class LightEngineMixin {
    @Shadow
    @Final
    protected LightChunkGetter chunkSource;

    @Inject(method = "getState", at = @At("RETURN"), cancellable = true)
    private void ti$transparent(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state.isAir())
            return;
        BlockProps p = InfusionHooks.props(chunkSource.getLevel(), pos, state);
        if (p == null || !p.transparent)
            return;
        cir.setReturnValue(p.light > 0
                ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Math.min(15, p.light))
                : Blocks.AIR.defaultBlockState());
    }
}
