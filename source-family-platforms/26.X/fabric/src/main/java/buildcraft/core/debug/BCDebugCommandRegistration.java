package buildcraft.core.debug;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Registers {@code /bcdebug} through Fabric's command registration callback. */
public final class BCDebugCommandRegistration {
    private BCDebugCommandRegistration() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> BCDebugCommands.register(dispatcher)
        );
    }
}
