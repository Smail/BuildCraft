package buildcraft.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import buildcraft.gametest.generic.BlockPlacementChecks;
import buildcraft.gametest.generic.EngineOrientationChecks;
import buildcraft.gametest.generic.FluidExposureChecks;
import buildcraft.gametest.generic.FluidFlowChecks;
import buildcraft.gametest.generic.PipeBreakingChecks;
import buildcraft.gametest.generic.PipeReloadChecks;
import buildcraft.lib.BCLib;

/**
 * NeoForge entry point for the loader independent checks in {@code buildcraft.gametest.generic}. Each method only
 * registers a check with NeoForge's annotation; the logic is shared with every other loader and Minecraft version.
 */
@GameTestHolder(BCLib.MODID)
@PrefixGameTestTemplate(false)
public final class BuildCraftGenericGameTests {
    private static final String EMPTY_TEMPLATE = "empty3x3x3";

    private BuildCraftGenericGameTests() {}

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEngineFacesPipeThatWasPlacedFirst(GameTestHelper helper) {
        EngineOrientationChecks.engineFacesPipeThatWasPlacedFirst(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEngineKeepsAValidFacingWhenAnotherPipeIsPlaced(GameTestHelper helper) {
        EngineOrientationChecks.engineKeepsAValidFacingWhenAnotherPipeIsPlaced(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEngineWithoutReceiverTurnsToANewlyPlacedPipe(GameTestHelper helper) {
        EngineOrientationChecks.engineWithoutReceiverTurnsToANewlyPlacedPipe(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEngineTurnsToAnotherReceiverWhenItsReceiverIsRemoved(GameTestHelper helper) {
        EngineOrientationChecks.engineTurnsToAnotherReceiverWhenItsReceiverIsRemoved(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEngineKeepsFacingWhenItsOnlyReceiverIsRemoved(GameTestHelper helper) {
        EngineOrientationChecks.engineKeepsFacingWhenItsOnlyReceiverIsRemoved(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericWrenchCyclesThroughValidReceiversOnly(GameTestHelper helper) {
        EngineOrientationChecks.wrenchCyclesThroughValidReceiversOnly(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericWrenchDoesNothingWithASingleReceiverOrNone(GameTestHelper helper) {
        EngineOrientationChecks.wrenchDoesNothingWithASingleReceiverOrNone(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericPipePlacedInFrontOfAnEngineConnectsToIt(GameTestHelper helper) {
        EngineOrientationChecks.pipePlacedInFrontOfAnEngineConnectsToIt(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericPipeOnlyConnectsToTheSideTheEngineFaces(GameTestHelper helper) {
        EngineOrientationChecks.pipeOnlyConnectsToTheSideTheEngineFaces(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEveryFluidPipeIsReachable(GameTestHelper helper) {
        FluidExposureChecks.everyFluidPipeIsReachable(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericAConnectedFluidPipeSideAcceptsWater(GameTestHelper helper) {
        FluidExposureChecks.aConnectedFluidPipeSideAcceptsWater(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericCombustionEngineAcceptsCoolantOnEverySide(GameTestHelper helper) {
        FluidExposureChecks.combustionEngineAcceptsCoolantOnEverySide(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericTankAcceptsWaterOnEverySide(GameTestHelper helper) {
        FluidExposureChecks.tankAcceptsWaterOnEverySide(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 150)
    public static void genericFluidTravelsAlongAStraightPipeLine(GameTestHelper helper) {
        FluidFlowChecks.fluidTravelsAlongAStraightPipeLine(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 150)
    public static void genericFluidTravelsThroughAJunction(GameTestHelper helper) {
        FluidFlowChecks.fluidTravelsThroughAJunction(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 150)
    public static void genericABlockedBranchDoesNotStallTheJunction(GameTestHelper helper) {
        FluidFlowChecks.aBlockedBranchDoesNotStallTheJunction(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEveryPipeSurvivesARenderPayloadRoundTrip(GameTestHelper helper) {
        PipeReloadChecks.everyPipeSurvivesARenderPayloadRoundTrip(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEveryPipeSurvivesASaveAndLoad(GameTestHelper helper) {
        PipeReloadChecks.everyPipeSurvivesASaveAndLoad(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericBreakingAPipeInSurvivalDropsItsItems(GameTestHelper helper) {
        PipeBreakingChecks.breakingAPipeInSurvivalDropsItsItems(helper);
    }

    @GameTest(templateNamespace = BCLib.MODID, template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void genericEveryBuildCraftTileBlockCanBePlaced(GameTestHelper helper) {
        BlockPlacementChecks.everyBuildCraftTileBlockCanBePlaced(helper);
    }
}
