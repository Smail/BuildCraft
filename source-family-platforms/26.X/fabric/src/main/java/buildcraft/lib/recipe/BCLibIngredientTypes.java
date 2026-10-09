/* Copyright (c) 2017-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.lib.recipe;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Native Fabric registration of the existing strict legacy-NBT recipe ingredient. */
public final class BCLibIngredientTypes {
    public static final CustomIngredientSerializer<LegacyStrictNbtIngredient> STRICT_NBT = new CustomIngredientSerializer<>() {
        @Override
        public Identifier getIdentifier() {
            return Identifier.fromNamespaceAndPath("buildcraftlib", "strict_nbt");
        }

        @Override
        public MapCodec<LegacyStrictNbtIngredient> getCodec() {
            return LegacyStrictNbtIngredient.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LegacyStrictNbtIngredient> getStreamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(LegacyStrictNbtIngredient.CODEC.codec());
        }
    };
    private static boolean registered;

    private BCLibIngredientTypes() {}

    public static synchronized void register() {
        if (registered) throw new IllegalStateException("Fabric BuildCraft ingredient serializers registered twice");
        CustomIngredientSerializer.register(STRICT_NBT);
        registered = true;
    }
}
