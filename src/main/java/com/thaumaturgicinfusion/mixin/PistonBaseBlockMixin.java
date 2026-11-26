package com.thaumaturgicinfusion.mixin;

import com.thaumaturgicinfusion.world.InfusionWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pistons cannot move an infused block, as in the original, where infused blocks were tile entities. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void ti$infusedStays(BlockState state, Level level, BlockPos pos, Direction direction,
            boolean allowDestroy, Direction pistonFacing, CallbackInfoReturnable<Boolean> cir) {
        if (!state.isAir() && InfusionWorld.get(level, pos) != null)
            cir.setReturnValue(false);
    }
}
