package com.thaumaturgicinfusion.mixin;

import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.world.InfusionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Potentia signal, Spiritus/Limus/Tenebrae collision and Arbor's half shape, on the methods without a NeoForge hook. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Unique
    private BlockProps ti$props(BlockGetter level, BlockPos pos) {
        return InfusionHooks.props(level, pos, (BlockState) (Object) this);
    }

    @Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
    private void ti$signal(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        BlockProps p = ti$props(level, pos);
        if (p != null && p.signal > cir.getReturnValue())
            cir.setReturnValue(p.signal);
    }

    @Inject(method = "getDirectSignal", at = @At("RETURN"), cancellable = true)
    private void ti$directSignal(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        BlockProps p = ti$props(level, pos);
        if (p != null && p.signal > cir.getReturnValue())
            cir.setReturnValue(p.signal);
    }

    @Unique
    private VoxelShape ti$collision(BlockGetter level, BlockPos pos, VoxelShape shape) {
        BlockProps p = ti$props(level, pos);
        if (p == null)
            return shape;
        if (p.noCollision || p.visibility == BlockProps.Visibility.DARK && InfusionHooks.hiddenByLight(level, pos))
            return Shapes.empty();
        return p.halfShape && !shape.isEmpty() ? InfusionHooks.HALF : shape;
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void ti$collision2(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(ti$collision(level, pos, cir.getReturnValue()));
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void ti$collision3(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(ti$collision(level, pos, cir.getReturnValue()));
    }

    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void ti$shape(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        BlockProps p = ti$props(level, pos);
        if (p != null && p.halfShape && !cir.getReturnValue().isEmpty())
            cir.setReturnValue(InfusionHooks.HALF);
    }
}
