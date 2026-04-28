# File Open-With Layer And Viewers Design

## Goal

在文件系统里引入一个独立的“打开方式”层，让所有文件打开动作都先经过统一决策管线，而不是由页面或预览弹窗直接猜测怎么打开。

本轮最终要交付的结果是：

1. 文件双击、工具栏打开、右键 `打开`、右键 `打开方式` 都走统一打开方式层。
2. 新建 `txt / md / drawio / excalidraw` 文件后默认直接进入对应编辑器，但这些文件后续从文件夹中再次打开时仍走统一打开方式层。
3. 打开方式选择器先展示“默认推荐阅读器”，再允许用户点击 `展开所有方式` 查看完整候选列表。
4. 用户可以把某个扩展名的默认打开方式保存到账户设置中，也可以在设置页清除单个扩展名或全部默认打开方式。
5. 系统内要预置并启用完整的 viewer 配置，覆盖 Cloudreve 当前模型中的 builtin、custom、wopi 三类阅读器。

## Scope

In scope:

- `frontend/src/pages/Files.tsx`
- `frontend/src/components/files/**`
- `frontend/src/components/workspace/**`
- `frontend/src/pages/AccountSettings.tsx`
- `frontend/src/lib/**` viewer registry, matching, pipeline, preference, and runtime helpers
- `frontend/src/api/**` and `frontend/src/lib/**` viewer config, user settings, and viewer-session helpers
- `backend/src/main/java/com/yoyuzh/identity/access/**` for user-level file open preference settings
- `backend/src/main/java/com/yoyuzh/files/workspace/**` and adjacent file-facing APIs for viewer configuration reads
- `backend/src/main/java/com/yoyuzh/files/**` or a new file-viewer-focused module for WOPI/custom viewer session support
- backend and frontend tests covering viewer matching, open-with preference persistence, and viewer opening behavior

Out of scope:

- 重写整个文件页视觉风格
- 新做一套与文件页完全分离的文档中心
- 细化到每一种第三方 Office 厂商都使用独立前端类型
- 处理与打开方式无关的分享、传输、回收站、上传重构
- 把系统做成通用插件平台后再开始支持阅读器

## Cloudreve Alignment

参考仓库内 `third_party/cloudreve-frontend`，Cloudreve 的真实模型不是“文件类型一对一打开器”，而是：

- 一个 `viewer` 可以支持多个扩展名
- 一个扩展名可以对应多个 `viewer`
- 所有打开动作进入统一 `openViewers(...)` 决策函数
- 用户默认打开方式按扩展名保存
- 右键 `打开方式` 可以绕过默认值重新选
- 新建文件模板和 viewer 绑定，创建成功后直接用该 viewer 打开

本项目需要对齐的是这个“viewer registry + open pipeline + preference + template”模型，而不是只复制几个单独的预览器组件。

## User Experience

### Entry points

以下动作都必须进入统一打开方式层：

- 文件双击
- 顶部工具栏或快捷动作触发的 `打开`
- 右键菜单 `打开`
- 右键菜单 `打开方式`
- 新建文件后的自动打开

唯一例外是目录。目录仍按现有逻辑进入目录浏览，不进入打开方式层。

### Open pipeline

统一决策顺序如下：

1. 读取当前文件扩展名的用户默认打开方式
2. 如果存在有效默认值，直接使用该 viewer 打开
3. 如果不存在用户默认值，构建该扩展名的推荐阅读器列表
4. 打开方式选择器先展示推荐阅读器
5. 用户点击 `展开所有方式` 后，展示该扩展名所有可用 viewer

这里不采用“只有一个候选 viewer 时自动打开”的 Cloudreve 默认行为。对本项目而言，统一打开方式层是显式的产品能力，不是只在多候选时才出现的异常路径。

### Recommended viewers

选择器默认态不是完整列表，而是“默认推荐阅读器”列表。

推荐来源按优先级组合：

1. 该扩展名的用户默认打开方式
2. 系统默认映射 `default_viewer_mapping`
3. 该扩展名的高优先级 builtin viewer

