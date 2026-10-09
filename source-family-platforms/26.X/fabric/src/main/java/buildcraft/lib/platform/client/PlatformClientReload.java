package buildcraft.lib.platform.client;

import java.util.Objects;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Orders atlas consumers before the completed-model consumers on every resource reload. */
@Environment(EnvType.CLIENT)
public final class PlatformClientReload {
    private PlatformClientReload() {}

    public static void register(Identifier id, Consumer<ClientAtlas.After> atlas,
            Consumer<ClientModelBaking.Completed> models) {
        Objects.requireNonNull(id, "Client reload listener ID");
        Objects.requireNonNull(atlas, "Atlas reload handler");
        Objects.requireNonNull(models, "Completed model handler");
        Identifier atlasId = id.withSuffix("/atlas");
        Identifier modelsId = id.withSuffix("/models");
        ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        loader.registerReloadListener(atlasId, (ResourceManagerReloadListener) resources -> {
            try {
                Minecraft.getInstance().getAtlasManager().forEach((location, textureAtlas) ->
                    atlas.accept(PlatformClientRegistration.atlas(textureAtlas)));
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Failed to reload BuildCraft atlas listener " + id, exception);
            }
        });
        loader.registerReloadListener(modelsId, (ResourceManagerReloadListener) resources -> {
            try {
                models.accept(PlatformClientModels.completed(Minecraft.getInstance().getModelManager()));
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Failed to reload BuildCraft model listener " + id, exception);
            }
        });
        loader.addListenerOrdering(ResourceReloaderKeys.Client.ATLAS, atlasId);
        loader.addListenerOrdering(atlasId, ResourceReloaderKeys.Client.MODELS);
        loader.addListenerOrdering(ResourceReloaderKeys.Client.MODELS, modelsId);
    }
}
