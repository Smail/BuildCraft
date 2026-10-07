//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.items.wrapper;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.neoforge263.items.IItemHandlerModifiable;

/** Presents several {@link IItemHandlerModifiable}s as one contiguous slot range. */
public class CombinedInvWrapper implements IItemHandlerModifiable {
    protected final IItemHandlerModifiable[] itemHandler;
    /** {@code baseIndex[i]} is the first slot after handler {@code i}, i.e. a running total of slot counts. */
    protected final int[] baseIndex;
    protected final int slotCount;

    public CombinedInvWrapper(IItemHandlerModifiable... itemHandler) {
        this.itemHandler = Objects.requireNonNull(itemHandler, "itemHandler");
        this.baseIndex = new int[itemHandler.length];
        int index = 0;
        for (int i = 0; i < itemHandler.length; i++) {
            index += Objects.requireNonNull(itemHandler[i], "itemHandler[" + i + "]").getSlots();
            baseIndex[i] = index;
        }
        this.slotCount = index;
    }

    /** Returns the handler index for a combined slot, or -1 if the slot is out of range. */
    protected int getIndexForSlot(int slot) {
        if (slot < 0) {
            return -1;
        }
        for (int i = 0; i < baseIndex.length; i++) {
            if (slot - baseIndex[i] < 0) {
                return i;
            }
        }
        return -1;
    }

    protected IItemHandlerModifiable getHandlerFromIndex(int index) {
        if (index < 0 || index >= itemHandler.length) {
            return EmptyHandler.INSTANCE;
        }
        return itemHandler[index];
    }

    protected int getSlotFromIndex(int slot, int index) {
        if (index <= 0 || index >= baseIndex.length) {
            return slot;
        }
        return slot - baseIndex[index - 1];
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        int index = getIndexForSlot(slot);
        getHandlerFromIndex(index).setStackInSlot(getSlotFromIndex(slot, index), stack);
    }

    @Override
    public int getSlots() {
        return slotCount;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).getStackInSlot(getSlotFromIndex(slot, index));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).insertItem(getSlotFromIndex(slot, index), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).extractItem(getSlotFromIndex(slot, index), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).getSlotLimit(getSlotFromIndex(slot, index));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).isItemValid(getSlotFromIndex(slot, index), stack);
    }

    /** Out-of-range slots behave as an empty, inert inventory. */
    private enum EmptyHandler implements IItemHandlerModifiable {
        INSTANCE;

        @Override public void setStackInSlot(int slot, ItemStack stack) {}
        @Override public int getSlots() { return 0; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
