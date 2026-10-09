package buildcraft.lib.platform.client.mixin;

import buildcraft.lib.platform.client.PlatformClientModels;
import net.minecraft.client.resources.model.ModelBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces models in vanilla's bake maps before any model dispatch tables are constructed. */
@Mixin(ModelBakery.BakingResult.class)
public abstract class ModelBakingResultMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void buildcraft$replaceModels(CallbackInfo callback) {
        PlatformClientModels.replaceModels((ModelBakery.BakingResult) (Object) this);
    }
}
