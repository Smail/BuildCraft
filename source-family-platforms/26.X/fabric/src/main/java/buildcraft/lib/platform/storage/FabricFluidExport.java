package buildcraft.lib.platform.storage;

import java.util.Iterator;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.IntStream;

import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.fluid.FabricFluidStack;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Indexed Tank/TankManager export. The backing owner supplies its one shared journal. */
public final class FabricFluidExport implements SlottedStorage<FluidVariant> {
    private final TankAccess storage;
    private final TransferJournal<?> journal;
    private final Map<Integer, TankView> slots = new HashMap<>();

    public FabricFluidExport(TankAccess storage, TransferJournal<?> journal) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.journal = Objects.requireNonNull(journal, "backing storage journal");
    }

    public TankAccess backingStorage() {
        return storage;
    }

    public interface TankAccess extends FluidStorage<FabricFluidStack> {
        int fillTank(int tank, FabricFluidStack fluid, boolean simulate);
        FabricFluidStack drainTank(int tank, FabricFluidStack fluid, boolean simulate);
    }

    @Override
    public int getSlotCount() {
        return storage.getTanks();
    }

    @Override
    public SingleSlotStorage<FluidVariant> getSlot(int slot) {
        Objects.checkIndex(slot, getSlotCount());
        return slots.computeIfAbsent(slot, TankView::new);
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        return IntStream.range(0, getSlotCount()).<StorageView<FluidVariant>>mapToObj(this::getSlot).iterator();
    }

    @Override
    public long insert(FluidVariant resource, long maximum, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maximum);
        try (Transaction operation = Transaction.openNested(Objects.requireNonNull(transaction, "transaction"))) {
            long moved = 0;
            for (int i = 0; i < getSlotCount() && maximum - moved >= FabricTransferOperations.DROPLETS_PER_MILLIBUCKET; i++) {
                moved += getSlot(i).insert(resource, maximum - moved, operation);
            }
            operation.commit();
            return moved;
        }
    }

    @Override
    public long extract(FluidVariant resource, long maximum, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maximum);
        try (Transaction operation = Transaction.openNested(Objects.requireNonNull(transaction, "transaction"))) {
            long moved = 0;
            for (int i = 0; i < getSlotCount() && maximum - moved >= FabricTransferOperations.DROPLETS_PER_MILLIBUCKET; i++) {
                moved += getSlot(i).extract(resource, maximum - moved, operation);
            }
            operation.commit();
            return moved;
        }
    }

    private final class TankView implements SingleSlotStorage<FluidVariant> {
        private final int index;

        TankView(int index) {
            this.index = index;
        }

        @Override
        public boolean isResourceBlank() {
            return storage.getFluidInTank(index).isEmpty();
        }

        @Override
        public FluidVariant getResource() {
            return storage.getFluidInTank(index).variant();
        }

        @Override
        public long getAmount() {
            return FabricTransferOperations.droplets(storage.getFluidInTank(index).amount());
        }

        @Override
        public long getCapacity() {
            return FabricTransferOperations.droplets(storage.getTankCapacity(index));
        }

        @Override
        public long insert(FluidVariant resource, long maximum, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maximum);
            int requested = FabricTransferOperations.millibuckets(maximum);
            if (requested == 0) {
                return 0;
            }
            try (Transaction operation = Transaction.openNested(Objects.requireNonNull(transaction, "transaction"))) {
                journal.updateSnapshots(operation);
                int moved = storage.fillTank(index, new FabricFluidStack(resource, requested), false);
                FabricTransferOperations.checkTransfer(moved, requested);
                operation.commit();
                return FabricTransferOperations.droplets(moved);
            }
        }

        @Override
        public long extract(FluidVariant resource, long maximum, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maximum);
            int requested = FabricTransferOperations.millibuckets(maximum);
            if (requested == 0) {
                return 0;
            }
            try (Transaction operation = Transaction.openNested(Objects.requireNonNull(transaction, "transaction"))) {
                journal.updateSnapshots(operation);
                FabricFluidStack moved = Objects.requireNonNull(
                    storage.drainTank(index, new FabricFluidStack(resource, requested), false), "extracted fluid");
                if (!moved.isEmpty() && !moved.variant().equals(resource)) {
                    throw new IllegalStateException("Tank extracted a different fluid variant");
                }
                FabricTransferOperations.checkTransfer(moved.amount(), requested);
                operation.commit();
                return FabricTransferOperations.droplets(moved.amount());
            }
        }
    }
}
