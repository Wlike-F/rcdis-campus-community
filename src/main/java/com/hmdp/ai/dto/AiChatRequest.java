package com.hmdp.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatRequest {
    private String conversationId;
    private List<AiMessage> messages;
    private AiLocation location;
}
