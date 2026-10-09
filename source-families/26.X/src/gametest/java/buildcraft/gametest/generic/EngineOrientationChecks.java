package buildcraft.gametest.generic;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.block.BlockEngine_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.internal.enums.EnumEngineType;
import buildcraft.transport.internal.pipe.IPipe.ConnectedType;
import buildcraft.transport.tile.TilePipeHolder;

import static buildcraft.gametest.generic.GameTestChecks.CENTER;
import static buildcraft.gametest.generic.GameTestChecks.SETTLE_TICKS;
import static buildcraft.gametest.generic.GameTestChecks.beside;
import static buildcraft.gametest.generic.GameTestChecks.placePipe;
import static buildcraft.gametest.generic.GameTestChecks.require;
import static buildcraft.gametest.generic.GameTestChecks.tile;

/**
 * Engine orientation rules of the original BuildCraft code:
 * <ul>
 * <li>An engine placed next to a receiver faces it.</li>
 * <li>A neighbour change only re-orients an engine whose current facing is no longer a valid receiver
 * ({@code rotateIfInvalid}); a valid facing is never changed by placing other blocks.</li>
 * <li>A wrench cycles through the valid receivers only.</li>
 * <li>A pipe connects to an engine only on the side the engine faces.</li>
 * </ul>
 */
public final class EngineOrientationChecks {
    private static final String KINESIS = "buildcrafttransport:wood_power";
    /** Wooden pipes are the engine MJ receivers: item and fluid extraction, and kinesis. */
    private static final List<String> WOODEN_PIPES = List.of(
        "buildcrafttransport:wood_item", "buildcrafttransport:wood_fluid", KINESIS);

    private EngineOrientationChecks() {}

    public static TileEngineBase_BC8 placeEngine(GameTestHelper helper, EnumEngineType type) {
        BlockEngine_BC8 block = (BlockEngine_BC8) BCCoreBlocks.ENGINE_BC8.get();
        helper.setBlock(CENTER, block.defaultBlockState().setValue(block.getEngineProperty(), type));
        TileEngineBase_BC8 engine = tile(helper, CENTER, TileEngineBase_BC8.class);
        engine.onPlacedBy(null, ItemStack.EMPTY);
        return engine;
    }

    private static TileEngineBase_BC8 placeEngine(GameTestHelper helper) {
        return placeEngine(helper, EnumEngineType.STONE);
    }

    private static void requireFacing(GameTestHelper helper, TileEngineBase_BC8 engine, Direction expected, String what) {
        require(helper, engine.getCurrentFacing() == expected,
            what + ": expected the engine to face " + expected + " but it faces " + engine.getCurrentFacing());
    }

    public static void engineFacesPipeThatWasPlacedFirst(GameTestHelper helper) {
        StringBuilder failures = new StringBuilder();
        for (String pipeId : WOODEN_PIPES) {
            for (Direction side : Direction.values()) {
                placePipe(helper, beside(side), pipeId);
                TileEngineBase_BC8 engine = placeEngine(helper);
                if (engine.getCurrentFacing() != side) {
                    failures.append("\n").append(pipeId).append(" on ").append(side)
                        .append(": engine faces ").append(engine.getCurrentFacing());
                } else if (engine.getPortToPower(side) == null) {
                    failures.append("\n").append(pipeId).append(" on ").append(side).append(": no power port");
                }
                helper.setBlock(CENTER, Blocks.AIR);
                helper.setBlock(beside(side), Blocks.AIR);
            }
        }
        require(helper, failures.isEmpty(), "engine did not orient to / find a port on an existing pipe:" + failures);
        helper.succeed();
    }

    public static void engineKeepsAValidFacingWhenAnotherPipeIsPlaced(GameTestHelper helper) {
        placePipe(helper, beside(Direction.EAST), KINESIS);
        TileEngineBase_BC8 engine = placeEngine(helper);
        requireFacing(helper, engine, Direction.EAST, "engine next to a pipe");
        placePipe(helper, beside(Direction.WEST), KINESIS);
        placePipe(helper, beside(Direction.NORTH), KINESIS);
        requireFacing(helper, engine, Direction.EAST, "engine after more pipes were placed around it");
        helper.succeed();
    }

    public static void engineWithoutReceiverTurnsToANewlyPlacedPipe(GameTestHelper helper) {
        TileEngineBase_BC8 engine = placeEngine(helper);
        require(helper, engine.getPortToPower(engine.getCurrentFacing()) == null,
            "a lone engine must not already face a receiver");
        placePipe(helper, beside(Direction.NORTH), KINESIS);
        requireFacing(helper, engine, Direction.NORTH, "engine that faced nothing, after a pipe appeared");
        helper.succeed();
    }

