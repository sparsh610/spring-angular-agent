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
    // Refuse a model-generated overwrite that drops most of a non-trivial existing
    // file: that is almost always an accidental clobber, not the requested edit.
    private static final int CLOBBER_GUARD_MIN_CHARS = 400;
    private static final double CLOBBER_GUARD_RATIO = 0.5;
    private static final String CREATE_SYSTEM_PROMPT = """
            You are a senior engineer working on a Spring Boot + Angular project.
            Generate the complete contents of a single new source file that implements the requested feature.
            Match the conventions already used in the project for the given file path and type.
            Respond with only the raw file contents.
            Do not add explanations, comments about the request, or markdown code fences.
            """;
    private static final String EDIT_SYSTEM_PROMPT = """
            You are a senior engineer editing an existing file in a Spring Boot + Angular project.
            You are given the current file contents and a requested change.
            Return the COMPLETE updated file with the change applied.
            Preserve all existing code, markup, imports, and structure that the change does not touch.
            Never replace the whole file with only the new snippet.
            Respond with only the raw file contents.
            Do not add explanations or markdown code fences.
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
        return "Add or improve functionality by writing a source file. "
                + "To change an EXISTING file, prefer a targeted edit: pass find (the exact snippet to "
                + "replace) and replace (the new snippet) so the rest of the file is preserved. "
                + "Otherwise pass a request and the configured model edits the existing file or creates a "
                + "new one, or pass content to write exact contents. Allowed under src/main/java, "
                + "src/main/resources, src/test/java, and frontend/src. Java files are recompiled so Spring "
                + "Boot DevTools hot-reloads them; frontend files hot-reload when ng serve is running.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(
                "path", "Relative target file path, for example "
                        + "frontend/src/app/app.component.html.",
                "find", "Optional. Exact existing snippet to replace (targeted edit). Must match once. "
                        + "Best way to change an existing file without clobbering it.",
                "replace", "Replacement snippet for find. Use an empty string to delete the snippet.",
                "request", "Optional. Describe the change; the model edits the existing file (preserving it) "
                        + "or creates a new one.",
                "content", "Optional. Exact full file contents to write."
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

        String relative = root.relativize(target).toString();
        boolean existed = Files.exists(target);
        Resolution resolution = resolveContent(arguments, path, target, relative, existed);
        if (resolution.content().length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Resulting content exceeds the " + MAX_CONTENT_LENGTH + " character limit");
        }

        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, resolution.content(), StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new IllegalStateException("Could not write file: " + exc.getMessage(), exc);
        }

        long lines = resolution.content().lines().count();
        String source = resolution.modelUsed()
                ? " using " + modelClientFactory.current().providerName()
                : "";
        String deploy = relative.toLowerCase().endsWith(".java")
                ? " " + hotReload(target, root)
                : " Frontend changes hot-reload automatically when ng serve is running.";
        return resolution.verb() + " " + relative + source
                + " (" + lines + " lines, " + resolution.content().length() + " characters)." + deploy;
    }

    private Resolution resolveContent(
            Map<String, Object> arguments, String path, Path target, String relative, boolean existed) {
        Object rawFind = arguments.get("find");
        if (rawFind != null && !String.valueOf(rawFind).isBlank()) {
            return editByFindReplace(arguments, target, relative, existed, String.valueOf(rawFind));
        }

        Object rawContent = arguments.get("content");
        if (rawContent != null && !String.valueOf(rawContent).isBlank()) {
            return new Resolution(String.valueOf(rawContent), false, existed ? "Updated" : "Created");
        }

        String request = String.valueOf(arguments.getOrDefault("request", "")).trim();
        if (request.isBlank()) {
            throw new IllegalArgumentException(
                    "Provide find+replace, content, or a request describing the change");
        }
        return existed
                ? generateEdit(path, target, relative, request)
                : new Resolution(generateNewFile(path, request), true, "Created");
    }

    private Resolution editByFindReplace(
            Map<String, Object> arguments, Path target, String relative, boolean existed, String find) {
        if (!existed) {
            throw new IllegalArgumentException(
                    "Cannot edit " + relative + " because it does not exist. "
                            + "Provide content or a request to create it.");
        }
        String existing = readFile(target, relative);
        int occurrences = countOccurrences(existing, find);
        if (occurrences == 0) {
            throw new IllegalArgumentException(
                    "The find text was not found in " + relative
                            + ". Provide the exact existing snippet to replace.");
        }
        if (occurrences > 1) {
            throw new IllegalArgumentException(
                    "The find text appears " + occurrences + " times in " + relative
                            + ". Include enough surrounding context to make it unique.");
        }
        String replace = String.valueOf(arguments.getOrDefault("replace", ""));
        return new Resolution(existing.replace(find, replace), false, "Edited");
    }

    private Resolution generateEdit(String path, Path target, String relative, String request) {
        String existing = readFile(target, relative);
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", EDIT_SYSTEM_PROMPT),
                new ChatMessage("user", "Target file: " + path
                        + "\n\nCurrent file contents:\n" + existing
                        + "\n\nRequested change: " + request
                        + "\n\nReturn the complete updated file.")
        );
        String generated = complete(messages);
        if (existing.length() >= CLOBBER_GUARD_MIN_CHARS
                && generated.length() < existing.length() * CLOBBER_GUARD_RATIO) {
            throw new IllegalStateException(
                    "Refusing to overwrite " + relative + ": the generated file ("
                            + generated.length() + " chars) is less than half the existing file ("
                            + existing.length() + " chars), which looks like an accidental clobber. "
                            + "Use find+replace for a targeted edit.");
        }
        return new Resolution(generated, true, "Updated");
    }

    private String generateNewFile(String path, String request) {
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", CREATE_SYSTEM_PROMPT),
                new ChatMessage("user", "Target file: " + path + "\nFeature request: " + request)
        );
        return complete(messages);
    }

    private String complete(List<ChatMessage> messages) {
        String generated = modelClientFactory.current().complete(messages);
        if (generated == null || generated.isBlank()) {
            throw new IllegalStateException("The model returned no content for the requested change");
        }
        return stripCodeFence(generated);
    }

    private String readFile(Path target, String relative) {
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new IllegalStateException("Could not read " + relative + ": " + exc.getMessage(), exc);
        }
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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

    private record Resolution(String content, boolean modelUsed, String verb) {
    }
}
