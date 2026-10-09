//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.mc263;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;

/** Colour queries that 26.3 removed from {@link ChatFormatting}. The 16 colours are still the first 16 constants. */
public final class ChatFormattingCompat {
    private static final int COLOUR_COUNT = 16;
    private static final ChatFormatting[] VALUES = ChatFormatting.values();

    private ChatFormattingCompat() {}

    /** @return true for the 16 colour codes, false for styles, {@link ChatFormatting#RESET} and null */
    public static boolean isColor(@Nullable ChatFormatting formatting) {
        return formatting != null && formatting.ordinal() < COLOUR_COUNT;
    }

    /** @return the colour with legacy id {@code id} (0-15), or null if the id is not a colour id */
    @Nullable
    public static ChatFormatting getById(int id) {
        if (id < 0 || id >= COLOUR_COUNT || id >= VALUES.length) {
            return null;
        }
        return VALUES[id];
    }
}
