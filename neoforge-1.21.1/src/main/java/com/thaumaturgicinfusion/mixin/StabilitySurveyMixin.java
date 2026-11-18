package com.thaumaturgicinfusion.mixin;

import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.world.InfusionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ordo: an infused block counts as an infusion stabiliser around the Thaumaturge altar. */
@Mixin(InfusionStabilitySurvey.class)
public abstract class StabilitySurveyMixin {
    @Inject(method = "isStabiliser", at = @At("HEAD"), cancellable = true)
    private static void ti$stabiliser(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockProps p = InfusionHooks.props(level, pos, level.getBlockState(pos));
        if (p != null && p.stabiliser)
            cir.setReturnValue(true);
    }
}
