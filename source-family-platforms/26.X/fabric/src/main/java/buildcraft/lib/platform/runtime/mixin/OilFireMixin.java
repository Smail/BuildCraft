package buildcraft.lib.platform.runtime.mixin;

import buildcraft.energy.BCEnergyConfig;
import buildcraft.energy.fluid.BCLiquidBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Oil burn settings remain live, using the same probabilities as the other loaders. */
@Mixin(FireBlock.class)
public abstract class OilFireMixin {
    @Inject(method = "getBurnOdds", at = @At("HEAD"), cancellable = true)
    private void buildcraft$oilBurnOdds(BlockState state, CallbackInfoReturnable<Integer> result) {
        if (state.getBlock() instanceof BCLiquidBlock oil) result.setReturnValue(BCEnergyConfig.enableOilBurn ? oil.igniteOdds : 0);
    }
    @Inject(method = "getIgniteOdds", at = @At("HEAD"), cancellable = true)
    private void buildcraft$oilSpreadOdds(BlockState state, CallbackInfoReturnable<Integer> result) {
        if (state.getBlock() instanceof BCLiquidBlock oil) result.setReturnValue(BCEnergyConfig.enableOilBurn ? oil.burnOdds : 0);
    }
}
