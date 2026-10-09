package buildcraft.lib.platform.runtime.mixin;

import buildcraft.factory.block.BlockWaterGel;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplosionDamageCalculator.class)
public abstract class WaterGelExplosionMixin {
    @Inject(method = "getBlockExplosionResistance", at = @At("HEAD"), cancellable = true)
    private void buildcraft$gelResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state,
            FluidState fluid, CallbackInfoReturnable<Optional<Float>> result) {
        if (state.getBlock() instanceof BlockWaterGel) {
            float resistance = state.getValue(BlockWaterGel.PROP_STAGE).hardness;
            result.setReturnValue(Optional.of(Math.max(resistance, fluid.getExplosionResistance())));
        }
    }
}
