package com.example.demo.service;

import java.util.List;

public interface MlSubmitClient {
    SubmitResult submit(String taskId, String prompt, List<AttachmentPayload> attachments);

    default TaskResult queryTask(String mlJobId) {
        return TaskResult.processing(mlJobId);
    }

    record SubmitResult(String mlJobId) {
    }

    record AttachmentPayload(String fieldName, String fileName, String contentType, byte[] content) {
    }

    record TaskResult(
            String mlJobId,
            String status,
            String title,
            String fileName,
            String fileType,
            String downloadUrl,
            String errorMessage
    ) {
        public static TaskResult processing(String mlJobId) {
            return new TaskResult(mlJobId, "PROCESSING", "", "", "", "", "");
        }
    }
}
