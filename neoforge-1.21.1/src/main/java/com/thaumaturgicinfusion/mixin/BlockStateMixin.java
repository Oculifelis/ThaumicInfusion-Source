package com.thaumaturgicinfusion.mixin;

import com.thaumaturgicinfusion.effect.BlockProps;
import com.thaumaturgicinfusion.world.InfusionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Overrides the NeoForge {@link IBlockStateExtension} defaults (same bodies) and layers the infusion's block
 * properties on top: Lux light, Gelum friction, Cognitio enchanting power, Ignis fire source, Messis soil,
 * Potentia redstone connection.
 */
@Mixin(BlockState.class)
public abstract class BlockStateMixin implements IBlockStateExtension {
    @Unique
    private BlockState ti$self() {
        return (BlockState) (Object) this;
    }

    @Override
    public int getLightEmission(BlockGetter level, BlockPos pos) {
        BlockState self = ti$self();
        int base = self.getBlock().getLightEmission(self, level, pos);
        BlockProps p = InfusionHooks.props(level, pos, self);
        return p == null ? base : Math.max(base, p.light);
    }

    @Override
    public float getFriction(LevelReader level, BlockPos pos, Entity entity) {
        BlockState self = ti$self();
        BlockProps p = InfusionHooks.props(level, pos, self);
        return p != null && !Float.isNaN(p.friction) ? p.friction : self.getBlock().getFriction(self, level, pos, entity);
    }

    @Override
    public float getEnchantPowerBonus(LevelReader level, BlockPos pos) {
        BlockState self = ti$self();
        BlockProps p = InfusionHooks.props(level, pos, self);
        return self.getBlock().getEnchantPowerBonus(self, level, pos) + (p == null ? 0 : p.enchantPower);
    }

    @Override
    public boolean isFireSource(LevelReader level, BlockPos pos, Direction side) {
        BlockState self = ti$self();
        BlockProps p = InfusionHooks.props(level, pos, self);
        return p != null && p.fireSource || self.getBlock().isFireSource(self, level, pos, side);
    }

    @Override
    public TriState canSustainPlant(BlockGetter level, BlockPos soil, Direction facing, BlockState plant) {
        BlockState self = ti$self();
        BlockProps p = InfusionHooks.props(level, soil, self);
        return p != null && p.sustainsPlants ? TriState.TRUE : self.getBlock().canSustainPlant(self, level, soil, facing, plant);
    }

    @Override
    public boolean isFertile(BlockGetter level, BlockPos pos) {
        BlockState self = ti$self();
        BlockProps p = InfusionHooks.props(level, pos, self);
        return p != null && p.sustainsPlants || self.getBlock().isFertile(self, level, pos);
    }

    @Override
    public boolean canRedstoneConnectTo(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState self = ti$self();
        BlockProps p = InfusionHooks.props(level, pos, self);
        return p != null && p.signal > 0 || self.getBlock().canConnectRedstone(self, level, pos, direction);
    }
}