如果某个推荐项同时也是完整候选列表的一部分，只展示一次。

### Expand all viewers

选择器底部提供明确的 `展开所有方式` 操作。

点击前：

- 只展示推荐阅读器
- 降低普通用户面对长列表的成本

点击后：

- 展示该扩展名全部可用 viewer
- 包括 builtin、custom、wopi

### Open vs Open With

`打开` 与 `打开方式` 的语义必须分离：

- `打开`：尊重用户默认打开方式；如果没有默认值，则弹出推荐阅读器选择器
- `打开方式`：始终绕过用户默认值，但仍先显示推荐阅读器，再允许 `展开所有方式`

这保证“平时顺手打开”和“显式更换打开器”是两条不同路径。

### New file behavior

新建以下四类文件时，创建成功后直接进入对应首选编辑器，不先弹出选择器：

- `txt`
- `md`
- `drawio`
- `excalidraw`

这是创作场景的显式特判，用来保留顺滑的新建体验。

但是这些文件一旦落到文件列表中，后续通过双击、右键 `打开`、右键 `打开方式` 再次打开时，仍回到统一打开方式层。

### User-managed defaults

用户设置中新增“文件”选项区域，支持按扩展名管理默认打开方式。

必须支持：

- 为某个扩展名选择默认打开方式
- 清除某个扩展名的默认打开方式
- 清除全部默认打开方式

默认打开方式是“按扩展名”生效，不按单文件生效。

## Viewer Model

前端和后端共享的 viewer 语义应对齐 Cloudreve：

- `id`
- `type`: `builtin | custom | wopi`
- `displayName`
- `exts[]`
- `icon`
- `maxSize`
- `openInNew`
- `templates[]`
- `props`
- 可选的平台限制或能力限制字段

这个模型的关键约束是：

- 一个 viewer 可以覆盖多个扩展名
- 一个扩展名可以映射到多个 viewer
- 打开逻辑永远按 `ext -> viewers[]` 展开，而不是按“图片类、文档类、压缩包类”写死分支

## Supported Viewer Families

### Builtin viewers

本项目前端 builtin viewer 范围定为：

- `image`
- `photopea`
- `code-monaco`
- `drawio`
- `markdown`
- `video`
- `pdf`
- `epub`
- `music`
- `excalidraw`
- `archive`

其中：

- `txt`、代码、普通文本类文件统一归入 `code-monaco` 家族
- `md` 归入独立 `markdown`
- `drawio` 与 `excalidraw` 使用独立编辑器
- `archive` 代表压缩包浏览器，不是单纯下载动作

### Custom viewers

`custom` viewer 负责承接外部 URL 或嵌入式阅读器能力。

它必须支持：

- URL 模板变量替换
- 新窗口打开
- iframe 或内页容器打开
- 同一 viewer 绑定多个扩展名

计划中的默认预置项应包括：

- Google 文档阅读器
- 其他适合用 URL 模板方式接入的阅读器

### WOPI viewers

Google / Microsoft / OnlyOffice 这类 Office 文档阅读器不写死成独立前端类型，而是统一落到 `wopi` viewer 家族。

前端职责：

- 请求创建 viewer session
- 接收 session 结果
- 在内页或新窗口中打开 WOPI 会话

后端职责：

- 根据 viewer id、文件 uri、期望动作生成 session
- 返回目标地址与访问凭证

WOPI viewer 可以覆盖多种扩展名，例如：

- `doc`
- `docx`
- `xls`
- `xlsx`
- `ppt`
- `pptx`
- `odt`
- `ods`
- `odp`

### Final default configuration

本轮不是“支持这些阅读器即可”，而是要把系统默认配置也落完整。

最终默认配置必须至少包含：

- 全部 builtin viewer 注册项
- Google 文档阅读器 custom viewer 配置
- Microsoft Office WOPI viewer 配置
- 压缩包浏览器配置
- 常见图片、视频、音频、Markdown、文本、代码、PDF、EPUB、白板类扩展名映射
- 新建 `txt / md / drawio / excalidraw` 对应的模板配置

