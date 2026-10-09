package buildcraft.lib.platform.events.mixin;

import buildcraft.lib.platform.events.BCEvents;
import buildcraft.lib.platform.events.PlatformEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void buildcraft$startTick(CallbackInfo callback) {
        PlatformEvents.firePlayerTick((Player) (Object) this, BCEvents.Phase.START);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void buildcraft$endTick(CallbackInfo callback) {
        PlatformEvents.firePlayerTick((Player) (Object) this, BCEvents.Phase.END);
    }
}
