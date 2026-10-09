package buildcraft.lib.internal.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Fabric metadata provides the same module IDs as the other loaders. */
public enum BCModules implements IBuildCraftMod {
    LIB, CORE, BUILDERS, ENERGY, FACTORY, ROBOTICS, SILICON, TRANSPORT, COMPAT;

    public static final BCModules[] VALUES = values();
    public final String lowerCaseName = name().toLowerCase(Locale.ROOT);
    public final String camelCaseName = name().charAt(0) + lowerCaseName.substring(1);
    private final String modId = "buildcraft" + lowerCaseName;
    private static volatile boolean checked;
    private static BCModules[] loadedModules;
    private static BCModules[] missingModules;
    private boolean loaded;

    private static synchronized void checkLoadStatus() {
        if (checked) return;
        FabricLoader loader = FabricLoader.getInstance();
        List<BCModules> found = new ArrayList<>();
        List<BCModules> missing = new ArrayList<>();
        for (BCModules module : VALUES) {
            module.loaded = loader.isModLoaded(module.modId);
            (module.loaded ? found : missing).add(module);
        }
        loadedModules = found.toArray(BCModules[]::new);
        missingModules = missing.toArray(BCModules[]::new);
        checked = true;
    }

    @Nullable
    public static BCModules getBcMod(String testModId) {
        for (BCModules module : VALUES) if (module.modId.equals(testModId)) return module;
        return null;
    }

    public static boolean isBcMod(String testModId) {
        return getBcMod(testModId) != null;
    }

    public static BCModules[] getLoadedModules() {
        checkLoadStatus();
        return loadedModules.clone();
    }

    public static BCModules[] getMissingModules() {
        checkLoadStatus();
        return missingModules.clone();
    }

    @Override
    public String getModId() {
        return modId;
    }

    public boolean isLoaded() {
        checkLoadStatus();
        return loaded;
    }

    public Identifier createLocation(String path) {
        return Identifier.fromNamespaceAndPath(modId, path);
    }
}
