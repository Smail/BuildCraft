package buildcraft.lib.internal.mj;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.*;
import buildcraft.lib.compat.transfer.TransferJournal;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/** Native Fabric rollback and the existing 1 MJ = 10 FE conversion with fractional remainders. */
public final class FabricMjProbe {
    private FabricMjProbe() {}

    public static void main(String[] args) {
        SimpleEnergyStorage energy = new SimpleEnergyStorage(100, 100, 100);
        boolean[] enabled = {true};
        var converted = new MjApi2PlatformBridge.ExternalPort(energy, () -> 100_000L, () -> enabled[0]);
        equal(200_000, converted.insert(MjAmount.ofMicro(200_001), OperationMode.SIMULATE).transferred().microMj());
        equal(0, energy.getAmount());
        var result = converted.insert(MjAmount.ofMicro(200_001), OperationMode.EXECUTE);
        equal(200_000, result.transferred().microMj());
        equal(1, result.remainder().microMj());
        equal(2, energy.getAmount());
        equal(0, converted.insert(MjAmount.ofMicro(99_999), OperationMode.EXECUTE).transferred().microMj());
        try (Transaction outer = Transaction.openOuter()) {
            converted.insert(MjAmount.ofMicro(300_000), OperationMode.EXECUTE);
            equal(5, energy.getAmount());
        }
        equal(2, energy.getAmount());
        equal(0, converted.insert(MjAmount.ofMicro(200_001), MjTransferPolicy.ALL_OR_NOTHING,
            OperationMode.EXECUTE).transferred().microMj());
        equal(2, energy.getAmount());
        equal(0, converted.extract(MjAmount.ofMicro(300_000), MjAmount.ofMicro(500_000),
            OperationMode.EXECUTE).transferred().microMj());
        equal(2, energy.getAmount());
        enabled[0] = false;
        equal(0, converted.extract(MjAmount.ofMj(1), OperationMode.EXECUTE).transferred().microMj());
        equal(2, energy.getAmount());

        Battery battery = new Battery();
        var journal = new TransferJournal<>(() -> battery.amount, previous -> battery.amount = previous, previous -> {});
        var firstSide = new FabricMjPort(battery, journal);
        var secondSide = new FabricMjPort(battery, journal);
        equal(40, firstSide.insert(MjAmount.ofMicro(40), OperationMode.SIMULATE).transferred().microMj());
        equal(0, battery.amount);
        try (Transaction outer = Transaction.openOuter()) {
            firstSide.insert(MjAmount.ofMicro(40), OperationMode.EXECUTE);
            secondSide.extract(MjAmount.ofMicro(10), OperationMode.EXECUTE);
            equal(30, battery.amount);
        }
        equal(0, battery.amount);
        firstSide.insert(MjAmount.ofMicro(40), OperationMode.EXECUTE);
        equal(0, secondSide.insert(MjAmount.ofMicro(80), MjTransferPolicy.ALL_OR_NOTHING,
            OperationMode.EXECUTE).transferred().microMj());
        equal(40, battery.amount);
        battery.fail = true;
        try (Transaction outer = Transaction.openOuter()) {
            try {
                firstSide.insert(MjAmount.ofMicro(10), OperationMode.EXECUTE);
                throw new AssertionError("Expected mutation failure");
            } catch (IllegalStateException expected) {
                equal(40, battery.amount);
            }
            outer.commit();
        }
        equal(40, battery.amount);
        System.out.println("Fabric MJ conversion and sided rollback probes passed");
    }

    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static final class Battery implements MjPort {
        long amount;
        boolean fail;

        @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
            long moved = Math.min(offered.microMj(), 100 - amount);
            if (mode == OperationMode.EXECUTE) amount += moved;
            if (fail) throw new IllegalStateException("MJ battery failed after mutation");
            return MjTransferResult.of(offered, MjAmount.ofMicro(moved));
        }

        @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
            long moved = Math.min(requested.microMj(), amount);
            if (mode == OperationMode.EXECUTE) amount -= moved;
            return MjTransferResult.of(requested, MjAmount.ofMicro(moved));
        }

        @Override public MjAmount stored() { return MjAmount.ofMicro(amount); }
        @Override public MjAmount capacity() { return MjAmount.ofMicro(100); }
    }
}
