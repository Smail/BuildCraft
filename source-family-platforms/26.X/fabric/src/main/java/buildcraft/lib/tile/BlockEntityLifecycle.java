//? source if >=26.3
package buildcraft.lib.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.level.block.entity.BlockEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Bridges Fabric block entity lifecycle events to the load and unload hooks that BuildCraft tiles expect. */
public final class BlockEntityLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockEntityLifecycle.class);
    private static final Set<BlockEntity> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean commonRegistered;

    private BlockEntityLifecycle() {}

    /** Install once from the common entrypoint. Safe to call repeatedly. */
    public static synchronized void registerCommon() {
        if (commonRegistered) {
            return;
        }
        // Fabric fires BLOCK_ENTITY_LOAD while the chunk is still being deserialized. Tiles that read
        // neighbouring chunks in onLoad (quarry, frames) would then wait on the chunk they are part of
        // and hang the world load. Defer to the start of the next server tick, like NeoForge does.
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            Objects.requireNonNull(blockEntity, "blockEntity");
            synchronized (PENDING) {
                PENDING.add(blockEntity);
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
            Objects.requireNonNull(blockEntity, "blockEntity");
            boolean wasPending;
            synchronized (PENDING) {
                wasPending = PENDING.remove(blockEntity);
            }
            if (!wasPending) {
                unload(blockEntity);
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> drainPending());
        commonRegistered = true;
    }

    private static void drainPending() {
        List<BlockEntity> batch;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (BlockEntity blockEntity : batch) {
            if (!blockEntity.isRemoved()) {
                load(blockEntity);
            }
        }
    }

    /** Also called by the client entrypoint, which owns the client-side block entity events. */
    public static void load(BlockEntity blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        if (blockEntity instanceof TileBC_Neptune tile) {
            try {
                tile.onLoad();
            } catch (RuntimeException exception) {
                LOGGER.error("BuildCraft tile {} failed during load", blockEntity.getBlockPos(), exception);
            }
        }
    }

    public static void unload(BlockEntity blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        if (blockEntity instanceof TileBC_Neptune tile) {
            try {
                tile.onChunkUnloaded();
            } catch (RuntimeException exception) {
                LOGGER.error("BuildCraft tile {} failed during unload", blockEntity.getBlockPos(), exception);
            }
        }
    }
}
