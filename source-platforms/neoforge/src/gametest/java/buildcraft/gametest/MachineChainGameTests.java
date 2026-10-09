package buildcraft.gametest;

import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.debug.BCDebugStructures;
import buildcraft.core.debug.BCDebugStructures.Built;
import buildcraft.lib.BCLib;
import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.misc.data.Box;

/**
 * Whole-machine GameTests: complete power and item chains built with the same layouts as the {@code /bcdebug}
 * commands, then left to run for real ticks.
 */
@GameTestHolder(BCLib.MODID)
@PrefixGameTestTemplate(false)
public final class MachineChainGameTests {
    private static final String RIG_TEMPLATE = "empty24x10x16";
    private static final int POLL_TICKS = 10;
    /** Prefix of a poll result that must fail the test immediately instead of waiting for the limit. */
    private static final String FATAL = "FATAL: ";
    private static final GameProfile ACTOR = new GameProfile(
        UUID.fromString("5b7c1f0e-8a47-4f4c-9b1e-2a5f9e0d6c11"), "BCTestMachineChain"
    );

    private MachineChainGameTests() {
    }

    @GameTest(templateNamespace = BCLib.MODID, template = RIG_TEMPLATE, timeoutTicks = 3000)
    public static void creativeEnginesPowerQuarryAndItemsReachTheChest(GameTestHelper helper) {
        Built rig = build(helper, "creative_quarry");
        ChestBlockEntity chest = chestAt(helper, rig, "chest");
        TileQuarry quarry = quarryAt(helper, rig);
        pollUntil(helper, 0, 2900, () -> {
            int items = countAll(chest);
            if (items > 0) {
                return null;
            }
            return "no mined item reached the chest after the timeout (" + describeQuarry(helper, quarry)
                + ", chest items=" + items + ")";
        });
    }

    @GameTest(templateNamespace = BCLib.MODID, template = RIG_TEMPLATE, timeoutTicks = 1500)
    public static void stirlingEnginesPushPowerThroughWoodenAndGoldPipesIntoQuarry(GameTestHelper helper) {
        Built rig = build(helper, "stirling_quarry");
        TileQuarry quarry = quarryAt(helper, rig);
        pollUntil(helper, 0, 1400, () -> {
            // Frame blocks are paid for with MJ, so one frame block proves the engines -> wood -> gold -> quarry chain.
            if (countFrameBlocks(helper, quarry) > 0) {
                return null;
            }
            return "quarry never received usable power from the stirling engines (" + describeQuarry(helper, quarry)
                + ")";
        });
    }

    @GameTest(templateNamespace = BCLib.MODID, template = PipeGameTestSupport.LARGE_EMPTY_TEMPLATE,
        timeoutTicks = 1500)
    public static void itemHubMovesTheWholeStockWithoutLossOrDrops(GameTestHelper helper) {
        Built hub = build(helper, "item_hub");
        ChestBlockEntity source = chestAt(helper, hub, "source");
        ChestBlockEntity[] sinks = {
            chestAt(helper, hub, "sink_north"), chestAt(helper, hub, "sink_south"), chestAt(helper, hub, "sink_east")
        };
        AABB area = AABB.encapsulatingFullBlocks(hub.boundsMin(), hub.boundsMax()).inflate(2);
        pollUntil(helper, 0, 1400, () -> {
            int cobble = 0;
            int apples = 0;
            int iron = 0;
            for (ChestBlockEntity sink : sinks) {
                cobble += PipeGameTestSupport.countItem(sink, Items.COBBLESTONE);
                apples += PipeGameTestSupport.countItem(sink, Items.APPLE);
                iron += PipeGameTestSupport.countItem(sink, Items.IRON_INGOT);
            }
            int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
            if (dropped > 0) {
                return FATAL + "item hub dropped " + dropped + " item entities instead of delivering them";
            }
            boolean done = cobble == 64 && apples == 32 && iron == 16 && countAll(source) == 0;
            if (done) {
                return null;
            }
            if (cobble > 64 || apples > 32 || iron > 16) {
                return FATAL + "item hub duplicated items: cobble=" + cobble + ", apples=" + apples + ", iron=" + iron;
            }
            return "item hub did not finish: sinks hold cobble=" + cobble + "/64, apples=" + apples + "/32, iron="
                + iron + "/16, source still holds " + countAll(source);
        });
    }

