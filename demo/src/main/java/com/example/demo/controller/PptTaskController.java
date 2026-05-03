package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.OnlyOfficeService;
import com.example.demo.service.PptTaskService;
import com.example.demo.service.SuperPptClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/ppt")
public class PptTaskController {

    private final PptTaskService pptTaskService;
    private final OnlyOfficeService onlyOfficeService;

    public PptTaskController(PptTaskService pptTaskService, OnlyOfficeService onlyOfficeService) {
        this.pptTaskService = pptTaskService;
        this.onlyOfficeService = onlyOfficeService;
    }

    @GetMapping("/healthz")
    public WpsResult<Object> healthz() {
        return WpsResult.ok(pptTaskService.healthz());
    }

    @GetMapping("/tasks")
    public WpsResult<Object> listTasks() {
        return WpsResult.ok(pptTaskService.listTasks(baseUrl()));
    }

    @GetMapping("/sessions")
    public WpsResult<Object> listSessions() {
        return WpsResult.ok(pptTaskService.listSessions(baseUrl()));
    }

    @PostMapping(value = {"/tasks", "/generate"})
    public WpsResult<Map<String, Object>> createTask(@RequestBody(required = false) Map<String, Object> request) {
        return WpsResult.ok(pptTaskService.createTask(request, baseUrl()));
    }

