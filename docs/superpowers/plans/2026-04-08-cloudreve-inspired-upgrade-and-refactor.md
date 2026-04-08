# Cloudreve 对标后的项目升级与重构工程书

日期：2026-04-08  
对标对象：Cloudreve v4，官方仓库 `cloudreve/Cloudreve`，调研时 master 提交 `58b83ea`，GitHub 最新 Release 页面显示 `4.15.0` 发布于 2026-03-14。  
适用项目：`C:\Users\yoyuz\Documents\code\my_site`

## 1. 目标与边界

本工程书不是要求把本站改造成 Cloudreve，也不建议直接复制 Cloudreve 代码。Cloudreve 采用 GPL-3.0，本项目如无明确开源兼容计划，应只参考架构思想、接口边界和产品能力分层，在现有 Spring Boot + React + Vite + Capacitor 技术栈中重新实现。

本次对标的目标是回答三个问题：

1. 当前 `yoyuzh.xyz` 网盘/快传/管理后台已经具备哪些基础能力。
2. Cloudreve 在成熟文件管理系统中抽象了哪些关键工程能力。
3. 哪些升级或重构工程最值得做，按什么顺序做，如何验收。

## 2. Cloudreve 技术实现摘要

### 2.1 技术栈与仓库模块

Cloudreve 官方 README 显示其定位为自托管、多云文件管理系统，支持本地、远程节点、OneDrive、S3 兼容、七牛、OSS、COS、OBS、KS3、Upyun 等存储；支持客户端直传存储提供商、离线下载、压缩/解压/预览、WebDAV、并行可续传上传、媒体元数据、用户组、分享链接、在线预览/编辑、主题/PWA/i18n 等能力。官方 README 标注技术栈为 Go + Gin + ent，前端为 React + Redux + Material-UI。

源码结构上，Cloudreve 后端边界非常明确：

- `routers/`：统一路由，Cloudreve API 除少数特殊重定向外以 `/api/v4/` 开头。
- `routers/controllers/`：HTTP 参数绑定与控制器。
- `service/`：业务服务，按 `admin`、`explorer`、`share`、`user`、`node`、`oauth` 等拆分。
- `pkg/filemanager/`：核心文件系统抽象、上传会话、驱动、事件、锁、工作流。
- `pkg/filemanager/driver/`：本地、远程节点、S3、COS、OSS、OBS、OneDrive、七牛、Upyun 等驱动。
- `pkg/queue/`、`pkg/downloader/`、`pkg/cluster/`：异步任务、离线下载、主从节点。
- `pkg/webdav/`、`pkg/wopi/`：WebDAV 与 Office 在线协作协议适配。
- `ent/schema/` 与 `inventory/`：实体模型和数据访问层。

### 2.2 核心数据模型

Cloudreve 把“用户看到的文件”与“物理对象/版本实体”拆开：

- `File`：用户视角文件或目录，包含名称、类型、所有者、大小、父子关系、主实体、元数据、分享、外链等关系。
- `Entity`：物理对象实体，包含 source、size、storage policy、reference_count、upload_session_id、props；同一个文件可以有关联版本、缩略图、LivePhoto 等多个实体。
- `Metadata`：文件元数据，key-value 结构，key 约定为 `<Namespace>:<Key>`，用于标签、上传会话、回收站恢复 URI、缩略图禁用、自定义属性等。
- `StoragePolicy`：存储策略，包含类型、服务端、桶名、私有桶标记、AK/SK、容量上限、命名规则、策略设置、远程节点关系。
- `Task`：异步任务，状态包括 queued、processing、suspending、error、canceled、completed。
- `Share`：分享链接，包含密码、访问次数、下载次数、过期时间、剩余下载次数和 props。

这套模型比本站当前的 `StoredFile + FileBlob` 更完整。本站已经完成了“逻辑文件与物理 blob 分离”，但还缺少存储策略、实体版本、元数据、任务、事件和细粒度分享策略这些扩展点。

### 2.3 API 与协议边界

