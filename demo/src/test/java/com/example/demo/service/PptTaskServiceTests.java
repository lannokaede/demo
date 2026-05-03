package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptTaskServiceTests {

    @Test
    void shouldCreateTaskFromLegacyPromptAndAddFrontendLinks() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        PptTaskService service = new PptTaskService(client, null);

        Map<String, Object> result = service.createTask(Map.of(
                "prompt", "生成一份 AI 教学 PPT",
                "pipelineId", "demo_001",
                "runtimeMode", "prod-like",
                "forceRestart", true
        ), "http://localhost:8080");

        assertEquals("生成一份 AI 教学 PPT", client.lastCreateSessionRequest.get("user_input"));
        @SuppressWarnings("unchecked")
        Map<String, Object> pipelineOptions = (Map<String, Object>) client.lastCreateSessionRequest.get("pipeline_options");
        assertEquals("demo_001", pipelineOptions.get("pipeline_id"));
        assertEquals("prod-like", pipelineOptions.get("runtime_mode"));
        assertEquals(true, pipelineOptions.get("force_restart"));
        assertEquals("demo-session-001", result.get("taskId"));
        assertEquals("demo-session-001", result.get("sessionId"));
        assertTrue(String.valueOf(result.get("pollUrl")).endsWith("/api/ppt/tasks/demo-session-001"));
        assertTrue(String.valueOf(result.get("sessionUrl")).endsWith("/api/ppt/sessions/demo-session-001"));
    }

    @Test
    void shouldBuildUserAssetsFromChatSessionAttachments() {
        PlatformMockService platformMockService = new PlatformMockService();
        Map<String, Object> session = platformMockService.createChatSession();
        String sessionId = String.valueOf(session.get("sessionId"));
        Map<String, Object> uploaded = platformMockService.uploadFile(
                new MockMultipartFile("file", "outline.pdf", "application/pdf", "outline".getBytes()),
                "chat",
                "u100",
                "http://localhost:8080"
        );
        String fileId = String.valueOf(uploaded.get("fileId"));
        platformMockService.sendChatMessage(sessionId, Map.of(
                "content", "请基于我上传的资料生成课件",
                "attachments", List.of(Map.of("fileId", fileId))
        ));

        CapturingSuperPptClient client = new CapturingSuperPptClient();
        PptTaskService service = new PptTaskService(client, platformMockService);
        service.createTask(Map.of("sessionId", sessionId), "http://localhost:8080");

        assertTrue(String.valueOf(client.lastCreateSessionRequest.get("user_input")).contains("user: 请基于我上传的资料生成课件"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> userAssets = (List<Map<String, Object>>) client.lastCreateSessionRequest.get("user_assets");
        assertEquals(1, userAssets.size());
        assertEquals("pdf", userAssets.getFirst().get("type"));
        assertEquals("upl-001", userAssets.getFirst().get("upload_id"));
        assertEquals("handout", userAssets.getFirst().get("role"));
        assertEquals(1, client.uploadCalls.size());
        assertEquals("outline.pdf", client.uploadCalls.getFirst().fileName());
    }

    @Test
    void shouldKeepProvidedUserAssetsWithoutReuploading() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        PptTaskService service = new PptTaskService(client, null);

        service.createSession(Map.of(
                "user_input", "请生成一份课程 PPT",
                "user_assets", List.of(Map.of(
                        "type", "pdf",
                        "upload_id", "upl-existing-001",
                        "role", "handout"
                ))
        ), "http://localhost:8080");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> userAssets = (List<Map<String, Object>>) client.lastCreateSessionRequest.get("user_assets");
        assertEquals(1, userAssets.size());
        assertEquals("upl-existing-001", userAssets.getFirst().get("upload_id"));
        assertTrue(client.uploadCalls.isEmpty());
    }

    @Test
    void shouldAppendAssetsToExistingSession() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        PptTaskService service = new PptTaskService(client, null);

        Map<String, Object> result = service.appendSessionAssets("demo-session-001", Map.of(
                "instructions", "结合新上传的 PDF 补充案例",
                "user_assets", List.of(Map.of(
                        "uploadId", "upl-existing-001"
                ))
        ), "http://localhost:8080");

        assertEquals("demo-session-001", client.lastAppendSessionAssetsSessionId);
        assertEquals("demo-session-001", result.get("sessionId"));
        assertTrue(String.valueOf(result.get("assetsUrl")).endsWith("/api/ppt/sessions/demo-session-001/assets"));
        assertEquals("结合新上传的 PDF 补充案例", client.lastAppendSessionAssetsRequest.get("instructions"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> userAssets = (List<Map<String, Object>>) client.lastAppendSessionAssetsRequest.get("user_assets");
        assertEquals(1, userAssets.size());
        assertEquals("document", userAssets.getFirst().get("type"));
        assertEquals("upl-existing-001", userAssets.getFirst().get("upload_id"));
    }

    @Test
    void shouldReturnDraftPreviewEntryAfterReviseDraft() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        PptTaskService service = new PptTaskService(client, null);

        Map<String, Object> result = service.reviseDraft("demo-session-001", Map.of(
                "instructions", "把第三页讲得更口语化"
        ), "http://localhost:8080");

        assertEquals("demo-session-001", result.get("sessionId"));
        assertEquals(
                "http://localhost:8080/api/ppt/sessions/demo-session-001/onlyoffice-preview?artifactName=draft_pptx",
                result.get("draftOnlyofficePreviewUrl")
        );
        assertEquals(
                "http://localhost:8080/api/ppt/sessions/demo-session-001/onlyoffice-preview",
                result.get("onlyofficePreviewUrl")
        );
    }

    @Test
    void shouldNormalizeArtifactsAsDownloadInfo() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        client.artifactsResponse = new LinkedHashMap<>(Map.of(
                "task_id", "demo-session-001",
                "session_id", "demo-session-001",
                "status", "completed",
                "minio_download_url", "https://example.com/final.pptx",
                "minio_download_urls", Map.of("pptx", "https://example.com/final.pptx")
        ));
        PptTaskService service = new PptTaskService(client, null);

        Map<String, Object> result = service.getDownloadInfo("demo-session-001", "http://localhost:8080");

        assertEquals("demo-session-001", result.get("taskId"));
        assertEquals("https://example.com/final.pptx", result.get("downloadUrl"));
        assertTrue(result.containsKey("downloadUrls"));
        assertTrue(String.valueOf(result.get("downloadInfoUrl")).endsWith("/api/ppt/tasks/demo-session-001/download-info"));
    }

    @Test
    void shouldResolveOnlyOfficePreviewSourceFromDraftFirst() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        client.draftResponse = new LinkedHashMap<>(Map.of(
                "task_id", "demo-session-001",
                "session_id", "demo-session-001",
                "status", "awaiting_draft_review",
                "minio_download_url", "https://example.com/draft-preview.pptx",
                "minio_download_urls", Map.of("draft_pptx", "https://example.com/draft-preview.pptx")
        ));
        PptTaskService service = new PptTaskService(client, null);

        Map<String, Object> result = service.getOnlyOfficePreviewSource("demo-session-001", "", "http://localhost:8080");

        assertTrue(String.valueOf(result.get("fileId")).startsWith("demosession001draftpptx"));
        assertEquals("demo-session-001-draft.pptx", result.get("fileName"));
        assertEquals("https://example.com/draft-preview.pptx", result.get("downloadUrl"));
    }

    @Test
    void shouldChangeDraftPreviewFileIdWhenDraftDownloadUrlChanges() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        PptTaskService service = new PptTaskService(client, null);

        client.draftResponse = new LinkedHashMap<>(Map.of(
                "task_id", "demo-session-001",
                "session_id", "demo-session-001",
                "status", "awaiting_draft_review",
                "minio_download_url", "https://example.com/draft-preview-v1.pptx",
                "minio_download_urls", Map.of("draft_pptx", "https://example.com/draft-preview-v1.pptx")
        ));
        Map<String, Object> first = service.getOnlyOfficePreviewSource("demo-session-001", "", "http://localhost:8080");

        client.draftResponse = new LinkedHashMap<>(Map.of(
                "task_id", "demo-session-001",
                "session_id", "demo-session-001",
                "status", "awaiting_draft_review",
                "minio_download_url", "https://example.com/draft-preview-v2.pptx",
                "minio_download_urls", Map.of("draft_pptx", "https://example.com/draft-preview-v2.pptx")
        ));
        Map<String, Object> second = service.getOnlyOfficePreviewSource("demo-session-001", "", "http://localhost:8080");

        assertFalse(String.valueOf(first.get("fileId")).isBlank());
        assertFalse(String.valueOf(second.get("fileId")).isBlank());
        assertEquals(false, first.get("fileId").equals(second.get("fileId")));
    }

    @Test
    void shouldResolveOnlyOfficePreviewSourceForDocxArtifact() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        client.artifactsResponse = new LinkedHashMap<>(Map.of(
                "task_id", "demo-session-001",
                "session_id", "demo-session-001",
                "status", "completed",
                "minio_download_urls", Map.of("teaching_plan_docx", "https://example.com/teaching-plan.docx")
        ));
        PptTaskService service = new PptTaskService(client, null);

        Map<String, Object> result = service.getOnlyOfficePreviewSource(
                "demo-session-001",
                "teaching_plan_docx",
                "http://localhost:8080"
        );

        assertEquals("demosession001teachingplandocx", result.get("fileId"));
        assertEquals("demo-session-001-teaching_plan.docx", result.get("fileName"));
        assertEquals("https://example.com/teaching-plan.docx", result.get("downloadUrl"));
    }

    @Test
    void shouldRejectCreateTaskWithoutResolvableUserInput() {
        PptTaskService service = new PptTaskService(new CapturingSuperPptClient(), null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.createTask(Map.of(), "http://localhost:8080")
        );

        assertEquals(40010, ex.getCode());
    }

    @Test
    void shouldListSessionsWithNormalizedAliases() {
        CapturingSuperPptClient client = new CapturingSuperPptClient();
        client.listSessionsResponse = List.of(Map.of(
                "session_id", "demo-session-001",
                "status", "running",
                "current_stage", "outline"
        ));
        PptTaskService service = new PptTaskService(client, null);

        Object result = service.listSessions("http://localhost:8080");

        assertInstanceOf(List.class, result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result;
        assertEquals("demo-session-001", list.getFirst().get("taskId"));
        assertEquals("outline", list.getFirst().get("currentStage"));
        assertFalse(String.valueOf(list.getFirst().get("pollUrl")).isBlank());
    }

    private static final class CapturingSuperPptClient implements SuperPptClient {
        private Map<String, Object> lastCreateSessionRequest = Map.of();
        private String lastAppendSessionAssetsSessionId = "";
        private Map<String, Object> lastAppendSessionAssetsRequest = Map.of();
        private final List<UploadCall> uploadCalls = new ArrayList<>();
        private int uploadSequence = 0;
        private Object listSessionsResponse = List.of();
        private Map<String, Object> artifactsResponse = Map.of(
                "task_id", "demo-session-001",
                "session_id", "demo-session-001",
                "status", "completed"
        );
        private Map<String, Object> draftResponse = Map.of(
                "task_id", "demo-session-001",
                "session_id", "demo-session-001",
                "status", "awaiting_draft_review"
        );

        @Override
        public Object healthz() {
            return Map.of("status", "ok");
        }

        @Override
        public Object listSessions() {
            return listSessionsResponse;
        }

        @Override
        public Map<String, Object> upload(UploadBinary file, String assetType, String role) {
            uploadSequence++;
            uploadCalls.add(new UploadCall(file.fileName(), file.contentType(), assetType, role, file.content()));
            return Map.of(
                    "upload_id", "upl-" + String.format("%03d", uploadSequence),
                    "filename", file.fileName(),
                    "asset_type", assetType,
                    "role", role
            );
        }

        @Override
        public Map<String, Object> getUpload(String uploadId) {
            return Map.of("upload_id", uploadId);
        }

        @Override
        public Map<String, Object> createSession(Map<String, Object> request) {
            this.lastCreateSessionRequest = request;
            return new LinkedHashMap<>(Map.of(
                    "task_id", "demo-session-001",
                    "session_id", "demo-session-001",
                    "pipeline_id", "demo_001",
                    "status", "running",
                    "current_stage", "",
                    "next_action", ""
            ));
        }

        @Override
        public Map<String, Object> getSession(String sessionId) {
            return Map.of(
                    "task_id", sessionId,
                    "session_id", sessionId,
                    "status", "running",
                    "current_stage", "outline"
            );
        }

        @Override
        public Map<String, Object> appendSessionAssets(String sessionId, Map<String, Object> request) {
            this.lastAppendSessionAssetsSessionId = sessionId;
            this.lastAppendSessionAssetsRequest = request;
            return Map.of(
                    "session_id", sessionId,
                    "status", "running",
                    "current_stage", "asset_analysis",
                    "next_action", "poll"
            );
        }

        @Override
        public Map<String, Object> submitClarifications(String sessionId, Map<String, Object> request) {
            return getSession(sessionId);
        }

        @Override
        public Map<String, Object> getOutline(String sessionId) {
            return getSession(sessionId);
        }

        @Override
        public Map<String, Object> reviewOutline(String sessionId, Map<String, Object> request) {
            return getSession(sessionId);
        }

        @Override
        public Map<String, Object> getDraft(String sessionId) {
            return draftResponse;
        }

        @Override
        public Map<String, Object> getRevisions(String sessionId) {
            return getSession(sessionId);
        }

        @Override
        public Map<String, Object> editSlides(String sessionId, Map<String, Object> request) {
            return getSession(sessionId);
        }

        @Override
        public Map<String, Object> reviseDraft(String sessionId, Map<String, Object> request) {
            return Map.of(
                    "task_id", sessionId,
                    "session_id", sessionId,
                    "status", "awaiting_draft_review",
                    "current_stage", "draft_revision",
                    "next_action", "preview"
            );
        }

        @Override
        public Map<String, Object> finalizeSession(String sessionId) {
            return getSession(sessionId);
        }

        @Override
        public Map<String, Object> getArtifacts(String sessionId) {
            return artifactsResponse;
        }

        @Override
        public Map<String, Object> getDigitalHumanPlugin(String sessionId) {
            return Map.of("status", "idle");
        }

        @Override
        public Map<String, Object> triggerDigitalHumanPlugin(String sessionId, Map<String, Object> request) {
            return Map.of("status", "running");
        }

        @Override
        public DownloadedFile downloadArtifact(String sessionId, String artifactName) {
            return new DownloadedFile(new byte[0], "application/octet-stream", "");
        }
    }

    private record UploadCall(String fileName, String contentType, String assetType, String role, byte[] content) {
    }
}
