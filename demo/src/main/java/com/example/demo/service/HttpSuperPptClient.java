package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.http.HttpClient;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HttpSuperPptClient implements SuperPptClient {

    private static final Logger log = LoggerFactory.getLogger(HttpSuperPptClient.class);

    private final String baseUrl;
    private final String authToken;
    private final RestClient restClient;
    private final int getRetryCount;
    private final long getRetryDelayMs;

    public HttpSuperPptClient(
            @Value("${superppt.base-url:}") String baseUrl,
            @Value("${superppt.auth-token:}") String authToken,
            @Value("${superppt.connect-timeout-ms:30000}") int connectTimeoutMs,
            @Value("${superppt.read-timeout-ms:300000}") int readTimeoutMs,
            @Value("${superppt.get-retry-count:2}") int getRetryCount,
            @Value("${superppt.get-retry-delay-ms:1500}") long getRetryDelayMs
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.authToken = authToken == null ? "" : authToken.trim();
        this.getRetryCount = Math.max(0, getRetryCount);
        this.getRetryDelayMs = Math.max(0L, getRetryDelayMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, readTimeoutMs)));
        requestFactory.enableCompression(true);
        this.restClient = this.baseUrl.isBlank()
                ? RestClient.builder().requestFactory(requestFactory).build()
                : RestClient.builder().baseUrl(this.baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Object healthz() {
        return requestObject(HttpMethod.GET, "/healthz", null);
    }

    @Override
    public Object listSessions() {
        return requestObject(HttpMethod.GET, "/sessions", null);
    }

    @Override
    public Map<String, Object> upload(UploadBinary file, String assetType, String role) {
        ensureConfigured();
        if (file == null || file.content() == null) {
            throw new BusinessException(40040, "file is required");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.content()) {
            @Override
            public String getFilename() {
                return file.fileName();
            }
        });
        if (!isBlank(assetType)) {
            body.add("asset_type", assetType.trim());
        }
        if (!isBlank(role)) {
            body.add("role", role.trim());
        }

        try {
            long start = System.nanoTime();
            log.info("Calling SuperPPT upload: url={} path=/uploads filename={}", baseUrl, file.fileName());
            Map<?, ?> response = restClient.post()
                    .uri("/uploads")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(this::applyHeaders)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            log.info("SuperPPT upload completed in {} ms", elapsedMillis(start));
            return toMap(response, "upload response");
        } catch (Exception ex) {
            log.warn("SuperPPT upload failed: {}", ex.getMessage(), ex);
            throw translateException("upload file to SuperPPT", ex);
        }
    }

    @Override
    public Map<String, Object> getUpload(String uploadId) {
        return requestMap(HttpMethod.GET, "/uploads/" + encodePathSegment(uploadId), null);
    }

    @Override
    public Map<String, Object> createSession(Map<String, Object> request) {
        return requestMap(HttpMethod.POST, "/sessions", safeBody(request));
    }

    @Override
    public Map<String, Object> getSession(String sessionId) {
        return requestMap(HttpMethod.GET, "/sessions/" + encodePathSegment(sessionId), null);
    }

    @Override
    public Map<String, Object> appendSessionAssets(String sessionId, Map<String, Object> request) {
        return requestMap(HttpMethod.POST, "/sessions/" + encodePathSegment(sessionId) + "/assets", safeBody(request));
    }

    @Override
    public Map<String, Object> submitClarifications(String sessionId, Map<String, Object> request) {
        return requestMap(HttpMethod.POST, "/sessions/" + encodePathSegment(sessionId) + "/clarifications", safeBody(request));
    }

    @Override
    public Map<String, Object> getOutline(String sessionId) {
        return requestMap(HttpMethod.GET, "/sessions/" + encodePathSegment(sessionId) + "/outline", null);
    }

    @Override
    public Map<String, Object> reviewOutline(String sessionId, Map<String, Object> request) {
        return requestMap(HttpMethod.POST, "/sessions/" + encodePathSegment(sessionId) + "/outline/review", safeBody(request));
    }

    @Override
    public Map<String, Object> getDraft(String sessionId) {
        return requestMap(HttpMethod.GET, "/sessions/" + encodePathSegment(sessionId) + "/draft", null);
    }

    @Override
    public Map<String, Object> getRevisions(String sessionId) {
        return requestMap(HttpMethod.GET, "/sessions/" + encodePathSegment(sessionId) + "/revisions", null);
    }

    @Override
    public Map<String, Object> editSlides(String sessionId, Map<String, Object> request) {
        return requestMap(HttpMethod.POST, "/sessions/" + encodePathSegment(sessionId) + "/slides/edit", safeBody(request));
    }

    @Override
    public Map<String, Object> reviseDraft(String sessionId, Map<String, Object> request) {
        return requestMap(HttpMethod.POST, "/sessions/" + encodePathSegment(sessionId) + "/draft/revise", safeBody(request));
    }

    @Override
    public Map<String, Object> finalizeSession(String sessionId) {
        return requestMap(HttpMethod.POST, "/sessions/" + encodePathSegment(sessionId) + "/finalize", Map.of());
    }

    @Override
    public Map<String, Object> getArtifacts(String sessionId) {
        return requestMap(HttpMethod.GET, "/sessions/" + encodePathSegment(sessionId) + "/artifacts", null);
    }

    @Override
    public Map<String, Object> getDigitalHumanPlugin(String sessionId) {
        return requestMap(HttpMethod.GET, "/sessions/" + encodePathSegment(sessionId) + "/plugins/digital-human", null);
    }

    @Override
    public Map<String, Object> triggerDigitalHumanPlugin(String sessionId, Map<String, Object> request) {
        return requestMap(HttpMethod.POST, "/sessions/" + encodePathSegment(sessionId) + "/plugins/digital-human", safeBody(request));
    }

    @Override
    public DownloadedFile downloadArtifact(String sessionId, String artifactName) {
        ensureConfigured();
        try {
            long start = System.nanoTime();
            String path = "/sessions/" + encodePathSegment(sessionId) + "/download/" + encodePathSegment(artifactName);
            log.info("Calling SuperPPT download: url={} path={}", baseUrl, path);
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(path)
                    .headers(this::applyHeaders)
                    .retrieve()
                    .toEntity(byte[].class);
            log.info(
                    "SuperPPT download completed in {} ms with status {}",
                    elapsedMillis(start),
                    response.getStatusCode().value()
            );
            HttpHeaders headers = response.getHeaders();
            return new DownloadedFile(
                    response.getBody() == null ? new byte[0] : response.getBody(),
                    headers.getFirst(HttpHeaders.CONTENT_TYPE),
                    headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)
            );
        } catch (Exception ex) {
            log.warn("SuperPPT download failed: {}", ex.getMessage(), ex);
            throw translateException("download artifact from SuperPPT", ex);
        }
    }

    private Map<String, Object> requestMap(HttpMethod method, String path, Map<String, Object> body) {
        Object response = requestObject(method, path, body);
        return toMap(response, "SuperPPT response");
    }

    private Object requestObject(HttpMethod method, String path, Map<String, Object> body) {
        ensureConfigured();
        int maxAttempts = HttpMethod.GET.equals(method) ? getRetryCount + 1 : 1;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long start = System.nanoTime();
            try {
                log.info(
                        "Calling SuperPPT: method={} url={} path={} attempt={}/{}",
                        method,
                        baseUrl,
                        path,
                        attempt,
                        maxAttempts
                );
                RestClient.RequestBodySpec request = restClient.method(method)
                        .uri(path)
                        .headers(this::applyHeaders);
                ResponseEntity<Object> response;
                if (body != null) {
                    response = request.contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toEntity(Object.class);
                } else {
                    response = request.retrieve().toEntity(Object.class);
                }
                log.info(
                        "SuperPPT response received: method={} path={} status={} durationMs={} attempt={}/{}",
                        method,
                        path,
                        response.getStatusCode().value(),
                        elapsedMillis(start),
                        attempt,
                        maxAttempts
                );
                return response.getBody();
            } catch (Exception ex) {
                lastException = ex;
                boolean shouldRetry = shouldRetry(method, attempt, maxAttempts, ex);
                log.warn(
                        "SuperPPT request failed: method={} path={} durationMs={} attempt={}/{} retry={} message={}",
                        method,
                        path,
                        elapsedMillis(start),
                        attempt,
                        maxAttempts,
                        shouldRetry,
                        ex.getMessage(),
                        ex
                );
                if (!shouldRetry) {
                    throw translateException("call SuperPPT " + method + " " + path, ex);
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw translateException("call SuperPPT " + method + " " + path, lastException == null
                ? new IllegalStateException("request failed without exception")
                : lastException);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value, String action) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value == null) {
            return new LinkedHashMap<>();
        }
        throw new BusinessException(50240, action + " is not a JSON object");
    }

    private Map<String, Object> safeBody(Map<String, Object> body) {
        return body == null ? Map.of() : body;
    }

    private void applyHeaders(HttpHeaders headers) {
        if (!authToken.isBlank()) {
            headers.setBearerAuth(authToken);
        }
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM));
    }

    private void ensureConfigured() {
        if (!baseUrl.isBlank()) {
            return;
        }
        throw new BusinessException(50040, "superppt.base-url is not configured");
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String encodePathSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(40041, "path value is required");
        }
        return UriUtils.encodePathSegment(value.trim(), StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private boolean shouldRetry(HttpMethod method, int attempt, int maxAttempts, Exception ex) {
        if (!HttpMethod.GET.equals(method) || attempt >= maxAttempts) {
            return false;
        }
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 502 || status == 503 || status == 504;
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt) {
        if (getRetryDelayMs <= 0) {
            return;
        }
        long delay = getRetryDelayMs * attempt;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new BusinessException(50042, "retry interrupted", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BusinessException translateException(String action, Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        if (ex instanceof RestClientResponseException responseException) {
            String responseBody = responseException.getResponseBodyAsString();
            String detail = responseBody == null || responseBody.isBlank()
                    ? responseException.getMessage()
                    : responseBody;
            HttpStatus status = HttpStatus.resolve(responseException.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.BAD_GATEWAY;
            }
            return new BusinessException(status.value() * 100 + 41, action + " failed: " + detail, status);
        }
        if (ex instanceof ResourceAccessException resourceAccessException) {
            if (isTimeoutException(resourceAccessException)) {
                return new BusinessException(
                        50442,
                        action + " timed out: " + resourceAccessException.getMessage(),
                        HttpStatus.GATEWAY_TIMEOUT
                );
            }
            return new BusinessException(
                    50242,
                    action + " failed: " + resourceAccessException.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
        return new BusinessException(50242, action + " failed: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        String message = throwable.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }
}
