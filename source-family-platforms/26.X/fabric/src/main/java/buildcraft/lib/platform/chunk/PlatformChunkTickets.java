package buildcraft.lib.platform.chunk;

import java.util.Objects;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;

/** Owner-aware native tickets restored from persistent machine data on world load. */
public final class PlatformChunkTickets {
    private static TicketType loading;
    private static TicketType ticking;
    private static BiConsumer<ServerLevel, BCTicketOwners> validation;
    private static RuntimeException initializationFailure;
    private static final ServerLevelEvents.Load RESTORE = (server, level) -> restore(level);

    private PlatformChunkTickets() {}

    /** Bind BCChunkTickets::validateTickets before init, from the common entrypoint. */
    public static synchronized void installValidation(BiConsumer<ServerLevel, BCTicketOwners> validator) {
        if (loading != null || initializationFailure != null) {
            throw new IllegalStateException("Chunk-ticket validation must be installed before initialization");
        }
        validation = Objects.requireNonNull(validator, "validator");
    }

    public static synchronized void init() {
        if (initializationFailure != null) {
            throw new IllegalStateException("BuildCraft chunk-ticket initialization previously failed", initializationFailure);
        }
        if (loading != null) {
            return;
        }
        if (validation == null) {
            throw new IllegalStateException("BuildCraft chunk-ticket validator has not been installed");
        }
        // The ledger owns persistence. Native tickets are recreated after loading
        // and are not shared with vanilla /forceload or another controller.
        try {
            TicketType registeredLoading = Registry.register(BuiltInRegistries.TICKET_TYPE,
                Identifier.fromNamespaceAndPath("buildcraftlib", "machines_non_ticking"),
                new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
            TicketType registeredTicking = Registry.register(BuiltInRegistries.TICKET_TYPE,
                Identifier.fromNamespaceAndPath("buildcraftlib", "machines"),
                new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
                    | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
            // Register after vanilla world data exists and before machine tick work.
            FabricTicketLedger.installWorldLoad(RESTORE);
            loading = registeredLoading;
            ticking = registeredTicking;
        } catch (RuntimeException failure) {
            initializationFailure = failure;
            throw failure;
        }
    }

    private static void restore(ServerLevel level) {
        FabricTicketLedger ledger = ledger(level);
        validation.accept(level, ledger);
        for (FabricTicketLedger.Entry entry : ledger.entries()) {
            level.getChunkSource().addTicketWithRadius(type(entry.ticking()), ChunkPos.unpack(entry.chunk()), 2);
        }
    }

    private static FabricTicketLedger ledger(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("BuildCraft chunk tickets must be changed on the server thread");
        }
        return level.getDataStorage().computeIfAbsent(FabricTicketLedger.TYPE);
    }

    private static TicketType type(boolean simulateChunks) {
        TicketType type = simulateChunks ? ticking : loading;
        if (type == null) {
            throw new IllegalStateException("BuildCraft chunk tickets have not been initialized");
        }
        return type;
    }

    public static boolean forceChunk(ServerLevel level, BlockPos owner, ChunkPos chunk, boolean add, boolean ticking) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(chunk, "chunk");
        TicketType nativeType = type(ticking);
        FabricTicketLedger ledger = ledger(level);
        boolean previouslyRequired = ledger.contains(chunk, ticking);
        if (!ledger.change(owner, chunk, add, ticking)) {
            return false;
        }
        boolean nowRequired = ledger.contains(chunk, ticking);
        try {
            if (!previouslyRequired && nowRequired) {
                level.getChunkSource().addTicketWithRadius(nativeType, chunk, 2);
            } else if (previouslyRequired && !nowRequired) {
                level.getChunkSource().removeTicketWithRadius(nativeType, chunk, 2);
            }
        } catch (RuntimeException exception) {
            ledger.change(owner, chunk, !add, ticking);
            throw exception;
        }
        return true;
    }
}
