package buildcraft.lib.internal.mj;

import java.util.Objects;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.*;
import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.platform.storage.FabricTransferOperations;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** A micro-MJ endpoint whose backing journal is shared with every sided machine/pipe alias. */
public final class FabricMjPort implements MjPort {
    private final MjPort backing;
    private final TransferJournal<?> journal;

    public FabricMjPort(MjPort backing, TransferJournal<?> journal) {
        this.backing = Objects.requireNonNull(backing, "backing");
        this.journal = Objects.requireNonNull(journal, "backing storage journal");
    }

    public MjPort backingPort() { return backing; }

    @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
        return transfer(offered, mode, true);
    }

    @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
        return transfer(requested, mode, false);
    }

    private MjTransferResult transfer(MjAmount amount, OperationMode mode, boolean inserting) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        long transferred = FabricTransferOperations.transfer(amount.microMj(), mode == OperationMode.SIMULATE,
            transaction -> {
                journal.updateSnapshots(transaction);
                MjTransferResult result = Objects.requireNonNull(inserting
                    ? backing.insert(amount, OperationMode.EXECUTE) : backing.extract(amount, OperationMode.EXECUTE),
                    "MJ transfer result");
                if (!result.requested().equals(amount)) {
                    throw new IllegalStateException("MJ endpoint returned a different requested amount");
                }
                return result.transferred().microMj();
            });
        return MjTransferResult.of(amount, MjAmount.ofMicro(transferred));
    }

    @Override
    public MjTransferResult insert(MjAmount offered, MjTransferPolicy policy, OperationMode mode) {
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(mode, "mode");
        if (policy == MjTransferPolicy.PARTIAL) return insert(offered, mode);
        try (Transaction operation = FabricTransferOperations.openTransaction()) {
            MjTransferResult result = insert(offered, OperationMode.EXECUTE);
            if (!result.completed()) return MjTransferResult.none(offered);
            if (mode == OperationMode.EXECUTE) operation.commit();
            return result;
        }
    }

    @Override
    public MjTransferResult extract(MjAmount minimum, MjAmount maximum, OperationMode mode) {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(mode, "mode");
        if (minimum.compareTo(maximum) > 0) throw new IllegalArgumentException("Invalid MJ extraction range");
        try (Transaction operation = FabricTransferOperations.openTransaction()) {
            MjTransferResult result = extract(maximum, OperationMode.EXECUTE);
            if (result.transferred().compareTo(minimum) < 0) return MjTransferResult.none(maximum);
            if (mode == OperationMode.EXECUTE) operation.commit();
            return result;
        }
    }

    @Override public MjAmount stored() { return backing.stored(); }
    @Override public MjAmount capacity() { return backing.capacity(); }
    @Override public boolean canInsert() { return backing.canInsert(); }
    @Override public boolean canExtract() { return backing.canExtract(); }
}
