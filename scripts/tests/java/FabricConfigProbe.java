package probe;

import buildcraft.lib.platform.config.BCConfigSpec;
import buildcraft.lib.platform.config.ConfigBinding;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs without Fabric initialization; verifies disk snapshots using the real Gson dependency. */
public final class FabricConfigProbe {
    private enum Choice { FIRST, SECOND }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid configuration was accepted");
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("bc-fabric-config-");
        Path file = directory.resolve("probe.json");
        try {
            BCConfigSpec.Builder builder = new BCConfigSpec.Builder();
            builder.push("general");
            builder.comment("Keep this description").worldRestart();
            var restart = builder.define("restart", true);
            var integer = builder.defineInRange("limit", 3, 1, 10);
            var decimal = builder.defineInRange("ratio", 1.5, 0.5, 4);
            var text = builder.define("text", "default");
            var enumeration = builder.defineEnum("choice", Choice.FIRST);
            builder.pop();
            builder.push("worldgen.oil");
            var oil = builder.define("enable", true);
            builder.pop();
            builder.push("worldgen.oil.spouts");
            var spouts = builder.define("enable", true);
            builder.pop();
            var schema = builder.build();
            var config = ConfigBinding.bind(schema);
            check(ConfigBinding.bind(schema) == config, "Schema binding was repeated");
            rejects(integer::get);
            config.load(file, false);
            check(integer.get() == 3 && restart.get(), "Defaults were lost");
            check(Files.readString(file).contains("general.limit"), "Defaults were not persisted");
            check(Files.readString(file).contains("worldgen.worldgen.oil.spouts.enable"), "Native dotted push/pop semantics changed");
            check(config.definitions().getFirst().worldRestart(), "Restart flag was lost");
            check(config.definitions().getFirst().comments().equals(java.util.List.of("Keep this description")), "Comments were lost");
            Files.writeString(file, "{\"general.restart\":false,\"general.limit\":7,\"general.ratio\":2.5,\"general.text\":\"changed\",\"general.choice\":\"SECOND\",\"addon.key\":9}");
            config.load(file, true);
            check(integer.get() == 7 && decimal.get() == 2.5 && text.get().equals("changed"), "Live reload failed");
            check(enumeration.get() == Choice.SECOND, "Enum reload failed");
            check(restart.get(), "Restart value changed during a running world");
            config.load(file, false);
            check(!restart.get(), "Restart value failed to apply at world startup");
            Files.writeString(file, "{\"general.limit\":5,\"general.ratio\":100}");
            rejects(() -> config.load(file, false));
            check(integer.get() == 7 && decimal.get() == 2.5, "Rejected reload partially changed getters");
            Files.writeString(file, "{\"general.limit\":5.5}");
            rejects(() -> config.load(file, false));
            check(integer.get() == 7, "Fractional integer accepted");
            Files.writeString(file, "[]");
            rejects(() -> config.load(file, false));
            Files.writeString(file, "{broken");
            rejects(() -> config.load(file, false));
            Files.writeString(file, "{\"general.limit\":8,\"addon.key\":9}");
            config.load(file, false);
            check(integer.get() == 8 && decimal.get() == 1.5, "Missing defaults were not restored");
            check(Files.readString(file).contains("addon.key"), "Unknown addon key was discarded");
            System.out.println("Fabric configuration probe passed");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
