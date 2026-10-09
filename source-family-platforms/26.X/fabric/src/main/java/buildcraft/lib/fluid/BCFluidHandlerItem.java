package buildcraft.lib.fluid;

import net.minecraft.world.item.ItemStack;

public interface BCFluidHandlerItem extends BCFluidHandler {
    ItemStack getContainer();
}
