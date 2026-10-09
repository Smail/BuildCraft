package buildcraft.core.client;

import com.mojang.serialization.MapCodec;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.internal.debug.BCLog;

/**
 * Tints the fluid layer of a fragile fluid shard with the colour of the fluid stored in the stack. Fabric item models
 * have no built-in fluid container model, so the shard model references this source on its fluid layer.
 */
@Environment(EnvType.CLIENT)
public record FluidShardTint() implements ItemTintSource {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("buildcraftcore", "fluid_shard");
    public static final MapCodec<FluidShardTint> CODEC = MapCodec.unit(new FluidShardTint());

    private static final int WHITE = 0xFFFFFFFF;

    @Override
    public int calculate(ItemStack stack, ClientLevel level, LivingEntity entity) {
        try {
            BCFluidStack fluid = ItemFragileFluidContainer.getFluid(stack);
            if (fluid.isEmpty()) {
                return WHITE;
            }
            // Fluid variant colours are RGB or ARGB depending on the fluid, an item tint needs an opaque ARGB value.
            return 0xFF000000 | FluidVariantRendering.getColor(FluidVariant.of(fluid.getFluid()));
        } catch (RuntimeException e) {
            BCLog.caught("FluidShardTint", e);
            return WHITE;
        }
    }

    @Override
    public MapCodec<FluidShardTint> type() {
        return CODEC;
    }
}
