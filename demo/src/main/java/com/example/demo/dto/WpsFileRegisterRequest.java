package com.example.demo.dto;

public record WpsFileRegisterRequest(
        String fileId,
        String objectKey,
        String fileName,
        String creatorId,
        String bucketName
) {
}
