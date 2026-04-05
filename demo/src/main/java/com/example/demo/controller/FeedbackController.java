package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.PlatformMockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final PlatformMockService platformMockService;

    public FeedbackController(PlatformMockService platformMockService) {
        this.platformMockService = platformMockService;
    }

    @PostMapping
    public WpsResult<Map<String, Object>> submit(@RequestBody Map<String, Object> request) {
        return WpsResult.ok(platformMockService.submitFeedback(request));
    }

    @PostMapping("/files/upload")
    public WpsResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return WpsResult.ok(platformMockService.uploadFeedbackFile(file, baseUrl()));
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
