package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> registeredTools) {
        tools = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools) {
            tools.put(tool.name(), tool);
        }
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }
}
