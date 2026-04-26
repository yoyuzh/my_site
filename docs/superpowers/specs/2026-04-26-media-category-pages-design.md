# 2026-04-26 Media Category Pages Design

## Summary

将网盘里的 `图片`、`视频`、`音乐`、`文档` 四个入口从占位页升级为真实可用的分类视角，并尽量对齐 Cloudreve 的实现方式：

- 不做四套独立页面
- 复用现有文件页的整套布局和交互
- 分类入口本质上是文件管理器的不同浏览视角
- 默认在全网盘范围内递归展示该分类下的所有文件

本轮只交付分类浏览能力，不补图片墙、视频播放页、音乐播放列表、文档阅读器等独立内容页。

## Goals

- `/dashboard/images`、`/dashboard/videos`、`/dashboard/music`、`/dashboard/documents` 不再显示建设中占位页
- 四个入口与 [frontend/src/pages/Files.tsx](/Users/mac/Documents/my_site/frontend/src/pages/Files.tsx) 保持一致的页面骨架：
  - 顶部工具栏
  - 搜索
  - 排序
  - 列表/网格视图
  - 右键菜单
  - 详情侧栏
- 分类页默认展示全网盘递归结果，而不是当前目录的局部结果
- 分类页搜索是在当前分类结果内二次筛选
- 尽量复用当前前后端已有搜索能力，不新造一套并行文件页

## Non-Goals

- 不做新的媒体专属瀑布流、相册墙、时间轴布局
- 不做新的播放器编排逻辑
- 不新增“按目录浏览分类文件”的额外模式
- 不做 Cloudreve 后台那种可配置分类查询表达式
- 不修改分享、回收站、与我共享、传输页行为

## Cloudreve Alignment

参考仓库内 `third_party/cloudreve-frontend` 的实现，Cloudreve 对这四个入口的处理方式是：

- 仍然进入同一个文件管理器
- 左侧导航切换到分类视角
- 分类视角通过路径参数表达，而不是切四套完全不同的页面
- 面包屑、顶部工具栏、分页、侧栏、右键菜单都继续复用文件管理器主实现

本项目不需要复制 Cloudreve 的内部 URI 结构，但要保持同样的产品语义：

- 分类入口是文件管理器视角切换
- 分类结果是全量递归文件集合
- 分类结果默认不混入文件夹

## UX Design

### Route model

保留当前四个入口 URL：

- `/dashboard/images`
- `/dashboard/videos`
- `/dashboard/music`
- `/dashboard/documents`

这四个路由不再进入 [frontend/src/pages/DashboardUnderConstruction.tsx](/Users/mac/Documents/my_site/frontend/src/pages/DashboardUnderConstruction.tsx)，而是进入与文件页共享的浏览页面，并携带一个前端分类视角参数：

- `image`
- `video`
- `audio`
- `document`

### Shared page shell

分类页与普通文件页共用同一套主体结构。用户看到的差异只体现在：

- 页面标题不同
- 面包屑根节点不同
- 默认数据源不同
- 结果只显示对应分类文件

以下交互保持一致：

- 搜索框位置和行为
- 排序菜单
- 列表/网格切换
- 单选、多选、全选
- 右键菜单
- 详情侧栏
- 打开、下载、分享、删除等已有文件动作

### Result semantics

分类页结果语义如下：

- 默认范围：当前用户全网盘
- 默认对象：文件，不包含文件夹
- 默认顺序：沿用现有搜索结果排序
- 搜索行为：在当前分类集合内按文件名检索

如果某类没有结果，空态文案应说明“当前分类下暂无文件”，而不是“功能未完成”。

### Breadcrumb behavior

分类页仍显示文件页风格的顶栏与路径感知，但根语义不再是一个真实目录，而是一个分类视角入口，例如：

- 图片
- 视频
- 音乐
- 文档

用户在这些页面里不通过目录层级继续下钻，因此面包屑只承担“当前视角”表达，不承担真实目录导航。

## Frontend Architecture

### Page reuse

[frontend/src/pages/Files.tsx](/Users/mac/Documents/my_site/frontend/src/pages/Files.tsx) 目前把目录浏览、搜索浏览、顶栏、列表、菜单、详情侧栏都写在一个页面里。本轮应把“浏览视角”从“目录路径”中解耦，形成两类模式：

- 普通目录模式
- 分类搜索模式

推荐做法：

- 保留 `Files` 作为普通文件页入口
- 新增一个轻量的分类页入口组件，例如 `MediaCategoryFiles`
- 抽出 `Files` 中与浏览状态相关的共享逻辑，让两个入口都复用同一套 explorer surface

### Query behavior

当前 [frontend/src/api/queries.ts](/Users/mac/Documents/my_site/frontend/src/api/queries.ts) 的 `useFiles` 行为是：

- 有搜索词时调用 `/api/v2/files/search`
- 没有搜索词时调用 `/api/files/list`

分类页不能沿用这个分支逻辑，因为分类页即使没有搜索词，也必须走“全网盘递归搜索”而不是目录 listing。建议把查询能力调整为明确的两种来源：

- `directory` source
- `category-search` source

分类页应始终调用 `/api/v2/files/search`，并自动附带：

- `category`
- `type=file`

当用户在分类页输入搜索词时，只是在同一个接口上继续追加 `name` 参数。

### File actions compatibility

分类结果中的文件项仍然是普通 `FileItem`，因此以下行为应继续可用：

- 打开预览
- 下载
- 分享
- 删除
- 收藏展示
- 详情侧栏

与“当前目录”强绑定的动作需要做语义确认：

- 上传文件
- 上传文件夹
- 新建文件夹
- 新建文件

