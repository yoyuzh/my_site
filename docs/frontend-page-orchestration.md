# 前端页面编排文档

## 1. 文档目标

这份文档按当前代码基线整理前端页面结构，回答四个问题：

1. 现有哪些路由页面
2. 每个页面由哪些组件/区域组成
3. 每个页面有哪些按钮与主要交互
4. 管理界面当前有哪些可操作设置项，哪些页面仍是占位页

本文对齐当前 `front/src` 实现，不虚构未落地页面。

---

## 2. 路由总表

### 2.1 公共页面

| 路由 | 页面组件 | 说明 |
| --- | --- | --- |
| `/login` | `front/src/pages/Login.tsx` | 登录/注册入口 |
| `/share/:token` | `front/src/pages/FileShare.tsx` | 公开分享访问页 |

### 2.2 主应用页面

| 路由 | 页面组件 | 说明 |
| --- | --- | --- |
| `/overview` | `front/src/pages/Overview.tsx` | 用户概览页 |
| `/files` | `front/src/pages/files/FilesPage.tsx` | 网盘工作区 |
| `/tasks` | `front/src/pages/Tasks.tsx` | 异步任务页 |
| `/shares` | `front/src/pages/Shares.tsx` | 我的分享列表 |
| `/recycle-bin` | `front/src/pages/RecycleBin.tsx` | 回收站 |
| `/transfer` | `front/src/transfer/pages/TransferPage.tsx` | 快传 |

### 2.3 管理台页面

| 路由 | 页面组件 | 当前状态 |
| --- | --- | --- |
| `/admin/dashboard` | `front/src/admin/dashboard.tsx` | 已实现 |
| `/admin/settings` | `front/src/admin/settings.tsx` | 占位页 |
| `/admin/filesystem` | `front/src/admin/filesystem.tsx` | 占位页 |
| `/admin/storage-policies` | `front/src/admin/storage-policies-list.tsx` | 已实现 |
| `/admin/users` | `front/src/admin/users-list.tsx` | 已实现 |
| `/admin/files` | `front/src/admin/files-list.tsx` | 已实现 |
| `/admin/file-blobs` | `front/src/admin/fileblobs.tsx` | 占位页 |
| `/admin/shares` | `front/src/admin/shares.tsx` | 占位页 |
| `/admin/tasks` | `front/src/admin/tasks.tsx` | 占位页 |
| `/admin/oauth-apps` | `front/src/admin/oauthapps.tsx` | 占位页 |

补充说明：

- 以上管理台路由仅桌面端可进入
- 移动端访问 `/admin/*` 会被重定向到 `/overview`
- `front/src/mobile-components/MobileLayout.tsx` 也没有后台导航入口

---

## 3. 全局页面壳

### 3.1 桌面主壳 `Layout`

文件：`front/src/components/layout/Layout.tsx`

#### 区域

- 左侧主导航
- 顶部品牌区
- 当前账号信息区
- 主内容区 `<Outlet />`
- 全局上传浮层 `UploadCenter`

#### 左侧导航项

- `概览`
- `网盘`
- `任务`
- `分享`
- `回收站`
- `快传`
- `后台`：仅 `session.user.role === 'ADMIN'` 时显示

#### 全局按钮

- 主题切换按钮
- 退出登录按钮

#### 全局辅助组件

- `TaskSummaryPanel`
- `UploadCenter`

### 3.2 移动主壳 `MobileLayout`

文件：`front/src/mobile-components/MobileLayout.tsx`

#### 区域

- 顶部浮层
- 中间页面区 `<Outlet />`
- 底部导航
- 全局上传浮层 `UploadCenter`

#### 顶部按钮

- 主题切换
- 退出登录

#### 底部导航项

- `概览`
- `网盘`
- `任务`
- `分享`
- `回收站`
- `快传`

### 3.3 管理台壳 `AdminLayout`

文件：`front/src/admin/AdminLayout.tsx`

#### 左侧二级导航项

- `总览`
- `系统设置`
- `文件系统`
- `存储策略`
- `用户管理`
- `文件审计`
- `对象实体`
- `分享管理`
- `任务监控`
- `三方应用`

---

## 4. 全局共用浮层与摘要组件

