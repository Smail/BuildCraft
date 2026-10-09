/* Copyright (c) 2016-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.lib;

import buildcraft.lib.debug.BCAdvDebugging;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.net.MessageGuideRecipeDisplays;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.platform.actor.BCActors;
import buildcraft.lib.platform.chunk.BCChunkTickets;
import buildcraft.lib.platform.events.BCEvents;
import buildcraft.lib.platform.events.PlatformEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Common events retain the established marker retry schedule and post-tick queue flush. */
public final class BCLibEventDist {
    private static boolean registered;

    private BCLibEventDist() {}

    public static synchronized void registerGameplayEvents() {
        if (registered) return;
        PlatformEvents.entityJoin(BCLibEventDist::onEntityJoinWorld);
        PlatformEvents.levelUnload(BCLibEventDist::onWorldUnload);
        PlatformEvents.serverTick(BCEvents.Phase.END, BCLibEventDist::serverTick);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            if (!joined) MessageUtil.doDelayedServer(1, () -> MessageManager.sendTo(MessageGuideRecipeDisplays.create(player), player));
        });
        registered = true;
    }

    public static void onEntityJoinWorld(BCEvents.EntityJoin event) {
        if (!(event.entity() instanceof ServerPlayer player)) return;
        for (int delay : new int[] {1, 5, 20, 60}) {
            MessageUtil.doDelayedServer(delay, () -> MarkerCache.onPlayerJoinLevel(player));
        }
        MessageUtil.doDelayedServer(5, () -> MessageManager.sendTo(MessageGuideRecipeDisplays.create(player), player));
    }

    public static void onWorldUnload(BCEvents.LevelUnload event) {
        MarkerCache.onLevelUnload(event.level());
        if (event.level() instanceof ServerLevel level) {
            BCActors.unloadWorld(level);
            BCChunkTickets.unloadWorld(level);
        }
    }

    public static void serverTick(BCEvents.ServerTick event) {
        BCAdvDebugging.INSTANCE.onServerPostTick();
        MessageUtil.postServerTick();
    }
}
