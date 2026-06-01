package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CalculateTool implements AgentTool {
    @Override
    public String name() {
        return "calculate";
    }

    @Override
    public String description() {
        return "Evaluate a simple arithmetic expression. Supports numbers and +, -, *, /, %, parentheses.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("expression", "Arithmetic expression as a string.");
    }

    @Override
    public String run(Map<String, Object> arguments) {
        Object expression = arguments.get("expression");
        if (expression == null || String.valueOf(expression).isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        double result = new ExpressionParser(String.valueOf(expression)).parse();
        if (result == Math.rint(result)) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }

    private static class ExpressionParser {
        private final String text;
        private int position;

        ExpressionParser(String text) {
            this.text = text;
        }

        double parse() {
            double value = parseExpression();
            skipWhitespace();
            if (position != text.length()) {
                throw new IllegalArgumentException("Unsupported expression");
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (consume('+')) {
                    value += parseTerm();
                } else if (consume('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipWhitespace();
                if (consume('*')) {
                    value *= parseFactor();
                } else if (consume('/')) {
                    value /= parseFactor();
                } else if (consume('%')) {
                    value %= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipWhitespace();
            if (consume('+')) {
                return parseFactor();
            }
            if (consume('-')) {
                return -parseFactor();
            }
            if (consume('(')) {
                double value = parseExpression();
                if (!consume(')')) {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipWhitespace();
            int start = position;
            while (position < text.length()) {
                char current = text.charAt(position);
                if (!Character.isDigit(current) && current != '.') {
                    break;
                }
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("Expected number");
            }
            return Double.parseDouble(text.substring(start, position));
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }
    }
}
