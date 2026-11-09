package com.hmdp.ai.service;

import com.hmdp.ai.client.SenseNovaClient;
import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.ai.dto.AiLocation;
import com.hmdp.ai.dto.AiMessage;
import com.hmdp.ai.tool.AiToolRegistry;
import com.hmdp.utils.UserHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class AiAssistantService {
    @Resource
    private SenseNovaClient senseNovaClient;
    @Resource
    private AiToolRegistry toolRegistry;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private AiConversationStore conversationStore;

    public AiChatResponse chat(AiChatRequest request) {
        return chat(request, (event, data) -> { });
    }

    public AiChatResponse chat(AiChatRequest request, BiConsumer<String, String> eventConsumer) {
        validateRequest(request);
        String conversationId = conversationId(request);
        Long userId = currentUserId();
        List<Map<String, Object>> messages = buildMessages(request, conversationId, userId);
        for (int round = 0; round < 3; round++) {
            JsonNode assistant = senseNovaClient.rawChat(messages, toolRegistry.definitions());
            JsonNode toolCalls = assistant.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String content = assistant.path("content").asText("");
                List<String> saved = new ArrayList<>(conversationStore.load(userId, conversationId));
                request.getMessages().forEach(message -> saved.add(message.getRole() + ": " + message.getContent()));
                saved.add("assistant: " + content);
                conversationStore.save(userId, conversationId, saved);
                return new AiChatResponse("sensenova-6.8-flash-lite", content);
            }
            Map<String, Object> assistantMessage = new HashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", assistant.path("content").isNull() ? null : assistant.path("content").asText());
            assistantMessage.put("tool_calls", objectMapper.convertValue(toolCalls, List.class));
            messages.add(assistantMessage);
            for (JsonNode toolCall : toolCalls) {
                String name = toolCall.path("function").path("name").asText("");
                String arguments = toolCall.path("function").path("arguments").asText("{}");
                eventConsumer.accept("tool_start", name);
                String result = toolRegistry.execute(name, arguments, request.getLocation());
                eventConsumer.accept("tool_result", name);
                Map<String, Object> toolMessage = new HashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", toolCall.path("id").asText(""));
                toolMessage.put("content", result);
                messages.add(toolMessage);
            }
        }
        throw new IllegalStateException("AI tool call exceeded maximum rounds");
    }

    public void stream(AiChatRequest request, java.util.function.Consumer<String> onDelta,
                       java.util.function.BiConsumer<String, String> onToolEvent) {
        validateRequest(request);
        String conversationId = conversationId(request);
        Long userId = currentUserId();
        List<Map<String, Object>> messages = buildMessages(request, conversationId, userId);
        for (int round = 0; round < 3; round++) {
            JsonNode assistant = senseNovaClient.rawChat(messages, toolRegistry.definitions());
            JsonNode toolCalls = assistant.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                StringBuilder answer = new StringBuilder();
                senseNovaClient.streamMessages(messages, delta -> {
                    answer.append(delta);
                    onDelta.accept(delta);
                }, () -> { });
                saveConversation(request, conversationId, answer.toString());
                return;
            }
            Map<String, Object> assistantMessage = new HashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", assistant.path("content").isNull() ? null : assistant.path("content").asText());
            assistantMessage.put("tool_calls", objectMapper.convertValue(toolCalls, List.class));
            messages.add(assistantMessage);
            for (JsonNode toolCall : toolCalls) {
                String name = toolCall.path("function").path("name").asText("");
                String arguments = toolCall.path("function").path("arguments").asText("{}");
                onToolEvent.accept("tool_start", name);
                String result = toolRegistry.execute(name, arguments, request.getLocation());
                onToolEvent.accept("tool_result", name);
                if ("query_nearby_shops".equals(name) || "query_shop".equals(name)) {
                    onToolEvent.accept("shops", shopCards(result));
                }
                Map<String, Object> toolMessage = new HashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", toolCall.path("id").asText(""));
                toolMessage.put("content", result);
                messages.add(toolMessage);
            }
        }
        throw new IllegalStateException("AI tool call exceeded maximum rounds");
    }

    private String shopCards(String result) {
        try {
            JsonNode data = objectMapper.readTree(result).path("data");
            List<Map<String, Object>> cards = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode shop : data) {
                    addShopCard(cards, shop);
                }
            } else if (data.isObject()) {
                addShopCard(cards, data);
            }
            return objectMapper.writeValueAsString(cards);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to parse shop tool result", exception);
        }
    }

    private void addShopCard(List<Map<String, Object>> cards, JsonNode shop) {
        if (!shop.path("id").canConvertToLong() || !shop.path("name").isTextual()) {
            return;
        }
        Map<String, Object> card = new HashMap<>();
        card.put("id", shop.path("id").asLong());
        card.put("name", shop.path("name").asText());
        if (shop.path("distance").isNumber()) {
            card.put("distance", shop.path("distance").asDouble());
        }
        cards.add(card);
    }

    private List<Map<String, Object>> buildMessages(AiChatRequest request, String conversationId, Long userId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是RCDIS沈阳药科大学周边生活助手。商户工具仅返回公开POI资料，位置为近似值，营业状态可能变化，推荐时请提醒用户出行前核实；优惠券为演示数据，不能当作真实优惠。没有可靠数据时直接说明无法确认。使用中文，简洁具体。");
        messages.add(systemMessage);
        List<String> history = conversationStore.load(userId, conversationId);
        history.forEach(item -> {
            int separator = item.indexOf(": ");
            if (separator > 0 && (item.startsWith("user: ") || item.startsWith("assistant: "))) {
                Map<String, Object> historyMessage = new HashMap<>();
                historyMessage.put("role", item.substring(0, separator));
                historyMessage.put("content", item.substring(separator + 2));
                messages.add(historyMessage);
            }
        });
        if (request.getLocation() != null && request.getLocation().getX() != null
                && request.getLocation().getY() != null) {
            systemMessage.put("content", systemMessage.get("content")
                    + " 用户已提供沈阳附近的位置，可用于附近检索，不要向用户暴露坐标数值。");
        }
        request.getMessages().forEach(message -> {
            Map<String, Object> item = new HashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            messages.add(item);
        });
        return messages;
    }

    private void validateRequest(AiChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().size() != 1) {
            throw new IllegalArgumentException("AI request must contain exactly one user message");
        }
        AiMessage message = request.getMessages().get(0);
        if (message == null || !"user".equals(message.getRole()) || message.getContent() == null
                || message.getContent().trim().isEmpty() || message.getContent().length() > 4000) {
            throw new IllegalArgumentException("AI user message must contain between 1 and 4000 characters");
        }
        String id = request.getConversationId();
        if (id != null && !id.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("AI conversation id must contain only letters, digits, underscores or hyphens");
        }
    }

    private String conversationId(AiChatRequest request) {
        String conversationId = request.getConversationId();
        return conversationId == null || conversationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : conversationId;
    }

    private Long currentUserId() {
        if (UserHolder.getUser() == null) {
            throw new IllegalStateException("AI conversation requires an authenticated user");
        }
        return UserHolder.getUser().getId();
    }

    private void streamFinalAnswer(List<Map<String, Object>> messages, String answer,
                                   java.util.function.Consumer<String> onDelta) {
        Map<String, Object> assistantMessage = new HashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", answer);
        messages.add(assistantMessage);
        senseNovaClient.streamMessages(messages, onDelta, () -> { });
    }

    private void saveConversation(AiChatRequest request, String conversationId, String answer) {
        List<String> saved = new ArrayList<>(conversationStore.load(currentUserId(), conversationId));
        request.getMessages().forEach(message -> saved.add(message.getRole() + ": " + message.getContent()));
        saved.add("assistant: " + answer);
        conversationStore.save(currentUserId(), conversationId, saved);
    }
}