Cloudreve API 关键特征：

- 版本化：官方文档写明大部分 API 以 `/api/v4/` 开头。
- 统一响应：HTTP 状态基本固定为 `200`，业务错误在 `code`、`msg` 中表达，并有细分错误码，例如上传会话过期、非法分片索引、容量不足、文件类型不允许等。
- 鉴权：支持登录直接获取 AccessToken/RefreshToken，也支持 OAuth2/OIDC 风格授权码 + PKCE；API 使用 `Authorization: Bearer <AccessToken>`。
- Scope：源码中可见 `Files.Read`、`Files.Write`、`Shares.Read`、`Shares.Write`、`Workflow.Read`、`Workflow.Write`、`Admin.Read`、`Admin.Write` 等范围控制。
- 文件定位：使用统一 `File URI`，例如 `cloudreve://my`、`cloudreve://shared_with_me`、`cloudreve://trash`，并支持在 URI query 中表达文件名、类型、分类、元数据、大小、创建/更新时间等搜索条件。
- 上传：官方上传文档描述完整流程为创建上传会话、上传分片、完成上传。上传会话返回 `session_id`、`chunk_size`、`upload_urls`、`credential`、`expires`、存储策略、并发上限和加密参数。
- 文件事件：官方文档提供 `GET /api/v4/file/events`，基于 SSE 推送文件创建、修改、重命名、删除等事件，支持 `X-Cr-Client-Id` 用于过滤自己产生的事件和断线恢复。
- 搜索：官方文档将搜索分为内置搜索、全文/AI 混合搜索、浏览器快速搜索；内置搜索支持文件名与属性过滤，全文搜索需要额外索引和内容提取服务。
- 存储策略：官方对比表明确列出本地、远程节点、OSS、COS、七牛、Upyun、OBS、OneDrive、S3、KS3 在分块上传、原生缩略图、限速、私有直链、回调、CORS、内网 endpoint、友好文件名下载、并行分片上传等方面的差异。

## 3. 本站现状对照

### 3.1 已具备的基础能力

本站当前已具备：

- 后端：Spring Boot 3.3.8 + Java 17 + Maven，模块为 `auth`、`files`、`transfer`、`admin`、`config`、`common`。
- 前端：Vite 6 + React 19 + TypeScript + Tailwind CSS v4，桌面和移动壳并存，已支持 Capacitor Android。
- 账号：注册邀请码、JWT access token、refresh token、按 desktop/mobile 拆分会话。
- 网盘：上传、下载、目录列表、mkdir、最近文件、重命名、移动、复制、分享、导入、回收站软删除与恢复。
- 物理对象：`StoredFile` 指向 `FileBlob`，新上传采用全局 `blobs/...` key，复制/分享导入可复用 blob。
- 存储：本地和对象存储两种 `FileStorage` 实现，线上对象存储走多吉云临时密钥再访问底层 COS 兼容接口。
- 快传：在线 P2P 信令 + 离线快传落站点存储，支持未登录接收、登录后存入网盘。
- 管理后台：用户、文件、邀请码、今日请求折线、7 天上线记录、存储/流量/快传占用指标、离线快传总上限。

### 3.2 与 Cloudreve 的主要差距

当前差距不是“缺一个网盘 UI”，而是底层工程平台化程度不足：

