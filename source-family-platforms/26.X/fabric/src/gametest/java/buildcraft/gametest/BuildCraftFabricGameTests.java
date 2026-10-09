package buildcraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.gametest.generic.BlockPlacementChecks;
import buildcraft.gametest.generic.EngineOrientationChecks;
import buildcraft.gametest.generic.FluidExposureChecks;
import buildcraft.gametest.generic.FluidFlowChecks;
import buildcraft.gametest.generic.PipeBreakingChecks;
import buildcraft.gametest.generic.PipeReloadChecks;

/**
 * Fabric entry point for the loader independent checks in {@code buildcraft.gametest.generic}. Each method only
 * registers a check with Fabric's annotation; the logic is shared with every other loader and Minecraft version.
 */
public final class BuildCraftFabricGameTests {
    private static final String STRUCTURE = "buildcraftlib:empty3x3x3";

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void engineFacesPipeThatWasPlacedFirst(GameTestHelper helper) {
        EngineOrientationChecks.engineFacesPipeThatWasPlacedFirst(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void engineKeepsAValidFacingWhenAnotherPipeIsPlaced(GameTestHelper helper) {
        EngineOrientationChecks.engineKeepsAValidFacingWhenAnotherPipeIsPlaced(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void engineWithoutReceiverTurnsToANewlyPlacedPipe(GameTestHelper helper) {
        EngineOrientationChecks.engineWithoutReceiverTurnsToANewlyPlacedPipe(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void engineTurnsToAnotherReceiverWhenItsReceiverIsRemoved(GameTestHelper helper) {
        EngineOrientationChecks.engineTurnsToAnotherReceiverWhenItsReceiverIsRemoved(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void engineKeepsFacingWhenItsOnlyReceiverIsRemoved(GameTestHelper helper) {
        EngineOrientationChecks.engineKeepsFacingWhenItsOnlyReceiverIsRemoved(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void wrenchCyclesThroughValidReceiversOnly(GameTestHelper helper) {
        EngineOrientationChecks.wrenchCyclesThroughValidReceiversOnly(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void wrenchDoesNothingWithASingleReceiverOrNone(GameTestHelper helper) {
        EngineOrientationChecks.wrenchDoesNothingWithASingleReceiverOrNone(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void pipePlacedInFrontOfAnEngineConnectsToIt(GameTestHelper helper) {
        EngineOrientationChecks.pipePlacedInFrontOfAnEngineConnectsToIt(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void pipeOnlyConnectsToTheSideTheEngineFaces(GameTestHelper helper) {
        EngineOrientationChecks.pipeOnlyConnectsToTheSideTheEngineFaces(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void everyFluidPipeIsReachable(GameTestHelper helper) {
        FluidExposureChecks.everyFluidPipeIsReachable(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void aConnectedFluidPipeSideAcceptsWater(GameTestHelper helper) {
        FluidExposureChecks.aConnectedFluidPipeSideAcceptsWater(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void combustionEngineAcceptsCoolantOnEverySide(GameTestHelper helper) {
        FluidExposureChecks.combustionEngineAcceptsCoolantOnEverySide(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void tankAcceptsWaterOnEverySide(GameTestHelper helper) {
        FluidExposureChecks.tankAcceptsWaterOnEverySide(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 150)
    public void fluidTravelsAlongAStraightPipeLine(GameTestHelper helper) {
        FluidFlowChecks.fluidTravelsAlongAStraightPipeLine(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 150)
    public void fluidTravelsThroughAJunction(GameTestHelper helper) {
        FluidFlowChecks.fluidTravelsThroughAJunction(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 150)
    public void aBlockedBranchDoesNotStallTheJunction(GameTestHelper helper) {
        FluidFlowChecks.aBlockedBranchDoesNotStallTheJunction(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void everyPipeSurvivesARenderPayloadRoundTrip(GameTestHelper helper) {
        PipeReloadChecks.everyPipeSurvivesARenderPayloadRoundTrip(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void everyPipeSurvivesASaveAndLoad(GameTestHelper helper) {
        PipeReloadChecks.everyPipeSurvivesASaveAndLoad(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void breakingAPipeInSurvivalDropsItsItems(GameTestHelper helper) {
        PipeBreakingChecks.breakingAPipeInSurvivalDropsItsItems(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 100)
    public void everyBuildCraftTileBlockCanBePlaced(GameTestHelper helper) {
        BlockPlacementChecks.everyBuildCraftTileBlockCanBePlaced(helper);
    }
}
