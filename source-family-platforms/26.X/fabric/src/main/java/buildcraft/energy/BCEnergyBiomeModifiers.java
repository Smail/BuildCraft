package buildcraft.energy;

import buildcraft.lib.platform.registry.BCRegistryBinder;
import java.util.Objects;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** The oil feature retains the authoritative API v2 biome and dimension rule checks. */
public final class BCEnergyBiomeModifiers {
    private static final ResourceKey<PlacedFeature> OIL = ResourceKey.create(
        Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("buildcraftenergy", "oil_placed_feature"));
    private static boolean registered;

    private BCEnergyBiomeModifiers() {}

    public static synchronized void register(BCRegistryBinder binder) {
        Objects.requireNonNull(binder, "binder");
        if (registered) throw new IllegalStateException("Fabric oil biome injection registered twice");
        // Include custom dimensions too; addons opt in through the existing WorldgenService.
        BiomeModifications.addFeature(BiomeSelectors.all(), GenerationStep.Decoration.FLUID_SPRINGS, OIL);
        registered = true;
    }
}
