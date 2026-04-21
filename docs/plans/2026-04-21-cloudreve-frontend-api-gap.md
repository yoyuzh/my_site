# Cloudreve 前端 API 缺口清单

## 1. 结论

Cloudreve 前端不能只靠修改 Vite proxy 直接接入当前 `my_site` 后端。

原因有三点：

- Cloudreve 前端请求前缀写死为 `/api/v4`，来源是 `third_party/cloudreve-frontend/src/api/request.ts` 的 `ApiPrefix = "/api/v4"`。
- 当前 `my_site` 后端没有任何 `/api/v4/**` Controller；运行时 API 分布在 `/api/auth/**`、`/api/user/**`、`/api/files/**`、`/api/v2/**`、`/api/transfer/**`、`/api/admin/**`。
- 两边响应包虽然都接近 `{ code, msg, data }`，但登录 token 字段、用户字段、文件 URI 模型、分页字段、上传会话、分享、管理后台资源模型都不兼容。

因此后续应优先做“前端 API 适配层”，而不是在后端硬补一套完整 Cloudreve v4 API。只有当前后端确实没有业务能力时，再按本项目模块边界补后端能力。

## 2. 对照来源

Cloudreve 前端来源：

- 源码目录：`third_party/cloudreve-frontend`
- 请求前缀：`third_party/cloudreve-frontend/src/api/request.ts`
- API 封装：`third_party/cloudreve-frontend/src/api/api.ts`
- 上传器请求封装：`third_party/cloudreve-frontend/src/component/Uploader/core/utils/request.ts`
- 本次静态抽取到 `src/api/api.ts` 中 127 个 `send(...)` 调用。

`my_site` 后端来源：

- 当前运行后端 Controller：`backend/src/main/java/com/yoyuzh/**/*Controller.java`
- 目标 API 模块参考：`backend-next/api-reference.md`
- 统一响应包：`backend/src/main/java/com/yoyuzh/shared/kernel/ApiResponse.java`

本清单是静态代码对照，不依赖本地后端是否正在运行。

## 3. 当前 `my_site` 已有 API 家族

当前后端已实现的主要 API 家族如下：

