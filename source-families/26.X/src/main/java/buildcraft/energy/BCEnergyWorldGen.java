//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

import com.mojang.serialization.MapCodec;

import buildcraft.lib.platform.registry.BCRegistryBinder;
import buildcraft.lib.platform.registry.BCRegistryEntry;
import buildcraft.lib.platform.registry.BCDeferredRegister;
import buildcraft.energy.generation.features.OilGenFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Registers the code-backed oil feature type. Since 26.3 features are data-driven values, so the registry holds the
 * feature codec and the configured oil feature itself lives in {@code worldgen/feature} data.
 */
public final class BCEnergyWorldGen {
    public static final BCDeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPE_REGISTER =
        BCDeferredRegister.create("minecraft:worldgen/feature_type", BCEnergy.MODID);

    public static final TagKey<Biome> IS_OIL_BIOME = TagKey.create(
        Registries.BIOME, Identifier.fromNamespaceAndPath(BCEnergy.MODID, "is_oil_biome")
    );

    public static final BCRegistryEntry<MapCodec<OilGenFeature>> OIL_FEATURE_TYPE = FEATURE_TYPE_REGISTER.register(
        "worldgen.feature.oil", () -> OilGenFeature.CODEC
    );

    private BCEnergyWorldGen() {
    }

    public static void preInit(BCRegistryBinder modEventBus) {
        BCEnergyBiomeModifiers.register(modEventBus);
        FEATURE_TYPE_REGISTER.register(modEventBus);
    }
}
