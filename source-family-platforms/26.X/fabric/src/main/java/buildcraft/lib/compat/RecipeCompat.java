//? source if >=26.3
package buildcraft.lib.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/** Recipe lookups that NeoForge exposes through a widened recipe map, rebuilt on top of vanilla's public API. */
public final class RecipeCompat {
    private RecipeCompat() {}

    /** All loaded recipes of exactly {@code type}, or an empty list if the server has no recipe manager yet. */
    @SuppressWarnings("unchecked")
    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> byType(ServerLevel level,
        RecipeType<T> type) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(type, "type");
        RecipeManager manager = level.getServer() == null ? null : level.getServer().getRecipeManager();
        if (manager == null) {
            return List.of();
        }
        Collection<RecipeHolder<?>> all = manager.getRecipes();
        List<RecipeHolder<T>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : all) {
            if (holder != null && holder.value().getType() == type) {
                result.add((RecipeHolder<T>) holder);
            }
        }
        return result;
    }
}
