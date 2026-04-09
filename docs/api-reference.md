# API 接口文档

本文档用于快速了解 `yoyuzh.xyz` 当前后端 API 的职责、鉴权方式和主要接口分组。

## 1. 基本约定

### 基础路径

- 后端接口统一以 `/api` 开头
- 本地开发默认地址：`http://localhost:8080`

### 返回格式

大部分接口返回统一结构：

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

常见含义：

- `code = 0`：成功
- `code = 1000`：参数校验失败
- `code = 1001`：未登录
- `code = 1002`：权限不足
- `code = 1003`：业务对象不存在、邀请码错误、取件码失效等业务失败

### 鉴权方式

- 采用 `Authorization: Bearer <accessToken>`
- `refreshToken` 通过 `/api/auth/refresh` 换取新的登录态
- 当前实现为“按客户端类型拆分会话”
  - 桌面端与移动端可以同时在线
  - 同一端类型再次登录后，该端旧 access token 会失效
  - `/api/auth/register`、`/api/auth/login`、`/api/auth/refresh` 与开发环境 `/api/auth/dev-login` 支持可选请求头 `X-Yoyuzh-Client: desktop|mobile`

### 权限分层

- 公开接口：
  - `/api/auth/**`
  - `/api/transfer/**`
  - `GET /api/files/share-links/{token}`
- 登录后接口：
  - `/api/user/**`
  - `/api/files/**`
  - `/api/admin/**`

## 2. 认证模块

控制器：

- `backend/src/main/java/com/yoyuzh/auth/AuthController.java`
- `backend/src/main/java/com/yoyuzh/auth/DevAuthController.java`
- `backend/src/main/java/com/yoyuzh/auth/UserController.java`

### 2.1 注册

`POST /api/auth/register`

说明：

- 使用邀请码注册
- 注册成功后直接返回登录态
- 邀请码成功使用后会自动刷新
- 若请求未显式带 `X-Yoyuzh-Client`，后端默认按 `desktop` 处理

请求重点字段：

- `username`
- `email`
- `phoneNumber`
- `password`
- `confirmPassword`
- `inviteCode`

### 2.2 登录

`POST /api/auth/login`

请求字段：

- `username`
- `password`

返回字段：

- `token`
- `accessToken`
- `refreshToken`
- `user`

补充说明：

- 可选请求头 `X-Yoyuzh-Client` 用于声明当前登录来自桌面端还是移动端
- 同账号桌面端与移动端可同时保持登录，但同类型端再次登录会顶掉旧会话

### 2.3 刷新登录态

`POST /api/auth/refresh`

请求字段：

- `refreshToken`

说明：

- 刷新后会返回新的 access token 与 refresh token
- 当前系统会让旧 refresh token 失效
- 刷新会沿用该 refresh token 原本所属的客户端类型；请求头缺省时仍按 `desktop` 兜底

### 2.4 开发环境登录

`POST /api/auth/dev-login`

说明：

- 仅用于开发联调
- 是否可用取决于当前环境配置
- 同样支持可选请求头 `X-Yoyuzh-Client: desktop|mobile`

### 2.5 Android 客户端更新信息

`GET /api/app/android/latest`

说明：

- 公开接口，不需要登录
- 返回当前 Android 安装包下载地址、文件名和最新发布时间
- 后端会先读取文件桶中的 `android/releases/latest.json` 元数据，再返回当前 APK 对应的后端下载地址
- 安卓端原生壳应通过该接口检查更新

### 2.6 Android 客户端下载入口

`GET /api/app/android/download`

说明：

- 公开接口，不需要登录
- 该接口会直接回传当前最新 APK 的字节流，并通过 `Content-Disposition` 指定带版本号的文件名
- Web 端总览页应直接使用这个公开下载入口，而不是直接访问对象存储路径

### 2.7 获取用户资料

`GET /api/user/profile`

### 2.8 更新用户资料

`PUT /api/user/profile`

### 2.9 修改密码

`POST /api/user/password`

说明：

