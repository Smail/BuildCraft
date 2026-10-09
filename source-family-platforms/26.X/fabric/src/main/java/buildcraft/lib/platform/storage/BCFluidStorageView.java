package buildcraft.lib.platform.storage;

import java.util.Objects;
import java.util.function.Predicate;

import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.fluid.FabricFluidStack;

/** Exposes a native-stack fluid storage through the mutable BuildCraft gameplay carrier. */
public final class BCFluidStorageView implements FilteredFluidStorage<BCFluidStack> {
    private final FluidStorage<FabricFluidStack> delegate;

    public BCFluidStorageView(FluidStorage<FabricFluidStack> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public FluidStorage<FabricFluidStack> nativeStorage() {
        return delegate;
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public BCFluidStack getFluidInTank(int tank) {
        return BCFluidStack.fromNative(delegate.getFluidInTank(tank));
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, BCFluidStack fluid) {
        Objects.requireNonNull(fluid, "fluid");
        return delegate.isFluidValid(tank, fluid.toNative());
    }

    @Override
    public int fill(BCFluidStack fluid, boolean simulate) {
        Objects.requireNonNull(fluid, "fluid");
        return delegate.fill(fluid.toNative(), simulate);
    }

    @Override
    public BCFluidStack drain(BCFluidStack fluid, boolean simulate) {
        Objects.requireNonNull(fluid, "fluid");
        return BCFluidStack.fromNative(delegate.drain(fluid.toNative(), simulate));
    }

    @Override
    public BCFluidStack drain(int amount, boolean simulate) {
        return BCFluidStack.fromNative(delegate.drain(amount, simulate));
    }

    @Override
    public BCFluidStack drain(Predicate<BCFluidStack> filter, int amount, boolean simulate) {
        Objects.requireNonNull(filter, "filter");
        Predicate<FabricFluidStack> nativeFilter = stack -> filter.test(BCFluidStack.fromNative(stack));
        if (delegate instanceof FilteredFluidStorage<FabricFluidStack> filtered) {
            return BCFluidStack.fromNative(filtered.drain(nativeFilter, amount, simulate));
        }
        // Ordinary per-tank fallback when the backing storage has no native filtered extraction.
        for (int tank = 0; tank < delegate.getTanks(); tank++) {
            FabricFluidStack current = delegate.getFluidInTank(tank);
            if (!current.isEmpty() && nativeFilter.test(current)) {
                FabricFluidStack drained = delegate.drain(current.copyWithAmount(amount), simulate);
                if (!drained.isEmpty()) {
                    return BCFluidStack.fromNative(drained);
                }
            }
        }
        return BCFluidStack.EMPTY;
    }
}
