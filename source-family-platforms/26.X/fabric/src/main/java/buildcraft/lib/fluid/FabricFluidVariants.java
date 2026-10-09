package buildcraft.lib.fluid;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Objects;

import buildcraft.api.v2.fluid.FluidComponentPayload;
import buildcraft.api.v2.fluid.FluidMatchContext;
import buildcraft.api.v2.fluid.FluidVolume;
import buildcraft.lib.internal.data.NbtSquishConstants;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluids;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

/** Shares the existing component payload format across loaders without dropping components. */
public final class FabricFluidVariants {
    private static final Identifier COMPONENT_FORMAT =
        Identifier.fromNamespaceAndPath("buildcraftlib", "neoforge_fluid_components");
    private static final long COMPONENT_DECODE_LIMIT = 2 * 1024 * 1024;
    public static final FluidMatchContext MATCH_CONTEXT = (fluidId, tagId) ->
        BuiltInRegistries.FLUID.getOptional(fluidId)
            .filter(fluid -> fluid != Fluids.EMPTY && fluid.is(TagKey.create(Registries.FLUID, tagId))).isPresent();

    private FabricFluidVariants() {}

    public static buildcraft.api.v2.fluid.FluidVariant toApi(FluidVariant variant, HolderLookup.Provider registries) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(registries, "registries");
        if (variant.isBlank()) {
            throw new IllegalArgumentException("Blank fluid has no API variant");
        }
        Identifier id = BuiltInRegistries.FLUID.getKey(variant.getFluid());
        DataComponentPatch components = variant.getComponentsPatch();
        if (components.isEmpty()) {
            return buildcraft.api.v2.fluid.FluidVariant.of(id);
        }
        var encoded = DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),
            components).getOrThrow();
        if (!(encoded instanceof CompoundTag tag)) {
            throw new IllegalStateException("Fluid components did not encode as a compound");
        }
        return buildcraft.api.v2.fluid.FluidVariant.of(id,
            FluidComponentPayload.of(COMPONENT_FORMAT, encodeComponents(tag)));
    }

    public static FluidVariant toNative(buildcraft.api.v2.fluid.FluidVariant variant, HolderLookup.Provider registries) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(registries, "registries");
        var fluid = BuiltInRegistries.FLUID.getOptional(variant.fluidId())
            .filter(value -> value != Fluids.EMPTY)
            .orElseThrow(() -> new IllegalArgumentException("Unknown fluid: " + variant.fluidId()));
        FluidComponentPayload components = variant.components();
        if (components.isEmpty()) {
            return FluidVariant.of(fluid);
        }
        if (!components.formatId().filter(COMPONENT_FORMAT::equals).isPresent()) {
            throw new IllegalArgumentException("Unsupported fluid component format: " + components.formatId());
        }
        try {
            CompoundTag tag = decodeComponents(components.copyCanonicalBytes());
            DataComponentPatch patch = DataComponentPatch.CODEC.parse(
                registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
            return FluidVariant.of(fluid, patch);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Invalid fluid components for " + variant.fluidId(), failure);
        }
    }

    public static FluidVolume volume(FluidVariant variant, long millibuckets, HolderLookup.Provider registries) {
        if (millibuckets < 0) {
            throw new IllegalArgumentException("Negative fluid amount");
        }
        return millibuckets == 0 ? FluidVolume.empty() : FluidVolume.of(toApi(variant, registries), millibuckets);
    }

    private static byte[] encodeComponents(CompoundTag tag) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes)) {
            // Match NbtSquisher's existing VANILLA payload exactly.
            output.writeShort(NbtSquishConstants.BUILDCRAFT_MAGIC);
            output.writeByte(NbtSquishConstants.VANILLA);
            NbtIo.write(tag, output);
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not serialize fluid components", failure);
        }
    }

    private static CompoundTag decodeComponents(byte[] bytes) throws IOException {
        if (bytes.length > COMPONENT_DECODE_LIMIT) {
            throw new IOException("Fluid component payload exceeds decode limit");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readUnsignedShort() != NbtSquishConstants.BUILDCRAFT_MAGIC
                || input.readUnsignedByte() != NbtSquishConstants.VANILLA) {
                throw new IOException("Unsupported fluid component encoding");
            }
            CompoundTag tag = NbtIo.read(input, NbtAccounter.create(COMPONENT_DECODE_LIMIT));
            if (input.available() != 0) {
                throw new IOException("Trailing bytes in fluid component payload");
            }
            return tag;
        }
    }
}
