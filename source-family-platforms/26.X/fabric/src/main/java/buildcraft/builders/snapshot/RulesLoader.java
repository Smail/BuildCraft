/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.JsonUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;

public class RulesLoader {
    private static final Gson GSON = JsonUtil.registerNbtSerializersDeserializers(new GsonBuilder())
        .registerTypeAdapter(
            BlockPos.class,
            (JsonDeserializer<BlockPos>) (json, typeOfT, context) ->
                new BlockPos(
                    json.getAsJsonArray().get(0).getAsInt(),
                    json.getAsJsonArray().get(1).getAsInt(),
                    json.getAsJsonArray().get(2).getAsInt()
                )
        )
        .registerTypeAdapter(RequiredExtractor.class, RequiredExtractor.DESERIALIZER)
        .registerTypeAdapter(EnumNbtCompareOperation.class, EnumNbtCompareOperation.DESERIALIZER)
        .registerTypeAdapter(NbtPath.class, NbtPath.DESERIALIZER)
        .registerTypeAdapterFactory(JsonSelector.TYPE_ADAPTER_FACTORY)
        .registerTypeAdapterFactory(NbtRef.TYPE_ADAPTER_FACTORY)
        .registerTypeAdapterFactory(NbtRef.NBT_TYPE_ADAPTER_FACTORY)
        .create();

    private static final List<JsonRule> RULES = new ArrayList<>();
    @SuppressWarnings("WeakerAccess")
    public static final Set<String> READ_DOMAINS = new HashSet<>();
    @SuppressWarnings("ConstantConditions")
    private static final LoadingCache<Pair<BlockState, CompoundTag>, Set<JsonRule>>
        BLOCK_RULES_CACHE = CacheBuilder.newBuilder()
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build(CacheLoader.from(pair -> getBlockRulesInternal(pair.getLeft(), pair.getRight())));

    public static void loadAll() {
        RULES.clear();
        READ_DOMAINS.clear();
        BLOCK_RULES_CACHE.invalidateAll();
        InventoryContentPolicy.loadConfig();
        FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
            String domain = modContainer.getMetadata().getId();
            if (!READ_DOMAINS.contains(domain)) {
                String base = "assets/" + domain + "/compat/buildcraft/builders/";
                ClassLoader classLoader = RulesLoader.class.getClassLoader();
                try (InputStream inputStream = classLoader.getResourceAsStream(base + "index.json")) {
                    if (inputStream == null) return;
                    List<String> names = GSON.fromJson(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                        new TypeToken<List<String>>() {}.getType());
                    if (names == null) throw new IOException("Empty builder rule index for " + domain);
                    List<JsonRule> loaded = new ArrayList<>();
                    for (String name : names) {
                        if (name == null || name.isBlank() || name.contains("..") || name.startsWith("/")) {
                            throw new IOException("Invalid builder rule resource name in " + domain + ": " + name);
                        }
                        String resource = base + name + ".json";
                        try (InputStream rules = classLoader.getResourceAsStream(resource)) {
                            if (rules == null) throw new IOException("Can't read " + resource);
                            List<JsonRule> entries = GSON.fromJson(
                                new InputStreamReader(rules, StandardCharsets.UTF_8),
                                new TypeToken<List<JsonRule>>() {}.getType());
                            if (entries == null || entries.contains(null)) {
                                throw new IOException("Invalid builder rules in " + resource);
                            }
                            loaded.addAll(entries);
                        }
                    }
                    RULES.addAll(loaded);
                    READ_DOMAINS.add(domain);
                } catch (IOException | RuntimeException exception) {
                    throw new IllegalStateException("Could not load builder rules for " + domain, exception);
                }
            }
        });
        READ_DOMAINS.add("minecraft");
        READ_DOMAINS.add("buildcraftcore");
        READ_DOMAINS.add("buildcraftlib");
        READ_DOMAINS.add("buildcraftbuilders");
        READ_DOMAINS.add("buildcraftenergy");
        READ_DOMAINS.add("buildcraftfactory");
        READ_DOMAINS.add("buildcraftrobotics");
        READ_DOMAINS.add("buildcraftsilicon");
        READ_DOMAINS.add("buildcrafttransport");
        READ_DOMAINS.addAll(InventoryContentPolicy.getAllowedBlockDomains());
        BLOCK_RULES_CACHE.invalidateAll();
    }

    private static Set<JsonRule> getBlockRulesInternal(BlockState blockState, CompoundTag tileNbt) {
        return RulesLoader.RULES.stream()
            .filter(rule -> rule.selectors != null)
            .filter(rule ->
                rule.selectors.stream()
                    .anyMatch(selector ->
                        selector.matches(
                            base -> {
                                boolean complex = base.contains("[");
                                return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(
                                    complex
                                        ? base.substring(0, base.indexOf("["))
                                        : base
                                )).orElse(null) == blockState.getBlock() &&
                                    (!complex ||
                                        Arrays.stream(
                                            base.substring(
                                                base.indexOf("[") + 1,
                                                base.indexOf("]")
                                            )
                                                .split(", ")
                                        )
                                            .map(nameValue -> nameValue.split("="))
                                            .allMatch(nameValue ->
                                                blockState.getProperties().stream()
                                                    .filter(property -> property.getName().equals(nameValue[0]))
                                                    .findFirst()
                                                    .map(property ->
                                                        BlockUtil.getPropertyStringValue(
                                                            blockState,
                                                            property
                                                        )
                                                    )
                                                    .map(nameValue[1]::equals)
                                                    .orElse(false)
                                            )
                                    );
                            },
                            tileNbt == null ? new CompoundTag() : tileNbt
                        )
                    )
            )
            .collect(Collectors.toCollection(HashSet::new));
    }

    @SuppressWarnings("WeakerAccess")
    public static Set<JsonRule> getRules(BlockState blockState, CompoundTag tileNbt) {
        return BLOCK_RULES_CACHE.getUnchecked(Pair.of(blockState, tileNbt));
    }

    @SuppressWarnings("WeakerAccess")
    public static Set<JsonRule> getRules(Identifier entityId, CompoundTag tileNbt) {
        // noinspection ConstantConditions
        return RulesLoader.RULES.stream()
            .filter(rule -> rule.selectors != null)
            .filter(rule ->
                rule.selectors.stream()
                    .anyMatch(selector -> selector.matches(entityId.toString()::equals, tileNbt))
            )
            .collect(Collectors.toCollection(HashSet::new));
    }
}

