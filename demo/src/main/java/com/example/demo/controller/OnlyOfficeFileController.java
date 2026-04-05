package com.example.demo.controller;

import com.example.demo.dto.OnlyOfficeH5GamePrepareRequest;
import com.example.demo.dto.WpsFileRegisterRequest;
import com.example.demo.dto.WpsResult;
import com.example.demo.service.OnlyOfficeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/onlyoffice/files")
public class OnlyOfficeFileController {

    private final OnlyOfficeService onlyOfficeService;

    public OnlyOfficeFileController(OnlyOfficeService onlyOfficeService) {
        this.onlyOfficeService = onlyOfficeService;
    }

    @PostMapping("/register")
    public WpsResult<Map<String, Object>> register(@RequestBody WpsFileRegisterRequest request) {
        return WpsResult.ok(onlyOfficeService.registerExistingFile(request, baseUrl()));
    }

    @GetMapping("/{fileId}/editor-config")
    public WpsResult<Map<String, Object>> editorConfig(
            @PathVariable String fileId,
            @RequestParam(value = "userId", defaultValue = "404") String userId,
            @RequestParam(value = "mode", defaultValue = "") String mode
    ) {
        return WpsResult.ok(onlyOfficeService.getEditorConfig(fileId, userId, mode, baseUrl()));
    }

    @GetMapping("/{fileId}/download-info")
    public WpsResult<Map<String, Object>> downloadInfo(@PathVariable String fileId) {
        return WpsResult.ok(onlyOfficeService.getDownloadInfo(fileId));
    }

    @PostMapping("/mock-preview")
    public WpsResult<Map<String, Object>> mockPreview(@RequestBody(required = false) Map<String, Object> request) {
        return WpsResult.ok(onlyOfficeService.buildMockPreviewResponse(request, baseUrl()));
    }

    @GetMapping("/mock-preview-content/{fileId}")
    public ResponseEntity<byte[]> mockPreviewContent(@PathVariable String fileId) {
        byte[] body = ("mock preview file: " + fileId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeFileName(fileId) + ".pptx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @PostMapping("/{fileId}/h5-games/prepare")
    public WpsResult<Map<String, Object>> prepareH5Game(
            @PathVariable String fileId,
            @RequestBody OnlyOfficeH5GamePrepareRequest request
    ) {
        return WpsResult.ok(onlyOfficeService.prepareH5GameInsertion(fileId, request, baseUrl()));
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable String fileId,
            @RequestParam(value = "version", required = false) Long version
    ) {
        OnlyOfficeService.FileContent content = onlyOfficeService.getFileContent(fileId, version);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFileName(content.fileName()) + "\"")
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.content());
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        return fileName.replace("\"", "");
    }
}
