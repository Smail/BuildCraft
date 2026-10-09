package buildcraft;

import buildcraft.lib.internal.module.BCModules;
import buildcraft.lib.internal.module.FabricModule;
import buildcraft.lib.platform.registry.RegistryBinding;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One common entrypoint controls catalog construction, native binding, setup, and API freeze. */
public final class BuildCraftFabric implements ModInitializer {
    public static final String MODULE_ENTRYPOINT = "buildcraft:module";
    private enum State { NEW, CONSTRUCTING, REGISTERING, SETUP, READY, COMPLETING, COMPLETE, FAILED }
    private record Hook(String module, Runnable action) {}
    private static final Logger LOGGER = LoggerFactory.getLogger("buildcraft");
    private static State state = State.NEW;
    private static RegistryBinding registries;
    private static String constructingModule;
    private static final List<Hook> SETUP = new ArrayList<>();
    private static final List<Hook> COMPLETE = new ArrayList<>();

    public static synchronized RegistryBinding registries() {
        requireConstruction();
        return registries;
    }

    public static synchronized void afterRegistries(Runnable action) {
        requireConstruction();
        SETUP.add(new Hook(constructingModule, Objects.requireNonNull(action, "action")));
    }

    public static synchronized void afterAllMods(Runnable action) {
        requireConstruction();
        COMPLETE.add(new Hook(constructingModule, Objects.requireNonNull(action, "action")));
    }

    private static void requireConstruction() {
        if (state != State.CONSTRUCTING) throw new IllegalStateException("Module construction hook requested during " + state);
    }

    public static synchronized String activeModuleId() {
        requireConstruction();
        return Objects.requireNonNull(constructingModule, "active module");
    }

    @Override
    public synchronized void onInitialize() {
        synchronized (BuildCraftFabric.class) {
            if (state != State.NEW) throw new IllegalStateException("Fabric BuildCraft initialized twice: " + state);
            try {
                EnumMap<BCModules, FabricModule> modules = discoverModules();
                List<Runnable> storageRegistrations = FabricLoader.getInstance()
                    .getEntrypoints("buildcraft:storage_registration", Runnable.class);
                if (storageRegistrations.size() != 1) {
                    throw new IllegalStateException("Expected one Fabric storage/content binding, found " + storageRegistrations.size());
                }
                Runnable storageRegistration = Objects.requireNonNull(storageRegistrations.getFirst(), "storage registration");
                registries = RegistryBinding.on();
                state = State.CONSTRUCTING;
                for (BCModules module : BCModules.VALUES) {
                    FabricModule initializer = modules.get(module);
                    if (initializer == null) continue;
                    constructingModule = module.getModId();
                    try {
                        initializer.onInitialize();
                    } catch (RuntimeException | LinkageError cause) {
                        throw new IllegalStateException("Failed to construct Fabric module " + constructingModule, cause);
                    }
                }
                constructingModule = null;
                state = State.REGISTERING;
                registries.registerAll();
                try {
                    storageRegistration.run();
                } catch (RuntimeException | LinkageError cause) {
                    throw new IllegalStateException("Failed to install Fabric storage/content bindings", cause);
                }
                logCreativeTabs();
                state = State.SETUP;
                runHooks(SETUP, "common setup");
                state = State.READY;
                buildcraft.lib.tile.BlockEntityLifecycle.registerCommon();
                buildcraft.core.debug.BCDebugCommandRegistration.register();
                ServerLifecycleEvents.SERVER_STARTING.register(server -> completeInitialization());
            } catch (RuntimeException | Error cause) {
                state = State.FAILED;
                throw cause;
            }
        }
    }

    /**
     * Debug aid: reports which BuildCraft creative tabs the native registry holds and where they sit. Fabric API
     * assigns row, column and page lazily (when tab contents are first built), so call this again from the
     * creative screen to see the final layout.
     */
    public static void logCreativeTabs() {
        try {
            List<String> ours = new ArrayList<>();
            for (var key : BuiltInRegistries.CREATIVE_MODE_TAB.keySet()) {
                if (key.getNamespace().equals("buildcraft")) ours.add(key.toString());
            }
            LOGGER.info("Creative tab registry holds {} tabs, {} from BuildCraft: {}",
                BuiltInRegistries.CREATIVE_MODE_TAB.size(), ours.size(), ours);
            for (var key : BuiltInRegistries.CREATIVE_MODE_TAB.keySet()) {
                var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(key);
                if (tab == null) {
                    LOGGER.error("Creative tab {} is registered but its value is null", key);
                    continue;
                }
                String page;
                try {
                    page = tab instanceof net.fabricmc.fabric.impl.creativetab.FabricCreativeModeTabImpl impl
                        ? Integer.toString(impl.fabric_getPage()) : "not-a-fabric-tab";
                } catch (IllegalStateException unassigned) {
                    page = "unassigned";
                }
                LOGGER.info("Creative tab {}: type={} row={} column={} page={} alignedRight={} shouldDisplay={}",
                    key, tab.getType(), tab.row(), tab.column(), page, tab.isAlignedRight(), tab.shouldDisplay());
            }
        } catch (RuntimeException | LinkageError cause) {
            LOGGER.warn("Could not report creative tab registry", cause);
        }
    }

    private static EnumMap<BCModules, FabricModule> discoverModules() {
        EnumMap<BCModules, FabricModule> modules = new EnumMap<>(BCModules.class);
        for (FabricModule initializer : FabricLoader.getInstance().getEntrypoints(MODULE_ENTRYPOINT, FabricModule.class)) {
            BCModules module = BCModules.getBcMod(initializer.getModId());
            if (module == null) throw new IllegalArgumentException("Unknown BuildCraft module entrypoint: " + initializer.getModId());
            if (modules.putIfAbsent(module, initializer) != null) throw new IllegalStateException("Duplicate Fabric module entrypoint: " + initializer.getModId());
        }
        for (BCModules module : BCModules.VALUES) {
            if (module == BCModules.COMPAT && !FabricLoader.getInstance().isModLoaded(module.getModId())) continue;
            if (!modules.containsKey(module)) throw new IllegalStateException("Missing Fabric module implementation: " + module.getModId());
            if (!FabricLoader.getInstance().isModLoaded(module.getModId())) {
                throw new IllegalStateException("Fabric metadata does not provide module ID: " + module.getModId());
            }
        }
        return modules;
    }

    /** Agent 4 calls this from CLIENT_STARTED, after every mod's client initializer has run. */
    public static synchronized void completeInitialization() {
        if (state == State.COMPLETE) return;
        // Fabric assigns tab rows, columns and pages after mod init, so report the final layout here.
        logCreativeTabs();
        if (state != State.READY) throw new IllegalStateException("Fabric load completion requested during " + state);
        state = State.COMPLETING;
        try {
            runHooks(COMPLETE, "load completion");
            state = State.COMPLETE;
        } catch (RuntimeException | Error cause) {
            state = State.FAILED;
            throw cause;
        }
    }

    private static void runHooks(List<Hook> hooks, String phase) {
        for (Hook hook : hooks) {
            try {
                hook.action.run();
            } catch (RuntimeException | LinkageError cause) {
                throw new IllegalStateException("Failed Fabric " + phase + " for " + hook.module, cause);
            }
        }
        hooks.clear();
    }
}
