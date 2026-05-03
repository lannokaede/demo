# PPT 生成前端对接文档

本文档用于前端对接后端 `/api/ppt` 接口，聚焦以下两类能力：

- 对话/带文件生成 PPT
- 预览与下载生成结果

## 1. 通用约定

### 1.1 Base URL

开发环境示例：

```text
http://127.0.0.1:8080
```

所有接口均挂在：

```text
/api/ppt
```

### 1.2 统一响应格式

后端统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

前端应始终从 `data` 读取业务数据。

### 1.3 会话主键

后端已做兼容，以下字段等价：

- `task_id`
- `taskId`
- `session_id`
- `sessionId`

前端推荐统一使用 `sessionId`。

## 2. 主流程

推荐前端按以下顺序接入：

1. 如有文件，先上传文件
2. 创建生成会话
3. 轮询会话状态
4. 如需澄清，提交澄清信息
5. 查看并确认/修改大纲
6. 查看 draft
7. 如需改稿，提交自然语言改稿
8. 导出最终结果
9. 获取产物列表并展示预览/下载

## 3. 接口说明

### 3.1 上传文件

`POST /api/ppt/uploads`

`Content-Type: multipart/form-data`

表单字段：

- `file`: 必填，文件本体
- `asset_type`: 可选，示例：`document`、`image`
- `role`: 可选，示例：`reference_file`、`reference_image`

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "upload_id": "upl_xxx",
    "uploadId": "upl_xxx",
    "asset_type": "document",
    "assetType": "document",
    "role": "reference_file"
  }
}
```

前端后续创建会话时，将 `upload_id` 放入 `user_assets`。

### 3.2 创建 PPT 会话

`POST /api/ppt/sessions`

也兼容旧接口：

- `POST /api/ppt/tasks`
- `POST /api/ppt/generate`

推荐请求体：

```json
{
  "user_input": "请生成一份关于 Transformer 入门的教学 PPT，面向本科生，20 页左右。",
  "user_assets": [
    {
      "type": "document",
      "upload_id": "upl_xxx",
      "role": "reference_file"
    }
  ],
  "pipeline_options": {
    "pipeline_id": "front_demo_001",
    "runtime_mode": "prod-like",
    "force_restart": true
  }
}
```

旧前端请求也兼容，例如：

```json
{
  "prompt": "请生成一份关于 Transformer 入门的教学 PPT",
  "attachments": [
    {
      "fileId": "file_xxx"
    }
  ]
}
```

响应关键字段：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session_id": "codex_xxx",
    "sessionId": "codex_xxx",
    "task_id": "codex_xxx",
    "taskId": "codex_xxx",
    "status": "running",
    "current_stage": "intent",
    "currentStage": "intent",
    "next_action": "poll",
    "nextAction": "poll",
    "sessionUrl": "http://127.0.0.1:8080/api/ppt/sessions/codex_xxx",
    "assetsUrl": "http://127.0.0.1:8080/api/ppt/sessions/codex_xxx/assets",
    "outlineUrl": "http://127.0.0.1:8080/api/ppt/sessions/codex_xxx/outline",
    "draftUrl": "http://127.0.0.1:8080/api/ppt/sessions/codex_xxx/draft",
    "artifactsUrl": "http://127.0.0.1:8080/api/ppt/sessions/codex_xxx/artifacts"
  }
}
```

### 3.3 轮询会话状态

`GET /api/ppt/sessions/{sessionId}`

兼容旧接口：

`GET /api/ppt/tasks/{taskId}`

前端轮询建议：

- 创建会话后每 2 到 3 秒轮询一次
- 根据 `status` 和 `nextAction` 控制页面流转

常见状态示例：

- `running`: 正在生成中
- `awaiting_clarification`: 等待补充澄清信息
- `awaiting_outline_review`: 等待确认大纲
- `awaiting_draft_review`: 等待确认 draft
- `completed`: 已完成
- `failed`: 失败

### 3.4 提交澄清信息

`POST /api/ppt/sessions/{sessionId}/clarifications`

请求示例：

```json
{
  "answers": [
    {
      "question": "课程受众是谁？",
      "answer": "本科二年级学生"
    }
  ]
}
```

提交后继续轮询 `GET /api/ppt/sessions/{sessionId}`。

### 3.4.1 追加资料到已有 session

`POST /api/ppt/sessions/{sessionId}/assets`

用于“当前 PPT session 已经创建后，用户继续上传 PDF/文档并希望纳入后续生成”的场景。

前端流程：

1. 先调用 `POST /api/ppt/uploads` 上传文件，拿到 `upload_id`
2. 如果当前已有 `sessionId`，不要重新调用 `POST /api/ppt/sessions`
3. 改为调用本接口，把 `upload_id` 追加到当前 session
4. 提交后继续轮询 `GET /api/ppt/sessions/{sessionId}`

请求示例：

```json
{
  "instructions": "请把这个 PDF 也纳入当前课件内容，重新整理大纲或草稿。",
  "user_assets": [
    {
      "type": "document",
      "upload_id": "upl_xxx",
      "role": "reference_file"
    }
  ]
}
```

兼容写法：

