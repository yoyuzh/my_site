# 2026-04-09 前端重设计生成说明书

## 目标

当前前端已经同时承载登录、总览、网盘、回收站、快传、公开分享、游戏、管理台和 Android WebView 移动端壳。文件页的问题最明显：目录树、文件列表、搜索结果、选中文件详情、后台任务、上传入口、回收站入口、分享/移动/复制/重命名/删除等操作已经挤在同一层级；但如果只重做文件页，其他页面仍会在后续功能继续增加时出现同类拥挤和风格割裂。

本说明书用于指导下一轮全站前端重设计：保留现有业务能力和 API 调用，不改后端语义，重排桌面端、管理台、公开页和移动端的信息架构和组件边界，让页面能继续容纳搜索、事件刷新、后台任务、媒体信息、分享二期、后续压缩/解压任务等能力。

## 适用范围

- 桌面入口：`front/src/App.tsx`
- 移动端入口：`front/src/MobileApp.tsx`
- 桌面全局壳：`front/src/components/layout/Layout.tsx`
- 移动端全局壳：`front/src/mobile-components/MobileLayout.tsx`
- 桌面页面：
  - `front/src/pages/Login.tsx`
  - `front/src/pages/Overview.tsx`
  - `front/src/pages/Files.tsx`
  - `front/src/pages/RecycleBin.tsx`
  - `front/src/pages/Transfer.tsx`
  - `front/src/pages/TransferReceive.tsx`
  - `front/src/pages/FileShare.tsx`
  - `front/src/pages/Games.tsx`
  - `front/src/pages/GamePlayer.tsx`
- 移动端页面：
  - `front/src/mobile-pages/MobileLogin.tsx`
  - `front/src/mobile-pages/MobileOverview.tsx`
  - `front/src/mobile-pages/MobileFiles.tsx`
  - `front/src/mobile-pages/MobileTransfer.tsx`
  - `front/src/mobile-pages/MobileFileShare.tsx`
  - `front/src/mobile-pages/MobileGames.tsx`
  - `front/src/mobile-pages/GamePlayerMobile.tsx`
- 当前移动端复用桌面页：
  - `front/src/pages/RecycleBin.tsx`
- 管理台页面：
  - `front/src/admin/AdminApp.tsx`
  - `front/src/admin/dashboard.tsx`
  - `front/src/admin/users-list.tsx`
  - `front/src/admin/files-list.tsx`
  - `front/src/admin/storage-policies-list.tsx`
- 共享组件：`front/src/components/ui/*`
- 文件页共享逻辑：`front/src/pages/files-*.ts`
- 新增能力 helper：
  - `front/src/lib/file-search.ts`
  - `front/src/lib/file-events.ts`
  - `front/src/lib/background-tasks.ts`

不在本轮生成中改动：

- 后端接口和数据模型
- 认证与会话策略
- 上传底层队列语义
- 快传页面主流程的协议语义
- 管理台权限边界

## 全站页面清单

### 桌面端

- 登录：`/login`，文件 `front/src/pages/Login.tsx`
- 总览：`/overview`，文件 `front/src/pages/Overview.tsx`
- 网盘：`/files`，文件 `front/src/pages/Files.tsx`
- 回收站：`/recycle-bin`，文件 `front/src/pages/RecycleBin.tsx`
- 快传：`/transfer`，文件 `front/src/pages/Transfer.tsx`
- 公开快传兼容入口：`PUBLIC_TRANSFER_ROUTE` / `LEGACY_PUBLIC_TRANSFER_ROUTE`，文件 `front/src/pages/Transfer.tsx`
- 公开分享：`${FILE_SHARE_ROUTE_PREFIX}/:token`，文件 `front/src/pages/FileShare.tsx`
- 游戏列表：`/games`，文件 `front/src/pages/Games.tsx`
- 游戏播放器：`/games/:gameId`，文件 `front/src/pages/GamePlayer.tsx`
- 管理台：`/admin/*`，文件 `front/src/admin/AdminApp.tsx` 及 `front/src/admin/*`

