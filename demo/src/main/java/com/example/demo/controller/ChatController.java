package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.PlatformMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final PlatformMockService platformMockService;

    public ChatController(PlatformMockService platformMockService) {
        this.platformMockService = platformMockService;
    }

    @PostMapping("/sessions")
    public WpsResult<Map<String, Object>> createSession() {
        return WpsResult.ok(platformMockService.createChatSession());
    }

    @GetMapping("/sessions")
    public WpsResult<List<Map<String, Object>>> listSessions() {
        return WpsResult.ok(platformMockService.listChatSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    public WpsResult<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return WpsResult.ok(platformMockService.getChatSession(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public WpsResult<Map<String, Object>> sendMessage(@PathVariable String sessionId, @RequestBody Map<String, Object> request) {
        return WpsResult.ok(platformMockService.sendChatMessage(sessionId, request));
    }
}