| 能力 | 当前路径 |
| --- | --- |
| 登录 / 注册 / 刷新 | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh` |
| 当前用户资料 | `GET /api/user/profile`, `PUT /api/user/profile`, `POST /api/user/password` |
| 当前用户头像 | `POST /api/user/avatar/upload/initiate`, `POST /api/user/avatar/upload`, `POST /api/user/avatar/upload/complete`, `GET /api/user/avatar/content` |
| 文件树 / 文件操作 | `GET /api/files/list`, `POST /api/files/mkdir`, `PATCH /api/files/{fileId}/rename`, `PATCH /api/files/{fileId}/move`, `POST /api/files/{fileId}/copy`, `DELETE /api/files/{fileId}` |
| 文件上传 / 下载 | `POST /api/files/upload`, `POST /api/files/upload/initiate`, `POST /api/files/upload/complete`, `GET /api/files/download/{fileId}`, `GET /api/files/download/{fileId}/url` |
| 回收站 | `GET /api/files/recycle-bin`, `POST /api/files/recycle-bin/{fileId}/restore` |
| 分享 | `POST /api/v2/shares`, `GET /api/v2/shares/{token}`, `POST /api/v2/shares/{token}/verify-password`, `POST /api/v2/shares/{token}/import`, `GET /api/v2/shares/mine`, `DELETE /api/v2/shares/{id}` |
| 搜索 / 事件 | `GET /api/v2/files/search`, `GET /api/v2/files/events` |
| 上传会话 v2 | `POST /api/v2/files/upload-sessions`, `GET /api/v2/files/upload-sessions/{sessionId}`, `GET /api/v2/files/upload-sessions/{sessionId}/prepare`, `PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}`, `POST /api/v2/files/upload-sessions/{sessionId}/content`, `POST /api/v2/files/upload-sessions/{sessionId}/complete` |
| 异步任务 | `GET /api/v2/tasks`, `GET /api/v2/tasks/{id}`, `DELETE /api/v2/tasks/{id}`, `POST /api/v2/tasks/{id}/retry`, `POST /api/v2/tasks/archive`, `POST /api/v2/tasks/extract`, `POST /api/v2/tasks/media-metadata` |
| 快传 | `/api/transfer/**` |
| 管理后台 | `/api/admin/summary`, `/api/admin/settings`, `/api/admin/users`, `/api/admin/files`, `/api/admin/file-blobs`, `/api/admin/shares`, `/api/admin/tasks`, `/api/admin/storage-policies` |

## 4. 启动首屏立即缺失的接口

Cloudreve 前端打开首页时会立即请求这些资源：

| Cloudreve 前端请求 | 当前 `my_site` 状态 | 处理建议 |
| --- | --- | --- |
| `GET /api/v4/site/config/basic` | 没有等价接口；当前只有 `GET /api/v2/site/ping` | 前端适配层先返回站点名称、图标、主题、登录开关等默认配置 |
| `GET /api/v4/site/config/login` | 没有等价接口 | 前端适配层先返回密码登录开启、注册策略、验证码策略等默认配置 |
| `GET /manifest.json` | Cloudreve 前端 proxy 到后端；当前后端没有这个 Cloudreve manifest | 可以改为前端静态 manifest，或在适配层返回 PWA manifest |

这三个是“先让页面起来”的第一批接口。

## 5. 分功能缺口

### 5.1 站点配置与验证码

Cloudreve 前端需要：

- `GET /api/v4/site/config/{section}`
- `GET /api/v4/site/captcha`

当前 `my_site`：

- 只有 `GET /api/v2/site/ping`
- 没有 Cloudreve 分 section 站点配置
- 没有 Cloudreve captcha 协议

缺口判断：

- `site/config/basic`、`site/config/login` 是首屏硬缺口。
- captcha 可以先在适配层声明关闭，除非后续要接入验证码。

### 5.2 登录、会话、用户资料

Cloudreve 前端需要：

- `GET /api/v4/session/prepare`
- `POST /api/v4/session/token`
- `POST /api/v4/session/token/2fa`
- `POST /api/v4/session/token/refresh`
- `DELETE /api/v4/session/token`
- `GET /api/v4/user/me`
- `GET /api/v4/user/info/{uid}`
- `POST /api/v4/user`
- `GET /api/v4/user/activate/{id}`
- `POST /api/v4/user/reset`
- `PATCH /api/v4/user/reset/{uid}`

当前 `my_site`：

- 已有 `POST /api/auth/login`
- 已有 `POST /api/auth/register`
- 已有 `POST /api/auth/refresh`
- 已有 `GET /api/user/profile`
- 已有 `PUT /api/user/profile`
- 已有 `POST /api/user/password`

缺口判断：

- 登录、注册、刷新、当前用户资料有业务能力，但路径、请求字段、返回字段都不同。
- Cloudreve 登录使用 `email/password`，返回 `{ user, token: { access_token, refresh_token, access_expires, refresh_expires } }`。
- `my_site` 登录使用 `username/password`，返回 `{ token, accessToken, refreshToken, user }`。
- 2FA、Passkey、OAuth、邮箱激活、找回密码、用户搜索、用户容量接口当前没有完整后端能力。

### 5.3 用户设置与安全能力

Cloudreve 前端需要：

- `GET /api/v4/user/setting`
- `PATCH /api/v4/user/setting`
- `PUT /api/v4/user/setting/avatar`
- `GET /api/v4/user/setting/2fa`
- `PUT /api/v4/user/authn`
- `POST /api/v4/user/authn`
- `DELETE /api/v4/user/authn?id=...`
- `PUT /api/v4/session/authn`
- `POST /api/v4/session/authn`
- `DELETE /api/v4/session/oauth/grant/{grant_id}`

当前 `my_site`：

- 有基础资料修改、密码修改、头像上传。
- 没有 Cloudreve 的用户设置聚合接口。
- 没有 2FA、Passkey、OAuth grant 管理。

缺口判断：

- 首期可只适配昵称、头像、密码。
- 2FA、Passkey、OAuth grant 不应为了跑 UI 盲目补后端，除非明确作为产品能力。

### 5.4 文件列表与基础文件操作

Cloudreve 前端需要：

- `GET /api/v4/file`
- `POST /api/v4/file/create`
- `GET /api/v4/file/info`
- `DELETE /api/v4/file`
- `POST /api/v4/file/rename`
- `POST /api/v4/file/move`
- `POST /api/v4/file/restore`
- `PATCH /api/v4/file/metadata`
- `PUT /api/v4/file/pin`
- `DELETE /api/v4/file/pin`
- `DELETE /api/v4/file/lock`
- `PATCH /api/v4/file/view`

当前 `my_site`：

- 有列表、mkdir、rename、move、copy、delete、recycle restore。
- 路径和对象模型不同：Cloudreve 使用 `uri` 和 `FileResponse`，`my_site` 当前多使用 `fileId`、`path`、`FileMetadataResponse`。
- 没有 pin、lock、view sync、Cloudreve metadata 字段体系。

缺口判断：

- 文件列表和基础操作可以通过前端适配层转换。
- pin、lock、view sync、metadata patch 是 Cloudreve UI 的增强能力，当前后端没有完整语义。

### 5.5 缩略图、版本、文件内容和预览会话

Cloudreve 前端需要：

- `GET /api/v4/file/thumb`
- `POST /api/v4/file/url`
- `PUT /api/v4/file/content`
- `POST /api/v4/file/version/current`
- `DELETE /api/v4/file/version`
- `PUT /api/v4/file/viewerSession`
- `GET /api/v4/file/archive`
- `GET /api/v4/file/search`

当前 `my_site`：

- 有下载 URL：`GET /api/files/download/{fileId}/url`
- 有搜索：`GET /api/v2/files/search`
- 有归档 / 解压任务：`POST /api/v2/tasks/archive`, `POST /api/v2/tasks/extract`
- 没有 Cloudreve 缩略图服务、版本控制、viewer session、文件内容直接更新、压缩包内部列表协议。

缺口判断：

- 下载 URL 和搜索可以适配。
- 缩略图、版本控制、viewer session、压缩包浏览是缺失能力。

### 5.6 上传

Cloudreve 前端需要：

- `PUT /api/v4/file/upload`
- `POST /api/v4/file/upload/{sessionID}/{index}`
- `DELETE /api/v4/file/upload`
- `GET /api/v4/callback/{policyType}/{sessionId}/{sessionKey}`
- `POST /api/v4/callback/onedrive/{sessionId}/{sessionKey}`

当前 `my_site`：

- 有 legacy 上传：`POST /api/files/upload`, `POST /api/files/upload/initiate`, `POST /api/files/upload/complete`
- 有 target v2 上传会话：`/api/v2/files/upload-sessions/**`

缺口判断：

- 上传业务能力有，但协议完全不同。
- Cloudreve 前端支持本地、从机、S3/OSS/COS/Qiniu/OneDrive/Upyun/OBS/KS3 等策略回调；当前 `my_site` 上传会话不暴露 Cloudreve 的 `policyType/sessionKey/callback` 协议。
- 首期建议改 Cloudreve 上传器适配 `my_site` 的 `/api/v2/files/upload-sessions/**`，不要在后端新增 Cloudreve callback 兼容层。

### 5.7 分享

Cloudreve 前端需要：

- `PUT /api/v4/share`
- `GET /api/v4/share`
- `POST /api/v4/share/{id}`
- `DELETE /api/v4/share/{id}`
- `GET /api/v4/user/shares/{uid}`
- 分享页信息通过动态 URI 调用，通常落在 Cloudreve 的 share route。

当前 `my_site`：

- 有 target 分享：`/api/v2/shares/**`
- 有 legacy 分享：`/api/files/share-links/**`

缺口判断：

- 分享业务能力有，但 Cloudreve `Share` 模型更复杂，包含 `visited`、`downloaded`、`remain_downloads`、`show_readme`、`source_uri`、`unlocked` 等字段。
- 首期可适配基础创建、查看、删除、导入；访问计数、下载次数限制、readme 展示等属于缺失或待补齐能力。

### 5.8 工作流、任务、远程下载和全文索引

Cloudreve 前端需要：

- `GET /api/v4/workflow`
- `GET /api/v4/workflow/progress/{id}`
- `POST /api/v4/workflow/archive`
- `POST /api/v4/workflow/extract`
- `POST /api/v4/workflow/import`
- `POST /api/v4/workflow/download`
- `PATCH /api/v4/workflow/download/{id}`
- `DELETE /api/v4/workflow/download/{id}`
- `POST /api/v4/workflow/rebuildFtsIndex`

当前 `my_site`：

- 有 `/api/v2/tasks`
- 有 archive / extract / media-metadata task
- 有 transfer import，但不等同于 Cloudreve workflow import
- 没有 Cloudreve 远程下载任务
- 没有 Cloudreve workflow phase progress 协议
- 搜索有 `/api/v2/files/search`，但没有 Cloudreve `rebuildFtsIndex` 协议

缺口判断：

- 基础任务列表可以适配。
- 远程下载、阶段进度、全文索引重建是缺失能力。

### 5.9 WebDAV / 设备

Cloudreve 前端需要：

- `GET /api/v4/devices/dav`
- `PUT /api/v4/devices/dav`
- `PATCH /api/v4/devices/dav/{id}`
- `DELETE /api/v4/devices/dav/{id}`

当前 `my_site`：

- 没有 WebDAV 账号管理后端。

缺口判断：

- 完全缺失。
- 不建议为了 UI 适配先补，除非产品明确要做 WebDAV。

### 5.10 管理后台总览和设置

Cloudreve 前端需要：

- `GET /api/v4/admin/summary`
- `POST /api/v4/admin/settings`
- `PATCH /api/v4/admin/settings`

当前 `my_site`：

- 有 `GET /api/admin/summary`
- 有 `GET /api/admin/settings`
- 有 `PUT /api/admin/settings`
- 另有注册邀请码、离线传输限额等细分设置接口。

缺口判断：

- 有部分能力，但 Cloudreve 的 settings 是 key-value 批量读写模型，当前 `my_site` 是本项目自己的设置结构。
- 需要前端适配，不建议后端复制 Cloudreve setting key 体系。

### 5.11 管理后台用户、组、权限

Cloudreve 前端需要：

- `POST /api/v4/admin/user`
- `GET /api/v4/admin/user/{id}`
- `PUT /api/v4/admin/user`
- `PUT /api/v4/admin/user/{id}`
- `POST /api/v4/admin/user/batch/delete`
- `POST /api/v4/admin/user/{id}/calibrate`
- `POST /api/v4/admin/group`
- `GET /api/v4/admin/group/{id}`
- `PUT /api/v4/admin/group`
- `PUT /api/v4/admin/group/{id}`
- `DELETE /api/v4/admin/group/{id}`

当前 `my_site`：

- 有 `GET /api/admin/users`
- 有角色、状态、密码、容量、最大上传大小更新接口。
- 没有 Cloudreve group 管理模型。
- 没有 Cloudreve 批量删除和 storage calibrate 接口。

缺口判断：

- 用户管理部分可适配。
- 用户组、权限 bitset、组策略、校准存储统计是缺失能力。

### 5.12 管理后台存储策略、节点、实体

Cloudreve 前端需要：

- `POST /api/v4/admin/policy`
- `GET /api/v4/admin/policy/{id}`
- `PUT /api/v4/admin/policy`
- `PUT /api/v4/admin/policy/{id}`
- `DELETE /api/v4/admin/policy/{id}`
- `POST /api/v4/admin/policy/cors`
- `GET /api/v4/admin/policy/oauth/redirect`
- `GET /api/v4/admin/policy/oauth/status/{id}`
- `POST /api/v4/admin/policy/oauth/signin`
- `POST /api/v4/admin/policy/oauth/callback`
- `GET /api/v4/admin/policy/oauth/root/{id}`
- `POST /api/v4/admin/node`
- `GET /api/v4/admin/node/{id}`
- `PUT /api/v4/admin/node`
- `PUT /api/v4/admin/node/{id}`
- `DELETE /api/v4/admin/node/{id}`
- `POST /api/v4/admin/node/test`
- `POST /api/v4/admin/node/test/downloader`
- `POST /api/v4/admin/entity`
- `GET /api/v4/admin/entity/{id}`
- `GET /api/v4/admin/entity/url/{id}`
- `POST /api/v4/admin/entity/batch/delete`

当前 `my_site`：

- 有 `GET/POST/PUT/PATCH /api/admin/storage-policies/**`
- 有 `POST /api/admin/storage-policies/migrations`
- 有 `GET /api/admin/file-blobs`
- 没有 Cloudreve node / slave node / downloader node 模型。
- 没有 Cloudreve entity 管理模型。
- 没有 OneDrive OAuth policy flow。

缺口判断：

- 存储策略有部分能力，但模型不同。
- 节点、实体、策略 OAuth、策略 CORS 生成、节点测试能力基本缺失。

### 5.13 管理后台文件、分享、任务队列

Cloudreve 前端需要：

- `POST /api/v4/admin/file`
- `GET /api/v4/admin/file/{id}`
- `PUT /api/v4/admin/file`
- `PUT /api/v4/admin/file/{id}`
- `GET /api/v4/admin/file/url/{id}`
- `POST /api/v4/admin/file/batch/delete`
- `POST /api/v4/admin/share`
- `GET /api/v4/admin/share/{id}`
- `POST /api/v4/admin/share/batch/delete`
- `POST /api/v4/admin/queue`
- `GET /api/v4/admin/queue/{id}`
- `POST /api/v4/admin/queue/batch/delete`
- `GET /api/v4/admin/queue/metrics`
- `POST /api/v4/admin/queue/cleanup`

当前 `my_site`：

- 有 `GET /api/admin/files`
- 有 `DELETE /api/admin/files/{fileId}`
- 有 `GET /api/admin/shares`
- 有 `DELETE /api/admin/shares/{shareId}`
- 有 `GET /api/admin/tasks`
- 有 `GET /api/admin/tasks/{taskId}`

缺口判断：

- 列表和单个删除可适配一部分。
- Cloudreve 的 upsert file、batch delete、queue metrics、queue cleanup、任务批量删除当前缺失。

### 5.14 管理后台 OAuth Client 与工具接口

Cloudreve 前端需要：

- `GET /api/v4/session/oauth/app/{app_id}`
- `POST /api/v4/session/oauth/consent`
- `POST /api/v4/admin/oauthClient`
- `GET /api/v4/admin/oauthClient/{id}`
- `PUT /api/v4/admin/oauthClient`
- `PUT /api/v4/admin/oauthClient/{id}`
- `DELETE /api/v4/admin/oauthClient/{id}`
- `POST /api/v4/admin/oauthClient/batch/delete`
- `GET /api/v4/admin/tool/wopi`
- `POST /api/v4/admin/tool/thumbExecutable`
- `POST /api/v4/admin/tool/mail`
- `DELETE /api/v4/admin/tool/entityUrlCache`

当前 `my_site`：

- 没有 OAuth Client 管理。
- 没有 WOPI discovery。
- 没有缩略图生成器测试。
- 没有 SMTP 测试接口。
- 没有 Cloudreve entity URL cache。

缺口判断：

- 这些能力基本完全缺失。
- 除 SMTP 测试可能和系统设置有关，其余不应作为首期适配目标。

## 6. 推荐适配顺序

### 第一阶段：让 Cloudreve 前端能进入主界面

目标是不新增后端能力，只写前端适配。

需要处理：

- `GET /api/v4/site/config/basic`
- `GET /api/v4/site/config/login`
- `GET /manifest.json`
- `GET /api/v4/session/prepare`
- `POST /api/v4/session/token` -> 转到 `POST /api/auth/login`
- `POST /api/v4/session/token/refresh` -> 转到 `POST /api/auth/refresh`
- `DELETE /api/v4/session/token` -> 前端本地清会话即可
- `GET /api/v4/user/me` -> 转到 `GET /api/user/profile`

### 第二阶段：让文件列表和基础文件操作可用

需要适配：

- `GET /api/v4/file` -> `GET /api/files/list`
- `POST /api/v4/file/create` -> `POST /api/files/mkdir`
- `POST /api/v4/file/rename` -> `PATCH /api/files/{fileId}/rename`
- `POST /api/v4/file/move` -> `PATCH /api/files/{fileId}/move`
- `DELETE /api/v4/file` -> `DELETE /api/files/{fileId}`
- `POST /api/v4/file/restore` -> `POST /api/files/recycle-bin/{fileId}/restore`
- `POST /api/v4/file/url` -> `GET /api/files/download/{fileId}/url`

主要难点：

- Cloudreve 使用 `uri` 定位文件，当前后端核心操作多使用 `fileId`。
- 需要在前端适配层维护 `uri <-> fileId` 映射，或在后端补一个按 path/uri 查文件的轻量查询接口。

### 第三阶段：适配上传和分享

需要适配：

- Cloudreve `PUT /api/v4/file/upload` 到 `my_site` `/api/v2/files/upload-sessions`
- Cloudreve chunk upload 到 `POST /api/v2/files/upload-sessions/{sessionId}/content` 或当前实际上传模式
- Cloudreve `/share` 到 `my_site` `/api/v2/shares`

主要难点：

- Cloudreve 上传策略多，当前 `my_site` 不应照搬全部 policy callback。
- 分享字段差异较大，先支持基础创建、查看、导入、删除。

### 第四阶段：管理后台按本项目模型重做，不追求 Cloudreve 全兼容

Cloudreve 管理后台包含 user/group/policy/node/entity/queue/oauthClient/tool 等大量 Cloudreve 专属模型。

`my_site` 当前管理后台已有自己的领域边界，不建议为了复用 Cloudreve 前端照搬这些接口。更合理的做法是：

- 保留 Cloudreve 可复用的 UI 组件和交互。
- 管理后台页面按 `my_site` 的 `/api/admin/**` 重写数据层。
- 不实现 Cloudreve node、entity、OAuthClient、WOPI、WebDAV 等非目标能力，除非产品需求明确。

## 7. 不建议首期实现的 Cloudreve 专属能力

这些能力在 Cloudreve 前端中存在，但不应作为“连接我的后端 API”的首期目标：

- WebDAV 账号管理：`/api/v4/devices/dav/**`
- Passkey / WebAuthn：`/api/v4/user/authn`, `/api/v4/session/authn`
- OAuth Server / OAuth Client：`/api/v4/session/oauth/**`, `/api/v4/admin/oauthClient/**`
- Cloudreve node / slave node：`/api/v4/admin/node/**`
- Cloudreve entity 管理：`/api/v4/admin/entity/**`
- OneDrive policy OAuth：`/api/v4/admin/policy/oauth/**`
- WOPI discovery：`/api/v4/admin/tool/wopi`
- 缩略图生成器测试：`/api/v4/admin/tool/thumbExecutable`
- 远程下载：`/api/v4/workflow/download/**`
- Cloudreve 文件锁：`/api/v4/file/lock`
- Cloudreve 文件版本：`/api/v4/file/version/**`

这些会改变产品边界和后端模块责任，应该单独立需求评估。

## 8. 下一步执行建议

如果目标是“先把 Cloudreve 前端连到 `my_site` 后端看效果”，下一步不要直接改 proxy 后硬跑，而是新建一个 Cloudreve 前端适配层：

- 在 `third_party/cloudreve-frontend/src/api` 下新增或替换一层 `my-site-adapter`。
- 保持 Cloudreve UI 调用的 TypeScript 类型不大改，先把 `my_site` 返回值转换成 Cloudreve 组件需要的形状。
- 第一批只做站点配置、登录、刷新、当前用户、文件列表。
- 每完成一个阶段，用浏览器实际打开页面，记录还在 404 或结构不匹配的接口。

后端只在以下情况下补接口：

- 当前领域确实有业务能力缺口。
- 该能力符合 `backend-next/archtecture.md` 的模块边界。
- 不是为了复制 Cloudreve 专属模型而绕开 `my_site` 的已有 API。
