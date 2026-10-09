package buildcraft.lib.client;

import com.mojang.serialization.MapCodec;
import buildcraft.BuildCraftFabric;
import buildcraft.builders.BCBuildersClientGuis;
import buildcraft.builders.BCBuildersClientRenderers;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.BCBuildersSprites;
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreClientRenderers;
import buildcraft.core.BCCoreItems;
import buildcraft.core.BCCoreSprites;
import buildcraft.core.client.MarkerSubmitRenderer121111;
import buildcraft.core.client.RenderTickListener;
import buildcraft.core.client.WorldGeometryEvents;
import buildcraft.core.client.render.RenderVolumeBoxes;
import buildcraft.core.list.GuiList;
import buildcraft.energy.BCEnergyClientRenderers;
import buildcraft.energy.BCEnergyGuis;
import buildcraft.energy.BCEnergySprites;
import buildcraft.energy.client.FabricEnergyClientFluids;
import buildcraft.energy.client.gui.GuiDynamoMJ;
import buildcraft.energy.client.gui.GuiEngineFE;
import buildcraft.energy.client.gui.GuiEngineIron_BC8;
import buildcraft.energy.client.gui.GuiEngineStone_BC8;
import buildcraft.factory.BCFactoryClientGuis;
import buildcraft.factory.BCFactoryClientRenderers;
import buildcraft.factory.BCFactoryModels;
import buildcraft.factory.BCFactorySprites;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.BCLibSprites;
import buildcraft.lib.client.model.ModelHolderRegistry;
import buildcraft.lib.client.model.json.VariablePartLed;
import buildcraft.lib.client.reload.LibConfigChangeListener;
import buildcraft.lib.client.render.DetachedRenderer;
import buildcraft.lib.client.render.DetachedRenderer.RenderMatrixType;
import buildcraft.lib.client.render.MarkerRenderer;
import buildcraft.lib.client.render.fluid.FluidRenderer;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.debug.ClientDebuggables;
import buildcraft.lib.debug.DebugRenderHelper;
import buildcraft.lib.internal.module.BCModules;
import buildcraft.lib.item.ItemDebugger;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.misc.ItemStackUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.SpriteUtil;
import buildcraft.lib.misc.data.ModelVariableData;
import buildcraft.lib.net.BCNetworkSide;
import buildcraft.lib.net.FabricClientNetworking;
import buildcraft.lib.net.GuideRecipeDisplayCache;
import buildcraft.lib.net.MessageDebugRequest;
import buildcraft.lib.net.MessageDebugResponse;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.net.MessageMarker;
import buildcraft.lib.net.MessageMarkerClientHandler;
import buildcraft.lib.net.cache.BuildCraftObjectCaches;
import buildcraft.lib.net.cache.MessageObjectCacheResponse;
import buildcraft.lib.platform.client.ClientAtlas;
import buildcraft.lib.platform.client.ClientModelBaking;
import buildcraft.lib.platform.client.PlatformClientModels;
import buildcraft.lib.platform.client.PlatformClientRegistration;
import buildcraft.lib.platform.client.PlatformClientReload;
import buildcraft.lib.platform.events.BCEvents;
import buildcraft.lib.platform.events.PlatformClientEvents;
import buildcraft.robotics.BCRoboticsClientGuis;
import buildcraft.robotics.BCRoboticsBoards;
import buildcraft.robotics.BCRoboticsClientRenderers;
import buildcraft.robotics.BCRoboticsModels;
import buildcraft.robotics.BCRoboticsSprites;
import buildcraft.silicon.BCSiliconClientGuis;
import buildcraft.silicon.BCSiliconClientRenderers;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconModels;
import buildcraft.silicon.BCSiliconSprites;
import buildcraft.transport.BCTransportClientGuis;
import buildcraft.transport.BCTransportClientRenderers;
import buildcraft.transport.BCTransportModels;
import buildcraft.transport.client.PipeRegistryClient;
import buildcraft.transport.client.model.ModelPipeNative2612;
import buildcraft.transport.client.model.PipeBaseModelGenStandard;
import buildcraft.transport.client.model.PipeModelCacheAll;
import buildcraft.transport.client.render.PipeFlowRendererFE;
import buildcraft.transport.client.render.PipeFlowRendererPower;
import buildcraft.transport.internal.pipe.PipeApiClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/** Client entrypoint; Fabric runs it after the common module and message catalogues are initialized. */
@Environment(EnvType.CLIENT)
public final class BuildCraftFabricClient implements ClientModInitializer {
    private static boolean started;