- 成功后会重新签发新的登录态
- 同时会顶掉旧设备会话

### 2.10 头像相关

- `POST /api/user/avatar/upload/initiate`
- `POST /api/user/avatar/upload`
- `POST /api/user/avatar/upload/complete`
- `GET /api/user/avatar/content`

说明：

- 支持初始化直传
- 支持代理上传
- 最终通过完成接口落库

## 3. 网盘模块

控制器：

- `backend/src/main/java/com/yoyuzh/files/core/FileController.java`

### 3.1 上传相关

- `POST /api/files/upload`
- `POST /api/files/upload/initiate`
- `POST /api/files/upload/complete`

说明：

- 兼容普通上传和 OSS 直传
- 前端会优先尝试“初始化上传 -> 直传/代理 -> 完成上传”
- `upload/initiate` 返回的 `storageName` 现在是一次上传对应的 opaque blob object key；新文件会落到全局 `blobs/...` key，而不是用户目录路径 key
- `upload/complete` 必须回传这个 opaque blob key，后端会据此创建 `FileBlob` 并把新 `StoredFile` 绑定到该 blob

### 3.2 目录与列表

- `POST /api/files/mkdir`
- `GET /api/files/list`
- `GET /api/files/recent`

说明：

- `list` 支持 `path`、`page`、`size`
- 当前前端会在网盘页缓存目录内容和最后访问路径

### 3.3 下载

- `GET /api/files/download/{fileId}`
- `GET /api/files/download/{fileId}/url`

说明：

- 普通文件优先获取下载 URL
- 文件夹可走 ZIP 下载
- 私有 `apk/ipa` 下载会返回一个短时有效的 `https://api.yoyuzh.xyz/_dl/...` URL；该 URL 由 Nginx 按签名和过期时间校验后代理到对象存储自定义下载域名，不是长期可复用的公开直链

### 3.4 文件操作

- `PATCH /api/files/{fileId}/rename`
- `PATCH /api/files/{fileId}/move`
- `POST /api/files/{fileId}/copy`
- `DELETE /api/files/{fileId}`
- `GET /api/files/recycle-bin`
- `POST /api/files/recycle-bin/{fileId}/restore`

说明：

- `move` 用于移动到目标路径
- `copy` 用于复制到目标路径
- 文件和文件夹都支持移动 / 复制
- 普通文件的 `move` / `rename` / `copy` 只改逻辑元数据；`copy` 会复用原有 `FileBlob`，不会复制底层对象
- `DELETE /api/files/{fileId}` 现在语义是“移入回收站”，不会立刻物理删除；删除的文件或整个目录树会保留 10 天
- `GET /api/files/recycle-bin` 返回当前用户回收站根条目分页列表，包含删除时间和预计清理时间
- `POST /api/files/recycle-bin/{fileId}/restore` 用于把某个回收站根条目恢复到原目录；若原位置已有同名文件，或当前剩余空间不足，则恢复失败

### 3.5 分享链接

- `POST /api/files/{fileId}/share-links`
- `GET /api/files/share-links/{token}`
- `POST /api/files/share-links/{token}/import`

说明：

- 已登录用户可为自己的文件或文件夹创建分享链接
- 公开访客可查看分享详情
- 登录用户可将分享内容导入自己的网盘
- 普通文件导入时会新建自己的 `StoredFile` 并复用源 `FileBlob`，不会再次写入物理文件

### 3.6 v2 上传会话

- `POST /api/v2/files/upload-sessions`
- `GET /api/v2/files/upload-sessions/{sessionId}`
- `DELETE /api/v2/files/upload-sessions/{sessionId}`
- `GET /api/v2/files/upload-sessions/{sessionId}/prepare`
- `POST /api/v2/files/upload-sessions/{sessionId}/content`
- `GET /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}/prepare`
- `PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}`
- `POST /api/v2/files/upload-sessions/{sessionId}/complete`

说明：

