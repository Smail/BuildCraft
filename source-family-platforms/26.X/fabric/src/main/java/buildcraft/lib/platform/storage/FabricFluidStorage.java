package buildcraft.lib.platform.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import buildcraft.lib.fluid.FabricFluidStack;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;

/** Millibucket operations over native Fabric tanks, preserving components and rollback. */
public final class FabricFluidStorage implements FilteredFluidStorage<FabricFluidStack> {
    private final Storage<FluidVariant> storage;

    public FabricFluidStorage(Storage<FluidVariant> storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public Storage<FluidVariant> nativeStorage() {
        return storage;
    }

    private List<StorageView<FluidVariant>> views() {
        List<StorageView<FluidVariant>> result = new ArrayList<>();
        storage.forEach(result::add);
        return result;
    }

    @Override
    public int getTanks() {
        return views().size();
    }

    @Override
    public FabricFluidStack getFluidInTank(int tank) {
        StorageView<FluidVariant> view = views().get(tank);
        return new FabricFluidStack(view.getResource(), FabricTransferOperations.millibuckets(view.getAmount()));
    }

    @Override
    public int getTankCapacity(int tank) {
        return FabricTransferOperations.millibuckets(views().get(tank).getCapacity());
    }

    @Override
    public boolean isFluidValid(int tank, FabricFluidStack fluid) {
        Objects.requireNonNull(fluid, "fluid");
        StorageView<FluidVariant> view = views().get(tank);
        if (fluid.isEmpty()) {
            return false;
        }
        // Fabric has no standalone validity query. Probe the actual destination;
        // a non-slotted storage owns insertion at storage level.
        Storage<FluidVariant> destination = view instanceof SingleSlotStorage<FluidVariant> slot ? slot : storage;
        long unit = FabricTransferOperations.DROPLETS_PER_MILLIBUCKET;
        return FabricTransferOperations.transfer(unit, true, tx -> destination.insert(fluid.variant(), unit, tx)) == unit;
    }

    @Override
    public int fill(FabricFluidStack fluid, boolean simulate) {
        Objects.requireNonNull(fluid, "fluid");
        if (fluid.isEmpty()) {
            return 0;
        }
        return FabricTransferOperations.transferFluid(fluid.amount(), simulate,
            (amount, tx) -> storage.insert(fluid.variant(), amount, tx));
    }

    @Override
    public FabricFluidStack drain(FabricFluidStack fluid, boolean simulate) {
        Objects.requireNonNull(fluid, "fluid");
        if (fluid.isEmpty()) {
            return FabricFluidStack.EMPTY;
        }
        int extracted = FabricTransferOperations.transferFluid(fluid.amount(), simulate,
            (amount, tx) -> storage.extract(fluid.variant(), amount, tx));
        return fluid.copyWithAmount(extracted);
    }

    @Override
    public FabricFluidStack drain(int amount, boolean simulate) {
        return drain(fluid -> true, amount, simulate);
    }

    @Override
    public FabricFluidStack drain(Predicate<FabricFluidStack> filter, int amount, boolean simulate) {
        Objects.requireNonNull(filter, "filter");
        if (amount <= 0) {
            return FabricFluidStack.EMPTY;
        }
        // Keep the iterator inside a transaction; native views may expire when
        // a transaction closes. Extract by variant to combine matching tanks.
        try (var transaction = FabricTransferOperations.openTransaction()) {
            for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
                FabricFluidStack current = new FabricFluidStack(view.getResource(),
                    FabricTransferOperations.millibuckets(view.getAmount()));
                if (!current.isEmpty() && filter.test(current)) {
                    FabricFluidStack drained = drain(current.copyWithAmount(amount), simulate);
                    if (!drained.isEmpty()) {
                        if (!simulate) {
                            transaction.commit();
                        }
                        return drained;
                    }
                }
            }
            return FabricFluidStack.EMPTY;
        }
    }
}
