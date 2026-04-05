package com.example.demo.controller;

import com.example.demo.dto.AuthLoginRequest;
import com.example.demo.dto.AuthRegisterRequest;
import com.example.demo.dto.WpsResult;
import com.example.demo.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public WpsResult<Map<String, Object>> register(@RequestBody AuthRegisterRequest request) {
        return WpsResult.ok(authService.register(request));
    }

    @PostMapping("/login")
    public WpsResult<Map<String, Object>> login(@RequestBody AuthLoginRequest request) {
        return WpsResult.ok(authService.login(request));
    }

    @GetMapping("/captcha")
    public WpsResult<Map<String, Object>> captcha() {
        return WpsResult.ok(authService.getCaptcha(), "success");
    }

    @PostMapping("/sms/send")
    public WpsResult<Void> sendSmsCode(@RequestBody Map<String, String> request) {
        authService.sendSmsCode(request.get("phone"), request.get("captchaId"), request.get("captchaCode"));
        return WpsResult.ok(null, "验证码已发送");
    }

    @PostMapping("/sms/login")
    public WpsResult<Map<String, Object>> smsLogin(@RequestBody Map<String, String> request) {
        return WpsResult.ok(
                authService.loginBySms(request.get("phone"), request.get("smsCode"), request.get("captchaId"), request.get("captchaCode")),
                "登录成功"
        );
    }

    @PostMapping("/email/send")
    public WpsResult<Void> sendEmailCode(@RequestBody Map<String, String> request) {
        authService.sendEmailCode(request.get("email"), request.get("captchaId"), request.get("captchaCode"));
        return WpsResult.ok(null, "验证码已发送");
    }

    @PostMapping("/email/login")
    public WpsResult<Map<String, Object>> emailLogin(@RequestBody Map<String, String> request) {
        return WpsResult.ok(
                authService.loginByEmail(request.get("email"), request.get("emailCode"), request.get("captchaId"), request.get("captchaCode")),
                "登录成功"
        );
    }

    @GetMapping("/qr/create")
    public WpsResult<Map<String, Object>> createQrLogin(@RequestParam String platform) {
        return WpsResult.ok(authService.createQrLogin(platform));
    }

    @GetMapping("/qr/status/{qrId}")
    public WpsResult<Map<String, Object>> qrStatus(@PathVariable String qrId) {
        return WpsResult.ok(authService.getQrStatus(qrId));
    }

    @GetMapping("/me")
    public WpsResult<Map<String, Object>> me(@RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        return WpsResult.ok(authService.getProfile(extractToken(authorization)));
    }

    @PostMapping("/logout")
    public WpsResult<Map<String, Object>> logout(@RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        return WpsResult.ok(authService.logout(extractToken(authorization)));
    }

    private String extractToken(String authorization) {
        if (authorization == null) {
            return "";
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
