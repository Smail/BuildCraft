package buildcraft.lib.compat.minecraft.gui.mixin;

import buildcraft.lib.compat.minecraft.gui.BCTooltipContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Registry-only tooltip contexts intentionally have no world to expose. */
@Mixin(targets = "net.minecraft.world.item.Item$TooltipContext$2")
public interface TooltipContextLevelAccessor extends BCTooltipContext.LevelAccess {
    @Override
    @Accessor("val$level")
    Level buildcraft$getLevel();
}
