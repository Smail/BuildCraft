package buildcraft.lib.fluid;

import java.util.Objects;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Mutable gameplay carrier. Fabric's immutable variant remains the native storage identity. */
public final class BCFluidStack implements DataComponentHolder {
    public static final BCFluidStack EMPTY = new BCFluidStack(Fluids.EMPTY, 0);
    public static final Codec<BCFluidStack> CODEC = FabricFluidStack.CODEC.xmap(BCFluidStack::fromNative, BCFluidStack::toNative);
    public static final Codec<BCFluidStack> OPTIONAL_CODEC = FabricFluidStack.OPTIONAL_CODEC.xmap(BCFluidStack::fromNative, BCFluidStack::toNative);
    public static final StreamCodec<RegistryFriendlyByteBuf, BCFluidStack> OPTIONAL_STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(OPTIONAL_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, BCFluidStack> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);
    private final Fluid fluid;
    private final PatchedDataComponentMap components;
    private int amount;

    public BCFluidStack(Fluid fluid, int amount) {
        this(fluid.builtInRegistryHolder(), amount, DataComponentPatch.EMPTY);
    }

    public BCFluidStack(Holder<Fluid> fluid, int amount) {
        this(fluid, amount, DataComponentPatch.EMPTY);
    }

    public BCFluidStack(Holder<Fluid> fluid, int amount, DataComponentPatch patch) {
        this.fluid = Objects.requireNonNull(fluid, "fluid").value();
        if (amount < 0) throw new IllegalArgumentException("Negative fluid amount: " + amount);
        this.amount = this.fluid == Fluids.EMPTY ? 0 : amount;
        components = PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, Objects.requireNonNull(patch, "components"));
    }

    public static BCFluidStack fromNative(FabricFluidStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.isEmpty() ? EMPTY : new BCFluidStack(stack.getFluid().builtInRegistryHolder(), stack.amount(), stack.variant().getComponentsPatch());
    }

    public FabricFluidStack toNative() {
        return isEmpty() ? FabricFluidStack.EMPTY : new FabricFluidStack(FluidVariant.of(fluid, components.asPatch()), amount);
    }
    public FabricFluidStack toFabric() { return toNative(); }

    public boolean isEmpty() { return amount == 0 || fluid == Fluids.EMPTY; }
    public Fluid getFluid() { return isEmpty() ? Fluids.EMPTY : fluid; }
    public Holder<Fluid> getFluidHolder() { return getFluid().builtInRegistryHolder(); }
    public int getAmount() { return isEmpty() ? 0 : amount; }
    public DataComponentPatch getComponentsPatch() { return components.asPatch(); }
    @Override public DataComponentMap getComponents() { return components; }

    private void requireMutable() {
        if (this == EMPTY) throw new IllegalStateException("Cannot mutate the shared empty fluid stack");
    }

    public void setAmount(int value) {
        requireMutable();
        if (value < 0) throw new IllegalArgumentException("Negative fluid amount: " + value);
        amount = fluid == Fluids.EMPTY ? 0 : value;
    }

    public void grow(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative growth");
        setAmount(Math.addExact(amount, value));
    }

    public void shrink(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative shrink");
        setAmount(Math.max(0, amount - value));
    }

    public BCFluidStack copy() { return copyWithAmount(getAmount()); }
    public BCFluidStack copyWithAmount(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative fluid amount: " + value);
        return value == 0 || fluid == Fluids.EMPTY ? EMPTY : new BCFluidStack(fluid.builtInRegistryHolder(), value, components.asPatch());
    }
    public <T> T set(DataComponentType<T> type, T value) { requireMutable(); return components.set(type, value); }
    public <T> T remove(DataComponentType<? extends T> type) { requireMutable(); return components.remove(type); }
    public void applyComponents(DataComponentPatch patch) { requireMutable(); components.applyPatch(patch); }
    public void applyComponents(DataComponentMap map) { requireMutable(); components.setAll(map); }
    public buildcraft.energy.fluid.BCFluidType getFluidType() { return buildcraft.energy.fluid.BCFluidType.of(getFluid()); }
    public Component getHoverName() { return Component.translatable(getFluidType().getDescriptionId()); }
    public String getDescriptionId() { return getFluidType().getDescriptionId(); }
    public static boolean isSameFluidSameComponents(BCFluidStack first, BCFluidStack second) {
        return first.getFluid() == second.getFluid() && first.components.equals(second.components);
    }
    public static boolean isSameFluid(BCFluidStack first, BCFluidStack second) { return first.getFluid() == second.getFluid(); }
    public static int hashFluidAndComponents(BCFluidStack stack) { return 31 * System.identityHashCode(stack.getFluid()) + stack.components.hashCode(); }
    public static BCFluidStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
        return fromNative(FabricFluidStack.parse(registries, tag));
    }
    public Tag saveOptional(HolderLookup.Provider registries) {
        return OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow();
    }
    @Override public String toString() { return getAmount() + "mB " + toNative().variant(); }
}
