package buildcraft.gametest.generic;

import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.block.BlockEngine_BC8;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.internal.enums.EnumEngineType;
import buildcraft.lib.platform.storage.FluidStorage;
import buildcraft.lib.platform.storage.PlatformStorage;

import static buildcraft.gametest.generic.GameTestChecks.CENTER;
import static buildcraft.gametest.generic.GameTestChecks.SETTLE_TICKS;
import static buildcraft.gametest.generic.GameTestChecks.beside;
import static buildcraft.gametest.generic.GameTestChecks.placePipe;
import static buildcraft.gametest.generic.GameTestChecks.require;

/**
 * Pumps, tanks and pipes move fluid by looking up the neighbour's fluid storage through {@link PlatformStorage}, the
 * same path on every loader. A block that never shows up there (for example because a loader export was rejected)
 * silently refuses all automation, so every fluid carrying block must be reachable and must accept fluid.
 */
public final class FluidExposureChecks {
    private static final List<String> FLUID_PIPES = List.of(
        "buildcrafttransport:wood_fluid", "buildcrafttransport:cobblestone_fluid", "buildcrafttransport:clay_fluid",
        "buildcrafttransport:sandstone_fluid", "buildcrafttransport:stone_fluid", "buildcrafttransport:quartz_fluid",
        "buildcrafttransport:void_fluid", "buildcrafttransport:gold_fluid", "buildcrafttransport:iron_fluid",
        "buildcrafttransport:diamond_fluid", "buildcrafttransport:diamond_wood_fluid");

    private FluidExposureChecks() {}

    private static FluidStack water() {
        return new FluidStack(Fluids.WATER, 1000);
    }

    /** @return a description of the problem, or null when the side exposes storage that accepts water. */
    private static String problemAt(GameTestHelper helper, BlockPos pos, Direction side) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(side, "side");
        FluidStorage<FluidStack> storage = PlatformStorage.fluids(helper.getLevel(), helper.absolutePos(pos), side);
        if (storage == null) {
            return side + ": not reachable through the platform fluid lookup";
        }
        if (storage.getTanks() <= 0) {
            return side + ": storage has no tanks";
        }
        if (storage.fill(water(), true) <= 0) {
            return side + ": accepted no water";
        }
        return null;
    }

    private static void requireAllSides(GameTestHelper helper, String what, BlockPos pos) {
        StringBuilder problems = new StringBuilder();
        for (Direction side : Direction.values()) {
            String problem = problemAt(helper, pos, side);
            if (problem != null) {
                problems.append("\n").append(problem);
            }
        }
        require(helper, problems.isEmpty(), what + " does not expose usable fluid storage:" + problems);
    }

    /** Every fluid pipe type must be reachable, because machines push into pipes through this lookup. */
    public static void everyFluidPipeIsReachable(GameTestHelper helper) {
        StringBuilder problems = new StringBuilder();
        for (String pipeId : FLUID_PIPES) {
            placePipe(helper, CENTER, pipeId);
            for (Direction side : Direction.values()) {
                if (PlatformStorage.fluids(helper.getLevel(), helper.absolutePos(CENTER), side) == null) {
                    problems.append("\n").append(pipeId).append(" ").append(side);
                }
            }
        }
        require(helper, problems.isEmpty(), "fluid pipes not reachable through the platform fluid lookup:" + problems);
        helper.succeed();
    }

    /** A pipe side that is connected to another pipe must accept fluid. */
    public static void aConnectedFluidPipeSideAcceptsWater(GameTestHelper helper) {
        placePipe(helper, CENTER, "buildcrafttransport:stone_fluid");
        placePipe(helper, beside(Direction.DOWN), "buildcrafttransport:stone_fluid");
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            String problem = problemAt(helper, CENTER, Direction.DOWN);
            require(helper, problem == null, "connected stone fluid pipe: " + problem);
            helper.succeed();
        });
    }

    public static void combustionEngineAcceptsCoolantOnEverySide(GameTestHelper helper) {
        BlockEngine_BC8 block = (BlockEngine_BC8) BCCoreBlocks.ENGINE_BC8.get();
        helper.setBlock(CENTER, block.defaultBlockState().setValue(block.getEngineProperty(), EnumEngineType.IRON));
        requireAllSides(helper, "combustion engine", CENTER);
        helper.succeed();
    }

    public static void tankAcceptsWaterOnEverySide(GameTestHelper helper) {
        helper.setBlock(CENTER, BCFactoryBlocks.TANK_BLOCK.get().defaultBlockState());
        requireAllSides(helper, "tank", CENTER);
        helper.succeed();
    }
}
