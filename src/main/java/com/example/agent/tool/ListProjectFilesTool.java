package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ListProjectFilesTool implements AgentTool {
    @Override
    public String name() {
        return "list_project_files";
    }

    @Override
    public String description() {
        return "List files under the current project directory.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("path", "Optional relative directory path. Use . for the project root.");
    }

    @Override
    public String run(Map<String, Object> arguments) {
        String path = String.valueOf(arguments.getOrDefault("path", "."));
        Path root = Path.of("").toAbsolutePath().normalize();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path must stay inside the project directory");
        }
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("Directory does not exist: " + path);
        }
        try (var stream = Files.walk(target, 3)) {
            String result = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .limit(100)
                    .map(root::relativize)
                    .map(Path::toString)
                    .collect(Collectors.joining("\n"));
            return result.isBlank() ? "(no files found)" : result;
        } catch (IOException exc) {
            throw new IllegalStateException("Could not list files: " + exc.getMessage(), exc);
        }
    }
}
