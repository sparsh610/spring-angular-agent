package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class DescribeCodeTool implements AgentTool {
    private static final List<String> SOURCE_ROOTS = List.of(
            "src/main/java",
            "frontend/src/app"
    );

    @Override
    public String name() {
        return "describe_code";
    }

    @Override
    public String description() {
        return "Describe this project's own source code structure and main responsibilities.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("focus", "Optional area to focus on, for example backend, frontend, tools, or llm.");
    }

    @Override
    public String run(Map<String, Object> arguments) {
        String focus = String.valueOf(arguments.getOrDefault("focus", "project")).toLowerCase();
        Path root = Path.of("").toAbsolutePath().normalize();
        List<Path> files = sourceFiles(root);

        long javaFiles = files.stream().filter(path -> path.toString().endsWith(".java")).count();
        long typeScriptFiles = files.stream().filter(path -> path.toString().endsWith(".ts")).count();
        long htmlFiles = files.stream().filter(path -> path.toString().endsWith(".html")).count();
        long cssFiles = files.stream().filter(path -> path.toString().endsWith(".css")).count();

        StringBuilder summary = new StringBuilder();
        summary.append("Project code summary\n");
        summary.append("- Backend: Spring Boot REST API exposes /api/agent/chat.\n");
        summary.append("- Agent loop: AgentService sends messages to the selected model, parses JSON actions, executes tools, and returns optional trace data.\n");
        summary.append("- Tools: ToolRegistry wires current_time, calculate, list_project_files, and describe_code.\n");
        summary.append("- LLM clients: DemoModelClient works without an API key; OpenAiModelClient supports OpenAI-compatible chat completions.\n");
        summary.append("- Frontend: Angular standalone component provides chat input, example prompts, and a tool trace panel.\n");
        summary.append("- Guardrails: requests are validated, agent steps are limited, and file listing stays inside the project directory.\n");
        summary.append("\nSource footprint\n");
        summary.append("- Java files: ").append(javaFiles).append("\n");
        summary.append("- TypeScript files: ").append(typeScriptFiles).append("\n");
        summary.append("- HTML files: ").append(htmlFiles).append("\n");
        summary.append("- CSS files: ").append(cssFiles).append("\n");
        summary.append("\nImportant files\n");
        files.stream()
                .map(root::relativize)
                .map(Path::toString)
                .filter(path -> matchesFocus(path.toLowerCase(), focus))
                .limit(14)
                .forEach(path -> summary.append("- ").append(path).append("\n"));
        return summary.toString().strip();
    }

    private List<Path> sourceFiles(Path root) {
        return SOURCE_ROOTS.stream()
                .map(root::resolve)
                .filter(Files::exists)
                .flatMap(this::walk)
                .filter(Files::isRegularFile)
                .filter(this::isSourceFile)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private Stream<Path> walk(Path path) {
        try {
            return Files.walk(path, 8);
        } catch (IOException exc) {
            throw new IllegalStateException("Could not inspect source files: " + exc.getMessage(), exc);
        }
    }

    private boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java")
                || fileName.endsWith(".ts")
                || fileName.endsWith(".html")
                || fileName.endsWith(".css");
    }

    private boolean matchesFocus(String path, String focus) {
        return switch (focus) {
            case "backend" -> path.contains("src\\main\\java") || path.contains("src/main/java");
            case "frontend" -> path.contains("frontend");
            case "tools", "tool" -> path.contains("tool");
            case "llm", "model" -> path.contains("llm");
            default -> true;
        };
    }
}
