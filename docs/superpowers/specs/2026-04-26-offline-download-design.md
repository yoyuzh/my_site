# 2026-04-26 Offline Download Design

## Summary

为网盘文件页补上真正可用的“离线下载”能力，对齐 Cloudreve 这一类网盘产品的核心语义：

- 用户提交远程下载源
- 系统在服务端后台下载
- 下载完成后自动导入用户网盘目录
- 用户可在任务页查看进度、取消任务、处理 BT 文件选择

本轮采用 `aria2 + qBittorrent` 双引擎方案：

- `HTTP/HTTPS` 普通链接走 `aria2`
- `magnet` 和 `.torrent` 走 `qBittorrent`

从产品名上继续叫“离线下载”，但在后端实现上使用 `RemoteDownload` 作为领域命名，避免与现有 `transfer` 模块中的“离线传输 / offline transfer”语义混淆。

## Goals

- 把 [frontend/src/pages/Files.tsx](../../../frontend/src/pages/Files.tsx) 里的“离线下载”占位入口升级为真实功能
- 支持三类下载源：
  - `HTTP/HTTPS` 链接
  - `magnet`
  - `.torrent`
- 支持完整任务闭环：
  - 新建离线下载任务
  - 列出我的任务
  - 查看任务详情和进度
  - 取消任务
  - BT 元数据就绪后选择文件
  - 下载完成后自动导入到目标网盘目录
- 保持与现有 `/api/v2/tasks/**` 任务页兼容，任务中心继续作为主观察入口
- 预留未来多下载节点能力，但首版只实现单节点运行

## Non-Goals

- 不做管理员图形化下载器配置页
- 不做真实多节点调度和跨机器下载编排
- 不做做种比率、tracker 编辑器、BT 高级限速等运维配置能力
- 不做嵌入第三方下载器原生 Web UI
- 不做浏览器本地下载替代方案
- 不改变现有离线传输、取件码传输、分享、回收站等业务语义

## Existing Context

仓库内已经存在可复用基础，不需要重新造一整套并行系统：

- 文件页入口已经预留“离线下载”菜单，但当前只弹 `alert`
- [frontend/src/pages/Tasks.tsx](../../../frontend/src/pages/Tasks.tsx) 已经能展示后台任务
- [frontend/src/lib/tasks.ts](../../../frontend/src/lib/tasks.ts) 已经为 `REMOTE_DOWNLOAD` 提供中文任务类型文案
- `transfer` 模块已有“离线文件导入网盘”的现成链路：
  - [backend/src/main/java/com/yoyuzh/transfer/api/TransferImportApi.java](../../../backend/src/main/java/com/yoyuzh/transfer/api/TransferImportApi.java)
  - [backend/src/main/java/com/yoyuzh/transfer/internal/application/RuntimeTransferImportApi.java](../../../backend/src/main/java/com/yoyuzh/transfer/internal/application/RuntimeTransferImportApi.java)
- `platform.job` 模块已有任务生命周期、任务列表、进度查询、取消/重试等基础设施

因此本轮设计不是新增一个与现有系统平行的“下载中心”，而是在现有 `transfer + platform.job + workspace` 结构上补齐远程下载能力。

## Cloudreve Alignment

参考 Cloudreve 的“离线下载 / Remote Download”语义，本项目本轮保持以下产品一致性：

- 离线下载是服务端代下载，不是浏览器直接下载
- 下载结果应自动进入用户指定网盘目录
- 离线下载是一个持续中的后台任务，而不是同步接口
- BT 下载支持在元数据就绪后选择要下载的文件
- 任务进度、失败信息、取消操作都通过统一任务中心表达

本项目不追求 Cloudreve 的接口兼容，也不复制 Cloudreve 的节点系统或后台配置界面，只对齐核心用户能力。

## Product Semantics

### User entry

首版入口放在文件页空白区域右键菜单和后续可复用的“新建”动作体系中：

- 用户在文件页点击“离线下载”
- 打开一个轻量弹窗
- 在弹窗中输入下载源，并选择导入目录

首版不新增独立的“离线下载首页”，避免把任务表单和任务中心拆成两套心智。

### Input types

首版支持以下输入形式：

- 单个 `HTTP/HTTPS` URL
- 单个 `magnet` 链接
- 单个 `.torrent` 文件上传

首版不支持：

- 多链接批量粘贴
- ed2k
- FTP
- 下载完成前的“先存到临时区不导入”

### Destination semantics

用户在创建任务时必须选择一个网盘目标目录，例如：

