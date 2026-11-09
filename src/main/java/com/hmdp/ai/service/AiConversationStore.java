package com.hmdp.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class AiConversationStore {
    private static final String KEY_PREFIX = "ai:conversation:";
    private static final long TTL_HOURS = 24L;
    private static final int MAX_MESSAGES = 20;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    public List<String> load(Long userId, String conversationId) {
        String json = stringRedisTemplate.opsForValue().get(key(userId, conversationId));
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load AI conversation: userId=" + userId
                    + ", conversationId=" + conversationId, exception);
        }
    }

    public void save(Long userId, String conversationId, List<String> messages) {
        try {
            List<String> bounded = messages.size() <= MAX_MESSAGES
                    ? new ArrayList<>(messages)
                    : new ArrayList<>(messages.subList(messages.size() - MAX_MESSAGES, messages.size()));
            stringRedisTemplate.opsForValue().set(key(userId, conversationId),
                    objectMapper.writeValueAsString(bounded), TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save AI conversation: userId=" + userId
                    + ", conversationId=" + conversationId, exception);
        }
    }

    private String key(Long userId, String conversationId) {
        return KEY_PREFIX + userId + ":" + conversationId;
    }
}
