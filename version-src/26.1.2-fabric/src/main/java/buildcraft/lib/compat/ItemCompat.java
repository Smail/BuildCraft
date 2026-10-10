package buildcraft.lib.compat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import buildcraft.lib.compat.minecraft.components.BCItemData;
import buildcraft.lib.misc.ItemStackUtil;
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
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.entity.FuelValues;

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
     * Furnace fuel is exposed through the level fuel-values table. Prefer the live server table so datapack fuel
     * overrides are respected, and fall back to the vanilla values built from the active registry lookup when no
     * server is available (client-side slot checks).
     */
    public static int getBurnTime(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        MinecraftServer server = PlatformServer.getCurrentServer();
        FuelValues values = server != null
            ? server.overworld().fuelValues()
            : FuelValues.vanillaBurnTimes(ItemStackUtil.requireActiveRegistryProvider(), FeatureFlags.DEFAULT_FLAGS);
        return Math.max(0, values.burnDuration(stack));
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
