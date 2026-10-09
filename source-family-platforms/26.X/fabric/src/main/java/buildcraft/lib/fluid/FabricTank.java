package buildcraft.lib.fluid;

import java.util.Objects;
import java.util.function.Predicate;

import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.platform.storage.FabricFluidExport;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;

/** Authoritative millibucket tank with one journal shared by all native sided views. */
public final class FabricTank implements FabricFluidExport.TankAccess {
    private final String name;
    private final Runnable changed;
    private final SlottedStorage<FluidVariant> nativeStorage;
    private final TransferJournal<State> journal;
    private FabricFluidStack fluid = FabricFluidStack.EMPTY;
    private int capacity;
    private Predicate<FabricFluidStack> filter;
    private boolean canFill = true;
    private boolean canDrain = true;

    private record State(FabricFluidStack fluid, int capacity, Predicate<FabricFluidStack> filter,
        boolean canFill, boolean canDrain) {}

    public FabricTank(String name, int capacity, Predicate<FabricFluidStack> filter, Runnable changed) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank() || capacity <= 0) {
            throw new IllegalArgumentException("Tank needs a nonempty name and positive capacity");
        }
        this.capacity = capacity;
        this.filter = Objects.requireNonNull(filter, "filter");
        this.changed = Objects.requireNonNull(changed, "changed");
        journal = new TransferJournal<>(this::snapshot, this::restore,
            before -> { if (!before.equals(snapshot())) this.changed.run(); });
        nativeStorage = new FabricFluidExport(this, journal);
    }

    public SlottedStorage<FluidVariant> nativeStorage() { return nativeStorage; }
    public String getTankName() { return name; }
    public FabricFluidStack getFluid() { return fluid; }
    public int getCapacity() { return capacity; }
    public boolean canFill() { return canFill; }
    public boolean canDrain() { return canDrain; }

    private State snapshot() { return new State(fluid, capacity, filter, canFill, canDrain); }

    private void restore(State state) {
        fluid = state.fluid;
        capacity = state.capacity;
        filter = state.filter;
        canFill = state.canFill;
        canDrain = state.canDrain;
    }

    private void notifyChanged() {
        if (!TransferJournal.active()) {
            changed.run();
        }
    }

    public void setCapacity(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Tank capacity must be positive");
        }
        if (value != capacity) {
            journal.record();
            capacity = value;
            if (fluid.amount() > value) fluid = fluid.copyWithAmount(value);
            notifyChanged();
        }
    }

    public void setFluid(FabricFluidStack value) {
        Objects.requireNonNull(value, "fluid");
        if (!value.equals(fluid)) {
            journal.record();
            fluid = value;
            notifyChanged();
        }
    }

    public void setAccess(boolean fill, boolean drain) {
        if (canFill != fill || canDrain != drain) {
            journal.record();
            canFill = fill;
            canDrain = drain;
            notifyChanged();
        }
    }

    public void setFilter(Predicate<FabricFluidStack> value) {
        Objects.requireNonNull(value, "filter");
        if (value != filter) {
            journal.record();
            filter = value;
            notifyChanged();
        }
    }

    @Override public int getTanks() { return 1; }
    @Override public FabricFluidStack getFluidInTank(int tank) { Objects.checkIndex(tank, 1); return fluid; }
    @Override public int getTankCapacity(int tank) { Objects.checkIndex(tank, 1); return capacity; }

    @Override
    public boolean isFluidValid(int tank, FabricFluidStack value) {
        Objects.checkIndex(tank, 1);
        Objects.requireNonNull(value, "fluid");
        return !value.isEmpty() && filter.test(value);
    }

    @Override
    public int fill(FabricFluidStack value, boolean simulate) {
        Objects.requireNonNull(value, "fluid");
        if (!canFill || !isFluidValid(0, value) || (!fluid.isEmpty() && !fluid.variant().equals(value.variant()))) {
            return 0;
        }
        int accepted = Math.min(value.amount(), Math.max(0, capacity - fluid.amount()));
        if (!simulate && accepted > 0) {
            setFluid(new FabricFluidStack(value.variant(), fluid.amount() + accepted));
        }
        return accepted;
    }

    @Override
    public FabricFluidStack drain(FabricFluidStack value, boolean simulate) {
        Objects.requireNonNull(value, "fluid");
        return value.isEmpty() || !value.variant().equals(fluid.variant())
            ? FabricFluidStack.EMPTY : drain(value.amount(), simulate);
    }

    @Override
    public FabricFluidStack drain(int maximum, boolean simulate) {
        if (maximum < 0) throw new IllegalArgumentException("Negative drain amount");
        if (!canDrain || maximum == 0 || fluid.isEmpty()) return FabricFluidStack.EMPTY;
        int amount = Math.min(maximum, fluid.amount());
        FabricFluidStack result = fluid.copyWithAmount(amount);
        if (!simulate) setFluid(fluid.copyWithAmount(fluid.amount() - amount));
        return result;
    }

    @Override public int fillTank(int tank, FabricFluidStack value, boolean simulate) {
        Objects.checkIndex(tank, 1);
        return fill(value, simulate);
    }

    @Override public FabricFluidStack drainTank(int tank, FabricFluidStack value, boolean simulate) {
        Objects.checkIndex(tank, 1);
        return drain(value, simulate);
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");
        var encoded = FabricFluidStack.OPTIONAL_CODEC.encodeStart(
            registries.createSerializationContext(NbtOps.INSTANCE), fluid).getOrThrow();
        if (!(encoded instanceof CompoundTag tag)) {
            throw new IllegalStateException("Tank fluid did not encode as a compound");
        }
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(tag, "tag");
        // Decode completely before touching the current state.
        FabricFluidStack decoded = FabricFluidStack.parse(registries, tag);
        setFluid(decoded);
    }
}
