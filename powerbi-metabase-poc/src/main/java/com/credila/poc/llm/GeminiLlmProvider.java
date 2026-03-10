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

@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini", matchIfMissing = true)
@Component
public class GeminiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
            .build();

    @Override
    public String complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM provider is 'gemini' but GEMINI_API_KEY is not set");
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of("maxOutputTokens", 1024)
        );
        String url = BASE_URL + "/" + model + ":generateContent?key=" + apiKey;
        String response = restClient.post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(String.class);
        return parseResponse(response);
    }

    private static String parseResponse(String response) {
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty() || !candidates.isArray()) {
                throw new IllegalStateException("Gemini API returned no candidates: " + response);
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isEmpty() || !parts.isArray()) {
                throw new IllegalStateException("Gemini API returned no content: " + response);
            }
            return parts.get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", response);
            throw new RuntimeException("Gemini API response parse failed", e);
        }
    }
}
