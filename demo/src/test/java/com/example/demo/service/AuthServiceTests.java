package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.AuthUserMapper;
import com.example.demo.mapper.AuthVerificationCodeMapper;
import com.example.demo.model.AuthUser;
import com.example.demo.model.AuthVerificationCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTests {

    private static final String JWT_SECRET = "unit-test-auth-secret";

    private CapturingEmailCodeSender emailCodeSender;
    private InMemoryAuthUserMapper authUserMapper;
    private InMemoryAuthVerificationCodeMapper authVerificationCodeMapper;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        emailCodeSender = new CapturingEmailCodeSender();
        authUserMapper = new InMemoryAuthUserMapper();
        authVerificationCodeMapper = new InMemoryAuthVerificationCodeMapper();
        authService = new AuthService(
                authUserMapper,
                authVerificationCodeMapper,
                emailCodeSender,
                new ObjectMapper(),
                JWT_SECRET,
                7200
        );
    }

    @Test
    void shouldReturnCaptchaPayload() {
        Map<String, Object> captcha = authService.getCaptcha();

        assertNotNull(captcha.get("captchaId"));
        assertNotNull(captcha.get("imageBase64"));
        assertEquals(captcha.get("captchaId"), captcha.get("id"));
        assertTrue(String.valueOf(captcha.get("captchaImage")).startsWith("data:image/png;base64,"));
    }

    @Test
    void shouldLoginBySmsAfterCaptchaAndSmsCodeValidation() {
        Map<String, Object> captcha = authService.getCaptcha();
        String captchaId = String.valueOf(captcha.get("captchaId"));
        String captchaCode = authVerificationCodeMapper.findLatestUnused(captchaId, "captcha").getCode();

        authService.sendSmsCode("13800000000", captchaId, captchaCode);
        String smsCode = authVerificationCodeMapper.findLatestUnused("13800000000", "sms").getCode();

        Map<String, Object> loginCaptcha = authService.getCaptcha();
        String loginCaptchaId = String.valueOf(loginCaptcha.get("captchaId"));
        String loginCaptchaCode = authVerificationCodeMapper.findLatestUnused(loginCaptchaId, "captcha").getCode();

        Map<String, Object> result = authService.loginBySms("13800000000", smsCode, loginCaptchaId, loginCaptchaCode);

        assertNotNull(result.get("token"));
        assertNotNull(authUserMapper.findByPhone("13800000000"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) result.get("userInfo");
        assertEquals("User-0000", userInfo.get("nickname"));
        assertTrue(authVerificationCodeMapper.wasMarkedUsed());
    }

    @Test
    void shouldRejectSmsSendWhenCaptchaIsWrong() {
        Map<String, Object> captcha = authService.getCaptcha();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> authService.sendSmsCode("13800000000", String.valueOf(captcha.get("captchaId")), "wrong")
        );

        assertEquals(40009, ex.getCode());
    }

    @Test
    void shouldSendAndLoginByEmailWithCaptchaValidation() {
        Map<String, Object> sendCaptcha = authService.getCaptcha();
        String sendCaptchaId = String.valueOf(sendCaptcha.get("captchaId"));
        String sendCaptchaCode = authVerificationCodeMapper.findLatestUnused(sendCaptchaId, "captcha").getCode();
        authService.sendEmailCode("test@example.com", sendCaptchaId, sendCaptchaCode);

        assertEquals("test@example.com", emailCodeSender.lastEmail);
        assertNotNull(emailCodeSender.lastCode);

        Map<String, Object> result = authService.loginByEmail("test@example.com", emailCodeSender.lastCode, sendCaptchaId, sendCaptchaCode);

        assertNotNull(result.get("token"));
        assertNotNull(authUserMapper.findByEmail("test@example.com"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) result.get("userInfo");
        assertEquals("test", userInfo.get("nickname"));
    }

    @Test
    void shouldAllowReusingSameCaptchaBetweenSendAndLogin() {
        Map<String, Object> captcha = authService.getCaptcha();
        String captchaId = String.valueOf(captcha.get("captchaId"));
        String captchaCode = authVerificationCodeMapper.findLatestUnused(captchaId, "captcha").getCode();

        authService.sendEmailCode("reuse@example.com", captchaId, captchaCode);
        Map<String, Object> login = authService.loginByEmail("reuse@example.com", emailCodeSender.lastCode, captchaId, captchaCode);

        assertNotNull(login.get("token"));
    }

    @Test
    void shouldRejectExpiredCaptcha() {
        AuthVerificationCode record = new AuthVerificationCode();
        record.setTargetValue("cap_manual");
        record.setCode("ABCD");
        record.setCodeType("captcha");
        record.setUsed(false);
        record.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        authVerificationCodeMapper.insert(record);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> authService.sendEmailCode("test@example.com", "cap_manual", "ABCD")
        );

        assertEquals(40012, ex.getCode());
    }

    @Test
    void shouldExposeProfileAfterSmsLogin() {
        Map<String, Object> sendCaptcha = authService.getCaptcha();
        String sendCaptchaId = String.valueOf(sendCaptcha.get("captchaId"));
        String sendCaptchaCode = authVerificationCodeMapper.findLatestUnused(sendCaptchaId, "captcha").getCode();
        authService.sendSmsCode("13800000000", sendCaptchaId, sendCaptchaCode);
        String smsCode = authVerificationCodeMapper.findLatestUnused("13800000000", "sms").getCode();

        Map<String, Object> loginCaptcha = authService.getCaptcha();
        String loginCaptchaId = String.valueOf(loginCaptcha.get("captchaId"));
        String loginCaptchaCode = authVerificationCodeMapper.findLatestUnused(loginCaptchaId, "captcha").getCode();
        Map<String, Object> login = authService.loginBySms("13800000000", smsCode, loginCaptchaId, loginCaptchaCode);

        String token = String.valueOf(login.get("token"));
        Map<String, Object> profile = authService.getProfile(token);

        assertTrue(profile.containsKey("userInfo"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) profile.get("userInfo");
        assertEquals("User-0000", userInfo.get("nickname"));
    }

    @Test
    void shouldRejectTamperedToken() {
        Map<String, Object> sendCaptcha = authService.getCaptcha();
        String sendCaptchaId = String.valueOf(sendCaptcha.get("captchaId"));
        String sendCaptchaCode = authVerificationCodeMapper.findLatestUnused(sendCaptchaId, "captcha").getCode();
        authService.sendSmsCode("13800000000", sendCaptchaId, sendCaptchaCode);
        String smsCode = authVerificationCodeMapper.findLatestUnused("13800000000", "sms").getCode();

        Map<String, Object> loginCaptcha = authService.getCaptcha();
        String loginCaptchaId = String.valueOf(loginCaptcha.get("captchaId"));
        String loginCaptchaCode = authVerificationCodeMapper.findLatestUnused(loginCaptchaId, "captcha").getCode();
        Map<String, Object> login = authService.loginBySms("13800000000", smsCode, loginCaptchaId, loginCaptchaCode);

        String tamperedToken = String.valueOf(login.get("token")) + "tampered";
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.getProfile(tamperedToken));

        assertEquals(40005, ex.getCode());
    }

    private static final class InMemoryAuthUserMapper implements AuthUserMapper {
        private final ConcurrentMap<String, AuthUser> usersByUsername = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, AuthUser> usersByUserId = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, AuthUser> usersByPhone = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, AuthUser> usersByEmail = new ConcurrentHashMap<>();

        @Override
        public AuthUser findByUsername(String username) {
            return usersByUsername.get(username);
        }

        @Override
        public AuthUser findByUserId(String userId) {
            return usersByUserId.get(userId);
        }

        @Override
        public AuthUser findByPhone(String phone) {
            return usersByPhone.get(phone);
        }

        @Override
        public AuthUser findByEmail(String email) {
            return usersByEmail.get(email);
        }

        @Override
        public int insert(AuthUser user) {
            usersByUsername.put(user.getUsername(), user);
            usersByUserId.put(user.getUserId(), user);
            if (user.getPhone() != null) {
                usersByPhone.put(user.getPhone(), user);
            }
            if (user.getEmail() != null) {
                usersByEmail.put(user.getEmail(), user);
            }
            return 1;
        }
    }

    private static final class InMemoryAuthVerificationCodeMapper implements AuthVerificationCodeMapper {
        private final AtomicLong idGenerator = new AtomicLong(1);
        private final ConcurrentMap<Long, AuthVerificationCode> records = new ConcurrentHashMap<>();
        private volatile boolean markedUsed;

        @Override
        public int insert(AuthVerificationCode record) {
            long id = idGenerator.getAndIncrement();
            record.setId(id);
            records.put(id, record);
            return 1;
        }

        @Override
        public AuthVerificationCode findLatestUnused(String targetValue, String codeType) {
            return records.values().stream()
                    .filter(item -> targetValue.equals(item.getTargetValue()))
                    .filter(item -> codeType.equals(item.getCodeType()))
                    .filter(item -> !Boolean.TRUE.equals(item.getUsed()))
                    .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int markUsed(Long id) {
            AuthVerificationCode record = records.get(id);
            if (record == null) {
                return 0;
            }
            record.setUsed(true);
            markedUsed = true;
            return 1;
        }

        @Override
        public int deleteExpiredOrUsed(LocalDateTime now, LocalDateTime usedBefore) {
            int before = records.size();
            records.entrySet().removeIf(entry -> {
                AuthVerificationCode record = entry.getValue();
                boolean expired = record.getExpiresAt() != null && record.getExpiresAt().isBefore(now);
                boolean usedAndOld = Boolean.TRUE.equals(record.getUsed())
                        && record.getUpdatedAt() != null
                        && record.getUpdatedAt().isBefore(usedBefore);
                return expired || usedAndOld;
            });
            return before - records.size();
        }

        public boolean wasMarkedUsed() {
            return markedUsed;
        }
    }

    private static final class CapturingEmailCodeSender implements EmailCodeSender {
        private String lastEmail;
        private String lastCode;

        @Override
        public void sendCode(String email, String code) {
            this.lastEmail = email;
            this.lastCode = code;
        }
    }
}
