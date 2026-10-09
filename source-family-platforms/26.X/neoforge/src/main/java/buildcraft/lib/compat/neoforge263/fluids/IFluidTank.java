//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.compat.neoforge263.fluids.capability.IFluidHandler.FluidAction;

/** A single fluid tank. */
public interface IFluidTank {
    FluidStack getFluid();

    int getFluidAmount();

    int getCapacity();

    boolean isFluidValid(FluidStack stack);

    int fill(FluidStack resource, FluidAction action);

    FluidStack drain(int maxDrain, FluidAction action);

    FluidStack drain(FluidStack resource, FluidAction action);
}
