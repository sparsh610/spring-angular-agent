package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class AddFeatureTool implements AgentTool {
    private static final List<String> ALLOWED_ROOTS = List.of(
            "src/main/java",
            "src/main/resources",
            "src/test/java",
            "frontend/src"
    );
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".java", ".ts", ".html", ".css", ".scss", ".json", ".yml", ".yaml", ".properties"
    );
    private static final int MAX_CONTENT_LENGTH = 60_000;

    @Override
    public String name() {
        return "add_feature";
    }

    @Override
    public String description() {
        return "Add or improve functionality by writing a source file in this project. "
                + "Use this to create a new file or replace an existing one with updated code. "
                + "Allowed under src/main/java, src/main/resources, src/test/java, and frontend/src. "
                + "Always provide the complete file content.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(
                "path", "Relative file path inside the project, for example "
                        + "src/main/java/com/example/agent/tool/MyTool.java.",
                "content", "The complete contents to write to the file."
        );
    }

    @Override
    public String run(Map<String, Object> arguments) {
        String path = String.valueOf(arguments.getOrDefault("path", "")).trim();
        if (path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Object rawContent = arguments.get("content");
        if (rawContent == null) {
            throw new IllegalArgumentException("content is required");
        }
        String content = String.valueOf(rawContent);
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "content exceeds the " + MAX_CONTENT_LENGTH + " character limit");
        }

        Path root = Path.of("").toAbsolutePath().normalize();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path must stay inside the project directory");
        }
        if (!isUnderAllowedRoot(root, target)) {
            throw new IllegalArgumentException(
                    "Path must be under one of: " + String.join(", ", ALLOWED_ROOTS));
        }
        if (!hasAllowedExtension(target)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Allowed extensions: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        boolean existed = Files.exists(target);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new IllegalStateException("Could not write file: " + exc.getMessage(), exc);
        }

        String relative = root.relativize(target).toString();
        long lines = content.lines().count();
        return (existed ? "Updated " : "Created ") + relative
                + " (" + lines + " lines, " + content.length() + " characters).";
    }

    private boolean isUnderAllowedRoot(Path root, Path target) {
        return ALLOWED_ROOTS.stream()
                .map(allowed -> root.resolve(allowed).normalize())
                .anyMatch(target::startsWith);
    }

    private boolean hasAllowedExtension(Path target) {
        String fileName = target.getFileName().toString().toLowerCase();
        return ALLOWED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