- API 没有版本化，仍以 `/api/files`、`/api/transfer`、`/api/admin` 直接暴露；未来移动端、Web 端、桌面端兼容成本会升高。
- 存储抽象只有当前 active storage，没有数据库级 `StoragePolicy`；无法针对用户/文件/功能切换存储策略，也缺少策略能力声明。
- 上传接口是 `initiate -> direct/proxy -> complete`，但未形成可恢复、可并发、可取消、可超时清理、可审计的上传会话模型。
- `FileBlob` 缺少 reference_count、entity type、upload_session_id、storage_policy、props 等字段；当前只能表达“一个物理对象”，不能表达“版本、缩略图、LivePhoto、转码输出、临时实体”等。
- 文件元数据还没有独立表；标签、搜索属性、缩略图状态、回收站恢复信息、上传中状态都散落在固定字段或不存在。
- 搜索能力弱，当前主要靠列表和管理后台 query；缺少用户侧全局搜索、元数据过滤、类型/大小/时间过滤。
- 实时同步弱；前端依赖刷新/本地缓存，没有文件事件流，多个窗口/设备同时操作时体验不稳定。
- 分享链接功能较基础，缺少密码、过期时间、下载次数、浏览次数、只读/导入策略、短链与分享视图权限模型。
- 异步任务系统缺失；压缩、解压、离线导入、缩略图、媒体元数据、HLS 转码等目前没有统一的任务队列和状态查询。
- WebDAV、WOPI/Office 在线协作、桌面同步客户端协议能力未接入。
- 管理后台配置能力仍偏单点配置，没有完整的存储策略、队列、节点、OAuth 客户端、系统设置、任务列表。
- 后端模块边界仍较薄，`FileService` 容易继续膨胀成上帝服务。

## 4. 升级/重构工程总览

建议按“先补平台边界，再补用户能力，再补高级能力”的顺序推进。优先级如下：

| 优先级 | 工程 | 目标 | 风险 |
|---|---|---|---|
| P0 | API v2 与兼容网关 | 给后续移动端/桌面端演进留版本边界 | 需要维持现有前端不破 |
| P0 | 文件实体模型二期 | 从 `StoredFile + FileBlob` 扩展到可承载版本、元数据、策略 | 涉及数据库迁移 |
| P0 | 上传会话二期 | 支持分片、恢复、取消、过期清理、失败回滚 | 涉及前后端上传链路 |
| P1 | 存储策略与能力声明 | 支持本地/对象存储策略化与能力矩阵 | 配置与迁移要谨慎 |
| P1 | 元数据与搜索 | 支持标签、类型、大小、时间、属性搜索 | 索引设计影响性能 |
| P1 | 文件事件流 | 多窗口/多设备实时同步 | SSE 连接与鉴权需处理 |
| P1 | 分享能力二期 | 密码、过期、次数限制、浏览/下载计数 | 公共访问边界要严格 |
| P2 | 异步任务框架 | 压缩/解压/缩略图/媒体元数据/转码/批处理 | 后台任务失败恢复 |
| P2 | 预览与媒体管线 | 缩略图、音视频元数据、HLS 统一纳管 | CPU/存储成本 |
| P2 | WebDAV | 支持系统文件管理器和同步客户端生态 | 路径权限、锁、性能 |
| P3 | OAuth/OIDC 与 Scope | 第三方客户端和更细权限 | 认证模型复杂化 |
| P3 | 远程节点/下载节点 | 扩展多机和离线下载负载 | 暂不适合当前规模先做 |

## 5. 详细工程设计

### 5.1 工程 A：API v2 与错误码体系

目标：引入 `/api/v2`，保留当前 `/api` 直到前端完全迁移。新接口用于承载重构后的文件、上传、分享、任务、搜索和事件能力。

建议改动：

- 新建 `backend/src/main/java/com/yoyuzh/api/v2/...` 或在现有包下建立 `v2` controller。
- 定义 `ApiErrorCode`，细分参数错误、未登录、权限不足、容量不足、文件不存在、对象冲突、上传会话过期、非法分片、存储策略不可用、任务不存在等。
- 前端 `front/src/lib/api.ts` 增加 API version 选择能力，默认仍走现有 `/api`，新功能走 `/api/v2`。
- `docs/api-reference.md` 增加 v2 章节，但不要删除 v1 描述。

验收：

- 旧路径 `/api/files/list`、`/api/transfer/**` 不受影响。
- 新路径 `/api/v2/site/ping` 或 `/api/v2/files` 可被前端独立调用。
- `mvn test` 覆盖错误码映射。
- `cd front && npm run lint` 通过。

### 5.2 工程 B：文件实体模型二期

