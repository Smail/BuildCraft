//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.items;

import net.minecraft.world.item.ItemStack;

/** An {@link IItemHandler} whose slots may be overwritten directly, bypassing insert/extract rules. */
public interface IItemHandlerModifiable extends IItemHandler {
    void setStackInSlot(int slot, ItemStack stack);
}
