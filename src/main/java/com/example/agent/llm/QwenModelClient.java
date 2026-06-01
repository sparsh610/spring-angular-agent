package com.example.agent.llm;

import com.example.agent.config.AgentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class QwenModelClient implements ModelClient {
    private final AgentProperties properties;

    public QwenModelClient(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "qwen:" + properties.getQwen().getModel();
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        String apiKey = properties.getQwen().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("QWEN_API_KEY is required when AGENT_PROVIDER=qwen");
        }

        RestClient client = RestClient.builder()
                .baseUrl(properties.getQwen().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = client.post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", properties.getQwen().getModel(),
                        "temperature", 0.2,
                        "messages", messages.stream()
                                .map(message -> Map.of("role", message.role(), "content", message.content()))
                                .toList()
                ))
                .retrieve()
                .body(Map.class);

        if (body == null) {
            throw new IllegalStateException("Empty Qwen response");
        }
        List<?> choices = (List<?>) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Qwen response did not include choices");
        }
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return String.valueOf(message.get("content"));
    }
}
