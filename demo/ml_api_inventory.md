# 机器学习相关接口梳理

## 1. 判定范围

这份清单把下面三类接口都算作“机器学习相关”：

1. 直接调用或代理外部 AI/ML 服务的接口
2. 给模型提供上下文的接口，例如对话、知识库引用、附件上传
3. 获取模型生成结果、预览、改稿、导出等接口

不纳入本清单的接口：

- 认证登录：`/api/auth/**`
- 普通反馈：`/api/feedback/**`
- OnlyOffice 基础文件/回调接口
- 模板管理：`/api/templates/**`

## 2. 总体结论

### 2.1 真正连接外部模型服务的接口

`/api/ppt/**` 是当前项目里最核心、最完整的 AI/ML 链路。

- 控制器入口在 `PptTaskController`
- 服务编排在 `PptTaskService`
- 真正的远程调用在 `HttpSuperPptClient`
- 远端服务配置来自 `superppt.base-url`

对应代码：

- `PptTaskController` 路由定义：`demo/src/main/java/com/example/demo/controller/PptTaskController.java`
- `PptTaskService` 编排：`demo/src/main/java/com/example/demo/service/PptTaskService.java`
- `HttpSuperPptClient` 远程代理：`demo/src/main/java/com/example/demo/service/HttpSuperPptClient.java`

### 2.2 与 AI 相关但当前是 mock 的接口

`/api/chat/**` 和 `/api/knowledge/files/**` 明显属于 AI/知识库场景，但当前实现没有直连真实模型，而是走 `PlatformMockService` 的内存 mock。

证据：

- 对话回复来自 `buildAssistantReply(...)`
- 是否展示需求表单由 `needsRequirementForm(...)` 判断
- 知识库“引用到对话”只是往当前会话追加一条提示消息

对应代码：

- `demo/src/main/java/com/example/demo/service/PlatformMockService.java`

### 2.3 AI 相关辅助接口

`/api/files/upload` 和 `/api/files/{fileId}/content` 不是模型接口本身，但会给聊天、知识库、PPT 生成链路提供附件和文件内容，因此建议按“辅助接口”一起看。

## 3. 机器学习相关接口清单

## 3.1 对话链路

实现位置：

- `ChatController`: `demo/src/main/java/com/example/demo/controller/ChatController.java`
- `PlatformMockService`: `demo/src/main/java/com/example/demo/service/PlatformMockService.java`

| 方法 | 路径 | 用途 | 当前实现 |
| --- | --- | --- | --- |
| POST | `/api/chat/sessions` | 创建聊天会话 | mock |
| GET | `/api/chat/sessions` | 查询聊天会话列表 | mock |
| GET | `/api/chat/sessions/{sessionId}` | 查看单个聊天会话 | mock |
| POST | `/api/chat/sessions/{sessionId}/messages` | 发送消息，驱动“AI 对话”回复 | mock |

关键代码位置：

- 聊天路由入口：`ChatController.java:25`
- 会话上下文提取：`PlatformMockService.java:72`
- 发送消息与生成回复：`PlatformMockService.java:88`
- mock 回复逻辑：`PlatformMockService.java:363`

补充说明：

- `PptTaskService` 会读取聊天会话上下文，把历史消息和附件拼成 PPT 生成请求
- 这说明聊天接口和 PPT 生成接口是串联关系，而不是完全独立

## 3.2 知识库链路

实现位置：

- `KnowledgeController`: `demo/src/main/java/com/example/demo/controller/KnowledgeController.java`
- `PlatformMockService`: `demo/src/main/java/com/example/demo/service/PlatformMockService.java`

| 方法 | 路径 | 用途 | 当前实现 |
| --- | --- | --- | --- |
| GET | `/api/knowledge/files` | 列出知识库文件 | mock |
| POST | `/api/knowledge/files/upload` | 上传知识库文件 | mock |
| GET | `/api/knowledge/files/{fileId}` | 查看知识库文件详情 | mock |
| DELETE | `/api/knowledge/files/{fileId}` | 删除知识库文件 | mock |
| POST | `/api/knowledge/files/{fileId}/quote-to-chat` | 把知识库文件引用到聊天会话 | mock |

关键代码位置：

- 知识库路由入口：`KnowledgeController.java:29`
- 上传知识库文件：`PlatformMockService.java:164`
- 引用到聊天：`PlatformMockService.java:193`

补充说明：

- 当前“引用到聊天”并不会做真实向量检索或 RAG，只是给会话加一条提示消息
- 因此这部分更像 AI 场景 mock，而不是真实知识库检索

## 3.3 PPT 生成主链路

