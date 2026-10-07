//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.mc263.blaze3d.vertex;

import java.util.Objects;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;

/**
 * Replacement for the vertex scratch buffer that 26.3 removed. It only builds CPU-side meshes; BuildCraft's
 * remaining immediate-mode call sites hand the result to the inert compatibility uploader.
 */
public final class Tesselator {
    private static final int DEFAULT_BYTES = 786432;
    private static final Tesselator INSTANCE = new Tesselator();

    private final ByteBufferBuilder buffer;

    public Tesselator(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Tesselator size must be positive: " + size);
        }
        this.buffer = new ByteBufferBuilder(size);
    }

    public Tesselator() {
        this(DEFAULT_BYTES);
    }

    public static Tesselator getInstance() {
        return INSTANCE;
    }

    public BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(format, "format");
        return new BufferBuilder(buffer, topology, format);
    }

    public void clear() {
        buffer.clear();
    }
}
