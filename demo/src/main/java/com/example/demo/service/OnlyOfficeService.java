package com.example.demo.service;

import com.example.demo.config.OnlyOfficeProperties;
import com.example.demo.dto.OnlyOfficeH5GamePrepareRequest;
import com.example.demo.dto.WpsFileRegisterRequest;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.WpsFileMapper;
import com.example.demo.model.WpsFileRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class OnlyOfficeService {

    private static final Duration REMOTE_FETCH_TIMEOUT = Duration.ofMinutes(5);
    private static final String DEFAULT_H5_PREVIEW_IMAGE_DATA_URI =
            "data:image/png;base64,"
                    + "iVBORw0KGgoAAAANSUhEUgAAAoAAAAHgCAYAAAA10dzkAAAACXBIWXMAAAsSAAALEgHS3X78AAAF"
                    + "qElEQVR4nO3VMQEAIAzAMMC/5yFjRxMFfXpm5gBA1usAANiVAQAwGQMAYDYGAMBkDACA2RgAAJMxAA"
                    + "BmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA"
                    + "2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYD"
                    + "YGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiN"
                    + "AQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYw"
                    + "AATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgA"
                    + "AJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAM"
                    + "BkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAw"
                    + "GQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATM"
                    + "YAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMx"
                    + "AABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDACA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMAYDYGAMBkDA"
                    + "CA2RgAAJMxAABmYwAATMYAAJiNAQAwGQMA4A1lzgLfaZkV0AAAAABJRU5ErkJggg==";

    private final WpsFileMapper fileMapper;
    private final MinioStorageService minioStorageService;
    private final OnlyOfficeProperties onlyOfficeProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ConcurrentMap<String, CompletableFuture<WpsFileRecord>> remotePreviewLoads = new ConcurrentHashMap<>();

    public OnlyOfficeService(
            WpsFileMapper fileMapper,
            MinioStorageService minioStorageService,
            OnlyOfficeProperties onlyOfficeProperties,
            ObjectMapper objectMapper,
            HttpClient outboundHttpClient
    ) {
        this.fileMapper = fileMapper;
        this.minioStorageService = minioStorageService;
        this.onlyOfficeProperties = onlyOfficeProperties;
        this.objectMapper = objectMapper;
        this.httpClient = outboundHttpClient;
    }

    public Map<String, Object> registerExistingFile(WpsFileRegisterRequest request, String baseUrl) {
        String fileId = normalizeFileId(request.fileId());
        String objectKey = normalize(request.objectKey(), "objectKey is required");
        String bucketName = request.bucketName() == null || request.bucketName().isBlank()
                ? minioStorageService.defaultBucket()
                : request.bucketName().trim();

        MinioStorageService.FileStat fileStat = minioStorageService.statObject(bucketName, objectKey);
        WpsFileRecord existing = fileMapper.findByFileId(fileId);
        WpsFileRecord record = existing == null ? new WpsFileRecord() : existing;
        long now = Instant.now().getEpochSecond();
        String creatorId = request.creatorId() == null || request.creatorId().isBlank() ? "404" : request.creatorId().trim();

        record.setFileId(fileId);
        record.setBucketName(bucketName);
        record.setObjectKey(objectKey);
        record.setFileName(resolveFileName(request.fileName(), objectKey));
        record.setVersion(existing == null || existing.getVersion() == null || existing.getVersion() < 1 ? 1L : existing.getVersion());
        record.setSize(fileStat.size());
        record.setCreatorId(existing == null ? creatorId : existing.getCreatorId());
        record.setModifierId(existing == null ? creatorId : existing.getModifierId());
        record.setCreateTime(existing == null ? now : existing.getCreateTime());
        record.setModifyTime(fileStat.lastModifiedEpochSecond());

        if (existing == null) {
            fileMapper.insert(record);
        } else {
            fileMapper.updateByFileId(record);
        }

        return buildRegistrationResult(record, creatorId, baseUrl);
    }

    public Map<String, Object> getEditorConfig(String fileId, String userId, String mode, String baseUrl) {
        WpsFileRecord record = requireFileRecord(fileId);
        MinioStorageService.FileStat stat = minioStorageService.statObject(record.getBucketName(), record.getObjectKey());
        updateStat(record, stat);

        String currentUserId = userId == null || userId.isBlank() ? "404" : userId.trim();
        String resolvedMode = resolveMode(mode);
        String publicBaseUrl = resolvePublicBaseUrl(baseUrl);
        String callbackUrl = publicBaseUrl + "/api/onlyoffice/callback/" + record.getFileId();
        String contentUrl = buildContentUrl(record, publicBaseUrl);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", suffix(record.getFileName()));
        document.put("key", buildDocumentKey(record));
        document.put("title", record.getFileName());
        document.put("url", contentUrl);
        document.put("permissions", buildPermissions(resolvedMode));

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", currentUserId);
        user.put("name", "User-" + currentUserId);

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("autosave", true);
        customization.put("forcesave", true);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", resolvedMode);
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", user);
        editorConfig.put("customization", customization);
        Map<String, Object> plugins = buildPluginsConfig(record, publicBaseUrl);
        if (!plugins.isEmpty()) {
            editorConfig.put("plugins", plugins);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", resolveDocumentType(record.getFileName()));
        config.put("document", document);
        config.put("editorConfig", editorConfig);
        String token = signJwt(config);
        if (!token.isBlank()) {
            config.put("token", token);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", record.getFileId());
        result.put("fileName", record.getFileName());
        result.put("documentServerUrl", requiredDocumentServerUrl());
        result.put("apiJsUrl", requiredDocumentServerUrl() + "/web-apps/apps/api/documents/api.js");
        result.put("editorConfig", config);
        result.put("directDownloadUrl", contentUrl);
        result.put("callbackUrl", callbackUrl);
        result.put("mode", resolvedMode);
        result.put("editable", "edit".equals(resolvedMode));
        return result;
    }

    public Map<String, Object> buildRemotePreviewConfig(String fileId, String fileName, String downloadUrl, String userId) {
        return buildRemotePreviewConfig(fileId, fileName, downloadUrl, userId, "");
    }

    public Map<String, Object> buildRemotePreviewConfig(String fileId, String fileName, String downloadUrl, String userId, String baseUrl) {
        String normalizedUrl = normalizeUrl(downloadUrl, "downloadUrl is required");
        String normalizedFileId = normalizeFileId(defaultIfBlank(fileId, "remote-preview"));
        String normalizedFileName = defaultIfBlank(trimToEmpty(fileName), normalizedFileId + ".pptx");
        String currentUserId = userId == null || userId.isBlank() ? "404" : userId.trim();

        WpsFileRecord record = ensureRemotePreviewFile(normalizedFileId, normalizedFileName, normalizedUrl, currentUserId);
        Map<String, Object> result = getEditorConfig(record.getFileId(), currentUserId, "edit", baseUrl);
        result.put("sourceUrl", normalizedUrl);
        result.put("mode", "edit");
        result.put("editable", true);
        result.put("sourceType", "superppt-minio");
        return result;
    }

    public Map<String, Object> prepareH5GameInsertion(
            String fileId,
            OnlyOfficeH5GamePrepareRequest request,
            String baseUrl
    ) {
        WpsFileRecord record = requireFileRecord(fileId);
        String publicBaseUrl = resolvePublicBaseUrl(baseUrl);
        String gameName = defaultIfBlank(request == null ? null : request.gameName(), "H5 Game");
        String htmlUrl = resolveGameHtmlUrl(request, "htmlUrl is required");
        String previewImageUrl = resolveGamePreviewImageUrl(request, publicBaseUrl);
        String pluginGuid = resolvePluginGuid(request == null ? null : request.pluginGuid());
        double widthMm = resolveSize(request == null ? null : request.widthMm(), onlyOfficeProperties.getH5Game().getDefaultWidthMm(), 120.0, "widthMm");
        double heightMm = resolveSize(request == null ? null : request.heightMm(), onlyOfficeProperties.getH5Game().getDefaultHeightMm(), 90.0, "heightMm");

        long widthEmu = mmToEmu(widthMm);
        long heightEmu = mmToEmu(heightMm);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "h5-game");
        payload.put("fileId", record.getFileId());
        payload.put("documentTitle", record.getFileName());
        payload.put("gameName", gameName);
        payload.put("gameUrl", htmlUrl);
        payload.put("coverImageUrl", previewImageUrl);
        payload.put("pluginGuid", pluginGuid);
        payload.put("widthMm", widthMm);
        payload.put("heightMm", heightMm);

        Map<String, Object> oleData = new LinkedHashMap<>();
        oleData.put("data", jsonString(payload));
        oleData.put("imgSrc", previewImageUrl);
        oleData.put("guid", pluginGuid);
        oleData.put("width", widthMm);
        oleData.put("height", heightMm);
        oleData.put("widthPix", widthEmu);
        oleData.put("heightPix", heightEmu);

        Map<String, Object> pluginBootstrap = new LinkedHashMap<>();
        pluginBootstrap.put("prepareApi", publicBaseUrl + "/api/onlyoffice/files/" + record.getFileId() + "/h5-games/prepare");
        pluginBootstrap.put("pluginGuid", pluginGuid);
        pluginBootstrap.put("defaultWidthMm", widthMm);
        pluginBootstrap.put("defaultHeightMm", heightMm);
        pluginBootstrap.put("supportedEditors", List.of("slide"));
        pluginBootstrap.put("sampleCommand", "window.Asc.plugin.executeMethod(\"AddOleObject\", [oleData]);");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", record.getFileId());
        result.put("fileName", record.getFileName());
        result.put("documentVersion", record.getVersion());
        result.put("pluginGuid", pluginGuid);
        result.put("gameName", gameName);
        result.put("htmlUrl", htmlUrl);
        result.put("coverImageUrl", previewImageUrl);
        result.put("widthMm", widthMm);
        result.put("heightMm", heightMm);
        result.put("oleData", oleData);
        result.put("pluginBootstrap", pluginBootstrap);
        return result;
    }

    public Map<String, Object> getH5GamePluginInstallInfo(String baseUrl) {
        String publicBaseUrl = resolvePublicBaseUrl(baseUrl);
        String pluginFolder = resolvePluginFolder();
        String pluginBaseUrl = publicBaseUrl + "/static/onlyoffice-plugins/" + pluginFolder;
        String sampleGameUrl = publicBaseUrl + "/static/h5-games/sample-game/index.html";
        String previewImageUrl = resolvePreviewImageUrl(null, publicBaseUrl);

        Map<String, Object> sourcePackage = new LinkedHashMap<>();
        sourcePackage.put("pluginFolder", pluginFolder);
        sourcePackage.put("pluginGuid", resolvePluginGuid(null));
        sourcePackage.put("configFileUrl", pluginBaseUrl + "/config.json");
        sourcePackage.put("indexFileUrl", pluginBaseUrl + "/index.html");
        sourcePackage.put("codeFileUrl", pluginBaseUrl + "/code.js");
        sourcePackage.put("iconFileUrl", pluginBaseUrl + "/resources/img/icon.svg");
        sourcePackage.put("sampleGameUrl", sampleGameUrl);
        sourcePackage.put("sampleCoverUrl", previewImageUrl);
        sourcePackage.put("note", "These URLs expose the source files in the backend project. Copy this folder into ONLYOFFICE sdkjs-plugins for runtime use.");

        Map<String, Object> containerInstall = new LinkedHashMap<>();
        containerInstall.put("hostSourceDir", "demo/src/main/resources/static/onlyoffice-plugins/" + pluginFolder);
        containerInstall.put("containerPluginDir", "/var/www/onlyoffice/documentserver/sdkjs-plugins/" + pluginFolder);
        containerInstall.put("runtimeConfigUrl", requiredDocumentServerUrl() + "/sdkjs-plugins/" + pluginFolder + "/config.json");
        containerInstall.put("existingConfiguredConfigUrls", onlyOfficeProperties.getPluginConfigUrls());

        return Map.of(
                "sourcePackage", sourcePackage,
                "containerInstall", containerInstall
        );
    }

    public Map<String, Object> getDownloadInfo(String fileId) {
        WpsFileRecord record = requireFileRecord(fileId);
        return Map.of(
                "fileId", record.getFileId(),
                "fileName", record.getFileName(),
                "version", record.getVersion(),
                "downloadUrl", buildContentUrl(record, resolvePublicBaseUrl(""))
        );
    }

    public Map<String, Object> buildMockPreviewResponse(Map<String, Object> request, String baseUrl) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String apiBaseUrl = defaultIfBlank(stringValue(safeRequest.get("apiBaseUrl")), resolvePublicBaseUrl(baseUrl));
        String bucketName = defaultIfBlank(stringValue(safeRequest.get("bucketName")), "ppt-files");
        String creatorId = defaultIfBlank(stringValue(safeRequest.get("creatorId")), "u100");
        String userId = defaultIfBlank(stringValue(safeRequest.get("userId")), creatorId);
        String mode = resolveMode(stringValue(safeRequest.get("mode")));
        String lang = defaultIfBlank(stringValue(safeRequest.get("lang")), "zh-CN");
        String variant = resolveMockPreviewVariant(safeRequest);

        Map<String, Object> beforeOverrides = mapValue(safeRequest.get("beforeFile"));
        Map<String, Object> afterOverrides = mapValue(safeRequest.get("afterFile"));
        Map<String, Object> directOverrides = mapValue(safeRequest.get("file"));
        Map<String, Object> selectedOverrides = "after".equals(variant) ? afterOverrides : beforeOverrides;
        if (!directOverrides.isEmpty()) {
            selectedOverrides = directOverrides;
        }

        if ("after".equals(variant)) {
            return buildMockPreviewFile(
                "pptchapter2process_edited",
                "第二章 进程管理-融合第9章与12章-修改版.pptx",
                bucketName,
                creatorId,
                userId,
                apiBaseUrl,
                mode,
                lang,
                selectedOverrides
            );
        }
        return buildMockPreviewFile(
                "pptchapter2process",
                "第二章 进程管理-融合第9章与12章.pptx",
                bucketName,
                creatorId,
                userId,
                apiBaseUrl,
                mode,
                lang,
                selectedOverrides
        );
    }

    public FileContent getFileContent(String fileId, Long version) {
        WpsFileRecord record = requireFileRecord(fileId);
        if (version != null && !Objects.equals(version, record.getVersion())) {
            throw new BusinessException(40401, "version not found");
        }
        byte[] content = minioStorageService.getObject(record.getBucketName(), record.getObjectKey());
        String contentType = resolveContentType(record.getFileName());
        return new FileContent(record.getFileName(), contentType, content);
    }

    public Map<String, Object> handleCallback(String fileId, Map<String, Object> body) {
        WpsFileRecord record = requireFileRecord(fileId);
        int status = parseStatus(body.get("status"));
        if (status == 2 || status == 6) {
            String downloadUrl = stringValue(body.get("url"));
            if (downloadUrl.isBlank()) {
                throw new BusinessException(40040, "callback url is required when status indicates save");
            }

            byte[] content = downloadEditedFile(downloadUrl);
            String contentType = resolveContentType(record.getFileName());
            minioStorageService.putObject(record.getBucketName(), record.getObjectKey(), content, contentType);

            MinioStorageService.FileStat stat = minioStorageService.statObject(record.getBucketName(), record.getObjectKey());
            record.setVersion(record.getVersion() + 1);
            record.setSize(stat.size());
            record.setModifierId(resolveModifier(body));
            record.setModifyTime(Instant.now().getEpochSecond());
            fileMapper.updateByFileId(record);
        }
        return Map.of("error", 0);
    }

    private Map<String, Object> buildRegistrationResult(WpsFileRecord record, String userId, String baseUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", record.getFileId());
        result.put("fileName", record.getFileName());
        result.put("bucketName", record.getBucketName());
        result.put("objectKey", record.getObjectKey());
        result.put("version", record.getVersion());
        result.put("editorInfo", getEditorConfig(record.getFileId(), userId, "edit", baseUrl));
        return result;
    }

    private WpsFileRecord ensureRemotePreviewFile(String fileId, String fileName, String sourceUrl, String userId) {
        CompletableFuture<WpsFileRecord> load = new CompletableFuture<>();
        CompletableFuture<WpsFileRecord> inFlight = remotePreviewLoads.putIfAbsent(fileId, load);
        if (inFlight != null) {
            return awaitRemotePreviewLoad(inFlight);
        }
        try {
            WpsFileRecord record = importRemotePreviewFile(fileId, fileName, sourceUrl, userId);
            load.complete(record);
            return record;
        } catch (Exception ex) {
            load.completeExceptionally(ex);
            throw ex;
        } finally {
            remotePreviewLoads.remove(fileId, load);
        }
    }

    private WpsFileRecord importRemotePreviewFile(String fileId, String fileName, String sourceUrl, String userId) {
        WpsFileRecord existing = fileMapper.findByFileId(fileId);
        if (existing != null && previewObjectExists(existing)) {
            return existing;
        }

        byte[] content = downloadRemotePreviewFile(sourceUrl);
        String bucketName = existing == null || trimToEmpty(existing.getBucketName()).isBlank()
                ? minioStorageService.defaultBucket()
                : existing.getBucketName();
        String objectKey = existing == null || trimToEmpty(existing.getObjectKey()).isBlank()
                ? buildRemotePreviewObjectKey(fileId, fileName)
                : existing.getObjectKey();
        String contentType = resolveContentType(fileName);
        minioStorageService.putObject(bucketName, objectKey, content, contentType);

        MinioStorageService.FileStat stat = minioStorageService.statObject(bucketName, objectKey);
        WpsFileRecord record = existing == null ? new WpsFileRecord() : existing;
        long now = Instant.now().getEpochSecond();
        record.setFileId(fileId);
        record.setBucketName(bucketName);
        record.setObjectKey(objectKey);
        record.setFileName(fileName);
        record.setVersion(resolveImportedVersion(existing));
        record.setSize(stat.size());
        record.setCreatorId(existing == null ? userId : existing.getCreatorId());
        record.setModifierId(userId);
        record.setCreateTime(existing == null || existing.getCreateTime() == null ? now : existing.getCreateTime());
        record.setModifyTime(stat.lastModifiedEpochSecond());

        if (existing == null) {
            fileMapper.insert(record);
        } else {
            fileMapper.updateByFileId(record);
        }
        return record;
    }

    private WpsFileRecord awaitRemotePreviewLoad(CompletableFuture<WpsFileRecord> inFlight) {
        try {
            return inFlight.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException(50025, "remote preview download failed: " + cause.getMessage());
        }
    }

    private boolean previewObjectExists(WpsFileRecord record) {
        try {
            minioStorageService.statObject(record.getBucketName(), record.getObjectKey());
            return true;
        } catch (BusinessException ex) {
            if (ex.getCode() == 40420) {
                return false;
            }
            throw ex;
        }
    }

    private Long resolveImportedVersion(WpsFileRecord existing) {
        if (existing == null || existing.getVersion() == null || existing.getVersion() < 1) {
            return 1L;
        }
        return existing.getVersion() + 1;
    }

    private String buildRemotePreviewObjectKey(String fileId, String fileName) {
        return "onlyoffice-preview/" + fileId + "/" + sanitizeObjectFileName(fileName);
    }

    private String sanitizeObjectFileName(String fileName) {
        String normalized = trimToEmpty(fileName).replace("\\", "_").replace("/", "_").replace("\"", "");
        return normalized.isBlank() ? "preview.pptx" : normalized;
    }

    private Map<String, Object> buildPermissions(String mode) {
        boolean editable = "edit".equals(mode);
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", editable);
        permissions.put("download", true);
        permissions.put("print", true);
        permissions.put("comment", editable);
        permissions.put("review", editable);
        permissions.put("fillForms", editable);
        permissions.put("copy", true);
        return permissions;
    }

    private Map<String, Object> buildPluginsConfig(WpsFileRecord record, String publicBaseUrl) {
        List<String> pluginConfigUrls = onlyOfficeProperties.getPluginConfigUrls().stream()
                .map(this::trimToEmpty)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> autostart = onlyOfficeProperties.getAutostartPluginGuids().stream()
                .map(this::trimToEmpty)
                .filter(value -> !value.isBlank())
                .toList();
        Map<String, Object> options = new LinkedHashMap<>(onlyOfficeProperties.getPluginOptions());

        String pluginGuid = resolvePluginGuid(null);
        Map<String, Object> h5Options = new LinkedHashMap<>();
        h5Options.put("fileId", record.getFileId());
        h5Options.put("prepareApi", publicBaseUrl + "/api/onlyoffice/files/" + record.getFileId() + "/h5-games/prepare");
        h5Options.put("pluginGuid", pluginGuid);
        h5Options.put("defaultBucket", minioStorageService.defaultBucket());
        h5Options.put("defaultEntryFile", "index.html");
        h5Options.put("defaultWidthMm", resolveSize(null, onlyOfficeProperties.getH5Game().getDefaultWidthMm(), 120.0, "defaultWidthMm"));
        h5Options.put("defaultHeightMm", resolveSize(null, onlyOfficeProperties.getH5Game().getDefaultHeightMm(), 90.0, "defaultHeightMm"));
        String previewImageUrl = trimToEmpty(onlyOfficeProperties.getH5Game().getPreviewImageUrl());
        if (!previewImageUrl.isBlank()) {
            h5Options.put("previewImageUrl", normalizeUrl(previewImageUrl, "onlyoffice.h5-game.preview-image-url is invalid"));
        }
        options.put(pluginGuid, h5Options);

        if (pluginConfigUrls.isEmpty() && autostart.isEmpty() && options.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> plugins = new LinkedHashMap<>();
        if (!pluginConfigUrls.isEmpty()) {
            plugins.put("pluginsData", pluginConfigUrls);
        }
        if (!autostart.isEmpty()) {
            plugins.put("autostart", autostart);
        }
        if (!options.isEmpty()) {
            plugins.put("options", options);
        }
        return plugins;
    }

    private String buildDocumentKey(WpsFileRecord record) {
        return record.getFileId() + "-v" + record.getVersion();
    }

    private String buildContentUrl(WpsFileRecord record, String publicBaseUrl) {
        return publicBaseUrl + "/api/onlyoffice/files/" + record.getFileId() + "/content?version=" + record.getVersion();
    }

    private String resolveDocumentType(String fileName) {
        String suffix = suffix(fileName);
        return switch (suffix) {
            case "doc", "docx", "odt", "txt", "rtf" -> "word";
            case "xls", "xlsx", "ods", "csv" -> "cell";
            case "ppt", "pptx", "odp" -> "slide";
            default -> "word";
        };
    }

    private String resolveMode(String mode) {
        return "edit";
    }

    private String resolvePluginGuid(String pluginGuid) {
        String configured = defaultIfBlank(pluginGuid, onlyOfficeProperties.getH5Game().getPluginGuid());
        String normalized = trimToEmpty(configured);
        if (normalized.isBlank()) {
            throw new BusinessException(40043, "plugin guid is required");
        }
        return normalized;
    }

    private String resolvePluginFolder() {
        String normalized = trimToEmpty(onlyOfficeProperties.getH5Game().getPluginFolder());
        if (normalized.isBlank()) {
            throw new BusinessException(40046, "plugin folder is required");
        }
        return normalized;
    }

    private String resolvePreviewImageUrl(String requestValue, String publicBaseUrl) {
        String candidate = defaultIfBlank(requestValue, onlyOfficeProperties.getH5Game().getPreviewImageUrl());
        String normalized = trimToEmpty(candidate);
        if (normalized.isBlank()) {
            return DEFAULT_H5_PREVIEW_IMAGE_DATA_URI;
        }
        if (normalized.startsWith("data:image/")) {
            return normalized;
        }

        String resolved = normalizeUrl(normalized, "coverImageUrl is invalid");
        if (resolved.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return DEFAULT_H5_PREVIEW_IMAGE_DATA_URI;
        }
        return resolved;
    }

    private String resolveGameHtmlUrl(OnlyOfficeH5GamePrepareRequest request, String errorMessage) {
        if (request == null) {
            throw new BusinessException(40045, errorMessage);
        }
        String directUrl = trimToEmpty(request.htmlUrl());
        if (!directUrl.isBlank()) {
            return normalizeUrl(directUrl, errorMessage);
        }

        String bucketName = defaultIfBlank(request.bucketName(), minioStorageService.defaultBucket());
        String gameDir = normalizeGameDir(request.gameDir());
        String entryFile = defaultIfBlank(request.entryFile(), "index.html");
        String objectKey = combineObjectKey(gameDir, entryFile);
        minioStorageService.statObject(bucketName, objectKey);
        return minioStorageService.publicObjectUrl(bucketName, objectKey);
    }

    private String resolveGamePreviewImageUrl(OnlyOfficeH5GamePrepareRequest request, String publicBaseUrl) {
        if (request != null) {
            String gameDir = trimToEmpty(request.gameDir());
            if (!gameDir.isBlank()) {
                String bucketName = defaultIfBlank(request.bucketName(), minioStorageService.defaultBucket());
                for (String fileName : List.of("cover.png", "cover.jpg", "cover.jpeg", "cover.webp")) {
                    String objectKey = combineObjectKey(gameDir, fileName);
                    try {
                        minioStorageService.statObject(bucketName, objectKey);
                        return minioStorageService.publicObjectUrl(bucketName, objectKey);
                    } catch (BusinessException ignored) {
                        // Fall back to the next candidate or the default preview image.
                    }
                }
            }
        }
        return resolvePreviewImageUrl(null, publicBaseUrl);
    }

    private String normalizeGameDir(String gameDir) {
        String normalized = trimToEmpty(gameDir).replace("\\", "/").replaceAll("/+", "/");
        if (normalized.isBlank()) {
            throw new BusinessException(40047, "gameDir is required when htmlUrl is not provided");
        }
        String cleaned = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isBlank()) {
            throw new BusinessException(40047, "gameDir is required when htmlUrl is not provided");
        }
        return cleaned;
    }

    private String combineObjectKey(String gameDir, String fileName) {
        String normalizedDir = normalizeGameDir(gameDir);
        String normalizedFileName = trimToEmpty(fileName).replace("\\", "/");
        if (normalizedFileName.isBlank()) {
            throw new BusinessException(40048, "entryFile is required");
        }
        String cleanedFileName = normalizedFileName.startsWith("/") ? normalizedFileName.substring(1) : normalizedFileName;
        return normalizedDir + "/" + cleanedFileName;
    }

    private double resolveSize(Double requestSize, Double configuredSize, double fallback, String fieldName) {
        double value = requestSize != null ? requestSize : (configuredSize != null ? configuredSize : fallback);
        if (value <= 0) {
            throw new BusinessException(40044, fieldName + " must be greater than 0");
        }
        return value;
    }

    private long mmToEmu(double mm) {
        return Math.round(mm * 36000);
    }

    private String jsonString(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(50027, "h5 game payload serialization failed: " + ex.getMessage());
        }
    }

    private String normalizeUrl(String value, String message) {
        String normalized = trimToEmpty(value);
        if (normalized.isBlank()) {
            throw new BusinessException(40045, message);
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null) {
                throw new IllegalArgumentException("missing scheme");
            }
            String lower = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(lower) && !"https".equals(lower)) {
                throw new IllegalArgumentException("unsupported scheme");
            }
            return normalized;
        } catch (Exception ex) {
            throw new BusinessException(40045, message);
        }
    }

    private byte[] downloadEditedFile(String downloadUrl) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(REMOTE_FETCH_TIMEOUT)
                    .GET();
            String secret = trimToEmpty(onlyOfficeProperties.getJwtSecret());
            if (!secret.isBlank()) {
                String token = signJwt(Map.of("url", downloadUrl));
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(50025, "onlyoffice download failed with status " + response.statusCode());
            }
            return response.body();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(50025, "onlyoffice download failed: " + ex.getMessage());
        }
    }

    private byte[] downloadRemotePreviewFile(String downloadUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(REMOTE_FETCH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(50025, "remote preview download failed with status " + response.statusCode());
            }
            return response.body();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(50025, "remote preview download failed: " + ex.getMessage());
        }
    }

    private String resolveModifier(Map<String, Object> body) {
        Object users = body.get("users");
        if (users instanceof List<?> list && !list.isEmpty()) {
            Object last = list.get(list.size() - 1);
            String value = String.valueOf(last);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "404";
    }

    private String resolveContentType(String fileName) {
        String suffix = suffix(fileName);
        return switch (suffix) {
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    private String requiredDocumentServerUrl() {
        String value = trimTrailingSlash(onlyOfficeProperties.getDocumentServerUrl(), "onlyoffice document server url is required");
        if (value.isBlank()) {
            throw new BusinessException(40041, "onlyoffice document server url is required");
        }
        return value;
    }

    private String resolvePublicBaseUrl(String fallbackBaseUrl) {
        String configured = onlyOfficeProperties.getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return trimTrailingSlash(configured, "onlyoffice public base url is required");
        }
        return trimTrailingSlash(fallbackBaseUrl, "onlyoffice public base url is required");
    }

    private WpsFileRecord requireFileRecord(String fileId) {
        String normalizedFileId = normalizeFileId(fileId);
        WpsFileRecord record = fileMapper.findByFileId(normalizedFileId);
        if (record != null) {
            return record;
        }

        MinioStorageService.FileStat stat = minioStorageService.statObject(minioStorageService.defaultBucket(), normalizedFileId);
        WpsFileRecord fallback = new WpsFileRecord();
        fallback.setFileId(normalizedFileId);
        fallback.setBucketName(minioStorageService.defaultBucket());
        fallback.setObjectKey(normalizedFileId);
        fallback.setFileName(normalizedFileId);
        fallback.setVersion(1L);
        fallback.setSize(stat.size());
        fallback.setCreatorId("404");
        fallback.setModifierId("404");
        fallback.setCreateTime(stat.lastModifiedEpochSecond());
        fallback.setModifyTime(stat.lastModifiedEpochSecond());
        fileMapper.insert(fallback);
        return fallback;
    }

    private void updateStat(WpsFileRecord record, MinioStorageService.FileStat stat) {
        boolean dirty = false;
        if (!Objects.equals(record.getSize(), stat.size())) {
            record.setSize(stat.size());
            dirty = true;
        }
        if (!Objects.equals(record.getModifyTime(), stat.lastModifiedEpochSecond())) {
            record.setModifyTime(stat.lastModifiedEpochSecond());
            dirty = true;
        }
        if (dirty) {
            fileMapper.updateByFileId(record);
        }
    }

    private String signJwt(Map<String, Object> payload) {
        String secret = trimToEmpty(onlyOfficeProperties.getJwtSecret());
        if (secret.isBlank()) {
            return "";
        }
        try {
            String headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            String payloadJson = objectMapper.writeValueAsString(payload);
            String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
            String body = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String content = header + "." + body;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = base64Url(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
            return content + "." + signature;
        } catch (JsonProcessingException ex) {
            throw new BusinessException(50026, "onlyoffice token serialization failed: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException(50026, "onlyoffice token signing failed: " + ex.getMessage());
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private int parseStatus(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            throw new BusinessException(40042, "invalid onlyoffice callback status");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, Object> buildMockPreviewFile(
            String defaultFileId,
            String defaultFileName,
            String bucketName,
            String creatorId,
            String userId,
            String apiBaseUrl,
            String mode,
            String lang,
            Map<String, Object> overrides
    ) {
        String fileId = defaultIfBlank(stringValue(overrides.get("fileId")), defaultFileId);
        String fileName = defaultIfBlank(stringValue(overrides.get("fileName")), defaultFileName);
        String objectKey = defaultIfBlank(stringValue(overrides.get("objectKey")), fileName);
        String directDownloadUrl = defaultIfBlank(
                stringValue(overrides.get("directDownloadUrl")),
                apiBaseUrl + "/api/onlyoffice/files/mock-preview-content/" + fileId
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", fileId);
        result.put("fileName", fileName);
        result.put("objectKey", objectKey);
        result.put("creatorId", defaultIfBlank(stringValue(overrides.get("creatorId")), creatorId));
        result.put("bucketName", defaultIfBlank(stringValue(overrides.get("bucketName")), bucketName));
        result.put("apiBaseUrl", defaultIfBlank(stringValue(overrides.get("apiBaseUrl")), apiBaseUrl));
        result.put("directDownloadUrl", directDownloadUrl);
        result.put("mode", defaultIfBlank(stringValue(overrides.get("mode")), mode));
        result.put("lang", defaultIfBlank(stringValue(overrides.get("lang")), lang));
        result.put("userId", defaultIfBlank(stringValue(overrides.get("userId")), userId));
        return result;
    }

    private String resolveMockPreviewVariant(Map<String, Object> request) {
        String variant = defaultIfBlank(
                stringValue(request.get("variant")),
                stringValue(request.get("version"))
        ).toLowerCase(Locale.ROOT);
        return "after".equals(variant) ? "after" : "before";
    }

    private String resolveFileName(String fileName, String objectKey) {
        if (fileName != null && !fileName.isBlank()) {
            return fileName.trim();
        }
        int index = objectKey.lastIndexOf('/');
        return index >= 0 ? objectKey.substring(index + 1) : objectKey;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToEmpty(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalize(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(40000, message);
        }
        return value.trim();
    }

    private String normalizeFileId(String fileId) {
        String normalized = normalize(fileId, "fileId is required");
        if (!normalized.matches("[A-Za-z0-9]+")) {
            throw new BusinessException(40005, "fileId must use letters and numbers only");
        }
        return normalized;
    }

    private String suffix(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private String trimTrailingSlash(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(40004, message);
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record FileContent(String fileName, String contentType, byte[] content) {
    }
}
