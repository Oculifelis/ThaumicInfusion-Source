package com.thaumaturgicinfusion.mixin.client;

import com.thaumaturgicinfusion.client.InfusionLooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** What the section mesher sees: hidden Sensus/Tenebrae blocks as air, Superbia as its disguise, Arbor as a slab. */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {
    @Shadow
    @Final
    private ClientLevel level;

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void ti$look(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state.isAir())
            return;
        BlockState look = InfusionLooks.look(level, pos, state);
        if (look != state)
            cir.setReturnValue(look);
    }
}
