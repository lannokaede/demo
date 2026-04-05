package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.PlatformMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final PlatformMockService platformMockService;

    public TemplateController(PlatformMockService platformMockService) {
        this.platformMockService = platformMockService;
    }

    @GetMapping("/categories")
    public WpsResult<List<Map<String, Object>>> categories() {
        return WpsResult.ok(platformMockService.listTemplateCategories());
    }

    @GetMapping
    public WpsResult<List<Map<String, Object>>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return WpsResult.ok(platformMockService.listTemplates(category, keyword));
    }

    @GetMapping("/{templateId}")
    public WpsResult<Map<String, Object>> detail(@PathVariable Integer templateId) {
        return WpsResult.ok(platformMockService.getTemplate(templateId));
    }

    @PostMapping("/upload")
    public WpsResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return WpsResult.ok(platformMockService.uploadTemplate(file, baseUrl()));
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
