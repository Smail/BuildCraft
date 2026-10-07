//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.mc263.client.renderer;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import buildcraft.lib.client.render.compat.CapturedBlockEntityRenderer;

/**
 * BuildCraft-owned replacement for the per-render-type buffer lookup that 26.3 removed. BuildCraft renderers only
 * ever ask it for a consumer per {@link RenderType}; geometry ends up in a {@link SubmitNodeCollector}.
 */
@FunctionalInterface
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType type);

    /** The shared world-space buffer that replaces the removed vanilla level buffer source. */
    static BufferSource world() {
        return BufferSource.WORLD;
    }

    /**
     * Records world geometry per render type and submits it as custom geometry while a submission scope is open.
     * Recorded vertices are already in camera space, so they are submitted with an identity pose.
     */
    final class BufferSource implements MultiBufferSource {
        private static final BufferSource WORLD = new BufferSource();

        private CapturedBlockEntityRenderer.RecordingBuffers recording = new CapturedBlockEntityRenderer.RecordingBuffers();
        @Nullable
        private SubmitNodeCollector collector;

        private BufferSource() {}

        /** Opens a submission scope. Geometry recorded outside a scope is discarded on the next flush. */
        public void beginSubmission(SubmitNodeCollector collector) {
            this.collector = Objects.requireNonNull(collector, "collector");
            this.recording = new CapturedBlockEntityRenderer.RecordingBuffers();
        }

        /** Submits everything still recorded and closes the scope. */
        public void endSubmission() {
            try {
                endBatch();
            } finally {
                collector = null;
            }
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return recording.getBuffer(Objects.requireNonNull(type, "type"));
        }

        /** Submits all recorded layers. */
        public void endBatch() {
            List<CapturedBlockEntityRenderer.Layer> layers = recording.finish();
            recording = new CapturedBlockEntityRenderer.RecordingBuffers();
            SubmitNodeCollector target = collector;
            if (target == null) {
                return;
            }
            for (CapturedBlockEntityRenderer.Layer layer : layers) {
                target.submitCustomGeometry(new PoseStack(), layer.type(), (pose, consumer) -> {
                    for (CapturedBlockEntityRenderer.Vertex vertex : layer.vertices()) {
                        vertex.emit(pose, consumer);
                    }
                });
            }
        }

        /** Batches are submitted per scope; flushing a single layer flushes all layers in recording order. */
        public void endBatch(RenderType type) {
            Objects.requireNonNull(type, "type");
            endBatch();
        }
    }
}
