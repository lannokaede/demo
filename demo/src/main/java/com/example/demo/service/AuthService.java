package com.example.demo.service;

import com.example.demo.dto.AuthLoginRequest;
import com.example.demo.dto.AuthRegisterRequest;
import com.example.demo.dto.AuthUserResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.AuthUserMapper;
import com.example.demo.mapper.AuthVerificationCodeMapper;
import com.example.demo.model.AuthUser;
import com.example.demo.model.AuthVerificationCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SMS_CODE_TYPE = "sms";
    private static final String EMAIL_CODE_TYPE = "email";
    private static final String CAPTCHA_CODE_TYPE = "captcha";
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int CAPTCHA_EXPIRE_MINUTES = 5;

    private final AuthUserMapper authUserMapper;
    private final AuthVerificationCodeMapper authVerificationCodeMapper;
    private final EmailCodeSender emailCodeSender;
    private final ObjectMapper objectMapper;
    private final String jwtSecret;
    private final long jwtExpireSeconds;
    private final Map<String, AuthUserResponse> externalUsersById = new ConcurrentHashMap<>();
    private final Map<String, QrLoginState> qrLoginStore = new ConcurrentHashMap<>();

    public AuthService(
            AuthUserMapper authUserMapper,
            AuthVerificationCodeMapper authVerificationCodeMapper,
            EmailCodeSender emailCodeSender,
            ObjectMapper objectMapper,
            @Value("${auth.jwt-secret:dev-auth-jwt-secret}") String jwtSecret,
            @Value("${auth.jwt-expire-seconds:604800}") long jwtExpireSeconds
    ) {
        this.authUserMapper = authUserMapper;
        this.authVerificationCodeMapper = authVerificationCodeMapper;
        this.emailCodeSender = emailCodeSender;
        this.objectMapper = objectMapper;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret.trim();
        this.jwtExpireSeconds = jwtExpireSeconds;
    }

    public Map<String, Object> register(AuthRegisterRequest request) {
        String username = normalize(request.username(), "username is required");
        String password = normalize(request.password(), "password is required");
        String confirmPassword = normalize(request.confirmPassword(), "confirmPassword is required");
        String nickname = request.nickname() == null || request.nickname().isBlank() ? username : request.nickname().trim();

        if (!password.equals(confirmPassword)) {
            throw new BusinessException(40001, "passwords do not match");
        }
        if (password.length() < 6) {
            throw new BusinessException(40002, "password length must be at least 6");
        }
        if (authUserMapper.findByUsername(username) != null) {
            throw new BusinessException(40003, "username already exists");
        }

        AuthUser user = new AuthUser();
        user.setUserId(buildUserId());
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname);
        authUserMapper.insert(user);

        return Map.of(
                "user", toUserResponse(user),
                "message", "register success"
        );
    }

    public Map<String, Object> login(AuthLoginRequest request) {
        String username = normalize(request.username(), "username is required");
        String password = normalize(request.password(), "password is required");

        AuthUser user = authUserMapper.findByUsername(username);
        if (user == null || !user.getPassword().equals(password)) {
            throw new BusinessException(40004, "invalid username or password");
        }

        String token = createToken(user.getUserId(), "db");
        return loginResult(token, toUserResponse(user));
    }

    public Map<String, Object> getCaptcha() {
        String captchaId = "cap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String captchaCode = generateCaptchaCode();
        saveVerificationCode(captchaId, captchaCode, CAPTCHA_CODE_TYPE, CAPTCHA_EXPIRE_MINUTES);

        String imageBase64 = buildCaptchaImageBase64(captchaCode);
        String dataUri = "data:image/png;base64," + imageBase64;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("captchaId", captchaId);
        data.put("id", captchaId);
        data.put("key", captchaId);
        data.put("imageBase64", imageBase64);
        data.put("base64", imageBase64);
        data.put("captchaImage", dataUri);
        data.put("image", dataUri);
        data.put("img", dataUri);
        return data;
    }

    public Map<String, Object> getProfile(String token) {
        Map<String, Object> claims = parseAndValidateToken(normalize(token, "token is required"));
        String userId = stringClaim(claims, "uid");
        String loginType = stringClaim(claims, "type");

        if ("external".equals(loginType)) {
            AuthUserResponse externalUser = externalUsersById.get(userId);
            if (externalUser != null) {
                return Map.of(
                        "user", externalUser,
                        "userInfo", toSimpleUserInfo(externalUser)
                );
            }
        }

        AuthUser user = authUserMapper.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(40006, "user not found");
        }

        AuthUserResponse profile = toUserResponse(user);
        return Map.of(
                "user", profile,
                "userInfo", toSimpleUserInfo(profile)
        );
    }

    public Map<String, Object> sendSmsCode(String phone, String captchaId, String captchaCode) {
        validateCaptcha(captchaId, captchaCode, false);
        String normalizedPhone = normalizePhone(phone);
        saveVerificationCode(normalizedPhone, generateVerificationCode(), SMS_CODE_TYPE, CODE_EXPIRE_MINUTES);
        return Map.of();
    }

    public Map<String, Object> loginBySms(String phone, String smsCode, String captchaId, String captchaCode) {
        validateCaptcha(captchaId, captchaCode, true);
        String normalizedPhone = normalizePhone(phone);
        verifyCode(normalizedPhone, normalize(smsCode, "smsCode is required"), SMS_CODE_TYPE, "smsCode is invalid");

        AuthUser user = authUserMapper.findByPhone(normalizedPhone);
        if (user == null) {
            user = createPhoneUser(normalizedPhone);
        }

        String token = createToken(user.getUserId(), "db");
        return loginResult(token, toUserResponse(user));
    }

    public Map<String, Object> sendEmailCode(String email, String captchaId, String captchaCode) {
        validateCaptcha(captchaId, captchaCode, false);
        String normalizedEmail = normalizeEmail(email);
        String code = generateVerificationCode();
        saveVerificationCode(normalizedEmail, code, EMAIL_CODE_TYPE, CODE_EXPIRE_MINUTES);
        emailCodeSender.sendCode(normalizedEmail, code);
        return Map.of();
    }

    public Map<String, Object> loginByEmail(String email, String emailCode, String captchaId, String captchaCode) {
        validateCaptcha(captchaId, captchaCode, true);
        String normalizedEmail = normalizeEmail(email);
        verifyCode(normalizedEmail, normalize(emailCode, "emailCode is required"), EMAIL_CODE_TYPE, "emailCode is invalid");

        AuthUser user = authUserMapper.findByEmail(normalizedEmail);
        if (user == null) {
            user = createEmailUser(normalizedEmail);
        }

        String token = createToken(user.getUserId(), "db");
        return loginResult(token, toUserResponse(user));
    }

    public Map<String, Object> createQrLogin(String platform) {
        String normalizedPlatform = normalize(platform, "platform is required").toLowerCase();
        if (!"qq".equals(normalizedPlatform) && !"wechat".equals(normalizedPlatform)) {
            throw new BusinessException(40007, "platform must be qq or wechat");
        }

        String qrId = "qr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        qrLoginStore.put(qrId, new QrLoginState(qrId, normalizedPlatform));
        return Map.of(
                "qrId", qrId,
                "qrUrl", "https://mock.local/qr/" + qrId + ".png",
                "expireSeconds", 120
        );
    }

    public Map<String, Object> getQrStatus(String qrId) {
        QrLoginState state = qrLoginStore.get(normalize(qrId, "qrId is required"));
        if (state == null) {
            throw new BusinessException(40407, "qr login not found");
        }

        state.pollCount++;
        if (state.pollCount <= 1) {
            return Map.of("status", "waiting");
        }
        if (state.pollCount == 2) {
            return Map.of("status", "scanned");
        }

        AuthUserResponse user = new AuthUserResponse(buildUserId(), "qr:" + state.platform + ":" + state.qrId, "Scan User");
        externalUsersById.put(user.userId(), user);
        String token = createToken(user.userId(), "external");
        return new LinkedHashMap<>(Map.of(
                "status", "confirmed",
                "token", token,
                "userInfo", toSimpleUserInfo(user)
        ));
    }

    public Map<String, Object> logout(String token) {
        normalize(token, "token is required");
        return Map.of("success", true);
    }

    private void validateCaptcha(String captchaId, String captchaCode, boolean consume) {
        verifyCode(
                normalize(captchaId, "captchaId is required"),
                normalize(captchaCode, "captchaCode is required"),
                CAPTCHA_CODE_TYPE,
                "captchaCode is invalid",
                consume
        );
    }

    private void saveVerificationCode(String targetValue, String code, String codeType, int expireMinutes) {
        AuthVerificationCode record = new AuthVerificationCode();
        record.setTargetValue(targetValue);
        record.setCode(code);
        record.setCodeType(codeType);
        record.setUsed(false);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(expireMinutes));
        authVerificationCodeMapper.insert(record);
    }

    private void verifyCode(String targetValue, String actualCode, String codeType, String invalidMessage) {
        verifyCode(targetValue, actualCode, codeType, invalidMessage, true);
    }

    private void verifyCode(String targetValue, String actualCode, String codeType, String invalidMessage, boolean consume) {
        AuthVerificationCode latest = authVerificationCodeMapper.findLatestUnused(targetValue, codeType);
        if (latest == null) {
            throw new BusinessException(40008, "verification code has not been sent");
        }
        if (latest.getExpiresAt() == null || latest.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40012, "verification code has expired");
        }
        if (!latest.getCode().equalsIgnoreCase(actualCode)) {
            throw new BusinessException(40009, invalidMessage);
        }
        if (consume) {
            authVerificationCodeMapper.markUsed(latest.getId());
        }
    }

    private AuthUser createPhoneUser(String phone) {
        AuthUser user = new AuthUser();
        user.setUserId(buildUserId());
        user.setUsername("phone_" + phone);
        user.setPassword("SMS_LOGIN_ONLY");
        user.setNickname("User-" + lastFour(phone));
        user.setPhone(phone);
        authUserMapper.insert(user);
        return user;
    }

    private AuthUser createEmailUser(String email) {
        String prefix = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        AuthUser user = new AuthUser();
        user.setUserId(buildUserId());
        user.setUsername("email_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        user.setPassword("EMAIL_LOGIN_ONLY");
        user.setNickname(prefix.isBlank() ? "New User" : prefix);
        user.setEmail(email);
        authUserMapper.insert(user);
        return user;
    }

    private String normalize(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(40000, message);
        }
        return value.trim();
    }

    private String normalizePhone(String phone) {
        String normalizedPhone = normalize(phone, "phone is required");
        if (!normalizedPhone.matches("^1\\d{10}$")) {
            throw new BusinessException(40010, "phone format is invalid");
        }
        return normalizedPhone;
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = normalize(email, "email is required").toLowerCase();
        if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new BusinessException(40011, "email format is invalid");
        }
        return normalizedEmail;
    }

    private String generateVerificationCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String generateCaptchaCode() {
        String source = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder builder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            builder.append(source.charAt(RANDOM.nextInt(source.length())));
        }
        return builder.toString();
    }

    private String buildCaptchaImageBase64(String captchaCode) {
        try {
            int width = 130;
            int height = 48;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(248, 250, 252));
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setFont(new Font("Arial", Font.BOLD, 28));

            for (int i = 0; i < 8; i++) {
                graphics.setColor(randomColor(160, 220));
                graphics.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height), RANDOM.nextInt(width), RANDOM.nextInt(height));
            }

            for (int i = 0; i < captchaCode.length(); i++) {
                graphics.setColor(randomColor(20, 140));
                int x = 18 + i * 24 + RANDOM.nextInt(4);
                int y = 32 + RANDOM.nextInt(8);
                graphics.drawString(String.valueOf(captchaCode.charAt(i)), x, y);
            }

            for (int i = 0; i < 40; i++) {
                graphics.setColor(randomColor(120, 200));
                graphics.fillOval(RANDOM.nextInt(width), RANDOM.nextInt(height), 2, 2);
            }
            graphics.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new BusinessException(50013, "failed to generate captcha image: " + ex.getMessage());
        }
    }

    private Color randomColor(int min, int max) {
        int red = min + RANDOM.nextInt(max - min + 1);
        int green = min + RANDOM.nextInt(max - min + 1);
        int blue = min + RANDOM.nextInt(max - min + 1);
        return new Color(red, green, blue);
    }

    private String buildUserId() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String createToken(String userId, String loginType) {
        if (jwtSecret.isBlank()) {
            throw new BusinessException(50014, "auth jwt secret is required");
        }
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uid", userId);
            payload.put("type", loginType);
            payload.put("iat", now);
            payload.put("exp", now + jwtExpireSeconds);

            String headerPart = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(payload));
            String content = headerPart + "." + payloadPart;
            return content + "." + sign(content);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(50015, "failed to create auth token: " + ex.getMessage());
        }
    }

    private Map<String, Object> parseAndValidateToken(String token) {
        if (jwtSecret.isBlank()) {
            throw new BusinessException(50014, "auth jwt secret is required");
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException(40005, "login status is invalid or expired");
            }
            String content = parts[0] + "." + parts[1];
            if (!sign(content).equals(parts[2])) {
                throw new BusinessException(40005, "login status is invalid or expired");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, new TypeReference<>() { });
            long exp = longClaim(payload, "exp");
            if (exp <= Instant.now().getEpochSecond()) {
                throw new BusinessException(40005, "login status is invalid or expired");
            }
            return payload;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40005, "login status is invalid or expired");
        }
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64Url(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String stringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) {
            throw new BusinessException(40005, "login status is invalid or expired");
        }
        return String.valueOf(value);
    }

    private long longClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            return Long.parseLong(String.valueOf(value));
        }
        throw new BusinessException(40005, "login status is invalid or expired");
    }

    private Map<String, Object> loginResult(String token, AuthUserResponse user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("tokenType", "Bearer");
        data.put("expiresIn", jwtExpireSeconds);
        data.put("user", user);
        data.put("userInfo", toSimpleUserInfo(user));
        return data;
    }

    private Map<String, Object> toSimpleUserInfo(AuthUserResponse user) {
        return Map.of(
                "userId", user.userId(),
                "nickname", user.nickname()
        );
    }

    private String lastFour(String value) {
        if (value.length() <= 4) {
            return value;
        }
        return value.substring(value.length() - 4);
    }

    private AuthUserResponse toUserResponse(AuthUser user) {
        return new AuthUserResponse(user.getUserId(), user.getUsername(), user.getNickname());
    }

    private static final class QrLoginState {
        private final String qrId;
        private final String platform;
        private int pollCount;

        private QrLoginState(String qrId, String platform) {
            this.qrId = qrId;
            this.platform = platform;
        }
    }
}
