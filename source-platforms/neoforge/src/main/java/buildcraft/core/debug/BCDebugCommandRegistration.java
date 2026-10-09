package buildcraft.core.debug;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import buildcraft.lib.BCLib;

/** Registers {@code /bcdebug} on the NeoForge game bus. */
@EventBusSubscriber(modid = BCLib.MODID)
public final class BCDebugCommandRegistration {
    private BCDebugCommandRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BCDebugCommands.register(event.getDispatcher());
    }
}
