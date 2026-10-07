//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.fluids.capability.templates;

import java.util.Objects;
import java.util.function.Predicate;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.compat.neoforge263.fluids.IFluidTank;
import buildcraft.lib.compat.neoforge263.fluids.capability.IFluidHandler;

/** Simple single-tank fluid store. Not persisted; owners that need saving serialize {@link #getFluid()} themselves. */
public class FluidTank implements IFluidHandler, IFluidTank {
    protected Predicate<FluidStack> validator;
    protected FluidStack fluid = FluidStack.EMPTY;
    protected int capacity;

    public FluidTank(int capacity) {
        this(capacity, stack -> true);
    }

    public FluidTank(int capacity, Predicate<FluidStack> validator) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Negative tank capacity: " + capacity);
        }
        this.capacity = capacity;
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public void setFluid(FluidStack stack) {
        this.fluid = stack == null ? FluidStack.EMPTY : stack;
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        return validator.test(stack);
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public FluidStack getFluid() {
        return fluid;
    }

    @Override
    public int getFluidAmount() {
        return fluid.getAmount();
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return fluid;
    }

    @Override
    public int getTankCapacity(int tank) {
        return capacity;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(resource)) {
            return 0;
        }
        if (!fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, resource)) {
            return 0;
        }
        int filled = Math.min(capacity - fluid.getAmount(), resource.getAmount());
        if (filled <= 0 || action.simulate()) {
            return Math.max(0, filled);
        }
        if (fluid.isEmpty()) {
            fluid = resource.copyWithAmount(filled);
        } else {
            fluid.grow(filled);
        }
        onContentsChanged();
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, fluid)) {
            return FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        int drained = Math.max(0, Math.min(maxDrain, fluid.getAmount()));
        FluidStack stack = fluid.copyWithAmount(drained);
        if (action.execute() && drained > 0) {
            fluid.shrink(drained);
            onContentsChanged();
        }
        return stack;
    }

    protected void onContentsChanged() {}
}
