package com.example.demo.service;

import com.example.demo.config.MinioProperties;
import com.example.demo.config.OnlyOfficeProperties;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.WpsFileMapper;
import com.example.demo.model.WpsFileRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlyOfficeServiceTests {

    @Test
    void shouldImportRemotePreviewAndReturnEditableConfig() throws Exception {
        byte[] remoteContent = "remote ppt content".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/demo.pptx", exchange -> {
            exchange.sendResponseHeaders(200, remoteContent.length);
            exchange.getResponseBody().write(remoteContent);
            exchange.close();
        });
        server.start();
        try {
            FakeWpsFileMapper mapper = new FakeWpsFileMapper();
            FakeMinioStorageService minio = new FakeMinioStorageService();
            OnlyOfficeProperties properties = new OnlyOfficeProperties();
            properties.setDocumentServerUrl("http://document-server.example");
            OnlyOfficeService service = new OnlyOfficeService(mapper, minio, properties, new ObjectMapper(), HttpClient.newHttpClient());
            String sourceUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/demo.pptx";

            Map<String, Object> result = service.buildRemotePreviewConfig(
                    "remotefile001",
                    "demo.pptx",
                    sourceUrl,
                    "u100",
                    "http://api.example"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> editorConfig = (Map<String, Object>) result.get("editorConfig");
            @SuppressWarnings("unchecked")
            Map<String, Object> document = (Map<String, Object>) editorConfig.get("document");
            @SuppressWarnings("unchecked")
            Map<String, Object> permissions = (Map<String, Object>) document.get("permissions");
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedEditorConfig = (Map<String, Object>) editorConfig.get("editorConfig");

            assertEquals(true, permissions.get("edit"));
            assertEquals("edit", nestedEditorConfig.get("mode"));
            assertEquals("edit", result.get("mode"));
            assertEquals(true, result.get("editable"));
            assertEquals("http://api.example/api/onlyoffice/callback/remotefile001", nestedEditorConfig.get("callbackUrl"));
            assertTrue(String.valueOf(document.get("url")).contains("/api/onlyoffice/files/remotefile001/content?version=1"));
            assertEquals(remoteContent.length, mapper.findByFileId("remotefile001").getSize());
            assertEquals(remoteContent.length, minio.getObject("ppt-files", "onlyoffice-preview/remotefile001/demo.pptx").length);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldForceEditableConfigEvenWhenViewModeRequested() {
        FakeWpsFileMapper mapper = new FakeWpsFileMapper();
        FakeMinioStorageService minio = new FakeMinioStorageService();
        OnlyOfficeProperties properties = new OnlyOfficeProperties();
        properties.setDocumentServerUrl("http://document-server.example");
        properties.setDefaultMode("view");
        minio.putObject("ppt-files", "existing/demo.docx", "doc content".getBytes(StandardCharsets.UTF_8), "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        WpsFileRecord record = new WpsFileRecord();
        record.setFileId("existingdoc001");
        record.setBucketName("ppt-files");
        record.setObjectKey("existing/demo.docx");
        record.setFileName("demo.docx");
        record.setVersion(1L);
        record.setSize(11L);
        record.setCreatorId("u100");
        record.setModifierId("u100");
        record.setCreateTime(Instant.now().getEpochSecond());
        record.setModifyTime(Instant.now().getEpochSecond());
        mapper.insert(record);

        OnlyOfficeService service = new OnlyOfficeService(mapper, minio, properties, new ObjectMapper(), HttpClient.newHttpClient());
        Map<String, Object> result = service.getEditorConfig("existingdoc001", "u100", "view", "http://api.example");

        @SuppressWarnings("unchecked")
        Map<String, Object> editorConfig = (Map<String, Object>) result.get("editorConfig");
        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) editorConfig.get("document");
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) document.get("permissions");
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedEditorConfig = (Map<String, Object>) editorConfig.get("editorConfig");

        assertEquals(true, permissions.get("edit"));
        assertEquals("edit", nestedEditorConfig.get("mode"));
        assertEquals("edit", result.get("mode"));
        assertEquals(true, result.get("editable"));
    }

    private static final class FakeWpsFileMapper implements WpsFileMapper {
        private final Map<String, WpsFileRecord> records = new HashMap<>();

        @Override
        public WpsFileRecord findByFileId(String fileId) {
            return records.get(fileId);
        }

        @Override
        public int insert(WpsFileRecord record) {
            records.put(record.getFileId(), record);
            return 1;
        }

        @Override
        public int updateByFileId(WpsFileRecord record) {
            records.put(record.getFileId(), record);
            return 1;
        }
    }

    private static final class FakeMinioStorageService extends MinioStorageService {
        private final Map<String, StoredObject> objects = new HashMap<>();

        private FakeMinioStorageService() {
            super(new MinioProperties());
        }

        @Override
        public FileStat statObject(String bucketName, String objectKey) {
            StoredObject stored = objects.get(key(bucketName, objectKey));
            if (stored == null) {
                throw new BusinessException(40420, "minio object not found: " + objectKey);
            }
            return new FileStat(stored.content().length, stored.lastModified(), stored.contentType());
        }

        @Override
        public byte[] getObject(String bucketName, String objectKey) {
            StoredObject stored = objects.get(key(bucketName, objectKey));
            if (stored == null) {
                throw new BusinessException(40420, "minio object not found: " + objectKey);
            }
            return stored.content();
        }

        @Override
        public void putObject(String bucketName, String objectKey, byte[] content, String contentType) {
            objects.put(
                    key(bucketName, objectKey),
                    new StoredObject(content == null ? new byte[0] : content, contentType, Instant.now().getEpochSecond())
            );
        }

        @Override
        public String defaultBucket() {
            return "ppt-files";
        }

        private String key(String bucketName, String objectKey) {
            return bucketName + "/" + objectKey;
        }
    }

    private record StoredObject(byte[] content, String contentType, long lastModified) {
    }
}