    @GameTest(templateNamespace = BCLib.MODID, template = RIG_TEMPLATE, timeoutTicks = 100)
    public static void everyDebugStructureBuildsAndClearsCleanly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BCDebugStructures.Structure structure : BCDebugStructures.all().values()) {
            Built built = build(helper, structure.name());
            for (var anchor : built.anchors().entrySet()) {
                if (level.getBlockState(anchor.getValue()).isAir()) {
                    helper.fail(structure.name() + " anchor '" + anchor.getKey() + "' is air at " + anchor.getValue());
                    return;
                }
            }
            BCDebugStructures.clear(level, structure, built.origin(), Rotation.NONE);
            for (BlockPos pos : BlockPos.betweenClosed(built.boundsMin(), built.boundsMax())) {
                if (!level.getBlockState(pos).isAir()) {
                    helper.fail(structure.name() + " clear left " + level.getBlockState(pos) + " at " + pos.immutable());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static Built build(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player placer = FakePlayerProvider.INSTANCE.getFakePlayer(level, ACTOR, origin);
        try {
            return BCDebugStructures.build(level, BCDebugStructures.get(name), origin, Rotation.NONE, placer);
        } catch (RuntimeException e) {
            helper.fail("could not build " + name + ": " + e.getMessage());
            throw e;
        }
    }

    private static ChestBlockEntity chestAt(GameTestHelper helper, Built built, String anchor) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(built.anchor(anchor));
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            helper.fail(built.structure().name() + " anchor '" + anchor + "' is not a chest");
            throw new IllegalStateException("missing chest " + anchor);
        }
        return chest;
    }

    private static TileQuarry quarryAt(GameTestHelper helper, Built built) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(built.anchor("quarry"));
        if (!(blockEntity instanceof TileQuarry quarry)) {
            helper.fail(built.structure().name() + " anchor 'quarry' is not a TileQuarry");
            throw new IllegalStateException("missing quarry");
        }
        return quarry;
    }

    private static int countAll(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count += container.getItem(slot).getCount();
        }
        return count;
    }

    private static int countFrameBlocks(GameTestHelper helper, TileQuarry quarry) {
        Box frame = quarry.frameBox;
        if (!frame.isInitialized()) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(frame.min(), frame.max())) {
            if (helper.getLevel().getBlockState(pos).is(BCBuildersBlocks.FRAME.get())) {
                count++;
            }
        }
        return count;
    }

    private static String describeQuarry(GameTestHelper helper, TileQuarry quarry) {
        return "frameBox initialised=" + quarry.frameBox.isInitialized() + ", frame blocks="
            + countFrameBlocks(helper, quarry) + ", current task=" + quarry.currentTask;
    }

    /**
     * Calls {@code pending} every few ticks. A null result means the condition holds and the test succeeds; any other
     * result is the failure message used once {@code limitTick} is reached.
     */
    private static void pollUntil(GameTestHelper helper, int tick, int limitTick, Supplier<String> pending) {
        String missing;
        try {
            missing = pending.get();
        } catch (RuntimeException e) {
            helper.fail("machine chain check threw " + e);
            return;
        }
        if (missing == null) {
            helper.succeed();
            return;
        }
        if (missing.startsWith(FATAL)) {
            helper.fail(missing.substring(FATAL.length()));
            return;
        }
        if (tick >= limitTick) {
            helper.fail(missing);
            return;
        }
        helper.runAfterDelay(POLL_TICKS, () -> pollUntil(helper, tick + POLL_TICKS, limitTick, pending));
    }
}
