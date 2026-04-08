# 未完成工作交接

更新时间：2026-04-09

本文只记录当前还没有完全收口的工作，方便后续窗口快速接上。新会话仍必须先读 `memory.md`、`docs/architecture.md`、`docs/api-reference.md`，再读本文。

## 当前 Git 状态

- 当前分支：`dev`
- 已推送到：`gitea/dev`
- 最近已推送提交：`977eb60 feat(files): add v2 task and metadata workflows`
- 当前未提交文件：
  - `docs/superpowers/plans/2026-04-09-frontend-redesign-generation-spec.md`
- 当前未跟踪但不要默认处理：
  - `.claude/`

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

### P1：阶段 6 后台任务继续真实化

当前状态：

- `/api/v2/tasks/**` 后端骨架已落地
- worker 会领取 `QUEUED` 任务并切换状态
- `MEDIA_META` 已有最小真实 handler，会写入基础媒体 metadata 和图片宽高
- `ARCHIVE` / `EXTRACT` 仍是 no-op handler

后续未完成：

- 真实压缩任务
- 真实解压任务
- 任务重试策略
- 服务重启后的 `RUNNING` 任务恢复策略
- 更完整的任务进度字段
- 前端任务面板自动轮询或 SSE 化
- 移动端任务入口

### P1：分享二期继续收口

当前状态：

- v2 分享后端骨架已落地
- 支持密码、过期时间、导入开关、下载次数相关字段、我的分享、删除
- `allowDownload` 已落库并返回

后续未完成：

- 独立 v2 下载路由消费 `allowDownload`
- 前端分享二期设置界面
- 公开分享页的密码输入与过期/次数提示体验

### P2：上传会话二期继续推进

当前状态：

- v2 upload session 创建、查询、取消、complete、part metadata 和过期清理骨架已落地
- 当前只记录会话和 part metadata，不做真实对象存储 multipart

后续未完成：

- 在 `FileContentStorage` 抽象层补 multipart 能力语义
- S3 multipart 的 create/upload-part/complete/abort
- 过期清理从普通 `deleteBlob` 升级为可 abort 未完成 multipart
- 前端上传队列切到 v2 session

### P2：存储策略继续推进

当前状态：

- `StoragePolicy` 和能力声明骨架已落地
- 管理台只读展示已落地
- 物理实体可追踪 `storagePolicyId`

后续未完成：

- 管理台新增/编辑/停用策略
- 多策略迁移任务
- 按策略能力决定上传路径
- 真正启用 multipart 能力

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