### 移动端

- 登录：`/login`，文件 `front/src/mobile-pages/MobileLogin.tsx`
- 总览：`/overview`，文件 `front/src/mobile-pages/MobileOverview.tsx`
- 网盘：`/files`，文件 `front/src/mobile-pages/MobileFiles.tsx`
- 回收站：`/recycle-bin`，当前复用 `front/src/pages/RecycleBin.tsx`，后续应补移动专用或响应式版本
- 快传：`/transfer`，文件 `front/src/mobile-pages/MobileTransfer.tsx`
- 公开分享：`${FILE_SHARE_ROUTE_PREFIX}/:token`，文件 `front/src/mobile-pages/MobileFileShare.tsx`
- 游戏：当前 `MobileApp.tsx` 把 `/games` 和 `/games/:gameId` 重定向到 `/overview`；仓库已有 `front/src/mobile-pages/MobileGames.tsx` 和 `front/src/mobile-pages/GamePlayerMobile.tsx`，后续应决定是否重新接入
- 管理台：当前移动端 `/admin/*` 重定向到 `/overview` 或 `/login`；本轮可以只设计“不可用说明/跳转桌面端”的移动态，不要求完整移动管理台

## 产品定位

这是个人网盘和文件流转工作台，不是营销站。第一屏必须是可操作的文件工作区，不要生成 landing page、宣传 hero、介绍卡片或“功能说明”区域。

设计目标：

- 文件列表永远是主视觉中心。
- 低频功能进入抽屉、Popover、底部面板或二级 Tab，不抢主列表空间。
- 页面允许持续增加能力，但每次只让用户看到当前任务需要的操作。
- 桌面端优先信息密度，移动端优先单手操作和底部动作层。

## 设计原则

1. 主内容优先：桌面端中间 60% 以上宽度给文件列表或搜索结果。
2. 三层操作：常用操作常驻，次常用操作收进菜单，高级任务收进右侧抽屉。
3. 不要卡片套卡片：页面主体用面板分区，不在 `Card` 里再放一组 `Card`。
4. 不使用紫色或紫蓝渐变，不加离散渐变光球或 bokeh 背景。
5. 卡片和按钮圆角不超过 8px。当前 `rounded-2xl` / `rounded-xl` 可在本次重构中收敛成 `rounded-lg` 或 `rounded-md`。
6. 保留当前深色、玻璃感、蓝色主色方向，但降低装饰强度，让空间留给内容。
7. 所有长文件名、路径、任务错误都必须 `min-w-0` + `truncate` / `break-words`，不得撑破父容器。
8. 图标按钮必须有 `aria-label`，表单输入必须有 label 或 `aria-label`。
9. 大于 50 条的文件列表要预留虚拟列表或 `content-visibility: auto` 的实现位置。
10. 不用 JS 测量做基础布局，优先 CSS grid / flex。

## 桌面端文件页目标布局

桌面端采用“四区一抽屉”：

```text
┌──────────────────────────────────────────────────────────────────────┐
│ 全局 Layout 顶栏 / 侧边导航                                           │
├──────────────┬──────────────────────────────────────┬────────────────┤
│ 导航 Rail    │ 文件工作区                            │ 右侧 Inspector │
│ 目录树       │ Toolbar + Breadcrumb + Main List      │ 默认折叠       │
│ 回收站       │ 搜索结果或当前目录列表                 │ 详情/任务/分享 │
└──────────────┴──────────────────────────────────────┴────────────────┘
```

建议断点：

- `>= 1280px`：显示左侧目录 Rail，右侧 Inspector 可展开，宽度 320px。
- `1024px - 1279px`：显示左侧目录 Rail，右侧 Inspector 默认以覆盖抽屉打开。
- `768px - 1023px`：隐藏目录 Rail，使用顶部“目录”按钮打开侧栏抽屉。
- `< 768px`：走 `MobileFiles` 专用布局。

