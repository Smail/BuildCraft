package buildcraft.lib.inventory;

import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.item.ItemStack;
import buildcraft.lib.platform.storage.MutableItemStorage;
import buildcraft.lib.compat.transfer.TransferJournal;

/** Owned slot inventory with exact rollback and the existing vanilla inventory save format. */
public class ItemStackStorage implements MutableItemStorage {
    protected NonNullList<ItemStack> stacks;
    private final TransferJournal<NonNullList<ItemStack>> journal = new TransferJournal<>(this::snapshot, this::restore, before -> onContentsChanged(-1));
    public ItemStackStorage() { this(1); }
    public ItemStackStorage(int size) { setSize(size); }
    public void setSize(int size) {
        if (size < 0) throw new IllegalArgumentException("Negative inventory size");
        stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }
    private NonNullList<ItemStack> snapshot() {
        NonNullList<ItemStack> copy = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < stacks.size(); slot++) copy.set(slot, stacks.get(slot).copy());
        return copy;
    }
    private void restore(NonNullList<ItemStack> saved) { stacks = saved; }
    protected void validateSlotIndex(int slot) { Objects.checkIndex(slot, stacks.size()); }
    @Override public int getSlots() { return stacks.size(); }
    @Override public ItemStack getStackInSlot(int slot) { validateSlotIndex(slot); return stacks.get(slot); }
    @Override public int getSlotLimit(int slot) { validateSlotIndex(slot); return 64; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { validateSlotIndex(slot); return !Objects.requireNonNull(stack).isEmpty(); }
    protected int getStackLimit(int slot, ItemStack stack) { return Math.min(getSlotLimit(slot), stack.getMaxStackSize()); }
    @Override public void setStackInSlot(int slot, ItemStack stack) {
        validateSlotIndex(slot); Objects.requireNonNull(stack);
        journal.record(); stacks.set(slot, stack);
        if (!TransferJournal.active()) onContentsChanged(slot);
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        validateSlotIndex(slot); Objects.requireNonNull(stack);
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        ItemStack current = stacks.get(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) return stack;
        int accepted = Math.min(stack.getCount(), Math.max(0, getStackLimit(slot, stack) - current.getCount()));
        if (!simulate && accepted > 0) setStackInSlot(slot, stack.copyWithCount(current.getCount() + accepted));
        return stack.copyWithCount(stack.getCount() - accepted);
    }
    @Override public ItemStack extractItem(int slot, int maximum, boolean simulate) {
        validateSlotIndex(slot);
        if (maximum < 0) throw new IllegalArgumentException("Negative extraction amount");
        ItemStack current = stacks.get(slot);
        int amount = Math.min(maximum, current.getCount());
        if (amount == 0) return ItemStack.EMPTY;
        ItemStack result = current.copyWithCount(amount);
        if (!simulate) setStackInSlot(slot, current.copyWithCount(current.getCount() - amount));
        return result;
    }
    protected void onContentsChanged(int slot) {}
    protected void onLoad() {}
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.putInt("Size", stacks.size());
        ContainerHelper.saveAllItems(output, stacks);
        return output.buildResult();
    }
    public CompoundTag serializeNBT() { return serializeNBT(buildcraft.lib.misc.ItemStackUtil.requireActiveRegistryProvider()); }
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        Objects.requireNonNull(registries); Objects.requireNonNull(tag);
        int size = tag.getInt("Size").orElse(stacks.size());
        if (size < 0 || size > 65536) throw new IllegalArgumentException("Invalid inventory size: " + size);
        NonNullList<ItemStack> loaded = NonNullList.withSize(size, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag), loaded);
        journal.record(); stacks = loaded; onLoad();
    }
    public void deserializeNBT(CompoundTag tag) { deserializeNBT(buildcraft.lib.misc.ItemStackUtil.requireActiveRegistryProvider(), tag); }
    public TransferJournal<?> transferJournal() { return journal; }
}
