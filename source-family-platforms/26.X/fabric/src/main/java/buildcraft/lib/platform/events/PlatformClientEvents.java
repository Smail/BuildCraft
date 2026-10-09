package buildcraft.lib.platform.events;

import java.util.Objects;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Client-only callbacks preserve the separate start/end phases and play-connection lifecycle. */
@Environment(EnvType.CLIENT)
public final class PlatformClientEvents {
    private PlatformClientEvents() {}

    public static void tick(BCEvents.Phase selected, Consumer<BCEvents.ClientTick> handler) {
        Objects.requireNonNull(selected, "Client tick phase");
        Objects.requireNonNull(handler, "Client tick handler");
        BCEvents.ClientTick tick = new BCEvents.ClientTick(selected);
        if (selected == BCEvents.Phase.START) {
            ClientTickEvents.START_CLIENT_TICK.register(client -> handler.accept(tick));
        } else {
            ClientTickEvents.END_CLIENT_TICK.register(client -> handler.accept(tick));
        }
    }

    public static void login(Runnable handler) {
        Objects.requireNonNull(handler, "Client login handler");
        ClientPlayConnectionEvents.JOIN.register((connection, sender, client) -> handler.run());
    }

    public static void logout(Runnable handler) {
        Objects.requireNonNull(handler, "Client logout handler");
        ClientPlayConnectionEvents.DISCONNECT.register((connection, client) -> handler.run());
    }
}
