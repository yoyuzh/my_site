# 2026-04-28 Offline Download Page Design

## Summary

本次工作只补完前端的独立“离线下载”页面，不扩展后端能力，也不重做全局任务中心。

目标是把当前 `/dashboard/offline-downloads` 的占位页替换成真正可用的工作台：

- 可以创建离线下载任务
- 可以查看活跃任务列表
- 可以在页面内查看完整详情
- 可以取消未完成任务
- 可以在 BT 元数据就绪后选择文件
- 可以折叠查看历史任务

## Scope

### In scope

- 新增独立页面组件，接管 `/dashboard/offline-downloads`
- 复用已有 `remote-downloads` API 查询与创建逻辑
- 复用已有离线下载状态文案和任务进度解析逻辑
- 把“任务页”里的离线下载详情交互抽成可复用结构，或在新页面内按同样语义实现
- 默认主视图突出进行中、待处理、等待选中文件的任务
- 历史任务单独分组显示 `COMPLETED`、`FAILED`、`CANCELED`

### Out of scope

- 不修改后端接口
- 不新增新的下载源类型
- 不改变 `Tasks` 页作为全局任务页的职责
- 不改“文件页”里已有创建离线下载弹窗的产品语义
- 不重做整个 Dashboard 视觉系统

## Existing Context

当前仓库已经具备这次页面补完所需的大部分基础：

- [frontend/src/App.tsx](../../../frontend/src/App.tsx) 里 `/dashboard/offline-downloads` 仍然指向占位页
- [frontend/src/components/files/CreateRemoteDownloadDialog.tsx](../../../frontend/src/components/files/CreateRemoteDownloadDialog.tsx) 已能创建任务
- [frontend/src/lib/remote-downloads.ts](../../../frontend/src/lib/remote-downloads.ts) 已提供创建、列表、详情、取消、文件选择 API
- [frontend/src/api/queries.ts](../../../frontend/src/api/queries.ts) 已提供 `useRemoteDownloads` 与 `useRemoteDownloadDetail`
- [frontend/src/pages/Tasks.tsx](../../../frontend/src/pages/Tasks.tsx) 已实现离线下载详情、进度、取消和 BT 文件选择交互

所以这次不是从零实现“离线下载”，而是把现有能力组织成一个专门页面，并减少“只有任务页能完整操作”的割裂感。

## UX Decision

### Page structure

页面采用三段结构：

1. 顶部操作区
   - 页面标题和说明
   - “新建离线下载”主按钮
   - 活跃任务数量与历史任务数量摘要

2. 主工作区
   - 左侧：活跃任务列表
   - 右侧：当前选中任务的完整详情

3. 历史区
   - 默认折叠
   - 展示已完成、失败、已取消任务
   - 点击可切换详情到右侧，不跳转页面

### Active vs history

按用户确认的偏好处理：

- 主列表只突出活跃任务
- 历史任务不与活跃任务混排
- 若没有活跃任务但存在历史任务，默认选中最近一条历史任务

### Detail depth

详情区必须在当前页内完整可用，不依赖跳转到 `/dashboard/tasks`：

- 当前状态
- 当前阶段
- 来源类型
- 下载引擎
- 目标目录
- 已选文件数
- 已导入文件数
- 失败原因
- 取消按钮
- 候选文件勾选与提交
- 进度条和说明消息

## Data Flow

### Loading

- 页面初始化时请求 `useRemoteDownloads()`
- 根据选中任务请求 `useRemoteDownloadDetail(id)`
- 如果远程下载关联了 `backgroundTaskId`，再从 `/v2/tasks` 结果中匹配对应任务快照，用于补足进度和 public state

### Selection

- 默认优先选中第一条活跃任务
- 没有活跃任务时选中最新历史任务
- 用户点击列表项后切换右侧详情

### Create flow

- 点击“新建离线下载”打开已有 `CreateRemoteDownloadDialog`
- 创建成功后刷新列表
- 自动选中新创建任务

### Mutations

- 取消任务：调用 `cancelRemoteDownload`
- 选择文件：调用 `selectRemoteDownloadFiles`
- 成功后统一刷新 `remoteDownloads`、`remoteDownloadDetail`、`tasks`

## Component Boundaries

建议把离线下载页拆成小而清晰的前端单元：

- `pages/OfflineDownloads.tsx`
  - 页面级状态、查询、选中逻辑、布局
- `components/offline-downloads/OfflineDownloadTaskList.tsx`
  - 活跃/历史列表展示
- `components/offline-downloads/OfflineDownloadDetailPanel.tsx`
  - 详情、进度、取消、候选文件选择

如果实现时为了减少改动，保留在单页文件内也可以，但不要复制 `Tasks.tsx` 的整段离线下载 JSX。

## Error and Empty States

- 列表加载失败：显示页面级错误提示和重试入口
- 没有任何任务：显示空态，并保留“立即创建任务”按钮
- 没有活跃任务但有历史任务：提示当前仅显示历史记录
- 候选文件提交失败：在详情区内显示错误，不丢失当前勾选

## Validation

本次前端验收以仓库已有命令为准：

- `cd frontend && npm run lint`

如果时间允许，再做一轮人工检查：

- 路由是否正常打开
- 创建成功后是否自动刷新并选中新任务
- 活跃/历史分组是否正确
- 等待选中文件状态是否可提交

