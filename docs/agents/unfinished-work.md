# 未完成工作交接

更新时间：2026-04-09

本文只记录当前还没有完全收口的工作，方便后续窗口快速接上。新会话仍必须先读 `memory.md`、`docs/architecture.md`、`docs/api-reference.md`，再读本文。

## 当前 files 后端结构

`backend/src/main/java/com/yoyuzh/files` 已完成一次只改结构、不改语义的包清理，当前按职责拆为：

- `com.yoyuzh.files.core`
- `com.yoyuzh.files.upload`
- `com.yoyuzh.files.share`
- `com.yoyuzh.files.search`
- `com.yoyuzh.files.events`
- `com.yoyuzh.files.tasks`
- `com.yoyuzh.files.storage`
- `com.yoyuzh.files.policy`

注意：

- 旧的 API 路径、数据库表/字段、响应结构、前端调用方式都没改
- 后续做 files 相关工作时，先按上面职责分区找类，不要再默认去平铺的 `com.yoyuzh.files.*` 根包下找
- `FileService` 仍然是当前 files 子系统最重的协调类，后续若继续做结构优化，应优先评估它和 `FileController` 是否值得继续按用例拆分

## 当前 Git 状态

- 当前分支：`dev`
- 已推送到：`gitea/dev`
- 最近已推送提交：`977eb60 feat(files): add v2 task and metadata workflows`
- 当前 worktree 不是干净状态：除本次后端 multipart / 后台任务相关文件外，还混有前端重构中的改动与若干新增文件；提交前先用 `git status` 逐项确认，不要批量回滚
- 当前未跟踪但不要默认处理：
  - `.claude/`
  - `docs/superpowers/plans/2026-04-09-multi-user-platform-upgrade-phase-2.md`
  - 前端重构过程中新增的 `front/src/components/ui/*`、`front/src/mobile-pages/*`、`front/src/pages/files/*` 等文件

## 已写好的前端重设计说明书

说明书位置：

- `docs/superpowers/plans/2026-04-09-frontend-redesign-generation-spec.md`

这份文档是下一步前端重构的主输入。它已经扩展为全站前端界面说明书，不只是网盘页。核心目标是解决文件页放不下新增能力的问题，同时统一桌面端、管理台、公开页和移动端的界面结构：

- 桌面端改为“目录 Rail + 主文件工作区 + 可折叠 Inspector”
- 移动端改为“顶部路径栏 + 文件列表 + 搜索/任务/操作底部 sheet”
- 桌面端覆盖登录、总览、网盘、回收站、快传、公开分享、游戏、游戏播放器、管理台
- 移动端覆盖登录、总览、网盘、回收站、快传、公开分享，并明确游戏和管理台当前路由状态
- 不改后端接口语义
- 不改上传、缓存、SSE、搜索和任务 helper 的业务语义
- 不做 landing page
- 不使用紫色或紫蓝渐变
- 不加离散渐变光球或 bokeh 背景
- 不做卡片套卡片
- 卡片和按钮圆角收敛到 8px 内

## 下一步优先级

### P0：先做全站前端界面重构，文件页优先

入口：

- `front/src/App.tsx`
- `front/src/MobileApp.tsx`
- `front/src/components/layout/Layout.tsx`
- `front/src/mobile-components/MobileLayout.tsx`
- `front/src/pages/Files.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/pages/Login.tsx`
- `front/src/pages/Overview.tsx`
- `front/src/pages/RecycleBin.tsx`
- `front/src/pages/Transfer.tsx`
- `front/src/pages/FileShare.tsx`
- `front/src/pages/Games.tsx`
- `front/src/pages/GamePlayer.tsx`
- `front/src/admin/*`
- `front/src/mobile-pages/*`

建议顺序：

