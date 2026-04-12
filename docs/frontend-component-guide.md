# 前端组件选型与替换指南

## 1. 文档目标

这份文档用于回答两个问题：

1. 当前 `front/` 已经有哪些前端基础组件与页面壳
2. 哪些“不完善但不承载业务规则”的前端组件适合用成熟开源组件替换

本文只讨论前端组件、交互容器、表现层能力与替换边界，不改变 `docs/architecture.md` 里已经确定的领域边界。

---

## 2. 组件替换原则

### 2.1 可以放心引入开源组件的范围

- 纯展示层组件
- 纯交互壳组件
- 表格、图表、树、分栏、拖拽、上传入口、媒体播放器 UI
- 不负责权限判断、不负责业务规则决策的基础组件

### 2.2 不应交给开源组件决定的范围

- 权限与身份判断
- 上传模式选择：`PROXY / DIRECT_SINGLE / DIRECT_MULTIPART`
- 分享是否可访问、可下载、可导入
- 工作空间路径合法性、同级重名规则、回收站恢复规则
- 任务是否可重试、是否可取消、状态机如何流转

结论：

- 开源组件负责“怎么展示、怎么交互”
- 业务代码负责“能不能做、规则是什么、最终状态如何判定”

---

## 3. 当前项目已有的核心基础组件

### 3.1 应用外壳

- `front/src/components/layout/Layout.tsx`
  - 桌面端主壳
  - 包含左侧导航、主题切换、任务摘要、退出按钮、`UploadCenter`
- `front/src/mobile-components/MobileLayout.tsx`
  - 移动端主壳
  - 包含顶部浮层、底部导航、主题切换、任务摘要、退出按钮、`UploadCenter`
- `front/src/admin/AdminLayout.tsx`
  - 管理台二级导航壳
  - 负责后台子页面切换，不承载业务规则

### 3.2 全局状态与共用组件

- `front/src/components/ThemeProvider.tsx`
  - 亮暗主题上下文
- `front/src/components/ThemeToggle.tsx`
  - 主题切换按钮
- `front/src/components/upload/UploadCenter.tsx`
  - 全局上传队列浮层
  - 展示任务数量、展开/收起、清理已完成任务
- `front/src/components/tasks/TaskSummaryPanel.tsx`
  - 导航栏里的活动任务摘要
- `front/src/components/media/FileThumbnail.tsx`
  - 文件缩略图/类型图标组件

### 3.3 现有组件短板

- 表格几乎全是页面内手写 `<table>`
- 文件树和文件详情面板仍是页面级拼装
- 上传入口是“按钮 + 隐藏 input”模式，拖拽能力弱
- 快传页面没有二维码、批量选择、可视化发送步骤
- 媒体预览/播放器能力基本空缺
- 管理台多个页面仍是占位页

---

## 4. 推荐引入的开源组件

以下推荐按“适合当前项目”优先，而不是单纯按星标排序。

### 4.1 `@tanstack/react-table`

- GitHub: <https://github.com/TanStack/table>
- 适合替换：
- `front/src/admin/users-list.tsx`
- `front/src/admin/files-list.tsx`
- `front/src/admin/fileblobs.tsx`
- `front/src/admin/shares.tsx`
- `front/src/admin/tasks.tsx`
- `front/src/pages/Shares.tsx`
- `front/src/pages/Tasks.tsx`
- 后续审计日志页
- 后续管理台所有表格页
- 适配原因：
  - Headless，不会破坏当前玻璃拟态视觉
  - 支持排序、筛选、分页、列配置、行操作
  - 特别适合你这种“业务规则自写，视觉样式自定”的项目
- 替换边界：
  - 仅替换表格渲染和交互
  - 用户角色、封禁、删除、任务取消等操作仍调用现有业务 API

### 4.2 `@tanstack/react-virtual`

- GitHub: <https://github.com/TanStack/virtual>
- 适合替换：
  - 网盘文件列表
  - 管理台用户表
  - 管理台文件审计表
  - 任务列表与后续审计日志列表
- 适配原因：
  - 大列表性能提升明显
  - 与 `TanStack Table` 搭配自然
- 替换边界：
  - 只负责渲染性能
  - 不负责分页规则和数据源决策

### 4.3 `react-dropzone`

- GitHub: <https://github.com/react-dropzone/react-dropzone>
- 适合替换：
  - `front/src/pages/files/FilesPage.tsx` 的上传文件入口
  - `front/src/transfer/pages/TransferPage.tsx` 的发送文件区域
- 适配原因：
  - 现有上传入口过于基础
  - 拖拽体验、选中文件反馈、目录导入体验都能更好
- 替换边界：
  - 只负责文件选择与拖拽
  - 上传策略仍由后端 `storage-governance` 决定

### 4.4 `react-arborist`

- GitHub: <https://github.com/brimdata/react-arborist>
- 适合替换：
  - `front/src/pages/files/FilesPage.tsx` 的左侧目录树
- 适配原因：
  - 当前目录树是递归按钮结构，功能够用但扩展性弱
  - 后续若需要键盘导航、虚拟化、拖拽移动、懒加载，`react-arborist` 很合适
- 替换边界：
  - 只负责树控件交互
  - 路径合法性、移动规则、同名冲突仍由后端判定

### 4.5 `dnd-kit`

- GitHub: <https://github.com/clauderic/dnd-kit>
- 适合替换：
  - 文件移动的可视化拖拽
  - 上传队列排序
  - 后续管理台面板编排
- 适配原因：
  - 你当前项目前端自定义程度高，`dnd-kit` 比重型集成组件更灵活
