//? source if >=26.3
package buildcraft.lib.platform.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

/** Small immutable key/value container standing in for NeoForge's model data on Fabric. */
public final class BCModelData {
    public static final BCModelData EMPTY = new BCModelData(Collections.emptyMap());

    private final Map<BCModelProperty<?>, Object> values;

    private BCModelData(Map<BCModelProperty<?>, Object> values) {
        this.values = values;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean has(BCModelProperty<?> property) {
        return values.containsKey(Objects.requireNonNull(property, "property"));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(BCModelProperty<T> property) {
        return (T) values.get(Objects.requireNonNull(property, "property"));
    }

    public static final class Builder {
        private final Map<BCModelProperty<?>, Object> values = new IdentityHashMap<>();

        private Builder() {}

        public <T> Builder with(BCModelProperty<T> property, T value) {
            values.put(Objects.requireNonNull(property, "property"), value);
            return this;
        }

        public BCModelData build() {
            return values.isEmpty() ? EMPTY : new BCModelData(Collections.unmodifiableMap(new IdentityHashMap<>(values)));
        }
    }
}
