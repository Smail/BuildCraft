//? source if >=1.21.11
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.client;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import net.neoforged.neoforge.common.NeoForge;
//? if >=26.3 {
import buildcraft.lib.compat.mc263.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
//?} else {
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?}

/**
 * Hooks world-space BuildCraft geometry into the level renderer, so renderers do not depend on the render event of
 * the current NeoForge version.
 */
public final class WorldGeometryEvents {
    /** Renders world-space geometry. {@code modelView} is a private copy the renderer may modify. */
    @FunctionalInterface
    public interface Renderer {
        void render(PoseStack pose, Matrix4f modelView);
    }

    private WorldGeometryEvents() {}

    //? if >=26.3 {
    /** 26.3 has no immediate level buffer: the world buffer source is recorded and submitted as custom geometry. */
    public static void register(Renderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, event -> {
            PoseStack pose = event.getPoseStack();
            if (pose == null) {
                return;
            }
            MultiBufferSource.BufferSource buffers = MultiBufferSource.world();
            buffers.beginSubmission(event.getSubmitNodeCollector());
            try {
                renderer.render(pose, new Matrix4f(pose.last().pose()));
            } finally {
                buffers.endSubmission();
            }
        });
    }
    //?} else {
    /** Draws directly into the level buffers after translucent blocks. */
    public static void register(Renderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, event -> {
            PoseStack pose = event.getPoseStack();
            if (pose == null) {
                return;
            }
            renderer.render(pose, new Matrix4f(event.getModelViewMatrix()));
        });
    }
    //?}
}
