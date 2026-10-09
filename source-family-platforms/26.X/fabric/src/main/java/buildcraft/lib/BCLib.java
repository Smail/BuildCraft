/* Copyright (c) 2016-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.lib;

import buildcraft.BuildCraftFabric;
import buildcraft.lib.block.VanillaRotationHandlers;
import buildcraft.lib.expression.ExpressionDebugManager;
import buildcraft.lib.internal.api.v2.platform.PlatformApi2Bootstrap;
import buildcraft.lib.internal.debug.BCLog;
import buildcraft.lib.internal.mj.MjApi2PlatformBridge;
import buildcraft.lib.internal.module.BCModules;
import buildcraft.lib.internal.module.FabricModule;
import buildcraft.lib.internal.statement.StatementManager;
import buildcraft.lib.list.VanillaListHandlers;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.misc.ExpressionCompat;
import buildcraft.lib.misc.ItemStackUtil;
import buildcraft.lib.net.BCNetworkSide;
import buildcraft.lib.net.BuildCraftTarget;
import buildcraft.lib.net.MessageContainer;
import buildcraft.lib.net.MessageDebugRequest;
import buildcraft.lib.net.MessageDebugResponse;
import buildcraft.lib.net.MessageGuideRecipeDisplays;
import buildcraft.lib.net.MessageGuideState;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.net.MessageMarker;
import buildcraft.lib.net.MessageUpdateTile;
import buildcraft.lib.net.cache.BuildCraftObjectCaches;
import buildcraft.lib.net.cache.MessageObjectCacheRequest;
import buildcraft.lib.net.cache.MessageObjectCacheResponse;
import buildcraft.lib.recipe.BCLibIngredientTypes;
import buildcraft.lib.platform.chunk.PlatformChunkTickets;
import net.fabricmc.loader.api.FabricLoader;

public final class BCLib implements FabricModule {
    public static final String MODID = "buildcraftlib";
    public static final String VERSION = BuildCraftTarget.MOD_VERSION;
    public static final String MC_VERSION = BuildCraftTarget.MINECRAFT_VERSION;
    public static final String GIT_BRANCH = BuildCraftTarget.GIT_BRANCH;
    public static final String GIT_COMMIT_HASH = BuildCraftTarget.GIT_COMMIT_HASH;
    public static final String GIT_COMMIT_MSG = BuildCraftTarget.GIT_COMMIT_MESSAGE;
    public static final String GIT_COMMIT_AUTHOR = BuildCraftTarget.GIT_COMMIT_AUTHOR;
    public static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("buildcraft.dev");

    @Override
    public String getModId() { return MODID; }

    @Override
    public void onInitialize() {
        BCLog.logger.info("Starting BuildCraft {} on Fabric", VERSION);
        BCLibRegistries.fmlPreInit();
        MjApi2PlatformBridge.install();
        PlatformApi2Bootstrap.install();
        BCLibItems.registry(BuildCraftFabric.registries());
        BCLibIngredientTypes.register();
        StatementManager.setRegistryProvider(ItemStackUtil::requireActiveRegistryProvider);
        registerMessages();
        MessageManager.registerPayloads();
        PlatformChunkTickets.installValidation(buildcraft.lib.platform.chunk.BCChunkTickets::validateTickets);
        PlatformChunkTickets.init();
        ExpressionDebugManager.logger = BCLog.logger::info;
        ExpressionCompat.setup();
        BuildCraftObjectCaches.fmlPreInit();
        BCLibEventDist.registerGameplayEvents();
        BuildCraftFabric.afterRegistries(() -> {
            BCLibRegistries.fmlInit();
            VanillaListHandlers.fmlInit();
            VanillaRotationHandlers.fmlInit();
        });
        BuildCraftFabric.afterAllMods(() -> {
            MarkerCache.postInit();
            BuildCraftObjectCaches.fmlPostInit();
            MessageManager.fmlPostInit();
            BCLibRegistries.fmlPostInit();
        });
    }

    private static void registerMessages() {
        MessageManager.registerMessageClass(BCModules.LIB, MessageUpdateTile.class,
            MessageUpdateTile.HANDLER, MessageUpdateTile::toBytes, MessageUpdateTile::new);
        MessageManager.registerMessageClass(BCModules.LIB, MessageContainer.class,
            MessageContainer.HANDLER, MessageContainer::toBytes, MessageContainer::new);
        MessageManager.registerClientboundMessageClass(BCModules.LIB, MessageMarker.class,
            MessageMarker.HANDLER, MessageMarker::toBytes, MessageMarker::new);
        MessageManager.registerMessageClass(BCModules.LIB, MessageObjectCacheRequest.class,
            MessageObjectCacheRequest.HANDLER, MessageObjectCacheRequest::toBytes, MessageObjectCacheRequest::new, BCNetworkSide.SERVER);
        MessageManager.registerClientboundMessageClass(BCModules.LIB, MessageObjectCacheResponse.class,
            MessageObjectCacheResponse.HANDLER, MessageObjectCacheResponse::toBytes, MessageObjectCacheResponse::new);
        MessageManager.registerMessageClass(BCModules.LIB, MessageDebugRequest.class,
            MessageDebugRequest.HANDLER, MessageDebugRequest::toBytes, MessageDebugRequest::new, BCNetworkSide.SERVER);
        MessageManager.registerClientboundMessageClass(BCModules.LIB, MessageDebugResponse.class,
            MessageDebugResponse.HANDLER, MessageDebugResponse::toBytes, MessageDebugResponse::new);
        MessageManager.registerMessageClass(BCModules.LIB, MessageGuideState.class,
            MessageGuideState.HANDLER, MessageGuideState::toBytes, MessageGuideState::new, BCNetworkSide.SERVER);
        MessageManager.registerClientboundMessageClass(BCModules.LIB, MessageGuideRecipeDisplays.class,
            MessageGuideRecipeDisplays.HANDLER, MessageGuideRecipeDisplays::toBytes, MessageGuideRecipeDisplays::new);
    }

    public static Error throwBadClass(Error cause, Class<?> type) {
        throw new Error("Bad " + type + " loaded from " + type.getClassLoader() + " domain: " + type.getProtectionDomain(), cause);
    }
}
