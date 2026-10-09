//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.mc263.client;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

/**
 * Bridges BuildCraft's boolean quad shading to 26.3 materials. 26.3 replaced {@code shade} with a shading direction
 * override (null = shade by the quad's own face) and added glint render types for items.
 */
public final class BakedQuadCompat {
    /** Unshaded quads take the brightness of the top face, which is full brightness in every dimension. */
    private static final Direction UNSHADED_DIRECTION = Direction.UP;

    private BakedQuadCompat() {}

    /** The pre-26.3 {@code MaterialInfo(sprite, layer, itemRenderType, tintIndex, shade, lightEmission, ao)}. */
    public static BakedQuad.MaterialInfo materialInfo(TextureAtlasSprite sprite, ChunkSectionLayer layer,
        RenderType itemRenderType, int tintIndex, boolean shade, int lightEmission, boolean ambientOcclusion) {
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(itemRenderType, "itemRenderType");
        boolean translucent = itemRenderType.hasBlending();
        boolean blockAtlas = TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation());
        RenderType glint;
        RenderType glintSpecial;
        if (blockAtlas) {
            glint = translucent ? Sheets.translucentBlockItemGlintSheet() : Sheets.cutoutBlockItemGlintSheet();
            glintSpecial = translucent ? Sheets.translucentBlockItemGlintSpecialSheet() : Sheets.cutoutBlockItemGlintSpecialSheet();
        } else {
            glint = translucent ? Sheets.translucentItemGlintSheet() : Sheets.cutoutItemGlintSheet();
            glintSpecial = translucent ? Sheets.translucentItemGlintSpecialSheet() : Sheets.cutoutItemGlintSpecialSheet();
        }
        return new BakedQuad.MaterialInfo(sprite, layer, itemRenderType, glint, glintSpecial, tintIndex,
            shadeOverride(shade), lightEmission, ambientOcclusion);
    }

    /**
     * The native 26.3 {@code MaterialInfo} constructor. The symbol rewrite redirects every {@code new MaterialInfo(...)}
     * here, so sources that already use the native shape must keep compiling.
     */
    public static BakedQuad.MaterialInfo materialInfo(TextureAtlasSprite sprite, ChunkSectionLayer layer,
        RenderType itemRenderType, RenderType glint, RenderType glintSpecial, int tintIndex,
        @Nullable Direction shadeDirectionOverride, int lightEmission, boolean ambientOcclusion) {
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(itemRenderType, "itemRenderType");
        return new BakedQuad.MaterialInfo(sprite, layer, itemRenderType, glint, glintSpecial, tintIndex,
            shadeDirectionOverride, lightEmission, ambientOcclusion);
    }

    /** @return whether the material is shaded by its own face, the pre-26.3 {@code shade} flag */
    public static boolean shade(BakedQuad.MaterialInfo material) {
        Objects.requireNonNull(material, "material");
        return material.shadeDirectionOverride() == null;
    }

    @Nullable
    private static Direction shadeOverride(boolean shade) {
        return shade ? null : UNSHADED_DIRECTION;
    }
}
