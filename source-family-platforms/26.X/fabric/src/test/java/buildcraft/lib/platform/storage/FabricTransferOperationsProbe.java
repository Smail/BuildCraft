package buildcraft.lib.platform.storage;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import buildcraft.lib.compat.transfer.TransferJournal;

/** Runs against the real Fabric transaction implementation without booting Minecraft. */
public final class FabricTransferOperationsProbe {
    private FabricTransferOperationsProbe() {}

    public static void main(String[] args) {
        Counter counter = new Counter();
        equal(81_000, FabricTransferOperations.droplets(1000));
        equal(0, FabricTransferOperations.millibuckets(80));
        equal(173_946_175_407L, FabricTransferOperations.droplets(Integer.MAX_VALUE));
        equal(100, FabricTransferOperations.transfer(100, true, tx -> counter.add(100, tx)));
        equal(0, counter.value);
        FabricTransferOperations.transfer(100, false, tx -> counter.add(100, tx));
        equal(100, counter.value);
        try (Transaction outer = Transaction.openOuter()) {
            FabricTransferOperations.transfer(25, false, tx -> counter.add(25, tx));
            equal(125, counter.value);
        }
        equal(100, counter.value);
        expectFailure(() -> FabricTransferOperations.transfer(10, false, tx -> {
            counter.add(10, tx);
            throw new IllegalStateException("provider failed");
        }));
        equal(100, counter.value);
        expectFailure(() -> FabricTransferOperations.transfer(10, false, tx -> counter.add(11, tx)));
        equal(100, counter.value);
        equal(1, FabricTransferOperations.transferFluid(2, false,
            (amount, tx) -> counter.add(Math.min(100, amount), tx)));
        equal(181, counter.value);
        equal(1, FabricTransferOperations.transferFluid(2, true,
            (amount, tx) -> counter.add(Math.min(100, amount), tx)));
        equal(181, counter.value);
        equal(0, FabricTransferOperations.transferFluid(1, false,
            (amount, tx) -> counter.add(80, tx)));
        equal(181, counter.value);
        int[] calls = {0};
        expectFailure(() -> FabricTransferOperations.transferFluid(2, false,
            (amount, tx) -> counter.add(++calls[0] == 1 ? 162 : 80, tx)));
        equal(181, counter.value);
        probeJournal();
        System.out.println("Fabric transaction probes passed");
    }

    private static void probeJournal() {
        long[] stored = {10};
        long[] committedFrom = {-1};
        int[] notifications = {0};
        TransferJournal<Long> journal = new TransferJournal<>(() -> stored[0], value -> stored[0] = value,
            original -> committedFrom[0] = original);
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = outer.openNested()) {
                journal.record();
                stored[0] = 20;
                TransferJournal.notifyAfterCommit(() -> notifications[0]++);
                nested.commit();
            }
            equal(20, stored[0]);
            equal(0, notifications[0]);
        }
        equal(10, stored[0]);
        equal(-1, committedFrom[0]);
        equal(0, notifications[0]);
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = outer.openNested()) {
                journal.record();
                stored[0] = 30;
                nested.commit();
            }
            journal.record();
            stored[0] = 40;
            TransferJournal.notifyAfterCommit(() -> notifications[0]++);
            outer.commit();
        }
        equal(40, stored[0]);
        equal(10, committedFrom[0]);
        equal(1, notifications[0]);
        try (Transaction outer = Transaction.openOuter()) {
            journal.record();
            stored[0] = 50;
            try (Transaction nested = outer.openNested()) {
                journal.record();
                stored[0] = 60;
            }
            equal(50, stored[0]);
            outer.commit();
        }
        equal(50, stored[0]);
        equal(40, committedFrom[0]);
        TransferJournal.notifyAfterCommit(() -> notifications[0]++);
        equal(2, notifications[0]);
    }

    private static void equal(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected operation to fail");
    }

    private static final class Counter extends SnapshotParticipant<Long> {
        long value;

        long add(long amount, Transaction transaction) {
            updateSnapshots(transaction);
            value += amount;
            return amount;
        }

        @Override
        protected Long createSnapshot() {
            return value;
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            value = snapshot;
        }
    }
}
