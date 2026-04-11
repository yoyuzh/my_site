# Frontend Upgrade Modules Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` when implementing this plan. Keep checkbox state updated when modules land.

**Goal:** 为当前项目整理一份独立的前端升级计划书，明确为了配合现有 v2 能力闭环、运行时能力升级、媒体预览和后续 WebDAV/平台化演进，前端还需要新增或重构哪些模块。

**Repository:** `C:\Users\yoyuz\Documents\code\my_site`

## 1. Current Frontend Baseline

当前前端已经具备：

- 桌面端主路由：`/overview`、`/files`、`/tasks`、`/shares`、`/recycle-bin`、`/transfer`、`/admin/*`
- 移动端壳与底部导航
- 文件页基础搜索、上传、分享、删除、回收站入口
- 任务页和概览页的后台任务消费
- 分享页与分享列表页
- 管理台用户、文件、存储策略页面
- `front/src/lib/*` 下的 API helper 已基本分层

当前明显缺的前端模块不是“页面太少”，而是：

- 缺统一的客户端运行时状态层
- 缺缓存感知与失效感知
- 缺跨页面的上传状态中心
- 缺更完整的任务入口和任务消费组件
- 缺媒体预览组件体系
- 缺移动端对应能力补齐
- 缺面向多实例和运行时状态变化的前端协作层

## 2. Upgrade Stage Alignment

这份前端计划对应的是本轮总升级里的全部前端侧工作。与主升级计划的关系如下：

### Stage 1: Runtime Foundation

前端对应模块：

- Session And Auth Runtime
- Files Data And Directory Cache
- Upload Runtime
- Tasks Runtime
- Realtime Event Bridge
- Admin Operations Surface

前端目标：

- 正确消费 token 黑名单和登录态失效
- 感知热门目录缓存与失效
- 消费上传状态缓存
- 为异步任务和多实例事件刷新预留前端运行时

### Stage 2: Close Existing v2 Gaps

前端对应模块：

- Files Data And Directory Cache
- Upload Runtime
- Tasks Runtime
- Mobile Capability Parity

前端目标：

- 移动端搜索补齐
- archive/extract 前端入口补齐
- 移动端任务入口补齐
- 旧读取链路切换后的 UI 消费收口

### Stage 3: Thumbnail And Rich Media Pipeline

前端对应模块：

- Media Preview System
- Tasks Runtime
- Mobile Capability Parity

前端目标：

- 文件列表缩略图
- 详情侧栏 / 弹层预览
- 视频时长、poster、媒体属性展示
- 缩略图与媒体任务状态反馈

### Stage 4: Metadata, Labels, And Search Expansion

前端对应模块：

- Files Data And Directory Cache
- Media Preview System
- Mobile Capability Parity

前端目标：

- 高级搜索
- 标签与 metadata 筛选
- 搜索条件与目录视图解耦

### Stage 5: WebDAV Minimum Viable Support

前端对应模块：

- Session And Auth Runtime
- Admin Operations Surface

前端目标：

- 如后端采用应用密码 / 专用凭据，则前端需要提供管理入口
- 管理台提供 WebDAV / 外部客户端配置展示位

## 3. Frontend Module Map

建议把后续前端升级拆成 8 个模块域：

1. Session & Auth Runtime
2. Files Data & Directory Cache
3. Upload Runtime
4. Tasks Runtime
5. Realtime Event Bridge
6. Media Preview System
7. Admin Operations Surface
8. Mobile Capability Parity

## 4. Admin IA Alignment

参考成熟项目后台目录，当前前端管理台后续不应只保留：

- Dashboard
- Users
- Files
- Storage Policies

而应逐步演进为如下信息架构：

1. 面板首页
2. 参数设置
3. 文件系统
4. 存储策略
5. 节点
6. 用户组
7. 用户
8. 文件
9. 文件 Blob
10. 分享
11. 后台任务
12. 订单
13. 事件
14. 滥用举报
15. OAuth 应用

当前项目前端建议分三层推进：

### Layer A: 当前阶段必须补齐

- 面板首页
- 参数设置
- 文件系统
- 存储策略
- 用户
- 文件
- 文件 Blob
- 分享
- 后台任务

### Layer B: 预留导航位但可先只读或隐藏

- OAuth 应用
- 节点
- 用户组

### Layer C: 暂不实现

- 订单
- 事件独立中心
- 滥用举报

前端管理台路由最终建议朝这个结构靠拢：

