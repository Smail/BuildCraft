package buildcraft.lib.platform.storage;

import java.util.Objects;
import java.util.function.ToLongFunction;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Fabric energy quantities remain FE-sized here; MJ conversion stays with its existing owner. */
public final class FabricEnergyStorage implements EnergyStorage {
    private final team.reborn.energy.api.EnergyStorage storage;

    public FabricEnergyStorage(team.reborn.energy.api.EnergyStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public team.reborn.energy.api.EnergyStorage nativeStorage() {
        return storage;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        return transfer(amount, simulate, tx -> storage.insert(amount, tx));
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        return transfer(amount, simulate, tx -> storage.extract(amount, tx));
    }

    @Override
    public int getEnergyStored() {
        return bounded(storage.getAmount());
    }

    @Override
    public int getMaxEnergyStored() {
        return bounded(storage.getCapacity());
    }

    @Override
    public boolean canExtract() {
        return storage.supportsExtraction();
    }

    @Override
    public boolean canReceive() {
        return storage.supportsInsertion();
    }

    private static int bounded(long amount) {
        if (amount < 0) {
            throw new IllegalStateException("Fabric energy storage reported a negative amount: " + amount);
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private static int transfer(int amount, boolean simulate, ToLongFunction<Transaction> operation) {
        return (int) FabricTransferOperations.transfer(amount, simulate, operation);
    }
}
