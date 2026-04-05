package com.example.demo.dto;

public record OnlyOfficeH5GamePrepareRequest(
        String gameName,
        String bucketName,
        String gameDir,
        String entryFile,
        String htmlUrl,
        String pluginGuid,
        Double widthMm,
        Double heightMm
) {
}