- 替换边界：
  - 只负责拖拽行为
  - 最终 move/copy 仍走现有 API

### 4.6 `react-resizable-panels`

- GitHub: <https://github.com/bvaughn/react-resizable-panels>
- 适合替换：
  - 网盘页面三栏布局：目录树 / 文件表 / 文件详情
  - 后续管理台多面板布局
- 适配原因：
  - 当前 `FilesPage` 三栏是固定宽度，不利于高密度工作流
  - 可调面板更符合“桌面工作台”气质

### 4.7 `recharts`

- GitHub: <https://github.com/recharts/recharts>
- 适合替换：
  - `front/src/admin/dashboard.tsx` 的遥测图表区域
  - 后续请求趋势、活跃用户、存储分布图
- 适配原因：
  - 当前后台总览以统计卡为主，图表层很薄
  - `Recharts` 接入简单，适合当前 React 栈
- 替换边界：
  - 只负责可视化
  - 指标口径仍由后端 summary 接口定义

### 4.8 `vidstack`

- GitHub: <https://github.com/vidstack/player>
- 适合替换：
  - 后续文件预览页里的音视频播放器
  - 分享页的音视频预览层
- 适配原因：
  - 当前项目几乎没有完整播放器能力
  - `vidstack` 的 React 组合式设计很适合保留你的视觉系统
- 替换边界：
  - 只负责播放 UI 与控制条
  - 下载授权、预览授权、签名地址获取仍由业务接口负责

### 4.9 `hls.js`

- GitHub: <https://github.com/video-dev/hls.js>
- 适合替换：
  - 后续若引入 `m3u8` / 分片视频播放
- 适配原因：
  - 这是 HLS 底层能力，不建议自己实现
- 替换边界：
  - 作为播放器底层能力
  - 不承载任何业务权限逻辑

---

## 5. 当前项目不建议优先引入的组件

### 5.1 `ag-grid`

- 功能很强
- 但对当前项目来说偏重
- 会明显抬高管理台复杂度和定制成本
- 只有在后台表格演进成“数据平台级界面”时才值得引入

### 5.2 重型文件管理器整包

- 不建议直接引入“整套 file manager UI”
- 原因是你的业务语义已经明显超出通用文件管理器：
  - 分享
  - 快传
  - 异步任务
  - 存储策略
  - 管理治理
- 更适合按能力拆分引入单点组件，而不是整体换壳

---

## 6. 推荐替换优先级

### P1：立刻有价值

1. `@tanstack/react-table`
2. `react-dropzone`
3. `react-resizable-panels`
4. `recharts`

### P2：网盘工作区增强

1. `react-arborist`
2. `@tanstack/react-virtual`
3. `dnd-kit`

### P3：媒体预览体系建设

1. `vidstack`
2. `hls.js`

---

## 7. 页面与推荐组件映射

| 页面 | 当前问题 | 推荐组件 | 替换范围 |
| --- | --- | --- | --- |
| `/files` | 文件树、文件表、上传入口都较原始 | `react-arborist` + `TanStack Table` + `react-dropzone` + `react-resizable-panels` | 目录树、文件表、上传选择、三栏布局 |
| `/tasks` | 列表为手写表格 | `TanStack Table` | 列表渲染与排序/筛选 |
| `/shares` | 列表信息密度一般 | `TanStack Table` | 分享表格与列配置 |
| `/transfer` | 文件选择、状态展示、历史记录卡片较基础 | `react-dropzone` | 文件选择与拖拽体验 |
| `/admin/dashboard` | 图表能力偏弱 | `recharts` | 指标趋势和分布图 |
| `/admin/users` | 表格操作多但结构不稳定 | `TanStack Table` | 用户管理表格 |
| `/admin/files` | 审计表格可扩展性弱 | `TanStack Table` + `TanStack Virtual` | 文件审计表格和大列表性能 |
| `/admin/file-blobs` | 风险巡检列表尚未实现 | `TanStack Table` + `TanStack Virtual` | 对象实体列表、筛选、风险标签 |
| `/admin/shares` | 分享治理页尚未实现 | `TanStack Table` | 分享治理列表和筛选栏 |
| `/admin/tasks` | 全站任务监控尚未实现 | `TanStack Table` | 任务监控表格与详情侧栏 |
| `/admin/audits` | 审计日志前端未接线 | `TanStack Table` | 审计日志表格与筛选栏 |
| 媒体预览页（待建） | 目前无播放器体系 | `vidstack` + `hls.js` | 播放器和流媒体底层 |

---

## 8. 建议保留自研的部分

这些部分不建议被开源组件接管：

- `front/src/lib/upload-session.ts`
- `front/src/lib/upload-runtime.ts`
- `front/src/lib/background-tasks.ts`
- `front/src/lib/shares-v2.ts`
- `front/src/lib/files.ts`
- `front/src/transfer/api/transfer.ts`
- 页面内所有调用业务 API 的 action handler

原因：

- 它们直接对应项目自己的领域模型和 API 语义
- 它们才是“项目资产”，而不是 UI 可替换件

---

## 9. 最推荐的一组组合

如果只做一轮最稳、最值的升级，建议组合如下：

- 表格：`@tanstack/react-table`
- 大列表：`@tanstack/react-virtual`
- 上传入口：`react-dropzone`
- 目录树：`react-arborist`
- 分栏布局：`react-resizable-panels`
- 图表：`recharts`
- 播放器：`vidstack`
- HLS 底层：`hls.js`

这组组合和当前项目的契合点在于：

- 不会抢走业务规则所有权
- 能显著改善工作台体验
- 能让管理台和网盘页更专业
- 不会把前端重构成另一个不可控的大系统