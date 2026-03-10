package com.credila.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates Metabase cards (questions) via the Metabase REST API.
 */
@Component
public class MetabaseClient {

    private static final Logger log = LoggerFactory.getLogger(MetabaseClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${metabase.base-url:}")
    private String baseUrl;

    @Value("${metabase.username:}")
    private String username;

    @Value("${metabase.password:}")
    private String password;

    @Value("${metabase.database-id:2}")
    private int databaseId;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
            .build();

    private String sessionToken;

    public void authenticate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("METABASE_URL is not set");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("METABASE_USER and METABASE_PASSWORD must be set");
        }

        String url = baseUrl.replaceAll("/$", "") + "/api/session";
        Map<String, String> credentials = Map.of(
                "username", username,
                "password", password
        );

        String response = restClient.post()
                .uri(url)
                .body(credentials)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = MAPPER.readTree(response);
            this.sessionToken = root.path("id").asText();
            log.info("Metabase session established");
        } catch (Exception e) {
            log.error("Metabase auth failed: {}", response);
            throw new RuntimeException("Metabase authentication failed", e);
        }
    }

    public String createCard(String name, String sql) {
        if (sessionToken == null) {
            throw new IllegalStateException("Call authenticate() first");
        }

        String url = baseUrl.replaceAll("/$", "") + "/api/card";

        Map<String, Object> nativeQuery = new LinkedHashMap<>();
        nativeQuery.put("query", sql);

        Map<String, Object> datasetQuery = new LinkedHashMap<>();
        datasetQuery.put("type", "native");
        datasetQuery.put("native", nativeQuery);
        datasetQuery.put("database", databaseId);

        Map<String, Object> cardRequest = new LinkedHashMap<>();
        cardRequest.put("name", name);
        cardRequest.put("dataset_query", datasetQuery);
        cardRequest.put("display", "table");
        cardRequest.put("visualization_settings", Map.of());

        String response = restClient.post()
                .uri(url)
                .header("X-Metabase-Session", sessionToken)
                .body(cardRequest)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = MAPPER.readTree(response);
            int cardId = root.path("id").asInt();
            String cardUrl = baseUrl.replaceAll("/$", "") + "/question/" + cardId;
            log.info("Created Metabase card: {}", cardUrl);
            return cardUrl;
        } catch (Exception e) {
            log.error("Failed to create card: {}", response);
            throw new RuntimeException("Metabase create card failed", e);
        }
    }
}
