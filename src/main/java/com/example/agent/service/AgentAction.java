package com.example.agent.service;

import java.util.Map;

record AgentAction(
        String finalAnswer,
        String tool,
        Map<String, Object> arguments
) {
    boolean isFinal() {
        return finalAnswer != null && !finalAnswer.isBlank();
    }
}
