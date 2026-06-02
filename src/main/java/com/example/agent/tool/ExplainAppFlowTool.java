package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExplainAppFlowTool implements AgentTool {
    @Override
    public String name() {
        return "explain_app_flow";
    }

    @Override
    public String description() {
        return "Explain how a user request flows through the Angular UI, Spring Boot API, agent loop, model provider, and tools.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(
                "focus",
                "Optional focus area: end_to_end, frontend, backend, model, tools, or config."
        );
    }

    @Override
    public String run(Map<String, Object> arguments) {
        String focus = String.valueOf(arguments.getOrDefault("focus", "end_to_end")).toLowerCase();
        return switch (focus) {
            case "frontend", "angular" -> frontendFlow();
            case "backend", "spring" -> backendFlow();
            case "model", "llm", "provider" -> modelFlow();
            case "tools", "tool" -> toolFlow();
            case "config", "configuration" -> configFlow();
            default -> endToEndFlow();
        };
    }

    private String endToEndFlow() {
        return """
                End-to-end app flow
                1. User enters a prompt in frontend/src/app/app.component.html.
                2. AppComponent.send() reads the prompt and calls AgentApiService.chat().
                3. AgentApiService posts JSON to /api/agent/chat.
                4. AgentController.chat() receives AgentRequest and delegates to AgentService.run().
                5. AgentService builds the system prompt, appends tool schemas from ToolRegistry, and sends messages to the selected ModelClient.
                6. ModelClientFactory chooses the provider from agent.provider: demo, qwen, openai, or ollama.
                7. The model returns either {"final":"..."} or {"tool":"tool_name","arguments":{...}}.
                8. If a tool is requested, AgentService finds it in ToolRegistry, executes it, records ToolTrace, and sends the tool result back to the model.
                9. When the model returns a final answer, AgentService returns AgentResponse with answer, provider, and optional trace.
                10. Angular renders the answer in the chat pane and tool steps in the trace pane.
                """.strip();
    }

    private String frontendFlow() {
        return """
                Frontend flow
                - app.component.html renders the chat history, example buttons, trace toggle, text area, and trace panel.
                - app.component.ts stores UI state with Angular signals: message, traceEnabled, loading, error, messages, and latestTrace.
                - send() appends the user message locally, clears the input, and calls AgentApiService.chat(message, trace).
                - agent-api.service.ts sends POST /api/agent/chat with { message, trace }.
                - The response is appended as an agent message. If trace is present, latestTrace() exposes it to the trace panel.
                """.strip();
    }

    private String backendFlow() {
        return """
                Backend flow
                - AgentController exposes POST /api/agent/chat.
                - AgentRequest validates that message is present and under the configured size limit.
                - AgentService.run() owns the agent loop.
                - It builds messages with a system prompt and the registered tool schemas.
                - It calls the selected ModelClient for each step.
                - It parses model JSON into AgentAction.
                - It returns AgentResponse once the action contains a final answer.
                """.strip();
    }

    private String modelFlow() {
        return """
                Model provider flow
                - ModelClientFactory reads agent.provider from application.yml or environment variables.
                - provider=demo uses DemoModelClient for deterministic local routing.
                - provider=qwen uses QwenModelClient with QWEN_API_KEY, QWEN_BASE_URL, and QWEN_MODEL.
                - provider=openai uses OpenAiModelClient with OPENAI_API_KEY, OPENAI_BASE_URL, and OPENAI_MODEL.
                - provider=ollama uses OllamaModelClient with OLLAMA_BASE_URL and OLLAMA_MODEL.
                - Every provider returns plain text that AgentService expects to be JSON.
                """.strip();
    }

    private String toolFlow() {
        return """
                Tool execution flow
                - Each tool implements AgentTool with name(), description(), parameters(), and run().
                - Spring discovers tool classes as components.
                - ToolRegistry receives all AgentTool beans and stores them by tool name.
                - AgentService includes tool schemas in the system prompt.
                - If the model asks for a tool, AgentService executes it and stores the result in ToolTrace.
                - Existing tools include current_time, calculate, list_project_files, describe_code, explain_app_flow, and add_feature.
                - add_feature writes or replaces a source file inside the project so the agent can implement requested functionality.
                """.strip();
    }

    private String configFlow() {
        return """
                Configuration flow
                - application.yml defines defaults using environment variable placeholders.
                - run-app.bat loads .env into environment variables before starting Spring Boot.
                - AGENT_PROVIDER selects demo, qwen, openai, or ollama.
                - Qwen keys stay local in .env and are ignored by Git.
                - Angular can be served by Spring Boot from src/main/resources/static after npm run build.
                """.strip();
    }
}
