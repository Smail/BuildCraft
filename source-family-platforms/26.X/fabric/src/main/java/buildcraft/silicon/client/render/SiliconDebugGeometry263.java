/*
 * Copyright (c) 2026 the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.silicon.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import buildcraft.core.client.WorldGeometryEvents;
import buildcraft.lib.debug.BCAdvDebugging;
import buildcraft.lib.debug.IAdvDebugTarget;
import buildcraft.silicon.tile.TileLaser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Draws the advanced debugger's laser cone through the Fabric world geometry capture. */
public final class SiliconDebugGeometry263 {
    private static final ThreadLocal<Boolean> CAPTURING = ThreadLocal.withInitial(() -> false);

    private SiliconDebugGeometry263() {}

    static boolean isCapturing() {
        return CAPTURING.get();
    }

    public static void register() {
        WorldGeometryEvents.register(SiliconDebugGeometry263::render);
    }

    private static void render(PoseStack pose, Matrix4f matrix) {
        IAdvDebugTarget target = BCAdvDebugging.getClientDebugTarget();
        if (!(target instanceof TileLaser laser) || !target.doesExistInWorld()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || player.level() != laser.getLevel()) {
            return;
        }
        CAPTURING.set(true);
        try {
            laser.getDebugRenderer().render(pose, matrix, player,
                minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        } finally {
            CAPTURING.remove();
        }
    }
}
