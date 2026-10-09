package buildcraft.lib.platform.capability;

import javax.annotation.Nullable;

import buildcraft.lib.internal.tiles.IHasWork;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;

/** Fabric lookup for the existing internal work capability. */
public final class PlatformCapabilities {
    public static final BlockApiLookup<IHasWork, Direction> HAS_WORK = BlockApiLookup.get(
        Identifier.fromNamespaceAndPath("buildcraftlib", "has_work"), IHasWork.class, Direction.class);

    private PlatformCapabilities() {}

    @Nullable
    public static IHasWork hasWork(@Nullable BlockEntity tile, @Nullable Direction face) {
        if (tile == null || tile.getLevel() == null || tile.isRemoved()) {
            return null;
        }
        return HAS_WORK.find(tile.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile, face);
    }
}
