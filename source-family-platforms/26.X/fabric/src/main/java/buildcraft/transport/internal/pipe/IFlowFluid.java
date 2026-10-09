package buildcraft.transport.internal.pipe;

import javax.annotation.Nullable;

import buildcraft.lib.internal.core.IFluidFilter;
import buildcraft.lib.internal.core.IFluidHandlerAdv;
import buildcraft.transport.internal.pluggable.PipePluggable;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.fluid.BCFluidHandler;
import buildcraft.lib.fluid.BCFluidHandler.FluidAction;

public interface IFlowFluid {
    /** @deprecated use the version below with a simulate paramater. */
    @Nullable
    @Deprecated
    default BCFluidStack tryExtractFluid(int millibuckets, Direction from, @Nullable BCFluidStack filter) {
        return tryExtractFluid(millibuckets, from, filter, FluidAction.SIMULATE);
    }

    /** @param millibuckets
     * @param from
     * @param filter The fluid stack that the extracted fluid must match, or null/empty for any fluid.
     * @return The fluidstack extracted and inserted into the pipe. */
    @Nullable
    BCFluidStack tryExtractFluid(int millibuckets, Direction from, @Nullable BCFluidStack filter, FluidAction simulate);

    /** @deprecated use the version below with a simulate paramater. */
    @Deprecated
    default InteractionResultHolder<BCFluidStack> tryExtractFluidAdv(int millibuckets, Direction from, IFluidFilter filter) {
        return tryExtractFluidAdv(millibuckets, from, filter, FluidAction.SIMULATE);
    }

    /** Advanced version of {@link #tryExtractFluid(int, Direction, BCFluidStack, boolean)}. Note that this only works for
     * instances of {@link BCFluidHandler} that ALSO extends {@link IFluidHandlerAdv}
     * 
     * @param millibuckets
     * @param from
     * @param filter A filter to try and match fluids.
     * @return The fluidstack extracted and inserted into the pipe. If {@link InteractionResultHolder#getResult()} equals
     *         {@link InteractionResult#PASS} then it means that the {@link BCFluidHandler} didn't implement
     *         {@link IFluidHandlerAdv} and you should call the basic version, if you can. */
    InteractionResultHolder<BCFluidStack> tryExtractFluidAdv(int millibuckets, Direction from, IFluidFilter filter, FluidAction simulate);

    /** Attempts to insert a fluid directly into the pipe. Note that this will fail if the pipe currently contains a
     * different fluid type.
     * 
     * @param from The side that the fluid should *not* go in, or null if the fluid may flow in any direction.
     * @return The amount of fluid that was accepted, or 0 if no fluid was accepted. */
    int insertFluidsForce(BCFluidStack fluid, @Nullable Direction from, FluidAction simulate);

    /** Tries to extract fluids directly from the pipe. NOTE: This is intended for {@link PipeBehaviour} and
     * {@link PipePluggable} implementors ONLY! This will result in very buggy behaviour if external tiles try to use
     * this!
     * 
     * @param min The minimum amount of fluid to extract. If less than this amount is in the given center then nothing
     *            will be extracted.
     * @param section The section to extract from. Null means the center.
     * @param simulate
     * @return */
    @Nullable
    BCFluidStack extractFluidsForce(int min, int max, @Nullable Direction section, FluidAction simulate);
}
