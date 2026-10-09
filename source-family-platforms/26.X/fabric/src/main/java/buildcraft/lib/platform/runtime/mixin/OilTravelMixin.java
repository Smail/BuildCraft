package buildcraft.lib.platform.runtime.mixin;

import buildcraft.lib.fluid.BCFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserves oil movement without marking oil as water for drowning, AI or tool speed. */
@Mixin(LivingEntity.class)
public abstract class OilTravelMixin {
    @Shadow protected abstract double getEffectiveGravity();
    @Shadow protected abstract void travelInWater(Vec3 movement, double gravity, boolean falling, double previousY);

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void buildcraft$travelInOil(Vec3 movement, CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!entity.isAffectedByFluids() || entity.isInWater() || entity.isInLava()) return;
        AABB box = entity.getFluidInteractionBox().deflate(0.001);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    pos.set(x, y, z);
                    FluidState fluid = entity.level().getFluidState(pos);
                    if (!(fluid.getType() instanceof BCFluid) || entity.canStandOnFluid(fluid)
                            || y + fluid.getHeight(entity.level(), pos) <= box.minY) continue;
                    travelInWater(movement, getEffectiveGravity(), entity.getDeltaMovement().y <= 0, entity.getY());
                    callback.cancel();
                    return;
                }
            }
        }
    }
}