### 4.1 `UploadCenter`

文件：`front/src/components/upload/UploadCenter.tsx`

#### 展示内容

- 上传任务总数
- 每个任务的文件名、大小、状态、进度、错误信息

补充说明：

- 组件内部虽然会计算上传中、成功、失败数量
- 但当前 UI 并没有把这三个数字单独渲染成统计块

#### 按钮

- 展开/收起面板
- 清理已完成任务

### 4.2 `TaskSummaryPanel`

文件：`front/src/components/tasks/TaskSummaryPanel.tsx`

#### 展示内容

- 当前活跃后台任务数
- 首个活跃任务类型

#### 按钮

- 无独立操作按钮
- 仅作为导航中的状态摘要组件

---

## 5. 公共页面编排

## 5.1 登录页 `/login`

文件：`front/src/pages/Login.tsx`

### 页面区域

- 右上角主题切换
- 标题区：`云盘门户`
- 登录/注册卡片
- 底部辅助操作区

### 表单模式

- 登录模式
- 注册模式

### 登录模式组件

- 用户名输入框
- 密码输入框
- 登录按钮

### 注册模式组件

- 用户名输入框
- 邮箱输入框
- 手机号输入框
- 邀请码输入框
- 密码输入框
- 确认密码输入框
- 注册按钮

### 页面按钮

- `登录`
- `注册账号`
- `还没有账号？去注册` / `已有账号？去登录`
- `直接进入快传`
- `开发账号`
- `管理员账号`
- 主题切换按钮

### 主要跳转

- 登录成功后：
  - 普通用户到 `/overview`
  - 管理员到 `/admin/dashboard`
- 可匿名进入 `/transfer`

## 5.2 分享访问页 `/share/:token`

文件：`front/src/pages/FileShare.tsx`

### 页面区域

- 分享标题区
- 分享者与文件基础信息区
- 错误提示区
- 密码验证区或分享操作区
- 底部版本戳

### 条件区域

- 若分享需要密码且未验证：
  - 显示密码输入框
  - 显示验证密码按钮
- 若已可访问：
  - 显示创建时间
  - 显示有效期
  - 显示下载统计
  - 显示下载与导入操作

### 页面按钮

- `验证密码`
- `下载文件`
- `导入网盘`

### 细节说明

- 未登录点击 `导入网盘` 会跳到 `/login`
- 下载走 `buildShareDownloadUrl`
- 导入时使用 `window.prompt` 让用户输入目标目录

---

## 6. 主应用页面编排

## 6.1 概览页 `/overview`

文件：`front/src/pages/Overview.tsx`

### 页面区域

- 页面标题区
- 三张状态卡
- 快捷入口卡片区
- 最近文件面板
- 最近任务面板

### 状态卡

- `账号权限`
- `存储配额`
- `上传上限`

### 快捷入口卡片

- `网盘`
- `任务`
- `分享`
- `快传`

### 最近文件面板内容

- 文件名
- 文件路径
- 文件大小
- 创建时间

### 最近任务面板内容

- 任务类型
- 任务状态
- 更新时间

### 页面按钮

- 快捷入口卡片本身可点击跳转

## 6.2 网盘页 `/files`

文件：`front/src/pages/files/FilesPage.tsx`

### 页面整体结构

- 左侧目录树栏
- 中间文件列表区
- 右侧文件详情栏

### 左侧目录树栏

#### 组件

- 根目录按钮
- 递归目录节点按钮
- 回收站入口按钮
- 目录树刷新状态点

#### 按钮

- `根目录`
- 每个目录节点按钮
- `回收站`

### 顶部工具区

#### 组件

- 面包屑路径导航
- 文件搜索框
- 工具按钮组

#### 按钮

- 面包屑各层级按钮
- `上传文件`
- `新建文件夹`
- `刷新`

### 文件列表表格

#### 列

- 名称
- 路径
- 大小
- 创建时间

#### 行交互

- 单击：选中文件
- 双击目录：进入目录

### 右侧文件详情栏

仅当存在 `selectedFile` 时显示。

#### 信息区

- 文件名
- 路径
- 类型
- 大小

#### 操作按钮

- `下载`
- `创建分享`
- `重命名`
- `移动`
- `删除`

