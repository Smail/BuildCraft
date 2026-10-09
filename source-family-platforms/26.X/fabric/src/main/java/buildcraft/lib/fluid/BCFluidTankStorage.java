package buildcraft.lib.fluid;

import java.util.Objects;
import java.util.function.Predicate;
import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.misc.FluidStackUtil;
import net.minecraft.nbt.CompoundTag;

/** A component-preserving mutable tank with an owner journal shared by every native view. */
public class BCFluidTankStorage implements BCFluidTank, BCFluidHandler {
    protected BCFluidStack fluid = BCFluidStack.EMPTY;
    protected int capacity;
    protected Predicate<BCFluidStack> validator;
    private final TransferJournal<BCFluidStack> journal = new TransferJournal<>(() -> fluid.copy(), saved -> fluid = saved.copy(), old -> onContentsChanged());
    public BCFluidTankStorage(int capacity) { this(capacity, stack -> true); }
    public BCFluidTankStorage(int capacity, Predicate<BCFluidStack> filter) {
        if (capacity < 0) throw new IllegalArgumentException("Negative tank capacity");
        this.capacity = capacity; validator = Objects.requireNonNull(filter);
    }
    protected void onContentsChanged() {}
    public BCFluidTankStorage setValidator(Predicate<BCFluidStack> filter) { validator = Objects.requireNonNull(filter); return this; }
    public void setCapacity(int value) { if (value < 0) throw new IllegalArgumentException("Negative tank capacity"); capacity = value; }
    public void setFluid(BCFluidStack value) {
        journal.record(); fluid = Objects.requireNonNull(value).copy();
        if (!TransferJournal.active()) onContentsChanged();
    }
    @Override public BCFluidStack getFluid() { return fluid; }
    @Override public int getFluidAmount() { return fluid.getAmount(); }
    @Override public int getCapacity() { return capacity; }
    @Override public boolean isFluidValid(BCFluidStack value) { return !Objects.requireNonNull(value).isEmpty() && validator.test(value); }
    @Override public int getTanks() { return 1; }
    @Override public BCFluidStack getFluidInTank(int tank) { Objects.checkIndex(tank, 1); return fluid; }
    @Override public int getTankCapacity(int tank) { Objects.checkIndex(tank, 1); return capacity; }
    @Override public boolean isFluidValid(int tank, BCFluidStack value) { Objects.checkIndex(tank, 1); return isFluidValid(value); }
    @Override public int fill(BCFluidStack value, FluidAction action) {
        Objects.requireNonNull(action);
        if (!isFluidValid(value) || !fluid.isEmpty() && !BCFluidStack.isSameFluidSameComponents(fluid, value)) return 0;
        int accepted = Math.min(value.getAmount(), Math.max(0, capacity - fluid.getAmount()));
        if (accepted > 0 && action.execute()) setFluid(value.copyWithAmount(fluid.getAmount() + accepted));
        return accepted;
    }
    @Override public BCFluidStack drain(BCFluidStack value, FluidAction action) {
        return value.isEmpty() || !BCFluidStack.isSameFluidSameComponents(fluid, value) ? BCFluidStack.EMPTY : drain(value.getAmount(), action);
    }
    @Override public BCFluidStack drain(int maximum, FluidAction action) {
        Objects.requireNonNull(action);
        if (maximum < 0) throw new IllegalArgumentException("Negative drain amount");
        int amount = Math.min(maximum, fluid.getAmount());
        BCFluidStack result = fluid.copyWithAmount(amount);
        if (amount > 0 && action.execute()) setFluid(fluid.copyWithAmount(fluid.getAmount() - amount));
        return result;
    }
    public CompoundTag writeToNBT(CompoundTag tag) { return tag.merge(FluidStackUtil.saveOptional(fluid)); }
    public BCFluidTankStorage readFromNBT(CompoundTag tag) { setFluid(FluidStackUtil.parseOptional(tag)); return this; }
    public TransferJournal<?> transferJournal() { return journal; }
}