目标：保留现有 `StoredFile`，扩展物理对象为更通用的 `FileEntity`。短期不要一次性删除 `FileBlob`，先用迁移表兼容。

建议模型：

- `StoredFile`
  - 继续作为用户可见文件/目录。
  - 增加 `primaryEntityId`，逐步替代直接 `blob_id`。
  - 增加 `updatedAt`、`type` 或保留 `directory` 但在 DTO 层统一枚举。
- `FileEntity`
  - `id`
  - `objectKey`
  - `size`
  - `contentType`
  - `entityType`: `VERSION`、`THUMBNAIL`、`LIVE_PHOTO`、`TRANSCODE`、`AVATAR`
  - `referenceCount`
  - `storagePolicyId`
  - `uploadSessionId`
  - `createdBy`
  - `propsJson`
  - `createdAt`
- `StoredFileEntity`
  - 多对多关系表，用于一个文件挂多个实体，支持版本和派生对象。

迁移策略：

1. 新增表，不改旧业务。
2. 将现有 `portal_file_blob` 每行迁移为 `file_entity` 的 `VERSION`。
3. 将 `portal_file.blob_id` 映射成 `primary_entity_id`。
4. 新写入路径先双写 `FileBlob` 与 `FileEntity`，验证稳定后切换读取。
5. 最后移除 `FileBlob` 直连依赖，但可保留表作为历史兼容。

验收：

- 现有文件下载、复制、移动、分享导入、回收站恢复全部通过。
- `totalStorageBytes` 按物理实体去重统计，不按逻辑文件重复累计。
- 迁移脚本可重复运行且不会重复创建实体。

### 5.3 工程 C：上传会话二期

目标：把当前 `upload/initiate` 与 `upload/complete` 升级为可追踪的上传会话，支持分片、取消、恢复、超时清理和失败回滚。

建议新增：

- `UploadSession`
  - `sessionId`
  - `userId`
  - `targetPath`
  - `filename`
  - `size`
  - `contentType`
  - `storagePolicyId`
  - `objectKey`
  - `chunkSize`
  - `chunkCount`
  - `uploadedPartsJson`
  - `status`: `CREATED`、`UPLOADING`、`COMPLETING`、`COMPLETED`、`CANCELLED`、`EXPIRED`、`FAILED`
  - `expiresAt`
  - `createdAt`
  - `updatedAt`

建议 API：

- `POST /api/v2/files/upload-sessions`
- `PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}`
- `POST /api/v2/files/upload-sessions/{sessionId}/complete`
- `DELETE /api/v2/files/upload-sessions/{sessionId}`
- `GET /api/v2/files/upload-sessions/{sessionId}`

前端改动：

- `front/src/pages/files-upload.ts` 支持分片队列、并发数、暂停/取消、失败重试。
- `front/src/pages/files-upload-store.ts` 持久化 sessionId，刷新页面后可恢复任务状态。
- `UploadProgressPanel` 展示每个上传的状态、速度、已上传分片、失败原因。

验收：

- 大文件可分片上传。
- 上传中刷新页面后，前端能识别已完成分片并继续或明确失败。
- 取消上传后，底层 `blobs/...` 临时对象被清理。
- 会话过期清理任务只清理未完成会话，不影响已完成文件。

### 5.4 工程 D：存储策略与能力声明

目标：从“系统只有一个当前存储”升级到“数据库可管理多个存储策略”。短期只需要本地和当前多吉云/COS 兼容对象存储，不急于支持所有云厂商。

建议模型：

- `StoragePolicy`
  - `id`
  - `name`
  - `type`: `LOCAL`、`S3_COMPATIBLE`
  - `bucketName`
  - `endpoint`
  - `region`
  - `privateBucket`
  - `prefix`
  - `credentialMode`: `STATIC`、`DOGECLOUD_TEMP`
  - `maxSizeBytes`
  - `capabilitiesJson`
  - `enabled`
  - `createdAt`
  - `updatedAt`

建议能力声明：

