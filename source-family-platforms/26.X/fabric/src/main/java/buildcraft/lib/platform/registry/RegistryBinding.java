package buildcraft.lib.platform.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Fabric catalogs are bound during construction and executed explicitly during common initialization. */
public final class RegistryBinding implements BCRegistryBinder {
    private enum State { PLANNING, REGISTERING, COMPLETE, FAILED }
    private final Map<Registry<?>, List<Runnable>> pending = new LinkedHashMap<>();
    private State state = State.PLANNING;

    private RegistryBinding() {}

    public static RegistryBinding on() {
        return new RegistryBinding();
    }

    @Override
    public <T> void register(BCDeferredRegister<T> catalog) {
        Objects.requireNonNull(catalog, "catalog");
        requirePlanning();
        Registry<T> registry = resolve(catalog.registryId());
        catalog.bindEntries(new BCDeferredRegister.EntryBinder<T>() {
            @Override
            public <I extends T> void bind(BCRegistryEntry<I> entry) {
                requirePlanning();
                RegisteredValue<I> value = new RegisteredValue<>(entry.getId());
                entry.bind(value::get, () -> value.value != null);
                pending.computeIfAbsent(registry, ignored -> new ArrayList<>())
                    .add(() -> registerEntry(registry, entry, value));
            }
        });
    }

    private void requirePlanning() {
        if (state != State.PLANNING) {
            throw new IllegalStateException("Fabric registration is no longer accepting catalogs: " + state);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> resolve(String registryId) {
        Identifier id = Identifier.parse(registryId);
        Registry<?> registry = BuiltInRegistries.REGISTRY.getOptional(id).orElseThrow(
            () -> new IllegalArgumentException("No static Fabric registry exists for " + id)
        );
        return (Registry<T>) registry;
    }

    private static <T, I extends T> void registerEntry(Registry<T> registry, BCRegistryEntry<I> entry,
            RegisteredValue<I> value) {
        try {
            if (registry.containsKey(entry.getId())) {
                throw new IllegalStateException("Registry ID already exists: " + entry.getId());
            }
            I created = Objects.requireNonNull(entry.factory().get(), "Registry factory returned null");
            value.value = Registry.register(registry, entry.getId(), created);
            RegistryAttributeHolder.get(registry).addAttribute(RegistryAttribute.MODDED);
        } catch (RuntimeException cause) {
            throw new IllegalStateException("Failed to register " + entry.getId() + " in " + registry.key(), cause);
        }
    }

    /** Call once after all module catalogs have been constructed, before their setup hooks. */
    public void registerAll() {
        requirePlanning();
        state = State.REGISTERING;
        try {
            // Native registry order ensures fluids and blocks exist before item factories run.
            for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
                List<Runnable> registrations = pending.get(registry);
                if (registrations != null) for (Runnable registration : registrations) registration.run();
            }
            pending.clear();
            state = State.COMPLETE;
        } catch (RuntimeException | Error cause) {
            state = State.FAILED;
            throw cause;
        }
    }

    private static final class RegisteredValue<T> {
        private final Identifier id;
        private T value;

        private RegisteredValue(Identifier id) {
            this.id = id;
        }

        private T get() {
            if (value == null) throw new IllegalStateException("Fabric entry read before registration: " + id);
            return value;
        }
    }
}
