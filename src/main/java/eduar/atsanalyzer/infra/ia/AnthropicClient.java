package eduar.atsanalyzer.infra.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eduar.atsanalyzer.exceptions.AnaliseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


import java.util.List;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.model}")
    private String model;

    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    public String enviar(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 2048,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.get("content").get(0).get("text").asText();

        } catch (Exception e) {
            log.error("falha no comunicação com a API da Anthropic: {}", e.getMessage(), e);
            throw new AnaliseException("Erro na comunicação com a API da Anthropic", e);
        }
    }
}
