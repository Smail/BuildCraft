package buildcraft.gametest;

import java.util.Objects;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import buildcraft.gametest.PipeGameTestSupport.TestPipe;

/** FE neighbour for pipe GameTests, exposed through the simulate/execute energy storage API. */
public final class EnergyTestEndpoint {
    private final boolean extractable;
    private final int receiveLimit;
    private final int capacity;
    private int stored;
    private int received;
    private int extractions;
    private int lastExtractRequest;

    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(Math.max(0, maxReceive), receiveLimit - received);
            if (accepted <= 0) {
                return 0;
            }
            if (!simulate) {
                received += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = extractable ? Math.min(Math.max(0, maxExtract), stored) : 0;
            if (extracted <= 0) {
                return 0;
            }
            if (!simulate) {
                stored -= extracted;
                extractions++;
                lastExtractRequest = maxExtract;
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return extractable;
        }

        @Override
        public boolean canReceive() {
            return receiveLimit > 0;
        }
    };

    private EnergyTestEndpoint(int stored, boolean extractable, int receiveLimit) {
        if (stored < 0 || receiveLimit < 0) {
            throw new IllegalArgumentException("Negative FE endpoint size: " + stored + ", " + receiveLimit);
        }
        this.extractable = extractable;
        this.receiveLimit = receiveLimit;
        this.capacity = stored;
        this.stored = stored;
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
        pipe.exposeCapability(side, Capabilities.EnergyStorage.BLOCK, storage);
    }

    public int stored() {
        return stored;
    }

    public int received() {
        return received;
    }

    /** Number of extractions that actually removed FE, ignoring simulated ones. */
    public int extractions() {
        return extractions;
    }

    /** Amount requested by the last extraction that actually removed FE. */
    public int lastExtractRequest() {
        return lastExtractRequest;
    }
}