## Frontend Architecture

### Registry and matching

前端新增统一 viewer 基础设施，建议职责拆分如下：

- `viewer registry`: 原始 viewer 列表与按扩展名展开后的索引
- `viewer recommendation`: 从系统默认、用户默认、builtin 优先级构建推荐列表
- `viewer preference`: 用户默认打开方式读写
- `open pipeline`: 所有打开入口共用的决策函数
- `viewer runtime`: 按 viewer type 调起 builtin/custom/wopi

### Files page orchestration

`frontend/src/pages/Files.tsx` 应继续作为页面编排层，但不再直接决定“如何打开文件”。

它只负责：

- 当前路径和列表状态
- 选中状态
- 右键菜单状态
- 打开方式选择器开关
- viewer host 当前打开态
- 新建文件动作接入统一打开管线

### Explorer surface integration

`FilesExplorerSurface.tsx` 当前双击直接调用 `onOpenFile(file)`。重构后它仍然只发出“用户请求打开文件”的事件，但该事件必须进入统一 open pipeline，而不是直连 `FilesPreviewDialog`。

### Viewer host

当前 `FilesPreviewDialog` 既承担“能不能打开”判断，又承担“如何渲染”实现。这个边界需要调整。

推荐改造方向：

- 引入新的 `FileViewerHost`
- `FileViewerHost` 接收已经选定的 `viewer` 和 `file`
- `FilesPreviewDialog` 退化为某些 builtin viewer 的展示容器，或者被拆解成更小的 builtin runtime 组件

最终链路应该是：

`open pipeline -> viewer chosen -> viewer host -> concrete builtin/custom/wopi runtime`

### User settings UI

用户设置页新增“文件打开方式”管理区域。

页面内容建议包括：

- 当前已设置默认打开方式的扩展名列表
- 每个扩展名当前绑定的 viewer
- 修改入口
- 清除单个默认值
- 清除全部默认值

这个设置页不是临时弹窗，而是正式的账户设置能力。

## Backend Architecture

### Viewer configuration contract

后端需要为前端提供正式的 viewer 配置读取契约，至少包括：

- `file_viewers`
- `default_viewer_mapping`
- viewer 元数据

这份配置是系统真相来源，不应完全写死在前端本地代码中。

### User default open-with settings

用户默认打开方式不再只放浏览器本地存储，而是作为正式用户设置的一部分持久化到账户侧。

推荐在 `identity.access` 范围内扩展：

- `GET /api/user/settings`
- `PUT /api/user/settings`

新增的设置字段建议是结构化映射，例如：

- `defaultOpenWithByExt: { [ext: string]: viewerId }`

这保证：

- 用户在设置页管理的是账户设置，而不是单端缓存
- 多端可以看到一致的默认打开方式
- 后续允许在登录态恢复文件打开偏好

`PUT /api/user/settings` 应支持原子更新基础用户设置与文件打开方式设置，避免把账户设置拆成多个并行真相来源。

### WOPI session API

后端需要新增或扩展 viewer-session 契约，以支持 `wopi` viewer。

请求至少应包含：

- 文件标识
- viewer id
- 期望动作 `view | edit`
- 可选版本信息

响应至少应包含：

- 会话凭证
- 打开地址
- 过期时间或等价会话信息

这个契约不应散在 `files.workspace.internal.web` 里直接拼装外部地址，应由清晰的应用服务边界统一负责。

### Ownership

按当前仓库约束，建议规则归属如下：

- `identity.access`：用户默认打开方式设置真相
- `files.workspace`：文件打开入口所需的文件元数据、路径、权限校验
- `platform` 或独立 viewer 应用服务：外部阅读器 session 和 viewer 配置编排

控制器不能直接决定阅读器业务规则，也不能在多个 controller 中散落 viewer 决策逻辑。

## Configuration Strategy

### System default mappings

系统默认映射 `default_viewer_mapping` 负责决定“没有用户默认值时”的推荐首项。

它不是完整候选列表，而是每个扩展名的系统推荐 viewer。

