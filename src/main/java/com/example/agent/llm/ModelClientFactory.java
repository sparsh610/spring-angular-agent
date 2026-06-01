package com.example.agent.llm;

import com.example.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

@Component
public class ModelClientFactory {
    private final AgentProperties properties;
    private final DemoModelClient demoModelClient;
    private final OllamaModelClient ollamaModelClient;
    private final OpenAiModelClient openAiModelClient;

    public ModelClientFactory(
            AgentProperties properties,
            DemoModelClient demoModelClient,
            OllamaModelClient ollamaModelClient,
            OpenAiModelClient openAiModelClient
    ) {
        this.properties = properties;
        this.demoModelClient = demoModelClient;
        this.ollamaModelClient = ollamaModelClient;
        this.openAiModelClient = openAiModelClient;
    }

    public ModelClient current() {
        if ("openai".equalsIgnoreCase(properties.getProvider())) {
            return openAiModelClient;
        }
        if ("ollama".equalsIgnoreCase(properties.getProvider())) {
            return ollamaModelClient;
        }
        return demoModelClient;
    }
}
