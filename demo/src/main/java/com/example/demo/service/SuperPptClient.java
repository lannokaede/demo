package com.example.demo.service;

import java.util.Map;

public interface SuperPptClient {

    Object healthz();

    Object listSessions();

    Map<String, Object> upload(UploadBinary file, String assetType, String role);

    Map<String, Object> getUpload(String uploadId);

    Map<String, Object> createSession(Map<String, Object> request);

    Map<String, Object> getSession(String sessionId);

    Map<String, Object> appendSessionAssets(String sessionId, Map<String, Object> request);

    Map<String, Object> submitClarifications(String sessionId, Map<String, Object> request);

    Map<String, Object> getOutline(String sessionId);

    Map<String, Object> reviewOutline(String sessionId, Map<String, Object> request);

    Map<String, Object> getDraft(String sessionId);

    Map<String, Object> getRevisions(String sessionId);

    Map<String, Object> editSlides(String sessionId, Map<String, Object> request);

    Map<String, Object> reviseDraft(String sessionId, Map<String, Object> request);

    Map<String, Object> finalizeSession(String sessionId);

    Map<String, Object> getArtifacts(String sessionId);

    Map<String, Object> getDigitalHumanPlugin(String sessionId);

    Map<String, Object> triggerDigitalHumanPlugin(String sessionId, Map<String, Object> request);

    DownloadedFile downloadArtifact(String sessionId, String artifactName);

    record UploadBinary(String fileName, String contentType, byte[] content) {
    }

    record DownloadedFile(byte[] content, String contentType, String contentDisposition) {
    }
}
