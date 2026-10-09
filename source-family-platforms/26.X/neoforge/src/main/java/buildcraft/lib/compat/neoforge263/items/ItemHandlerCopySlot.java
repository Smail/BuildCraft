//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.items;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.world.inventory.StackCopySlot;

/**
 * Menu slot over an {@link IItemHandlerModifiable} that hands vanilla a private copy of the stack, so menu code
 * may mutate it freely. Changes are written back through {@link IItemHandlerModifiable#setStackInSlot}.
 */
public class ItemHandlerCopySlot extends StackCopySlot {
    private final SlotItemHandler slotItemHandler;

    public ItemHandlerCopySlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(index, xPosition, yPosition);
        slotItemHandler = new SlotItemHandler(itemHandler, index, xPosition, yPosition);
    }

    public ItemHandlerCopySlot(SlotItemHandler slotItemHandler) {
        super(slotItemHandler.getSlotIndex(), slotItemHandler.x, slotItemHandler.y);
        this.slotItemHandler = slotItemHandler;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return slotItemHandler.mayPlace(stack);
    }

    @Override
    protected ItemStack getStackCopy() {
        return slotItemHandler.getItem();
    }

    @Override
    protected void setStackCopy(ItemStack stack) {
        if (!(slotItemHandler.getItemHandler() instanceof IItemHandlerModifiable modifiable)) {
            throw new UnsupportedOperationException("Copy slot " + slotItemHandler.index
                + " is backed by a read-only item handler: " + slotItemHandler.getItemHandler().getClass().getName());
        }
        modifiable.setStackInSlot(slotItemHandler.index, stack);
    }

    @Override
    public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
        slotItemHandler.onQuickCraft(oldStack, newStack);
    }

    @Override
    public int getMaxStackSize() {
        return slotItemHandler.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return slotItemHandler.getMaxStackSize(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return slotItemHandler.mayPickup(player);
    }

    @Override
    public boolean isSameInventory(Slot other) {
        return slotItemHandler.isSameInventory(other);
    }

    public IItemHandler getItemHandler() {
        return slotItemHandler.getItemHandler();
    }
}
