package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.PlatformMockService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final PlatformMockService platformMockService;

    public FileController(PlatformMockService platformMockService) {
        this.platformMockService = platformMockService;
    }

    @PostMapping("/upload")
    public WpsResult<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "userId", required = false) String userId
    ) {
        return WpsResult.ok(platformMockService.uploadFile(file, bizType, userId, baseUrl()));
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String fileId) {
        PlatformMockService.FileContent fileContent = platformMockService.getFileContent(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileContent.fileName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(fileContent.contentType()))
                .body(fileContent.content());
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
