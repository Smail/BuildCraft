package buildcraft.lib.fluid;

import buildcraft.lib.compat.transfer.TransferJournal;

/** A fluid handler whose backing owner has a transaction journal, so it can be exported to Fabric's native lookup. */
public interface JournaledFluidHandler extends BCFluidHandler {
    /** The one journal shared by every face of the backing storage. */
    TransferJournal<?> transferJournal();

    /**
     * Journal for handlers that only delegate to {@link Tank}s. Every tank records its own snapshot before it changes,
     * so this journal has no state of its own.
     */
    static TransferJournal<?> delegatingJournal() {
        return new TransferJournal<>(() -> 0, ignored -> {}, ignored -> {});
    }
}
