//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.items;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.lib.compat.transfer.TransferInterop;

/**
 * BuildCraft-owned slot inventory contract. NeoForge 26.3 removed its legacy item handler, but BuildCraft's
 * internal inventories, menus and pipes are still written against the slot/stack model. Native NeoForge
 * storage is only reached through {@link TransferInterop} at capability boundaries.
 */
public interface IItemHandler {
    /** Presents a native item resource handler through the legacy slot contract. */
    static IItemHandler of(ResourceHandler<ItemResource> handler) {
        Objects.requireNonNull(handler, "handler");
        return TransferInterop.importItems(handler);
    }

    int getSlots();

    /** The returned stack must not be modified by the caller. */
    ItemStack getStackInSlot(int slot);

    /** Returns the part of {@code stack} that was not inserted. {@code stack} itself is never modified. */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    /** Returns the extracted stack, which may be freely modified by the caller. */
    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);
}
