package com.example.demo.controller;

import com.example.demo.dto.WpsResult;
import com.example.demo.service.PptTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
public class PptTaskController {

    private final PptTaskService pptTaskService;

    public PptTaskController(PptTaskService pptTaskService) {
        this.pptTaskService = pptTaskService;
    }

    /**
     * 创建 PPT 任务。
     * 一般由前端发起，提交生成参数后返回任务 id 和初始状态。
     */
    @PostMapping("/api/ppt/tasks")
    public WpsResult<Map<String, Object>> createTask(@RequestBody Map<String, Object> request) {
        return WpsResult.ok(pptTaskService.createTask(request, baseUrl()));
    }

    @PostMapping("/api/ppt/generate")
    public WpsResult<Map<String, Object>> generate(@RequestBody Map<String, Object> request) {
        return WpsResult.ok(pptTaskService.createTask(request, baseUrl()));
    }

    /**
     * 查询单个任务详情。
     * 用任务 id 查看当前生成进度、状态和结果信息。
     */
    @GetMapping("/api/ppt/tasks/{taskId}")
    public WpsResult<Map<String, Object>> getTask(@PathVariable String taskId) {
        return WpsResult.ok(pptTaskService.getTask(taskId));
    }

    /**
     * 获取下载信息。
     * 当任务完成后，前端可以通过这个接口拿到下载地址或下载相关数据。
     */
    @GetMapping("/api/ppt/tasks/{taskId}/download-info")
    public WpsResult<Map<String, Object>> getDownloadInfo(@PathVariable String taskId) {
        return WpsResult.ok(pptTaskService.getDownloadInfo(taskId));
    }

    /**
     * 处理模型服务回调。
     * ML 服务在生成完成或状态变化后，会通过这个接口把结果推回后端。
     */
    @PostMapping("/api/ppt/tasks/{taskId}/ml-callback")
    public WpsResult<Map<String, Object>> mlCallback(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-ML-Callback-Token", defaultValue = "") String callbackToken
    ) {
        return WpsResult.ok(pptTaskService.updateFromMlCallback(taskId, request, callbackToken));
    }

    /**
     * 生成当前服务的基础地址，方便拼接回调地址或下载地址。
     */
    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
