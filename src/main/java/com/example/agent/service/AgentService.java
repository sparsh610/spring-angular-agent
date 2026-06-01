package com.example.agent.service;

import com.example.agent.api.AgentRequest;
import com.example.agent.api.AgentResponse;
import com.example.agent.api.ToolTrace;
import com.example.agent.config.AgentProperties;
import com.example.agent.llm.ChatMessage;
import com.example.agent.llm.ModelClient;
import com.example.agent.llm.ModelClientFactory;
import com.example.agent.tool.AgentTool;
import com.example.agent.tool.ToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {
    private static final String SYSTEM_PROMPT = """
            You are a practical assistant that can use tools.

            When a tool is useful, respond with only JSON in this format:
            {"tool":"tool_name","arguments":{"name":"value"}}

            When you have enough information to answer, respond with only JSON in this format:
            {"final":"your answer"}

            Do not wrap JSON in markdown. Use only the listed tools.
            """;

    private final AgentProperties properties;
    private final ModelClientFactory modelClientFactory;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public AgentService(
            AgentProperties properties,
            ModelClientFactory modelClientFactory,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.modelClientFactory = modelClientFactory;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    public AgentResponse run(AgentRequest request) {
        ModelClient modelClient = modelClientFactory.current();
        List<ChatMessage> messages = new ArrayList<>();
        List<ToolTrace> trace = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT + "\nTools:\n" + toolSchemas()));
        messages.add(new ChatMessage("user", request.message()));

        for (int step = 1; step <= properties.getMaxSteps(); step++) {
            String rawResponse = modelClient.complete(messages);
            messages.add(new ChatMessage("assistant", rawResponse));
            AgentAction action = parseAction(rawResponse);

            if (action.isFinal()) {
                return new AgentResponse(
                        action.finalAnswer(),
                        modelClient.providerName(),
                        request.trace() ? trace : List.of()
                );
            }

            String result = runTool(action);
            trace.add(new ToolTrace(step, "tool_call", action.tool(), action.arguments(), result));
            messages.add(new ChatMessage("user", "Tool result for " + action.tool() + ":\n" + result));
        }

        throw new IllegalStateException("Agent stopped after " + properties.getMaxSteps() + " steps without a final answer");
    }

    private String runTool(AgentAction action) {
        if (action.tool() == null || action.tool().isBlank()) {
            return "Tool error: model did not select a tool";
        }
        return toolRegistry.find(action.tool())
                .map(tool -> executeTool(tool, action.arguments()))
                .orElse("Tool error: unknown tool " + action.tool());
    }

    private String executeTool(AgentTool tool, Map<String, Object> arguments) {
        try {
            return tool.run(arguments == null ? Map.of() : arguments);
        } catch (RuntimeException exc) {
            return "Tool error: " + exc.getMessage();
        }
    }

    private AgentAction parseAction(String rawResponse) {
        try {
            String cleaned = stripCodeFence(rawResponse);
            JsonNode root = objectMapper.readTree(cleaned);
            if (root.hasNonNull("final")) {
                return new AgentAction(root.get("final").asText(), null, Map.of());
            }
            Map<String, Object> arguments = objectMapper.convertValue(
                    root.path("arguments"),
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
            );
            return new AgentAction(null, root.path("tool").asText(), arguments);
        } catch (JsonProcessingException exc) {
            return new AgentAction(
                    "The model returned invalid JSON, so the agent stopped. Raw response: " + rawResponse,
                    null,
                    Map.of()
            );
        }
    }

    private String stripCodeFence(String text) {
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```json\\s*", "");
            cleaned = cleaned.replaceFirst("^```\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.strip();
    }

    private String toolSchemas() {
        List<Map<String, Object>> schemas = toolRegistry.all().stream()
                .map(tool -> {
                    Map<String, Object> schema = new LinkedHashMap<>();
                    schema.put("name", tool.name());
                    schema.put("description", tool.description());
                    schema.put("parameters", tool.parameters());
                    return schema;
                })
                .toList();
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemas);
        } catch (JsonProcessingException exc) {
            throw new IllegalStateException("Could not serialize tool schemas", exc);
        }
    }
}
