package com.example.demo.service;

import com.example.demo.config.MinioProperties;
import com.example.demo.exception.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class MinioStorageService {

    private final MinioProperties properties;
    private volatile MinioClient minioClient;

    public MinioStorageService(MinioProperties properties) {
        this.properties = properties;
    }

    public FileStat statObject(String bucketName, String objectKey) {
        try {
            var response = client().statObject(
                    StatObjectArgs.builder()
                            .bucket(requireBucket(bucketName))
                            .object(requireObjectKey(objectKey))
                            .build()
            );
            long lastModified = response.lastModified() == null
                    ? System.currentTimeMillis() / 1000
                    : response.lastModified().toEpochSecond();
            return new FileStat(response.size(), lastModified, response.contentType());
        } catch (ErrorResponseException ex) {
            String code = ex.errorResponse() == null ? "" : ex.errorResponse().code();
            if ("NoSuchKey".equalsIgnoreCase(code) || "NoSuchObject".equalsIgnoreCase(code)) {
                throw new BusinessException(40420, "minio object not found: " + objectKey);
            }
            throw new BusinessException(50021, "minio stat object failed: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException(50021, "minio stat object failed: " + ex.getMessage());
        }
    }

    public byte[] getObject(String bucketName, String objectKey) {
        try (InputStream stream = client().getObject(
                GetObjectArgs.builder()
                        .bucket(requireBucket(bucketName))
                        .object(requireObjectKey(objectKey))
                        .build()
        )) {
            return stream.readAllBytes();
        } catch (Exception ex) {
            throw new BusinessException(50022, "minio get object failed: " + ex.getMessage());
        }
    }

    public void putObject(String bucketName, String objectKey, byte[] content, String contentType) {
        byte[] safeContent = content == null ? new byte[0] : content;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(safeContent)) {
            client().putObject(
                    PutObjectArgs.builder()
                            .bucket(requireBucket(bucketName))
                            .object(requireObjectKey(objectKey))
                            .stream(inputStream, safeContent.length, -1)
                            .contentType(contentType == null || contentType.isBlank()
                                    ? "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                    : contentType)
                            .build()
            );
        } catch (Exception ex) {
            throw new BusinessException(50023, "minio put object failed: " + ex.getMessage());
        }
    }

    public String presignedDownloadUrl(String bucketName, String objectKey, String fileName) {
        try {
            return client().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(requireBucket(bucketName))
                            .object(requireObjectKey(objectKey))
                            .method(Method.GET)
                            .expiry(requireExpirySeconds())
                            .extraQueryParams(Map.of(
                                    "response-content-disposition",
                                    "attachment; filename=\"" + sanitizeFileName(fileName) + "\""
                            ))
                            .build()
            );
        } catch (Exception ex) {
            throw new BusinessException(50024, "minio presigned url failed: " + ex.getMessage());
        }
    }

    public String defaultBucket() {
        return requireBucket(properties.getBucket());
    }

    public String publicObjectUrl(String bucketName, String objectKey) {
        String endpoint = properties.getEndpoint();
        if (isBlank(endpoint)) {
            throw new BusinessException(50020, "minio config is incomplete");
        }
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return normalizedEndpoint + "/" + encodePathSegment(requireBucket(bucketName)) + "/" + encodeObjectKey(requireObjectKey(objectKey));
    }

    private MinioClient client() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    requireConfigured();
                    minioClient = MinioClient.builder()
                            .endpoint(properties.getEndpoint())
                            .credentials(properties.getAccessKey(), properties.getSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private void requireConfigured() {
        if (isBlank(properties.getEndpoint()) || isBlank(properties.getAccessKey()) || isBlank(properties.getSecretKey()) || isBlank(properties.getBucket())) {
            throw new BusinessException(50020, "minio config is incomplete");
        }
    }

    private String requireBucket(String bucketName) {
        if (isBlank(bucketName)) {
            throw new BusinessException(40020, "bucket name is required");
        }
        return bucketName.trim();
    }

    private String requireObjectKey(String objectKey) {
        if (isBlank(objectKey)) {
            throw new BusinessException(40021, "object key is required");
        }
        return objectKey.trim();
    }

    private int requireExpirySeconds() {
        Integer expiry = properties.getDownloadExpirySeconds();
        if (expiry == null || expiry < 1) {
            return 3600;
        }
        return expiry;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "download.pptx";
        }
        return fileName.replace("\"", "");
    }

    private String encodeObjectKey(String objectKey) {
        String normalized = objectKey.trim().replace("\\", "/").replaceAll("/+", "/");
        String[] segments = normalized.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(encodePathSegment(segment));
        }
        return builder.toString();
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record FileStat(long size, long lastModifiedEpochSecond, String contentType) {
    }
}
