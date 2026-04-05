package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptTaskServiceTests {

    private static final String CALLBACK_TOKEN = "dev-ml-callback-token";
    private PptTaskService service;
    private StubMlSubmitClient mlClient;

    @BeforeEach
    void setUp() {
        mlClient = new StubMlSubmitClient("ml-job-001");
        service = new PptTaskService(CALLBACK_TOKEN, mlClient);
    }

    @Test
    void shouldCreateTaskAndReturnPollUrl() {
        Map<String, Object> request = Map.of(
                "prompt", "Generate a product launch deck",
                "image_urls", java.util.List.of("https://cdn.example.com/a.png"),
                "user_id", "u100"
        );
        Map<String, Object> result = service.createTask(request, "http://localhost:8080");

        assertNotNull(result.get("taskId"));
        assertEquals("PROCESSING", result.get("status"));
        assertEquals("ml-job-001", result.get("mlTaskId"));
        assertTrue(String.valueOf(result.get("pollUrl")).contains("/api/ppt/tasks/"));
    }

    @Test
    void shouldCompleteTaskAfterMlCallback() {
        Map<String, Object> created = service.createTask(Map.of("prompt", "make ppt"), "http://localhost:8080");
        String taskId = String.valueOf(created.get("taskId"));

        mlClient.taskResult = new MlSubmitClient.TaskResult(
                "ml-job-001",
                "SUCCEEDED",
                "",
                "",
                "",
                "https://oss.example.com/ppt/001.pptx",
                ""
        );
        Map<String, Object> task = service.getTask(taskId);
        Map<String, Object> downloadInfo = service.getDownloadInfo(taskId);

        assertEquals("SUCCEEDED", task.get("status"));
        assertEquals("https://oss.example.com/ppt/001.pptx", task.get("downloadUrl"));
        assertEquals("https://oss.example.com/ppt/001.pptx", downloadInfo.get("downloadUrl"));
    }

    @Test
    void shouldSupportMlCallbackWithStructuredFilePayload() {
        Map<String, Object> created = service.createTask(Map.of("prompt", "make ppt"), "http://localhost:8080");
        String taskId = String.valueOf(created.get("taskId"));

        mlClient.taskResult = new MlSubmitClient.TaskResult(
                "ml-job-001",
                "SUCCEEDED",
                "test",
                "presentation_20260324_131145_themed_refined_content_round1.pptx",
                "pptx",
                "https://tenant-trips-donate-allocation.trycloudflare.com/presentation_20260324_131145_themed_refined_content_round1.pptx",
                ""
        );
        Map<String, Object> task = service.getTask(taskId);

        assertEquals("SUCCEEDED", task.get("status"));
        assertEquals("test", task.get("title"));
        assertEquals("presentation_20260324_131145_themed_refined_content_round1.pptx", task.get("fileName"));
        assertEquals("https://tenant-trips-donate-allocation.trycloudflare.com/presentation_20260324_131145_themed_refined_content_round1.pptx", task.get("downloadUrl"));
        @SuppressWarnings("unchecked")
        Map<String, Object> file = (Map<String, Object>) task.get("file");
        assertEquals("pptx", file.get("fileType"));
        assertEquals("https://tenant-trips-donate-allocation.trycloudflare.com/presentation_20260324_131145_themed_refined_content_round1.pptx", file.get("downloadUrl"));

        Map<String, Object> downloadInfo = service.getDownloadInfo(taskId);
        assertEquals("test", downloadInfo.get("title"));
        assertEquals("presentation_20260324_131145_themed_refined_content_round1.pptx", downloadInfo.get("fileName"));
        assertEquals("pptx", downloadInfo.get("fileType"));
        assertEquals("https://tenant-trips-donate-allocation.trycloudflare.com/presentation_20260324_131145_themed_refined_content_round1.pptx", downloadInfo.get("downloadUrl"));
    }

    @Test
    void shouldSupportFailedTaskFromMlCallback() {
        Map<String, Object> created = service.createTask(Map.of("prompt", "make ppt"), "http://localhost:8080");
        String taskId = String.valueOf(created.get("taskId"));

        mlClient.taskResult = new MlSubmitClient.TaskResult(
                "ml-job-002",
                "FAILED",
                "",
                "",
                "",
                "",
                "input not enough"
        );
        Map<String, Object> task = service.getTask(taskId);

        assertEquals("FAILED", task.get("status"));
        assertFalse(String.valueOf(task.get("errorMessage")).isBlank());
    }

    @Test
    void shouldRejectDownloadWhenTaskNotReady() {
        Map<String, Object> created = service.createTask(Map.of("prompt", "make ppt"), "http://localhost:8080");
        String taskId = String.valueOf(created.get("taskId"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDownloadInfo(taskId));
        assertEquals(40901, ex.getCode());
    }

    @Test
    void shouldRejectInvalidCallbackToken() {
        Map<String, Object> created = service.createTask(Map.of("prompt", "make ppt"), "http://localhost:8080");
        String taskId = String.valueOf(created.get("taskId"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.updateFromMlCallback(taskId, Map.of("status", "PROCESSING"), "wrong-token")
        );
        assertEquals(40101, ex.getCode());
    }

    @Test
    void shouldMarkTaskFailedWhenMlSubmitFails() {
        PptTaskService failedSubmitService = new PptTaskService(
                CALLBACK_TOKEN,
                new StubMlSubmitClient("ml-job-001") {
                    @Override
                    public MlSubmitClient.SubmitResult submit(String taskId, String prompt, java.util.List<MlSubmitClient.AttachmentPayload> attachments) {
                        throw new BusinessException(50210, "submit to ml failed");
                    }
                }
        );
        Map<String, Object> created = failedSubmitService.createTask(Map.of("prompt", "make ppt"), "http://localhost:8080");
        String taskId = String.valueOf(created.get("taskId"));
        Map<String, Object> task = failedSubmitService.getTask(taskId);

        assertEquals("FAILED", created.get("status"));
        assertEquals("FAILED", task.get("status"));
        assertFalse(String.valueOf(task.get("errorMessage")).isBlank());
    }

    @Test
    void shouldAcceptAlternativePromptFields() {
        Map<String, Object> created = service.createTask(Map.of(
                "content", "make ppt from chat content",
                "user", Map.of("id", "u200")
        ), "http://localhost:8080");

        assertEquals("PROCESSING", created.get("status"));
        assertEquals("u200", created.get("userId"));
    }

    @Test
    void shouldAcceptNestedRequirementsPrompt() {
        Map<String, Object> created = service.createTask(Map.of(
                "requirements", Map.of("topic", "AI lesson design")
        ), "http://localhost:8080");

        assertEquals("PROCESSING", created.get("status"));
    }

    @Test
    void shouldIgnoreMissingAttachmentFileIds() {
        Map<String, Object> created = service.createTask(Map.of(
                "prompt", "make ppt",
                "attachments", java.util.List.of(Map.of("fileId", "missing-file"))
        ), "http://localhost:8080");

        assertEquals("PROCESSING", created.get("status"));
    }

    @Test
    void shouldBuildPromptFromChatSessionContent() {
        PlatformMockService platformMockService = new PlatformMockService();
        Map<String, Object> session = platformMockService.createChatSession();
        String sessionId = String.valueOf(session.get("sessionId"));
        platformMockService.sendChatMessage(sessionId, Map.of("content", "帮我做一份人工智能教学PPT"));

        CapturingMlSubmitClient capturingClient = new CapturingMlSubmitClient();
        PptTaskService taskService = new PptTaskService(
                CALLBACK_TOKEN,
                "presentation_20260326_094052_themed_refined.pptx",
                capturingClient,
                null,
                platformMockService
        );

        Map<String, Object> created = taskService.createTask(Map.of("sessionId", sessionId), "http://localhost:8080");

        assertEquals("PROCESSING", created.get("status"));
        assertTrue(capturingClient.lastPrompt.contains("user: 帮我做一份人工智能教学PPT"));
    }

    @Test
    void shouldLoadSessionAttachmentContentForMlSubmit() {
        PlatformMockService platformMockService = new PlatformMockService();
        Map<String, Object> session = platformMockService.createChatSession();
        String sessionId = String.valueOf(session.get("sessionId"));
        Map<String, Object> uploaded = platformMockService.uploadFile(
                new MockMultipartFile("file", "outline.pdf", "application/pdf", "lesson outline".getBytes()),
                "chat",
                "u100",
                "http://localhost:8080"
        );
        String fileId = String.valueOf(uploaded.get("fileId"));
        platformMockService.sendChatMessage(sessionId, Map.of(
                "content", "请参考我上传的材料生成PPT",
                "attachments", java.util.List.of(Map.of("fileId", fileId))
        ));

        CapturingMlSubmitClient capturingClient = new CapturingMlSubmitClient();
        PptTaskService taskService = new PptTaskService(
                CALLBACK_TOKEN,
                "presentation_20260326_094052_themed_refined.pptx",
                capturingClient,
                null,
                platformMockService
        );

        Map<String, Object> created = taskService.createTask(Map.of("sessionId", sessionId), "http://localhost:8080");

        assertEquals("PROCESSING", created.get("status"));
        assertEquals(1, capturingClient.lastAttachments.size());
        assertEquals("pdf_file", capturingClient.lastAttachments.getFirst().fieldName());
        assertEquals("outline.pdf", capturingClient.lastAttachments.getFirst().fileName());
    }

    @Test
    void shouldMarkMockJobAsSucceededImmediately() {
        PptTaskService taskService = new PptTaskService(
                CALLBACK_TOKEN,
                new StubMlSubmitClient("mock-job-001")
        );

        Map<String, Object> created = taskService.createTask(Map.of("prompt", "demo ppt"), "http://localhost:8080");
        assertTrue(String.valueOf(created.get("fileId")).matches("[A-Za-z0-9]+"));
        assertEquals("mockppt", created.get("fileName"));
        assertEquals("mockppt", created.get("objectKey"));
        assertEquals("anonymous", created.get("creatorId"));
        assertEquals("ppt-files", created.get("bucketName"));
        assertEquals("http://localhost:8080", created.get("apiBaseUrl"));
        assertEquals("edit", created.get("mode"));
        assertEquals("zh-CN", created.get("lang"));
        assertEquals("anonymous", created.get("userId"));
        assertTrue(String.valueOf(created.get("directDownloadUrl")).contains("/api/onlyoffice/files/mock-preview-content/"));
    }

    private static class StubMlSubmitClient implements MlSubmitClient {
        private final String mlJobId;
        private MlSubmitClient.TaskResult taskResult;

        private StubMlSubmitClient(String mlJobId) {
            this.mlJobId = mlJobId;
            this.taskResult = MlSubmitClient.TaskResult.processing(mlJobId);
        }

        @Override
        public MlSubmitClient.SubmitResult submit(String taskId, String prompt, java.util.List<MlSubmitClient.AttachmentPayload> attachments) {
            return new MlSubmitClient.SubmitResult(mlJobId);
        }

        @Override
        public MlSubmitClient.TaskResult queryTask(String mlJobId) {
            return taskResult;
        }
    }

    private static final class CapturingMlSubmitClient implements MlSubmitClient {
        private String lastPrompt = "";
        private java.util.List<MlSubmitClient.AttachmentPayload> lastAttachments = java.util.List.of();

        @Override
        public SubmitResult submit(String taskId, String prompt, java.util.List<AttachmentPayload> attachments) {
            this.lastPrompt = prompt;
            this.lastAttachments = attachments;
            return new SubmitResult("ml-job-captured");
        }

        @Override
        public TaskResult queryTask(String mlJobId) {
            return TaskResult.processing(mlJobId);
        }
    }
}