- 需要登录，只允许操作当前用户自己的上传会话
- 会话响应返回 `sessionId`、`objectKey`、`directUpload`、`multipartUpload`、`uploadMode`、`path`、`filename`、`contentType`、`size`、`storagePolicyId`、`status`、`chunkSize`、`chunkCount`、`expiresAt`、`createdAt`、`updatedAt`，以及一个面向前端消费的 `strategy` 对象
- `uploadMode` 目前有三种：`PROXY`、`DIRECT_SINGLE`、`DIRECT_MULTIPART`
- 默认 S3 存储策略下，创建会话时会立即初始化 multipart upload，并把 `directUpload=true`、`multipartUpload=true`、`uploadMode=DIRECT_MULTIPART` 返回给客户端；若默认策略 `directUpload=true` 但 `multipartUpload=false`，会返回 `DIRECT_SINGLE`；若 `directUpload=false`，则返回 `PROXY`
- `strategy` 会把当前会话下一步该调用的后端入口显式返回出来：`DIRECT_SINGLE` 返回 `prepareUrl` + `completeUrl`，`PROXY` 返回 `proxyContentUrl` + `proxyFormField=file` + `completeUrl`，`DIRECT_MULTIPART` 返回 `partPrepareUrlTemplate`、`partRecordUrlTemplate` 和 `completeUrl`
- `GET /{sessionId}/prepare` 仅用于 `DIRECT_SINGLE`，返回整文件直传所需的 `direct/uploadUrl/method/headers/storageName`
- `POST /{sessionId}/content` 仅用于 `PROXY`，以 multipart 表单上传整文件内容到当前 upload session 绑定的 `objectKey`
- `GET /parts/{partIndex}/prepare` 会返回当前分片的直传信息：`direct`、`uploadUrl`、`method`、`headers`、`storageName`
- `PUT /parts/{partIndex}` 请求体仍为 `{ "etag": "...", "size": 8388608 }`，只负责记录 part 元数据，不直接接收字节流
- `POST /complete` 会先按已记录的 part 元数据提交 multipart complete，再复用旧上传完成链路写入 `FileBlob + StoredFile + FileEntity.VERSION`
- 后端每小时清理过期且未完成的会话；若会话已绑定 multipart upload，会优先向对象存储发送 abort
- 当前前端网盘上传主链路已经消费这套 v2 接口：桌面/移动文件页和“存入网盘”入口都会按 `uploadMode + strategy` 自动选择代理上传、单请求直传或 multipart 分片上传

## 4. 快传模块

控制器：

- `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`

### 4.1 创建会话

`POST /api/transfer/sessions`

说明：

- 在线快传会话允许未登录用户创建
- 离线快传会话仍要求发送端登录
- 请求体必须区分 `mode`
  - `ONLINE`: 在线快传，15 分钟有效，只能被接收一次
  - `OFFLINE`: 离线快传，7 天有效，文件会落到站点存储并可被重复接收
- 返回会话 ID、取件码、模式、过期时间和文件清单

### 4.2 通过取件码查找会话

`GET /api/transfer/sessions/lookup?pickupCode=xxxxxx`

说明：

- 接收端通过 6 位取件码查找会话
- 在线快传和离线快传都允许未登录用户查找

### 4.3 加入会话

`POST /api/transfer/sessions/{sessionId}/join`

说明：

- 在线快传会占用一次性会话
- 离线快传返回可下载文件清单，不需要建立 P2P 通道
- 在线快传和离线快传都允许未登录用户加入

### 4.4 信令交换

- `POST /api/transfer/sessions/{sessionId}/signals`
- `GET /api/transfer/sessions/{sessionId}/signals`

说明：

- 后端负责 WebRTC 信令交换
- 文件内容本身不经过后端
- 实际文件通过浏览器 DataChannel 进行 P2P 传输
- 该组接口仅用于 `ONLINE` 模式

### 4.5 查看我的离线快传记录

`GET /api/transfer/sessions/offline/mine`

说明：

- 需要登录
- 返回当前用户未过期的离线快传会话列表
- 每个会话包含取件码、有效期和文件清单，前端可据此重新展示二维码与分享链接

### 4.6 上传离线快传文件

