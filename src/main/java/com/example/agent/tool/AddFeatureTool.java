package com.example.agent.tool;

import com.example.agent.llm.ChatMessage;
import com.example.agent.llm.ModelClientFactory;
import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    // Must match <java.version> in pom.xml so generated classes match the bytecode
    // version the running Spring context (and its ASM scanner) can read on restart.
    private static final String TARGET_RELEASE = "17";
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
                + "and frontend/src. Java files are recompiled so Spring Boot DevTools hot-reloads them; "
                + "frontend files hot-reload when ng serve is running.";
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

        Object rawContent = arguments.get("content");
        boolean modelGenerated = rawContent == null || String.valueOf(rawContent).isBlank();
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
        String source = modelGenerated
                ? " using " + modelClientFactory.current().providerName()
                : " from supplied content";
        String deploy = relative.toLowerCase().endsWith(".java")
                ? " " + hotReload(target, root)
                : " Frontend changes hot-reload automatically when ng serve is running.";
        return (existed ? "Updated " : "Created ") + relative + source
                + " (" + lines + " lines, " + content.length() + " characters)." + deploy;
    }

    private String hotReload(Path javaFile, Path root) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return "No JDK compiler is available, so the class was not built; "
                    + "restart the backend to load the change.";
        }
        Path classesDir = root.resolve("target").resolve("classes");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Files.createDirectories(classesDir);
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjects(javaFile.toFile());
            List<String> options = List.of(
                    "--release", TARGET_RELEASE,
                    "-classpath", System.getProperty("java.class.path", ""),
                    "-d", classesDir.toString()
            );
            boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (compiled) {
                return "Compiled it into target/classes; Spring Boot DevTools will restart "
                        + "and load the change automatically.";
            }
            return "Compilation failed, so the running app was left untouched. Fix these errors: "
                    + formatDiagnostics(diagnostics);
        } catch (IOException exc) {
            return "Could not compile the class for hot reload (" + exc.getMessage()
                    + "); restart the backend to load the change.";
        }
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> "line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null))
                .limit(10)
                .collect(Collectors.joining("; "));
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
