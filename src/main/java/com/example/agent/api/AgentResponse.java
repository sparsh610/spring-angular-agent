package com.example.agent.api;

import java.util.List;

public record AgentResponse(
        String answer,
        String provider,
        List<ToolTrace> trace
) {
}
