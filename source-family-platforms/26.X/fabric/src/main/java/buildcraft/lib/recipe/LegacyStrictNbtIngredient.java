/* Copyright (c) 2017-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.lib.recipe;

import buildcraft.lib.compat.NbtCompat;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Matches full legacy share-tag components and damage, while ignoring stack count. */
public final class LegacyStrictNbtIngredient implements CustomIngredient {
    public static final MapCodec<LegacyStrictNbtIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter((LegacyStrictNbtIngredient value) -> value.item),
        Codec.INT.optionalFieldOf("count", 1).forGetter((LegacyStrictNbtIngredient value) -> value.count),
        Codec.STRING.optionalFieldOf("nbt", "").forGetter((LegacyStrictNbtIngredient value) -> value.legacyNbt)
    ).apply(instance, LegacyStrictNbtIngredient::new));

    private final Item item;
    private final int count;
    private final String legacyNbt;
    private volatile ItemStack displayStack;

    private LegacyStrictNbtIngredient(Item item, int count, String legacyNbt) {
        if (count <= 0) throw new IllegalArgumentException("Legacy strict-NBT ingredient count must be positive");
        this.item = Objects.requireNonNull(item, "item");
        this.count = count;
        this.legacyNbt = Objects.requireNonNull(legacyNbt, "legacyNbt");
    }

    private ItemStack displayStack() {
        ItemStack stack = displayStack;
        if (stack != null) return stack;
        synchronized (this) {
            stack = displayStack;
            if (stack == null) {
                CompoundTag tag;
                try {
                    tag = legacyNbt.isBlank() ? new CompoundTag() : NbtCompat.parseTag(legacyNbt);
                } catch (CommandSyntaxException cause) {
                    throw new IllegalArgumentException("Invalid legacy strict-NBT ingredient tag: " + legacyNbt, cause);
                }
                stack = new ItemStack(item, count);
                if (NbtCompat.contains(tag, "Damage", 99) && stack.isDamageableItem()) {
                    stack.set(DataComponents.DAMAGE, Math.max(0, NbtCompat.getInt(tag, "Damage")));
                    tag.remove("Damage");
                }
                if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
                else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
                displayStack = stack;
            }
        }
        return stack;
    }

    @Override
    public boolean test(ItemStack input) {
        return input != null && !input.isEmpty() && input.getItem() == item
            && ItemStack.isSameItemSameComponents(input, displayStack());
    }

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(item.builtInRegistryHolder());
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return BCLibIngredientTypes.STRICT_NBT;
    }
}
