package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailCodeSender implements EmailCodeSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;
    private final String brandName;
    private final String sceneText;

    public SmtpEmailCodeSender(
            JavaMailSender javaMailSender,
            @Value("${spring.mail.username}") String fromAddress,
            @Value("${auth.email-template.brand-name:AI PPT Studio}") String brandName,
            @Value("${auth.email-template.scene-text:AI课件与PPT生成平台}") String sceneText
    ) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        this.brandName = brandName == null || brandName.isBlank() ? "AI PPT Studio" : brandName.trim();
        this.sceneText = sceneText == null || sceneText.isBlank() ? "AI课件与PPT生成平台" : sceneText.trim();
    }

    @Override
    public void sendCode(String email, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject("[" + brandName + "] 登录验证码");
            message.setText(buildEmailText(code));
            javaMailSender.send(message);
        } catch (Exception ex) {
            throw new BusinessException(50012, "failed to send email verification code: " + ex.getMessage());
        }
    }

    private String buildEmailText(String code) {
        return brandName + " 登录验证\n\n"
                + "您好，\n\n"
                + "您正在使用" + sceneText + "进行邮箱登录验证。\n"
                + "本次验证码为： " + code + "\n\n"
                + "验证码 5 分钟内有效，请勿泄露给他人。\n"
                + "如果这不是您的操作，请忽略此邮件。\n\n"
                + "--\n"
                + brandName;
    }
}