- `/`
- `/影视`
- `/下载/课程资料`

“离线下载”不是一个独立文件空间，下载完成后直接作为普通网盘文件落到该目录下。

### Task ownership

任务归创建人所有：

- 只有创建人能看到自己的离线下载任务
- 只有创建人能取消任务
- 导入后文件归创建人所有

## Architecture Decision

### Recommended approach

采用“`transfer` 持有业务真相，`platform.job` 负责异步执行，下载器作为外部引擎”的方案。

原因：

- `transfer` 已经拥有“外部内容进入用户空间”的相近语义和导入链路
- `platform.job` 已经拥有统一任务生命周期，不应再承载业务真相
- `files.workspace` 和 `files.content` 应继续只负责目录规则和内容注册，不直接理解下载器状态机

### Ownership

- `transfer`
  - 拥有离线下载业务状态、来源类型、节点选择、任务阶段、文件选择、导入资格等规则
- `platform.job`
  - 拥有排队、执行、重试、取消、进度快照等后台任务基础设施
- `files.workspace`
  - 拥有目标路径合法性、目录层级创建、同目录重名校验
- `files.content`
  - 拥有内容注册与 blob 真相
- `platform.storage`
  - 拥有尺寸限制、默认存储策略等基础约束
- `transfer.internal.infra`
  - 持有 `aria2` 和 `qBittorrent` 客户端适配器

### Naming rule

为了避免与现有 `OfflineTransferSession` / `OfflineDownloadResult` 概念冲突，本轮统一采用：

- 产品对外文案：离线下载
- 后端领域名：`RemoteDownload`
- 任务类型：继续复用现有 `BackgroundTaskType.REMOTE_DOWNLOAD`

不得把新能力建模为新的 `OfflineTransfer*` 类型。

## Backend Design

### Module shape

后端新增能力放在 `transfer` 模块内，分层如下：

- `transfer.api`
  - `RemoteDownloadApi`
  - 创建任务、查询详情、列出任务、选择 BT 文件、取消任务等契约
- `transfer.internal.web`
  - 新的离线下载控制器和请求 DTO
- `transfer.internal.application`
  - 任务编排、下载状态同步、导入调度
- `transfer.internal.domain`
  - `RemoteDownloadTask`、状态枚举、来源值对象、候选文件值对象、下载节点值对象
- `transfer.internal.infra`
  - JPA repository、`aria2` 客户端、`qBittorrent` 客户端、下载器轮询适配器

### Domain model

本轮至少引入以下核心概念：

- `RemoteDownloadTask`
  - 一条离线下载业务记录
- `RemoteDownloadSource`
  - 来源类型与原始输入
- `RemoteDownloadStatus`
  - 业务阶段状态
- `RemoteDownloadCandidateFile`
  - BT 元数据解析后的候选文件
- `DownloadNode`
  - 逻辑下载节点
- `DownloadEngineType`
  - `ARIA2` 或 `QBITTORRENT`

`RemoteDownloadTask` 应至少包含这些业务字段：

- `id`
- `userId`
- `targetPath`
- `sourceType`
- `sourceValue`
- `engineType`
- `downloadNodeId`
- `status`
- `backgroundTaskId`
- `downloaderTaskId`
- `selectedFileCount`
- `importedFileCount`
- `failureCode`
- `failureMessage`
- `createdAt`
- `updatedAt`
- `finishedAt`

### State model

业务状态定义为：

- `PENDING`
- `SUBMITTED`
- `FETCHING_METADATA`
- `AWAITING_FILE_SELECTION`
- `DOWNLOADING`
- `IMPORTING`
- `COMPLETED`
- `FAILED`
- `CANCELED`

状态语义如下：

- `PENDING`
  - 业务记录已创建，还未提交给下载器
- `SUBMITTED`
  - 已提交给下载器，等待实际启动
- `FETCHING_METADATA`
  - 正在获取 BT 元数据或文件清单
- `AWAITING_FILE_SELECTION`
  - 候选文件已可用，等待用户勾选
- `DOWNLOADING`
  - 下载器正在拉取实际内容
- `IMPORTING`
  - 内容已下载完成，系统正在导入网盘
- `COMPLETED`
  - 导入成功
- `FAILED`
  - 下载或导入失败
- `CANCELED`
  - 用户主动取消，或系统成功撤销下载器任务

### Engine routing

来源与下载引擎的映射固定如下：

- `HTTP/HTTPS` -> `aria2`
- `MAGNET` -> `qBittorrent`
- `TORRENT_FILE` -> `qBittorrent`