#### 实际未暴露按钮

- 文件页虽然导入了 `copyFile`
- 当前 UI 没有“复制”按钮

### 用户输入方式

- 上传：隐藏 `input[type=file]`
- 新建文件夹：`window.prompt`
- 重命名：`window.prompt`
- 移动：`window.prompt`
- 删除：`window.confirm`

## 6.3 任务页 `/tasks`

文件：`front/src/pages/Tasks.tsx`

### 页面区域

- 标题区
- 顶部控制区
- 任务表格

### 顶部控制

- 自动刷新复选框
- 刷新按钮

### 任务表格列

- 类型
- 对象
- 状态
- 进度
- 更新时间
- 操作

### 操作按钮

- `刷新`
- `重试任务`：仅 `FAILED`
- `取消任务`：仅 `QUEUED` 或 `RUNNING`
- `自动刷新` 开关

### 状态展示内容

- 业务状态标签
- `phase`
- `errorMessage`
- 进度条

## 6.4 分享页 `/shares`

文件：`front/src/pages/Shares.tsx`

### 页面区域

- 标题区
- 分享表格

### 表格列

- 分享名称
- 权限
- 过期时间
- 统计
- 操作

### 权限标签

- `需密码`
- `可下载` / `仅查看`
- `可导入` / `受保护`

### 统计内容

- 下载次数 `DL`
- 查看次数 `VW`

### 操作按钮

- `打开链接`
- `删除分享`

## 6.5 回收站页 `/recycle-bin`

文件：`front/src/pages/RecycleBin.tsx`

### 页面区域

- 标题区
- 回收站表格

### 表格列

- 文件
- 原路径
- 大小
- 删除时间
- 过期时间
- 操作

### 操作按钮

- `恢复`

## 6.6 快传页 `/transfer`

文件：`front/src/transfer/pages/TransferPage.tsx`

### 页面顶部 Tab

- `发送`
- `接收`
- `记录`

### 路由级深链行为

- 当 URL 中带 `?code=XXXXXX` 时
- 页面会自动切到 `接收` Tab
- 并自动执行一次取件码查询

### A. 发送 Tab

#### 左侧：传输配置区

- 模式切换按钮
  - `离线快传`
  - `在线快传`
- 文件选择拖拽区
- 已选文件列表
- 错误提示
- 成功提示
- `创建会话` 按钮

#### 右侧：会话信息区

创建会话后显示：

- 取件码
- 模式
- 过期时间
- 上传进度

#### 右侧按钮

- `复制取件码`
- `复制链接`

### B. 接收 Tab

#### 左侧：接收会话区

- 取件码输入框
- `查询取件码`
- 会话查询结果卡
- `加入会话`

#### 右侧：文件列表区

离线快传加入成功后：

- 文件名
- 相对路径
- 文件大小

#### 文件级按钮

- `下载`
- `导入网盘`：仅登录用户显示

### C. 记录 Tab

#### 页面区域

- 标题
- 刷新按钮
- 历史记录卡片列表

#### 每条记录内容

- 取件码
- 过期时间
- 文件数
- 文件卡片网格

#### 会话级按钮

- `复制取件码`
- `复制链接`

#### 文件级按钮

- `下载`
- `导入网盘`

#### 特殊状态

- 未登录时仅显示“登录后可查看记录”

---

## 7. 管理台页面编排

本节分成两层：

- 当前前端已经落地的界面与按钮
- 依据现有后端 `/api/admin/**` 能力，管理台应补齐的完整设置项与筛选项

这样文档既能反映真实代码，也能指导后续把占位页补完整。

## 7.1 后台总览 `/admin/dashboard`

文件：`front/src/admin/dashboard.tsx`

### 页面区域

- 标题区
- 核心指标卡区
- 快捷入口区
- 运行概览区

### 核心指标卡

- `用户总数`
- `文件总数`
- `存储容量`
- `快传占用`

### 快捷入口

- `用户管理`
- `文件审计`
- `存储策略`

### 运行概览内容

- 服务健康状态
- 邀请码展示
- 下载流量
- 请求量

### 页面按钮

- `刷新状态`
- `复制邀请码`
- 三个快捷入口卡片按钮

