package buildcraft.gametest.generic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.lib.internal.debug.BCLog;

import static buildcraft.gametest.generic.GameTestChecks.CENTER;
import static buildcraft.gametest.generic.GameTestChecks.require;

/** Catches broken tile classes (static initialisers, constructors) that only fail the first time a block is placed. */
public final class BlockPlacementChecks {
    private BlockPlacementChecks() {}

    /**
     * Places every BuildCraft block that owns a tile entity. A tile class whose static initialiser throws (for
     * example an id allocator used out of order, which depends on which tile class was loaded first) fails here
     * instead of in a player's world.
     */
    public static void everyBuildCraftTileBlockCanBePlaced(GameTestHelper helper) {
        StringBuilder problems = new StringBuilder();
        int placed = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!id.startsWith("buildcraft") || !(block instanceof EntityBlock)) {
                continue;
            }
            try {
                helper.setBlock(CENTER, block.defaultBlockState());
                BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
                // Some blocks (the water spring) only own a tile for certain variants, so a missing tile on the
                // default state is not a failure. A throwing class initialiser or constructor is.
                if (entity != null) {
                    placed++;
                }
            } catch (Throwable error) {
                BCLog.caught("BlockPlacementChecks " + id, error instanceof Exception e ? e : new RuntimeException(error));
                problems.append("\n").append(id).append(": ").append(error);
            } finally {
                helper.setBlock(CENTER, Blocks.AIR);
            }
        }
        require(helper, placed > 0, "found no BuildCraft tile blocks to place");
        require(helper, problems.isEmpty(), "BuildCraft tile blocks that fail to place:" + problems);
        helper.succeed();
    }
}
