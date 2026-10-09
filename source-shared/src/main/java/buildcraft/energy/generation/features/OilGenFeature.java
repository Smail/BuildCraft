package buildcraft.energy.generation.features;

import com.mojang.serialization.Codec;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

public class OilGenFeature extends Feature<OilFeatureConfiguration> {

    public OilGenFeature(Codec<OilFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OilFeatureConfiguration> context) {
        if (context == null) {
            return false;
        }
        return OilDepositPlacer.place(context.level(), context.origin(), context.random(), context.config());
    }
}
