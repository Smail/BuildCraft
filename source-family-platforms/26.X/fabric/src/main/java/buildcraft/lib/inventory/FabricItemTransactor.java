package buildcraft.lib.inventory;

import java.util.Objects;

import javax.annotation.Nullable;

import buildcraft.lib.internal.core.IStackFilter;
import buildcraft.lib.internal.inventory.IItemTransactor;
import buildcraft.lib.platform.storage.FabricTransferOperations;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Handles arbitrary Fabric storages without inventing inventory slots. */
public final class FabricItemTransactor implements IItemTransactor {
    private final Storage<ItemVariant> storage;

    public FabricItemTransactor(Storage<ItemVariant> storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean allOrNone, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = FabricTransferOperations.openTransaction()) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            FabricTransferOperations.checkTransfer(inserted, stack.getCount());
            if (allOrNone && inserted != stack.getCount()) {
                return stack.copy();
            }
            ItemStack remainder = stack.copyWithCount(stack.getCount() - (int) inserted);
            if (!simulate) {
                transaction.commit();
            }
            return remainder;
        }
    }

    @Override
    public NonNullList<ItemStack> insert(NonNullList<ItemStack> stacks, boolean simulate) {
        Objects.requireNonNull(stacks, "stacks");
        // The entire batch shares capacity even when the caller only simulates.
        try (Transaction transaction = FabricTransferOperations.openTransaction()) {
            NonNullList<ItemStack> remainder = NonNullList.create();
            for (ItemStack stack : stacks) {
                ItemStack left = insert(stack, false, false);
                if (!left.isEmpty()) {
                    remainder.add(left);
                }
            }
            if (!simulate) {
                transaction.commit();
            }
            return remainder;
        }
    }

    @Override
    public ItemStack extract(@Nullable IStackFilter filter, int min, int max, boolean simulate) {
        min = Math.max(1, min);
        if (max < min) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = FabricTransferOperations.openTransaction()) {
            for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
                ItemVariant resource = view.getResource();
                ItemStack candidate = resource.toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE));
                if (filter != null && !filter.matches(candidate)) {
                    continue;
                }
                try (Transaction attempt = transaction.openNested()) {
                    long extracted = storage.extract(resource, max, attempt);
                    FabricTransferOperations.checkTransfer(extracted, max);
                    if (extracted < min) {
                        continue;
                    }
                    ItemStack result = resource.toStack((int) extracted);
                    attempt.commit();
                    if (!simulate) {
                        transaction.commit();
                    }
                    return result;
                }
            }
            return ItemStack.EMPTY;
        }
    }
}
