package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.PlatformMockService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/files")
public class KnowledgeController {

    private final PlatformMockService platformMockService;

    public KnowledgeController(PlatformMockService platformMockService) {
        this.platformMockService = platformMockService;
    }

    @GetMapping
    public WpsResult<List<Map<String, Object>>> list() {
        return WpsResult.ok(platformMockService.listKnowledgeFiles());
    }

    @PostMapping("/upload")
    public WpsResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return WpsResult.ok(platformMockService.uploadKnowledgeFile(file, baseUrl()));
    }

    @GetMapping("/{fileId}")
    public WpsResult<Map<String, Object>> detail(@PathVariable String fileId) {
        return WpsResult.ok(platformMockService.getKnowledgeFile(fileId));
    }

    @DeleteMapping("/{fileId}")
    public WpsResult<Map<String, Object>> delete(@PathVariable String fileId) {
        return WpsResult.ok(platformMockService.deleteKnowledgeFile(fileId));
    }

    @PostMapping("/{fileId}/quote-to-chat")
    public WpsResult<Map<String, Object>> quoteToChat(@PathVariable String fileId, @RequestBody Map<String, String> request) {
        return WpsResult.ok(platformMockService.quoteKnowledgeFileToChat(fileId, request.get("sessionId")));
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