实现位置：

- `PptTaskController`: `demo/src/main/java/com/example/demo/controller/PptTaskController.java`
- `PptTaskService`: `demo/src/main/java/com/example/demo/service/PptTaskService.java`
- `HttpSuperPptClient`: `demo/src/main/java/com/example/demo/service/HttpSuperPptClient.java`

### A. 服务探活与会话管理

| 方法 | 路径 | 用途 | 上游调用 |
| --- | --- | --- | --- |
| GET | `/api/ppt/healthz` | 检查 AI/PPT 服务健康状态 | `/healthz` |
| GET | `/api/ppt/tasks` | 列出任务，兼容旧前端 | `/sessions` |
| GET | `/api/ppt/sessions` | 列出会话 | `/sessions` |
| POST | `/api/ppt/tasks` | 创建生成任务，旧接口 | `/sessions` |
| POST | `/api/ppt/generate` | 创建生成任务，旧接口别名 | `/sessions` |
| POST | `/api/ppt/sessions` | 创建生成会话 | `/sessions` |
| GET | `/api/ppt/tasks/{taskId}` | 查询任务状态，旧接口 | `/sessions/{id}` |
| GET | `/api/ppt/sessions/{sessionId}` | 查询会话状态 | `/sessions/{id}` |

关键代码位置：

- 路由定义：`PptTaskController.java:35`
- 创建任务：`PptTaskController.java:50`
- 创建会话：`PptTaskController.java:55`
- 服务层创建会话：`PptTaskService.java:67`
- 上游真实调用：`HttpSuperPptClient.java:113`

### B. 输入资料与上下文增强

| 方法 | 路径 | 用途 | 上游调用 |
| --- | --- | --- | --- |
| POST | `/api/ppt/uploads` | 上传生成素材 | `/uploads` |
| GET | `/api/ppt/uploads/{uploadId}` | 查看上传素材信息 | `/uploads/{id}` |
| POST | `/api/ppt/sessions/{sessionId}/assets` | 给已有会话追加资料 | `/sessions/{id}/assets` |
| POST | `/api/ppt/sessions/{sessionId}/clarifications` | 提交澄清问答 | `/sessions/{id}/clarifications` |

关键代码位置：

- 上传路由：`PptTaskController.java:60`
- 追加资料路由：`PptTaskController.java:84`
- 澄清路由：`PptTaskController.java:97`
- 会话请求组装：`PptTaskService.java:185`
- 从聊天会话提取上下文：`PptTaskService.java:238`
- 从老附件格式补传到上游：`PptTaskService.java:383`
- 上游上传：`HttpSuperPptClient.java:69`

补充说明：

- `PptTaskService` 会兼容旧字段，如 `prompt`、`content`、`attachments`
- 如果前端带了 `sessionId`，会把聊天里的历史文本和附件一起拼进生成请求
- 这一步是当前项目里最像“多模态上下文汇总”的逻辑

### C. 大纲、草稿、改稿、定稿

| 方法 | 路径 | 用途 | 上游调用 |
| --- | --- | --- | --- |
| GET | `/api/ppt/sessions/{sessionId}/outline` | 获取大纲 | `/sessions/{id}/outline` |
| POST | `/api/ppt/sessions/{sessionId}/outline/review` | 确认或修改大纲 | `/sessions/{id}/outline/review` |
| GET | `/api/ppt/sessions/{sessionId}/draft` | 获取 draft 预览信息 | `/sessions/{id}/draft` |
| GET | `/api/ppt/sessions/{sessionId}/revisions` | 获取修订记录 | `/sessions/{id}/revisions` |
| POST | `/api/ppt/sessions/{sessionId}/slides/edit` | 局部编辑页面 | `/sessions/{id}/slides/edit` |
| POST | `/api/ppt/sessions/{sessionId}/draft/revise` | 自然语言改稿 | `/sessions/{id}/draft/revise` |
| POST | `/api/ppt/sessions/{sessionId}/finalize` | 定稿导出 | `/sessions/{id}/finalize` |

关键代码位置：

- 大纲获取：`PptTaskController.java:105`
- 大纲确认/修改：`PptTaskController.java:110`
- draft 获取：`PptTaskController.java:118`
- revisions 获取：`PptTaskController.java:123`
- slides edit：`PptTaskController.java:128`
- draft revise：`PptTaskController.java:136`
- finalize：`PptTaskController.java:144`
- 服务层对应方法：`PptTaskService.java:97` 到 `PptTaskService.java:125`
- 上游真实调用：`HttpSuperPptClient.java:128` 到 `HttpSuperPptClient.java:163`

补充说明：

