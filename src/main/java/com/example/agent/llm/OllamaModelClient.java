package com.example.agent.llm;

import com.example.agent.config.AgentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class OllamaModelClient implements ModelClient {
    private final AgentProperties properties;

    public OllamaModelClient(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "ollama:" + properties.getOllama().getModel();
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        RestClient client = RestClient.builder()
                .baseUrl(properties.getOllama().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            Map<String, Object> body = client.post()
                    .uri("/api/chat")
                    .body(Map.of(
                            "model", properties.getOllama().getModel(),
                            "stream", false,
                            "messages", messages.stream()
                                    .map(message -> Map.of("role", message.role(), "content", message.content()))
                                    .toList(),
                            "options", Map.of("temperature", 0.2)
                    ))
                    .retrieve()
                    .body(Map.class);

            if (body == null) {
                throw new IllegalStateException("Empty Ollama response");
            }
            Map<?, ?> message = (Map<?, ?>) body.get("message");
            if (message == null || message.get("content") == null) {
                throw new IllegalStateException("Ollama response did not include message.content");
            }
            return String.valueOf(message.get("content"));
        } catch (RestClientException exc) {
            throw new IllegalStateException(
                    "Ollama request failed. Start Ollama and pull the model, for example: ollama pull "
                            + properties.getOllama().getModel(),
                    exc
            );
        }
    }
}
