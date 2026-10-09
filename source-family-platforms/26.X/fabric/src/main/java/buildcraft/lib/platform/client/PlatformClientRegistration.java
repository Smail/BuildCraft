package buildcraft.lib.platform.client;

import java.util.List;
import java.util.Objects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fabric client initialization is synchronous; reload views always use the current atlas. */
@Environment(EnvType.CLIENT)
public final class PlatformClientRegistration {
    private PlatformClientRegistration() {}

    public static ClientRegistration.Renderers renderers() {
        return new ClientRegistration.Renderers() {
            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(
                    BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> factory) {
                BlockEntityRenderers.register(Objects.requireNonNull(type, "Block entity type"),
                    Objects.requireNonNull(factory, "Block entity renderer factory"));
            }

            @Override
            public <T extends Entity> void registerEntityRenderer(
                    EntityType<? extends T> type, EntityRendererProvider<T> factory) {
                EntityRenderers.register(Objects.requireNonNull(type, "Entity type"),
                    Objects.requireNonNull(factory, "Entity renderer factory"));
            }
        };
    }

    public static ClientRegistration.Screens screens() {
        return new ClientRegistration.Screens() {
            @Override
            public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void register(
                    MenuType<? extends M> type, ClientRegistration.ScreenFactory<M, S> factory) {
                Objects.requireNonNull(type, "Menu type");
                Objects.requireNonNull(factory, "Screen factory");
                MenuScreens.<M, S>register(type, (menu, inventory, title) -> Objects.requireNonNull(
                    factory.create(menu, inventory, title), "Screen factory returned null for " + type));
            }
        };
    }

    public static ClientRegistration.BlockColours blockColours() {
        return (colour, blocks) -> {
            Objects.requireNonNull(blocks, "Tinted blocks");
            for (var block : blocks) Objects.requireNonNull(block, "Tinted block");
            BlockColorRegistry.register(List.of(Objects.requireNonNull(colour, "Block tint source")), blocks);
        };
    }

    public static ClientModelProperties.Ranges rangeProperties() {
        return (id, codec) -> RangeSelectItemModelProperties.ID_MAPPER.put(
            Objects.requireNonNull(id, "Range property ID"), Objects.requireNonNull(codec, "Range property codec"));
    }

    public static ClientModelProperties.Tints tintSources() {
        return (id, codec) -> ItemTintSources.ID_MAPPER.put(
            Objects.requireNonNull(id, "Tint source ID"), Objects.requireNonNull(codec, "Tint source codec"));
    }

    public static ClientAtlas.After atlas(TextureAtlas atlas) {
        return new ClientAtlas.After(Objects.requireNonNull(atlas, "Reloaded atlas"));
    }
}
