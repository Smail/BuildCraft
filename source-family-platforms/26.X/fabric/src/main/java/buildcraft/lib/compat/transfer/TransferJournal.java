package buildcraft.lib.compat.transfer;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/** One Fabric snapshot participant per backing storage, shared by all sided aliases. */
public final class TransferJournal<S> extends SnapshotParticipant<S> {
    private final Supplier<S> capture;
    private final Consumer<S> restore;
    private final Consumer<S> committed;
    private boolean committingRoot;
    private S originalState;

    public TransferJournal(Supplier<S> capture, Consumer<S> restore, Consumer<S> committed) {
        this.capture = Objects.requireNonNull(capture, "capture");
        this.restore = Objects.requireNonNull(restore, "restore");
        this.committed = Objects.requireNonNull(committed, "committed");
    }

    public void record() {
        TransactionContext transaction = current();
        if (transaction != null) {
            updateSnapshots(transaction);
        }
    }

    @Override
    protected S createSnapshot() {
        return Objects.requireNonNull(capture.get(), "storage snapshot");
    }

    @Override
    protected void readSnapshot(S state) {
        restore.accept(state);
    }

    @Override
    public void onClose(TransactionContext transaction, Transaction.Result result) {
        committingRoot = transaction.nestingDepth() == 0 && result.wasCommitted();
        try {
            super.onClose(transaction, result);
        } finally {
            committingRoot = false;
        }
    }

    @Override
    protected void releaseSnapshot(S state) {
        if (committingRoot) {
            originalState = state;
        }
    }

    @Override
    protected void onFinalCommit() {
        S original = Objects.requireNonNull(originalState, "original storage snapshot");
        originalState = null;
        committed.accept(original);
    }

    @SuppressWarnings("deprecation")
    public static TransactionContext current() {
        return Transaction.getCurrentUnsafe();
    }

    public static boolean active() {
        return current() != null;
    }

    public static boolean defer(Runnable notification) {
        Objects.requireNonNull(notification, "notification");
        TransactionContext transaction = current();
        if (transaction == null) {
            return false;
        }
        new SnapshotParticipant<Boolean>() {
            @Override
            protected Boolean createSnapshot() {
                return Boolean.TRUE;
            }

            @Override
            protected void readSnapshot(Boolean ignored) {
                // No resource mutation: abort discards this notification.
            }

            @Override
            protected void onFinalCommit() {
                notification.run();
            }
        }.updateSnapshots(transaction);
        return true;
    }

    public static void notifyAfterCommit(Runnable notification) {
        if (!defer(notification)) {
            notification.run();
        }
    }
}
