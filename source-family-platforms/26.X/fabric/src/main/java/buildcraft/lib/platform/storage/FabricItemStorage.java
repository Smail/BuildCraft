package buildcraft.lib.platform.storage;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import static buildcraft.lib.platform.storage.FabricTransferOperations.checkTransfer;
import static buildcraft.lib.platform.storage.FabricTransferOperations.openTransaction;

/** Slot-preserving view of a Fabric inventory. Simulation always rolls back. */
public final class FabricItemStorage implements ItemStorage {
    private final SlottedStorage<ItemVariant> storage;

    public FabricItemStorage(SlottedStorage<ItemVariant> storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public SlottedStorage<ItemVariant> nativeStorage() {
        return storage;
    }

    @Override
    public int getSlots() {
        return storage.getSlotCount();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        SingleSlotStorage<ItemVariant> view = storage.getSlot(slot);
        return view.getResource().toStack(boundedAmount(view.getAmount()));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        SingleSlotStorage<ItemVariant> view = storage.getSlot(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = openTransaction()) {
            long inserted = view.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            checkTransfer(inserted, stack.getCount());
            ItemStack remainder = stack.copyWithCount(stack.getCount() - (int) inserted);
            if (!simulate) {
                transaction.commit();
            }
            return remainder;
        }
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SingleSlotStorage<ItemVariant> view = storage.getSlot(slot);
        if (amount <= 0 || view.isResourceBlank()) {
            return ItemStack.EMPTY;
        }
        ItemVariant resource = view.getResource();
        try (Transaction transaction = openTransaction()) {
            long extracted = view.extract(resource, amount, transaction);
            checkTransfer(extracted, amount);
            ItemStack result = resource.toStack((int) extracted);
            if (!simulate) {
                transaction.commit();
            }
            return result;
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return boundedAmount(storage.getSlot(slot).getCapacity());
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        SingleSlotStorage<ItemVariant> view = storage.getSlot(slot);
        if (stack.isEmpty() || !view.supportsInsertion()) {
            return false;
        }
        // Fabric has no separate filter query. Probe insertion without committing.
        try (Transaction transaction = openTransaction()) {
            long inserted = view.insert(ItemVariant.of(stack), 1, transaction);
            checkTransfer(inserted, 1);
            return inserted == 1;
        }
    }

    private static int boundedAmount(long amount) {
        if (amount < 0) {
            throw new IllegalStateException("Fabric inventory reported a negative amount: " + amount);
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

}