### 当前可操作设置项

- 无真正表单设置项
- 只有“复制邀请码”和跳转入口

## 7.2 系统设置 `/admin/settings`

文件：`front/src/admin/settings.tsx`

### 当前状态

- 占位页
- 仅显示标题 `Admin Settings`

### 当前组件

- 页面容器
- 标题

### 当前按钮

- 无

### 后端已支持、前端未实现的设置分组

根据 `GET /api/admin/settings`，本页应至少拆成以下分组：

#### A. 站点能力分组 `site`

- `supported`
- `writeSupported`

说明：

- 当前后端返回只读快照
- 可作为“站点设置能力状态卡”，不一定需要编辑表单

#### B. 注册设置分组 `registration`

- `inviteCodeRequired`
- `currentInviteCode`
- `managementRoles`
- `writeSupported`

#### C. 用户会话分组 `userSession`

- `accessExpirationSeconds`
- `refreshExpirationSeconds`
- `tokenBlacklistEnabled`
- `tokenBlacklistTtlBufferSeconds`
- `writeSupported`

#### D. 快传设置分组 `transfer`

- `offlineTransferStorageLimitBytes`
- `writeSupported`

#### E. 媒体处理分组 `mediaProcessing`

- `metadataExtractionEnabled`
- `thumbnailGenerationEnabled`
- `videoPosterEnabled`
- `writeSupported`

#### F. 队列分组 `queue`

- `backend`
- `mediaMetadataFixedDelayMs`
- `mediaMetadataInitialDelayMs`
- `writeSupported`

#### G. 外观分组 `appearance`

- `supported`
- `writeSupported`

#### H. 服务器分组 `server`

- `storageProvider`
- `redisEnabled`
- `writeSupported`

### 后端已支持、前端未实现的可编辑设置项

根据当前后端可写接口，本页至少应提供：

- `当前邀请码` 编辑
- `邀请码轮换`
- `离线快传总容量上限` 编辑

### 未来应有按钮（当前前端未实现）

- `保存邀请码`
- `轮换邀请码`
- `保存离线快传容量上限`
- `刷新设置`

### 未来应有只读状态卡（当前前端未实现）

- Access Token 过期时间
- Refresh Token 过期时间
- Token 黑名单状态
- 媒体元数据提取开关状态
- 缩略图生成状态
- 视频封面状态
- 队列后端类型
- Redis 启用状态
- 当前存储提供者

## 7.3 文件系统 `/admin/filesystem`

文件：`front/src/admin/filesystem.tsx`

### 当前状态

- 占位页

### 当前按钮

- 无

### 后端已支持、前端未实现的页面分组

根据 `GET /api/admin/filesystem`，本页应至少包含：

#### A. 总览分组 `overview`

- `storageProvider`
- `totalFiles`
- `totalBlobs`
- `totalEntities`

#### B. 默认策略分组 `defaultPolicy`

- 默认策略名称
- 类型
- 桶名称
- 端点
- 地域
- 私有桶状态
- 前缀
- 凭证模式
- 最大对象大小
- 能力集合
- 启用状态
- 是否默认策略

#### C. 上传能力分组 `upload`

- `proxyUpload`
- `directSingleUpload`
- `directMultipartUpload`
- `effectiveMaxFileSizeBytes`

#### D. 媒体处理分组 `mediaProcessing`

- `metadataExtractionEnabled`
- `nativeThumbnailSupport`

#### E. 缓存分组 `cache`

- `backend`
- `filesListTtlSeconds`
- `directoryVersionTtlSeconds`

#### F. WebDAV 分组 `webdav`

- `enabled`

### 未来应有按钮（当前前端未实现）

- `刷新文件系统状态`
- `跳转默认存储策略`
- `查看对象实体`

### 建议交互（当前前端未实现）

- 文件总数 / Blob 总数 / 实体总数指标卡
- 当前上传模式矩阵卡
- 默认策略详情抽屉
- 缓存与 WebDAV 状态卡

## 7.4 存储策略 `/admin/storage-policies`

文件：`front/src/admin/storage-policies-list.tsx`

这是当前管理台里设置项最完整的页面之一。

### 页面区域

