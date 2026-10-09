package buildcraft.gametest.generic;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.internal.debug.BCLog;
import buildcraft.transport.internal.pipe.IFlowFluid;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.lib.platform.storage.FluidStorage;
import buildcraft.lib.platform.storage.PlatformStorage;

import static buildcraft.gametest.generic.GameTestChecks.CENTER;
import static buildcraft.gametest.generic.GameTestChecks.SETTLE_TICKS;
import static buildcraft.gametest.generic.GameTestChecks.placePipe;
import static buildcraft.gametest.generic.GameTestChecks.require;
import static buildcraft.gametest.generic.GameTestChecks.tile;

/**
 * Fluid has to travel pipe to pipe, not only enter a pipe. A pipe that takes fluid in but never hands it to its
 * neighbour (a stuck direction cooldown, a rejected export, a rolled back transfer) leaves the rest of a pipeline empty.
 */
public final class FluidFlowChecks {
    private static final String PIPE = "buildcrafttransport:gold_fluid";
    private static final int FLOW_TICKS = 80;

    private FluidFlowChecks() {}

    private static int amountIn(GameTestHelper helper, BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        int total = 0;
        for (Direction side : Direction.values()) {
            FluidStorage<FluidStack> storage = PlatformStorage.fluids(helper.getLevel(), helper.absolutePos(pos), side);
            if (storage == null) {
                continue;
            }
            for (int tank = 0; tank < storage.getTanks(); tank++) {
                total += storage.getFluidInTank(tank).getAmount();
            }
        }
        return total;
    }

    /** Pushes water into the pipe at {@code from}, like a pump does. The open end of the line has no neighbour, so a side fill is refused. */
    private static void feed(GameTestHelper helper, BlockPos from) {
        try {
            TilePipeHolder holder = tile(helper, from, TilePipeHolder.class);
            if (holder.getPipe().getFlow() instanceof IFlowFluid flow) {
                flow.insertFluidsForce(new FluidStack(Fluids.WATER, 500), null, FluidAction.EXECUTE);
            }
        } catch (RuntimeException e) {
            BCLog.caught("FluidFlowChecks.feed", e);
            throw e;
        }
    }

    private static void runFlow(GameTestHelper helper, BlockPos source, BlockPos... reached) {
        // Both callbacks are registered up front: the game test runner iterates its action map while ticking, so
        // scheduling from inside a callback crashes it. Feeding before the pipes settled just fills nothing.
        helper.onEachTick(() -> feed(helper, source));
        helper.runAfterDelay(SETTLE_TICKS + FLOW_TICKS, () -> {
            StringBuilder empty = new StringBuilder();
            for (BlockPos pos : reached) {
                if (amountIn(helper, pos) <= 0) {
                    empty.append("\n").append(pos).append(" received no fluid");
                }
            }
            require(helper, empty.isEmpty(), "fluid did not travel along the pipeline:" + empty);
            helper.succeed();
        });
    }

    /** Three connected pipes in a row: the fluid fed into the first must reach the last. */
    public static void fluidTravelsAlongAStraightPipeLine(GameTestHelper helper) {
        BlockPos west = CENTER.west();
        BlockPos east = CENTER.east();
        placePipe(helper, west, PIPE);
        placePipe(helper, CENTER, PIPE);
        placePipe(helper, east, PIPE);
        runFlow(helper, west, CENTER, east);
    }

    /** A junction with a branch up: fluid must reach both the far end of the line and the branch. */
    public static void fluidTravelsThroughAJunction(GameTestHelper helper) {
        BlockPos west = CENTER.west();
        BlockPos east = CENTER.east();
        BlockPos up = CENTER.above();
        placePipe(helper, west, PIPE);
        placePipe(helper, CENTER, PIPE);
        placePipe(helper, east, PIPE);
        placePipe(helper, up, PIPE);
        runFlow(helper, west, CENTER, east, up);
    }

    /**
     * One branch ends in a consumer that is full and refuses the fluid (a halted engine, a full tank). The junction must
     * keep passing fluid on to its other branch instead of stalling behind the blocked one.
     */
    public static void aBlockedBranchDoesNotStallTheJunction(GameTestHelper helper) {
        BlockPos west = CENTER.west();
        BlockPos east = CENTER.east();
        BlockPos up = CENTER.above();
        placePipe(helper, west, PIPE);
        placePipe(helper, CENTER, PIPE);
        placePipe(helper, east, PIPE);
        helper.setBlock(up, BCFactoryBlocks.TANK_BLOCK.get().defaultBlockState());
        FluidStorage<FluidStack> tank = PlatformStorage.fluids(helper.getLevel(), helper.absolutePos(up), Direction.DOWN);
        require(helper, tank != null, "tank above the junction is not reachable");
        for (int i = 0; i < 40 && tank.fill(new FluidStack(Fluids.LAVA, 1000), false) > 0; i++) {
            // fill it with a fluid the pipe will never be able to deliver
        }
        runFlow(helper, west, CENTER, east);
    }
}