    @Override
    public synchronized void onInitializeClient() {
        if (started) throw new IllegalStateException("BuildCraft client was initialized twice");
        started = true;
        try {
            initializeLibrary();
            initializeModules();
            ModelLoadingPlugin.register(context -> ModelHolderRegistry.preModelBake(PlatformClientModels.additional(context)));
            PlatformClientReload.register(Identifier.fromNamespaceAndPath("buildcraftlib", "client_reload"),
                BuildCraftFabricClient::reloadAtlas, BuildCraftFabricClient::completeModels);
            PlatformClientEvents.login(BuildCraftFabricClient::login);
            PlatformClientEvents.logout(BuildCraftFabricClient::logout);
            PlatformClientEvents.tick(BCEvents.Phase.END, event -> tick());
            WorldGeometryEvents.register((pose, matrix) -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return;
                LaserRenderer_BC8.setupLaserRenderState();
                DetachedRenderer.INSTANCE.renderWorldLastEvent(pose, matrix, client.player,
                    client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            });
            FabricClientNetworking.register();
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_LOAD
                .register((blockEntity, world) -> buildcraft.lib.tile.BlockEntityLifecycle.load(blockEntity));
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD
                .register((blockEntity, world) -> buildcraft.lib.tile.BlockEntityLifecycle.unload(blockEntity));
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> BuildCraftFabric.completeInitialization());
            // Diagnostics: Fabric paginates modded tabs at the end of CreativeModeTabs.buildAllTabContents (world join),
            // so report the layout a few seconds after the player appears.
            final int[] joinedTicks = {0};
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.player == null) {
                    joinedTicks[0] = 0;
                } else if (++joinedTicks[0] == 100) {
                    BuildCraftFabric.logCreativeTabs();
                    try {
                        buildcraft.lib.internal.debug.BCLog.logger.info(
                            "Creative tabs visible to screen: {} (vanilla+mods)",
                            net.minecraft.world.item.CreativeModeTabs.tabs().size());
                    } catch (RuntimeException | LinkageError error) {
                        buildcraft.lib.internal.debug.BCLog.caught("BuildCraftFabricClient.logTabs", error);
                    }
                }
            });
            // Diagnostics: Fabric lays modded creative tabs out when the creative screen first builds its contents.
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
                if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative) {
                    BuildCraftFabric.logCreativeTabs();
                    buildcraft.lib.internal.debug.BCLog.logger.info("Creative screen opened: pageCount={} tabs={}",
                        ((net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen) creative).getPageCount(),
                        net.minecraft.world.item.CreativeModeTabs.tabs().size());
                }
            });
        } catch (RuntimeException | LinkageError error) {
            throw new IllegalStateException("Failed to initialize BuildCraft Fabric client", error);
        }
    }

    private static void initializeLibrary() {
        DetachedRenderer.INSTANCE.addRenderer(RenderMatrixType.FROM_WORLD_ORIGIN, MarkerRenderer.INSTANCE);
        DetachedRenderer.INSTANCE.addRenderer(RenderMatrixType.FROM_WORLD_ORIGIN, DebugRenderHelper.INSTANCE);
        BCLibSprites.fmlPreInitClient();
        SpriteHolderRegistry.bootstrapBuiltinHolders();
        BCLibConfig.configChangeListeners.add(LibConfigChangeListener.INSTANCE);
        MessageManager.setHandler(MessageMarker.class, MessageMarker.HANDLER, BCNetworkSide.CLIENT);
        MessageManager.setHandler(MessageObjectCacheResponse.class, MessageObjectCacheResponse.HANDLER, BCNetworkSide.CLIENT);
        MessageManager.setHandler(MessageDebugResponse.class, MessageDebugResponse.HANDLER, BCNetworkSide.CLIENT);
    }

    private static void initializeModules() {
        var screens = PlatformClientRegistration.screens();
        var renderers = PlatformClientRegistration.renderers();
        var ranges = PlatformClientRegistration.rangeProperties();
        if (BCModules.TRANSPORT.isLoaded()) {
            PipeApiClient.registry = PipeRegistryClient.INSTANCE;
            BCTransportModels.init();
            BCTransportClientGuis.clientInit(screens);
            BCTransportClientRenderers.register(renderers);
            PlatformClientModels.registerReplacements(BCTransportModels::onModelBake);
        }
        if (BCModules.CORE.isLoaded()) {
            BCCoreSprites.init();
            PlatformClientRegistration.tintSources().register(
                buildcraft.core.client.FluidShardTint.ID, buildcraft.core.client.FluidShardTint.CODEC);
            screens.register(BCCore.LIST_MENU.get(), GuiList::new);
            BCCoreClientRenderers.register(renderers);
            BCCoreItems.registerItemModelProperties(ranges);
            DetachedRenderer.INSTANCE.addRenderer(RenderMatrixType.FROM_WORLD_ORIGIN, RenderVolumeBoxes.INSTANCE);
            WorldGeometryEvents.register(RenderTickListener::renderLast);
            WorldGeometryEvents.register(MarkerSubmitRenderer121111::submit);
        }
        if (BCModules.BUILDERS.isLoaded()) {
            BCBuildersSprites.init();
            BCBuildersClientGuis.clientInit(screens);
            BCBuildersClientRenderers.register(renderers);
            BCBuildersItems.registerItemModelProperties(ranges);
        }
        if (BCModules.ENERGY.isLoaded()) {
            BCEnergySprites.init();
            FabricEnergyClientFluids.register();
            screens.register(BCEnergyGuis.MENU_STONE.get(), GuiEngineStone_BC8::new);
            screens.register(BCEnergyGuis.MENU_IRON.get(), GuiEngineIron_BC8::new);
            screens.register(BCEnergyGuis.MENU_FE.get(), GuiEngineFE::new);
            screens.register(BCEnergyGuis.MENU_DYNAMO_MJ.get(), GuiDynamoMJ::new);
            BCEnergyClientRenderers.register(renderers);
        }
        if (BCModules.FACTORY.isLoaded()) {
            BCFactorySprites.init();
            BCFactoryModels.init();
            BCFactoryClientGuis.clientInit(screens);
            BCFactoryClientRenderers.register(renderers);
        }
        if (BCModules.SILICON.isLoaded()) {
            BCSiliconSprites.fmlPreInit();
            BCSiliconModels.fmlPreInit();
            BCSiliconModels.init();
            BCSiliconClientGuis.clientInit(screens);
            BCSiliconClientRenderers.register(renderers);
            BCSiliconItems.registerItemModelProperties(ranges);
            PlatformClientModels.registerReplacements(BCSiliconModels::onModelBake);
            buildcraft.silicon.client.render.SiliconDebugGeometry263.register();
        }
        if (BCModules.ROBOTICS.isLoaded()) {
            BCRoboticsSprites.preInit();
            BCRoboticsModels.init();
            BCRoboticsClientGuis.clientInit(screens);
            BCRoboticsClientRenderers.register(renderers);
            ranges.register(Identifier.fromNamespaceAndPath("buildcraftrobotics", "robot"), RobotModelProperty.CODEC);
            ranges.register(Identifier.fromNamespaceAndPath("buildcraftrobotics", "board"), BoardModelProperty.CODEC);
        }
    }

    private static void reloadAtlas(ClientAtlas.After event) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(event.getAtlas().location())) return;
        ModelHolderRegistry.reloadVariableModels();
        SpriteHolderRegistry.onTextureStitchPost(event);
        SpriteUtil.clearAtlasCache();
        DebugRenderHelper.clearTextureCache();
        LaserRenderer_BC8.clearModels();
        FluidRenderer.onTextureStitchPost(event);
        VariablePartLed.onTextureStitchPost(event);
        ModelVariableData.onModelBake();
        if (BCModules.TRANSPORT.isLoaded()) {
            PipeBaseModelGenStandard.loadSpritesCache(event.getAtlas());
            PipeModelCacheAll.clearModels();
            ModelPipeNative2612.clearTextureCache();
            PipeFlowRendererPower.clearTextureCache();
            PipeFlowRendererFE.clearTextureCache();
        }
        if (BCModules.SILICON.isLoaded()) BCSiliconModels.clearAtlasDependentCaches();
    }

    private static void completeModels(ClientModelBaking.Completed event) {
        SpriteHolderRegistry.exportTextureMap();
        LaserRenderer_BC8.clearModels();
        ModelHolderRegistry.onModelBake(event);
        if (BCModules.TRANSPORT.isLoaded()) BCTransportModels.onModelBakeComplete();
        if (BCModules.ROBOTICS.isLoaded()) BCRoboticsModels.onModelBake(event);
    }

    private static void login() {
        Minecraft client = Minecraft.getInstance();
        ItemStackUtil.setClientRegistryProvider(client.level == null ? null : client.level.registryAccess());
        MarkerCache.clearClientCaches();
        MessageMarkerClientHandler.clearQueuedMessages();
        GuideRecipeDisplayCache.clear();
        BuildCraftObjectCaches.onClientJoinServer();
    }

    private static void logout() {
        ItemStackUtil.setClientRegistryProvider(null);
        MarkerCache.clearClientCaches();
        MessageMarkerClientHandler.clearQueuedMessages();
        GuideRecipeDisplayCache.clear();
    }

    private static void tick() {
        BuildCraftObjectCaches.onClientTick();
        MessageUtil.postClientTick();
        MessageMarkerClientHandler.flushQueuedMessages();
        Minecraft client = Minecraft.getInstance();
        ItemStackUtil.setClientRegistryProvider(client.level == null ? null : client.level.registryAccess());
        if (client.player != null && ItemDebugger.isShowDebugInfo(client.player)
                && client.hitResult instanceof BlockHitResult hit
                && ClientDebuggables.getDebuggableObject(hit) instanceof BlockEntity tile) {
            MessageManager.sendToServer(new MessageDebugRequest(tile.getBlockPos(), hit.getDirection()));
        }
    }

    private record RobotModelProperty() implements RangeSelectItemModelProperty {
        private static final MapCodec<RobotModelProperty> CODEC = MapCodec.unit(new RobotModelProperty());

        @Override
        public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
            return BCRoboticsBoards.getRobotModelValue(java.util.Objects.requireNonNull(stack, "Robot item stack"));
        }

        @Override
        public MapCodec<RobotModelProperty> type() { return CODEC; }
    }

    private record BoardModelProperty() implements RangeSelectItemModelProperty {
        private static final MapCodec<BoardModelProperty> CODEC = MapCodec.unit(new BoardModelProperty());

        @Override
        public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
            return BCRoboticsBoards.getBoardModelValue(java.util.Objects.requireNonNull(stack, "Board item stack"));
        }

        @Override
        public MapCodec<BoardModelProperty> type() { return CODEC; }
    }
}
