/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.list;

import javax.annotation.Nonnull;

import buildcraft.api.v2.list.ListMatchType;
import buildcraft.lib.compat.ItemCompat;
import net.minecraft.world.item.ItemStack;

public class ListMatchHandlerTools extends ListMatchHandlerBackend {
    private static final int AXE = 1 << 0;
    private static final int PICKAXE = 1 << 1;
    private static final int SHOVEL = 1 << 2;
    private static final int HOE = 1 << 3;
    private static final int SWORD = 1 << 4;
    private static final int SHEARS = 1 << 5;

    private static int getToolTypes(ItemStack stack) {
        int types = 0;
        if (ItemCompat.isAxe(stack)) types |= AXE;
        if (ItemCompat.isPickaxe(stack)) types |= PICKAXE;
        if (ItemCompat.isShovel(stack)) types |= SHOVEL;
        if (ItemCompat.isHoe(stack)) types |= HOE;
        if (ItemCompat.isSword(stack)) types |= SWORD;
        if (ItemCompat.isShears(stack)) types |= SHEARS;
        return types;
    }

    public boolean matches(ListMatchType type, @Nonnull ItemStack stack, @Nonnull ItemStack target, boolean precise) {
        if (type != ListMatchType.TYPE) {
            return false;
        }

        int sourceTypes = getToolTypes(stack);
        int targetTypes = getToolTypes(target);
        if (sourceTypes == 0 || targetTypes == 0) {
            return false;
        }

        return precise ? sourceTypes == targetTypes : (targetTypes & sourceTypes) == sourceTypes;
    }

    public boolean isValidSource(ListMatchType type, @Nonnull ItemStack stack) {
        return type == ListMatchType.TYPE && getToolTypes(stack) != 0;
    }
}
