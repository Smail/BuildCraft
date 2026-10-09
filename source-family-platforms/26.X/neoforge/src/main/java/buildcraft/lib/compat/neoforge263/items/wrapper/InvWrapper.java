//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.items.wrapper;

import java.util.Objects;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.neoforge263.items.IItemHandlerModifiable;

/** Presents a vanilla {@link Container} as an {@link IItemHandlerModifiable}. */
public class InvWrapper implements IItemHandlerModifiable {
    private final Container inv;

    public InvWrapper(Container inv) {
        this.inv = Objects.requireNonNull(inv, "inv");
    }

    public Container getInv() {
        return inv;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return inv.equals(((InvWrapper) o).inv);
    }

    @Override
    public int hashCode() {
        return inv.hashCode();
    }

    @Override
    public int getSlots() {
        return inv.getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inv.getItem(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = inv.getItem(slot);
        int space;
        if (!existing.isEmpty()) {
            if (existing.getCount() >= Math.min(existing.getMaxStackSize(), getSlotLimit(slot))
                || !ItemStack.isSameItemSameComponents(stack, existing)
                || !inv.canPlaceItem(slot, stack)) {
                return stack;
            }
            space = Math.min(stack.getMaxStackSize(), getSlotLimit(slot)) - existing.getCount();
        } else {
            if (!inv.canPlaceItem(slot, stack)) {
                return stack;
            }
            space = Math.min(stack.getMaxStackSize(), getSlotLimit(slot));
        }
        int moved = Math.min(space, stack.getCount());
        if (!simulate) {
            // Never hand the caller's stack to the container: the caller still owns it.
            inv.setItem(slot, stack.copyWithCount(moved + existing.getCount()));
            inv.setChanged();
        }
        return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = inv.getItem(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (simulate) {
            return existing.copyWithCount(Math.min(existing.getCount(), amount));
        }
        ItemStack removed = inv.removeItem(slot, Math.min(existing.getCount(), amount));
        inv.setChanged();
        return removed;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inv.setItem(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inv.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inv.canPlaceItem(slot, stack);
    }
}
