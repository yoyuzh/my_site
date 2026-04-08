# 2026-04-09 前端重设计生成说明书

## 目标

当前文件页已经同时承载目录树、文件列表、搜索结果、选中文件详情、后台任务、上传入口、回收站入口、分享/移动/复制/重命名/删除等操作。继续把新能力横向堆在一个页面里会导致桌面端宽度不足、移动端操作层级混乱。

本说明书用于指导下一轮前端重设计：保留现有业务能力和 API 调用，不改后端语义，重排信息架构和组件边界，让页面能继续容纳搜索、事件刷新、后台任务、媒体信息、分享二期、后续压缩/解压任务等能力。

## 适用范围

- 主入口：`front/src/pages/Files.tsx`
- 移动端入口：`front/src/mobile-pages/MobileFiles.tsx`
- 全局壳：`front/src/components/layout/Layout.tsx`
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
- 快传页面主流程
- 管理台独立路由

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

## 桌面端目标布局

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

## 移动端目标布局

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

## 组件拆分要求

不要继续让 `Files.tsx` 膨胀。生成时先拆组件，再改布局。

建议文件：

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

兼容策略：

- `front/src/pages/Files.tsx` 可保留为 re-export 或薄入口。
- `front/src/mobile-pages/MobileFiles.tsx` 可保留为 re-export 或薄入口。
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

必须保留：

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

可以改变：

- 操作入口的位置。
- 详情面板呈现方式。
- 任务面板从页面常驻改为抽屉/Tab。
- 移动端 action sheet 的分组和按钮顺序。
- 桌面端目录树是否默认折叠。

不能改变：

- 后端请求路径与认证方式。
- `apiRequest()` / `apiV2Request()` 的调用约定。
- 上传完成、复制、移动、删除后的缓存刷新语义。
- 匿名快传与登录网盘的权限边界。

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

## 生成顺序

1. 只做组件拆分，不改变视觉：把 `Files.tsx` 的目录树、列表、详情、任务面板拆出去。
2. 抽状态 hook：目录、搜索、后台任务、弹层状态。
3. 替换桌面布局：`FilesShell` + `DirectoryRail` + `MainPane` + `InspectorDrawer`。
4. 替换移动布局：搜索 sheet、任务 sheet、动作 sheet 分组。
5. 收敛视觉 token：圆角、间距、阴影、面板样式。
6. 做响应式和长文本修复。
7. 最后跑测试和类型检查。

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
- 桌面端文件列表不会被详情和任务面板挤到不可用。
- 移动端新增能力都通过 sheet 进入，不堆在主列表上方。
- 搜索、任务、详情、操作四类能力有明确入口，不互相抢空间。
- 长文本不撑破容器。
- 图标按钮、搜索输入、弹层关闭按钮符合基础可访问性要求。
- 既有文件能力无回归。

## 给实现代理的提示词

请基于 `docs/superpowers/plans/2026-04-09-frontend-redesign-generation-spec.md` 重构前端文件页。先拆分组件和状态 hook，再调整布局。保持现有 API 语义、缓存语义和上传语义不变。桌面端实现目录 Rail + 主文件工作区 + 可折叠 Inspector；移动端实现顶部路径栏 + 文件列表 + 搜索/任务/操作底部 sheet。不要做 landing page，不要新增紫色或紫蓝渐变，不要使用卡片套卡片。每个阶段完成后运行 `cd front && npm run lint`；最终运行 `cd front && npm run test`。如果只改前端，不要运行根目录 npm 命令。
