//? source if >=26.3
package buildcraft.lib.compat;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import buildcraft.lib.compat.minecraft.components.BCItemData;
import buildcraft.lib.platform.server.PlatformServer;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/** Compatibility helpers for the current ItemStack APIs used by shared BuildCraft code. */
public final class ItemCompat {
    private ItemCompat() {}

    public static boolean isArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
            || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)) {
            return true;
        }
        // Modern data-driven/modded armour may not use the removed ArmorItem subclass.
        // An equippable armour slot plus a positive armour attribute is the closest semantic
        // equivalent without accidentally accepting elytra/pumpkins as robot armour.
        return getArmorSlot(stack) != null && getArmorDefense(stack) > 0;
    }

    public static int getArmorDefense(ItemStack stack) {
        EquipmentSlot slot = getArmorSlot(stack);
        if (slot == null) return 0;
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return Math.max(0, (int) Math.round(modifiers.compute(Attributes.ARMOR, 0.0D, slot)));
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.is(ItemTags.SWORDS));
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.is(ItemTags.AXES));
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.is(ItemTags.PICKAXES));
    }

    public static boolean isShovel(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.is(ItemTags.SHOVELS));
    }

    public static boolean isHoe(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.is(ItemTags.HOES));
    }

    public static boolean isShears(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ConventionalItemTags.SHEAR_TOOLS);
    }

    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return null;
        return switch (equippable.slot()) {
            case HEAD, CHEST, LEGS, FEET -> equippable.slot();
            default -> null;
        };
    }

    /**
     * Furnace fuel is the data-driven {@link DataComponents#COOKING_FUEL} component. Referenced burn times are resolved
     * against the live server registries. Without a server (client-side slot checks) a referenced value cannot be
     * resolved, so the vanilla standard burn time is reported for any item that carries the component.
     */
    public static int getBurnTime(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
        if (fuel == null) return 0;

        MinecraftServer server = PlatformServer.getCurrentServer();
        if (server == null) {
            return fuel.burnTime() instanceof ResolvableInt.Constant constant
                ? Math.max(0, constant.value())
                : AbstractFurnaceBlockEntity.BURN_TIME_STANDARD;
        }
        try {
            LootContext context = new LootContext.Builder(
                new LootParams.Builder(server.overworld()).create(LootContextParamSets.EMPTY)
            ).create(Optional.empty());
            return Math.max(0, ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0));
        } catch (RuntimeException e) { buildcraft.lib.internal.debug.BCLog.caught("ItemCompat.getBurnTime", e);
            // A datapack provider that needs furnace-only context parameters cannot be evaluated for an engine.
            return AbstractFurnaceBlockEntity.BURN_TIME_STANDARD;
        }
    }

    @Nonnull
    public static ItemStack getCraftingRemainingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        @Nullable ItemStackTemplate remainder = stack.getCraftingRemainder();
        if (remainder == null) return ItemStack.EMPTY;
        return remainder.create();
    }

    /** Serialize an ItemStack with the registry-aware ItemStack codec. Empty stacks encode as an empty compound. */
    public static CompoundTag saveOptional(ItemStack stack, HolderLookup.Provider registries) {
        return BCItemData.save(stack, registries);
    }

    /** Decode the current ItemStack codec representation. Legacy normalization is handled by ItemStackUtil. */
    public static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
        return parseOptional(registries, (Tag) tag);
    }

    public static ItemStack parseOptional(HolderLookup.Provider registries, Tag tag) {
        return BCItemData.load(registries, tag);
    }
}
