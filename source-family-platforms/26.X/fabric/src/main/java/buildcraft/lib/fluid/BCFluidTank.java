package buildcraft.lib.fluid;

public interface BCFluidTank {
    BCFluidStack getFluid();
    int getFluidAmount();
    int getCapacity();
    boolean isFluidValid(BCFluidStack stack);
    int fill(BCFluidStack stack, BCFluidHandler.FluidAction action);
    BCFluidStack drain(int maximum, BCFluidHandler.FluidAction action);
    BCFluidStack drain(BCFluidStack stack, BCFluidHandler.FluidAction action);
}