### 左侧目录 Rail

职责：

- 目录树
- 回收站入口
- 当前路径上下文

生成要求：

- 宽度固定在 220-260px，允许用户折叠成 56px icon rail。
- 目录树只显示目录，不显示文件。
- 树节点行高 32-36px，不使用大卡片样式。
- 回收站入口放在底部固定区，不挤占目录树滚动区。
- 折叠时只保留“文件”“回收站”“展开”图标。

### 文件工作区

职责：

- 当前目录文件列表
- 搜索结果列表
- 上传/新建/视图切换
- 面包屑
- 空状态与错误状态

结构：

1. 顶部 Toolbar，单行优先：
   - 左侧：面包屑，可横向滚动，最后一级可截断。
   - 中间：搜索框，宽度 280-360px，窄屏时进入第二行。
   - 右侧：上传、新建、视图切换、刷新。
2. 内容区：
   - 默认显示当前目录列表。
   - 搜索触发后显示“搜索结果模式”，顶部出现结果提示和清除搜索按钮。
   - 列表和网格共用同一组选中状态。
3. 底部状态条：
   - 显示选中数量、当前目录项数量、事件流连接状态。
   - 后台任务只显示一个“任务”按钮和未完成数量，不在底部铺开任务列表。

### 右侧 Inspector

职责：

- 选中文件详情
- 文件操作
- 分享设置
- 后台任务
- 媒体信息

生成要求：

- Inspector 默认折叠。用户选中文件后可以自动打开“详情”页签，但不要强制永久占位。
- Inspector 使用 Tab：
  - `详情`：名称、类型、大小、时间、路径。
  - `操作`：下载、分享、重命名、移动、复制、删除、提取媒体信息。
  - `任务`：最近 10 条后台任务，刷新/取消入口。
  - `媒体`：后续展示 `FileMetadata` 扩展信息；当前可显示“尚未提取”或“已创建提取任务”。
- 任务列表不要常驻占用主工作区；只在 Inspector 的 `任务` Tab 或右上角任务抽屉中出现。
- `删除` 用危险色并二次确认；不要放在主按钮位置。

## 桌面端其他页面目标布局

全站桌面端要统一为“应用壳 + 页面工作区”的产品形态。不要让每个页面各自发明背景、卡片、按钮和间距。

### 桌面全局 Layout

目标文件：

- `front/src/components/layout/Layout.tsx`

生成要求：

- 保留当前主导航：总览、网盘、快传、游戏、后台。
- 侧边导航宽度、账号菜单、上传进度面板要和新文件页的 Rail / Inspector 不冲突。
- 主内容区统一使用 `main` 语义标签，并预留 `min-w-0`。
- 全局上传进度面板继续存在，但不要遮挡文件页 Inspector、移动端 sheet 或管理台表格操作。
- 桌面端页面内容最大宽度由页面类型决定：工作台类全宽，表单类居中，公开页自适应。

### 登录页

目标文件：

- `front/src/pages/Login.tsx`

生成要求：

- 登录页可以保留独立视觉，但不要做营销落地页。
- 第一屏只做登录、注册入口、直达快传入口。
- 表单字段必须有 label、自动完成属性和清晰错误提示。
- 邀请码、密码策略、匿名快传入口不能被装饰内容挤到屏幕外。
- 移动端登录页要与 `MobileLogin.tsx` 同源设计，不要另起一套颜色。

### 总览页

目标文件：

- `front/src/pages/Overview.tsx`

生成要求：

- 总览页是“最近状态 + 快捷入口”，不是宣传页。
- 上方显示账号/存储/快传/Android APK 状态等核心摘要。
- 中间放最近文件、最近快传或最近任务的轻量列表；没有数据时显示可执行空状态。
- APK 下载入口保留，但不要做成大 hero。
- 快捷入口使用紧凑按钮组，避免大卡片占满第一屏。

