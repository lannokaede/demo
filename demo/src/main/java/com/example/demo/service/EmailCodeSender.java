package com.example.demo.service;

public interface EmailCodeSender {
    void sendCode(String email, String code);
}
