//? source if >=26.2
/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.fluid;

import buildcraft.lib.platform.client.ClientAtlas;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;

import buildcraft.lib.client.model.MutableVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.VecUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.fluid.FabricFluidStack;
import buildcraft.lib.platform.storage.FluidStorage;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.minecraft.core.registries.BuiltInRegistries;
import buildcraft.lib.compat.RenderCompat;

/** Can render 3D fluid cuboid's, up to 1x1x1 in size. Note that they *must* be contained within the 1x1x1 block space -
 * you can't use this to render off large multiblocks. Not thread safe -- this uses static variables so you should only
 * call this from the main client thread. */
// Perhaps move this into IModelRenderer? And that way we get the buffer, force shaders to cope with fluids (?!), etc
@Environment(EnvType.CLIENT)
public class FluidRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("buildcraft.fluid.render");

    private static final EnumMap<FluidSpriteType, Map<String, TextureAtlasSprite>> fluidSprites =
        new EnumMap<>(FluidSpriteType.class);
    public static final MutableVertex vertex = new MutableVertex();
    private static final boolean[] DEFAULT_FACES = { true, true, true, true, true, true };
    private static Function<Identifier, TextureAtlasSprite> blockTexMap;

    // Cached fields that prevent lots of arguments on most methods
    private static VertexConsumer bb;
    private static Pose pose;
    private static TextureAtlasSprite sprite;
    private static TexMap texmap;
    private static int color = 0xFFFFFFFF; //ARGB
    private static boolean invertU, invertV;
    private static double xTexDiff, yTexDiff, zTexDiff;


    static {
        for (FluidSpriteType type : FluidSpriteType.values()) {
            fluidSprites.put(type, new HashMap<>());
        }
    }

    private static net.minecraft.client.renderer.block.FluidModel nativeModel(Fluid fluid) {
        var models = Objects.requireNonNull(Minecraft.getInstance().getModelManager().getFluidStateModelSet(),
            "Fluid models have not completed reloading");
        return Objects.requireNonNull(models.get(Objects.requireNonNull(fluid, "Fluid").defaultFluidState()),
            "No baked fluid model");
    }

    private static FluidVariant fluidVariant(Fluid fluid, FabricFluidStack stack) {
        return stack == null || stack.isEmpty() ? FluidVariant.of(fluid) : stack.variant();
    }

    private static int fluidColor(Fluid fluid, FabricFluidStack stack) {
        return FluidVariantRendering.getColor(fluidVariant(fluid, stack));
    }

    private static Identifier getStillTextureSafe(Fluid fluid, FabricFluidStack stack) {
        return getFluidTextureSafe(
            fluid, "still",
            () -> nativeModel(fluid).stillMaterial().sprite().contents().name(),
            MissingTextureAtlasSprite::getLocation
        );
    }

    private static Identifier getFlowingTextureSafe(Fluid fluid, FabricFluidStack stack) {
        return getFluidTextureSafe(
            fluid, "flowing",
            () -> nativeModel(fluid).flowingMaterial().sprite().contents().name(),
            () -> getStillTextureSafe(fluid, stack)
        );
    }

    private static Identifier getFluidTextureSafe(
        Fluid fluid, String kind, Supplier<Identifier> getter, Supplier<Identifier> fallback
    ) {
        try {
            Identifier texture = getter.get();
            if (texture != null) {
                return texture;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "[lib.fluid.render] Failed to resolve {} texture for fluid {}; using fallback",
                kind, BuiltInRegistries.FLUID.getKey(fluid), exception
            );
        }
        return fallback.get();
    }

    /** Refreshes all fluid sprites after the 1.20 block atlas has been uploaded. */
    public static void onTextureStitchPost(ClientAtlas.After event) {
        if (!net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS.equals(event.getAtlas().location())) {
            return;
        }
        clearSpriteCache();
        blockTexMap = event.getAtlas()::getSprite;

        // Fluid models are published after atlas application. Resolve sprites lazily from that generation.
    }

    private static void clearSpriteCache() {
        for (FluidSpriteType type : FluidSpriteType.values()) {
            fluidSprites.get(type).clear();
        }
        blockTexMap = null;
    }

    /** Renders a fluid cuboid to the given vertex buffer. The cube shouldn't cross over any 0 to 1 boundary
     * (so the cube must be contained within a block).
     *
     * @param type The type of sprite to use. See {@link FluidSpriteType} for more details.
     * @param tank The fluid tank that should be rendered.
     * @param min The minimum coordinate that the tank should be rendered from
     * @param max The maximum coordinate that the tank will be rendered to.
     * @param bbIn The {@link BufferBuilder} that the fluid will be rendered into.
     * @param sideRender A size 6 boolean array that determines if the face will be rendered. If it is null then all
     *            faces will be rendered. The indexes are determined by what {@link Direction#ordinal()} returns.
     * @see #renderFluid(FluidSpriteType, FabricFluidStack, double, double, Vec3, Vec3, BufferBuilder, boolean[]) */
    public static void renderFluid(FluidSpriteType type, FluidStorage<?> tank, Vec3 min, Vec3 max, VertexConsumer bbIn, Pose matrix,
        boolean[] sideRender) {
        Objects.requireNonNull(tank, "Fluid storage");
        if (tank.getTanks() == 0) return;
        Object fluid = tank.getFluidInTank(0);
        if (fluid instanceof BCFluidStack gameplay) {
            renderFluid(type, gameplay, tank.getTankCapacity(0), min, max, bbIn, matrix, sideRender);
        } else if (fluid instanceof FabricFluidStack nativeStack) {
            renderFluid(type, nativeStack, tank.getTankCapacity(0), min, max, bbIn, matrix, sideRender);
        } else {
            throw new IllegalArgumentException("Unsupported fluid stack type: " + fluid);
        }
    }

    /** Render's a fluid cuboid to the given vertex buffer. The cube shouldn't cross over any 0 to 1 boundary
     * (so the cube must be contained within a block).
     *
     * @param type The type of sprite to use. See {@link FluidSpriteType} for more details.
     * @param fluid The stack that represents the fluid to render
     * @param cap The maximum amount of fluid that could be in the stack. Usually the capacity of the tank.
     * @param min The minimum coordinate that the tank should be rendered from
     * @param max The maximum coordinate that the tank will be rendered to.
     * @param bbIn The {@link BufferBuilder} that the fluid will be rendered into.
     * @param sideRender A size 6 boolean array that determines if the face will be rendered. If it is null then all
     *            faces will be rendered. The indexes are determined by what {@link Direction#ordinal()} returns. */
    public static void renderFluid(FluidSpriteType type, FabricFluidStack fluid, int cap, Vec3 min, Vec3 max,
    		VertexConsumer bbIn, Pose matrix, boolean[] sideRender) {
        renderFluid(type, fluid, fluid == null ? 0 : fluid.getAmount(), cap, min, max, bbIn, matrix,sideRender);
    }

    /** Render's a fluid cuboid to the given vertex buffer. The cube shouldn't cross over any 0 to 1 boundary
     * (so the cube must be contained within a block).
     *
     * @param type The type of sprite to use. See {@link FluidSpriteType} for more details.
     * @param fluid The stack that represents the fluid to render. Note that the amount from the stack is NOT used.
     * @param amount The actual amount of fluid in the stack. Is a "double" rather than an "int" as then you can
     *            interpolate between frames.
     * @param cap The maximum amount of fluid that could be in the stack. Usually the capacity of the tank.
     * @param min The minimum coordinate that the tank should be rendered from
     * @param max The maximum coordinate that the tank will be rendered to.
     * @param fluidBuffer The {@link VertexConsumer} that the fluid will be rendered into.
     * @param matrix the last position for render
     * @param sideRender A size 6 boolean array that determines if the face will be rendered. If it is null then all
     *            faces will be rendered. The indexes are determined by what {@link Direction#ordinal()} returns. */
    public static void renderFluid(FluidSpriteType type, FabricFluidStack fluid, double amount, double cap, Vec3 min,
        Vec3 max, VertexConsumer fluidBuffer, Pose matrix, boolean[] sideRender) {
        if (fluid == null || fluid.getFluid() == null || amount <= 0 )
            return;
        renderFluidInteral(type, fluid, fluid.getFluid(), amount, cap, min, max, fluidBuffer, matrix, sideRender, 0x00F000F0);
    }

    /** Use only {@link Fluid} for render in order to avoid too often {@link FabricFluidStack} creation
     *  As a price, parameter of {@link FluidRenderer#getFluidSprite} will only been {@link FabricFluidStack#EMPTY}
     *  So this method may render different effect if the Fluid need special use of the parameter.(Such as NBT data in the ItemStack)
     *
     * @param type The type of sprite to use. See {@link FluidSpriteType} for more details.
     * @param fluidType The fluid to render.
     * @param amount The actual amount of fluid in the stack. Is a "double" rather than an "int" as then you can
     *            interpolate between frames.
     * @param cap The maximum amount of fluid that could be in the stack. Usually the capacity of the tank.
     * @param min The minimum coordinate that the tank should be rendered from
     * @param max The maximum coordinate that the tank will be rendered to.
     * @param fluidBuffer The {@link VertexConsumer} that the fluid will be rendered into.
     * @param matrix the last position for render
     * @param sideRender A size 6 boolean array that determines if the face will be rendered. If it is null then all
     *            faces will be rendered. The indexes are determined by what {@link Direction#ordinal()} returns. */
    public static void renderFluid(FluidSpriteType type, Fluid fluidType, double amount, double cap, Vec3 min,
	        Vec3 max, VertexConsumer fluidBuffer, Pose matrix, boolean[] sideRender) {
        if (fluidType == null || amount <= 0 )
            return;
        renderFluidInteral(type, FabricFluidStack.EMPTY, fluidType, amount, cap, min, max, fluidBuffer, matrix, sideRender, 0x00F000F0);
    }

    /** Same as the interpolated FabricFluidStack overload, but uses the caller-provided packed block/sky light. */
    public static void renderFluid(FluidSpriteType type, FabricFluidStack fluid, double amount, double cap, Vec3 min, Vec3 max,
        VertexConsumer fluidBuffer, Pose matrix, boolean[] sideRender, int packedLight) {
        if (fluid == null || fluid.isEmpty() || amount <= 0) return;
        renderFluidInteral(type, fluid, fluid.getFluid(), amount, cap, min, max, fluidBuffer, matrix, sideRender, packedLight);
    }

    /** Same as the raw-fluid overload, but uses the caller-provided packed block/sky light. */
    public static void renderFluid(FluidSpriteType type, Fluid fluidType, double amount, double cap, Vec3 min, Vec3 max,
        VertexConsumer fluidBuffer, Pose matrix, boolean[] sideRender, int packedLight) {
        if (fluidType == null || amount <= 0) return;
        renderFluidInteral(type, FabricFluidStack.EMPTY, fluidType, amount, cap, min, max, fluidBuffer, matrix, sideRender, packedLight);
    }

    /** Gameplay-carrier overloads. Rendering reads the immutable native stack, so these only convert. */
    public static void renderFluid(FluidSpriteType type, BCFluidStack fluid, int cap, Vec3 min, Vec3 max,
        VertexConsumer bbIn, Pose matrix, boolean[] sideRender) {
        renderFluid(type, fluid, fluid == null ? 0 : fluid.getAmount(), cap, min, max, bbIn, matrix, sideRender);
    }

    public static void renderFluid(FluidSpriteType type, BCFluidStack fluid, double amount, double cap, Vec3 min,
        Vec3 max, VertexConsumer fluidBuffer, Pose matrix, boolean[] sideRender) {
        renderFluid(type, fluid == null ? null : fluid.toNative(), amount, cap, min, max, fluidBuffer, matrix, sideRender);
    }

    public static void renderFluid(FluidSpriteType type, BCFluidStack fluid, double amount, double cap, Vec3 min, Vec3 max,
        VertexConsumer fluidBuffer, Pose matrix, boolean[] sideRender, int packedLight) {
        renderFluid(type, fluid == null ? null : fluid.toNative(), amount, cap, min, max, fluidBuffer, matrix, sideRender, packedLight);
    }

    public static TextureAtlasSprite getFluidSprite(FluidSpriteType type, Fluid fluid, BCFluidStack stack) {
        return getFluidSprite(type, fluid, stack == null ? FabricFluidStack.EMPTY : stack.toNative());
    }

    public static void drawFluidForGui(BCFluidStack fluid, double startX, double startY, double endX, double endY, GuiGraphicsExtractor guiGraphics) {
        if (fluid == null) return;
        drawFluidForGui(fluid.toNative(), startX, startY, endX, endY, guiGraphics);
    }

    private static void renderFluidInteral(FluidSpriteType type, FabricFluidStack texParam, Fluid fluidType, double amount, double cap, Vec3 min,
	        Vec3 max, VertexConsumer fluidBuffer, Pose matrix, boolean[] sideRender, int packedLight) {
        Objects.requireNonNull(fluidType, "Fluid type");
        Objects.requireNonNull(min, "Fluid minimum bounds");
        Objects.requireNonNull(max, "Fluid maximum bounds");
        Objects.requireNonNull(fluidBuffer, "Fluid vertex consumer");
        Objects.requireNonNull(matrix, "Fluid pose");
        if (!Double.isFinite(cap) || cap <= 0 || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("Fluid amount must be finite and capacity must be positive");
        }
        if (sideRender != null && sideRender.length != Direction.values().length) {
            throw new IllegalArgumentException("Fluid face mask must contain six sides");
        }
        try {
        if (sideRender == null)
            sideRender = DEFAULT_FACES;
        if (type == null)
            type = FluidSpriteType.STILL;
        sprite = getFluidSprite(type, fluidType, texParam);

        double height = Mth.clamp(amount / cap, 0, 1);
        final Vec3 realMin, realMax;
        if (FluidVariantAttributes.isLighterThanAir(fluidVariant(fluidType, texParam))) {
            realMin = VecUtil.replaceValue(min, Axis.Y, MathUtil.interp(1 - height, min.y, max.y));
            realMax = max;
        } else {
            realMin = min;
            realMax = VecUtil.replaceValue(max, Axis.Y, MathUtil.interp(height, min.y, max.y));
        }

        bb = fluidBuffer;
        pose = matrix;
        vertex.lighti(packedLight);


        final double xs = realMin.x;
        final double ys = realMin.y;
        final double zs = realMin.z;

        final double xb = realMax.x;
        final double yb = realMax.y;
        final double zb = realMax.z;

        if (type == FluidSpriteType.FROZEN) {
            if (min.x >= 1) {
                xTexDiff = Math.floor(min.x);
            } else if (min.x < 0) {
                xTexDiff = Math.floor(min.x);
            } else {
                xTexDiff = 0;
            }
            if (min.y >= 1) {
                yTexDiff = Math.floor(min.y);
            } else if (min.y < 0) {
                yTexDiff = Math.floor(min.y);
            } else {
                yTexDiff = 0;
            }
            if (min.z >= 1) {
                zTexDiff = Math.floor(min.z);
            } else if (min.z < 0) {
                zTexDiff = Math.floor(min.z);
            } else {
                zTexDiff = 0;
            }
        } else {
            xTexDiff = 0;
            yTexDiff = 0;
            zTexDiff = 0;
        }
        vertex.colouri(fluidColor(fluidType, texParam));

        setTexMap(TexMap.XZ, false, false);
        if (sideRender[Direction.UP.ordinal()]) {
            vertex.normalf(0, 1, 0);
            vertex(xs, yb, zb);
            vertex(xb, yb, zb);
            vertex(xb, yb, zs);
            vertex(xs, yb, zs);
        }

        setTexMap(TexMap.XZ, false, true);
        if (sideRender[Direction.DOWN.ordinal()]) {
            vertex.normalf(0, -1, 0);
            vertex(xs, ys, zs);
            vertex(xb, ys, zs);
            vertex(xb, ys, zb);
            vertex(xs, ys, zb);
        }

        setTexMap(TexMap.ZY, false, true);
        if (sideRender[Direction.WEST.ordinal()]) {
            vertex.normalf(-1, 0, 0);
            vertex(xs, ys, zs);
            vertex(xs, ys, zb);
            vertex(xs, yb, zb);
            vertex(xs, yb, zs);
        }

        setTexMap(TexMap.ZY, true, true);
        if (sideRender[Direction.EAST.ordinal()]) {
            vertex.normalf(1, 0, 0);
            vertex(xb, yb, zs);
            vertex(xb, yb, zb);
            vertex(xb, ys, zb);
            vertex(xb, ys, zs);
        }

        setTexMap(TexMap.XY, true, true);
        if (sideRender[Direction.NORTH.ordinal()]) {
            vertex.normalf(0, 0, -1);
            vertex(xs, yb, zs);
            vertex(xb, yb, zs);
            vertex(xb, ys, zs);
            vertex(xs, ys, zs);
        }

        setTexMap(TexMap.XY, false, true);
        if (sideRender[Direction.SOUTH.ordinal()]) {
            vertex.normalf(0, 0, 1);
            vertex(xs, ys, zb);
            vertex(xb, ys, zb);
            vertex(xb, yb, zb);
            vertex(xs, yb, zb);
        }


        } finally {
            sprite = null;
            texmap = null;
            bb = null;
            pose = null;
        }
    }


    private static void setTexMap(TexMap map, boolean flipU, boolean flipV) {
        texmap = map;
        invertU = flipU;
        invertV = flipV;
    }

    public static TextureAtlasSprite getFluidSprite(FluidSpriteType type, Fluid fluid, FabricFluidStack stack) {
        if (fluid == null) {
            return RenderCompat.blockSprites().apply(MissingTextureAtlasSprite.getLocation());
        }
        type = Objects.requireNonNullElse(type, FluidSpriteType.STILL);
        TextureAtlasSprite s = getSprite(type, BuiltInRegistries.FLUID.getKey(fluid).toString(), fluid, stack);
        return s != null ? s : RenderCompat.blockSprites().apply(MissingTextureAtlasSprite.getLocation());
    }

    private static TextureAtlasSprite getSprite(FluidSpriteType type, String key, Fluid fluid, FabricFluidStack stack) {
    	TextureAtlasSprite tex = fluidSprites.get(type).get(key);
    	if(blockTexMap == null) blockTexMap = RenderCompat.blockSprites();
    	switch(type){
		case FLOWING:
			if(tex == null)
				tex = blockTexMap.apply(getFlowingTextureSafe(fluid, stack));
			fluidSprites.get(type).put(key, tex);
			break;
		case STILL:
			if(tex == null)
				tex = blockTexMap.apply(getStillTextureSafe(fluid, stack));
			fluidSprites.get(type).put(key, tex);
			break;
		case FROZEN:
			if (tex == null) {
                tex = blockTexMap.apply(getStillTextureSafe(fluid, stack));
            }
			fluidSprites.get(type).put(key, tex);
			break;
    	}
    	return tex;
    }

    /** Helper function to add a vertex. */
    private static void vertex(double x, double y, double z) {

        vertex.positiond(x, y, z);
        texmap.apply(x - xTexDiff, y - yTexDiff, z - zTexDiff);
        vertex.renderAsBlock(pose.pose(), pose.normal(), bb);
    }

    /** Native deferred GUI path. The GUI renderer no longer consumes PoseStack vertices,
     * so tile the atlas sprite through GuiGraphicsExtractor instead of immediate BufferBuilder rendering. */
    public static void drawFluidForGui(FabricFluidStack fluid, double startX, double startY, double endX, double endY, GuiGraphicsExtractor guiGraphics) {
        if (fluid == null || fluid.isEmpty()) return;
        TextureAtlasSprite fluidSprite = getFluidSprite(FluidSpriteType.STILL, fluid.getFluid(), fluid);
        int tint = FluidVariantRendering.getColor(fluid.variant());
        drawFluidForGuiNative(fluidSprite, tint, startX, startY, endX, endY, guiGraphics);
    }

    public static void drawFluidForGui(Fluid fluid, double startX, double startY, double endX, double endY, GuiGraphicsExtractor guiGraphics) {
        if (fluid == null) return;
        TextureAtlasSprite fluidSprite = getFluidSprite(FluidSpriteType.STILL, fluid, FabricFluidStack.EMPTY);
        int tint = FluidVariantRendering.getColor(FluidVariant.of(fluid));
        drawFluidForGuiNative(fluidSprite, tint, startX, startY, endX, endY, guiGraphics);
    }

    private static void drawFluidForGuiNative(TextureAtlasSprite fluidSprite, int tint, double startX, double startY,
        double endX, double endY, GuiGraphicsExtractor guiGraphics) {
        if (fluidSprite == null) {
            fluidSprite = RenderCompat.blockSprites().apply(MissingTextureAtlasSprite.getLocation());
        }
        // Fabric's FluidVariantRendering.getColor is 0xRRGGBB without alpha, while blitSprite takes ARGB: a zero alpha
        // makes the fluid completely transparent.
        tint = 0xFF000000 | tint;
        int minX = (int) Math.floor(Math.min(startX, endX));
        int maxX = (int) Math.ceil(Math.max(startX, endX));
        int minY = (int) Math.floor(Math.min(startY, endY));
        int maxY = (int) Math.ceil(Math.max(startY, endY));
        if (maxX <= minX || maxY <= minY) return;

        guiGraphics.enableScissor(minX, minY, maxX, maxY);
        try {
            for (int x = minX; x < maxX; x += 16) {
                for (int y = minY; y < maxY; y += 16) {
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, fluidSprite, x, y, 16, 16, tint);
                }
            }
        } finally {
            guiGraphics.disableScissor();
        }
    }

    /** Used to keep track of what position maps to what texture co-ord.
     * <p>
     * For example XY maps X to U and Y to V, and ignores Z */
    private enum TexMap {
        XY(true, true),
        XZ(true, false),
        ZY(false, true);

        /** If true, then X maps to U. Otherwise Z maps to U. */
        private final boolean ux;
        /** If true, then Y maps to V. Otherwise Z maps to V. */
        private final boolean vy;

        TexMap(boolean ux, boolean vy) {
            this.ux = ux;
            this.vy = vy;
        }

        /** Changes the vertex's texture co-ord to be the same as the position, for that face. (Uses {@link #ux} and
         * {@link #vy} to determine how they are mapped). */
        private void apply(double x, double y, double z) {
            double realu = ux ? x : z;
            double realv = vy ? y : z;
            if (invertU) {
                realu = 1 - realu;
            }
            if (invertV) {
                realv = 1 - realv;
            }
            vertex.texf(sprite.getU((float) realu), sprite.getV((float) realv));

        }
    }

    public static class TankSize {
        public final Vec3 min;
        public final Vec3 max;

        public TankSize(int sx, int sy, int sz, int ex, int ey, int ez) {
            this(new Vec3(sx, sy, sz).scale(1 / 16.0), new Vec3(ex, ey, ez).scale(1 / 16.0));
        }

        public TankSize(Vec3 min, Vec3 max) {
            this.min = min;
            this.max = max;
        }

        public TankSize shrink(double by) {
            return shrink(by, by, by);
        }

        public TankSize shrink(double x, double y, double z) {
            return new TankSize(min.add(x, y, z), max.subtract(x, y, z));
        }

        public TankSize shink(Vec3 by) {
            return shrink(by.x, by.y, by.z);
        }

        public TankSize rotateY() {
            Vec3 _min = rotateY(min);
            Vec3 _max = rotateY(max);
            return new TankSize(VecUtil.min(_min, _max), VecUtil.max(_min, _max));
        }

        private static Vec3 rotateY(Vec3 vec) {
            return new Vec3(//
                1 - vec.z, //
                vec.y, //
                vec.x//
            );
        }
    }
}