- `frontend_ppt_api.md` 已把这一段定义成完整前端对接主流程
- 其中 `draft/revise` 和 `slides/edit` 都是典型的“自然语言驱动内容修改”接口

### D. 结果产物、预览、下载

| 方法 | 路径 | 用途 | 上游调用 |
| --- | --- | --- | --- |
| GET | `/api/ppt/tasks/{taskId}/download-info` | 获取下载信息，旧接口 | `/sessions/{id}/artifacts` |
| GET | `/api/ppt/tasks/{taskId}/artifacts` | 获取任务产物，旧接口 | `/sessions/{id}/artifacts` |
| GET | `/api/ppt/sessions/{sessionId}/artifacts` | 获取会话产物 | `/sessions/{id}/artifacts` |
| GET | `/api/ppt/sessions/{sessionId}/onlyoffice-preview` | 获取 OnlyOffice 预览配置 | 先读 draft/artifacts，再生成预览配置 |
| GET | `/api/ppt/tasks/{taskId}/onlyoffice-preview` | 旧任务预览接口 | 同上 |
| GET | `/api/ppt/tasks/{taskId}/download/{artifactName}` | 下载任务产物 | `/sessions/{id}/download/{artifact}` |
| GET | `/api/ppt/sessions/{sessionId}/download/{artifactName}` | 下载会话产物 | `/sessions/{id}/download/{artifact}` |

关键代码位置：

- artifacts 路由：`PptTaskController.java:149`
- onlyoffice preview 路由：`PptTaskController.java:159`
- 下载路由：`PptTaskController.java:196`
- 预览源拼装：`PptTaskService.java:137`
- 产物下载上游调用：`HttpSuperPptClient.java:168`
- 文件下载上游调用：`HttpSuperPptClient.java:183`

补充说明：

- 这些接口本身不执行推理，但直接消费模型生成结果
- 如果你要画“AI 生成链路全景图”，这部分也应该算进去

### E. 数字人插件扩展

| 方法 | 路径 | 用途 | 上游调用 |
| --- | --- | --- | --- |
| GET | `/api/ppt/sessions/{sessionId}/plugins/digital-human` | 获取数字人插件信息 | `/sessions/{id}/plugins/digital-human` |
| POST | `/api/ppt/sessions/{sessionId}/plugins/digital-human` | 触发数字人插件 | `/sessions/{id}/plugins/digital-human` |

关键代码位置：

- 数字人 GET：`PptTaskController.java:183`
- 数字人 POST：`PptTaskController.java:188`
- 服务层：`PptTaskService.java:169`
- 上游调用：`HttpSuperPptClient.java:173`

补充说明：

- 这部分是否算“机器学习接口”取决于上游数字人插件实现
- 从当前后端看，它已经被纳入 AI/PPT 生成链路

## 3.4 AI 辅助接口

实现位置：

- `FileController`: `demo/src/main/java/com/example/demo/controller/FileController.java`

| 方法 | 路径 | 用途 | 当前角色 |
| --- | --- | --- | --- |
| POST | `/api/files/upload` | 通用文件上传 | AI 链路辅助接口 |
| GET | `/api/files/{fileId}/content` | 读取已上传文件内容 | AI 链路辅助接口 |

关键代码位置：

- 上传：`FileController.java:29`
- 文件内容读取：`FileController.java:38`

补充说明：

- 旧版聊天附件、知识库上传、本地文件转上游素材时都可能依赖这类文件能力
- 它不是模型接口本体，但建议在联调时一起纳入

## 4. 这批接口之间的关系

最核心的调用链如下：

1. 用户先在 `/api/chat/**` 中形成需求和附件上下文
2. `PptTaskService` 读取聊天上下文，组装成 `user_input` 和 `user_assets`
3. `/api/ppt/sessions` 把请求转发给外部 `superppt` 服务
4. 后续通过 outline、draft、revise、finalize、artifacts 等接口持续交互
5. 最终通过预览和下载接口消费生成结果

如果只看“真实模型调用”，优先关注 `/api/ppt/**`。

如果要看“整个机器学习业务闭环”，则应该一起看：

- `/api/chat/**`
- `/api/knowledge/files/**`
- `/api/ppt/**`
- `/api/files/**` 中的上传和文件内容读取

## 5. 最终建议

如果你接下来要继续拆解，我建议按下面优先级看：

1. 先看 `/api/ppt/**`，这是唯一真正落到外部 AI 服务的主链路
2. 再看 `/api/chat/**` 和 `/api/knowledge/files/**`，这是上游输入上下文
3. 最后补 `/api/files/**`，这是附件/素材基础设施

