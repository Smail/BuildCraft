package buildcraft.gametest.generic;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.internal.debug.BCLog;
import buildcraft.transport.internal.pipe.IFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

import static buildcraft.gametest.generic.GameTestChecks.CENTER;
import static buildcraft.gametest.generic.GameTestChecks.placePipe;
import static buildcraft.gametest.generic.GameTestChecks.require;

/**
 * Breaking a pipe in survival must hand back what it carries. Like the original BuildCraft, creative mode drops nothing
 * (vanilla skips the loot table), so only survival is checked.
 */
public final class PipeBreakingChecks {
    private static final int CARRIED = 3;

    private PipeBreakingChecks() {}

    private static int stonesIn(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(Items.STONE)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Survival: the items travelling through the pipe are part of the block's loot. */
    public static void breakingAPipeInSurvivalDropsItsItems(GameTestHelper helper) {
        TilePipeHolder holder = placePipe(helper, CENTER, "buildcrafttransport:stone_item");
        require(helper, holder.getPipe().getFlow() instanceof IFlowItems, "stone_item pipe has no item flow");
        ((IFlowItems) holder.getPipe().getFlow())
            .insertItemsForce(new ItemStack(Items.STONE, CARRIED), Direction.WEST, null, 0.05);
        BlockPos abs = helper.absolutePos(CENTER);
        BlockState state = helper.getLevel().getBlockState(abs);
        try {
            List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), abs, holder);
            require(helper, stonesIn(drops) == CARRIED, "survival loot has " + stonesIn(drops) + " stone, expected " + CARRIED);
        } catch (RuntimeException e) {
            BCLog.caught("PipeBreakingChecks survival", e);
            throw e;
        }
        helper.succeed();
    }
}
