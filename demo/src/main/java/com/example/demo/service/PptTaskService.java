package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PptTaskService {

    private static final Set<String> LEGACY_CREATE_KEYS = Set.of(
            "prompt",
            "content",
            "requirements",
            "sessionId",
            "attachments",
            "image_urls",
            "user_id",
            "user",
            "audience",
            "templateId",
            "pipelineId",
            "runtimeMode",
            "forceRestart",
            "force_restart"
    );

    private final SuperPptClient superPptClient;
    private final PlatformMockService platformMockService;

    public PptTaskService(SuperPptClient superPptClient, PlatformMockService platformMockService) {
        this.superPptClient = superPptClient;
        this.platformMockService = platformMockService;
    }

    public Object healthz() {
        return superPptClient.healthz();
    }

    public Object listTasks(String baseUrl) {
        return normalizeCollection(superPptClient.listSessions(), baseUrl);
    }

    public Object listSessions(String baseUrl) {
        return normalizeCollection(superPptClient.listSessions(), baseUrl);
    }

    public Map<String, Object> uploadFile(MultipartFile file, String assetType, String role) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40040, "file is required");
        }
        return normalizeUpload(superPptClient.upload(toUploadBinary(file), assetType, role));
    }

    public Map<String, Object> getUpload(String uploadId) {
        return normalizeUpload(superPptClient.getUpload(uploadId));
    }

    public Map<String, Object> createTask(Map<String, Object> request, String baseUrl) {
        return normalizeSession(superPptClient.createSession(buildSessionRequest(request)), baseUrl);
    }

    public Map<String, Object> createSession(Map<String, Object> request, String baseUrl) {
        return normalizeSession(superPptClient.createSession(buildSessionRequest(request)), baseUrl);
    }

    public Map<String, Object> getTask(String taskId, String baseUrl) {
        return normalizeSession(superPptClient.getSession(taskId), baseUrl);
    }

    public Map<String, Object> getSession(String sessionId, String baseUrl) {
        return normalizeSession(superPptClient.getSession(sessionId), baseUrl);
    }

    public Map<String, Object> appendSessionAssets(String sessionId, Map<String, Object> request, String baseUrl) {
        Map<String, Object> response = new LinkedHashMap<>(superPptClient.appendSessionAssets(
                sessionId,
                buildAppendAssetsRequest(request)
        ));
        response.putIfAbsent("session_id", sessionId);
        response.putIfAbsent("task_id", sessionId);
        return normalizeSession(response, baseUrl);
    }

    public Map<String, Object> getDownloadInfo(String taskId, String baseUrl) {
        return normalizeSession(superPptClient.getArtifacts(taskId), baseUrl);
    }

    public Map<String, Object> submitClarifications(String sessionId, Map<String, Object> request, String baseUrl) {
        return normalizeSession(superPptClient.submitClarifications(sessionId, request), baseUrl);
    }

    public Map<String, Object> getOutline(String sessionId, String baseUrl) {
        return normalizeSession(superPptClient.getOutline(sessionId), baseUrl);
    }

    public Map<String, Object> reviewOutline(String sessionId, Map<String, Object> request, String baseUrl) {
        return normalizeSession(superPptClient.reviewOutline(sessionId, request), baseUrl);
    }

    public Map<String, Object> getDraft(String sessionId, String baseUrl) {
        return normalizeSession(superPptClient.getDraft(sessionId), baseUrl);
    }

    public Map<String, Object> getRevisions(String sessionId, String baseUrl) {
        return normalizeSession(superPptClient.getRevisions(sessionId), baseUrl);
    }

    public Map<String, Object> editSlides(String sessionId, Map<String, Object> request, String baseUrl) {
        return normalizeSession(superPptClient.editSlides(sessionId, request), baseUrl);
    }

    public Map<String, Object> reviseDraft(String sessionId, Map<String, Object> request, String baseUrl) {
        Map<String, Object> normalized = normalizeSession(superPptClient.reviseDraft(sessionId, request), baseUrl);
        if (!sessionId.isBlank()) {
            String sessionBase = normalizeBaseUrl(baseUrl) + "/api/ppt/sessions/" + sessionId;
            normalized.put("onlyofficePreviewUrl", sessionBase + "/onlyoffice-preview");
            normalized.put("draftOnlyofficePreviewUrl", sessionBase + "/onlyoffice-preview?artifactName=draft_pptx");
        }
        return normalized;
    }

    public Map<String, Object> finalizeSession(String sessionId, String baseUrl) {
        return normalizeSession(superPptClient.finalizeSession(sessionId), baseUrl);
    }

    public Map<String, Object> getArtifactsByTask(String taskId, String baseUrl) {
        return normalizeSession(superPptClient.getArtifacts(taskId), baseUrl);
    }

    public Map<String, Object> getArtifactsBySession(String sessionId, String baseUrl) {
        return normalizeSession(superPptClient.getArtifacts(sessionId), baseUrl);
    }

    public Map<String, Object> getOnlyOfficePreviewSource(String sessionId, String artifactName, String baseUrl) {
        String requestedArtifact = stringValue(artifactName);
        if (!requestedArtifact.isBlank()) {
            Map<String, Object> artifacts = normalizeSession(superPptClient.getArtifacts(sessionId), baseUrl);
            String artifactUrl = resolveArtifactDownloadUrl(artifacts, requestedArtifact);
            if (artifactUrl.isBlank()) {
                throw new BusinessException(40443, "preview artifact url not found");
            }
            return buildOnlyOfficePreviewSource(sessionId, requestedArtifact, artifactUrl);
        }

        Map<String, Object> draft = normalizeSession(superPptClient.getDraft(sessionId), baseUrl);
        String draftUrl = firstNonBlank(
                draft.get("downloadUrl"),
                nestedDownloadUrl(draft, "draft_pptx"),
                nestedDownloadUrl(draft, "pptx")
        );
        if (!draftUrl.isBlank()) {
            return buildOnlyOfficePreviewSource(sessionId, "draft_pptx", draftUrl);
        }

        Map<String, Object> artifacts = normalizeSession(superPptClient.getArtifacts(sessionId), baseUrl);
        String finalUrl = firstNonBlank(
                resolveArtifactDownloadUrl(artifacts, "pptx"),
                artifacts.get("downloadUrl")
        );
        if (!finalUrl.isBlank()) {
            return buildOnlyOfficePreviewSource(sessionId, "pptx", finalUrl);
        }
        throw new BusinessException(40444, "onlyoffice preview url is not ready");
    }

    public Map<String, Object> getDigitalHumanPlugin(String sessionId, String baseUrl) {
        return normalizeSession(superPptClient.getDigitalHumanPlugin(sessionId), baseUrl);
    }

    public Map<String, Object> triggerDigitalHumanPlugin(String sessionId, Map<String, Object> request, String baseUrl) {
        return normalizeSession(superPptClient.triggerDigitalHumanPlugin(sessionId, request), baseUrl);
    }

    public SuperPptClient.DownloadedFile downloadTaskArtifact(String taskId, String artifactName) {
        return superPptClient.downloadArtifact(taskId, artifactName);
    }

    public SuperPptClient.DownloadedFile downloadSessionArtifact(String sessionId, String artifactName) {
        return superPptClient.downloadArtifact(sessionId, artifactName);
    }

    private Map<String, Object> buildSessionRequest(Map<String, Object> request) {
        Map<String, Object> incoming = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        PlatformMockService.ChatGenerationContext sessionContext = resolveSessionContext(incoming);
        String userInput = resolveUserInput(incoming, sessionContext);
        if (userInput.isBlank()) {
            throw new BusinessException(40010, "user_input or prompt is required");
        }

        Map<String, Object> sessionRequest = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            if (!LEGACY_CREATE_KEYS.contains(entry.getKey())) {
                sessionRequest.put(entry.getKey(), entry.getValue());
            }
        }
        sessionRequest.put("user_input", userInput);
        sessionRequest.put("user_assets", resolveUserAssets(incoming, sessionContext));

        Map<String, Object> pipelineOptions = resolvePipelineOptions(incoming);
        if (!pipelineOptions.isEmpty()) {
            sessionRequest.put("pipeline_options", pipelineOptions);
        }
        return sessionRequest;
    }

    private Map<String, Object> buildAppendAssetsRequest(Map<String, Object> request) {
        Map<String, Object> incoming = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        List<Map<String, Object>> userAssets = resolveAppendUserAssets(incoming);
        if (userAssets.isEmpty()) {
            throw new BusinessException(40046, "user_assets or upload_id is required");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_assets", userAssets);

        String instructions = firstNonBlank(
                incoming.get("instructions"),
                incoming.get("instruction"),
                incoming.get("prompt"),
                incoming.get("content")
        );
        if (!instructions.isBlank()) {
            payload.put("instructions", instructions);
        }
        copyIfPresent(incoming, payload, "action");
        copyIfPresent(incoming, payload, "pipeline_options");
        copyIfPresent(incoming, payload, "pipelineOptions");
        copyIfPresent(incoming, payload, "runtime_mode");
        copyIfPresent(incoming, payload, "runtimeMode");
        copyIfPresent(incoming, payload, "force_restart");
        copyIfPresent(incoming, payload, "forceRestart");
        return payload;
    }

    private PlatformMockService.ChatGenerationContext resolveSessionContext(Map<String, Object> request) {
        String sessionId = firstNonBlank(request.get("sessionId"), request.get("session_id"));
        if (sessionId.isBlank() || platformMockService == null) {
            return null;
        }
        return platformMockService.getChatGenerationContext(sessionId);
    }

    private String resolveUserInput(Map<String, Object> request, PlatformMockService.ChatGenerationContext sessionContext) {
        String userInput = firstNonBlank(
                request.get("user_input"),
                request.get("prompt"),
                request.get("content")
        );
        if (!userInput.isBlank()) {
            return userInput;
        }
        Map<String, Object> requirements = mapValue(request.get("requirements"));
        userInput = firstNonBlank(
                requirements.get("prompt"),
                requirements.get("content"),
                requirements.get("topic"),
                requirements.get("outline")
        );
        if (!userInput.isBlank()) {
            return userInput;
        }
        if (sessionContext != null) {
            return stringValue(sessionContext.prompt());
        }
        return "";
    }

    private List<Map<String, Object>> resolveUserAssets(Map<String, Object> request, PlatformMockService.ChatGenerationContext sessionContext) {
        List<Map<String, Object>> resolved = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();

        for (Map<String, Object> asset : asMapList(request.get("user_assets"))) {
            appendUserAsset(resolved, uniqueKeys, normalizeProvidedUserAsset(asset));
        }

        for (Map<String, Object> attachment : mergeAttachments(request.get("attachments"), sessionContext)) {
            appendUserAsset(resolved, uniqueKeys, resolveLegacyAttachmentAsset(attachment));
        }
        return resolved;
    }

    private List<Map<String, Object>> resolveAppendUserAssets(Map<String, Object> request) {
        List<Map<String, Object>> resolved = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();

        for (Map<String, Object> asset : mergeAppendAssetInputs(request)) {
            appendUserAsset(resolved, uniqueKeys, normalizeAppendUserAsset(asset));
        }

        Map<String, Object> rootAsset = normalizeAppendUserAsset(request);
        appendUserAsset(resolved, uniqueKeys, rootAsset);
        return resolved;
    }

    private List<Map<String, Object>> mergeAppendAssetInputs(Map<String, Object> request) {
        List<Map<String, Object>> merged = new ArrayList<>();
        merged.addAll(asMapList(request.get("user_assets")));
        merged.addAll(asMapList(request.get("userAssets")));
        merged.addAll(asMapList(request.get("assets")));
        merged.addAll(asMapList(request.get("attachments")));
        return merged;
    }

    private Map<String, Object> normalizeAppendUserAsset(Map<String, Object> asset) {
        if (asset == null || asset.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> candidate = new LinkedHashMap<>(asset);
        boolean hasUploadReference = !firstNonBlank(
                candidate.get("upload_id"),
                candidate.get("uploadId"),
                candidate.get("path")
        ).isBlank();
        boolean hasAssetType = !firstNonBlank(
                candidate.get("type"),
                candidate.get("assetType"),
                candidate.get("asset_type")
        ).isBlank();
        if (hasUploadReference && !hasAssetType) {
            candidate.put("type", "document");
        }
        return normalizeProvidedUserAsset(candidate);
    }

    private List<Map<String, Object>> mergeAttachments(Object requestAttachments, PlatformMockService.ChatGenerationContext sessionContext) {
        List<Map<String, Object>> merged = new ArrayList<>(asMapList(requestAttachments));
        if (sessionContext == null) {
            return merged;
        }
        for (Map<String, Object> item : sessionContext.attachments()) {
            String signature = attachmentSignature(item);
            boolean exists = merged.stream().anyMatch(existing -> signature.equals(attachmentSignature(existing)));
            if (!signature.isBlank() && !exists) {
                merged.add(item);
            }
        }
        return merged;
    }

    private String attachmentSignature(Map<String, Object> attachment) {
        return firstNonBlank(
                attachment.get("upload_id"),
                attachment.get("uploadId"),
                attachment.get("fileId"),
                attachment.get("file_id"),
                attachment.get("path")
        );
    }

    private Map<String, Object> normalizeProvidedUserAsset(Map<String, Object> asset) {
        if (asset == null || asset.isEmpty()) {
            return Map.of();
        }
        String type = resolveAssetType(
                firstNonBlank(asset.get("fileName"), asset.get("name")),
                firstNonBlank(asset.get("contentType"), asset.get("content_type")),
                firstNonBlank(asset.get("type"), asset.get("assetType"), asset.get("asset_type"))
        );
        String uploadId = firstNonBlank(asset.get("upload_id"), asset.get("uploadId"));
        String path = firstNonBlank(asset.get("path"));
        if (type.isBlank() || (uploadId.isBlank() && path.isBlank())) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("type", type);
        if (!uploadId.isBlank()) {
            normalized.put("upload_id", uploadId);
        }
        if (!path.isBlank()) {
            normalized.put("path", path);
        }
        String role = firstNonBlank(asset.get("role"), defaultRole(type));
        if (!role.isBlank()) {
            normalized.put("role", role);
        }
        return normalized;
    }

    private Map<String, Object> resolveLegacyAttachmentAsset(Map<String, Object> attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return Map.of();
        }

        String providedUploadId = firstNonBlank(attachment.get("upload_id"), attachment.get("uploadId"));
        if (!providedUploadId.isBlank()) {
            Map<String, Object> normalized = normalizeProvidedUserAsset(attachment);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        String path = firstNonBlank(attachment.get("path"));
        if (!path.isBlank()) {
            return normalizeProvidedUserAsset(attachment);
        }

        if (platformMockService == null) {
            return Map.of();
        }

        String fileId = firstNonBlank(attachment.get("fileId"), attachment.get("file_id"));
        if (fileId.isBlank()) {
            return Map.of();
        }

        PlatformMockService.FileContent fileContent;
        try {
            fileContent = platformMockService.getFileContent(fileId);
        } catch (BusinessException ex) {
            return Map.of();
        }

        String assetType = resolveAssetType(
                fileContent.fileName(),
                fileContent.contentType(),
                firstNonBlank(attachment.get("type"), attachment.get("assetType"), attachment.get("asset_type"))
        );
        String role = firstNonBlank(attachment.get("role"), defaultRole(assetType));
        Map<String, Object> uploadResult = superPptClient.upload(
                new SuperPptClient.UploadBinary(fileContent.fileName(), fileContent.contentType(), fileContent.content()),
                assetType,
                role
        );
        String uploadId = firstNonBlank(uploadResult.get("upload_id"), uploadResult.get("uploadId"));
        if (uploadId.isBlank()) {
            throw new BusinessException(50243, "SuperPPT upload response is missing upload_id");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("type", assetType);
        normalized.put("upload_id", uploadId);
        if (!role.isBlank()) {
            normalized.put("role", role);
        }
        return normalized;
    }

    private Map<String, Object> resolvePipelineOptions(Map<String, Object> request) {
        Map<String, Object> pipelineOptions = new LinkedHashMap<>(mapValue(request.get("pipeline_options")));
        putIfAbsent(pipelineOptions, "pipeline_id", firstNonBlank(request.get("pipelineId"), request.get("pipeline_id")));
        putIfAbsent(pipelineOptions, "runtime_mode", firstNonBlank(request.get("runtimeMode"), request.get("runtime_mode")));

        Object forceRestart = request.containsKey("forceRestart")
                ? request.get("forceRestart")
                : request.get("force_restart");
        if (forceRestart != null && !pipelineOptions.containsKey("force_restart")) {
            pipelineOptions.put("force_restart", forceRestart);
        }
        return pipelineOptions;
    }

    private Object normalizeCollection(Object payload, String baseUrl) {
        if (payload instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof Map<?, ?> map ? normalizeSession(castMap(map), baseUrl) : item)
                    .toList();
        }
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>(castMap(map));
            for (String key : List.of("items", "sessions", "tasks")) {
                Object value = normalized.get(key);
                if (value instanceof List<?> list) {
                    normalized.put(key, list.stream()
                            .map(item -> item instanceof Map<?, ?> entry ? normalizeSession(castMap(entry), baseUrl) : item)
                            .toList());
                }
            }
            if (looksLikeSessionPayload(normalized)) {
                return normalizeSession(normalized, baseUrl);
            }
            return normalized;
        }
        return payload;
    }

    private boolean looksLikeSessionPayload(Map<String, Object> payload) {
        return payload.containsKey("session_id")
                || payload.containsKey("task_id")
                || payload.containsKey("sessionId")
                || payload.containsKey("taskId");
    }

    private Map<String, Object> normalizeUpload(Map<String, Object> payload) {
        Map<String, Object> normalized = new LinkedHashMap<>(payload);
        aliasTextField(normalized, "upload_id", "uploadId");
        aliasTextField(normalized, "asset_type", "assetType");
        return normalized;
    }

    private Map<String, Object> normalizeSession(Map<String, Object> payload, String baseUrl) {
        Map<String, Object> normalized = new LinkedHashMap<>(payload);
        String sessionId = firstNonBlank(
                normalized.get("session_id"),
                normalized.get("task_id"),
                normalized.get("sessionId"),
                normalized.get("taskId")
        );
        if (!sessionId.isBlank()) {
            normalized.put("task_id", sessionId);
            normalized.put("session_id", sessionId);
            normalized.put("taskId", sessionId);
            normalized.put("sessionId", sessionId);
        }

        aliasTextField(normalized, "pipeline_id", "pipelineId");
        aliasTextField(normalized, "current_stage", "currentStage");
        aliasTextField(normalized, "next_action", "nextAction");
        aliasTextField(normalized, "minio_download_url", "downloadUrl");

        if (!normalized.containsKey("errorMessage") && normalized.containsKey("error")) {
            normalized.put("errorMessage", normalized.get("error"));
        }
        if (!normalized.containsKey("downloadUrls") && normalized.containsKey("minio_download_urls")) {
            normalized.put("downloadUrls", normalized.get("minio_download_urls"));
        }

        String apiBase = normalizeBaseUrl(baseUrl);
        if (!apiBase.isBlank() && !sessionId.isBlank()) {
            String taskBase = apiBase + "/api/ppt/tasks/" + sessionId;
            String sessionBase = apiBase + "/api/ppt/sessions/" + sessionId;
            normalized.put("pollUrl", taskBase);
            normalized.put("downloadInfoUrl", taskBase + "/download-info");
            normalized.put("sessionUrl", sessionBase);
            normalized.put("assetsUrl", sessionBase + "/assets");
            normalized.put("outlineUrl", sessionBase + "/outline");
            normalized.put("draftUrl", sessionBase + "/draft");
            normalized.put("onlyofficePreviewUrl", sessionBase + "/onlyoffice-preview");
            normalized.put("draftOnlyofficePreviewUrl", sessionBase + "/onlyoffice-preview?artifactName=draft_pptx");
            normalized.put("artifactsUrl", sessionBase + "/artifacts");
        }
        return normalized;
    }

    private Map<String, Object> buildOnlyOfficePreviewSource(String sessionId, String artifactName, String downloadUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("artifactName", artifactName);
        result.put("downloadUrl", downloadUrl);
        result.put("fileId", buildPreviewFileId(sessionId, artifactName, downloadUrl));
        result.put("fileName", buildPreviewFileName(sessionId, artifactName));
        return result;
    }

    private String buildPreviewFileId(String sessionId, String artifactName, String downloadUrl) {
        String raw = firstNonBlank(sessionId) + firstNonBlank(artifactName);
        if ("draft_pptx".equalsIgnoreCase(stringValue(artifactName)) && !stringValue(downloadUrl).isBlank()) {
            raw = raw + "_" + Integer.toUnsignedString(stringValue(downloadUrl).hashCode(), 36);
        }
        String sanitized = raw.replaceAll("[^A-Za-z0-9]", "");
        if (!sanitized.isBlank()) {
            return sanitized;
        }
        return "preview" + Math.abs(raw.hashCode());
    }

    private String buildPreviewFileName(String sessionId, String artifactName) {
        if ("pptx".equalsIgnoreCase(artifactName)) {
            return sessionId + ".pptx";
        }
        if ("draft_pptx".equalsIgnoreCase(artifactName)) {
            return sessionId + "-draft.pptx";
        }
        if (artifactName != null && artifactName.contains(".")) {
            return sessionId + "-" + artifactName;
        }
        String normalizedArtifact = stringValue(artifactName);
        String suffix = resolveArtifactSuffix(normalizedArtifact);
        String label = removeArtifactSuffixToken(normalizedArtifact, suffix);
        if (label.isBlank()) {
            return sessionId + "." + suffix;
        }
        return sessionId + "-" + label + "." + suffix;
    }

    private String resolveArtifactSuffix(String artifactName) {
        String normalized = artifactName.toLowerCase(Locale.ROOT);
        for (String suffix : List.of("pptx", "ppt", "docx", "doc", "xlsx", "xls", "pdf")) {
            if (normalized.equals(suffix)
                    || normalized.endsWith("_" + suffix)
                    || normalized.endsWith("-" + suffix)
                    || normalized.contains("_" + suffix + "_")
                    || normalized.contains("-" + suffix + "-")) {
                return suffix;
            }
        }
        if (normalized.contains("word") || normalized.contains("doc") || normalized.contains("teaching_plan")) {
            return "docx";
        }
        return "pptx";
    }

    private String removeArtifactSuffixToken(String artifactName, String suffix) {
        String normalized = stringValue(artifactName);
        if (normalized.equalsIgnoreCase(suffix)) {
            return "";
        }
        return normalized
                .replaceFirst("(?i)[_-]" + suffix + "$", "")
                .replaceFirst("(?i)[_-]" + suffix + "([_-])", "$1");
    }

    private String resolveArtifactDownloadUrl(Map<String, Object> payload, String artifactName) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        Map<String, Object> downloadUrls = mapValue(payload.get("downloadUrls"));
        if (downloadUrls.isEmpty()) {
            downloadUrls = mapValue(payload.get("minio_download_urls"));
        }
        return firstNonBlank(downloadUrls.get(artifactName));
    }

    private String nestedDownloadUrl(Map<String, Object> payload, String artifactName) {
        return resolveArtifactDownloadUrl(payload, artifactName);
    }

    private void aliasTextField(Map<String, Object> payload, String snakeCaseKey, String camelCaseKey) {
        String value = firstNonBlank(payload.get(snakeCaseKey), payload.get(camelCaseKey));
        if (value.isBlank()) {
            return;
        }
        payload.put(snakeCaseKey, value);
        payload.put(camelCaseKey, value);
    }

    private void appendUserAsset(List<Map<String, Object>> assets, Set<String> uniqueKeys, Map<String, Object> asset) {
        if (asset == null || asset.isEmpty()) {
            return;
        }
        String key = firstNonBlank(asset.get("upload_id"), asset.get("path")) + "|" + firstNonBlank(asset.get("type"));
        if (!uniqueKeys.add(key)) {
            return;
        }
        assets.add(asset);
    }

    private SuperPptClient.UploadBinary toUploadBinary(MultipartFile file) {
        try {
            return new SuperPptClient.UploadBinary(
                    file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "upload.bin" : file.getOriginalFilename(),
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException ex) {
            throw new BusinessException(50041, "failed to read uploaded file");
        }
    }

    private String resolveAssetType(String fileName, String contentType, String preferredType) {
        if (!preferredType.isBlank()) {
            return preferredType;
        }
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String suffix = suffix(fileName);
        if ("application/pdf".equals(normalizedContentType) || "pdf".equals(suffix)) {
            return "pdf";
        }
        if (normalizedContentType.startsWith("audio/") || List.of("mp3", "wav", "m4a", "aac", "ogg").contains(suffix)) {
            return "audio";
        }
        if (normalizedContentType.startsWith("image/") || List.of("png", "jpg", "jpeg", "gif", "bmp", "webp").contains(suffix)) {
            return "image";
        }
        return suffix.isBlank() ? "file" : suffix;
    }

    private String defaultRole(String assetType) {
        return switch (assetType) {
            case "pdf" -> "handout";
            case "audio" -> "teacher_audio";
            case "image" -> "reference_image";
            default -> "";
        };
    }

    private String suffix(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void putIfAbsent(Map<String, Object> target, String key, String value) {
        if (value.isBlank() || target.containsKey(key)) {
            return;
        }
        target.put(key, value);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }
}
