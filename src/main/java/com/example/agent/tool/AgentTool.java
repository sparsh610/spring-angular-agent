package com.example.agent.tool;

import java.util.Map;

public interface AgentTool {
    String name();

    String description();

    Map<String, String> parameters();

    String run(Map<String, Object> arguments);
}