- 标题区
- 顶部操作区
- 策略表格
- 新建/编辑策略弹窗

### 顶部按钮

- `刷新`
- `新建策略`

### 策略表格列

- 名称
- 后端类型
- 访问端点
- 状态
- 对象上限
- 操作

### 每行操作按钮

- `编辑策略`
- `停用` / `启用`
  - 默认策略不显示此按钮
- `发起迁移`

### 弹窗表单字段

#### 基础字段

- `策略名称`
- `驱动协议`
  - `LOCAL`
  - `S3_COMPATIBLE`
- `端点地址`
- `桶名称`
- `地域`
- `前缀`
- `凭证模式`
- `对象大小上限（字节）`

#### 布尔设置项

- `私有桶` `privateBucket`
- `启用` `enabled`
- `直传` `capabilities.directUpload`
- `分片上传` `capabilities.multipartUpload`
- `签名下载` `capabilities.signedDownloadUrl`
- `代理下载` `capabilities.serverProxyDownload`
- `原生缩略图` `capabilities.thumbnailNative`
- `友好下载名` `capabilities.friendlyDownloadName`
- `需要 CORS` `capabilities.requiresCors`
- `内网端点` `capabilities.supportsInternalEndpoint`

### 弹窗按钮

- `取消`
- `保存`

### 当前前端未完全暴露的问题

当前弹窗只暴露了基础字段和部分布尔能力，尚未把以下字段做成独立输入控件：

- `region`
- `prefix`
- `credentialMode`
- `capabilities.thumbnailNative`
- `capabilities.friendlyDownloadName`

因此本页需要补成“完整策略表单”，否则管理台与后端能力不一致。

## 7.5 用户管理 `/admin/users`

文件：`front/src/admin/users-list.tsx`

### 页面区域

- 标题区
- 刷新按钮
- 搜索框
- 用户表格

### 搜索能力

- 搜索用户名
- 搜索邮箱
- 搜索手机号

### 页面按钮

- `刷新列表`

### 表格列

- 用户信息
- 角色
- 状态
- 资源配额
- 注册时间
- 操作

### 每个用户的可调设置项

- `角色`
  - 通过 prompt 输入 `USER` 或 `ADMIN`
- `存储配额`
  - 通过 prompt 输入字节数
- `最大上传大小`
  - 后端已支持，前端未暴露
- `密码`
  - 通过 prompt 直接设置新密码
- `重置密码`
  - 后端支持返回临时密码，前端未暴露
- `账号状态`
  - 禁用 / 恢复

### 每行按钮

- `修改角色`
- `修改配额`
- `重置密码`
  - 实际行为是“直接设置新密码”
- `禁用账号` / `恢复账号`

### 当前前端未暴露但 API 已存在的管理项

根据 `front/src/lib/admin-users.ts`，存在但本页没有按钮：

- `updateUserMaxUploadSize`
- `resetUserPassword`

也就是说，当前用户管理页尚未把“上传上限”和“自动生成临时密码”暴露出来。

### 本页应补齐的按钮

- `修改上传上限`
- `生成临时密码`
- `复制临时密码`

## 7.6 文件审计 `/admin/files`

文件：`front/src/admin/files-list.tsx`

### 页面区域

- 标题区
- 刷新按钮
- 两个搜索框
- 文件表格

### 搜索项

- 文件名 / 路径搜索
- 所属用户搜索

### 页面按钮

- `刷新索引`

### 表格列

- 文件
- 所属用户
- 大小
- 创建时间
- 操作

### 每行按钮

- `彻底删除`

### 注意

- 当前删除确认文案写的是“物理擦除 / 硬件级销毁”
- 但前端文案夸张，建议后续改成真实业务语义

## 7.7 对象实体 `/admin/file-blobs`

文件：`front/src/admin/fileblobs.tsx`

### 当前状态

- 占位页

### 当前按钮

- 无

### 后端已支持、前端未实现的筛选项

根据 `GET /api/admin/file-blobs`，本页应支持：

- `userQuery`
- `storagePolicyId`
- `objectKey`
- `entityType`

### `entityType` 选项

- `VERSION`
- `THUMBNAIL`
- `LIVE_PHOTO`
- `TRANSCODE`
- `AVATAR`

