package com.example.demo.exception;

import com.example.demo.dto.WpsResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<WpsResult<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus()).body(WpsResult.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<WpsResult<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(WpsResult.fail(41300, "Uploaded file is too large. Maximum allowed size is 100MB."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WpsResult<Void>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(WpsResult.fail(50000, ex.getMessage()));
    }
}
