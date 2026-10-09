package probe;

import buildcraft.lib.net.BCNetworkSide;
import buildcraft.lib.platform.events.BCEvents;
import buildcraft.lib.platform.events.PlatformEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Checks the actual Fabric event invokers rather than a second event implementation. */
public final class FabricEventsProbe {
    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        PlatformEvents.serverTick(BCEvents.Phase.START, event -> events.add("server:" + event.phase()));
        PlatformEvents.serverTick(BCEvents.Phase.END, event -> events.add("server:" + event.phase()));
        PlatformEvents.levelTick(BCEvents.Phase.START, event -> {
            if (event.side() != BCNetworkSide.SERVER) throw new AssertionError("Incorrect server level side");
            events.add("level:" + event.phase());
        });
        PlatformEvents.levelTick(BCEvents.Phase.END, event -> events.add("level:" + event.phase()));
        ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(null);
        ServerTickEvents.START_LEVEL_TICK.invoker().onStartTick(null);
        ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(null);
        ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(null);
        if (!events.equals(List.of("server:START", "level:START", "level:END", "server:END"))) {
            throw new AssertionError("Tick phases changed: " + events);
        }
        System.out.println("Fabric lifecycle phase probe passed");
    }
}