### 回收站页

目标文件：

- `front/src/pages/RecycleBin.tsx`

生成要求：

- 桌面端复用文件列表视觉，重点显示原路径、删除时间、预计清理时间。
- 恢复和永久清理类操作必须二次确认。
- 从文件页进入时保持“回收站是文件系统的一个特殊区”的感知，不要像独立产品页。
- 当前移动端也复用该文件，后续要补响应式或移动专用版本，保证 390px 下表格不横向溢出。

### 快传页

目标文件：

- `front/src/pages/Transfer.tsx`
- `front/src/pages/TransferReceive.tsx`

生成要求：

- 保留在线快传、离线快传、我的离线快传记录和接收流程。
- 发送、接收、历史记录使用 Tab 或分段控件，不要同时铺满页面。
- 传输中的文件列表和连接状态优先展示；二维码、分享链接、取件码进入结果区或弹层。
- 匿名用户可用的能力和登录后能力要视觉区分，但不要用大段说明文案。
- WebRTC 连接状态、失败重试、TURN 未配置风险要有短提示，不要遮挡主要操作。

### 公开分享页

目标文件：

- `front/src/pages/FileShare.tsx`

生成要求：

- 公开分享页不使用主站登录态工作台布局，但视觉 token 要一致。
- 核心信息是分享名、文件列表、过期/权限状态、导入/下载操作。
- 后续分享二期要容纳密码输入、过期提示、次数限制、是否允许导入、是否允许下载。
- 密码输入态必须紧凑，错误提示靠近输入框。
- 未登录导入时，引导登录但不要阻断下载能力，具体取决于后端返回权限。

### 游戏页

目标文件：

- `front/src/pages/Games.tsx`
- `front/src/pages/GamePlayer.tsx`

生成要求：

- 游戏列表是应用内工具入口，不要做营销 hero。
- 游戏卡片只展示名称、状态、进入按钮和必要描述。
- 播放器页要让游戏 iframe / canvas 占据主体，不要被全局装饰挤压。
- 退出按钮、友情链接、播放器状态放在顶部紧凑工具条。
- 游戏体验页不强行复用文件页 Inspector。

### 管理台

目标文件：

- `front/src/admin/AdminApp.tsx`
- `front/src/admin/dashboard.tsx`
- `front/src/admin/users-list.tsx`
- `front/src/admin/files-list.tsx`
- `front/src/admin/storage-policies-list.tsx`

生成要求：

- 管理台可以更像数据后台，但颜色、字体、按钮和表格密度要和主站一致。
- Dashboard 只保留关键指标、请求趋势、最近 7 天上线记录和存储策略摘要。
- 用户列表、文件列表、存储策略列表统一表格密度、分页、空状态、错误状态。
- 表格列过多时优先使用列隐藏、详情抽屉或横向滚动容器，不要压缩到不可读。
- 管理台移动端当前不完整，先给 `/admin/*` 移动路由设计“请使用桌面端管理台”的可读状态，而不是硬跳转造成困惑。

## 移动端文件页目标布局

移动端采用“列表优先 + 底部动作层”：

```text
┌────────────────────────────┐
│ 顶部路径栏 + 搜索入口       │
├────────────────────────────┤
│ 文件列表                    │
│                            │
├────────────────────────────┤
│ 底部导航/任务入口/FAB        │
└────────────────────────────┘
```

生成要求：

- 顶部只保留返回、当前目录名、搜索按钮，不显示完整长面包屑。
- 搜索打开为全屏 sheet，结果列表复用文件行组件。
- 文件操作继续使用底部 action sheet，但分组：
  - 第一行：下载、分享、打开
  - 第二行：移动、复制、重命名
  - 危险区：删除
  - 高级区：提取媒体信息、任务详情
