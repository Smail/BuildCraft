package buildcraft.lib.platform.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric has no config-spec service; schemas retain live getters backed by validated snapshots. */
public final class ConfigBinding {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<BCConfigSpec, BoundConfig> SPECS = new IdentityHashMap<>();
    private static final Map<String, Registration> REGISTERED = new LinkedHashMap<>();
    private static boolean lifecycleRegistered;
    private static boolean worldRunning;

    private ConfigBinding() {}

    public static synchronized BoundConfig bind(BCConfigSpec schema) {
        Objects.requireNonNull(schema, "schema");
        BoundConfig existing = SPECS.get(schema);
        if (existing != null) return existing;
        BoundConfig config = new BoundConfig();
        schema.bind(config);
        SPECS.put(schema, config);
        return config;
    }

    public static synchronized void register(String modId, BCConfigSpec schema,
            Consumer<String> onLoad, Consumer<String> onReload) {
        if (modId == null || !modId.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid config module ID: " + modId);
        Objects.requireNonNull(onLoad, "onLoad");
        Objects.requireNonNull(onReload, "onReload");
        if (REGISTERED.containsKey(modId)) throw new IllegalStateException("Config module registered twice: " + modId);
        BoundConfig config = bind(schema);
        if (config.file != null) throw new IllegalStateException("Config schema already attached to " + config.file);
        Path file = FabricLoader.getInstance().getConfigDir().resolve(modId + "-common.json");
        config.load(file, false);
        REGISTERED.put(modId, new Registration(config, onReload));
        onLoad.accept(modId);
        if (!lifecycleRegistered) {
            ServerLifecycleEvents.SERVER_STARTING.register(server -> {
                reloadAll();
                synchronized (ConfigBinding.class) { worldRunning = true; }
            });
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
                synchronized (ConfigBinding.class) {
                    worldRunning = false;
                    for (Registration registration : REGISTERED.values()) registration.config.applyPending();
                }
            });
            ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resources) -> reloadAll());
            lifecycleRegistered = true;
        }
    }

    /** Used by the client config screen and the server reload lifecycle. */
    public static synchronized void reloadAll() {
        for (Map.Entry<String, Registration> entry : REGISTERED.entrySet()) {
            Registration registration = entry.getValue();
            registration.config.load(registration.config.file, worldRunning);
            registration.onReload.accept(entry.getKey());
        }
    }

    private record Registration(BoundConfig config, Consumer<String> onReload) {}

    public record Definition(String path, Object defaultValue, List<String> comments, boolean worldRestart) {}

    public static final class BoundConfig implements BCConfigSpec.Backend {
        private final List<String> sections = new ArrayList<>();
        private final Map<String, Field<?>> fields = new LinkedHashMap<>();
        private List<String> comments = List.of();
        private boolean restart;
        private volatile Map<String, Object> values = Map.of();
        private Map<String, Object> pending = Map.of();
        private Path file;

        private BoundConfig() {}

        public List<Definition> definitions() {
            return fields.values().stream().map(field -> field.definition).toList();
        }

        public Path file() {
            return file;
        }

        @Override
        public void push(String path) {
            requireSchemaOpen();
            // Native config builders split dotted pushes and pop one segment at a time.
            for (String segment : Objects.requireNonNull(path, "path").split("\\.", -1)) sections.add(validSegment(segment));
        }

        @Override
        public void pop() {
            requireSchemaOpen();
            if (sections.isEmpty()) throw new IllegalStateException("Config section stack is empty");
            sections.removeLast();
        }

        @Override
        public void comment(String... lines) {
            requireSchemaOpen();
            comments = List.copyOf(Arrays.asList(Objects.requireNonNull(lines, "comments")));
        }

        @Override
        public void worldRestart() {
            requireSchemaOpen();
            restart = true;
        }

        @Override
        public Supplier<Boolean> bool(String name, boolean value) {
            return define(name, value, raw -> {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) throw invalid(name, "boolean");
                return raw.getAsBoolean();
            });
        }

        @Override
        public Supplier<Integer> integer(String name, int value, int min, int max) {
            if (min > max || value < min || value > max) throw invalid(name, "valid integer default and range");
            return define(name, value, raw -> {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw invalid(name, "integer");
                int decoded = raw.getAsBigDecimal().intValueExact();
                if (decoded < min || decoded > max) throw invalid(name, "integer in [" + min + ", " + max + "]");
                return decoded;
            });
        }

        @Override
        public Supplier<Double> decimal(String name, double value, double min, double max) {
            if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max)
                    || min > max || value < min || value > max) throw invalid(name, "valid decimal default and range");
            return define(name, value, raw -> {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) throw invalid(name, "decimal");
                double decoded = raw.getAsDouble();
                if (!Double.isFinite(decoded) || decoded < min || decoded > max) throw invalid(name, "decimal in [" + min + ", " + max + "]");
                return decoded;
            });
        }

        @Override
        public Supplier<String> string(String name, String value) {
            return define(name, Objects.requireNonNull(value, "default"), raw -> {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) throw invalid(name, "string");
                return raw.getAsString();
            });
        }

        @Override
        public <E extends Enum<E>> Supplier<E> enumeration(String name, E value, E[] allowed) {
            Objects.requireNonNull(value, "default");
            List<E> choices = List.copyOf(Arrays.asList(allowed.length == 0 ? value.getDeclaringClass().getEnumConstants() : allowed));
            if (!choices.contains(value)) throw invalid(name, "allowed enum default");
            return define(name, value, raw -> {
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) throw invalid(name, "enum name");
                for (E choice : choices) if (choice.name().equalsIgnoreCase(raw.getAsString())) return choice;
                throw invalid(name, "one of " + choices);
            });
        }

        private static String validSegment(String segment) {
            if (segment == null || segment.isEmpty() || segment.contains(".")) throw new IllegalArgumentException("Invalid config path segment: " + segment);
            return segment;
        }

        private void requireSchemaOpen() {
            if (file != null) throw new IllegalStateException("Config schema is already loaded: " + file);
        }

        private <T> Supplier<T> define(String name, T value, Function<JsonElement, T> decoder) {
            requireSchemaOpen();
            for (String segment : Objects.requireNonNull(name, "name").split("\\.", -1)) validSegment(segment);
            String path = String.join(".", sections);
            path = path.isEmpty() ? name : path + "." + name;
            if (fields.containsKey(path)) throw new IllegalArgumentException("Duplicate config field: " + path);
            Field<T> field = new Field<>(new Definition(path, value, comments, restart), decoder);
            fields.put(path, field);
            comments = List.of();
            restart = false;
            String key = path;
            return () -> {
                Object current = values.get(key);
                if (current == null) throw new IllegalStateException("Config value read before loading: " + key);
                @SuppressWarnings("unchecked") T typed = (T) current;
                return typed;
            };
        }

        /** Validate the complete file before publishing any live getter or rewriting defaults. */
        public synchronized void load(Path path, boolean preserveRestartValues) {
            Objects.requireNonNull(path, "file");
            if (file != null && !file.equals(path)) throw new IllegalArgumentException("Cannot change config file from " + file + " to " + path);
            try {
                JsonObject document;
                boolean exists = Files.exists(path);
                if (exists) {
                    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        JsonElement parsed = JsonParser.parseReader(reader);
                        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Config root must be an object");
                        document = parsed.getAsJsonObject();
                    }
                } else {
                    document = new JsonObject();
                }
                Map<String, Object> candidate = new LinkedHashMap<>();
                boolean addedDefaults = !exists;
                for (Field<?> field : fields.values()) {
                    String key = field.definition.path();
                    JsonElement raw = document.get(key);
                    if (raw == null) {
                        raw = JSON.toJsonTree(field.definition.defaultValue());
                        document.add(key, raw);
                        addedDefaults = true;
                    }
                    try {
                        candidate.put(key, Objects.requireNonNull(field.decoder.apply(raw), "Decoded null value"));
                    } catch (RuntimeException cause) {
                        throw new IllegalArgumentException("Invalid config field " + key, cause);
                    }
                }
                if (addedDefaults) save(path, document);
                Map<String, Object> active = new LinkedHashMap<>(candidate);
                if (preserveRestartValues) {
                    for (Field<?> field : fields.values()) {
                        String key = field.definition.path();
                        if (field.definition.worldRestart() && values.containsKey(key)) active.put(key, values.get(key));
                    }
                }
                pending = Map.copyOf(candidate);
                values = Map.copyOf(active);
                file = path;
            } catch (IOException | RuntimeException cause) {
                throw new IllegalStateException("Failed to load BuildCraft config " + path + "; previous values retained", cause);
            }
        }

        private synchronized void applyPending() {
            values = pending;
        }

        private static void save(Path path, JsonObject document) throws IOException {
            Path parent = path.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, JSON.toJson(document) + "\n", StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException cause) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException cause) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanup) {
                    cause.addSuppressed(cleanup);
                }
                throw cause;
            }
        }

        private static IllegalArgumentException invalid(String name, String expected) {
            return new IllegalArgumentException("Config " + name + " requires " + expected);
        }
    }

    private record Field<T>(Definition definition, Function<JsonElement, T> decoder) {}
}