- `/admin/dashboard`
- `/admin/settings`
- `/admin/filesystem`
- `/admin/storage-policies`
- `/admin/users`
- `/admin/files`
- `/admin/file-blobs`
- `/admin/shares`
- `/admin/tasks`
- `/admin/oauth-apps`

其中两个最重要的二级结构要先定下来：

### 参数设置页内部结构

建议采用顶部 tabs：

1. 站点信息
2. 用户会话
3. 验证码
4. 媒体处理
5. 增值服务
6. 邮件
7. 队列
8. 外观
9. 事件
10. 服务器

当前项目建议：

- 先做可编辑：
  - 站点信息
  - 用户会话
  - 媒体处理
  - 队列
  - 外观
- 先做只读：
  - 服务器
- 先预留标签但不做深实现：
  - 验证码
  - 邮件
  - 事件
- 当前不做：
  - 增值服务

### 文件系统页内部结构

建议采用顶部 tabs：

1. 参数设置
2. 全文搜索
3. 文件图标
4. 文件浏览应用
5. 自定义属性

当前项目建议：

- 先做：
  - 参数设置
  - 文件图标
  - 自定义属性
- 先做只读壳：
  - 文件浏览应用
- 当前延后：
  - 全文搜索

---

## 5. Module A: Session And Auth Runtime

**Goal:** 让前端能正确消费后端登录态策略升级后的失效语义，而不是继续把登录态仅看成本地 `portal-session`。

**Files likely involved:**

- `front/src/lib/api.ts`
- `front/src/lib/auth.ts` 或新增对应模块
- `front/src/App.tsx`
- `front/src/pages/Login.tsx`
- `front/src/components/layout/Layout.tsx`
- `front/src/mobile-components/MobileLayout.tsx`
- `front/src/hooks/*`

- [ ] **Step 1: 新增统一 session runtime 模块**
  - 负责 access token、refresh token、client id、client type、当前用户信息
  - 不让多个页面各自处理 401 / refresh / logout

- [ ] **Step 2: 补 token 撤销与强制失效感知**
  - 后端返回“token 已失效 / 已撤销 / 会话被顶掉”时统一退出
  - 区分普通 401 与“需要强制重新登录”的 401

- [ ] **Step 3: 新增全局 auth error handler**
  - 处理改密、管理员重置密码、封禁、同端挤下线
  - 避免文件页、任务页、分享页分别弹自己的错误

- [ ] **Step 4: 增加用户可见的会话状态提示组件**
  - 例如顶部 banner、toast 或 modal
  - 明确告诉用户“当前登录已失效，需要重新登录”

**Deliverables:**

- `session-runtime.ts`
- `use-session-runtime.ts`
- `AuthBoundary` 或同类组件
- 全局 auth failure UI

---

## 6. Module B: Files Data And Directory Cache

**Goal:** 让前端能真正利用后端“热门目录缓存”，同时保持目录缓存、搜索结果、回收站和任务列表的边界清晰。

**Files likely involved:**

- `front/src/pages/files/FilesPage.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/lib/files.ts`
- `front/src/lib/file-search.ts`
- `front/src/lib/file-events.ts`
- `front/src/lib/types.ts`
- 可新增 `front/src/lib/files-cache.ts`

- [ ] **Step 1: 抽出统一的 files query key / cache key 生成模块**
  - 覆盖 `path + page + size + view mode + sort`
  - 不和搜索结果共用

- [ ] **Step 2: 新增目录数据缓存协调层**
  - 负责首屏读取、命中缓存、后台刷新、失效
  - 与 SSE 事件和上传完成后的本地刷新统一

- [ ] **Step 3: 把搜索视图和目录视图彻底解耦**
  - 搜索结果永不写回目录缓存
  - 清空搜索后才能回到目录态

- [ ] **Step 4: 增加“缓存命中但后台刷新中”的 UI 状态**
  - 避免用户误以为界面卡死
  - 尤其适合热门目录

**Deliverables:**

- `files-cache.ts`
- `use-directory-data.ts`
- 目录刷新状态 UI

---

## 7. Module C: Upload Runtime

**Goal:** 为后端上传状态能力做前端消费层，把上传队列从“局部页面状态”升级成“全局上传运行时”。

**Files likely involved:**

- `front/src/lib/upload-session.ts`
- `front/src/pages/files/FilesPage.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/components/**/*`
- 可新增 `front/src/lib/upload-runtime.ts`
- 可新增 `front/src/components/upload/*`

- [ ] **Step 1: 新增全局 upload runtime store**
  - 管理上传项、会话 ID、进度、速度、错误、重试、取消
  - 桌面和移动共用

