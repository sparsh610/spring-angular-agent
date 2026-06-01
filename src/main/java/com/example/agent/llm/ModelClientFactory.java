package com.example.agent.llm;

import com.example.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

@Component
public class ModelClientFactory {
    private final AgentProperties properties;
    private final DemoModelClient demoModelClient;
    private final OpenAiModelClient openAiModelClient;

    public ModelClientFactory(
            AgentProperties properties,
            DemoModelClient demoModelClient,
            OpenAiModelClient openAiModelClient
    ) {
        this.properties = properties;
        this.demoModelClient = demoModelClient;
        this.openAiModelClient = openAiModelClient;
    }

    public ModelClient current() {
        if ("openai".equalsIgnoreCase(properties.getProvider())) {
            return openAiModelClient;
        }
        return demoModelClient;
    }
}
