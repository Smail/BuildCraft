//? source if >=26.3
package buildcraft.gametest;

import java.util.Objects;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.gametest.PipeGameTestSupport.TestPipe;

/** FE neighbour for pipe GameTests, exposed through the transactional transfer API. */
public final class EnergyTestEndpoint {
    private final boolean extractable;
    private final int receiveLimit;
    private final int capacity;
    private State state;

    private final SnapshotJournal<State> journal = new SnapshotJournal<>() {
        @Override
        protected State createSnapshot() {
            return state;
        }

        @Override
        protected void revertToSnapshot(State snapshot) {
            state = snapshot;
        }
    };

    private final EnergyHandler handler = new EnergyHandler() {
        @Override
        public long getAmountAsLong() {
            return state.stored;
        }

        @Override
        public long getCapacityAsLong() {
            return capacity;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            requireValid(amount, transaction);
            int accepted = Math.min(amount, receiveLimit - state.received);
            if (accepted <= 0) {
                return 0;
            }
            journal.updateSnapshots(transaction);
            state = new State(state.stored, state.received + accepted, state.extractions, state.lastExtractRequest);
            return accepted;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            requireValid(amount, transaction);
            int extracted = extractable ? Math.min(amount, state.stored) : 0;
            if (extracted <= 0) {
                return 0;
            }
            journal.updateSnapshots(transaction);
            state = new State(state.stored - extracted, state.received, state.extractions + 1, amount);
            return extracted;
        }
    };

    private EnergyTestEndpoint(int stored, boolean extractable, int receiveLimit) {
        if (stored < 0 || receiveLimit < 0) {
            throw new IllegalArgumentException("Negative FE endpoint size: " + stored + ", " + receiveLimit);
        }
        this.extractable = extractable;
        this.receiveLimit = receiveLimit;
        this.capacity = stored;
        this.state = new State(stored, 0, 0, 0);
    }

    /** Extract-only endpoint that starts with the given amount of FE. */
    public static EnergyTestEndpoint source(int stored) {
        return new EnergyTestEndpoint(stored, true, 0);
    }

    /** Receive-only endpoint that reports no buffer and accepts at most the given total. */
    public static EnergyTestEndpoint bufferlessReceiver(int demand) {
        return new EnergyTestEndpoint(0, false, demand);
    }

    public void exposeTo(TestPipe pipe, Direction side) {
        Objects.requireNonNull(pipe, "pipe");
        Objects.requireNonNull(side, "side");
        pipe.exposeCapability(side, Capabilities.Energy.BLOCK, handler);
    }

    public int stored() {
        return state.stored;
    }

    public int received() {
        return state.received;
    }

    /** Number of extractions that actually removed FE, ignoring simulated ones. */
    public int extractions() {
        return state.extractions;
    }

    /** Amount requested by the last extraction that actually removed FE. */
    public int lastExtractRequest() {
        return state.lastExtractRequest;
    }

    private static void requireValid(int amount, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (amount < 0) {
            throw new IllegalArgumentException("Negative energy amount: " + amount);
        }
    }

    private record State(int stored, int received, int extractions, int lastExtractRequest) {
    }
}