- `directUpload`
- `multipartUpload`
- `signedDownloadUrl`
- `serverProxyDownload`
- `thumbnailNative`
- `friendlyDownloadName`
- `requiresCors`
- `supportsInternalEndpoint`
- `maxObjectSize`

管理后台：

- `front/src/admin/` 增加 Storage Policies 资源。
- 支持查看当前策略能力，不先做在线新增复杂云配置。

验收：

- 现有对象存储配置迁移为一条默认 `StoragePolicy`。
- 新上传文件写入 `storage_policy_id`。
- 管理后台可显示策略、启停、能力矩阵。
- 未启用策略不能被新上传选择，但旧文件仍可读取。

### 5.5 工程 E：文件元数据与搜索

目标：新增统一 `FileMetadata`，为标签、搜索、缩略图状态、上传状态、回收站恢复信息和后续媒体元数据提供扩展口。

建议模型：

- `FileMetadata`
  - `id`
  - `fileId`
  - `name`
  - `value`
  - `publicVisible`
  - `createdAt`
  - `updatedAt`
  - 唯一索引：`file_id + name`

建议 metadata 命名：

- `sys:upload_session_id`
- `sys:restore_path`
- `sys:expected_cleanup_time`
- `thumb:disabled`
- `tag:<tag_name>`
- `media:duration`
- `media:width`
- `media:height`
- `props:<key>`

建议搜索 API：

- `GET /api/v2/files/search?name=&type=&category=&sizeGte=&sizeLte=&createdGte=&createdLte=&updatedGte=&updatedLte=&meta.xxx=`

验收：

- 文件名搜索、类型过滤、大小过滤、时间过滤、标签过滤可组合。
- 回收站恢复信息逐步从 `StoredFile` 固定字段迁移到 metadata，但保留旧字段直到迁移完成。
- 前端当前目录快速搜索继续可用，新增服务端搜索入口。

### 5.6 工程 F：文件事件流

目标：实现类似 Cloudreve 的文件变更 SSE，让桌面端、移动端、多窗口能自动同步目录列表和回收站状态。

建议新增：

- `FileEvent`
  - `id`
  - `userId`
  - `eventType`: `CREATED`、`UPDATED`、`RENAMED`、`MOVED`、`DELETED`、`RESTORED`
  - `fileId`
  - `fromPath`
  - `toPath`
  - `clientId`
  - `payloadJson`
  - `createdAt`

建议 API：

- `GET /api/v2/files/events?path=/`
- 请求头：`X-Yoyuzh-Client-Id`
- 返回：`text/event-stream`

前端改动：

- `front/src/lib/file-events.ts` 管理 EventSource、重连、clientId。
- `Files.tsx`、`MobileFiles.tsx` 订阅当前目录事件，自己发起的事件用 clientId 过滤或降噪。
- 本地 cache 在事件到达时按 path 失效。

验收：

- 两个浏览器窗口打开同一路径，A 上传/重命名/删除后，B 自动刷新。
- 断线重连不导致无限刷新。
- 后端 SSE 不阻塞普通 API 请求。

### 5.7 工程 G：分享链接二期

目标：把分享从“基本公开链接”升级为可控访问策略。

建议模型：

- `FileShareLink`
  - 保留现有 token/file/user 关系。
  - 新增 `passwordHash`
  - 新增 `expiresAt`
  - 新增 `maxDownloads`
  - 新增 `downloadCount`
  - 新增 `viewCount`
  - 新增 `allowImport`
  - 新增 `allowDownload`
  - 新增 `shareName`

建议 API：

- `POST /api/v2/shares`
- `PATCH /api/v2/shares/{id}`
- `GET /api/v2/shares/{token}`
- `POST /api/v2/shares/{token}/verify-password`
- `POST /api/v2/shares/{token}/import`
- `GET /api/v2/shares/mine`
- `DELETE /api/v2/shares/{id}`

验收：

- 无密码公开分享仍兼容旧行为。
- 设置密码后，未验证不能查看文件列表和下载。
- 过期或下载次数耗尽后，下载/导入都失败。
- 管理后台可按用户/文件名检索分享。

