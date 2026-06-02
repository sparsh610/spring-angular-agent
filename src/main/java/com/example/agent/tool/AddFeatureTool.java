package com.example.agent.tool;

import com.example.agent.llm.ChatMessage;
import com.example.agent.llm.ModelClientFactory;
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
    private static final String GENERATION_SYSTEM_PROMPT = """
            You are a senior engineer working on a Spring Boot + Angular project.
            Generate the complete contents of a single source file that implements the requested feature.
            Match the conventions already used in the project for the given file path and type.
            Respond with only the raw file contents.
            Do not add explanations, comments about the request, or markdown code fences.
            """;

    private final ModelClientFactory modelClientFactory;

    public AddFeatureTool(ModelClientFactory modelClientFactory) {
        this.modelClientFactory = modelClientFactory;
    }

    @Override
    public String name() {
        return "add_feature";
    }

    @Override
    public String description() {
        return "Add or improve functionality by generating a source file with the configured model "
                + "and writing it to the project. Provide a clear description of the feature and the "
                + "target file path. Allowed under src/main/java, src/main/resources, src/test/java, "
                + "and frontend/src.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(
                "request", "Describe the functionality to add or improve.",
                "path", "Relative target file path, for example "
                        + "src/main/java/com/example/agent/tool/MyTool.java.",
                "content", "Optional. Exact file contents to write. If omitted, the model generates them "
                        + "from the request."
        );
    }

    @Override
    public String run(Map<String, Object> arguments) {
        String path = String.valueOf(arguments.getOrDefault("path", "")).trim();
        if (path.isBlank()) {
            throw new IllegalArgumentException("path is required");
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

        String content = resolveContent(arguments, path);
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Generated content exceeds the " + MAX_CONTENT_LENGTH + " character limit");
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
                + " using " + modelClientFactory.current().providerName()
                + " (" + lines + " lines, " + content.length() + " characters).";
    }

    private String resolveContent(Map<String, Object> arguments, String path) {
        Object rawContent = arguments.get("content");
        if (rawContent != null && !String.valueOf(rawContent).isBlank()) {
            return String.valueOf(rawContent);
        }

        String request = String.valueOf(arguments.getOrDefault("request", "")).trim();
        if (request.isBlank()) {
            throw new IllegalArgumentException("Provide either content or a request describing the feature");
        }

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", GENERATION_SYSTEM_PROMPT),
                new ChatMessage("user", "Target file: " + path + "\nFeature request: " + request)
        );
        String generated = modelClientFactory.current().complete(messages);
        if (generated == null || generated.isBlank()) {
            throw new IllegalStateException("The model returned no content for the requested feature");
        }
        return stripCodeFence(generated);
    }

    private String stripCodeFence(String text) {
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.strip() + System.lineSeparator();
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
