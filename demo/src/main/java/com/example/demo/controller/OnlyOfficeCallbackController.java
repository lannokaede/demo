package com.example.demo.controller;

import com.example.demo.service.OnlyOfficeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/onlyoffice")
public class OnlyOfficeCallbackController {

    private final OnlyOfficeService onlyOfficeService;

    public OnlyOfficeCallbackController(OnlyOfficeService onlyOfficeService) {
        this.onlyOfficeService = onlyOfficeService;
    }

    @PostMapping("/callback/{fileId}")
    public Map<String, Object> callback(@PathVariable String fileId, @RequestBody(required = false) Map<String, Object> body) {
        return onlyOfficeService.handleCallback(fileId, body == null ? Map.of() : body);
    }
}