例如：

- `md -> markdown`
- `txt -> code-monaco`
- `drawio -> drawio`
- `excalidraw -> excalidraw`
- `pdf -> pdf`
- `zip -> archive`
- `docx -> microsoft-office-wopi`

### Viewer templates

viewer `templates` 用来描述“新建文件后直接用哪个 viewer 打开”。

本轮必须至少配置：

- `txt`
- `md`
- `drawio`
- `excalidraw`

模板需要决定：

- 默认扩展名
- 新建菜单显示名称
- 新建后首选 viewer

### Recommendation expansion

推荐列表与完整列表要明确区分：

- 推荐列表来自默认映射、builtin 优先级、用户默认
- 完整列表来自该扩展名映射到的全部 viewer

前端不能把这两个概念混成一个数组直接渲染，否则无法实现“默认推荐 + 展开所有方式”。

## Data Flow

### Double click or Open

1. 用户双击文件或点击 `打开`
2. 前端进入统一 open pipeline
3. 读取用户默认打开方式
4. 如果默认值存在且有效，直接打开该 viewer
5. 否则构建推荐阅读器列表
6. 打开打开方式选择器
7. 用户从推荐列表中直接选，或点击 `展开所有方式` 后从完整列表中选
8. 如果用户选择“始终使用”，写回账户设置
9. viewer runtime 按 builtin/custom/wopi 调起真实阅读器

### Open With

1. 用户点击右键 `打开方式`
2. 前端进入统一 open pipeline 的“ignore saved default”分支
3. 直接展示推荐阅读器列表
4. 用户可选择某个 viewer 本次打开，或保存为新的默认值

### New file

1. 用户从新建菜单选择 `txt / md / drawio / excalidraw`
2. 系统依据 viewer template 创建文件
3. 文件创建成功
4. 直接调用对应 viewer 打开，不弹选择器
5. 文件后续再次从列表中打开时回归统一 open pipeline

### User settings update

1. 用户进入设置页
2. 查看已配置的扩展名默认打开方式
3. 对单个扩展名修改或清除
4. 或执行清除全部默认打开方式
5. 更新后立即影响后续双击与 `打开` 动作

## Testing Strategy

### Frontend unit coverage

需要覆盖：

- `ext -> viewers[]` 匹配
- 推荐列表构建
- 用户默认值优先级
- `展开所有方式` 前后渲染差异
- 清除单个默认值
- 清除全部默认值

### Frontend interaction coverage

需要覆盖：

- 双击文件打开
- 右键 `打开`
- 右键 `打开方式`
- 新建四类文件后自动打开
- 在设置页修改默认打开方式后再次打开文件

### Viewer runtime coverage

至少验证三条完整链路：

- builtin viewer 链路
- custom viewer 链路
- wopi viewer 链路

### Backend coverage

需要覆盖：

- viewer 配置读取
- 用户默认打开方式设置读写
- 单扩展名清除
- 全量清除
- WOPI session 创建
- 无权限或无效 viewer 的拒绝路径

## Risks And Decisions

### Chosen decision: account-backed preferences

本设计明确选择“默认打开方式保存在账户设置中”，不采用仅浏览器本地存储的方案。

原因：

- 用户明确要求在设置页中正式管理
- 打开方式属于账号偏好，不应局限在单一浏览器实例
- 这能让设置页、双击逻辑、右键逻辑共享同一真相来源

### Chosen decision: explicit open-with layer

本设计明确选择“所有文件打开都先经过打开方式层”，不采用“只有多候选时才弹出”的弱入口方案。

原因：

- 这是本轮用户需求的核心，不是附加功能
- 它能统一双击、右键、新建、默认偏好、推荐列表这几条路径

### Chosen decision: configuration-backed viewer inventory

本设计明确要求最终系统默认配置上所有阅读器，而不是只让代码具备支持能力。

原因：

- 用户要求“支持他所有的阅读器并最终配置上所有阅读器”
- 没有默认配置，用户实际仍无法在打开方式层看到完整可选项
