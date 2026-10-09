//? source if >=26.3
package buildcraft.lib.fluid;

import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

/** Immutable component-preserving fluid carrier measured in millibuckets. */
public record FabricFluidStack(FluidVariant variant, int amount) {
    public static final FabricFluidStack EMPTY = new FabricFluidStack(FluidVariant.blank(), 0);
    // Retain the existing id/amount/components persistence shape rather than
    // serializing Fabric droplets or its different variant field names.
    public static final Codec<FabricFluidStack> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(
        instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FabricFluidStack::getFluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FabricFluidStack::amount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                .forGetter(stack -> stack.variant().getComponentsPatch())
        ).apply(instance, (fluid, amount, components) -> new FabricFluidStack(FluidVariant.of(fluid, components), amount))));
    public static final Codec<FabricFluidStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC).xmap(
        stack -> stack.orElse(EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));

    public FabricFluidStack {
        Objects.requireNonNull(variant, "variant");
        if (amount < 0) {
            throw new IllegalArgumentException("Negative fluid amount: " + amount);
        }
        if (amount == 0 || variant.isBlank()) {
            variant = FluidVariant.blank();
            amount = 0;
        }
    }

    public FabricFluidStack(Fluid fluid, int amount) {
        this(FluidVariant.of(Objects.requireNonNull(fluid, "fluid")), amount);
    }

    public boolean isEmpty() {
        return amount == 0;
    }

    public int getAmount() {
        return amount;
    }

    public Fluid getFluid() {
        return variant.getFluid();
    }

    public FabricFluidStack copyWithAmount(int amount) {
        return new FabricFluidStack(variant, amount);
    }

    /** Read both current component-based saves and the existing Forge legacy tank format. */
    public static FabricFluidStack parse(HolderLookup.Provider registries, CompoundTag source) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(source, "source");
        CompoundTag normalized = normalizeLegacy(source);
        if (normalized.getInt("amount").filter(amount -> amount == 0).isPresent()) {
            return EMPTY;
        }
        return OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), normalized).getOrThrow();
    }

    private static CompoundTag normalizeLegacy(CompoundTag source) {
        CompoundTag current = source;
        int depth = 0;
        while (current.getString("id").isEmpty() && current.getCompound("Fluid").isPresent()) {
            if (++depth > 64) throw new IllegalArgumentException("Too many nested legacy fluid compounds");
            current = current.getCompound("Fluid").orElseThrow();
        }
        CompoundTag normalized = current.copy();
        if (normalized.getString("id").isEmpty()) {
            for (String key : new String[] {"FluidName", "FluidType", "fluid"}) {
                var id = normalized.getString(key);
                if (id.isPresent()) {
                    normalized.putString("id", id.get());
                    break;
                }
            }
        }
        if (!normalized.contains("amount") && normalized.getInt("Amount").isPresent()) {
            normalized.putInt("amount", normalized.getInt("Amount").orElseThrow());
        }
        for (String key : new String[] {"Tag", "tag"}) {
            if (!current.contains(key)) continue;
            CompoundTag legacy = current.getCompound(key)
                .orElseThrow(() -> new IllegalArgumentException("Legacy fluid data is not a compound"));
            if (!legacy.isEmpty()) {
                if (normalized.contains("components") && normalized.getCompound("components").isEmpty()) {
                    throw new IllegalArgumentException("Fluid components are not a compound");
                }
                CompoundTag components = normalized.getCompound("components").map(CompoundTag::copy)
                    .orElseGet(CompoundTag::new);
                if (!components.contains("minecraft:custom_data")) {
                    components.put("minecraft:custom_data", legacy.copy());
                }
                normalized.put("components", components);
            }
            break;
        }
        return normalized;
    }
}
