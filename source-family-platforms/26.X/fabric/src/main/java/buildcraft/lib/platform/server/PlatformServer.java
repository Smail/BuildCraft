package buildcraft.lib.platform.server;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/** Tracks the running {@link MinecraftServer}, the Fabric equivalent of NeoForge's ServerLifecycleHooks. */
public final class PlatformServer {
    private static volatile MinecraftServer server;
    private static boolean installed;

    private PlatformServer() {}

    /** Install once from the common entrypoint. Safe to call repeatedly. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> server = starting);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            if (server == stopped) {
                server = null;
            }
        });
        installed = true;
    }

    /** The running server, or null on a pure client without an integrated server and before startup. */
    @Nullable
    public static MinecraftServer getCurrentServer() {
        return server;
    }
}
