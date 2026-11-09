package com.hmdp.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.ai.exception.AiApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Slf4j
@Component
public class SenseNovaClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final int maxRetries;
    private final long baseBackoffMillis;

    public SenseNovaClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${sensenova.api-base-url}") String apiBaseUrl,
            @Value("${sensenova.api-key}") String apiKey,
            @Value("${sensenova.max-retries}") int maxRetries,
            @Value("${sensenova.base-backoff-millis}") long baseBackoffMillis) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.endpoint = apiBaseUrl + "/chat/completions";
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;
        this.baseBackoffMillis = baseBackoffMillis;
    }

    public AiChatResponse chat(AiChatRequest request) {
        return chat(request, java.util.Collections.emptyList());
    }

    public AiChatResponse chat(AiChatRequest request, List<Map<String, Object>> tools) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AiApiException("SenseNova API key is not configured: set SENSENOVA_API_KEY");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", "sensenova-6.8-flash-lite");
        body.put("messages", request.getMessages());
        body.put("stream", false);
        if (!tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        endpoint, HttpMethod.POST, entity, String.class);
                return parseResponse(response.getBody());
            } catch (HttpStatusCodeException exception) {
                if (exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || attempt == maxRetries) {
                    throw apiException(exception, attempt);
                }
                long delay = retryDelayMillis(exception, attempt);
                log.warn("SenseNova request rate limited, retrying: attempt={}, delayMillis={}, status={}",
                        attempt + 1, delay, exception.getRawStatusCode());
                sleep(delay);
            } catch (ResourceAccessException exception) {
                throw new AiApiException("SenseNova request failed due to network or timeout: endpoint=" + endpoint,
                        exception);
            }
        }
        throw new AiApiException("SenseNova request exhausted retries: endpoint=" + endpoint);
    }

    public JsonNode rawChat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AiApiException("SenseNova API key is not configured: set SENSENOVA_API_KEY");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", "sensenova-6.8-flash-lite");
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("tool_choice", "auto");
        body.put("stream", false);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST,
                        new HttpEntity<>(body, headers), String.class);
                return objectMapper.readTree(response.getBody()).path("choices").path(0).path("message");
            } catch (HttpStatusCodeException exception) {
                if (exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || attempt == maxRetries) {
                    throw apiException(exception, attempt);
                }
                long delay = retryDelayMillis(exception, attempt);
                log.warn("SenseNova tool request rate limited, retrying: attempt={}, delayMillis={}, status={}",
                        attempt + 1, delay, exception.getRawStatusCode());
                sleep(delay);
            } catch (Exception exception) {
                throw new AiApiException("SenseNova tool request failed: endpoint=" + endpoint, exception);
            }
        }
        throw new AiApiException("SenseNova tool request exhausted retries: endpoint=" + endpoint);
    }

    public void stream(AiChatRequest request, Consumer<String> onDelta, Runnable onCompleted) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AiApiException("SenseNova API key is not configured: set SENSENOVA_API_KEY");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", "sensenova-6.8-flash-lite");
        body.put("messages", request.getMessages());
        body.put("stream", true);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            restTemplate.execute(endpoint, HttpMethod.POST, requestFactory -> {
                requestFactory.getHeaders().putAll(headers);
                objectMapper.writeValue(requestFactory.getBody(), body);
            }, response -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new AiApiException("SenseNova stream failed: status=" + response.getRawStatusCode());
                }
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.getBody(), java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode delta = objectMapper.readTree(data).path("choices").path(0)
                            .path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        onDelta.accept(delta.asText());
                    }
                }
                return null;
            });
            onCompleted.run();
        } catch (HttpStatusCodeException exception) {
            throw apiException(exception, 0);
        } catch (Exception exception) {
            throw new AiApiException("SenseNova stream request failed: endpoint=" + endpoint, exception);
        }
    }

    public void streamMessages(java.util.List<java.util.Map<String, Object>> messages,
                               Consumer<String> onDelta, Runnable onCompleted) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AiApiException("SenseNova API key is not configured: set SENSENOVA_API_KEY");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", "sensenova-6.8-flash-lite");
        body.put("messages", messages);
        body.put("stream", true);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
        final boolean[] received = {false};
        try {
            restTemplate.execute(endpoint, HttpMethod.POST, requestFactory -> {
                requestFactory.getHeaders().putAll(headers);
                objectMapper.writeValue(requestFactory.getBody(), body);
            }, response -> {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.getBody(), java.nio.charset.StandardCharsets.UTF_8));
                String line;
                boolean finished = false;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) { finished = true; break; }
                    JsonNode delta = objectMapper.readTree(data).path("choices").path(0)
                            .path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) { received[0] = true; onDelta.accept(delta.asText()); }
                }
                if (!finished) throw new AiApiException("SenseNova stream ended before completion: endpoint=" + endpoint);
                return null;
            });
            onCompleted.run();
            return;
        } catch (HttpStatusCodeException exception) {
            if (received[0] || exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || attempt == maxRetries) {
                throw apiException(exception, attempt);
            }
            long delay = retryDelayMillis(exception, attempt);
            log.warn("SenseNova stream rate limited, retrying: attempt={}, delayMillis={}", attempt + 1, delay);
            sleep(delay);
        } catch (Exception exception) {
            throw new AiApiException("SenseNova stream request failed: endpoint=" + endpoint, exception);
        }
        }
    }

    private AiChatResponse parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            String model = root.path("model").asText("");
            String content = choice.path("message").path("content").asText(null);
            if (content == null) {
                throw new AiApiException("SenseNova response missing choices[0].message.content: body=" + body);
            }
            return new AiChatResponse(model, content);
        } catch (AiApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiApiException("Unable to parse SenseNova response: body=" + body, exception);
        }
    }

    private AiApiException apiException(HttpStatusCodeException exception, int attempt) {
        return new AiApiException("SenseNova API request failed: endpoint=" + endpoint
                + ", status=" + exception.getRawStatusCode() + ", attempt=" + attempt
                + ", response=" + exception.getResponseBodyAsString());
    }

    private long retryDelayMillis(HttpStatusCodeException exception, int attempt) {
        String retryAfter = exception.getResponseHeaders() == null
                ? null : exception.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Math.max(0L, Long.parseLong(retryAfter) * 1000L);
            } catch (NumberFormatException ignored) {
                log.warn("Invalid SenseNova Retry-After header: value={}", retryAfter);
            }
        }
        long exponential = baseBackoffMillis * (1L << Math.min(attempt, 6));
        return exponential + ThreadLocalRandom.current().nextLong(baseBackoffMillis + 1L);
    }

    private void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiApiException("SenseNova retry interrupted: delayMillis=" + delayMillis, exception);
        }
    }
}
