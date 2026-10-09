package buildcraft.lib.platform.storage;

import java.util.Objects;
import java.util.function.ToLongFunction;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Simulation and whole-millibucket operations at the Fabric transaction boundary. */
public final class FabricTransferOperations {
    public static final long DROPLETS_PER_MILLIBUCKET = FluidConstants.BUCKET / 1000;

    private FabricTransferOperations() {}

    @SuppressWarnings("deprecation")
    public static Transaction openTransaction() {
        // Simulation callers cannot pass a context. A nested transaction retains
        // the enclosing operation's ability to abort all changes.
        return Transaction.openNested(Transaction.getCurrentUnsafe());
    }

    public static long transfer(long requested, boolean simulate, ToLongFunction<Transaction> operation) {
        if (requested < 0) {
            throw new IllegalArgumentException("Negative transfer amount: " + requested);
        }
        Objects.requireNonNull(operation, "operation");
        try (Transaction transaction = openTransaction()) {
            long transferred = operation.applyAsLong(transaction);
            checkTransfer(transferred, requested);
            if (!simulate) {
                transaction.commit();
            }
            return transferred;
        }
    }

    public static long droplets(int millibuckets) {
        if (millibuckets < 0) {
            throw new IllegalArgumentException("Negative fluid volume: " + millibuckets);
        }
        return Math.multiplyExact((long) millibuckets, DROPLETS_PER_MILLIBUCKET);
    }

    public static int millibuckets(long droplets) {
        if (droplets < 0) {
            throw new IllegalArgumentException("Negative fluid volume: " + droplets);
        }
        return (int) Math.min(droplets / DROPLETS_PER_MILLIBUCKET, Integer.MAX_VALUE);
    }

    /** Quantize before committing so fractional droplets are never lost or invented. */
    public static int transferFluid(int requested, boolean simulate, FluidOperation operation) {
        return Math.toIntExact(transferFluid((long) requested, simulate, operation));
    }

    public static long transferFluid(long requested, boolean simulate, FluidOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (requested < 0) {
            throw new IllegalArgumentException("Negative fluid volume: " + requested);
        }
        long maximum = Math.min(requested, Long.MAX_VALUE / DROPLETS_PER_MILLIBUCKET)
            * DROPLETS_PER_MILLIBUCKET;
        if (maximum == 0) {
            return 0;
        }
        long probed = transfer(maximum, true, tx -> operation.transfer(maximum, tx));
        long whole = probed / DROPLETS_PER_MILLIBUCKET;
        if (simulate || whole == 0) {
            return whole;
        }
        long exact = whole * DROPLETS_PER_MILLIBUCKET;
        return transfer(exact, false, tx -> {
            long actual = operation.transfer(exact, tx);
            if (actual != exact) {
                throw new IllegalStateException("Fluid storage changed between probe and transfer: "
                    + actual + " of " + exact + " droplets");
            }
            return actual;
        }) / DROPLETS_PER_MILLIBUCKET;
    }

    public static void checkTransfer(long transferred, long requested) {
        if (transferred < 0 || transferred > requested) {
            throw new IllegalStateException("Fabric storage transferred " + transferred + " of " + requested);
        }
    }

    @FunctionalInterface
    public interface FluidOperation {
        long transfer(long droplets, Transaction transaction);
    }
}
