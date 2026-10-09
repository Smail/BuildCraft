package buildcraft.lib.client.render.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Runs against the real Minecraft classes; no window or graphics context is needed. */
@Environment(EnvType.CLIENT)
public final class FabricGeometryCaptureProbe {
    private FabricGeometryCaptureProbe() {}

    public static void main(String[] args) {
        var consumer = new CapturedBlockEntityRenderer.RecordingConsumer();
        consumer.addVertex(1, 2, 3).setColor(17, 34, 51, 68).setUv(0.25F, 0.5F)
            .setUv1(7, 8).setUv2(9, 10).setNormal(0, 1, 0).setLineWidth(2).setUv3(0.75F, 0.875F);
        var vertices = consumer.finish();
        if (vertices.size() != 1) {
            throw new AssertionError("Geometry snapshot lost its final pending vertex");
        }
        var vertex = vertices.getFirst();
        if (vertex.uv3U() != 0.75F || vertex.uv3V() != 0.875F || vertex.a() != 68
                || vertex.overlayU() != 7 || vertex.lightV() != 10 || vertex.width() != 2) {
            throw new AssertionError("Geometry snapshot lost tint, lighting, overlay, width, or UV3");
        }
        consumer.addVertex(4, 5, 6).setColor(255, 255, 255, 255);
        var updated = consumer.finish();
        if (vertices.size() != 1 || updated.size() != 2) {
            throw new AssertionError("Deferred geometry retained mutable vertex storage");
        }
        var second = updated.get(1);
        if (second.uv3U() != 0 || second.uv3V() != 0) {
            throw new AssertionError("New vertices inherited UV3 from the previous vertex");
        }
        var replay = new CapturedBlockEntityRenderer.RecordingConsumer();
        vertex.emit(new PoseStack().last(), replay);
        var replayed = replay.finish().getFirst();
        if (!vertex.equals(replayed)) {
            throw new AssertionError("Replaying captured geometry changed its vertex channels");
        }
        try {
            vertices.clear();
            throw new AssertionError("Deferred geometry exposes mutable vertex lists");
        } catch (UnsupportedOperationException expected) {
            System.out.println("Fabric geometry capture probe passed");
        }
    }
}
