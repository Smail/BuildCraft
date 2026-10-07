//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.items;

import java.util.Objects;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu slot backed by one index of an {@link IItemHandler}. */
public class SlotItemHandler extends Slot {
    private static final Container EMPTY_INVENTORY = new SimpleContainer(0);

    private final IItemHandler itemHandler;
    protected final int index;

    public SlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(EMPTY_INVENTORY, index, xPosition, yPosition);
        this.itemHandler = Objects.requireNonNull(itemHandler, "itemHandler");
        this.index = index;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && itemHandler.isItemValid(index, stack);
    }

    @Override
    public ItemStack getItem() {
        return itemHandler.getStackInSlot(index);
    }

    /** Requires an {@link IItemHandlerModifiable}; subclasses over read-only handlers must override this. */
    @Override
    public void set(ItemStack stack) {
        modifiable().setStackInSlot(index, stack);
        setChanged();
    }

    public void initialize(ItemStack stack) {
        modifiable().setStackInSlot(index, stack);
        setChanged();
    }

    @Override
    public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {}

    @Override
    public int getMaxStackSize() {
        return itemHandler.getSlotLimit(index);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), itemHandler.getSlotLimit(index));
    }

    @Override
    public boolean mayPickup(Player player) {
        return !itemHandler.extractItem(index, 1, true).isEmpty();
    }

    @Override
    public ItemStack remove(int amount) {
        return itemHandler.extractItem(index, amount, false);
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    public boolean isSameInventory(Slot other) {
        return other instanceof SlotItemHandler slot && slot.itemHandler == itemHandler;
    }

    private IItemHandlerModifiable modifiable() {
        if (itemHandler instanceof IItemHandlerModifiable modifiable) {
            return modifiable;
        }
        throw new UnsupportedOperationException(
            "Slot " + index + " is backed by a read-only item handler: " + itemHandler.getClass().getName());
    }
}
