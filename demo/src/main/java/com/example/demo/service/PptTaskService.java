package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PptTaskService {

    private final ConcurrentMap<String, PptTask> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger mockSequence = new AtomicInteger();
    private final String mlCallbackToken;
    private final String mockDownloadObjectKey;
    private final String mockFileId;
    private final String mockPreviousFileId;
    private final String mockPreviousFileName;
    private final String mockFileName;
    private final String mockObjectKey;
    private final String mockCreatorId;
    private final String mockBucketName;
    private final String mockApiBaseUrl;
    private final String mockDirectDownloadUrl;
    private final String mockMode;
    private final String mockLang;
    private final String mockUserId;
    private final MockFileSpec firstMockFile;
    private final MockFileSpec secondMockFile;
    private final MlSubmitClient mlSubmitClient;
    private final MinioStorageService minioStorageService;
    private final PlatformMockService platformMockService;

    @Autowired
    public PptTaskService(
            @Value("${ml.callback-token:dev-ml-callback-token}") String mlCallbackToken,
            @Value("${ml.mock-download-object-key:presentation_20260326_094052_themed_refined.pptx}") String mockDownloadObjectKey,
            @Value("${ml.mock-file.file-id:pptchapter2process}") String mockFileId,
            @Value("${ml.mock-file.previous-file-id:}") String mockPreviousFileId,
            @Value("${ml.mock-file.previous-file-name:}") String mockPreviousFileName,
            @Value("${ml.mock-file.file-name:第二章 进程管理-融合第9章与12章.pptx}") String mockFileName,
            @Value("${ml.mock-file.object-key:第二章 进程管理-融合第9章与12章.pptx}") String mockObjectKey,
            @Value("${ml.mock-file.creator-id:u100}") String mockCreatorId,
            @Value("${ml.mock-file.bucket-name:ppt-files}") String mockBucketName,
            @Value("${ml.mock-file.api-base-url:http://47.109.139.75:8080}") String mockApiBaseUrl,
            @Value("${ml.mock-file.direct-download-url:}") String mockDirectDownloadUrl,
            @Value("${ml.mock-file.mode:edit}") String mockMode,
            @Value("${ml.mock-file.lang:zh-CN}") String mockLang,
            @Value("${ml.mock-file.user-id:u100}") String mockUserId,
            MlSubmitClient mlSubmitClient,
            MinioStorageService minioStorageService,
            PlatformMockService platformMockService
    ) {
        this.mlCallbackToken = mlCallbackToken;
        this.mockDownloadObjectKey = mockDownloadObjectKey;
        this.mockFileId = mockFileId;
        this.mockPreviousFileId = mockPreviousFileId;
        this.mockPreviousFileName = mockPreviousFileName;
        this.mockFileName = mockFileName;
        this.mockObjectKey = mockObjectKey;
        this.mockCreatorId = mockCreatorId;
        this.mockBucketName = mockBucketName;
        this.mockApiBaseUrl = mockApiBaseUrl;
        this.mockDirectDownloadUrl = mockDirectDownloadUrl;
        this.mockMode = mockMode;
        this.mockLang = mockLang;
        this.mockUserId = mockUserId;
        this.mlSubmitClient = mlSubmitClient;
        this.minioStorageService = minioStorageService;
        this.platformMockService = platformMockService;
        this.firstMockFile = buildPreviousMockFileSpec();
        this.secondMockFile = buildCurrentMockFileSpec();
    }

    PptTaskService(String mlCallbackToken, MlSubmitClient mlSubmitClient) {
        this(
                mlCallbackToken,
                "presentation_20260326_094052_themed_refined.pptx",
                "pptchapter2process",
                "",
                "第二章 进程管理-融合第9章与12章.pptx",
                "第二章 进程管理-融合第9章与12章.pptx",
                "u100",
                "ppt-files",
                "http://47.109.139.75:8080",
                "",
                "edit",
                "zh-CN",
                "u100",
                mlSubmitClient,
                null,
                null
        );
    }

    public Map<String, Object> createTask(Map<String, Object> request, String baseUrl) {
        request = request == null ? Map.of() : request;
        PlatformMockService.ChatGenerationContext sessionContext = resolveSessionContext(request);
        String prompt = resolvePrompt(request, sessionContext);
        if (prompt.isBlank()) {
            throw new BusinessException(40010, "prompt or sessionId is required");
        }

        String taskId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        List<String> imageUrls = parseImageUrls(request.get("image_urls"));
        String userId = resolveUserId(request);
        Object attachments = mergeAttachments(request.get("attachments"), sessionContext);
        List<MlSubmitClient.AttachmentPayload> mlAttachments = extractMlAttachments(attachments);

        PptTask task = new PptTask(taskId, prompt, imageUrls, userId, now);
        task.sessionId = stringValue(request.get("sessionId"));
        task.audience = stringValue(request.get("audience"));
        task.templateId = stringValue(request.get("templateId"));
        task.attachments = attachments;
        task.status = "SUBMITTING";
        task.updatedAt = now;
        tasks.put(taskId, task);

        try {
            MlSubmitClient.SubmitResult submitResult = mlSubmitClient.submit(taskId, prompt, mlAttachments);
            task.mlJobId = submitResult.mlJobId();
            if (isMockJob(task.mlJobId)) {
                task.mockFileSpec = selectMockFileSpec();
                markTaskMockSucceeded(task);
            } else {
                task.status = "PROCESSING";
                task.errorMessage = "";
            }
            task.updatedAt = Instant.now().toEpochMilli();
        } catch (BusinessException ex) {
            task.status = "FAILED";
            task.errorMessage = ex.getMessage();
            task.updatedAt = Instant.now().toEpochMilli();
        }

        if (isMockJob(task.mlJobId) && "SUCCEEDED".equals(task.status)) {
            return buildMockPreviewPayload(task, baseUrl);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        data.put("status", task.status);
        data.put("userId", userId);
        data.put("pollUrl", baseUrl + "/api/ppt/tasks/" + taskId);
        data.put("mlTaskId", task.mlJobId);
        data.put("errorMessage", task.errorMessage);
        data.put("mlSubmitPayload", Map.of(
                "taskId", taskId,
                "userInput", prompt,
                "attachmentCount", mlAttachments.size()
        ));
        return data;
    }

    public Map<String, Object> getTask(String taskId) {
        PptTask task = requireTask(taskId);
        refreshTaskFromMl(task);
        return toTaskView(task);
    }

    public Map<String, Object> updateFromMlCallback(String taskId, Map<String, Object> request, String callbackToken) {
        if (!mlCallbackToken.equals(callbackToken)) {
            throw new BusinessException(40101, "invalid ml callback token");
        }

        PptTask task = requireTask(taskId);
        String status = String.valueOf(request.getOrDefault("status", "")).trim().toUpperCase();
        if (status.isBlank() && (request.containsKey("downloadUrl") || request.containsKey("file"))) {
            status = "SUCCEEDED";
        }
        if (status.isBlank()) {
            throw new BusinessException(40011, "status is required");
        }

        task.mlJobId = String.valueOf(request.getOrDefault("ml_job_id", ""));
        task.updatedAt = Instant.now().toEpochMilli();
        if ("SUCCEEDED".equals(status)) {
            Map<String, Object> filePayload = extractFilePayload(request);
            String downloadUrl = stringValue(filePayload.get("downloadUrl"));
            if (downloadUrl.isBlank()) {
                throw new BusinessException(40012, "downloadUrl is required when status is SUCCEEDED");
            }
            task.status = "SUCCEEDED";
            task.objectStorageUrl = downloadUrl;
            task.resultTitle = defaultValue(stringValue(filePayload.get("title")), task.prompt);
            task.resultFileName = defaultValue(stringValue(filePayload.get("fileName")), resolveFileName(downloadUrl, task.id));
            task.resultFileType = defaultValue(stringValue(filePayload.get("fileType")), suffix(task.resultFileName));
            task.errorMessage = "";
        } else if ("FAILED".equals(status)) {
            task.status = "FAILED";
            task.objectStorageUrl = "";
            task.errorMessage = String.valueOf(request.getOrDefault("error_message", "ml generation failed"));
        } else if ("PROCESSING".equals(status)) {
            task.status = "PROCESSING";
        } else {
            throw new BusinessException(40013, "unsupported status");
        }
        return toTaskView(task);
    }

    public Map<String, Object> getDownloadInfo(String taskId) {
        PptTask task = requireTask(taskId);
        refreshTaskFromMl(task);
        if (!"SUCCEEDED".equals(task.status)) {
            throw new BusinessException(40901, "ppt is not ready");
        }
        Map<String, Object> file = buildResultFile(task);
        return Map.of(
                "taskId", task.id,
                "status", task.status,
                "downloadUrl", task.objectStorageUrl,
                "title", file.get("title"),
                "fileName", file.get("fileName"),
                "fileType", file.get("fileType"),
                "file", file
        );
    }

    private PptTask requireTask(String taskId) {
        PptTask task = tasks.get(taskId);
        if (task == null) {
            throw new BusinessException(40410, "task not found");
        }
        return task;
    }

    private List<String> parseImageUrls(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.trim());
        }
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private List<MlSubmitClient.AttachmentPayload> extractMlAttachments(Object attachments) {
        if (!(attachments instanceof List<?> list) || platformMockService == null) {
            return List.of();
        }
        List<MlSubmitClient.AttachmentPayload> resolved = new ArrayList<>();
        boolean pdfAdded = false;
        boolean audioAdded = false;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> attachmentMap)) {
                continue;
            }
            String fileId = stringValue(attachmentMap.get("fileId"));
            if (fileId.isBlank()) {
                continue;
            }
            PlatformMockService.FileContent content;
            try {
                content = platformMockService.getFileContent(fileId);
            } catch (BusinessException ignored) {
                // Ignore stale file ids so the request returns a clear task result instead of failing hard.
                continue;
            }
            String fileName = content.fileName();
            String fieldName = resolveMlFieldName(fileName, content.contentType(), pdfAdded, audioAdded);
            if (fieldName.isBlank()) {
                continue;
            }
            if ("pdf_file".equals(fieldName)) {
                pdfAdded = true;
            }
            if ("audio_file".equals(fieldName)) {
                audioAdded = true;
            }
            resolved.add(new MlSubmitClient.AttachmentPayload(fieldName, fileName, content.contentType(), content.content()));
        }
        return resolved;
    }

    private String resolveMlFieldName(String fileName, String contentType, boolean pdfAdded, boolean audioAdded) {
        String suffix = suffix(fileName);
        boolean isPdf = "pdf".equals(suffix) || "application/pdf".equalsIgnoreCase(stringValue(contentType));
        if (isPdf && !pdfAdded) {
            return "pdf_file";
        }
        boolean isAudio = List.of("mp3", "wav", "m4a", "aac", "ogg").contains(suffix)
                || stringValue(contentType).toLowerCase().startsWith("audio/");
        if (isAudio && !audioAdded) {
            return "audio_file";
        }
        return "";
    }

    private Map<String, Object> toTaskView(PptTask task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.id);
        data.put("status", task.status);
        data.put("prompt", task.prompt);
        data.put("imageUrls", task.imageUrls);
        data.put("userId", task.userId);
        data.put("mlJobId", task.mlJobId);
        data.put("objectStorageUrl", task.objectStorageUrl);
        if (!task.resultTitle.isBlank()) {
            data.put("title", task.resultTitle);
        }
        if (!task.resultFileName.isBlank()) {
            data.put("fileName", task.resultFileName);
        }
        if (!task.resultFileType.isBlank()) {
            data.put("fileType", task.resultFileType);
        }
        if (!task.objectStorageUrl.isBlank()) {
            data.put("downloadUrl", task.objectStorageUrl);
        }
        data.put("errorMessage", task.errorMessage);
        data.put("createdAt", task.createdAt);
        data.put("updatedAt", task.updatedAt);
        if (!task.sessionId.isBlank()) {
            data.put("sessionId", task.sessionId);
        }
        if (!task.audience.isBlank()) {
            data.put("audience", task.audience);
        }
        if (!task.templateId.isBlank()) {
            data.put("templateId", task.templateId);
        }
        data.put("attachments", task.attachments);
        if ("SUCCEEDED".equals(task.status)) {
            data.put("fileInfo", buildFileInfo(task));
            data.put("file", buildResultFile(task));
        }
        return data;
    }

    private Map<String, Object> buildFileInfo(PptTask task) {
        String downloadUrl = resolveTaskDownloadUrl(task);
        String fileName = resolveTaskFileName(task, downloadUrl);
        Map<String, Object> fileInfo = new LinkedHashMap<>();
        fileInfo.put("fileId", resolveTaskFileId(task));
        fileInfo.put("fileName", fileName);
        fileInfo.put("objectKey", resolveTaskObjectKey(task, fileName));
        fileInfo.put("creatorId", resolveTaskCreatorId(task));
        fileInfo.put("bucketName", resolveTaskBucketName(task));
        fileInfo.put("directDownloadUrl", downloadUrl);
        fileInfo.put("apiBaseUrl", resolveTaskApiBaseUrl(task));
        fileInfo.put("mode", resolveTaskMode(task));
        fileInfo.put("userId", resolveTaskUserId(task));
        if (isMockJob(task.mlJobId)) {
            fileInfo.put("lang", defaultValue(mockLang, "zh-CN"));
        }
        return fileInfo;
    }

    private Map<String, Object> buildResultFile(PptTask task) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("title", defaultValue(task.resultTitle, task.prompt));
        String downloadUrl = resolveTaskDownloadUrl(task);
        file.put("fileName", resolveTaskFileName(task, downloadUrl));
        file.put("fileType", task.resultFileType.isBlank() ? suffix(file.get("fileName").toString()) : task.resultFileType);
        file.put("downloadUrl", downloadUrl);
        return file;
    }

    private Map<String, Object> extractFilePayload(Map<String, Object> request) {
        Object directPayload = request.get("file");
        if (directPayload instanceof Map<?, ?> fileMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) fileMap;
            return casted;
        }

        if (request.containsKey("downloadUrl") || request.containsKey("fileName") || request.containsKey("title")) {
            return request;
        }

        String objectStorageUrl = stringValue(request.get("object_storage_url"));
        if (!objectStorageUrl.isBlank()) {
            return Map.of(
                    "downloadUrl", objectStorageUrl,
                    "fileName", resolveFileName(objectStorageUrl, "generated"),
                    "fileType", suffix(resolveFileName(objectStorageUrl, "generated")),
                    "title", ""
            );
        }
        return Map.of();
    }

    private PlatformMockService.ChatGenerationContext resolveSessionContext(Map<String, Object> request) {
        String sessionId = stringValue(request.get("sessionId"));
        if (sessionId.isBlank() || platformMockService == null) {
            return null;
        }
        return platformMockService.getChatGenerationContext(sessionId);
    }

    private Object mergeAttachments(Object requestAttachments, PlatformMockService.ChatGenerationContext sessionContext) {
        List<Map<String, Object>> merged = new ArrayList<>();
        merged.addAll(asAttachmentList(requestAttachments));
        if (sessionContext != null) {
            for (Map<String, Object> item : sessionContext.attachments()) {
                String fileId = stringValue(item.get("fileId"));
                boolean exists = merged.stream().anyMatch(existing -> fileId.equals(stringValue(existing.get("fileId"))));
                if (!fileId.isBlank() && !exists) {
                    merged.add(item);
                }
            }
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asAttachmentList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> attachments = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                attachments.add((Map<String, Object>) map);
            }
        }
        return attachments;
    }

    private String resolvePrompt(Map<String, Object> request, PlatformMockService.ChatGenerationContext sessionContext) {
        String prompt = firstNonBlank(
                request.get("prompt"),
                request.get("content"),
                request.get("user_input")
        );
        if (!prompt.isBlank()) {
            return prompt;
        }
        Map<String, Object> requirements = mapValue(request.get("requirements"));
        prompt = firstNonBlank(
                requirements.get("prompt"),
                requirements.get("content"),
                requirements.get("topic"),
                requirements.get("outline")
        );
        if (!prompt.isBlank()) {
            return prompt;
        }
        if (sessionContext != null && !sessionContext.prompt().isBlank()) {
            return sessionContext.prompt();
        }
        String sessionId = stringValue(request.get("sessionId"));
        String audience = stringValue(request.get("audience"));
        String templateId = stringValue(request.get("templateId"));
        if (sessionId.isBlank() && audience.isBlank() && templateId.isBlank()) {
            return "";
        }
        return "Generate PPT for session " + defaultValue(sessionId, "unknown")
                + ", audience=" + defaultValue(audience, "general")
                + ", templateId=" + defaultValue(templateId, "default");
    }

    private String resolveUserId(Map<String, Object> request) {
        String userId = firstNonBlank(
                request.get("user_id"),
                request.get("userId")
        );
        if (!userId.isBlank()) {
            return userId;
        }
        Map<String, Object> user = mapValue(request.get("user"));
        userId = firstNonBlank(
                user.get("user_id"),
                user.get("userId"),
                user.get("id")
        );
        return userId.isBlank() ? "anonymous" : userId;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            String text = stringValue(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void refreshTaskFromMl(PptTask task) {
        if (task == null || task.mlJobId == null || task.mlJobId.isBlank()) {
            return;
        }
        if (isMockJob(task.mlJobId)) {
            return;
        }
        if ("SUCCEEDED".equals(task.status) || "FAILED".equals(task.status)) {
            return;
        }
        MlSubmitClient.TaskResult mlTask = mlSubmitClient.queryTask(task.mlJobId);
        if (mlTask == null) {
            return;
        }
        task.updatedAt = Instant.now().toEpochMilli();
        task.mlJobId = defaultValue(mlTask.mlJobId(), task.mlJobId);
        if ("SUCCEEDED".equals(mlTask.status())) {
            if (mlTask.downloadUrl() == null || mlTask.downloadUrl().isBlank()) {
                task.status = "FAILED";
                task.errorMessage = "ml task succeeded but downloadUrl is missing";
                return;
            }
            task.status = "SUCCEEDED";
            task.objectStorageUrl = mlTask.downloadUrl();
            task.resultTitle = defaultValue(mlTask.title(), task.prompt);
            task.resultFileName = defaultValue(mlTask.fileName(), resolveFileName(mlTask.downloadUrl(), task.id));
            task.resultFileType = defaultValue(mlTask.fileType(), suffix(task.resultFileName));
            task.errorMessage = "";
            return;
        }
        if ("FAILED".equals(mlTask.status())) {
            task.status = "FAILED";
            task.errorMessage = defaultValue(mlTask.errorMessage(), "ml generation failed");
            return;
        }
        task.status = "PROCESSING";
    }

    private String resolveFileName(String downloadUrl, String taskId) {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            return "generated-" + taskId + ".pptx";
        }
        try {
            String path = URI.create(downloadUrl).getPath();
            if (path != null && path.contains("/")) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // Fall back to generated name when the callback url is not a valid URI.
        }
        return "generated-" + taskId + ".pptx";
    }

    private boolean isMockJob(String mlJobId) {
        return mlJobId != null && mlJobId.startsWith("mock-");
    }

    private void markTaskMockSucceeded(PptTask task) {
        MockFileSpec spec = requireMockFileSpec(task);
        task.status = "SUCCEEDED";
        task.objectStorageUrl = spec.directDownloadUrl();
        task.resultTitle = defaultValue(task.resultTitle, task.prompt);
        task.resultFileName = defaultValue(spec.fileName(), resolveFileName(task.objectStorageUrl, task.id));
        task.resultFileType = "pptx";
        task.errorMessage = "";
    }

    private Map<String, Object> buildMockPreviewPayload(PptTask task, String baseUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileId", resolveTaskFileId(task));
        data.put("fileName", resolveTaskFileName(task, task.objectStorageUrl));
        data.put("objectKey", resolveTaskObjectKey(task, resolveTaskFileName(task, task.objectStorageUrl)));
        data.put("creatorId", resolveTaskCreatorId(task));
        data.put("bucketName", resolveTaskBucketName(task));
        data.put("apiBaseUrl", resolveTaskApiBaseUrl(task).isBlank() ? baseUrl : resolveTaskApiBaseUrl(task));
        data.put("directDownloadUrl", resolveTaskDownloadUrl(task));
        data.put("mode", resolveTaskMode(task));
        data.put("lang", requireMockFileSpec(task).lang());
        data.put("userId", resolveTaskUserId(task));
        return data;
    }

    private String resolveTaskDownloadUrl(PptTask task) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).directDownloadUrl();
        }
        return task.objectStorageUrl;
    }

    private String resolveTaskFileId(PptTask task) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).fileId();
        }
        return "generated" + task.id.replace("-", "");
    }

    private String resolveTaskFileName(PptTask task, String downloadUrl) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).fileName();
        }
        return task.resultFileName.isBlank() ? resolveFileName(downloadUrl, task.id) : task.resultFileName;
    }

    private String resolveTaskObjectKey(PptTask task, String fileName) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).objectKey();
        }
        return fileName;
    }

    private String resolveTaskCreatorId(PptTask task) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).creatorId();
        }
        return task.userId;
    }

    private String resolveTaskBucketName(PptTask task) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).bucketName();
        }
        return defaultBucket();
    }

    private String resolveTaskApiBaseUrl(PptTask task) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).apiBaseUrl();
        }
        return "";
    }

    private String resolveTaskMode(PptTask task) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).mode();
        }
        return "edit";
    }

    private String resolveTaskUserId(PptTask task) {
        if (isMockJob(task.mlJobId)) {
            return requireMockFileSpec(task).userId();
        }
        return task.userId;
    }

    private MockFileSpec requireMockFileSpec(PptTask task) {
        if (task.mockFileSpec != null) {
            return task.mockFileSpec;
        }
        return buildPreviousMockFileSpec();
    }

    private MockFileSpec selectMockFileSpec() {
        int current = mockSequence.getAndIncrement();
        return current % 2 == 0 ? buildPreviousMockFileSpec() : buildCurrentMockFileSpec();
    }

    private MockFileSpec buildPreviousMockFileSpec() {
        return buildMockFileSpec(
                previousFileId(),
                defaultValue(mockPreviousFileName, removeVariantSuffix(mockFileName)),
                removeVariantSuffix(mockObjectKey),
                mockCreatorId,
                mockBucketName,
                mockApiBaseUrl,
                removeVariantSuffixFromUrl(mockDirectDownloadUrl),
                mockMode,
                mockLang,
                mockUserId
        );
    }

    private MockFileSpec buildCurrentMockFileSpec() {
        return buildMockFileSpec(
                defaultValue(mockFileId, normalizeMockFileId(mockObjectKey, mockFileName, mockDirectDownloadUrl)),
                mockFileName,
                mockObjectKey,
                mockCreatorId,
                mockBucketName,
                mockApiBaseUrl,
                mockDirectDownloadUrl,
                mockMode,
                mockLang,
                mockUserId
        );
    }

    private MockFileSpec buildMockFileSpec(
            String fileId,
            String fileName,
            String objectKey,
            String creatorId,
            String bucketName,
            String apiBaseUrl,
            String directDownloadUrl,
            String mode,
            String lang,
            String userId
    ) {
        String resolvedFileName = defaultValue(fileName, objectKey);
        String resolvedObjectKey = defaultValue(objectKey, resolvedFileName);
        String resolvedBucketName = defaultValue(bucketName, defaultBucket());
        String resolvedDirectDownloadUrl = defaultValue(
                directDownloadUrl,
                minioStorageService == null ? "" : minioStorageService.publicObjectUrl(resolvedBucketName, resolvedObjectKey)
        );
        String resolvedCreatorId = defaultValue(creatorId, "u100");
        return new MockFileSpec(
                defaultValue(fileId, normalizeMockFileId(resolvedObjectKey, resolvedFileName, resolvedDirectDownloadUrl)),
                resolvedFileName,
                resolvedObjectKey,
                resolvedCreatorId,
                resolvedBucketName,
                defaultValue(apiBaseUrl, ""),
                resolvedDirectDownloadUrl,
                defaultValue(mode, "edit"),
                defaultValue(lang, "zh-CN"),
                defaultValue(userId, resolvedCreatorId)
        );
    }

    private String previousFileId() {
        if (mockPreviousFileId != null && !mockPreviousFileId.isBlank()) {
            return mockPreviousFileId;
        }
        if (mockFileId != null && !mockFileId.isBlank() && mockFileId.endsWith("1pptx")) {
            return mockFileId.substring(0, mockFileId.length() - "1pptx".length()) + "pptx";
        }
        return normalizeMockFileId(removeVariantSuffix(mockObjectKey), removeVariantSuffix(mockFileName), removeVariantSuffixFromUrl(mockDirectDownloadUrl));
    }

    private String removeVariantSuffix(String value) {
        return stringValue(value).replace(" (1)", "");
    }

    private String removeVariantSuffixFromUrl(String value) {
        return stringValue(value)
                .replace("%20%281%29", "")
                .replace(" (1)", "");
    }

    private String normalizeMockFileId(String... values) {
        String source = firstNonBlank((Object[]) values);
        String normalized = source
                .replaceAll("https?://", "")
                .replaceAll("[^A-Za-z0-9]", "");
        if (!normalized.isBlank()) {
            return normalized;
        }
        return "mockpptfile";
    }

    private String suffix(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String defaultBucket() {
        if (minioStorageService == null) {
            return "ppt-files";
        }
        return minioStorageService.defaultBucket();
    }

    private static final class PptTask {
        private final String id;
        private final String prompt;
        private final List<String> imageUrls;
        private final String userId;
        private final long createdAt;
        private long updatedAt;
        private String status;
        private String mlJobId;
        private String objectStorageUrl;
        private String errorMessage;
        private String sessionId;
        private String audience;
        private String templateId;
        private Object attachments;
        private String resultTitle;
        private String resultFileName;
        private String resultFileType;
        private MockFileSpec mockFileSpec;

        private PptTask(String id, String prompt, List<String> imageUrls, String userId, long createdAt) {
            this.id = id;
            this.prompt = prompt;
            this.imageUrls = imageUrls;
            this.userId = userId;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
            this.status = "PENDING";
            this.mlJobId = "";
            this.objectStorageUrl = "";
            this.errorMessage = "";
            this.sessionId = "";
            this.audience = "";
            this.templateId = "";
            this.attachments = List.of();
            this.resultTitle = "";
            this.resultFileName = "";
            this.resultFileType = "";
            this.mockFileSpec = null;
        }
    }

    private record MockFileSpec(
            String fileId,
            String fileName,
            String objectKey,
            String creatorId,
            String bucketName,
            String apiBaseUrl,
            String directDownloadUrl,
            String mode,
            String lang,
            String userId
    ) {
    }
}
