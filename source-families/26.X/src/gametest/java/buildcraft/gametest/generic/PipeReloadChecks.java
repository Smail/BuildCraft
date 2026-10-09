package buildcraft.gametest.generic;

import java.io.IOException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.lib.internal.debug.BCLog;
import buildcraft.lib.net.BCNetworkSide;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.transport.internal.pipe.IItemPipe;
import buildcraft.transport.internal.pipe.IFlowFluid;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

import static buildcraft.gametest.generic.GameTestChecks.CENTER;
import static buildcraft.gametest.generic.GameTestChecks.placePipe;
import static buildcraft.gametest.generic.GameTestChecks.require;

/**
 * A client learns about a pipe only through the render payload of a chunk or update packet. If a pipe type writes
 * something its reader does not consume (or the reader throws), the client keeps {@code Pipe.EMPTY}: the pipe is
 * invisible and never connects. These checks run the same write and read code a world reload uses, for every pipe.
 */
public final class PipeReloadChecks {
    private PipeReloadChecks() {}

    private static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** Sends the render payload of a placed pipe to a fresh holder, as a client joining a world would. */
    private static String reloadProblem(GameTestHelper helper, String pipeId, BlockPos pos, TilePipeHolder server) {
        ByteBuf buf = Unpooled.buffer();
        try {
            server.writePayload(TilePipeHolder.NET_RENDER_DATA, new FriendlyByteBuf(buf), BCNetworkSide.SERVER);
            TilePipeHolder client = new TilePipeHolder(helper.absolutePos(pos), server.getBlockState());
            client.setLevel(helper.getLevel());
            FriendlyByteBuf in = new FriendlyByteBuf(buf);
            client.readPayload(TilePipeHolder.NET_RENDER_DATA, in, BCNetworkSide.CLIENT, null);
            if (in.isReadable()) {
                return pipeId + ": reader left " + in.readableBytes() + " unread bytes";
            }
            if (client.getPipe() == Pipe.EMPTY) {
                return pipeId + ": client pipe is still empty after the render payload";
            }
            if (client.getPipe().getDefinition() != server.getPipe().getDefinition()) {
                return pipeId + ": client got a different pipe definition";
            }
            for (Direction side : Direction.values()) {
                if (client.getPipe().isConnected(side) != server.getPipe().isConnected(side)) {
                    return pipeId + ": connection on " + side + " differs after reload";
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            BCLog.caught("PipeReloadChecks " + pipeId, e);
            return pipeId + ": " + e;
        } finally {
            buf.release();
        }
    }

    /** Every pipe, with a neighbour so there is a connection to carry, must survive a render payload round trip. */
    public static void everyPipeSurvivesARenderPayloadRoundTrip(GameTestHelper helper) {
        StringBuilder problems = new StringBuilder();
        int checked = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof IItemPipe)) {
                continue;
            }
            String id = idOf(item);
            try {
                TilePipeHolder placed = placePipe(helper, CENTER, id);
                if (placed.getPipe().getFlow() instanceof IFlowFluid fluidFlow) {
                    fluidFlow.insertFluidsForce(new FluidStack(Fluids.WATER, 500), null, FluidAction.EXECUTE);
                }
                placePipe(helper, CENTER.east(), id);
                String problem = reloadProblem(helper, id, CENTER, placed);
                checked++;
                if (problem != null) {
                    problems.append("\n").append(problem);
                }
            } catch (RuntimeException e) {
                BCLog.caught("PipeReloadChecks " + id, e);
                problems.append("\n").append(id).append(": ").append(e);
            } finally {
                helper.setBlock(CENTER, net.minecraft.world.level.block.Blocks.AIR);
                helper.setBlock(CENTER.east(), net.minecraft.world.level.block.Blocks.AIR);
            }
        }
        require(helper, checked > 0, "found no pipe items to check");
        require(helper, problems.isEmpty(), "pipes that do not survive a reload:" + problems);
        helper.succeed();
    }

    /** The pipe saved to NBT must come back as the same pipe (same definition, same behaviour data). */
    public static void everyPipeSurvivesASaveAndLoad(GameTestHelper helper) {
        StringBuilder problems = new StringBuilder();
        int checked = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof IItemPipe)) {
                continue;
            }
            String id = idOf(item);
            try {
                TilePipeHolder holder = placePipe(helper, CENTER, id);
                CompoundTag saved = holder.getPipe().writeToNbt();
                CompoundTag behaviour = saved.getCompound("beh");
                if (behaviour.contains("currentDir")) {
                    // A directional pipe that had picked a side before the world was saved.
                    behaviour.put("currentDir", NBTUtilBC.writeEnum(Direction.EAST));
                }
                Pipe loaded = new Pipe(holder, saved);
                // A chunk load builds the pipe from the block entity before it has a level. Pipes that hold items
                // (filters) also need the registries the real load supplies, which a bare holder cannot have.
                TilePipeHolder unattached = new TilePipeHolder(helper.absolutePos(CENTER), holder.getBlockState());
                try {
                    new Pipe(unattached, saved);
                } catch (IllegalStateException e) {
                    if (!String.valueOf(e.getMessage()).contains("registry lookup")) {
                        throw e;
                    }
                }
                checked++;
                if (loaded.getDefinition() != holder.getPipe().getDefinition()) {
                    problems.append("\n").append(id).append(": loaded a different definition");
                }
                if (loaded.getBehaviour() == null || loaded.flow == null) {
                    problems.append("\n").append(id).append(": loaded pipe has no behaviour or flow");
                }
            } catch (IOException | RuntimeException e) {
                BCLog.caught("PipeReloadChecks " + id, e);
                problems.append("\n").append(id).append(": ").append(e);
            } finally {
                helper.setBlock(CENTER, net.minecraft.world.level.block.Blocks.AIR);
            }
        }
        require(helper, checked > 0, "found no pipe items to check");
        require(helper, problems.isEmpty(), "pipes that do not survive a save and load:" + problems);
        helper.succeed();
    }
}
