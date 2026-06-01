package com.example.agent.llm;

import java.util.List;

public interface ModelClient {
    String providerName();

    String complete(List<ChatMessage> messages);
}
