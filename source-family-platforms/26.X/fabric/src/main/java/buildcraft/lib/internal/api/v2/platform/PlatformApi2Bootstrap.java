package buildcraft.lib.internal.api.v2.platform;

import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.platform.PlatformServices;
import buildcraft.lib.internal.api.v2.BuildCraftApiRuntime;
import java.util.List;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;

/** Agent 3 supplies the native transfer service through the Fabric entrypoint. */
public final class PlatformApi2Bootstrap {
    public static final String ENTRYPOINT = "buildcraft:platform_services";
    private static PlatformServices installed;

    private PlatformApi2Bootstrap() {}

    public static synchronized void install() {
        if (installed != null) return;
        List<PlatformServices> providers = FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, PlatformServices.class);
        if (providers.size() != 1) {
            throw new IllegalStateException("Expected exactly one Fabric BuildCraft platform service provider, found " + providers.size());
        }
        install(providers.getFirst());
    }

    public static synchronized void install(PlatformServices services) {
        Objects.requireNonNull(services, "services");
        if (installed != null) {
            if (installed != services) throw new IllegalStateException("Fabric platform services already installed by another provider");
            return;
        }
        var runtime = BuildCraftApiRuntime.INSTANCE;
        var existing = runtime.service(BuildCraftServices.PLATFORM);
        if (existing.isPresent() && existing.get() != services) {
            throw new IllegalStateException("API v2 platform service was installed before Fabric startup by another provider");
        }
        if (existing.isEmpty()) runtime.installService(BuildCraftServices.PLATFORM, services);
        installed = services;
    }
}