`POST /api/transfer/sessions/{sessionId}/files/{fileId}/content`

说明：

- 需要发送端登录
- 发送端把离线文件内容上传到站点存储
- 线上环境会把离线文件落到对象存储

### 4.6 下载离线快传文件

`GET /api/transfer/sessions/{sessionId}/files/{fileId}/download`

说明：

- 不需要登录
- 离线文件在有效期内可以被重复下载

### 4.7 存入网盘

`POST /api/transfer/sessions/{sessionId}/files/{fileId}/import`

说明：

- 需要登录
- 把离线快传文件导入到当前用户网盘

## 5. 管理台模块

控制器：

- `backend/src/main/java/com/yoyuzh/admin/AdminController.java`

### 5.1 总览

`GET /api/admin/summary`

返回内容包括：

- 用户总数
- 文件总数
- 当前邀请码
- 今日请求次数
- 今日按小时请求折线图
- 最近 7 天每日上线人数和用户名单
- 当前离线快传占用与上限

补充说明：

- `requestTimeline` 现在只返回当天已经过去的小时，例如当天只到 `07:xx` 时只会返回 `00:00` 到 `07:00`
- `dailyActiveUsers` 固定返回最近 7 天，按日期升序排列；每项包含日期、展示标签、当天去重后的上线人数和用户名列表
- “上线”定义为用户成功通过 JWT 鉴权访问受保护接口后的当天首次记录

### 5.2 用户管理

- `GET /api/admin/users`
- `PATCH /api/admin/users/{userId}/role`
- `PATCH /api/admin/users/{userId}/status`
- `PUT /api/admin/users/{userId}/password`
- `POST /api/admin/users/{userId}/password/reset`

说明：

- 可调整用户角色
- 可封禁用户
- 可重置或直接设置密码
- 封禁/改密会使原登录态失效

### 5.3 文件管理

- `GET /api/admin/files`
- `DELETE /api/admin/files/{fileId}`

### 5.4 存储策略

- `GET /api/admin/storage-policies`
- `POST /api/admin/storage-policies`
- `PUT /api/admin/storage-policies/{policyId}`
- `PATCH /api/admin/storage-policies/{policyId}/status`
- `POST /api/admin/storage-policies/migrations`

说明：

- 需要管理员登录
- 返回当前存储策略列表和结构化能力声明
- 新增/编辑接口当前允许维护名称、类型、bucket/endpoint/region、前缀、凭证模式、最大对象大小、能力声明与启用状态
- `PATCH /status` 用于启用或停用非默认策略；默认策略不能被停用
- `POST /migrations` 需要管理员登录，请求体为 `sourcePolicyId`、`targetPolicyId` 与可选 `correlationId`；当前会创建一个 `STORAGE_POLICY_MIGRATION` 后台任务，返回值沿用 `/api/v2/tasks/{id}` 的任务响应形状
- 当前迁移任务会在“当前活动存储后端”内执行真实对象迁移：复制旧对象到新的 target-policy object key，更新 `FileBlob` 与 `FileEntity.VERSION`，并在事务提交后清理旧对象；如果源/目标策略类型与当前运行时存储后端不匹配，任务会失败
- 当前仍不支持删除策略、切换默认策略或通过管理接口暴露实际凭证内容
- `capabilities.multipartUpload` 现在会反映默认策略是否支持 v2 上传会话 multipart；当前默认 S3 策略为 `true`，本地策略为 `false`

## 6. 前端公开路由与接口关系

前端入口在：

- `front/src/App.tsx`

主要页面：

- `/login`
- `/overview`
- `/files`
- `/transfer`
- `/share/:token`
- `/admin/*`

接口关系：

- 登录页：调用 `/api/auth/login`、`/api/auth/register`
- 网盘页：调用 `/api/files/**`
- 快传页：调用 `/api/transfer/**`
- 分享页：调用 `/api/files/share-links/{token}` 和导入接口
- 管理台：调用 `/api/admin/**`

## 7. 建议阅读顺序

后续新窗口如果要接手后端功能，建议按这个顺序看：

