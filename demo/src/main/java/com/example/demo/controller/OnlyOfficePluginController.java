package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.OnlyOfficeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/onlyoffice/plugins")
public class OnlyOfficePluginController {

    private final OnlyOfficeService onlyOfficeService;

    public OnlyOfficePluginController(OnlyOfficeService onlyOfficeService) {
        this.onlyOfficeService = onlyOfficeService;
    }

    @GetMapping("/h5-game/install-info")
    public WpsResult<Map<String, Object>> installInfo() {
        return WpsResult.ok(onlyOfficeService.getH5GamePluginInstallInfo(baseUrl()));
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
