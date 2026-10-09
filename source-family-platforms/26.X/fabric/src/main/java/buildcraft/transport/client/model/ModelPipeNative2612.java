package buildcraft.transport.client.model;

import java.util.List;
import java.util.function.Predicate;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.model.MutableVertex;
import buildcraft.lib.misc.SpriteUtil;
import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.client.model.key.PipeModelKey;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable Fabric terrain meshes retain pipe depth, colours and translucent facades. */
@Environment(EnvType.CLIENT)
public final class ModelPipeNative2612 implements BlockStateModel, FabricBlockStateModel {
    public static final ModelPipeNative2612 INSTANCE = new ModelPipeNative2612();
    public static final int PIPE_TINT_MARKER = 0x5A000000;
    private static volatile long generation;
    private ModelPipeNative2612() {}
    public static boolean isPipeTint(int index) { return (index & 0xFF000000) == PIPE_TINT_MARKER; }
    public static int pipeTintColour(int index) { return 0xFF000000 | (index & 0xFFFFFF); }
    public static long generation() { return generation; }
    public static void clearTextureCache() { generation++; }

    public static PipeRenderData buildModelData(TilePipeHolder tile) {
        Pipe pipe = tile.getPipe();
        if (pipe == null || pipe == Pipe.EMPTY) return null;
        PipeModelKey key = pipe.getModel();
        if (key == null || key.definition == null) return null;
        var cutoutPlug = new PipeModelCachePluggable.PluggableKey(buildcraft.lib.compat.RenderCompat.cutout(), tile);
        var translucentPlug = new PipeModelCachePluggable.PluggableKey(buildcraft.lib.compat.RenderCompat.translucent(), tile);
        MutableMesh mesh = Renderer.get().mutableMesh();
        TextureAtlasSprite[] particle = new TextureAtlasSprite[1];
        synchronized (PipeModelCacheBase.class) {
            append(mesh.emitter(), PipeModelCacheBase.cacheCutout.bake(new PipeModelCacheBase.PipeBaseCutoutKey(key)), false, true, particle);
            append(mesh.emitter(), PipeModelCacheBase.cacheTranslucent.bake(new PipeModelCacheBase.PipeBaseTranslucentKey(key)), true, true, particle);
            append(mesh.emitter(), PipeModelCachePluggable.cacheCutoutAll.bake(cutoutPlug), false, false, particle);
            append(mesh.emitter(), PipeModelCachePluggable.cacheTranslucentAll.bake(translucentPlug), true, false, particle);
        }
        return new PipeRenderData(new GeometryKey(key, cutoutPlug, translucentPlug, generation), mesh.immutableCopy(),
            particle[0] == null ? fallbackParticle() : particle[0]);
    }

    private static void append(QuadEmitter emitter,
        List<buildcraft.lib.compat.mc2612.client.renderer.block.model.BakedQuad> source,
        boolean translucent, boolean body, TextureAtlasSprite[] particle) {
        for (var baked : source) {
            if (baked == null || baked.getVertices().length < 32) continue;
            MutableQuad quad = new MutableQuad(baked);
            if (quad.getSprite() == null) continue;
            if (particle[0] == null) particle[0] = quad.getSprite();
            emitter.clear().atlas(QuadAtlas.BLOCK).chunkLayer(translucent ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.CUTOUT)
                .cullFace(null).nominalFace(quad.getFace()).ambientOcclusion(TriState.TRUE).tintIndex(body ? -1 : quad.getTint());
            for (int i = 0; i < 4; i++) {
                MutableVertex vertex = quad.vertexs[i];
                emitter.pos(i, vertex.position_x, vertex.position_y, vertex.position_z)
                    .uv(i, vertex.tex_u, vertex.tex_v).normal(i, vertex.normal_x, vertex.normal_y, vertex.normal_z)
                    .color(i, colour(vertex, quad, translucent, body))
                    .lightmap(i, (Math.min(15, vertex.light_block & 0xFFFF) << 4)
                        | (Math.min(15, vertex.light_sky & 0xFFFF) << 20));
            }
            emitter.emit();
        }
    }

    private static int colour(MutableVertex vertex, MutableQuad quad, boolean translucent, boolean body) {
        float divisor = 1;
        if (body && !translucent && quad.getSprite() != BCTransportSprites.PIPE_COLOUR_BORDER_OUTER.getSprite()
            && quad.getSprite() != BCTransportSprites.PIPE_COLOUR_BORDER_INNER.getSprite()) {
            divisor = MutableQuad.diffuseLight(vertex.normal_x, vertex.normal_y, vertex.normal_z);
            Direction nominal = quad.getFace();
            if (nominal != null && vertex.normal_x * nominal.getStepX() + vertex.normal_y * nominal.getStepY()
                + vertex.normal_z * nominal.getStepZ() < -0.5F) divisor *= 0.75F;
            if (!Float.isFinite(divisor) || divisor < 0.01F) divisor = 1;
        }
        return ((body ? 255 : vertex.colour_a & 255) << 24) | (clamp(Math.round(vertex.colour_r / divisor)) << 16)
            | (clamp(Math.round(vertex.colour_g / divisor)) << 8) | clamp(Math.round(vertex.colour_b / divisor));
    }
    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private static PipeRenderData data(BlockAndTintGetter level, BlockPos position) {
        Object value = ((FabricBlockGetter) level).getBlockEntityRenderData(position);
        return value instanceof PipeRenderData data ? data : null;
    }
    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
        RandomSource random, Predicate<Direction> cullTest) {
        PipeRenderData data = data(level, pos);
        if (data != null) data.mesh().outputTo(emitter);
    }
    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        PipeRenderData data = data(level, pos);
        return data == null ? PipeModelKey.DEFAULT_KEY : data.key();
    }
    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {}
    @Override
    public Material.Baked particleMaterial() { return new Material.Baked(fallbackParticle(), false); }
    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos position, BlockState state) {
        PipeRenderData data = data(level, position);
        return new Material.Baked(data == null ? fallbackParticle() : data.particle(), false);
    }
    @Override
    public int materialFlags() { return 3; }
    private static TextureAtlasSprite fallbackParticle() {
        TextureAtlasSprite sprite = SpriteUtil.missingSprite();
        if (sprite == null) throw new IllegalStateException("Pipe model has no fallback particle sprite");
        return sprite;
    }
    public record GeometryKey(PipeModelKey pipe, PipeModelCachePluggable.PluggableKey cutout,
        PipeModelCachePluggable.PluggableKey translucent, long generation) {}
    public record PipeRenderData(GeometryKey key, Mesh mesh, TextureAtlasSprite particle) {}
}
