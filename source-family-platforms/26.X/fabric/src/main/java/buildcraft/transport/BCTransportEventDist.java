/* Copyright (c) 2017-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.transport;

import buildcraft.lib.platform.events.BCEvents;
import buildcraft.lib.platform.events.PlatformEvents;
import buildcraft.transport.net.PipeItemMessageQueue;
import buildcraft.transport.wire.WorldSavedDataWireSystems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Retains both native tick phases used by wire systems and the pipe message queue. */
public final class BCTransportEventDist {
    private static boolean registered;

    private BCTransportEventDist() {}

    public static synchronized void registerGameplayEvents() {
        if (registered) return;
        PlatformEvents.levelTick(BCEvents.Phase.START, BCTransportEventDist::onWorldTick);
        PlatformEvents.levelTick(BCEvents.Phase.END, BCTransportEventDist::onWorldTick);
        ServerTickEvents.START_SERVER_TICK.register(server -> onServerTick(new BCEvents.ServerTick(BCEvents.Phase.START)));
        ServerTickEvents.END_SERVER_TICK.register(server -> onServerTick(new BCEvents.ServerTick(BCEvents.Phase.END)));
        PlatformEvents.chunkWatch(BCTransportEventDist::onChunkWatch);
        registered = true;
    }

    public static void onWorldTick(BCEvents.LevelTick event) {
        var level = event.level();
        if (!level.isClientSide() && level.getServer() != null) WorldSavedDataWireSystems.get(level).tick();
    }

    public static void onServerTick(BCEvents.ServerTick event) {
        PipeItemMessageQueue.serverTick();
    }

    public static void onChunkWatch(BCEvents.ChunkWatch event) {
        WorldSavedDataWireSystems.get(event.level()).changedPlayers.add(event.player());
    }
}
