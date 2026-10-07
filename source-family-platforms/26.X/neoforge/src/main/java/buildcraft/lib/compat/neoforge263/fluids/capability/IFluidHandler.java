//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.fluids.capability;

import java.util.Objects;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.lib.compat.transfer.TransferInterop;

/**
 * BuildCraft-owned multi-tank fluid contract. NeoForge 26.3 removed its legacy fluid handler, but BuildCraft's
 * tanks, pipes and robots still use it internally. Native storage is bridged by {@link TransferInterop}.
 */
public interface IFluidHandler {
    /** Presents a native fluid resource handler through the legacy tank contract. */
    static IFluidHandler of(ResourceHandler<FluidResource> handler) {
        Objects.requireNonNull(handler, "handler");
        return TransferInterop.importFluids(handler);
    }

    enum FluidAction {
        EXECUTE, SIMULATE;

        public boolean execute() {
            return this == EXECUTE;
        }

        public boolean simulate() {
            return this == SIMULATE;
        }
    }

    int getTanks();

    /** The returned stack must not be modified by the caller. */
    FluidStack getFluidInTank(int tank);

    int getTankCapacity(int tank);

    /** Whether the tank could ever hold the fluid, ignoring its current contents. */
    boolean isFluidValid(int tank, FluidStack stack);

    /** Returns the amount that was (or would have been, if simulated) filled. */
    int fill(FluidStack resource, FluidAction action);

    /** Drains up to {@code resource.getAmount()} of exactly that fluid. */
    FluidStack drain(FluidStack resource, FluidAction action);

    /** Drains up to {@code maxDrain} of whatever fluid is available. */
    FluidStack drain(int maxDrain, FluidAction action);
}