```json
{
  "content": "结合我刚上传的 PDF，补充应用案例",
  "assets": [
    {
      "uploadId": "upl_xxx"
    }
  ]
}
```

响应仍然是当前 session 状态，前端按 `status/currentStage/nextAction` 流转即可。

### 3.5 查看大纲

`GET /api/ppt/sessions/{sessionId}/outline`

用于展示当前大纲内容与页数。

### 3.6 确认或修改大纲

`POST /api/ppt/sessions/{sessionId}/outline/review`

确认大纲：

```json
{
  "action": "accept"
}
```

自然语言修改大纲：

```json
{
  "action": "revise",
  "instructions": "增加一页实际应用案例，并减少公式页"
}
```

修改后继续查看大纲，或继续轮询会话状态。

### 3.7 查看 draft

`GET /api/ppt/sessions/{sessionId}/draft`

这是前端“预览 PPT”最关键的接口。

响应中会返回机器学习侧真实生成的预览地址，前端应优先读取：

- `data.downloadUrl`

后端同时保留上游原字段：

- `data.minio_download_url`

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "codex_xxx",
    "status": "awaiting_draft_review",
    "downloadUrl": "https://xxx/minio/draft.pptx?signature=...",
    "minio_download_url": "https://xxx/minio/draft.pptx?signature=...",
    "downloadUrls": {
      "draft_pptx": "https://xxx/minio/draft.pptx?signature=..."
    }
  }
}
```

前端处理建议：

- 预览 PPT 时，不再使用写死地址
- 改为使用 `data.downloadUrl`
- 如果要区分不同文件，可读 `data.downloadUrls`

### 3.8 自然语言改稿

`POST /api/ppt/sessions/{sessionId}/draft/revise`

请求示例：

```json
{
  "instructions": "第三页内容太学术化了，改得更口语一点，并增加一个生活化例子。"
}
```

提交后继续轮询会话，或重新请求 `GET /draft` 获取最新预览地址。

### 3.9 导出最终结果

`POST /api/ppt/sessions/{sessionId}/finalize`

调用后继续轮询 `GET /api/ppt/sessions/{sessionId}`，直到 `status=completed`。

### 3.10 获取最终产物列表

`GET /api/ppt/sessions/{sessionId}/artifacts`

兼容旧接口：

`GET /api/ppt/tasks/{taskId}/artifacts`

响应中会返回最终产物下载地址。前端优先读取：

- `data.downloadUrls`

后端同时保留上游原字段：

- `data.minio_download_urls`

示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "codex_xxx",
    "status": "completed",
    "downloadUrls": {
      "pptx": "https://xxx/minio/final.pptx?signature=...",
      "draft_pptx": "https://xxx/minio/draft.pptx?signature=...",
      "teaching_plan_docx": "https://xxx/minio/teaching-plan.docx?signature=...",
      "h5_entry_html": "https://xxx/minio/index.html?signature=...",
      "h5_package_zip": "https://xxx/minio/h5.zip?signature=...",
      "package_zip": "https://xxx/minio/all.zip?signature=..."
    }
  }
}
```

前端常用映射建议：

- PPT 预览/下载：`downloadUrls.pptx` 或 draft 阶段使用 `GET /draft` 返回的 `downloadUrl`
- 教案下载：`downloadUrls.teaching_plan_docx`
- H5 入口：`downloadUrls.h5_entry_html`
- H5 压缩包：`downloadUrls.h5_package_zip`
- 全量打包下载：`downloadUrls.package_zip`

## 4. 前端最简接入建议

### 4.1 对话生成 PPT

最简流程：

1. 用户输入 prompt
2. 如有附件，先调用 `/api/ppt/uploads`
3. 调用 `/api/ppt/sessions`
4. 保存 `sessionId`
5. 轮询 `/api/ppt/sessions/{sessionId}`
6. 根据状态进入澄清、大纲确认、draft 预览、最终完成页面

### 4.2 预览 PPT

draft 阶段：

1. 调用 `GET /api/ppt/sessions/{sessionId}/draft`
2. 使用返回的 `data.downloadUrl` 作为预览/下载地址

完成阶段：

1. 调用 `GET /api/ppt/sessions/{sessionId}/artifacts`
2. 使用 `data.downloadUrls.pptx` 作为最终 PPT 地址

## 5. 前端兼容建议

### 5.1 推荐读取字段

前端优先读取以下兼容字段：

- 会话 ID：`data.sessionId`
- 当前阶段：`data.currentStage`
- 下一步动作：`data.nextAction`
- draft 预览地址：`data.downloadUrl`
- 产物地址集合：`data.downloadUrls`

### 5.2 保留兼容字段

后端目前仍会返回部分上游原字段，前端可作为兜底：

- `session_id`
- `task_id`
- `current_stage`
- `next_action`
- `minio_download_url`
- `minio_download_urls`

## 6. 说明

### 6.1 与旧写死预览地址的关系

PPT 生成链路请使用 `/api/ppt/...` 系列接口。

该链路中的预览 URL 已改为来自机器学习侧真实返回的地址，不再依赖写死的 mock 预览图地址。

### 6.2 数字人接口

本次文档未纳入数字人插件对接说明。