### 后端已支持、前端未实现的表格列

- 实体 ID `entityId`
- Blob ID `blobId`
- 对象键 `objectKey`
- 实体类型 `entityType`
- 存储策略 ID `storagePolicyId`
- 大小 `size`
- 内容类型 `contentType`
- 引用计数 `referenceCount`
- 关联逻辑文件数 `linkedStoredFileCount`
- 关联所有者数 `linkedOwnerCount`
- 示例所有者用户名 `sampleOwnerUsername`
- 示例所有者邮箱 `sampleOwnerEmail`
- 创建人 ID `createdByUserId`
- 创建人用户名 `createdByUsername`
- 实体创建时间 `createdAt`
- Blob 创建时间 `blobCreatedAt`
- `blobMissing`
- `orphanRisk`
- `referenceMismatch`

### 未来应有按钮（当前前端未实现）

- `刷新对象实体`
- `清空筛选`
- `按对象键复制`
- `跳转存储策略`

### 未来应有风险标签（当前前端未实现）

- `Blob 缺失`
- `孤儿风险`
- `引用不一致`

## 7.8 分享管理 `/admin/shares`

文件：`front/src/admin/shares.tsx`

### 当前状态

- 占位页

### 当前按钮

- 无

### 后端已支持、前端未实现的筛选项

根据 `GET /api/admin/shares`，本页应支持：

- `userQuery`
- `fileName`
- `token`
- `passwordProtected`
- `expired`

### 后端已支持、前端未实现的表格列

- 分享 ID
- Token
- 分享名称
- 是否密码保护
- 是否已过期
- 创建时间
- 过期时间
- 最大下载次数
- 已下载次数
- 已查看次数
- 是否允许导入
- 是否允许下载
- 所有者 ID
- 所有者用户名
- 所有者邮箱
- 文件 ID
- 文件名
- 文件路径
- 文件内容类型
- 文件大小
- 是否目录

### 未来应有按钮（当前前端未实现）

- `刷新分享列表`
- `删除分享`
- `复制分享 Token`
- `打开分享链接`
- `按所有者筛选`
- `按文件名筛选`

### 核心治理动作（当前前端未实现）

- 撤销分享
- 快速定位滥用链接
- 筛选密码保护分享
- 筛选过期分享

## 7.9 任务监控 `/admin/tasks`

文件：`front/src/admin/tasks.tsx`

### 当前状态

- 占位页

### 当前按钮

- 无

### 后端已支持、前端未实现的筛选项

根据 `GET /api/admin/tasks`，本页应支持：

- `userQuery`
- `type`
- `status`
- `failureCategory`
- `leaseState`

### 后端已支持、前端未实现的表格列

- 任务 ID
- 任务类型
- 任务状态
- 用户 ID
- 所有者用户名
- 所有者邮箱
- `publicStateJson`
- `correlationId`
- `errorMessage`
- `attemptCount`
- `maxAttempts`
- `nextRunAt`
- `leaseOwner`
- `leaseExpiresAt`
- `heartbeatAt`
- `createdAt`
- `updatedAt`
- `finishedAt`
- `failureCategory`
- `retryScheduled`
- `workerOwner`
- `leaseState`

### 未来应有按钮（当前前端未实现）

- `刷新任务监控`
- `查看详情`
- `复制 correlationId`
- `按失败类别筛选`
- `按租约状态筛选`

### 未来应有详情面板（当前前端未实现）

- 任务公共状态 JSON 展开区
- 执行元信息区
- 重试预算区
- 租约与心跳状态区

### 注意

- 管理台任务监控是“全站视角”
- 不等同于普通用户 `/tasks` 页面

## 7.10 三方应用 `/admin/oauth-apps`

文件：`front/src/admin/oauthapps.tsx`

### 当前状态

- 占位页

### 当前按钮

- 无

### 当前设置项

- 当前后端管理接口里未看到对应 `/api/admin/oauth-apps` 能力
- 因此前端现在不应凭空设计可写设置项

### 本页建议定位

- 先保留为规划页
- 等后端 OAuth 管理接口落地后再补充

## 7.11 审计日志（后端已支持，前端未挂路由）

