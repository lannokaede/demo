package com.example.demo.dto;

public record WpsResult<T>(int code, T data, String message) {
    public static <T> WpsResult<T> ok(T data) {
        return new WpsResult<>(0, data, "");
    }

    public static <T> WpsResult<T> ok(T data, String message) {
        return new WpsResult<>(0, data, message);
    }

    public static <T> WpsResult<T> fail(int code, String message) {
        return new WpsResult<>(code, null, message);
    }
}
