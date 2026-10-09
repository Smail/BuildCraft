package buildcraft.lib.platform.runtime.mixin;

import buildcraft.factory.block.BlockWaterGel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native state hardness preserves the gel curing stages for players and automation. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class WaterGelStrengthMixin {
    @Shadow protected abstract BlockState asState();

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void buildcraft$gelHardness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> result) {
        BlockState state = asState();
        if (state.getBlock() instanceof BlockWaterGel) result.setReturnValue(state.getValue(BlockWaterGel.PROP_STAGE).hardness);
    }
}
