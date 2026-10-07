//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.mc263;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Block state queries that 26.3 removed from {@link BlockState}. */
public final class BlockStateCompat {
    private BlockStateCompat() {}

    /** The removed {@code BlockState.blocksMotion()}: legacy-solid blocks except cobwebs and bamboo saplings. */
    public static boolean blocksMotion(BlockState state) {
        Objects.requireNonNull(state, "state");
        Block block = state.getBlock();
        return block != Blocks.COBWEB && block != Blocks.BAMBOO_SAPLING && state.isSolid();
    }
}