- FAB 只放上传文件、上传文件夹、新建文件夹。不要再扩展更多高级任务。
- 后台任务作为底部 sheet，从状态条或工具栏图标进入。
- 触控目标最小 44x44px。
- 移动端避免自动聚焦输入框，防止键盘遮挡。

## 移动端其他页面目标布局

移动端要以 `MobileLayout` 为统一底座，使用底部导航、顶部轻量标题栏和 sheet 交互。不要把桌面端三栏布局直接缩小。

### 移动端全局 Layout

目标文件：

- `front/src/mobile-components/MobileLayout.tsx`

生成要求：

- 底部导航只放一级入口：总览、网盘、快传、账号或更多。
- 顶部区域只显示当前页面必要标题和 1-2 个操作按钮。
- 所有 sheet、drawer、modal 要考虑 safe area bottom。
- 避免页面级横向滚动。

### 移动端登录页

目标文件：

- `front/src/mobile-pages/MobileLogin.tsx`

生成要求：

- 表单优先，登录/注册/直达快传在一屏内可达。
- 不自动聚焦输入框。
- 错误提示靠近字段或提交按钮。
- 邀请码输入、密码策略提示和匿名快传入口不能折叠到不可见位置。

### 移动端总览页

目标文件：

- `front/src/mobile-pages/MobileOverview.tsx`

生成要求：

- 用纵向摘要流：存储状态、最近文件、快传入口、Android 更新入口。
- 每个区块高度要克制，第一屏至少能看到第二个区块开头。
- APK 检查更新入口只在 Capacitor 壳内突出；Web 移动端保持普通下载入口。
- 最近项列表最多显示 3-5 条，其余进入对应页面。

### 移动端快传页

目标文件：

- `front/src/mobile-pages/MobileTransfer.tsx`

生成要求：

- 发送、接收、离线记录使用顶部分段控件或底部 sheet，不要三块同时展开。
- 文件选择、取件码、二维码、分享链接分阶段展示。
- 传输状态要固定在当前流程顶部或底部，不要随长列表滚走。
- 匿名用户可用能力必须清楚，但文案要短。

### 移动端公开分享页

目标文件：

- `front/src/mobile-pages/MobileFileShare.tsx`

生成要求：

- 文件名、分享状态、下载/导入按钮必须在 390px 宽度下可读。
- 密码输入、过期提示、次数限制提示用紧凑状态条。
- 文件列表长时使用单列行，不使用桌面表格。
- 下载和导入作为底部固定操作区或文件行内操作，不能同时堆满顶部。

### 移动端回收站

目标文件：

- 当前：`front/src/pages/RecycleBin.tsx`
- 建议新增：`front/src/mobile-pages/MobileRecycleBin.tsx`

生成要求：

- 若继续复用桌面 `RecycleBin`，必须保证小屏无横向溢出。
- 更推荐新增移动专用页：单列回收条目、底部确认 sheet、恢复按钮固定在条目操作区。
- 删除时间和清理时间可以折叠进次要信息，不抢文件名空间。

### 移动端游戏

目标文件：

- 当前仓库已有 `front/src/mobile-pages/MobileGames.tsx`
- 当前仓库已有 `front/src/mobile-pages/GamePlayerMobile.tsx`
- 当前路由尚未接入，`MobileApp.tsx` 会把 `/games` 与 `/games/:gameId` 重定向到 `/overview`

生成要求：

- 如果重新接入移动游戏页，先做可用的游戏列表和全屏播放器，不做复杂介绍页。
- 播放器页只保留退出、标题、必要链接；游戏区域必须占据主体。
- 如果本轮不接入，移动端总览可以显示“游戏请在桌面端打开”的轻提示，但不要出现死入口。

### 移动端管理台

当前状态：

- `MobileApp.tsx` 将 `/admin/*` 重定向到 `/overview` 或 `/login`

生成要求：