1. `memory.md`
2. `docs/architecture.md`
3. `docs/api-reference.md`
4. `AGENTS.md`
5. `CLAUDE.md`
6. `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`
7. 对应业务模块的 `Controller + Service`

补充说明：

- 根目录 `.env` 现在是本地密钥和部署参数的统一入口
- 额外的交接背景可查看 `docs/agents/handoff.md`
## 2026-04-08 API v2 阶段 1 补充

`GET /api/v2/site/ping`

说明：

- 公开接口，不需要登录。
- 当前是 v2 API 的最小边界探针，返回结构为 `{ "code": 0, "msg": "success", "data": { "status": "ok", "apiVersion": "v2" } }`。
- v2 错误响应开始使用独立 `ApiV2ErrorCode` 范围；旧 `/api/**` 接口暂不迁移。
- 前端访问 v2 接口时可通过 `apiV2Request()` 自动拼接 `/api/v2/**`，内部请求会携带 `X-Yoyuzh-Client-Id`。

## 2026-04-08 文件实体模型二期第一小步

- 本阶段只新增后端实体和迁移映射，不新增对外 API。
- 旧 `/api/files/**`、分享、回收站、快传接口继续使用现有 DTO 和响应结构。
- `StoredFile.primaryEntity` 与 `portal_stored_file_entity` 目前只作为兼容迁移数据，后续阶段稳定后再切换新读写路径。

## 2026-04-08 文件实体模型二期第二小步

- 本阶段不新增对外 API，`/api/files/**`、分享、回收站、快传导入等响应结构保持不变。
- 后端在旧接口内部开始双写实体模型：上传完成、外部导入、分享导入和网盘复制会继续写 `FileBlob`，同时创建或复用 `FileEntity.VERSION`，并写入 `StoredFile.primaryEntity` 与 `StoredFileEntity(PRIMARY)`。
- 下载、分享详情、回收站、ZIP 下载仍读取 `StoredFile.blob`；后续阶段稳定后再切换到 `primaryEntity` 读取。
- 2026-04-08 阶段 3 第一小步 API 补充：新增受保护的 v2 上传会话接口族，`POST /api/v2/files/upload-sessions` 创建会话，`GET /api/v2/files/upload-sessions/{sessionId}` 查询当前用户自己的会话，`DELETE /api/v2/files/upload-sessions/{sessionId}` 取消会话。当前响应会返回 `sessionId`、`objectKey`、`multipartUpload`、路径、文件名、状态、分片大小、分片数量和时间字段。
- 2026-04-08 阶段 3 第二小步 API 补充：`POST /api/v2/files/upload-sessions/{sessionId}/complete` 用于把当前用户自己的上传会话提交完成。当前默认 S3 策略下，该接口会先完成 multipart 合并，再复用旧上传完成链路落库，成功后返回 `COMPLETED` 状态的 v2 会话响应。
- 2026-04-08 阶段 3 第三小步 API 补充：`PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}` 请求体为 `{ "etag": "...", "size": 8388608 }`，用于记录当前用户上传会话的 part 元数据并返回 v2 会话响应；`GET /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}/prepare` 则返回该分片的直传地址和请求头。字节流仍直接上传到对象存储，不经过后端转发。
- 2026-04-08 阶段 3 第四小步 API 补充：本小步没有新增额外资源类型。后端新增上传会话过期清理任务，只处理未完成且已过期的会话，并把它们标记为 `EXPIRED`；若会话绑定了 multipart upload，还会在清理时发起 abort。
- 2026-04-08 阶段 4 第一小步 API 补充：本小步没有新增存储策略管理 API。v2 上传会话响应新增 `storagePolicyId`，用于标识该会话绑定的默认存储策略；该字段现在也用于区分会话是否应按策略能力走 multipart 上传。

## 2026-04-08 阶段 5 文件搜索第一小步

`GET /api/v2/files/search`

说明：