1. 先核对桌面、移动、公开页、管理台路由清单。
2. 先拆文件页组件，不改视觉。
3. 抽状态 hook：目录、搜索、后台任务、弹层。
4. 桌面端文件页替换为目录 Rail + 主列表 + Inspector。
5. 桌面其他页面统一布局：登录、总览、回收站、快传、公开分享、游戏、管理台。
6. 移动端补搜索 sheet、任务 sheet、分组 action sheet。
7. 移动其他页面统一布局：登录、总览、快传、公开分享、回收站、游戏/管理台状态。
8. 最后统一圆角、间距、长文本截断和可访问性。

必须保留：

- 当前目录加载与缓存
- SSE 文件事件刷新当前目录
- 搜索结果不污染目录缓存
- 上传文件、上传文件夹、新建文件夹
- 列表/网格切换
- 文件夹双击进入
- 下载、分享、重命名、移动、复制、删除
- 回收站入口
- 创建媒体信息提取任务
- 最近后台任务查看与取消

验证命令：

```powershell
cd front
npm run lint
npm run test
```

不要在仓库根目录运行 `npm` 命令。

### P1：阶段 6 后台任务继续收口

当前状态：

- `/api/v2/tasks/**` 后端骨架已落地
- worker 会领取 `QUEUED` 任务并切换状态
- `MEDIA_META` 已有最小真实 handler，会写入基础媒体 metadata 和图片宽高
- `ARCHIVE` 已有真实 handler：会派生 `outputPath/outputFilename`，生成 zip 并回写同级目录
- `EXTRACT` 已有真实 handler：当前只支持 zip-compatible 归档，会派生 `outputPath/outputDirectoryName`，剥离共享根目录，并在批量导入失败时清理已写入 blob
- `/api/v2/tasks/**` 已有最小 progress 字段：`publicStateJson.phase` 会经历 `queued -> running -> archiving/extracting/extracting-metadata -> completed/failed/cancelled`
- `ARCHIVE/EXTRACT` 还会暴露真实计数字段：`processedFileCount/totalFileCount`、`processedDirectoryCount/totalDirectoryCount` 与真实 `progressPercent`
- 后台任务已有按任务类型区分的自动重试：`ARCHIVE` 默认最多 4 次、`EXTRACT` 最多 3 次、`MEDIA_META` 最多 2 次；失败会归类为 `UNSUPPORTED_INPUT/DATA_STATE/TRANSIENT_INFRASTRUCTURE/RATE_LIMITED/UNKNOWN`，自动重试时会写入 `retryScheduled/nextRetryAt/retryDelaySeconds/lastFailureMessage/lastFailureAt/failureCategory`
- `/api/v2/tasks/{id}/retry` 已支持最小手动重试：仅 `FAILED` 任务可由当前用户重置回 `QUEUED`，并清空 `finishedAt/errorMessage`
- worker 已有 heartbeat 与多实例 lease：运行中会暴露 `workerOwner/heartbeatAt/leaseExpiresAt/startedAt`，服务启动和调度前都只回收 lease 已过期的 `RUNNING` 任务
- `BackgroundTaskV2ControllerIntegrationTest` 已补 worker 驱动的 archive/extract 完成态与 extract 失败态端到端覆盖

后续未完成：

- 前端任务面板自动轮询或 SSE 化
- 移动端任务入口

### P1：分享二期继续收口

当前状态：

- v2 分享后端骨架已落地
- 支持密码、过期时间、导入开关、下载次数相关字段、我的分享、删除
- `allowDownload` 已接入 `GET /api/v2/shares/{token}?download=1`，会校验密码、过期、下载开关和下载次数并递增 `downloadCount`

后续未完成：

- 前端分享二期设置界面
- 公开分享页的密码输入与过期/次数提示体验

### P2：上传会话二期继续推进

当前状态：