    @PostMapping("/sessions")
    public WpsResult<Map<String, Object>> createSession(@RequestBody(required = false) Map<String, Object> request) {
        return WpsResult.ok(pptTaskService.createSession(request, baseUrl()));
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WpsResult<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "asset_type", required = false) String assetType,
            @RequestParam(value = "role", required = false) String role
    ) {
        return WpsResult.ok(pptTaskService.uploadFile(file, assetType, role));
    }

    @GetMapping("/uploads/{uploadId}")
    public WpsResult<Map<String, Object>> getUpload(@PathVariable String uploadId) {
        return WpsResult.ok(pptTaskService.getUpload(uploadId));
    }

    @GetMapping("/tasks/{taskId}")
    public WpsResult<Map<String, Object>> getTask(@PathVariable String taskId) {
        return WpsResult.ok(pptTaskService.getTask(taskId, baseUrl()));
    }

    @GetMapping("/sessions/{sessionId}")
    public WpsResult<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return WpsResult.ok(pptTaskService.getSession(sessionId, baseUrl()));
    }

    @PostMapping("/sessions/{sessionId}/assets")
    public WpsResult<Map<String, Object>> appendSessionAssets(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return WpsResult.ok(pptTaskService.appendSessionAssets(sessionId, request, baseUrl()));
    }

    @GetMapping("/tasks/{taskId}/download-info")
    public WpsResult<Map<String, Object>> getDownloadInfo(@PathVariable String taskId) {
        return WpsResult.ok(pptTaskService.getDownloadInfo(taskId, baseUrl()));
    }

    @PostMapping("/sessions/{sessionId}/clarifications")
    public WpsResult<Map<String, Object>> submitClarifications(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return WpsResult.ok(pptTaskService.submitClarifications(sessionId, request, baseUrl()));
    }

    @GetMapping("/sessions/{sessionId}/outline")
    public WpsResult<Map<String, Object>> getOutline(@PathVariable String sessionId) {
        return WpsResult.ok(pptTaskService.getOutline(sessionId, baseUrl()));
    }

    @PostMapping("/sessions/{sessionId}/outline/review")
    public WpsResult<Map<String, Object>> reviewOutline(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return WpsResult.ok(pptTaskService.reviewOutline(sessionId, request, baseUrl()));
    }

    @GetMapping("/sessions/{sessionId}/draft")
    public WpsResult<Map<String, Object>> getDraft(@PathVariable String sessionId) {
        return WpsResult.ok(pptTaskService.getDraft(sessionId, baseUrl()));
    }

    @GetMapping("/sessions/{sessionId}/revisions")
    public WpsResult<Map<String, Object>> getRevisions(@PathVariable String sessionId) {
        return WpsResult.ok(pptTaskService.getRevisions(sessionId, baseUrl()));
    }

    @PostMapping("/sessions/{sessionId}/slides/edit")
    public WpsResult<Map<String, Object>> editSlides(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return WpsResult.ok(pptTaskService.editSlides(sessionId, request, baseUrl()));
    }

    @PostMapping("/sessions/{sessionId}/draft/revise")
    public WpsResult<Map<String, Object>> reviseDraft(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return WpsResult.ok(pptTaskService.reviseDraft(sessionId, request, baseUrl()));
    }

    @PostMapping("/sessions/{sessionId}/finalize")
    public WpsResult<Map<String, Object>> finalizeSession(@PathVariable String sessionId) {
        return WpsResult.ok(pptTaskService.finalizeSession(sessionId, baseUrl()));
    }

    @GetMapping("/tasks/{taskId}/artifacts")
    public WpsResult<Map<String, Object>> getTaskArtifacts(@PathVariable String taskId) {
        return WpsResult.ok(pptTaskService.getArtifactsByTask(taskId, baseUrl()));
    }

    @GetMapping("/sessions/{sessionId}/artifacts")
    public WpsResult<Map<String, Object>> getSessionArtifacts(@PathVariable String sessionId) {
        return WpsResult.ok(pptTaskService.getArtifactsBySession(sessionId, baseUrl()));
    }

    @GetMapping("/sessions/{sessionId}/onlyoffice-preview")
    public WpsResult<Map<String, Object>> getSessionOnlyOfficePreview(
            @PathVariable String sessionId,
            @RequestParam(value = "artifactName", required = false) String artifactName,
            @RequestParam(value = "userId", defaultValue = "404") String userId
    ) {
        Map<String, Object> previewSource = pptTaskService.getOnlyOfficePreviewSource(sessionId, artifactName, baseUrl());
        return WpsResult.ok(onlyOfficeService.buildRemotePreviewConfig(
                String.valueOf(previewSource.get("fileId")),
                String.valueOf(previewSource.get("fileName")),
                String.valueOf(previewSource.get("downloadUrl")),
                userId,
                baseUrl()
        ));
    }

    @GetMapping("/tasks/{taskId}/onlyoffice-preview")
    public WpsResult<Map<String, Object>> getTaskOnlyOfficePreview(
            @PathVariable String taskId,
            @RequestParam(value = "artifactName", required = false) String artifactName,
            @RequestParam(value = "userId", defaultValue = "404") String userId
    ) {
        return getSessionOnlyOfficePreview(taskId, artifactName, userId);
    }

    @GetMapping("/sessions/{sessionId}/plugins/digital-human")
    public WpsResult<Map<String, Object>> getDigitalHumanPlugin(@PathVariable String sessionId) {
        return WpsResult.ok(pptTaskService.getDigitalHumanPlugin(sessionId, baseUrl()));
    }

    @PostMapping("/sessions/{sessionId}/plugins/digital-human")
    public WpsResult<Map<String, Object>> triggerDigitalHumanPlugin(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return WpsResult.ok(pptTaskService.triggerDigitalHumanPlugin(sessionId, request, baseUrl()));
    }

    @GetMapping("/tasks/{taskId}/download/{artifactName}")
    public ResponseEntity<byte[]> downloadTaskArtifact(
            @PathVariable String taskId,
            @PathVariable String artifactName
    ) {
        return toDownloadResponse(pptTaskService.downloadTaskArtifact(taskId, artifactName), artifactName);
    }

    @GetMapping("/sessions/{sessionId}/download/{artifactName}")
    public ResponseEntity<byte[]> downloadSessionArtifact(
            @PathVariable String sessionId,
            @PathVariable String artifactName
    ) {
        return toDownloadResponse(pptTaskService.downloadSessionArtifact(sessionId, artifactName), artifactName);
    }

    private ResponseEntity<byte[]> toDownloadResponse(SuperPptClient.DownloadedFile downloadedFile, String fallbackFileName) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        String contentDisposition = downloadedFile.contentDisposition();
        if (contentDisposition == null || contentDisposition.isBlank()) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeFileName(fallbackFileName) + "\"");
        } else {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        }

        String contentType = downloadedFile.contentType();
        if (contentType == null || contentType.isBlank()) {
            builder.contentType(MediaType.APPLICATION_OCTET_STREAM);
        } else {
            builder.contentType(MediaType.parseMediaType(contentType));
        }
        return builder.body(downloadedFile.content());
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "download.bin";
        }
        return value.replace("\"", "");
    }
}
