package buildcraft.lib.fluid;

import java.util.Objects;
import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.platform.storage.FabricFluidExport;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/** Publish gameplay fluid storage without creating separate state or journals for faces. */
public final class LegacyFluidExport {
    private LegacyFluidExport() {}
    public static Storage<FluidVariant> nativeStorage(BCFluidHandler handler) {
        Objects.requireNonNull(handler);
        if (handler instanceof JournaledFluidHandler journaled) return nativeStorage(handler, journaled.transferJournal());
        if (handler instanceof Tank tank) return nativeStorage(handler, tank.transferJournal());
        if (handler instanceof BCFluidTankStorage tank) return nativeStorage(handler, tank.transferJournal());
        if (handler instanceof TankManager manager) {
            // Every tank owns its journal. Native operations record those same journals before mutation.
            return nativeStorage(handler, new TransferJournal<>(() -> 0, ignored -> {}, ignored -> {}));
        }
        throw new IllegalArgumentException("Fluid handler needs its owner's transaction journal: " + handler.getClass().getName());
    }
    public static Storage<FluidVariant> nativeStorage(BCFluidHandler handler, TransferJournal<?> journal) {
        return new FabricFluidExport(new FabricFluidExport.TankAccess() {
            public int getTanks() { return handler.getTanks(); }
            public FabricFluidStack getFluidInTank(int tank) { return handler.getFluidInTank(tank).toNative(); }
            public int getTankCapacity(int tank) { return handler.getTankCapacity(tank); }
            public boolean isFluidValid(int tank, FabricFluidStack value) { return handler.isFluidValid(tank, BCFluidStack.fromNative(value)); }
            public int fill(FabricFluidStack value, boolean simulate) { return handler.fill(BCFluidStack.fromNative(value), simulate); }
            public FabricFluidStack drain(FabricFluidStack value, boolean simulate) { return handler.drain(BCFluidStack.fromNative(value), simulate).toNative(); }
            public FabricFluidStack drain(int maximum, boolean simulate) { return handler.drain(maximum, simulate).toNative(); }
            public int fillTank(int index, FabricFluidStack value, boolean simulate) {
                if (handler instanceof buildcraft.lib.compat.transfer.IndexedFluidHandler indexed) return indexed.fillTank(index, BCFluidStack.fromNative(value), simulate ? BCFluidHandler.FluidAction.SIMULATE : BCFluidHandler.FluidAction.EXECUTE);
                Objects.checkIndex(index, handler.getTanks());
                if (handler.getTanks() != 1) throw new IllegalStateException("Indexed fluid storage must implement per-tank operations");
                return fill(value, simulate);
            }
            public FabricFluidStack drainTank(int index, FabricFluidStack value, boolean simulate) {
                if (handler instanceof buildcraft.lib.compat.transfer.IndexedFluidHandler indexed) return indexed.drainTank(index, BCFluidStack.fromNative(value), simulate ? BCFluidHandler.FluidAction.SIMULATE : BCFluidHandler.FluidAction.EXECUTE).toNative();
                Objects.checkIndex(index, handler.getTanks());
                if (handler.getTanks() != 1) throw new IllegalStateException("Indexed fluid storage must implement per-tank operations");
                return drain(value, simulate);
            }
        }, Objects.requireNonNull(journal));
    }
}