- 本轮不要求完整移动管理台。
- 若要改善体验，可以新增一个移动端不可用说明页：说明管理台需要桌面端，提供返回总览按钮。
- 不要把桌面管理台表格硬塞进移动端。

## 组件拆分要求

不要继续让 `Files.tsx` 膨胀，也不要在其他页面复制新的巨型组件。生成时先拆组件，再改布局。

建议文件：

- `front/src/components/layout/AppPageShell.tsx`
- `front/src/components/layout/PageToolbar.tsx`
- `front/src/components/layout/ResponsiveSheet.tsx`
- `front/src/components/ui/StatusBadge.tsx`
- `front/src/components/ui/EmptyState.tsx`
- `front/src/components/ui/ConfirmDialog.tsx`
- `front/src/pages/files/FilesPage.tsx`
- `front/src/pages/files/FilesToolbar.tsx`
- `front/src/pages/files/FilesDirectoryRail.tsx`
- `front/src/pages/files/FilesMainPane.tsx`
- `front/src/pages/files/FilesInspector.tsx`
- `front/src/pages/files/FilesTaskPanel.tsx`
- `front/src/pages/files/FilesSearchPanel.tsx`
- `front/src/pages/files/FileListView.tsx`
- `front/src/pages/files/FileGridView.tsx`
- `front/src/pages/files/FileActionMenu.tsx`
- `front/src/mobile-pages/files/MobileFilesPage.tsx`
- `front/src/mobile-pages/files/MobileFilesSearchSheet.tsx`
- `front/src/mobile-pages/files/MobileFilesTaskSheet.tsx`
- `front/src/mobile-pages/files/MobileFileActionSheet.tsx`
- `front/src/mobile-pages/MobileRecycleBin.tsx`
- `front/src/mobile-pages/MobileAdminUnavailable.tsx`
- `front/src/mobile-pages/games/MobileGamesPage.tsx`
- `front/src/mobile-pages/games/GamePlayerMobilePage.tsx`
- `front/src/pages/transfer/TransferPage.tsx`
- `front/src/pages/transfer/TransferModeTabs.tsx`
- `front/src/pages/share/FileSharePage.tsx`
- `front/src/admin/components/AdminTableShell.tsx`
- `front/src/admin/components/AdminMetricGrid.tsx`

兼容策略：

- `front/src/pages/Files.tsx` 可保留为 re-export 或薄入口。
- `front/src/mobile-pages/MobileFiles.tsx` 可保留为 re-export 或薄入口。
- 其他桌面页面和移动页面可逐步变成薄入口，不要求一次性完成所有页面拆分。
- 共享逻辑继续复用 `front/src/pages/files-upload.ts`、`files-state.ts`、`files-tree.ts`、`recycle-bin-state.ts`。

## 状态管理要求

把页面状态分成 5 类，不要全都堆在一个组件里：

- 路径状态：`currentPath`、目录缓存、展开目录。
- 列表状态：`currentFiles`、`viewMode`、`selectedFile`。
- 搜索状态：`searchQuery`、`searchResults`、`searchLoading`、`searchError`。
- 任务状态：`backgroundTasks`、`backgroundTasksLoading`、`backgroundTasksError`、任务 action id。
- 弹层状态：重命名、删除、移动/复制 picker、Inspector 展开状态、移动端 sheet。

推荐做法：

- 先抽 `useFilesDirectoryState()`。
- 再抽 `useFilesSearchState()`。
- 再抽 `useBackgroundTasksState()`。
- 上传队列继续使用当前 `files-upload-store`。
- SSE 订阅留在目录状态 hook 内，保证当前目录变更时自动重连。

## 行为要求

网盘必须保留：

- 当前目录加载与缓存。
- SSE 文件事件刷新当前目录。
- 搜索结果不污染目录缓存。
- 上传文件、上传文件夹、新建文件夹。
- 列表/网格切换。
- 文件夹双击进入。
- 文件下载、分享、重命名、移动、复制、删除。
- 回收站入口。
- 创建媒体信息提取任务。
- 最近后台任务查看与取消。

