package com.hmdp.ai.controller;

import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.ai.service.AiAssistantService;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiStreamController {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    @Resource
    private AiAssistantService aiAssistantService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(180000L);
        emitter.onTimeout(emitter::complete);
        UserDTO user = UserHolder.getUser();
        try {
            executor.execute(() -> process(request, emitter, user));
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            log.warn("AI stream queue is full", exception);
            send(emitter, "error", "当前请求较多，请稍后再试。");
            emitter.complete();
        }
        return emitter;
    }

    @javax.annotation.PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private void process(AiChatRequest request, SseEmitter emitter, UserDTO user) {
        try {
            if (user == null) throw new IllegalStateException("AI stream requires an authenticated user");
            UserHolder.saveUser(user);
            aiAssistantService.stream(request, text -> send(emitter, "delta", text),
                    (event, data) -> send(emitter, event, data));
            send(emitter, "done", "");
            emitter.complete();
        } catch (Exception exception) {
            log.error("AI stream request failed", exception);
            try {
                send(emitter, "error", "AI 服务暂时不可用，请稍后重试。");
            } finally {
                emitter.complete();
            }
        } finally {
            UserHolder.removeUser();
        }
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to write AI SSE event: event=" + event, exception);
        }
    }
}
