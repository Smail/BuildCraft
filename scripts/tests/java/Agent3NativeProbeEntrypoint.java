package probe;

import net.fabricmc.api.ModInitializer;

/** Only the isolated agent3FluidProbe subprocess includes this probe mod's metadata. */
public final class Agent3NativeProbeEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            var registries = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
                .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
            buildcraft.lib.fluid.FabricFluidProbe.main(new String[0]);
            buildcraft.lib.platform.storage.FabricItemProbe.main(new String[0]);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