当前 `App.tsx` 没有 `/admin/audits` 路由，但后端已经提供 `GET /api/admin/audits`。

### 后端已支持、前端未实现的筛选项

- `actorQuery`
- `actionType`
- `targetType`
- `targetId`

### 后端已支持、前端未实现的表格列

- 审计日志 ID
- 操作者用户 ID
- 操作者用户名
- 操作者权限快照
- 动作类型
- 目标类型
- 目标 ID
- 摘要
- `detailsJson`
- 创建时间

### 未来应有按钮（当前前端未实现）

- `刷新审计日志`
- `复制详情 JSON`
- `按动作类型筛选`
- `按目标类型筛选`

---

## 8. 管理台设置项总清单

为了便于单独查看，这里把“当前前端实际可操作的管理设置项”重新汇总一次。

### 已落地设置项

#### 后台总览

- 复制邀请码

#### 存储策略

- 新建策略
- 编辑策略
- 启用策略
- 停用策略
- 发起策略迁移
- 设置策略名称
- 设置驱动协议
- 设置端点地址
- 设置桶名称
- 设置对象大小上限
- 设置私有桶
- 设置启用状态
- 设置是否支持直传
- 设置是否支持分片上传
- 设置是否支持签名下载
- 设置是否支持代理下载
- 设置是否需要 CORS
- 设置是否支持内网端点

#### 用户管理

- 修改用户角色
- 修改用户存储配额
- 修改用户密码
- 禁用用户
- 恢复用户

#### 文件审计

- 按文件名/路径筛选
- 按所属用户筛选
- 彻底删除文件

### 应补齐但前端未落地的设置项

#### 系统设置

- 当前邀请码编辑
- 邀请码轮换
- 离线快传总容量上限编辑
- 会话时长与黑名单状态查看
- 媒体处理状态查看
- 队列后端与延迟参数查看
- Redis 与存储提供者状态查看

#### 文件系统

- 文件总量 / Blob 总量 / 实体总量查看
- 默认策略详情查看
- 上传能力矩阵查看
- 缓存 TTL 查看
- WebDAV 状态查看

#### 对象实体

- 按用户筛选
- 按存储策略筛选
- 按对象键筛选
- 按实体类型筛选
- 风险标签巡检

#### 分享管理

- 按所有者筛选
- 按文件名筛选
- 按 Token 筛选
- 按是否密码保护筛选
- 按是否过期筛选
- 撤销分享

#### 任务监控

- 按用户筛选
- 按任务类型筛选
- 按任务状态筛选
- 按失败类别筛选
- 按租约状态筛选
- 查看详情 JSON

#### 审计日志

- 按操作者筛选
- 按动作类型筛选
- 按目标类型筛选
- 按目标 ID 筛选

### 暂无后端能力支撑的规划页

- 三方应用

---

## 9. 当前页面编排上的主要缺口

### 9.1 管理台

- 只有一半左右页面真正实现
- `settings/filesystem/file-blobs/shares/tasks/oauth-apps` 仍是占位页
- 用户管理缺少“上传上限”与“自动生成临时密码”
- 存储策略页未暴露全部策略字段

### 9.2 用户工作台

- 网盘页缺少“复制”操作按钮
- 网盘页缺少图片/视频/音频预览页
- 快传页缺少二维码、发送进度分文件视图、在线直连流程可视化

### 9.3 移动端

- 当前移动端复用主页面路由
- 但管理台被直接重定向走开，没有单独移动后台方案
- 移动端底部导航也没有后台入口

---

## 10. 结论

当前前端已经具备完整的：

- 登录
- 概览
- 网盘
- 分享
- 回收站
- 快传
- 基础任务页
- 部分管理能力

但管理台目前仍是“半成品治理台”：

- `dashboard`
- `storage-policies`
- `users`
- `files`

这四页是当前真正可用的后台主线；

同时，后端管理能力已经明显超过当前前端管理台：

- `settings`
- `filesystem`
- `file-blobs`
- `shares`
- `tasks`
- `audits`

这些都已经有明确的查询或写入接口，因此后续管理台补齐时，不应该从零猜需求，而应直接以这里列出的字段、筛选项和按钮为基线实现。
