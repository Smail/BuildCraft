//? source if >=26.3
package buildcraft.energy.generation.features;

import java.util.Objects;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Since 26.3 a feature is a data-driven value: the configuration lives on the feature instance itself. */
public class OilGenFeature implements Feature {
    public static final MapCodec<OilGenFeature> CODEC =
        OilFeatureConfiguration.MAP_CODEC.xmap(OilGenFeature::new, OilGenFeature::config);

    private final OilFeatureConfiguration config;

    public OilGenFeature(OilFeatureConfiguration config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public MapCodec<OilGenFeature> codec() {
        return CODEC;
    }

    public OilFeatureConfiguration config() {
        return config;
    }

    @Override
    public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        return OilDepositPlacer.place(world, origin, random, config);
    }
}
