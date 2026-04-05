package com.example.demo.dto;

public record AuthRegisterRequest(String username, String password, String confirmPassword, String nickname) {
}
