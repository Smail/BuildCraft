package buildcraft.lib.platform.storage;

import buildcraft.lib.compat.transfer.TransferJournal;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Tests caller recovery after a backing battery mutates and then fails. */
public final class FabricEnergyExportProbe {
    private FabricEnergyExportProbe() {}

    public static void main(String[] args) {
        Battery battery = new Battery();
        TransferJournal<Integer> journal = new TransferJournal<>(() -> battery.amount,
            snapshot -> battery.amount = snapshot, previous -> {});
        var nativeStorage = StorageAdapters.toNativeEnergy(battery, journal);
        check(StorageAdapters.fromNativeEnergy(nativeStorage) == battery);
        try (Transaction outer = Transaction.openOuter()) {
            check(nativeStorage.insert(20, outer) == 20);
            battery.fail = true;
            expectFailure(() -> nativeStorage.insert(10, outer));
            check(battery.amount == 20);
            battery.fail = false;
            outer.commit();
        }
        check(battery.amount == 20);
        try (Transaction outer = Transaction.openOuter()) {
            check(nativeStorage.extract(5, outer) == 5);
            battery.fail = true;
            expectFailure(() -> nativeStorage.extract(5, outer));
            check(battery.amount == 15);
        }
        check(battery.amount == 20);
        battery.fail = false;
        battery.invalidResult = true;
        try (Transaction outer = Transaction.openOuter()) {
            expectFailure(() -> nativeStorage.insert(3, outer));
            check(battery.amount == 20);
            outer.commit();
        }
        check(battery.amount == 20);
        System.out.println("Fabric energy export recovery probes passed");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Energy export invariant failed");
        }
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected backing storage failure");
    }

    private static final class Battery implements EnergyStorage {
        int amount;
        boolean fail;
        boolean invalidResult;

        @Override
        public int receiveEnergy(int requested, boolean simulate) {
            int accepted = Math.min(requested, 100 - amount);
            if (!simulate) {
                amount += accepted;
            }
            if (fail) {
                throw new IllegalStateException("Battery failed after insertion");
            }
            return invalidResult ? requested + 1 : accepted;
        }

        @Override
        public int extractEnergy(int requested, boolean simulate) {
            int extracted = Math.min(requested, amount);
            if (!simulate) {
                amount -= extracted;
            }
            if (fail) {
                throw new IllegalStateException("Battery failed after extraction");
            }
            return extracted;
        }

        @Override public int getEnergyStored() { return amount; }
        @Override public int getMaxEnergyStored() { return 100; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }
}
