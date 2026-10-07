//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.fluids.capability;

import net.minecraft.world.item.ItemStack;

/** A fluid handler for one item container. Its contents may change the container item, see {@link #getContainer()}. */
public interface IFluidHandlerItem extends IFluidHandler {
    /** Returns the container item in its current state, after any fills or drains. */
    ItemStack getContainer();
}
