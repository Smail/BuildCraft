package buildcraft.lib.fluid;

/** Millibucket contract used by gameplay; native Fabric storage is bound at the boundary. */
public interface BCFluidHandler extends buildcraft.lib.platform.storage.FluidStorage<BCFluidStack> {
    enum FluidAction {
        EXECUTE, SIMULATE;
        public boolean execute() { return this == EXECUTE; }
        public boolean simulate() { return this == SIMULATE; }
    }
    int getTanks();
    BCFluidStack getFluidInTank(int tank);
    int getTankCapacity(int tank);
    boolean isFluidValid(int tank, BCFluidStack stack);
    int fill(BCFluidStack stack, FluidAction action);
    BCFluidStack drain(BCFluidStack stack, FluidAction action);
    BCFluidStack drain(int maximum, FluidAction action);
    @Override default int fill(BCFluidStack stack, boolean simulate) { return fill(stack, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE); }
    @Override default BCFluidStack drain(BCFluidStack stack, boolean simulate) { return drain(stack, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE); }
    @Override default BCFluidStack drain(int maximum, boolean simulate) { return drain(maximum, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE); }
}
