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

- `backend/src/main/java/com/yoyuzh/files/FileController.java`

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
- 2026-04-08 阶段 3 第一小步 API 补充：新增受保护的 v2 上传会话骨架接口，`POST /api/v2/files/upload-sessions` 创建会话，`GET /api/v2/files/upload-sessions/{sessionId}` 查询当前用户自己的会话，`DELETE /api/v2/files/upload-sessions/{sessionId}` 取消会话。当前响应只返回 `sessionId`、`objectKey`、路径、文件名、状态、分片大小、分片数量和时间字段；实际文件内容仍走旧上传链路，尚未开放 v2 分片上传/完成接口。
- 2026-04-08 阶段 3 第二小步 API 补充：新增 `POST /api/v2/files/upload-sessions/{sessionId}/complete`，用于把当前用户自己的上传会话提交完成。该接口当前不接收请求体，会复用会话里的 `objectKey/path/filename/contentType/size` 调用旧上传完成落库链路，成功后返回 `COMPLETED` 状态的 v2 会话响应；分片内容上传端点仍未开放。
- 2026-04-08 阶段 3 第三小步 API 补充：新增 `PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}`，请求体为 `{ "etag": "...", "size": 8388608 }`，用于记录当前用户上传会话的 part 元数据并返回 v2 会话响应。该接口会校验 part 范围和会话状态，当前只更新 `uploadedPartsJson`，不接收或合并真实文件分片内容。
- 2026-04-08 阶段 3 第四小步 API 补充：本小步没有新增对外 API。后端新增上传会话过期清理任务，只处理未完成且已过期的会话，并把它们标记为 `EXPIRED`；已完成会话和旧 `/api/files/**` 上传接口响应不变。
- 2026-04-08 阶段 4 第一小步 API 补充：本小步没有新增存储策略管理 API。v2 上传会话响应新增 `storagePolicyId`，用于标识该会话绑定的默认存储策略；当前该字段只服务后续 multipart/多策略迁移，旧 `/api/files/**` 上传下载接口响应不变。
