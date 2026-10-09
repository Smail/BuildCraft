package buildcraft.lib.platform.client;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.geometry.QuadCollection;

/** Each resource reload binds fresh Fabric model keys to the stable BuildCraft descriptors. */
@Environment(EnvType.CLIENT)
public final class PlatformClientModels {
    private static final CopyOnWriteArrayList<Consumer<ClientModelBaking.Models>> REPLACEMENTS = new CopyOnWriteArrayList<>();

    private PlatformClientModels() {}

    public static ClientModelBaking.Additional additional(ModelLoadingPlugin.Context context) {
        Objects.requireNonNull(context, "Model loading context");
        return model -> {
            Objects.requireNonNull(model, "Standalone model");
            ExtraModelKey<QuadCollection> key = ExtraModelKey.create(
                () -> "BuildCraft static model " + model.location());
            context.addModel(key, new SimpleUnbakedExtraModel<>(model.location(), (resolved, baker) ->
                resolved.bakeTopGeometry(resolved.getTopTextureSlots(), baker, BlockModelRotation.IDENTITY)));
            model.bind(manager -> ((FabricModelManager) Objects.requireNonNull(manager, "Model manager")).getModel(key));
        };
    }

    public static ClientModelBaking.Completed completed(ModelManager manager) {
        return new ClientModelBaking.Completed(Objects.requireNonNull(manager, "Reloaded model manager"));
    }

    public static void registerReplacements(Consumer<ClientModelBaking.Models> handler) {
        REPLACEMENTS.add(Objects.requireNonNull(handler, "Model replacement handler"));
    }

    /** Called before vanilla publishes the bake result; these are the actual mutable result maps. */
    public static void replaceModels(ModelBakery.BakingResult result) {
        Objects.requireNonNull(result, "Model bake result");
        ClientModelBaking.Models models = new ClientModelBaking.Models(result.blockStateModels(), result.itemStackModels());
        for (Consumer<ClientModelBaking.Models> handler : REPLACEMENTS) {
            try {
                handler.accept(models);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Failed to replace BuildCraft models in the current reload", exception);
            }
        }
    }
}
