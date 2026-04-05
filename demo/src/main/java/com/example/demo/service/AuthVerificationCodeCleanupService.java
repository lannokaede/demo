package com.example.demo.service;

import com.example.demo.mapper.AuthVerificationCodeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthVerificationCodeCleanupService {

    private final AuthVerificationCodeMapper authVerificationCodeMapper;
    private final int usedRetentionMinutes;

    public AuthVerificationCodeCleanupService(
            AuthVerificationCodeMapper authVerificationCodeMapper,
            @Value("${auth.verification-code.used-retention-minutes:30}") int usedRetentionMinutes
    ) {
        this.authVerificationCodeMapper = authVerificationCodeMapper;
        this.usedRetentionMinutes = usedRetentionMinutes;
    }

    @Scheduled(fixedDelayString = "${auth.verification-code.cleanup-interval-ms:600000}", initialDelayString = "${auth.verification-code.cleanup-initial-delay-ms:60000}")
    public void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime usedBefore = now.minusMinutes(Math.max(1, usedRetentionMinutes));
        authVerificationCodeMapper.deleteExpiredOrUsed(now, usedBefore);
    }
}
