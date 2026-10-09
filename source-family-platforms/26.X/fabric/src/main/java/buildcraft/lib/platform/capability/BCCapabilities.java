package buildcraft.lib.platform.capability;

import buildcraft.lib.fluid.BCFluidHandler;
import buildcraft.lib.platform.storage.ItemStorage;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public final class BCCapabilities {
    private BCCapabilities() {}
    public static final class ItemHandler {
        public static final BCBlockCapability<ItemStorage, Direction> BLOCK = BCBlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftlib", "items"), ItemStorage.class);
        private ItemHandler() {}
    }
    public static final class FluidHandler {
        public static final BCBlockCapability<BCFluidHandler, Direction> BLOCK = BCBlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftlib", "fluids"), BCFluidHandler.class);
        private FluidHandler() {}
    }
    public static final class EnergyStorage {
        public static final BCBlockCapability<buildcraft.lib.platform.storage.EnergyStorage, Direction> BLOCK = BCBlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftlib", "fe"), buildcraft.lib.platform.storage.EnergyStorage.class);
        private EnergyStorage() {}
    }
}