首版不做动态路由策略，也不允许用户手动改选引擎。

### Downloader integration

#### aria2

`aria2` 通过官方 `JSON-RPC` 接口接入，首版使用它处理普通链接下载。

首版需要的能力：

- 新建下载任务
- 查询任务状态
- 查询进度
- 取消任务
- 获取下载结果路径

#### qBittorrent

`qBittorrent` 通过 WebUI API 接入，首版使用它处理 `magnet` 和 `.torrent`。

首版需要的能力：

- 添加磁力链任务
- 上传种子并创建任务
- 查询种子元数据和文件列表
- 设置文件优先级或勾选下载文件
- 查询下载进度
- 取消任务
- 获取完成后的内容路径

### Download node strategy

首版只实现单个逻辑节点，例如 `local-default`，但模型必须带上：

- `downloadNodeId`
- 节点支持的引擎集合
- 节点共享下载目录

这样未来扩成多节点时，只需新增节点注册与选路，不必推翻表结构和接口。

### Shared staging directory

后端与下载器必须共享同一个可读目录。流程假设如下：

- `aria2` / `qBittorrent` 把内容下载到共享 staging 目录
- 后端从 staging 目录读取完成文件
- 后端调用内容注册与导入链路写入正式存储
- 导入完成后清理 staging 文件

如果下载器输出目录与后端不可互通，则任务必须直接失败并给出明确错误，而不是静默卡死。

### Import flow

下载完成后，不直接让下载器“入库”，而是由后端显式执行导入：

1. 校验任务归属和状态
2. 校验目标目录合法性
3. 校验用户容量和上传尺寸限制
4. 为每个待导入文件注册 blob
5. 创建正式 workspace 文件节点
6. 更新导入计数和任务状态
7. 清理 staging 文件

这条链路应尽量复用现有 `TransferImportApi` 的能力边界，而不是绕过 `workspace` 和 `content` 直接落库。

### BT file selection

BT 文件选择必须是显式状态，而不是隐藏实现细节。

规则如下：

- `magnet` / `.torrent` 提交后先进入 `FETCHING_METADATA`
- 下载器返回可选文件列表后转为 `AWAITING_FILE_SELECTION`
- 用户至少选择一个文件后，任务才允许继续
- 若用户取消全部文件，应视为取消任务，而不是继续空下载

候选文件应包含：

- 文件标识
- 相对路径
- 文件大小
- 默认是否勾选

### API surface

新增一组认证态 API，由 `transfer` 模块提供，路径如下：

- `POST /api/transfer/remote-downloads`
- `GET /api/transfer/remote-downloads`
- `GET /api/transfer/remote-downloads/{id}`
- `POST /api/transfer/remote-downloads/{id}/selection`
- `DELETE /api/transfer/remote-downloads/{id}`

请求语义如下：

- `POST /api/transfer/remote-downloads`
  - 创建任务
  - 使用 `multipart/form-data`
  - 字段固定为 `sourceType`、`sourceValue`、`torrentFile`、`targetPath`
  - `HTTP/HTTPS` 和 `MAGNET` 使用 `sourceValue`
  - `TORRENT_FILE` 使用 `torrentFile`
- `GET /api/transfer/remote-downloads`
  - 列出当前用户的离线下载业务记录
- `GET /api/transfer/remote-downloads/{id}`
  - 返回任务详情、业务状态、文件选择信息、导入摘要
- `POST /api/transfer/remote-downloads/{id}/selection`
  - 提交 BT 选中文件
- `DELETE /api/transfer/remote-downloads/{id}`
  - 取消任务

`/api/v2/tasks/**` 继续承担通用任务列表与进度展示，不替代业务详情接口。

### Task integration

每个离线下载业务任务都要绑定一条 `BackgroundTask`：

- `BackgroundTask.type = REMOTE_DOWNLOAD`
- `publicStateJson` 保存面向任务中心展示的进度快照

`publicStateJson` 统一暴露这些字段：

- `phase`
- `message`
- `progressPercent`
- `processedItems`
- `totalItems`
- `downloadedBytes`
- `totalBytes`
- `engineType`
- `sourceType`

这样现有任务页可以先无痛显示基本进度，后续再增强离线下载专属详情。

### Retry and cancel rules

取消规则：

- `PENDING`、`SUBMITTED`、`FETCHING_METADATA`、`AWAITING_FILE_SELECTION`、`DOWNLOADING` 可取消
- `IMPORTING` 默认不可取消，避免导入中断留下半完成状态
- `COMPLETED`、`FAILED`、`CANCELED` 不可再次取消

