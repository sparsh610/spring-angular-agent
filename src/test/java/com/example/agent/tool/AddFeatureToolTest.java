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
