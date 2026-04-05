package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class HttpMlSubmitClient implements MlSubmitClient {

    private final String submitUrl;
    private final String submitAuthToken;
    private final boolean mockEnabled;
    private final RestClient restClient;
    private final HttpClient httpClient;

    public HttpMlSubmitClient(
            @Value("${ml.submit-url:}") String submitUrl,
            @Value("${ml.submit-auth-token:}") String submitAuthToken,
            @Value("${ml.mock-enabled:false}") boolean mockEnabled
    ) {
        this.submitUrl = submitUrl;
        this.submitAuthToken = submitAuthToken;
        this.mockEnabled = mockEnabled;
        this.restClient = RestClient.builder().build();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public SubmitResult submit(String taskId, String prompt, List<AttachmentPayload> attachments) {
        if (mockEnabled || submitUrl.isBlank()) {
            return new SubmitResult("mock-" + taskId);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_input", prompt);
        for (AttachmentPayload attachment : attachments) {
            if (attachment == null || attachment.content() == null) {
                continue;
            }
            body.add(attachment.fieldName(), namedResource(attachment));
        }

        try {
            Map<?, ?> response = restClient.post()
                    .uri(submitUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> {
                        if (!submitAuthToken.isBlank()) {
                            headers.setBearerAuth(submitAuthToken);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String mlJobId = firstNonBlank(
                    mapValue(response, "task_id"),
                    mapValue(response, "ml_job_id"),
                    "submitted-" + taskId
            );
            return new SubmitResult(mlJobId);
        } catch (Exception ex) {
            throw new BusinessException(50210, "submit to ml failed: " + ex.getMessage());
        }
    }

    @Override
    public TaskResult queryTask(String mlJobId) {
        if (mockEnabled || submitUrl.isBlank()) {
            return TaskResult.processing(mlJobId);
        }

        String queryUrl = buildQueryUrl(mlJobId);
        try {
            ResponseEntity<Map> entity = restClient.get()
                    .uri(queryUrl)
                    .headers(headers -> {
                        if (!submitAuthToken.isBlank()) {
                            headers.setBearerAuth(submitAuthToken);
                        }
                    })
                    .retrieve()
                    .toEntity(Map.class);

            Map<?, ?> body = entity.getBody();
            String normalizedStatus = normalizeStatus(firstNonBlank(
                    mapValue(body, "status"),
                    mapValue(body, "state"),
                    entity.getStatusCode().is2xxSuccessful() ? "PROCESSING" : "FAILED"
            ));
            String downloadUrl = resolveDownloadUrl(body);
            String title = firstNonBlank(mapValue(body, "title"), nestedValue(body, "result", "title"));
            String fileName = firstNonBlank(
                    mapValue(body, "fileName"),
                    nestedValue(body, "result", "fileName"),
                    nestedValue(body, "file", "fileName"),
                    resolveFileName(downloadUrl, mlJobId)
            );
            String fileType = firstNonBlank(
                    mapValue(body, "fileType"),
                    nestedValue(body, "result", "fileType"),
                    nestedValue(body, "file", "fileType"),
                    suffix(fileName)
            );
            String errorMessage = firstNonBlank(
                    mapValue(body, "error_message"),
                    mapValue(body, "errorMessage"),
                    nestedValue(body, "result", "error_message"),
                    nestedValue(body, "result", "errorMessage")
            );
            if ("SUCCEEDED".equals(normalizedStatus) && !isDownloadReady(downloadUrl)) {
                normalizedStatus = "PROCESSING";
            }
            return new TaskResult(mlJobId, normalizedStatus, title, fileName, fileType, downloadUrl, errorMessage);
        } catch (Exception ex) {
            throw new BusinessException(50211, "query ml task failed: " + ex.getMessage());
        }
    }

    private ByteArrayResource namedResource(AttachmentPayload attachment) {
        return new ByteArrayResource(attachment.content()) {
            @Override
            public String getFilename() {
                return attachment.fileName();
            }
        };
    }

    private String buildQueryUrl(String mlJobId) {
        String normalized = submitUrl.endsWith("/") ? submitUrl.substring(0, submitUrl.length() - 1) : submitUrl;
        int index = normalized.lastIndexOf("/generate");
        if (index >= 0) {
            return normalized.substring(0, index) + "/task/" + mlJobId;
        }
        return normalized + "/" + mlJobId;
    }

    private String resolveDownloadUrl(Map<?, ?> body) {
        return firstNonBlank(
                mapValue(body, "downloadUrl"),
                mapValue(body, "download_url"),
                mapValue(body, "object_storage_url"),
                nestedValue(body, "result", "downloadUrl"),
                nestedValue(body, "result", "download_url"),
                nestedValue(body, "result", "object_storage_url"),
                nestedValue(body, "file", "downloadUrl")
        );
    }

    private String normalizeStatus(String rawStatus) {
        String value = firstNonBlank(rawStatus, "PROCESSING").trim().toUpperCase();
        return switch (value) {
            case "SUCCESS", "SUCCEEDED", "COMPLETED", "DONE" -> "SUCCEEDED";
            case "FAIL", "FAILED", "ERROR" -> "FAILED";
            case "PENDING", "QUEUED", "RUNNING", "PROCESSING", "IN_PROGRESS" -> "PROCESSING";
            default -> value;
        };
    }

    private String mapValue(Map<?, ?> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return "";
        }
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nestedValue(Map<?, ?> map, String parentKey, String childKey) {
        if (map == null || !map.containsKey(parentKey)) {
            return "";
        }
        Object nested = map.get(parentKey);
        if (!(nested instanceof Map<?, ?> nestedMap) || !nestedMap.containsKey(childKey)) {
            return "";
        }
        Object value = nestedMap.get(childKey);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isDownloadReady(String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            return false;
        }
        try {
            HttpRequest headRequest = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(15))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
            if (isSuccessful(headResponse.statusCode())) {
                return true;
            }
        } catch (Exception ignored) {
            // Fall back to GET because some object storage gateways do not support HEAD well.
        }
        try {
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<Void> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.discarding());
            return isSuccessful(getResponse.statusCode());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private String resolveFileName(String downloadUrl, String taskId) {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            return "generated-" + taskId + ".pptx";
        }
        int queryIndex = downloadUrl.indexOf('?');
        String raw = queryIndex >= 0 ? downloadUrl.substring(0, queryIndex) : downloadUrl;
        int slashIndex = raw.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < raw.length() - 1) {
            return raw.substring(slashIndex + 1);
        }
        return "generated-" + taskId + ".pptx";
    }

    private String suffix(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