这些按钮为了保持“布局和文件页一致”继续展示，但它们的目标目录仍应回到用户普通文件浏览上下文，而不是把分类页误当成真实目录。最稳妥的方案是：

- 分类页保留相同的顶部布局
- 与目录强绑定的新建/上传按钮在分类页点击后，仍落到默认网盘根目录 `/`

这样不需要制造“分类页目录路径”的伪概念。

## Backend Architecture

### API surface

不新增新的分类专用端点，直接扩展已有 `/api/v2/files/search`。

新增查询参数：

- `category=image|video|audio|document`

现有参数继续保留：

- `name`
- `type`
- `sizeGte`
- `sizeLte`
- `createdGte`
- `createdLte`
- `updatedGte`
- `updatedLte`
- `page`
- `size`

分类页请求会固定带上 `type=file`，避免目录结果混入。

### Ownership

- HTTP 入口和参数解释仍归 `files.search`
- 目录/文件最终真相仍归 `files.workspace`
- 本轮不让 `files.workspace` 决定分类页面产品语义

实现上允许把“分类筛选条件”作为查询契约传递给 `WorkspaceFileSearchApi`，但分类值的校验和外部 API 语义仍应由 `files.search` 负责。

### Search category contract

需要给以下契约补充分类字段：

- `SearchFilesQuery`
- `WorkspaceFileSearchQuery`

建议后端内部使用明确枚举，而不是裸字符串，避免前后端魔法值散落。

候选值：

- `IMAGE`
- `VIDEO`
- `AUDIO`
- `DOCUMENT`

### Matching rule

分类匹配采用“优先 contentType，缺失时回退扩展名”的规则：

- 图片：`image/*` 或常见图片扩展名
- 视频：`video/*` 或常见视频扩展名
- 音乐：`audio/*` 或常见音频扩展名
- 文档：常见文档 MIME / 扩展名，例如 `pdf`、`doc`、`docx`、`xls`、`xlsx`、`ppt`、`pptx`、`txt`、`md`

这样可以兼容：

- 已有 content type 完整的上传记录
- 历史数据或第三方来源导致的 content type 缺失

本轮不做管理员可配置分类规则；分类匹配规则直接固化在后端搜索实现里。

### Persistence filtering

当前 `StoredFileRepository.searchUserFiles(...)` 已经支持按名称、目录、大小、时间分页搜索。需要在这个搜索链路上追加分类筛选。

要求：

- 只筛当前用户未删除文件
- 与现有分页逻辑兼容
- 不破坏普通文件搜索

## Affected Files

预期会修改或新增这些区域：

- [frontend/src/App.tsx](/Users/mac/Documents/my_site/frontend/src/App.tsx)
- [frontend/src/pages/Files.tsx](/Users/mac/Documents/my_site/frontend/src/pages/Files.tsx)
- `frontend/src/pages/MediaCategoryFiles.tsx` 或等价入口文件
- [frontend/src/api/queries.ts](/Users/mac/Documents/my_site/frontend/src/api/queries.ts)
- [frontend/src/lib/files.ts](/Users/mac/Documents/my_site/frontend/src/lib/files.ts)
- [frontend/src/components/workspace/WorkspaceSidebar.tsx](/Users/mac/Documents/my_site/frontend/src/components/workspace/WorkspaceSidebar.tsx)
- [backend/src/main/java/com/yoyuzh/files/search/internal/web/FileSearchV2Controller.java](/Users/mac/Documents/my_site/backend/src/main/java/com/yoyuzh/files/search/internal/web/FileSearchV2Controller.java)
- [backend/src/main/java/com/yoyuzh/files/search/api/SearchFilesQuery.java](/Users/mac/Documents/my_site/backend/src/main/java/com/yoyuzh/files/search/api/SearchFilesQuery.java)
- [backend/src/main/java/com/yoyuzh/files/search/internal/application/RuntimeFileSearchApi.java](/Users/mac/Documents/my_site/backend/src/main/java/com/yoyuzh/files/search/internal/application/RuntimeFileSearchApi.java)
- [backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceFileSearchQuery.java](/Users/mac/Documents/my_site/backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceFileSearchQuery.java)
- [backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceFileSearchApi.java](/Users/mac/Documents/my_site/backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceFileSearchApi.java)
- [backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java](/Users/mac/Documents/my_site/backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java)

## Testing Plan

### Frontend

使用仓库已有命令：

- `cd frontend && npm run lint`

需要重点验证：

- 四个路由都能进入真实页面
- 页面骨架与文件页一致
- 默认结果只展示对应分类文件
- 分类页搜索只在当前分类集合内生效
- 列表/网格切换、右键菜单、详情侧栏仍正常

### Backend

使用仓库已有命令：

- `cd backend && mvn test`

需要补的测试重点：

- `/api/v2/files/search?category=image&type=file`
- `/api/v2/files/search?category=video&type=file`
- `/api/v2/files/search?category=audio&type=file`
- `/api/v2/files/search?category=document&type=file`
- content type 缺失时的扩展名回退匹配
- 非法 `category` 参数返回输入错误

## Risks

- 当前文件页把目录浏览和搜索浏览写得比较耦合，前端抽共享浏览逻辑时容易把目录专属语义带进分类页
- 文档类匹配规则如果过窄会漏文件，过宽会把压缩包、代码文件等误算进去
- 分类页继续展示上传/新建按钮时，如果目标目录语义不明确，容易让用户误以为自己正在某个真实目录里

## Recommendation

按 Cloudreve 思路实现“同一文件管理器的分类视角”，不要再扩展 `DashboardUnderConstruction` 这类独立壳页面。

这样可以：

- 最大化复用现有文件页能力
- 让四个入口以后继续自然演进成更强的分类视图
- 避免先做四个独立页面，后面再回收合并
