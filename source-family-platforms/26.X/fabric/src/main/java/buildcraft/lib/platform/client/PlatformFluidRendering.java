package buildcraft.lib.platform.client;

import java.util.Objects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRenderHandler;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/** Registers native fluid models, including the corresponding flowing variant, for every atlas bake. */
@Environment(EnvType.CLIENT)
public final class PlatformFluidRendering {
    private PlatformFluidRendering() {}

    public static void register(Fluid still, Fluid flowing, Identifier stillTexture, Identifier flowingTexture, int tint) {
        Objects.requireNonNull(still, "Still fluid");
        Objects.requireNonNull(flowing, "Flowing fluid");
        Objects.requireNonNull(stillTexture, "Still fluid texture");
        Objects.requireNonNull(flowingTexture, "Flowing fluid texture");
        FluidModel.Unbaked model = new FluidModel.Unbaked(new Material(stillTexture),
            new Material(flowingTexture), null, BlockTintSources.constant(tint));
        try {
            FluidRenderingRegistry.register(still, flowing, model);
            FluidVariantRenderHandler variantRenderer = new FluidVariantRenderHandler() {
                @Override
                public int getColor(FluidVariant variant, BlockAndTintGetter level, BlockPos pos) {
                    Objects.requireNonNull(variant, "Fluid variant");
                    return tint;
                }
            };
            FluidVariantRendering.register(still, variantRenderer);
            FluidVariantRendering.register(flowing, variantRenderer);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to register BuildCraft fluid model " + stillTexture, exception);
        }
    }
}
