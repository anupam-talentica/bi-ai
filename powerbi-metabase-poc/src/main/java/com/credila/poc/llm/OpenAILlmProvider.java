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

@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
@Component
public class OpenAILlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAILlmProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String URL = "https://api.openai.com/v1/chat/completions";

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
            .build();

    @Override
    public String complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM provider is 'openai' but OPENAI_API_KEY is not set");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        String response = restClient.post()
                .uri(URL)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(String.class);
        return parseResponse(response);
    }

    private static String parseResponse(String response) {
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty() || !choices.isArray()) {
                throw new IllegalStateException("OpenAI API returned no choices: " + response);
            }
            return choices.get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", response);
            throw new RuntimeException("OpenAI API response parse failed", e);
        }
    }
}
