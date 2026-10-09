package buildcraft.gametest.generic;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Shared helpers for the loader independent BuildCraft game tests. Test bodies live in the {@code generic} package and
 * use only vanilla game test API plus BuildCraft classes; each loader (and Minecraft version) adds a thin entry class
 * that registers them with its own annotation.
 */
public final class GameTestChecks {
    /** Template every entry class must use: an empty 3x3x3 volume. */
    public static final String EMPTY_TEMPLATE = "empty3x3x3";
    /** Ticks pipes need to settle their connections after a placement. */
    public static final int SETTLE_TICKS = 5;
    /** The block under test, in the middle of the empty template. */
    public static final BlockPos CENTER = new BlockPos(1, 1, 1);

    private GameTestChecks() {}

    public static void require(GameTestHelper helper, boolean condition, String message) {
        Objects.requireNonNull(helper, "helper");
        Objects.requireNonNull(message, "message");
        if (!condition) {
            helper.fail(message);
            throw new IllegalStateException(message);
        }
    }

    public static <T extends BlockEntity> T tile(GameTestHelper helper, BlockPos pos, Class<T> type) {
        Objects.requireNonNull(helper, "helper");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(type, "type");
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        require(helper, type.isInstance(entity), "expected " + type.getSimpleName() + " at " + pos + " but found " + entity);
        return type.cast(entity);
    }

    /** Places a pipe holder at {@code pos} the way the item would, and checks the pipe really exists. */
    public static TilePipeHolder placePipe(GameTestHelper helper, BlockPos pos, String pipeItemId) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(pipeItemId, "pipeItemId");
        Item item = ItemLookup.find(pipeItemId);
        require(helper, item != null && item != Items.AIR, "missing pipe item " + pipeItemId);
        helper.setBlock(pos, BCTransportBlocks.pipeHolder.get().defaultBlockState());
        TilePipeHolder holder = tile(helper, pos, TilePipeHolder.class);
        holder.onPlacedBy(null, new ItemStack(item));
        require(helper, holder.getPipe() != Pipe.EMPTY, "placing " + pipeItemId + " did not create a pipe at " + pos);
        return holder;
    }

    public static BlockPos beside(Direction side) {
        return CENTER.relative(Objects.requireNonNull(side, "side"));
    }

    /** Registry lookup that works with both {@code ResourceLocation} and {@code Identifier} based Minecraft versions. */
    private static final class ItemLookup {
        private ItemLookup() {}

        static Item find(String id) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (BuiltInRegistries.ITEM.getKey(item).toString().equals(id)) {
                    return item;
                }
            }
            return null;
        }
    }
}
