package com.credila.poc.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "llm.provider", havingValue = "claude")
@Component
public class ClaudeLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String URL = "https://api.anthropic.com/v1/messages";

    @Value("${claude.api-key:}")
    private String apiKey;

    @Value("${claude.model:claude-3-5-sonnet-20241022}")
    private String model;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
            .build();

    @Override
    public String complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM provider is 'claude' but CLAUDE_API_KEY is not set");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        String response = restClient.post()
                .uri(URL)
                .header("x-api-key", apiKey)
                .body(body)
                .retrieve()
                .body(String.class);
        return parseResponse(response);
    }

    private static String parseResponse(String response) {
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode content = root.path("content");
            if (content.isEmpty() || !content.isArray()) {
                throw new IllegalStateException("Claude API returned no content: " + response);
            }
            return content.get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Failed to parse Claude response: {}", response);
            throw new RuntimeException("Claude API response parse failed", e);
        }
    }
}
