package buildcraft.lib.internal.module;

import net.fabricmc.api.ModInitializer;

/** A BuildCraft module constructed by the common coordinator, not a second main entrypoint. */
public interface FabricModule extends ModInitializer, IBuildCraftMod {}
