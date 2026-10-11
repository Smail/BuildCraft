package buildcraft.lib.client.render.fluid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Guards the resource reload ordering that {@code FluidRenderer} depends on.
 * <p>
 * The block atlas is uploaded before the fluid models are baked, so {@code onTextureStitchPost} must not resolve any
 * fluid sprite or model. It may only drop its caches and remember the atlas lookup; sprites are resolved lazily on
 * first use. Lazy resolution is also correct on versions that do not enforce the ordering, so this applies to every
 * target.
 * <p>
 * The renderer needs a running client, so this test reads the renderer source that the current target compiles. The
 * Gradle test task runs with the effective source root as its working directory. */
class FluidRendererStitchOrderTester {
    private static final Path RENDERER = Path.of(
        "src/main/java/buildcraft/lib/client/render/fluid/FluidRenderer.java");
    private static final String HANDLER_SIGNATURE = "public static void onTextureStitchPost(";
    /** Calls that only touch the atlas event or reset the caches and so cannot resolve a fluid sprite or model.
     * {@code if} is listed because the call pattern also matches the keyword. */
    private static final Set<String> ALLOWED_CALLS = Set.of(
        "if", "onTextureStitchPost", "equals", "location", "getAtlas", "clearSpriteCache");
    private static final Pattern CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    @Test
    void stitchHandlerOnlyClearsCaches() throws IOException {
        Path file = RENDERER.toAbsolutePath();
        Assertions.assertTrue(Files.isRegularFile(file), "Renderer source not found at " + file);
        String body = stripComments(handlerBody(Files.readString(file, StandardCharsets.UTF_8)));

        Set<String> calls = new TreeSet<>();
        Matcher matcher = CALL.matcher(body);
        while (matcher.find()) {
            calls.add(matcher.group(1));
        }
        Set<String> unexpected = new TreeSet<>(calls);
        unexpected.removeAll(ALLOWED_CALLS);
        Assertions.assertTrue(unexpected.isEmpty(),
            "onTextureStitchPost resolves fluid state before the fluid models are baked: " + unexpected);
        Assertions.assertTrue(calls.contains("clearSpriteCache"),
            "onTextureStitchPost must drop stale sprites after an atlas upload");
    }

    /** Returns the text between the braces of the stitch handler. */
    private static String handlerBody(String source) {
        int start = source.indexOf(HANDLER_SIGNATURE);
        Assertions.assertTrue(start >= 0, "No onTextureStitchPost in the renderer");
        int open = source.indexOf('{', start);
        Assertions.assertTrue(open >= 0, "onTextureStitchPost has no body");
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, i);
                }
            }
        }
        throw new AssertionError("Unbalanced braces in onTextureStitchPost");
    }

    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
