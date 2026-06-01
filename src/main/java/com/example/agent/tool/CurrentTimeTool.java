package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class CurrentTimeTool implements AgentTool {
    @Override
    public String name() {
        return "current_time";
    }

    @Override
    public String description() {
        return "Return the current local date and time.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of();
    }

    @Override
    public String run(Map<String, Object> arguments) {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
