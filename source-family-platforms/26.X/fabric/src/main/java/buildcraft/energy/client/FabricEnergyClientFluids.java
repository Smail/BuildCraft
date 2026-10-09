package buildcraft.energy.client;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.lib.platform.client.PlatformFluidRendering;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Uses the Energy fluid descriptors without retaining baked sprites across resource reloads. */
@Environment(EnvType.CLIENT)
public final class FabricEnergyClientFluids {
    private FabricEnergyClientFluids() {}

    public static void register() {
        if (BCEnergyFluids.OIL_TYPE.size() != BCEnergyFluids.OIL_SOURCE.size()) {
            throw new IllegalStateException("BuildCraft fluid descriptors and source registrations differ in size");
        }
        for (int index = 0; index < BCEnergyFluids.OIL_SOURCE.size(); index++) {
            var descriptor = BCEnergyFluids.OIL_TYPE.get(index).get();
            var fluid = BCEnergyFluids.OIL_SOURCE.get(index).get();
            PlatformFluidRendering.register(fluid, fluid.getFlowing(), descriptor.getStillTextureLocation(),
                descriptor.getFlowTextureLocation(), descriptor.getFluidTintColor());
        }
        if (BCEnergyFluids.SPOUT_OIL_SOURCE == null || BCEnergyFluids.SPOUT_OIL_FLOWING == null
                || BCEnergyFluids.OIL_TYPE.isEmpty()) {
            throw new IllegalStateException("BuildCraft oil spout fluid descriptors have not been registered");
        }
        var descriptor = BCEnergyFluids.OIL_TYPE.getFirst().get();
        PlatformFluidRendering.register(BCEnergyFluids.SPOUT_OIL_SOURCE.get(), BCEnergyFluids.SPOUT_OIL_FLOWING.get(),
            descriptor.getStillTextureLocation(), descriptor.getFlowTextureLocation(), descriptor.getFluidTintColor());
    }
}
