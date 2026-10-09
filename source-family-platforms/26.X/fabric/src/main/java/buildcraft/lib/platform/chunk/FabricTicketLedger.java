package buildcraft.lib.platform.chunk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;

/** Persistent ownership separate from vanilla's ownerless native ticket set. */
public final class FabricTicketLedger extends SavedData implements BCTicketOwners {
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.fieldOf("owner").forGetter(Entry::owner),
        Codec.LONG.fieldOf("chunk").forGetter(Entry::chunk),
        Codec.BOOL.fieldOf("ticking").forGetter(Entry::ticking)
    ).apply(instance, Entry::new));
    private static final Codec<FabricTicketLedger> CODEC = ENTRY_CODEC.listOf().fieldOf("tickets").codec()
        .xmap(FabricTicketLedger::new, ledger -> List.copyOf(ledger.entries));
    public static final SavedDataType<FabricTicketLedger> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("buildcraftlib", "machines"), FabricTicketLedger::new, CODEC, null);
    private final Set<Entry> entries = new HashSet<>();

    public FabricTicketLedger() {}

    public static void installWorldLoad(ServerLevelEvents.Load restoration) {
        Objects.requireNonNull(restoration, "restoration");
        ServerLevelEvents.LOAD.register(restoration);
    }

    private FabricTicketLedger(List<Entry> entries) {
        this.entries.addAll(entries);
    }

    public record Entry(BlockPos owner, long chunk, boolean ticking) {
        public Entry {
            owner = Objects.requireNonNull(owner, "owner").immutable();
        }
    }

    public Set<Entry> entries() {
        return Set.copyOf(entries);
    }

    public boolean change(BlockPos owner, ChunkPos chunk, boolean add, boolean ticking) {
        Entry entry = new Entry(owner, Objects.requireNonNull(chunk, "chunk").pack(), ticking);
        boolean changed = add ? entries.add(entry) : entries.remove(entry);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean contains(ChunkPos chunk, boolean ticking) {
        long packed = Objects.requireNonNull(chunk, "chunk").pack();
        return entries.stream().anyMatch(entry -> entry.chunk() == packed && entry.ticking() == ticking);
    }

    @Override
    public Set<BlockPos> owners() {
        Set<BlockPos> owners = new HashSet<>();
        for (Entry entry : entries) {
            owners.add(entry.owner());
        }
        return Set.copyOf(owners);
    }

    @Override
    public void removeAllTickets(BlockPos owner) {
        Objects.requireNonNull(owner, "owner");
        if (entries.removeIf(entry -> entry.owner().equals(owner))) {
            setDirty();
        }
    }
}