    public static void engineTurnsToAnotherReceiverWhenItsReceiverIsRemoved(GameTestHelper helper) {
        placePipe(helper, beside(Direction.EAST), KINESIS);
        placePipe(helper, beside(Direction.WEST), KINESIS);
        TileEngineBase_BC8 engine = placeEngine(helper);
        Direction first = engine.getCurrentFacing();
        require(helper, first == Direction.EAST || first == Direction.WEST, "engine faces " + first + ", not a pipe");
        helper.setBlock(beside(first), Blocks.AIR);
        requireFacing(helper, engine, first.getOpposite(), "engine after the pipe it faced was removed");
        helper.succeed();
    }

    public static void engineKeepsFacingWhenItsOnlyReceiverIsRemoved(GameTestHelper helper) {
        placePipe(helper, beside(Direction.EAST), KINESIS);
        TileEngineBase_BC8 engine = placeEngine(helper);
        helper.setBlock(beside(Direction.EAST), Blocks.AIR);
        requireFacing(helper, engine, Direction.EAST, "engine with no other receiver to turn to");
        helper.succeed();
    }

    public static void wrenchCyclesThroughValidReceiversOnly(GameTestHelper helper) {
        Set<Direction> receivers = EnumSet.of(Direction.EAST, Direction.WEST, Direction.NORTH);
        for (Direction side : receivers) {
            placePipe(helper, beside(side), KINESIS);
        }
        TileEngineBase_BC8 engine = placeEngine(helper);
        Set<Direction> visited = EnumSet.noneOf(Direction.class);
        visited.add(engine.getCurrentFacing());
        for (int i = 0; i < receivers.size(); i++) {
            InteractionResult result = engine.attemptRotation();
            require(helper, result == InteractionResult.SUCCESS, "wrench rotation " + i + " returned " + result);
            visited.add(engine.getCurrentFacing());
        }
        require(helper, visited.equals(receivers),
            "wrench visited " + visited + " but the valid receivers are " + receivers);
        helper.succeed();
    }

    public static void wrenchDoesNothingWithASingleReceiverOrNone(GameTestHelper helper) {
        TileEngineBase_BC8 engine = placeEngine(helper);
        Direction facing = engine.getCurrentFacing();
        require(helper, engine.attemptRotation() == InteractionResult.FAIL, "wrench rotated an engine with no receiver");
        requireFacing(helper, engine, facing, "engine without receivers after a wrench use");

        placePipe(helper, beside(Direction.EAST), KINESIS);
        requireFacing(helper, engine, Direction.EAST, "engine after its first receiver appeared");
        require(helper, engine.attemptRotation() == InteractionResult.FAIL,
            "wrench rotated an engine that has only one receiver");
        requireFacing(helper, engine, Direction.EAST, "engine with a single receiver after a wrench use");
        helper.succeed();
    }

    public static void pipePlacedInFrontOfAnEngineConnectsToIt(GameTestHelper helper) {
        TileEngineBase_BC8 engine = placeEngine(helper);
        Direction facing = engine.getCurrentFacing();
        TilePipeHolder pipe = placePipe(helper, beside(facing), WOODEN_PIPES.get(0));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            ConnectedType type = pipe.getPipe().getConnectedType(facing.getOpposite());
            require(helper, type == ConnectedType.TILE,
                "pipe in front of the engine (engine facing " + facing + ") is not connected to it, type=" + type);
            require(helper, engine.getPortToPower(facing) != null,
                "engine facing a pipe placed after it found no power port");
            helper.succeed();
        });
    }

    public static void pipeOnlyConnectsToTheSideTheEngineFaces(GameTestHelper helper) {
        TilePipeHolder front = placePipe(helper, beside(Direction.EAST), KINESIS);
        TileEngineBase_BC8 engine = placeEngine(helper);
        requireFacing(helper, engine, Direction.EAST, "engine next to a pipe");
        TilePipeHolder behind = placePipe(helper, beside(Direction.WEST), KINESIS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            ConnectedType frontType = front.getPipe().getConnectedType(Direction.WEST);
            ConnectedType behindType = behind.getPipe().getConnectedType(Direction.EAST);
            require(helper, frontType == ConnectedType.TILE, "pipe the engine faces is not connected, type=" + frontType);
            require(helper, behindType != ConnectedType.TILE,
                "pipe behind the engine connected to its back, type=" + behindType);
            requireFacing(helper, engine, Direction.EAST, "engine after both pipes settled");
            helper.succeed();
        });
    }
}
