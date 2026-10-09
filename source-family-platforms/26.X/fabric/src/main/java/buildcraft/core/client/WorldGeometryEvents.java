package buildcraft.core.client;

import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import buildcraft.lib.client.render.compat.BCWorldGeometry;
import buildcraft.lib.client.render.compat.CapturedBlockEntityRenderer.Layer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Records BuildCraft world geometry before Fabric draws the collected submit nodes. */
@Environment(EnvType.CLIENT)
public final class WorldGeometryEvents {
    @FunctionalInterface
    public interface Renderer {
        void render(PoseStack pose, Matrix4f modelView);
    }

    private WorldGeometryEvents() {}

    public static void register(Renderer renderer) {
        Objects.requireNonNull(renderer, "World geometry renderer");
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            PoseStack pose = Objects.requireNonNull(context.poseStack(), "World geometry pose stack");
            // The lib renderers write into the active BCWorldGeometry scope, so record first and submit the layers.
            List<Layer> layers = BCWorldGeometry.capture(() -> renderer.render(pose, new Matrix4f(pose.last().pose())));
            if (!layers.isEmpty()) {
                BCWorldGeometry.submit(layers, new PoseStack(), context.submitNodeCollector());
            }
        });
    }
}
