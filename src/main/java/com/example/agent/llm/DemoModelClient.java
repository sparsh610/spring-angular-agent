package com.example.agent.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DemoModelClient implements ModelClient {
    private static final Pattern CALCULATION = Pattern.compile("([0-9][0-9+\\-*/(). %]+[0-9])");

    @Override
    public String providerName() {
        return "demo";
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        String latest = latestUserMessage(messages);
        String lowered = latest.toLowerCase(Locale.ROOT);

        String toolResult = latestToolResult(messages);
        if (!toolResult.isBlank()) {
            return "{\"final\":\"Tool result: " + escape(toolResult) + "\"}";
        }
        if (lowered.contains("time") || lowered.contains("date")) {
            return "{\"tool\":\"current_time\",\"arguments\":{}}";
        }
        Matcher matcher = CALCULATION.matcher(latest);
        if (lowered.contains("calculate") || matcher.find()) {
            String expression = matcher.find(0) ? matcher.group(1) : latest.replaceAll("(?i)calculate", "").trim();
            return "{\"tool\":\"calculate\",\"arguments\":{\"expression\":\"" + escape(expression) + "\"}}";
        }
        if (lowered.contains("files") || lowered.contains("list")) {
            return "{\"tool\":\"list_project_files\",\"arguments\":{\"path\":\".\"}}";
        }
        return "{\"final\":\"Demo agent received your message. Ask for time, a calculation, or project files to see tool use.\"}";
    }

    private static String latestUserMessage(List<ChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if ("user".equals(message.role())) {
                return message.content();
            }
        }
        return "";
    }

    private static String latestToolResult(List<ChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            String content = messages.get(index).content();
            if (content.startsWith("Tool result for ")) {
                int separator = content.indexOf(":\n");
                return separator >= 0 ? content.substring(separator + 2).strip() : content;
            }
        }
        return "";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
