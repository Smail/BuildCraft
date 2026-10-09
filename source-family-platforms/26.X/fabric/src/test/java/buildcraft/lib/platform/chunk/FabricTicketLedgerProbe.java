package buildcraft.lib.platform.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.ChunkPos;

/** Ownership and save/reload checks over the actual Minecraft codec and SavedData class. */
public final class FabricTicketLedgerProbe {
    private FabricTicketLedgerProbe() {}

    public static void main(String[] args) {
        FabricTicketLedger ledger = new FabricTicketLedger();
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 2);
        ChunkPos shared = new ChunkPos(-10, 23);
        check(ledger.change(first, shared, true, true));
        check(!ledger.change(first, shared, true, true));
        check(ledger.change(second, shared, true, true));
        check(ledger.change(first, shared, true, false));
        check(ledger.owners().size() == 2);
        check(ledger.isDirty());
        var encoded = FabricTicketLedger.TYPE.codec().encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
        FabricTicketLedger loaded = FabricTicketLedger.TYPE.codec().parse(NbtOps.INSTANCE, encoded).getOrThrow();
        check(loaded.entries().equals(ledger.entries()));
        check(!loaded.isDirty());
        loaded.removeAllTickets(first);
        check(loaded.contains(shared, true));
        check(!loaded.contains(shared, false));
        check(loaded.owners().equals(java.util.Set.of(second)));
        check(loaded.change(second, shared, false, true));
        check(!loaded.contains(shared, true));
        check(loaded.owners().isEmpty());
        check(!loaded.change(second, shared, false, true));
        System.out.println("Fabric ticket persistence probes passed");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Ticket ledger invariant failed");
        }
    }
}