- [ ] **Step 2: 新增上传状态 polling / subscribe 模块**
  - 消费后端暴露的短生命周期上传状态
  - 保持与数据库最终状态分离

- [ ] **Step 3: 新增上传中心 UI**
  - 顶部悬浮面板或独立抽屉
  - 显示进行中、失败、完成

- [ ] **Step 4: 统一上传入口**
  - 文件页上传
  - “保存到网盘”
  - 后续 WebDAV 或外部导入触发后的状态展示

- [ ] **Step 5: 错误与恢复语义统一**
  - 会话过期
  - 分片失败
  - 容量不足
  - 锁冲突 / 重复提交

**Deliverables:**

- `upload-runtime.ts`
- `use-upload-runtime.ts`
- `UploadCenter`
- `UploadItemCard`

---

## 8. Module D: Tasks Runtime

**Goal:** 把当前任务页和概览页里分散的后台任务消费统一成一个任务运行时模块，并补齐 archive/extract 的前端入口。

**Files likely involved:**

- `front/src/lib/background-tasks.ts`
- `front/src/pages/Tasks.tsx`
- `front/src/pages/Overview.tsx`
- `front/src/pages/files/FilesPage.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- 可新增 `front/src/lib/task-runtime.ts`
- 可新增 `front/src/components/tasks/*`

- [ ] **Step 1: 抽出统一 task state parser**
  - 现在 `publicStateJson` 的解析不应只留在 `Tasks.tsx`

- [ ] **Step 2: 新增任务类型组件化展示**
  - `MEDIA_META`
  - `ARCHIVE`
  - `EXTRACT`
  - 后续 `THUMBNAIL`

- [ ] **Step 3: 为文件页增加任务快捷操作区**
  - 创建压缩
  - 创建解压
  - 提取媒体信息

- [ ] **Step 4: 为移动端增加最小任务能力**
  - 最近任务
  - 取消运行中任务
  - 查看失败原因

- [ ] **Step 5: 为异步任务 / 多实例任务更新预留刷新机制**
  - 支持轮询或事件驱动刷新

**Deliverables:**

- `task-runtime.ts`
- `TaskSummaryPanel`
- `TaskActionMenu`
- `TaskStatusBadge`

---

## 9. Module E: Realtime Event Bridge

**Goal:** 把当前 SSE 文件事件消费升级为真正的“前端实时桥接层”，适配跨实例事件分发后的实时语义。

**Files likely involved:**

- `front/src/lib/file-events.ts`
- `front/src/pages/files/FilesPage.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/pages/RecycleBin.tsx`
- 可新增 `front/src/lib/realtime-runtime.ts`

- [ ] **Step 1: 把文件事件订阅从页面内逻辑抽到 runtime**
  - 管理连接、重连、暂停、恢复

- [ ] **Step 2: 增加事件去重与防抖层**
  - 防止事件总线广播 + 本地更新重复触发刷新

- [ ] **Step 3: 让目录页、回收站、任务页可订阅不同事件域**
  - 文件目录变更
  - 回收站恢复/删除
  - 任务状态刷新

- [ ] **Step 4: 增加连接质量 UI**
  - 如“实时同步中 / 已断开，正在重连”

**Deliverables:**

- `realtime-runtime.ts`
- `use-realtime-status.ts`
- `RealtimeStatusChip`

---

## 10. Module F: Media Preview System

**Goal:** 为缩略图、poster、视频时长和 richer preview 提供统一前端消费层。

**Files likely involved:**

- `front/src/pages/files/FilesPage.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/pages/FileShare.tsx`
- `front/src/lib/files.ts`
- `front/src/lib/types.ts`
- 可新增 `front/src/components/media/*`

- [ ] **Step 1: 新增文件缩略图组件**
  - 图片缩略图
  - 视频封面
  - 加载中和失败 fallback

- [ ] **Step 2: 新增媒体元数据展示组件**
  - 时长
  - 尺寸
  - 基本编码信息

- [ ] **Step 3: 新增详情侧栏或预览弹层**
  - 桌面优先
  - 移动端先做简化版

- [ ] **Step 4: 为分享页补预览消费能力**
  - 不破坏密码保护和权限模型

**Deliverables:**

- `FileThumbnail`
- `MediaMetadataPanel`
- `FilePreviewDialog` 或同类组件

---

## 11. Module G: Admin Operations Surface

**Goal:** 让管理台能够消费运行时与平台化能力，而不只是停留在用户/文件/存储策略。

**Files likely involved:**

- `front/src/admin/*`
- `front/src/lib/admin*.ts`
- 可新增 `front/src/admin/runtime-dashboard.tsx`

- [ ] **Step 1: 重构管理台导航信息架构**
  - 从当前 4 个核心资源扩展为 dashboard / settings / filesystem / storage-policies / users / files / file-blobs / shares / tasks / oauth-apps
  - 移动端继续对 admin 路由做受限处理，不强行开放完整后台

- [ ] **Step 2: 新增参数设置页**
  - 展示和编辑当前允许后台维护的系统参数
  - 明确哪些配置只读、哪些可热更新
  - 使用 tabs 承载：站点信息 / 用户会话 / 验证码 / 媒体处理 / 增值服务 / 邮件 / 队列 / 外观 / 事件 / 服务器

- [ ] **Step 3: 新增文件系统页**
  - 展示上传模式、媒体处理、缓存、WebDAV、运行态能力
  - 使用 tabs 承载：参数设置 / 全文搜索 / 文件图标 / 文件浏览应用 / 自定义属性

- [ ] **Step 4: 新增文件 Blob 页**
  - 展示 Blob / Entity 列表、引用数、对象 key、策略、实体类型

- [ ] **Step 5: 新增分享管理页**
  - 展示分享状态、过期、下载数、查看数、密码保护状态

- [ ] **Step 6: 新增后台任务管理页**
  - 与用户侧任务页不同，这里展示全局任务池、失败分类、worker/lease 状态

- [ ] **Step 7: 新增 runtime 观测页或卡片**
  - 热门目录缓存命中
  - 队列积压
  - 强制失效 token 规模
  - 事件总线状态

- [ ] **Step 8: 新增任务运行态概览**
  - 缩略图、媒体处理、迁移任务分布

- [ ] **Step 9: 为后续 WebDAV / 外部客户端 / OAuth 应用预留管理入口**
  - 当前不必完整实现
  - 但前端信息架构要留位置和路由壳

**Deliverables:**

- `RuntimeDashboard`
- `QueueHealthCard`
- `CacheStatsCard`
- `AdminSettingsPage`
- `AdminSettingsSiteInfoTab`
- `AdminSettingsSessionsTab`
- `AdminSettingsMediaTab`
- `AdminSettingsQueueTab`
- `AdminSettingsAppearanceTab`
- `AdminSettingsServerTab`
- `AdminFilesystemPage`
- `AdminFilesystemSettingsTab`
- `AdminFilesystemIconsTab`
- `AdminFilesystemViewerAppsTab`
- `AdminFilesystemMetadataTab`
- `AdminFileBlobsPage`
- `AdminSharesPage`
- `AdminTasksPage`
- `AdminOAuthAppsPage`（可先空壳）

---

## 12. Module H: Mobile Capability Parity

**Goal:** 不让桌面端继续越走越远，所有新能力都要同步评估移动端最小实现。

**Files likely involved:**

- `front/src/mobile-pages/*`
- `front/src/mobile-components/*`
- `front/src/MobileApp.tsx`

- [ ] **Step 1: 移动端搜索能力补齐**
- [ ] **Step 2: 移动端任务面板补齐**
- [ ] **Step 3: 移动端上传中心补齐**
- [ ] **Step 4: 移动端文件预览最小化接入**
- [ ] **Step 5: 移动端实时状态提示补齐**

**Deliverables:**

- `MobileSearchBar`
- `MobileTaskDrawer`
- `MobileUploadCenter`
- `MobilePreviewSheet`

---

## 13. Recommended Execution Order

1. Module A: Session And Auth Runtime
2. Module B: Files Data And Directory Cache
3. Module C: Upload Runtime
4. Module D: Tasks Runtime
5. Module E: Realtime Event Bridge
6. Module H: Mobile Capability Parity
7. Module F: Media Preview System
8. Module G: Admin Operations Surface

## 14. Verification Rules

前端阶段验证只使用仓库已有命令：

- `cd front && npm run test`
- `cd front && npm run lint`
- `cd front && npm run build`

必要时补充手动验证：

- 登录失效与重新登录流程
- 热门目录首屏和失效刷新
- 上传中心状态变化
- 任务创建、取消、失败重试
- SSE/实时状态重连
- 移动端同能力检查

## 15. Documentation Follow-Up

当前端新增关键模块后，需要同步更新：

- `memory.md`
- `docs/architecture.md`
- `docs/api-reference.md`

至少记录：

- 新前端运行时模块名称和职责
- 与后端运行时能力的对应关系
- 是否桌面/移动双端都已接入
- 当前仍缺的 UI 闭环