全站必须保留：

- 登录、注册、刷新登录态和退出登录。
- 总览页的核心摘要、Android APK 下载/检查更新入口。
- 快传在线发送、在线接收、离线发送、离线接收、我的离线快传记录。
- 公开快传路由兼容重定向。
- 公开分享详情、导入和后续密码校验入口。
- 游戏列表、桌面游戏播放器、退出游戏入口和友情链接。
- 管理台 dashboard、用户列表、文件列表、存储策略只读展示。
- 移动端登录、总览、网盘、快传、公开分享。
- 当前移动端游戏和管理台路由的既有重定向语义，除非本轮明确接入移动专用页面。

可以改变：

- 操作入口的位置。
- 详情面板呈现方式。
- 任务面板从页面常驻改为抽屉/Tab。
- 移动端 action sheet 的分组和按钮顺序。
- 桌面端目录树是否默认折叠。
- 页面内部组件拆分和视觉层级。
- 管理台表格的列密度和详情呈现方式。
- 移动端游戏入口是否接入，但必须在文档和路由中保持一致。

不能改变：

- 后端请求路径与认证方式。
- `apiRequest()` / `apiV2Request()` 的调用约定。
- 上传完成、复制、移动、删除后的缓存刷新语义。
- 匿名快传与登录网盘的权限边界。
- 管理员权限判断和受保护路由边界。
- 公开分享和公开快传的匿名访问边界。

## 视觉系统

使用当前项目已有基础：

- 字体：Inter + JetBrains Mono。
- 背景：`#07101D`。
- 主色：`#336EFF`。
- 文本：白色、slate 系辅助色。
- 面板：轻玻璃感，但减少 blur 和阴影层级。

新增建议 token：

- `--panel-bg: rgba(15, 23, 42, 0.72)`
- `--panel-border: rgba(148, 163, 184, 0.18)`
- `--panel-hover: rgba(148, 163, 184, 0.10)`
- `--danger: #F43F5E`
- `--success: #10B981`
- `--warning: #F59E0B`

圆角：

- 页面面板：8px。
- 按钮：6-8px。
- 表格行：0-6px。
- 移动端底部 sheet 顶部可用 16px，但卡片和按钮不要超过 8px。

布局密度：

- 桌面表格行高：44-52px。
- 桌面 toolbar 高度：48-56px。
- Inspector 操作按钮高度：36-40px。
- 移动端文件行高：60-72px。

## 可访问性与交互要求

- 图标按钮必须有 `aria-label`。
- 搜索输入必须有 `aria-label="搜索文件"` 或显式 label。
- 可点击行如果触发选择，行内更多操作必须 `stopPropagation()`。
- 目录树使用 `<button>`，不要用 `<div onClick>`。
- 键盘用户可以 Tab 到搜索、上传、新建、视图切换、文件行操作。
- 加载和错误提示使用 `aria-live="polite"`。
- 所有 Popover / Sheet / Dialog 关闭按钮必须可键盘触发。
- 动画只使用 `opacity` / `transform`，并尊重 `prefers-reduced-motion`。
- 不使用 `transition-all`。
- 不禁用浏览器缩放。

## 响应式验收标准

必须人工检查这些宽度：

- 390px：移动端，文件名超长、任务 sheet 打开、搜索 sheet 打开。
- 768px：平板窄屏，目录 Rail 不应挤压主列表。
- 1024px：Inspector 覆盖抽屉，不应让主列表变成窄列。
- 1280px：目录 Rail + 主列表 + Inspector 可以同时展示。
- 1440px：主列表占比仍然最高，不要出现大片空白。

内容压力测试：