### 5.8 工程 H：异步任务框架

目标：把耗时功能纳入统一任务框架，而不是在 Controller/Service 同步执行。

建议模型：

- `BackgroundTask`
  - `id`
  - `type`: `ARCHIVE`、`EXTRACT`、`THUMBNAIL`、`MEDIA_META`、`REMOTE_DOWNLOAD`、`HLS_TRANSCODE`、`CLEANUP`
  - `status`: `QUEUED`、`RUNNING`、`FAILED`、`CANCELLED`、`COMPLETED`
  - `userId`
  - `publicStateJson`
  - `privateStateJson`
  - `correlationId`
  - `createdAt`
  - `updatedAt`
  - `finishedAt`

建议 API：

- `GET /api/v2/tasks`
- `GET /api/v2/tasks/{id}`
- `DELETE /api/v2/tasks/{id}`
- `POST /api/v2/tasks/archive`
- `POST /api/v2/tasks/extract`
- `POST /api/v2/tasks/media-metadata`

验收：

- 管理后台展示队列长度、失败任务、最近任务。
- 用户可查看自己的任务进度。
- 任务失败能记录错误原因，不吞异常。

### 5.9 工程 I：缩略图、媒体元数据与 HLS 管线归档

本站现在 Nextcloud 侧已有独立 HLS 管线经验，但主站网盘还没有统一媒体管线。建议在任务框架后再做：

- 图片缩略图：生成 `THUMBNAIL` entity。
- 视频 poster：生成 `THUMBNAIL` entity。
- 视频 HLS：生成 `TRANSCODE` entity 或外部播放索引。
- 媒体元数据：写入 `FileMetadata`。

验收：

- 文件列表可显示图片/视频缩略图。
- 大视频不阻塞上传完成。
- 失败文件写入 `thumb:disabled` 或任务失败状态，避免前端无限重试。

### 5.10 工程 J：WebDAV

WebDAV 是很有价值但不应太早做的工程。必须等文件模型、权限、锁、事件和上传会话稳定之后再接入。

建议范围：

- 第一阶段只支持当前登录用户自己的 `/files`。
- 支持 `PROPFIND`、`GET`、`PUT`、`DELETE`、`MKCOL`、`MOVE`。
- 暂缓复杂共享挂载和跨用户管理员访问。

验收：

- macOS/Windows/WebDAV 客户端能列目录、上传小文件、下载文件、创建目录、删除文件。
- WebDAV PUT 复用上传会话和存储策略，不绕过容量/权限检查。

## 6. 推荐实施顺序

### 阶段 0：只做文档与技术基线

- 完成本文档。
- 补充 `docs/architecture.md` 中“未来 v2 文件平台”的占位说明，只有真正开始落地时再更新。
- 不改生产逻辑。

### 阶段 1：API v2 + 错误码 + 文件事件最小骨架

先做无业务破坏的边界：

- `/api/v2/site/ping`
- v2 `ApiResponse`
- v2 错误码枚举
- `X-Yoyuzh-Client-Id` 前端生成工具

验收命令：

- `cd backend && mvn test`
- `cd front && npm run lint`
- `cd front && npm run test`

### 阶段 2：文件实体模型迁移

先新增表和只读验证，再切换写路径：

- 新增 `FileEntity`、`StoredFileEntity`、`FileMetadata`。
- 写迁移测试，覆盖现有 `FileBlob`。
- 先让 DTO 返回保持不变。

验收命令：

- `cd backend && mvn test`
- 前端无需改动时不强制跑 build，但至少跑 `cd front && npm run lint`。

### 阶段 3：上传会话二期

- 后端会话表、分片状态、取消/过期清理。
- 前端上传队列改造。
- 对象存储直传先支持当前 S3/COS 兼容路径，不做多云。

验收命令：

- `cd backend && mvn test`
- `cd front && npm run lint`
- `cd front && npm run test`
- 手动验证 1 MB、50 MB、取消上传、刷新恢复、失败重试。

### 阶段 4：存储策略管理