- 需要登录，且只返回当前用户自己的未删除文件或目录。
- 返回 v2 envelope，`data` 结构复用 `PageResponse<FileMetadataResponse>`：`items`、`total`、`page`、`size`。
- 支持查询参数：`name`、`type`、`sizeGte`、`sizeLte`、`createdGte`、`createdLte`、`updatedGte`、`updatedLte`、`page`、`size`。
- `type` 支持 `file`、`directory`、`folder`、`all`；时间参数使用 ISO 日期时间格式，例如 `2026-04-08T12:00:00`。
- 当前搜索只基于 `StoredFile` 固定字段，不启用标签或 metadata 条件过滤；旧 `/api/files/list` 与上传下载分享接口保持不变。

## 2026-04-08 阶段 5 文件搜索第二小步

- 前端通过 `front/src/lib/file-search.ts` 接入 `GET /api/v2/files/search`，该 helper 会拼接 `name`、`type`、`sizeGte/sizeLte`、`createdGte/createdLte`、`updatedGte/updatedLte`、`page`、`size`，并复用 `apiV2Request()` 的生产端点、认证与 client id 头。
- `front/src/pages/Files.tsx` 的桌面端文件页新增独立搜索视图，搜索结果不写入 `getFilesListCacheKey(...)`，清空搜索后回到当前目录列表；移动端搜索尚未接入。

## 2026-04-08 阶段 5 分享二期后端最小骨架

`POST /api/v2/shares`

需要登录。

- 为当前用户自己的非目录文件创建分享。
- 请求字段：`fileId`，以及可选的 `password`、`expiresAt`、`maxDownloads`、`allowImport`、`allowDownload`、`shareName`。
- 密码只保存 hash，不在响应中返回。

`GET /api/v2/shares/{token}`

公开访问。

- 返回分享摘要。
- 如果分享设置了密码，在校验前不返回 `file` 详情。

`POST /api/v2/shares/{token}/verify-password`

公开访问。

- 校验分享密码，成功后返回可读分享摘要。
- 响应永不返回 `passwordHash`。

`POST /api/v2/shares/{token}/import`

需要登录。

- 把分享文件导入当前用户网盘。
- 分享过期、密码错误或未提供、`allowImport=false`、`maxDownloads` 已耗尽时拒绝导入。

`GET /api/v2/shares/mine`

需要登录。

- 分页列出当前用户创建的分享。

`DELETE /api/v2/shares/{id}`

需要登录。

- 只删除当前用户自己的分享。

- 旧 `/api/files/share-links/**` 接口保留兼容；当前 `allowDownload` 已落库并返回，但还没有独立 v2 下载路由消费它。

## 2026-04-08 阶段 5 文件事件流最小闭环

`GET /api/v2/files/events?path=/`

说明：
- 需要登录，返回 `text/event-stream`
- 请求头支持 `X-Yoyuzh-Client-Id`
- 首次连接会先推送一个轻量 `READY` 事件
- 事件写入 `FileEvent` 表，字段包含 `userId`、`eventType`、`fileId`、`fromPath`、`toPath`、`clientId`、`payloadJson`、`createdAt`
- 当前后端已做同用户广播、路径前缀过滤和同 `clientId` 自身事件抑制
- 前端通过 `front/src/lib/file-events.ts` 以 fetch stream 订阅该 SSE，复用鉴权与 `X-Yoyuzh-Client-Id` 请求头；桌面 `Files` 与移动 `MobileFiles` 收到变更事件后会失效当前目录缓存并刷新当前目录列表

## 2026-04-08 阶段 6 任务框架与 worker 后端最小骨架

`GET /api/v2/tasks`

需要登录。分页列出当前用户自己的后台任务。

`GET /api/v2/tasks/{id}`

需要登录。只返回当前用户自己的任务详情。

`DELETE /api/v2/tasks/{id}`

需要登录。取消当前用户自己的任务，`QUEUED` / `RUNNING` 会转为 `CANCELLED` 并写入 `finishedAt`，终态任务保持原样。

`POST /api/v2/tasks/{id}/retry`

需要登录。仅允许当前用户重试自己处于 `FAILED` 的后台任务。