重试规则：

- 只允许对 `FAILED` 状态任务重试
- 重试沿用原始来源、原始目标目录、原始文件选择结果
- 如果失败原因是用户容量不足，重试前必须重新校验，不得跳过限制

## Frontend Design

### Entry behavior

[frontend/src/pages/Files.tsx](../../../frontend/src/pages/Files.tsx) 中当前的占位菜单改为真实弹窗：

- 点击“离线下载”
- 弹出创建任务对话框
- 输入下载源
- 选择导入目录
- 提交后关闭弹窗并提示任务已创建

### Dialog shape

首版对话框包含：

- 来源类型自动识别或轻量切换
- URL / magnet 输入框
- `.torrent` 文件上传入口
- 目标目录选择器
- 提交按钮

首版不需要在弹窗里展示下载器级高级参数。

### Tasks page integration

[frontend/src/pages/Tasks.tsx](../../../frontend/src/pages/Tasks.tsx) 继续作为统一任务页，但要增强 `REMOTE_DOWNLOAD` 任务表现：

- 显示当前阶段文案
- 显示下载进度
- 显示失败原因
- 显示“去选择文件”操作，当状态为 `AWAITING_FILE_SELECTION`
- 显示“取消任务”操作，当任务仍可取消

如果任务页当前只依赖 `/api/v2/tasks/**`，则在选中 `REMOTE_DOWNLOAD` 任务时补拉业务详情接口。

### File selection UX

BT 文件选择不单独拆新页面，直接在任务详情或独立小弹窗中完成：

- 用户点击“选择文件”
- 拉取候选文件列表
- 支持多选
- 提交后任务恢复执行

### Completion feedback

任务导入完成后，前端应提供可跳转的结果提示：

- 显示目标目录路径
- 提供“打开所在目录”动作

这样用户能从任务中心快速回到网盘结果位置。

## Error Handling

需要明确区分以下失败类别：

- 输入非法
  - 例如 URL 不是 `http/https`、磁力链格式错误、种子文件无法解析
- 下载器不可用
  - 例如 `aria2` / `qBittorrent` 未启动、认证失败、接口超时
- 元数据阶段失败
  - 例如 magnet 长时间拿不到元数据
- 下载失败
  - 例如资源失效、磁盘不足、下载器返回错误
- 导入失败
  - 例如目标目录非法、容量不足、内容注册异常
- 清理失败
  - 下载与导入成功，但 staging 清理失败

对用户展示时，错误信息要优先转成稳定的中文业务文案；原始下载器错误可以作为附加调试信息保存在任务明细里。

## Security and Governance

- 所有离线下载 API 都要求登录态
- 只能访问自己的离线下载任务
- 目标目录必须经过 `files.workspace` 路径校验
- 导入必须经过既有容量和文件大小限制校验
- 首版不提供后台管理端对其他用户离线下载任务的直接干预接口

## Testing Strategy

### Backend

后端至少覆盖以下测试层次：

- 控制器测试
  - 创建任务、查询详情、选择文件、取消任务的参数校验和权限校验
- 应用服务测试
  - 来源识别
  - 引擎路由
  - 状态流转
  - 可取消 / 可重试规则
  - 下载完成后的导入调度
- 基础设施测试
  - `aria2` 客户端请求封装
  - `qBittorrent` 客户端请求封装
  - repository 查询和持久化
- 集成测试
  - 任务创建后能生成 `REMOTE_DOWNLOAD` 类型后台任务
  - 任务完成后能导入到 workspace

### Frontend

前端至少覆盖以下验证：

- 创建任务弹窗交互
- 表单参数组装
- 任务页中 `REMOTE_DOWNLOAD` 类型渲染
- 文件选择交互
- 任务完成后的跳转动作

## Deployment Assumptions

首版部署假设如下：

- 后端服务、`aria2`、`qBittorrent` 可互相访问
- 后端与下载器共享 staging 下载目录
- 下载器认证信息和节点配置先通过服务端配置文件提供
- 系统中只注册一个逻辑下载节点 `local-default`

如果运行环境不满足共享目录要求，则本轮设计不提供替代导入路径。

## Rollout Shape

实施顺序分三步：

1. 打通 `HTTP/HTTPS -> aria2 -> 自动导入`
2. 接入 `magnet/.torrent -> qBittorrent -> 候选文件选择 -> 自动导入`
3. 增强任务页、错误展示和“打开所在目录”体验

这三步都属于同一产品能力，但实现上可以分阶段交付。
