package com.example.agent.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddFeatureToolTest {
    // Model factory is unused on the content-bypass path, so null is safe here.
    private final AddFeatureTool tool = new AddFeatureTool(null);

    @Test
    void writesFileFromExplicitContent() throws Exception {
        String relative = "src/test/java/com/example/agent/tool/GeneratedSample.txt".replace("txt", "json");
        try {
            String result = tool.run(Map.of(
                    "path", relative,
                    "content", "{\"sample\":true}"
            ));
            Path written = Path.of("").toAbsolutePath().resolve(relative).normalize();
            assertTrue(Files.exists(written), "file should be created");
            assertEquals("{\"sample\":true}", Files.readString(written).strip());
            assertTrue(result.startsWith("Created ") || result.startsWith("Updated "));
        } finally {
            Files.deleteIfExists(Path.of("").toAbsolutePath().resolve(relative).normalize());
        }
    }

    @Test
    void compilesJavaFileForHotReload() throws Exception {
        String relative = "src/test/java/com/example/agent/tool/HotReloadSample.java";
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(relative).normalize();
        Path compiled = root.resolve("target/classes/com/example/agent/tool/HotReloadSample.class").normalize();
        try {
            String result = tool.run(Map.of(
                    "path", relative,
                    "content", "package com.example.agent.tool;\n"
                            + "public class HotReloadSample { public int value() { return 42; } }\n"
            ));
            assertTrue(result.contains("Compiled it into target/classes"),
                    "expected hot-reload compile message, was: " + result);
            assertTrue(Files.exists(compiled), "a .class file should be produced");
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(compiled);
        }
    }

    @Test
    void reportsCompilationErrorsWithoutCrashing() throws Exception {
        String relative = "src/test/java/com/example/agent/tool/BrokenSample.java";
        Path source = Path.of("").toAbsolutePath().resolve(relative).normalize();
        try {
            String result = tool.run(Map.of(
                    "path", relative,
                    "content", "package com.example.agent.tool;\n"
                            + "public class BrokenSample { this is not valid java }\n"
            ));
            assertTrue(result.contains("Compilation failed"),
                    "expected compilation failure message, was: " + result);
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void editReplacesSnippetWithoutClobbering() throws Exception {
        String relative = "src/test/java/com/example/agent/tool/EditSample.json";
        Path file = Path.of("").toAbsolutePath().resolve(relative).normalize();
        try {
            tool.run(Map.of("path", relative, "content", "{\"label\":\"old\",\"keep\":true}"));
            String result = tool.run(Map.of(
                    "path", relative,
                    "find", "\"old\"",
                    "replace", "\"new\""
            ));
            assertTrue(result.startsWith("Edited "), "expected Edited verb, was: " + result);
            assertEquals("{\"label\":\"new\",\"keep\":true}", Files.readString(file).strip());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void editFailsWhenFindMissing() throws Exception {
        String relative = "src/test/java/com/example/agent/tool/EditMissing.json";
        Path file = Path.of("").toAbsolutePath().resolve(relative).normalize();
        try {
            tool.run(Map.of("path", relative, "content", "{\"a\":1}"));
            assertThrows(IllegalArgumentException.class, () -> tool.run(Map.of(
                    "path", relative, "find", "not-present", "replace", "x"
            )));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void editFailsWhenFindAmbiguous() throws Exception {
        String relative = "src/test/java/com/example/agent/tool/EditDup.json";
        Path file = Path.of("").toAbsolutePath().resolve(relative).normalize();
        try {
            tool.run(Map.of("path", relative, "content", "{\"x\":\"x\"}"));
            assertThrows(IllegalArgumentException.class, () -> tool.run(Map.of(
                    "path", relative, "find", "x", "replace", "y"
            )));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void editFailsWhenFileMissing() {
        assertThrows(IllegalArgumentException.class, () -> tool.run(Map.of(
                "path", "src/test/java/com/example/agent/tool/DoesNotExist.json",
                "find", "a", "replace", "b"
        )));
    }

    @Test
    void rejectsPathOutsideAllowedRoots() {
        assertThrows(IllegalArgumentException.class, () -> tool.run(Map.of(
                "path", "pom.xml",
                "content", "x"
        )));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> tool.run(Map.of(
                "path", "../../etc/evil.java",
                "content", "x"
        )));
    }

    @Test
    void rejectsDisallowedExtension() {
        assertThrows(IllegalArgumentException.class, () -> tool.run(Map.of(
                "path", "src/main/resources/evil.exe",
                "content", "x"
        )));
    }

    @Test
    void requiresContentOrRequest() {
        assertThrows(IllegalArgumentException.class, () -> tool.run(Map.of(
                "path", "src/main/resources/empty.json"
        )));
    }
}