补充说明：

- 成功后任务状态会重置为 `QUEUED`
- `finishedAt` 与 `errorMessage` 会被清空
- `publicStateJson.phase` 会重置为 `queued`
- `publicStateJson.attemptCount` 会重置为 `0`
- 公开 state 会按服务端保存的 `privateStateJson` 重建，因此失败执行时写入的瞬时字段不会保留
- 非 `FAILED` 任务调用会返回 `400`

`POST /api/v2/tasks/archive`

需要登录。创建 `ARCHIVE` 类型的 `QUEUED` 任务；`fileId` 必须属于当前用户且未删除，`path` 必须匹配服务端派生逻辑路径，暂允许文件和目录；当前 worker 会生成 zip 并把归档结果回写到原文件同级目录。

`POST /api/v2/tasks/extract`

需要登录。创建 `EXTRACT` 类型的 `QUEUED` 任务；`fileId` 必须属于当前用户且未删除，`path` 必须匹配服务端派生逻辑路径，并拒绝目录和非压缩包类文件；当前 worker 只支持 zip-compatible 归档，会剥离共享根目录，并把解压结果恢复到原文件父目录。

`POST /api/v2/tasks/media-metadata`

需要登录。创建 `MEDIA_META` 类型的 `QUEUED` 任务；`fileId` 必须属于当前用户且未删除，`path` 必须匹配服务端派生逻辑路径，并拒绝目录和非媒体类文件；worker 会重新按 `userId + fileId` 加载文件，写入 `media:contentType`、`media:size`，对 ImageIO 可识别图片额外写 `media:width` 和 `media:height`。当前仍不做缩略图、视频时长或前端任务面板。

补充说明：

- worker 会定时领取少量 `QUEUED` 任务并切换为 `RUNNING`，完成后标记 `COMPLETED`，异常时标记 `FAILED` 并写入 `errorMessage`。
- `publicStateJson.phase` 当前会经历 `queued -> running -> archiving/extracting/extracting-metadata -> completed/failed/cancelled` 这样的最小阶段流转。
- `publicStateJson` 还会暴露 `attemptCount/maxAttempts`；当前默认预算为 `ARCHIVE=4`、`EXTRACT=3`、`MEDIA_META=2`。
- 任务进入 `RUNNING` 后，`publicStateJson` 会额外暴露 `workerOwner/heartbeatAt/leaseExpiresAt/startedAt`，用于描述当前 worker 的 lease 和 heartbeat；终态或重排回队列后会移除运行态 owner/lease 字段。
- `ARCHIVE/EXTRACT` 任务还会在 `publicStateJson` 里暴露 `processedFileCount/totalFileCount`、`processedDirectoryCount/totalDirectoryCount` 与真实 `progressPercent`；`MEDIA_META` 会额外暴露 `metadataStage`。
- 当 worker 命中失败时，任务会按失败分类写入 `failureCategory`。`TRANSIENT_INFRASTRUCTURE`、`RATE_LIMITED` 与部分 `UNKNOWN` 失败会按任务类型退避自动重排回 `QUEUED`，并在 `publicStateJson` 写入 `retryScheduled=true`、`nextRetryAt`、`retryDelaySeconds`、`lastFailureMessage`、`lastFailureAt`；`UNSUPPORTED_INPUT` 与 `DATA_STATE` 这类确定性失败不会自动重试。
- 已取消或其他终态任务不会被重新执行。
- 服务重启后，只有 lease 已过期或历史上没有 lease 的 `RUNNING` 任务会在启动完成时被重置回 `QUEUED`，避免多实例下误抢仍在运行的 worker。
- 创建成功后的任务 state 使用服务端文件信息，至少包含 `fileId`、`path`、`filename`、`directory`、`contentType`、`size`。
- 桌面端 `Files` 页面会拉取最近 10 条任务、提供 `QUEUED/RUNNING` 取消按钮，并可为当前选中文件创建 `MEDIA_META` 任务；移动端与 archive/extract 的前端入口暂未接入。
