package buildcraft.lib.compat.minecraft.gui;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Reads the exact world captured by vanilla's world-backed tooltip context. */
public final class BCTooltipContext {
    private BCTooltipContext() {}

    public interface LevelAccess {
        Level buildcraft$getLevel();
    }

    @Nullable
    public static Level level(Item.TooltipContext context) {
        Objects.requireNonNull(context, "Tooltip context");
        return context instanceof LevelAccess access ? access.buildcraft$getLevel() : null;
    }
}