- 文件名 120 个字符。
- 当前路径 8 层目录。
- 目录下 100 个文件。
- 后台任务 10 条，其中 3 条错误消息很长。
- 搜索结果为空、搜索失败、搜索中。
- SSE 断线重连中。
- 上传队列打开时再打开 Inspector。
- 快传同时存在 10 个文件、二维码、分享链接和错误提示。
- 公开分享页包含长分享名、密码输入、过期提示和多文件列表。
- 管理台用户表/文件表列很多时不压坏布局。
- 总览页在没有最近数据时显示可执行空状态。
- 游戏播放器在桌面和移动端都不被全局导航挤压。

页面覆盖检查：

- 桌面：登录、总览、网盘、回收站、快传、公开分享、游戏列表、游戏播放器、管理台 dashboard、用户列表、文件列表、存储策略列表。
- 移动：登录、总览、网盘、回收站、快传、公开分享，以及游戏/管理台当前重定向或不可用状态。

## 生成顺序

1. 做全站页面清单核对：桌面、移动、公开页、管理台都要列入验收。
2. 只做组件拆分，不改变视觉：先拆 `Files.tsx`，再拆快传、公开分享、管理台表格壳。
3. 抽状态 hook：目录、搜索、后台任务、弹层状态。
4. 替换桌面文件页布局：`FilesShell` + `DirectoryRail` + `MainPane` + `InspectorDrawer`。
5. 补桌面其他页面布局：登录、总览、回收站、快传、公开分享、游戏、管理台。
6. 替换移动文件页布局：搜索 sheet、任务 sheet、动作 sheet 分组。
7. 补移动其他页面布局：登录、总览、快传、公开分享、回收站、游戏/管理台状态页。
8. 收敛视觉 token：圆角、间距、阴影、面板样式。
9. 做响应式和长文本修复。
10. 最后跑测试和类型检查。

每一步都必须能单独通过 `npm run lint`，不要一次性大爆改。

## 验证命令

只使用仓库已有命令：

```powershell
cd front
npm run lint
npm run test
```

如修改了共享 API helper 或后端 DTO 才需要补跑：

```powershell
cd backend
mvn test
```

## 交付标准

- `front/src/pages/Files.tsx` 不再是超大组件。
- 桌面和移动端所有当前路由都有明确重设计方案，不只覆盖网盘页。
- 桌面端文件列表不会被详情和任务面板挤到不可用。
- 移动端新增能力都通过 sheet 进入，不堆在主列表上方。
- 搜索、任务、详情、操作四类能力有明确入口，不互相抢空间。
- 登录、总览、快传、公开分享、回收站、游戏、管理台的布局和视觉 token 与文件页一致。
- 移动端登录、总览、网盘、快传、公开分享、回收站都有小屏验收；游戏和管理台至少有明确路由状态。
- 长文本不撑破容器。
- 图标按钮、搜索输入、弹层关闭按钮符合基础可访问性要求。
- 既有文件能力无回归。

## 给实现代理的提示词

请基于 `docs/superpowers/plans/2026-04-09-frontend-redesign-generation-spec.md` 重构全站前端界面，不要只重构网盘页。先核对桌面、移动、公开页、管理台的路由清单，再拆分组件和状态 hook。保持现有 API 语义、缓存语义、上传语义、公开访问边界和管理权限边界不变。桌面端文件页实现目录 Rail + 主文件工作区 + 可折叠 Inspector；移动端文件页实现顶部路径栏 + 文件列表 + 搜索/任务/操作底部 sheet。登录、总览、回收站、快传、公开分享、游戏和管理台要使用同一套视觉 token 与响应式规则。移动端必须覆盖登录、总览、网盘、回收站、快传、公开分享，并明确游戏和管理台当前是接入还是不可用状态。不要做 landing page，不要新增紫色或紫蓝渐变，不要使用卡片套卡片。每个阶段完成后运行 `cd front && npm run lint`；最终运行 `cd front && npm run test`。如果只改前端，不要运行根目录 npm 命令。