- 默认策略迁移。
- 管理后台只读列表。
- 文件写入策略 ID。
- 能力矩阵展示。
- 2026-04-08 追加评估：不建议在阶段 3 末尾直接把 v2 上传会话接入真实对象存储 multipart。当前仓库的 `FileContentStorage` 只有单对象 PUT/校验/删除抽象，`UploadSession` 还没有 `multipartUploadId` 和 abort 语义；S3 multipart 未完成分片需要显式 abort，不能只靠 `deleteBlob(objectKey)` 清理。先完成本阶段的存储策略与能力声明，把 `multipartUpload`、`directUpload`、`signedDownloadUrl`、`requiresCors` 等能力落库，再按启用策略实现 S3 multipart。

### 阶段 5：搜索、分享、事件流

- 先服务端搜索。
- 再分享二期。
- 最后文件事件 SSE 接入前端自动刷新。

### 阶段 6：任务框架与媒体能力

- 后台任务表和 worker。
- 压缩/解压、缩略图、媒体元数据。
- HLS 只在资源成本可控后接入主站。

### 阶段 7：WebDAV 与第三方客户端

- WebDAV 最小读写。
- 再考虑 OAuth/OIDC Scope、桌面同步客户端。

## 7. 不建议现在做的事项

- 不建议直接支持 Cloudreve 的全部存储提供商。当前站点规模只需要本地 + 当前对象存储策略化。
- 不建议立即做远程节点。当前生产瓶颈和需求未证明需要多节点。
- 不建议立即做完整 WOPI/Office 在线协作。成本高、权限和锁复杂，应在 WebDAV 后评估。
- 不建议直接改掉所有旧 `/api` 路径。应先加 v2，再分批迁移。
- 不建议把快传和网盘完全合并成一个抽象。快传的在线 P2P 信令与网盘持久文件管理是不同业务，离线快传可以复用文件实体/上传会话，但在线 P2P 不应被强行塞入网盘模型。

## 8. 对应文件与预期改动范围

后端核心路径：

- `backend/src/main/java/com/yoyuzh/files/`
- `backend/src/main/java/com/yoyuzh/files/storage/`
- `backend/src/main/java/com/yoyuzh/transfer/`
- `backend/src/main/java/com/yoyuzh/admin/`
- `backend/src/main/java/com/yoyuzh/config/`
- `backend/src/test/java/com/yoyuzh/...`

前端核心路径：

- `front/src/lib/api.ts`
- `front/src/pages/Files.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/pages/files-upload.ts`
- `front/src/pages/files-upload-store.ts`
- `front/src/components/layout/UploadProgressPanel.tsx`
- `front/src/lib/netdisk-upload.ts`
- `front/src/lib/file-share.ts`
- `front/src/admin/`

文档路径：

- `docs/api-reference.md`
- `docs/architecture.md`
- `memory.md`：只有当实际落地重大架构变化时更新。

## 9. 参考资料

- Cloudreve GitHub README：https://github.com/cloudreve/Cloudreve
- Cloudreve API Introduction：https://docs.cloudreve.org/en/api/overview/
- Cloudreve Authentication：https://docs.cloudreve.org/en/api/auth/
- Cloudreve File URI：https://docs.cloudreve.org/en/api/file-uri/
- Cloudreve Metadata：https://docs.cloudreve.org/en/api/metadata/
- Cloudreve File Change Events：https://docs.cloudreve.org/en/api/events/
- Cloudreve File Upload：https://docs.cloudreve.org/en/api/upload/
- Cloudreve Storage Policy Comparison：https://docs.cloudreve.org/en/usage/storage/
- Cloudreve Search Types：https://docs.cloudreve.org/en/usage/search/
- Cloudreve 源码关键路径：`routers/router.go`、`pkg/filemanager/driver/handler.go`、`ent/schema/file.go`、`ent/schema/entity.go`、`ent/schema/metadata.go`、`ent/schema/policy.go`、`ent/schema/task.go`、`ent/schema/share.go`
