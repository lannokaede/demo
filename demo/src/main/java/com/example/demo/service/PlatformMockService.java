package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlatformMockService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern ATTACHMENT_FILE_ID_PATTERN = Pattern.compile("fileId=([^,}\\]]+)");

    private final ConcurrentMap<String, ChatSession> chatSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StoredFile> files = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, KnowledgeFile> knowledgeFiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, TemplateItem> templates = new ConcurrentHashMap<>();
    private final AtomicInteger templateIdGenerator = new AtomicInteger(3);
    private final List<Map<String, Object>> feedbackEntries = new CopyOnWriteArrayList<>();

    public PlatformMockService() {
        seedTemplates();
    }

    public Map<String, Object> createChatSession() {
        String sessionId = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ChatSession session = new ChatSession(sessionId, "未命名对话");
        chatSessions.put(sessionId, session);
        return Map.of(
                "sessionId", sessionId,
                "title", session.title
        );
    }

    public List<Map<String, Object>> listChatSessions() {
        return chatSessions.values().stream()
                .sorted(Comparator.comparing((ChatSession session) -> session.updatedAt).reversed())
                .map(this::chatSessionView)
                .toList();
    }

    public Map<String, Object> getChatSession(String sessionId) {
        ChatSession session = requireChatSession(sessionId);
        List<Map<String, Object>> messages = session.messages.stream()
                .map(this::chatMessageView)
                .toList();
        return Map.of(
                "sessionId", session.sessionId,
                "title", session.title,
                "messages", messages
        );
    }

    public ChatGenerationContext getChatGenerationContext(String sessionId) {
        ChatSession session = requireChatSession(sessionId);
        List<String> promptParts = new ArrayList<>();
        List<Map<String, Object>> attachments = new ArrayList<>();
        for (ChatMessage message : session.messages) {
            if ("text".equals(message.type) && message.content != null && !message.content.isBlank()) {
                promptParts.add(message.role + ": " + message.content.trim());
                continue;
            }
            if ("attachments".equals(message.type)) {
                attachments.addAll(parseAttachmentRefs(message.content));
            }
        }
        return new ChatGenerationContext(session.sessionId, session.title, String.join("\n", promptParts), attachments);
    }

    public Map<String, Object> sendChatMessage(String sessionId, Map<String, Object> request) {
        ChatSession session = requireChatSession(sessionId);
        String content = normalize(String.valueOf(request.getOrDefault("content", "")), "content is required");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attachments = request.get("attachments") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();

        if ("未命名对话".equals(session.title)) {
            session.title = shortenTitle(content);
        }

        session.messages.add(new ChatMessage("user", "text", content));
        if (!attachments.isEmpty()) {
            session.messages.add(new ChatMessage("user", "attachments", attachments.toString()));
        }

        String replyText = buildAssistantReply(content, attachments, request.get("templateId"));
        session.messages.add(new ChatMessage("assistant", "text", replyText));
        session.updatedAt = LocalDateTime.now();

        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("role", "assistant");
        reply.put("type", "text");
        reply.put("content", replyText);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reply", reply);
        data.put("requireForm", needsRequirementForm(content));
        return data;
    }

    public Map<String, Object> uploadFile(MultipartFile file, String bizType, String userId, String baseUrl) {
        String resolvedBizType = bizType == null || bizType.isBlank() ? "chat" : bizType.trim();
        String fileId = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "upload.bin"
                : file.getOriginalFilename().trim();
        String objectKey = "uploads/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/" + fileId + "-" + fileName;

        StoredFile storedFile = new StoredFile(
                fileId,
                fileName,
                detectFileType(fileName),
                fileSize(file),
                objectKey,
                normalizeBaseUrl(baseUrl) + "/api/files/" + fileId + "/content",
                resolvedBizType,
                userId == null || userId.isBlank() ? "anonymous" : userId.trim(),
                readBytes(file)
        );
        files.put(fileId, storedFile);

        return Map.of(
                "fileId", storedFile.fileId,
                "fileName", storedFile.fileName,
                "fileType", storedFile.fileType,
                "fileSize", storedFile.fileSize,
                "objectKey", storedFile.objectKey,
                "url", storedFile.url
        );
    }

    public FileContent getFileContent(String fileId) {
        StoredFile storedFile = requireFile(fileId);
        return new FileContent(storedFile.fileName, contentType(storedFile.fileName), storedFile.content);
    }

    public List<Map<String, Object>> listKnowledgeFiles() {
        return knowledgeFiles.values().stream()
                .sorted(Comparator.comparing((KnowledgeFile item) -> item.createdAt).reversed())
                .map(this::knowledgeListItem)
                .toList();
    }

    public Map<String, Object> uploadKnowledgeFile(MultipartFile file, String baseUrl) {
        Map<String, Object> uploaded = uploadFile(file, "knowledge", "anonymous", baseUrl);
        String fileId = String.valueOf(uploaded.get("fileId"));
        StoredFile storedFile = requireFile(fileId);
        KnowledgeFile knowledgeFile = new KnowledgeFile(
                storedFile.fileId,
                storedFile.fileName,
                storedFile.fileType,
                storedFile.fileSize,
                LocalDate.now(),
                storedFile.url
        );
        knowledgeFiles.put(fileId, knowledgeFile);
        return knowledgeDetail(knowledgeFile);
    }

    public Map<String, Object> getKnowledgeFile(String fileId) {
        return knowledgeDetail(requireKnowledgeFile(fileId));
    }

    public Map<String, Object> deleteKnowledgeFile(String fileId) {
        KnowledgeFile removed = knowledgeFiles.remove(normalize(fileId, "fileId is required"));
        if (removed == null) {
            throw new BusinessException(40420, "knowledge file not found");
        }
        files.remove(removed.fileId);
        return Map.of("deleted", true);
    }

    public Map<String, Object> quoteKnowledgeFileToChat(String fileId, String sessionId) {
        KnowledgeFile file = requireKnowledgeFile(fileId);
        ChatSession session = requireChatSession(sessionId);
        session.messages.add(new ChatMessage("assistant", "text", "已引用知识库文件《" + file.name + "》到当前对话。"));
        session.updatedAt = LocalDateTime.now();
        return Map.of(
                "sessionId", sessionId,
                "fileId", fileId,
                "quoted", true
        );
    }

    public List<Map<String, Object>> listTemplateCategories() {
        return List.of(
                Map.of("key", "通用", "name", "通用"),
                Map.of("key", "教育", "name", "教育"),
                Map.of("key", "科技", "name", "科技"),
                Map.of("key", "自定义", "name", "自定义")
        );
    }

    public List<Map<String, Object>> listTemplates(String category, String keyword) {
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return templates.values().stream()
                .filter(template -> normalizedCategory.isBlank() || template.category.equalsIgnoreCase(normalizedCategory))
                .filter(template -> normalizedKeyword.isBlank() || template.title.toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparingInt((TemplateItem item) -> item.id))
                .map(this::templateView)
                .toList();
    }

    public Map<String, Object> getTemplate(Integer templateId) {
        TemplateItem template = templates.get(templateId);
        if (template == null) {
            throw new BusinessException(40430, "template not found");
        }
        return templateView(template);
    }

    public Map<String, Object> uploadTemplate(MultipartFile file, String baseUrl) {
        Map<String, Object> uploaded = uploadFile(file, "template", "anonymous", baseUrl);
        int templateId = templateIdGenerator.incrementAndGet();
        TemplateItem template = new TemplateItem(
                templateId,
                stripExtension(String.valueOf(uploaded.get("fileName"))),
                "自定义",
                0,
                normalizeBaseUrl(baseUrl) + "/static/template-cover.png",
                String.valueOf(uploaded.get("url"))
        );
        templates.put(templateId, template);
        return templateView(template);
    }

    public Map<String, Object> submitFeedback(Map<String, Object> request) {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("feedbackId", "fb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        saved.put("type", String.valueOf(request.getOrDefault("type", "other")));
        saved.put("message", String.valueOf(request.getOrDefault("message", "")));
        saved.put("contact", request.getOrDefault("contact", Map.of()));
        saved.put("attachments", request.getOrDefault("attachments", List.of()));
        saved.put("createdAt", formatDateTime(LocalDateTime.now()));
        feedbackEntries.add(saved);
        return Map.of("submitted", true);
    }

    public Map<String, Object> uploadFeedbackFile(MultipartFile file, String baseUrl) {
        return uploadFile(file, "feedback", "anonymous", baseUrl);
    }

    private Map<String, Object> knowledgeDetail(KnowledgeFile file) {
        return Map.of(
                "fileId", file.fileId,
                "name", file.name,
                "type", file.type,
                "size", readableSize(file.size),
                "date", file.createdAt.format(DATE_FORMATTER),
                "previewUrl", file.previewUrl
        );
    }

    private Map<String, Object> templateView(TemplateItem template) {
        return Map.of(
                "id", template.id,
                "title", template.title,
                "category", template.category,
                "uses", template.uses,
                "cover", template.cover,
                "fileUrl", template.fileUrl
        );
    }

    private Map<String, Object> chatSessionView(ChatSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.sessionId);
        data.put("title", session.title);
        data.put("updatedAt", formatDateTime(session.updatedAt));
        return data;
    }

    private Map<String, Object> chatMessageView(ChatMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", message.role);
        data.put("type", message.type);
        data.put("content", message.content);
        return data;
    }

    private Map<String, Object> knowledgeListItem(KnowledgeFile item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileId", item.fileId);
        data.put("name", item.name);
        data.put("type", item.type);
        data.put("size", readableSize(item.size));
        data.put("date", item.createdAt.format(DATE_FORMATTER));
        return data;
    }

    private void seedTemplates() {
        templates.put(1, new TemplateItem(
                1,
                "Technology Data Report",
                "通用",
                996,
                "https://mock.local/templates/technology-data-report.png",
                "https://mock.local/templates/technology-data-report.pptx"
        ));
        templates.put(2, new TemplateItem(
                2,
                "智慧教育课程汇报",
                "教育",
                268,
                "https://mock.local/templates/education-cover.png",
                "https://mock.local/templates/education.pptx"
        ));
        templates.put(3, new TemplateItem(
                3,
                "AI 创新路演",
                "科技",
                432,
                "https://mock.local/templates/ai-demo-cover.png",
                "https://mock.local/templates/ai-demo.pptx"
        ));
    }

    private ChatSession requireChatSession(String sessionId) {
        ChatSession session = chatSessions.get(normalize(sessionId, "sessionId is required"));
        if (session == null) {
            throw new BusinessException(40411, "chat session not found");
        }
        return session;
    }

    private StoredFile requireFile(String fileId) {
        StoredFile storedFile = files.get(normalize(fileId, "fileId is required"));
        if (storedFile == null) {
            throw new BusinessException(40412, "file not found");
        }
        return storedFile;
    }

    private KnowledgeFile requireKnowledgeFile(String fileId) {
        KnowledgeFile knowledgeFile = knowledgeFiles.get(normalize(fileId, "fileId is required"));
        if (knowledgeFile == null) {
            throw new BusinessException(40421, "knowledge file not found");
        }
        return knowledgeFile;
    }

    private String buildAssistantReply(String content, List<Map<String, Object>> attachments, Object templateId) {
        List<String> parts = new ArrayList<>();
        parts.add("已收到你的需求");
        if (!attachments.isEmpty()) {
            parts.add("并识别到 " + attachments.size() + " 个附件");
        }
        if (templateId != null) {
            parts.add("将优先参考模板 " + templateId);
        }
        parts.add("接下来可以继续完善受众、页数和风格信息。");
        return String.join("，", parts) + " 当前主题：" + content;
    }

    private boolean needsRequirementForm(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return normalized.contains("ppt") || normalized.contains("课件") || normalized.contains("演示");
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(50010, "failed to read uploaded file");
        }
    }

    private long fileSize(MultipartFile file) {
        return file.getSize();
    }

    private String contentType(String fileName) {
        String fileType = detectFileType(fileName);
        return switch (fileType) {
            case "pdf" -> "application/pdf";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    private String detectFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "file";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String shortenTitle(String content) {
        String trimmed = content.trim();
        return trimmed.length() > 12 ? trimmed.substring(0, 12) : trimmed;
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String readableSize(long size) {
        if (size < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", size / 1024.0 / 1024.0);
    }

    private String formatDateTime(LocalDateTime value) {
        return value.format(DATE_TIME_FORMATTER);
    }

    private String normalize(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(40000, message);
        }
        return value.trim();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private List<Map<String, Object>> parseAttachmentRefs(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> attachments = new ArrayList<>();
        Matcher matcher = ATTACHMENT_FILE_ID_PATTERN.matcher(rawContent);
        while (matcher.find()) {
            String fileId = matcher.group(1).trim();
            if (!fileId.isBlank()) {
                attachments.add(Map.of("fileId", fileId));
            }
        }
        return attachments;
    }

    public record FileContent(String fileName, String contentType, byte[] content) {
    }

    public record ChatGenerationContext(
            String sessionId,
            String title,
            String prompt,
            List<Map<String, Object>> attachments
    ) {
    }

    private static final class ChatSession {
        private final String sessionId;
        private String title;
        private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
        private LocalDateTime updatedAt;

        private ChatSession(String sessionId, String title) {
            this.sessionId = sessionId;
            this.title = title;
            this.updatedAt = LocalDateTime.now();
        }
    }

    private record ChatMessage(String role, String type, String content) {
    }

    private record StoredFile(
            String fileId,
            String fileName,
            String fileType,
            long fileSize,
            String objectKey,
            String url,
            String bizType,
            String userId,
            byte[] content
    ) {
    }

    private record KnowledgeFile(
            String fileId,
            String name,
            String type,
            long size,
            LocalDate createdAt,
            String previewUrl
    ) {
    }

    private record TemplateItem(
            int id,
            String title,
            String category,
            int uses,
            String cover,
            String fileUrl
    ) {
    }
}
