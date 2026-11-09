package com.hmdp.ai.controller;

import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.ai.service.AiAssistantService;
import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/ai")
public class AiAssistantController {
    @Resource
    private AiAssistantService aiAssistantService;

    @PostMapping("/chat")
    public Result chat(@RequestBody AiChatRequest request) {
        return Result.ok(aiAssistantService.chat(request));
    }
}
