package buildcraft.core.item;

import buildcraft.lib.misc.FluidStackUtil;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import buildcraft.api.v2.drop.FluidDropContext;
import buildcraft.api.v2.drop.FluidDropProvider;
import buildcraft.lib.fluid.FuelApiBridge;
import buildcraft.lib.fluid.BCFluid;
import buildcraft.lib.fluid.FluidCompatRegistry;
import buildcraft.lib.misc.ItemStackUtil;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.StackUtil;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.fluid.BCFluidHandlerItem;
import buildcraft.lib.compat.NbtCompat;
import buildcraft.lib.compat.RegistryCompat;

public class ItemFragileFluidContainer extends Item implements FluidDropProvider {
    public static final int MAX_FLUID_HELD = 500;

    public ItemFragileFluidContainer() {
        super(RegistryCompat.itemProperties(new Item.Properties()).stacksTo(1));
    }

    public Component getName(ItemStack stack) {
        BCFluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) {
            return Component.translatable(getDescriptionId(), Component.translatable("buildcraft.error.empty_fluid"));
        }
        if (fluid.getFluid() instanceof BCFluid bcFluid && bcFluid.isHeatable()) {
            return Component.translatable(getDescriptionId(), bcFluid.getBareLocalizedName(fluid))
                .copy().append(Component.translatable("buildcraft.fluid.heat_" + bcFluid.getHeatValue()));
        }
        return Component.translatable(getDescriptionId(), fluid.getHoverName());
    }
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        CompoundTag data = ItemStackUtil.getCustomDataOrNull(stack);
        CompoundTag fluidTag = data == null ? null : NbtCompat.getCompound(data, "fluid");
        if (fluidTag != null) {
            BCFluidStack fluid = FluidStackUtil.parseOptional(fluidTag);
            if (!fluid.isEmpty()) {
                tooltip.accept(LocaleUtil.localizeFluidStaticAmount(fluid.getAmount(), MAX_FLUID_HELD));
            }
        }
    }

    public void addFluidDrops(NonNullList<ItemStack> toDrop, @Nullable BCFluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        int amount = fluid.getAmount();
        if (amount >= MAX_FLUID_HELD) {
            BCFluidStack fullShard = fluid.copy();
            fullShard.setAmount(MAX_FLUID_HELD);
            while (amount >= MAX_FLUID_HELD) {
                ItemStack stack = new ItemStack(this);
                setFluid(stack, fullShard);
                amount -= MAX_FLUID_HELD;
                toDrop.add(stack);
            }
        }
        if (amount > 0) {
            ItemStack stack = new ItemStack(this);
            setFluid(stack, fluid.copyWithAmount(amount));
            toDrop.add(stack);
        }
    }

    public java.util.Collection<ItemStack> createDrops(FluidDropContext context) {
        NonNullList<ItemStack> drops = NonNullList.create();
        BCFluidStack fluid = FuelApiBridge.stackOf(context.fluid());
        addFluidDrops(drops, fluid);
        return java.util.List.copyOf(drops);
    }

    public static void setFluid(ItemStack container, BCFluidStack fluid) {
        CompoundTag data = ItemStackUtil.getCustomData(container);
        data.put("fluid", FluidStackUtil.saveOptional(fluid));
        ItemStackUtil.setCustomData(container, data);
    }

    @NotNull
    public static BCFluidStack getFluid(ItemStack container) {
        if (container.isEmpty()) {
            return BCFluidStack.EMPTY;
        }
        CompoundTag data = ItemStackUtil.getCustomDataOrNull(container);
        CompoundTag fluidNbt = data == null ? null : NbtCompat.getCompound(data, "fluid");
        return fluidNbt == null ? BCFluidStack.EMPTY : FluidStackUtil.parseOptional(fluidNbt);
    }

    public static final class FragileFluidHandler implements BCFluidHandlerItem {
        @Nonnull
        private ItemStack container;

        public FragileFluidHandler(@Nonnull ItemStack container) {
            this.container = java.util.Objects.requireNonNull(container, "Fluid container");
        }

        public int getTanks() {
            return 1;
        }

        public @NotNull BCFluidStack getFluidInTank(int tank) {
            checkTank(tank);
            return getFluid(container);
        }

        public int getTankCapacity(int tank) {
            checkTank(tank);
            return MAX_FLUID_HELD;
        }

        public boolean isFluidValid(int tank, @NotNull BCFluidStack stack) {
            checkTank(tank);
            return false;
        }

        private static void checkTank(int tank) {
            if (tank != 0) throw new IndexOutOfBoundsException("Fluid container tank: " + tank);
        }

        public int fill(BCFluidStack resource, FluidAction action) {
            return 0;
        }

        public @NotNull BCFluidStack drain(BCFluidStack resource, FluidAction action) {
            BCFluidStack fluid = getFluid(container);
            if (fluid.isEmpty() || resource.isEmpty() || !FluidCompatRegistry.areEquivalent(fluid, resource)) {
                return BCFluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        public @NotNull BCFluidStack drain(int maxDrain, FluidAction action) {
            BCFluidStack fluid = getFluid(container);
            if (fluid.isEmpty() || maxDrain <= 0) {
                return BCFluidStack.EMPTY;
            }
            int amount = Math.min(maxDrain, fluid.getAmount());
            BCFluidStack drained = fluid.copyWithAmount(amount);
            if (action.execute()) {
                fluid.shrink(amount);
                if (fluid.isEmpty()) {
                    container = StackUtil.EMPTY;
                } else {
                    setFluid(container, fluid);
                }
            }
            return drained;
        }

        public ItemStack getContainer() {
            return container;
        }

    }
}

