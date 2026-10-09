package buildcraft.lib.platform.storage;

import java.util.Iterator;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;

import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.fluid.FabricFluidStack;

import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Lossless native imports; exports receive the backing owner's shared transaction journal. */
public final class StorageAdapters {
    private StorageAdapters() {}

    public static ItemStorage fromNativeItems(ItemStorage storage) { return storage; }
    public static ItemStorage toNativeItems(ItemStorage storage) { return storage; }
    public static EnergyStorage fromNativeEnergy(EnergyStorage storage) { return storage; }
    public static EnergyStorage toNativeEnergy(EnergyStorage storage) { return storage; }
    public static FluidStorage<buildcraft.lib.fluid.BCFluidStack> fromNativeFluids(buildcraft.lib.fluid.BCFluidHandler storage) { return storage; }
    public static buildcraft.lib.fluid.BCFluidHandler toNativeFluids(FluidStorage<buildcraft.lib.fluid.BCFluidStack> storage) {
        if (storage == null) return null;
        if (storage instanceof buildcraft.lib.fluid.BCFluidHandler handler) return handler;
        return new buildcraft.lib.fluid.BCFluidHandler() {
            public int getTanks() { return storage.getTanks(); }
            public buildcraft.lib.fluid.BCFluidStack getFluidInTank(int tank) { return storage.getFluidInTank(tank); }
            public int getTankCapacity(int tank) { return storage.getTankCapacity(tank); }
            public boolean isFluidValid(int tank, buildcraft.lib.fluid.BCFluidStack stack) { return storage.isFluidValid(tank, stack); }
            public int fill(buildcraft.lib.fluid.BCFluidStack stack, FluidAction action) { return storage.fill(stack, action.simulate()); }
            public buildcraft.lib.fluid.BCFluidStack drain(buildcraft.lib.fluid.BCFluidStack stack, FluidAction action) { return storage.drain(stack, action.simulate()); }
            public buildcraft.lib.fluid.BCFluidStack drain(int maximum, FluidAction action) { return storage.drain(maximum, action.simulate()); }
        };
    }

