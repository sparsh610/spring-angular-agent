package com.example.agent.api;

import java.util.Map;

public record ToolTrace(
        int step,
        String type,
        String tool,
        Map<String, Object> arguments,
        String result
) {
}