- v2 upload session 后端已补齐创建、查询、取消、prepare-part、record-part、complete 和过期清理
- `FileContentStorage` 已新增 multipart 抽象；`S3FileContentStorage` 已实现 create/upload-part/complete/abort
- 默认 S3 存储策略现在会声明 `multipartUpload=true`；创建会话时会生成 `multipartUploadId`，v2 响应会返回 `multipartUpload`
- v2 会话现在还会返回 `directUpload` 与 `uploadMode=PROXY|DIRECT_SINGLE|DIRECT_MULTIPART`
- v2 会话响应还会返回 `strategy`，显式给出当前模式应该走的 `prepareUrl/proxyContentUrl/partPrepareUrlTemplate/partRecordUrlTemplate/completeUrl`
- `GET /api/v2/files/upload-sessions/{sessionId}/prepare` 已支持 `DIRECT_SINGLE` 模式下的整文件直传
- `POST /api/v2/files/upload-sessions/{sessionId}/content` 已支持 `PROXY` 模式下的整文件代理上传
- `GET /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}/prepare` 已可返回单分片直传地址；`POST /complete` 会先提交 multipart complete，再复用旧 `FileService.completeUpload()` 落库
- 过期清理已从普通 `deleteBlob` 升级为对未完成 multipart 执行 abort
- 旧 `/api/files/upload/initiate` 现在也会尊重默认策略 `directUpload/maxObjectSize`
- 桌面端 `FilesPage`、移动端 `MobileFilesPage` 和 `saveFileToNetdisk()` 已切到 v2 upload session，并按 `uploadMode + strategy` 自动选择 `PROXY / DIRECT_SINGLE / DIRECT_MULTIPART`
- 前端现已接上 `create/get/cancel/prepare/content/part-prepare/part-record/complete` 全套 v2 upload session helper

后续未完成：

- 头像上传等非 files 子系统上传入口仍在走旧 `/api/**` 上传链路

### P2：存储策略继续推进

当前状态：

- `StoragePolicy` 和能力声明骨架已落地
- 管理台查看、新增、编辑、启停非默认策略的后端接口已落地
- `POST /api/admin/storage-policies/migrations` 已可创建 `STORAGE_POLICY_MIGRATION` 后台任务
- `STORAGE_POLICY_MIGRATION` 现在会在当前活动存储后端内真实复制对象数据到新的 `policies/{targetPolicyId}/blobs/...` key，并更新 `FileBlob` 与 `FileEntity.VERSION`
- 迁移任务会暴露 `migrationStage`、`processedEntityCount/totalEntityCount`、`processedStoredFileCount/totalStoredFileCount`、`migratedEntityCount`、`migratedStoredFileCount` 和真实 `progressPercent`
- 如果迁移过程中失败，handler 会删除本轮新写对象，并依赖事务回滚元数据；旧对象清理则在成功提交后 best-effort 执行
- 物理实体可追踪 `storagePolicyId`

后续未完成：

- 跨不同运行时后端类型的真正 provider 级迁移
- 继续把按策略能力的上传体验外扩到其他非 files 子系统上传入口

## 当前本地运行状态

最近一次本地查看时：

- 前端：`http://127.0.0.1:3000`
- 后端 Swagger：`http://127.0.0.1:8080/swagger-ui.html`
- 后端使用本地内存 H2 启动，重启后数据会清空

如果后续会话接手时进程已经不存在，使用现有脚本：

```powershell
.\scripts\start-backend-dev.ps1
.\scripts\start-frontend-dev.ps1
```

## 已知注意事项

- `.claude/` 是未跟踪目录，不要默认提交。
- `front/node_modules` 已通过 `cd front && npm install` 补齐过缺失依赖，但不要提交 `node_modules`。
- 前端类型检查命令就是 `npm run lint`，没有单独的 `typecheck` 脚本。
- 后端没有独立 lint 命令，常规验证用 `cd backend && mvn test`。
- 如果只做前端布局重构，一般不需要跑后端测试。
- 如果改了 API helper、DTO 对齐或后端语义，再跑 `cd backend && mvn test`。