    /** Views a section style storage as a handler that can be exported natively with the owner's journal. */
    public static buildcraft.lib.fluid.JournaledFluidHandler toNativeFluids(
        FluidStorage<buildcraft.lib.fluid.BCFluidStack> storage, TransferJournal<?> journal) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(journal, "journal");
        return new buildcraft.lib.fluid.JournaledFluidHandler() {
            public TransferJournal<?> transferJournal() { return journal; }
            public int getTanks() { return storage.getTanks(); }
            public buildcraft.lib.fluid.BCFluidStack getFluidInTank(int tank) { return storage.getFluidInTank(tank); }
            public int getTankCapacity(int tank) { return storage.getTankCapacity(tank); }
            public boolean isFluidValid(int tank, buildcraft.lib.fluid.BCFluidStack stack) { return storage.isFluidValid(tank, stack); }
            public int fill(buildcraft.lib.fluid.BCFluidStack stack, FluidAction action) { return storage.fill(stack, action.simulate()); }
            public buildcraft.lib.fluid.BCFluidStack drain(buildcraft.lib.fluid.BCFluidStack stack, FluidAction action) { return storage.drain(stack, action.simulate()); }
            public buildcraft.lib.fluid.BCFluidStack drain(int maximum, FluidAction action) { return storage.drain(maximum, action.simulate()); }
        };
    }

    /** Keep each operation atomic even when a caller catches a backing storage failure. */
    private static long atomic(TransactionContext parent, ToLongFunction<Transaction> operation) {
        Objects.requireNonNull(parent, "transaction");
        try (Transaction child = parent.openNested()) {
            long result = operation.applyAsLong(child);
            child.commit();
            return result;
        }
    }

    public static ItemStorage fromNativeItems(SlottedStorage<ItemVariant> storage) {
        if (storage == null) {
            return null;
        }
        return storage instanceof ExportItems exported ? exported.storage : new FabricItemStorage(storage);
    }

    public static SlottedStorage<ItemVariant> toNativeItems(ItemStorage storage, TransferJournal<?> journal) {
        Objects.requireNonNull(storage, "storage");
        if (storage instanceof FabricItemStorage imported) {
            return imported.nativeStorage();
        }
        return new ExportItems(storage, Objects.requireNonNull(journal, "backing storage journal"));
    }

    public static EnergyStorage fromNativeEnergy(team.reborn.energy.api.EnergyStorage storage) {
        if (storage == null) {
            return null;
        }
        return storage instanceof ExportEnergy exported ? exported.storage : new FabricEnergyStorage(storage);
    }

    public static team.reborn.energy.api.EnergyStorage toNativeEnergy(EnergyStorage storage, TransferJournal<?> journal) {
        Objects.requireNonNull(storage, "storage");
        if (storage instanceof FabricEnergyStorage imported) {
            return imported.nativeStorage();
        }
        return new ExportEnergy(storage, Objects.requireNonNull(journal, "backing storage journal"));
    }

    public static FluidStorage<FabricFluidStack> fromNativeFluids(Storage<FluidVariant> storage) {
        if (storage == null) {
            return null;
        }
        return storage instanceof FabricFluidExport exported ? exported.backingStorage() : new FabricFluidStorage(storage);
    }

    public static Storage<FluidVariant> toNativeFluids(FabricFluidStorage storage) {
        return Objects.requireNonNull(storage, "storage").nativeStorage();
    }

    public static SlottedStorage<FluidVariant> toNativeFluids(FabricFluidExport.TankAccess storage, TransferJournal<?> journal) {
        return new FabricFluidExport(storage, journal);
    }

    private static final class ExportItems implements SlottedStorage<ItemVariant> {
        final ItemStorage storage;
        final TransferJournal<?> journal;
        private final Map<Integer, Slot> slots = new HashMap<>();

        ExportItems(ItemStorage storage, TransferJournal<?> journal) {
            this.storage = storage;
            this.journal = journal;
        }

        @Override
        public int getSlotCount() {
            return storage.getSlots();
        }

        @Override
        public SingleSlotStorage<ItemVariant> getSlot(int slot) {
            Objects.checkIndex(slot, getSlotCount());
            return slots.computeIfAbsent(slot, Slot::new);
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return IntStream.range(0, getSlotCount()).<StorageView<ItemVariant>>mapToObj(this::getSlot).iterator();
        }

        @Override
        public long insert(ItemVariant resource, long maximum, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maximum);
            return atomic(transaction, child -> insertAll(resource, maximum, child));
        }

        private long insertAll(ItemVariant resource, long maximum, TransactionContext transaction) {
            long moved = 0;
            // Fill matching stacks before using empty slots, retaining slot order.
            for (int pass = 0; pass < 2 && moved < maximum; pass++) {
                for (int i = 0; i < getSlotCount() && moved < maximum; i++) {
                    ItemStack current = storage.getStackInSlot(i);
                    if ((pass == 0 && !current.isEmpty() && resource.matches(current))
                        || (pass == 1 && current.isEmpty())) {
                        moved += getSlot(i).insert(resource, maximum - moved, transaction);
                    }
                }
            }
            return moved;
        }

        @Override
        public long extract(ItemVariant resource, long maximum, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maximum);
            return atomic(transaction, child -> extractAll(resource, maximum, child));
        }

        private long extractAll(ItemVariant resource, long maximum, TransactionContext transaction) {
            long moved = 0;
            for (int i = 0; i < getSlotCount() && moved < maximum; i++) {
                moved += getSlot(i).extract(resource, maximum - moved, transaction);
            }
            return moved;
        }

        private final class Slot implements SingleSlotStorage<ItemVariant> {
            private final int index;

            Slot(int index) {
                this.index = index;
            }

            @Override
            public boolean isResourceBlank() {
                return storage.getStackInSlot(index).isEmpty();
            }

            @Override
            public ItemVariant getResource() {
                return ItemVariant.of(storage.getStackInSlot(index));
            }

            @Override
            public long getAmount() {
                return storage.getStackInSlot(index).getCount();
            }

            @Override
            public long getCapacity() {
                ItemStack current = storage.getStackInSlot(index);
                return current.isEmpty() ? storage.getSlotLimit(index)
                    : Math.min(storage.getSlotLimit(index), current.getMaxStackSize());
            }

            @Override
            public long insert(ItemVariant resource, long maximum, TransactionContext transaction) {
                StoragePreconditions.notBlankNotNegative(resource, maximum);
                return atomic(transaction, child -> insertChecked(resource, maximum, child));
            }

            private long insertChecked(ItemVariant resource, long maximum, TransactionContext transaction) {
                int requested = (int) Math.min(maximum, Integer.MAX_VALUE);
                journal.updateSnapshots(Objects.requireNonNull(transaction, "transaction"));
                ItemStack remainder = Objects.requireNonNull(
                    storage.insertItem(index, resource.toStack(requested), false), "insertion remainder");
                if (!remainder.isEmpty() && !resource.matches(remainder)) {
                    throw new IllegalStateException("Item storage returned a different insertion remainder");
                }
                long moved = (long) requested - remainder.getCount();
                FabricTransferOperations.checkTransfer(moved, requested);
                return moved;
            }

            @Override
            public long extract(ItemVariant resource, long maximum, TransactionContext transaction) {
                StoragePreconditions.notBlankNotNegative(resource, maximum);
                return atomic(transaction, child -> extractChecked(resource, maximum, child));
            }

            private long extractChecked(ItemVariant resource, long maximum, TransactionContext transaction) {
                if (!resource.matches(storage.getStackInSlot(index))) {
                    return 0;
                }
                int requested = (int) Math.min(maximum, Integer.MAX_VALUE);
                journal.updateSnapshots(Objects.requireNonNull(transaction, "transaction"));
                ItemStack result = Objects.requireNonNull(storage.extractItem(index, requested, false), "extracted stack");
                if (!result.isEmpty() && !resource.matches(result)) {
                    throw new IllegalStateException("Item storage extracted a different resource");
                }
                FabricTransferOperations.checkTransfer(result.getCount(), requested);
                return result.getCount();
            }
        }
    }

    private record ExportEnergy(EnergyStorage storage, TransferJournal<?> journal)
        implements team.reborn.energy.api.EnergyStorage {
        @Override
        public long insert(long maximum, TransactionContext transaction) {
            StoragePreconditions.notNegative(maximum);
            return atomic(transaction, child -> insertChecked(maximum, child));
        }

        private long insertChecked(long maximum, TransactionContext transaction) {
            int requested = (int) Math.min(maximum, Integer.MAX_VALUE);
            journal.updateSnapshots(Objects.requireNonNull(transaction, "transaction"));
            int accepted = storage.receiveEnergy(requested, false);
            FabricTransferOperations.checkTransfer(accepted, requested);
            return accepted;
        }

        @Override
        public long extract(long maximum, TransactionContext transaction) {
            StoragePreconditions.notNegative(maximum);
            return atomic(transaction, child -> extractChecked(maximum, child));
        }

        private long extractChecked(long maximum, TransactionContext transaction) {
            int requested = (int) Math.min(maximum, Integer.MAX_VALUE);
            journal.updateSnapshots(Objects.requireNonNull(transaction, "transaction"));
            int extracted = storage.extractEnergy(requested, false);
            FabricTransferOperations.checkTransfer(extracted, requested);
            return extracted;
        }

        @Override
        public long getAmount() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacity() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public boolean supportsInsertion() {
            return storage.canReceive();
        }

        @Override
        public boolean supportsExtraction() {
            return storage.canExtract();
        }
    }
}
