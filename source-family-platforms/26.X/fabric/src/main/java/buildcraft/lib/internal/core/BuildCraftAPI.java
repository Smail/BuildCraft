/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.lib.internal.core;

import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

public final class BuildCraftAPI {
    /** Fabric has no active mod container while loading, so unqualified names belong to BuildCraft itself. */
    private static final String DEFAULT_NAMESPACE = "buildcraft";

    /** Deactivate constructor */
    private BuildCraftAPI() {}

    public static String getVersion() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(DEFAULT_NAMESPACE);
        if (container.isPresent()) {
            return container.get().getMetadata().getVersion().getFriendlyString();
        }
        return "UNKNOWN VERSION";
    }

    public static Identifier nameToResourceLocation(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Illegal recipe name '" + name + "'. Provide a non-empty name.");
        }
        if (name.indexOf(':') > 0) return Identifier.parse(name);
        return Identifier.fromNamespaceAndPath(DEFAULT_NAMESPACE, name);
    }
}
