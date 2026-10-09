package buildcraft.lib.compat.minecraft.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import com.mojang.blaze3d.vertex.PoseStack;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.model.MutableVertex;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Deferred geometry preserves the colours and normals absent from vanilla's immutable baked quad format. */
public final class NativeItemModelBuilder {
    private NativeItemModelBuilder() {}

    public static ItemModel layer(List<MutableQuad> mutable, ItemTransforms transforms, RenderType renderType,
        boolean usesBlockLight) {
        Objects.requireNonNull(mutable, "Item geometry");
        Objects.requireNonNull(transforms, "Item transforms");
        Objects.requireNonNull(renderType, "Item render type");
        List<MutableQuad> captured = new ArrayList<>(mutable.size());
        for (MutableQuad quad : mutable) {
            if (quad == null || quad.getSprite() == null) {
                throw new IllegalArgumentException("Item geometry contains a quad without an atlas sprite");
            }
            captured.add(new MutableQuad(quad));
        }
        return new GeometryItemModel(List.copyOf(captured), transforms, renderType, usesBlockLight);
    }

    private static final class GeometryItemModel implements ItemModel, SpecialModelRenderer<Void> {
        private final List<MutableQuad> quads;
        private final ItemTransforms transforms;
        private final RenderType renderType;
        private final boolean usesBlockLight;
        private final Vector3fc[] extents;

        private GeometryItemModel(List<MutableQuad> quads, ItemTransforms transforms, RenderType renderType,
            boolean usesBlockLight) {
            this.quads = quads;
            this.transforms = transforms;
            this.renderType = renderType;
            this.usesBlockLight = usesBlockLight;
            List<Vector3fc> positions = new ArrayList<>(quads.size() * 4);
            for (MutableQuad quad : quads) {
                for (MutableVertex vertex : quad.vertexs) {
                    positions.add(new Vector3f(vertex.position_x, vertex.position_y, vertex.position_z));
                }
            }
            extents = positions.toArray(Vector3fc[]::new);
        }

        @Override
        public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
            Objects.requireNonNull(renderState, "Item render state").appendModelIdentityElement(this);
            if (quads.isEmpty()) return;
            ItemStackRenderState.LayerRenderState layer = renderState.newLayer();
            layer.setUsesBlockLight(usesBlockLight);
            layer.setItemTransform(transforms.getTransform(displayContext));
            layer.setParticleMaterial(new Material.Baked(quads.getFirst().getSprite(),
                renderType.hasBlending()));
            layer.setExtents(() -> extents.clone());
            layer.setupSpecialModel(this, null);
            if (stack.hasFoil()) layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        }

        @Override
        public void submit(Void argument, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
            boolean foil, int outlineColor) {
            Objects.requireNonNull(pose, "Item pose");
            Objects.requireNonNull(collector, "Item submit collector");
            List<MutableQuad> submitted = new ArrayList<>(quads.size());
            for (MutableQuad original : quads) {
                MutableQuad captured = new MutableQuad(original);
                for (MutableVertex vertex : captured.vertexs) {
                    int emissiveBlock = vertex.light_block;
                    vertex.lighti(light);
                    vertex.light_block = (short) Math.max(vertex.light_block, emissiveBlock);
                    vertex.overlay(overlay);
                }
                submitted.add(captured);
            }
            collector.submitCustomGeometry(pose, renderType, (capturedPose, vertices) -> {
                for (MutableQuad quad : submitted) {
                    quad.render(capturedPose.pose(), capturedPose.normal(), vertices);
                }
            });
            if (foil) {
                RenderType glint = renderType.hasBlending()
                    ? net.minecraft.client.renderer.rendertype.RenderTypes.itemTranslucentGlint(
                        net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                    : net.minecraft.client.renderer.rendertype.RenderTypes.itemCutoutGlint(
                        net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
                collector.submitCustomGeometry(pose, glint, (capturedPose, vertices) -> {
                    for (MutableQuad quad : submitted) {
                        quad.render(capturedPose.pose(), capturedPose.normal(), vertices);
                    }
                });
            }
        }

        @Override
        public void getExtents(Consumer<Vector3fc> consumer) {
            Objects.requireNonNull(consumer, "Item extent consumer");
            for (Vector3fc extent : extents) consumer.accept(new Vector3f(extent));
        }

        @Override
        public Void extractArgument(ItemStack stack) {
            Objects.requireNonNull(stack, "Item stack");
            return null;
        }
    }
}
