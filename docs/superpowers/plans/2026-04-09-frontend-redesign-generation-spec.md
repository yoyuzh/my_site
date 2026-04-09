# 2026-04-09 前端构造说明书（Cloudreve 对照版）

## 文档性质

这是一份前端构造说明书，不是升级计划，不是任务拆解，也不是排期文档。

它只定义三件事：

1. 当前产品前端应该有哪些页面与入口。
2. 每个页面应该由哪些区块组成，哪些按钮应该放在哪一层。
3. 每个主要按钮应该接当前仓库里已经存在的哪组接口。

## 参考基线

本说明书参考 Cloudreve 前端的产品分层，但不照搬全部能力。

参考仓库与关键文件：

- [cloudreve/frontend](https://github.com/cloudreve/frontend)
- [路由 `src/router/index.tsx`](https://github.com/cloudreve/frontend/blob/master/src/router/index.tsx)
- [API 汇总 `src/api/api.ts`](https://github.com/cloudreve/frontend/blob/master/src/api/api.ts)
- [文件动作显示规则 `src/component/FileManager/ContextMenu/useActionDisplayOpt.ts`](https://github.com/cloudreve/frontend/blob/master/src/component/FileManager/ContextMenu/useActionDisplayOpt.ts)
- [任务页 `src/component/Pages/Tasks/TaskList.tsx`](https://github.com/cloudreve/frontend/blob/master/src/component/Pages/Tasks/TaskList.tsx)

Cloudreve 最值得借鉴的不是皮肤，而是结构：

- 普通用户工作区拆成独立的 `home/tasks/shares/settings/connect/profile`
- admin 拆成独立的 `policy/user/file/task/share/...`
- 文件页不承担所有系统级能力
- 文件动作按层分组，不把全部按钮堆在主工作区

## 当前项目的真实后端能力边界

本说明书只对齐当前项目已经存在的接口，不引入伪能力。

已存在且前端应该承接的接口组：

- `GET /api/admin/summary`
- `PATCH /api/admin/settings/offline-transfer-storage-limit`
- `GET /api/admin/users`
- `PATCH /api/admin/users/{userId}/role`
- `PATCH /api/admin/users/{userId}/status`
- `PUT /api/admin/users/{userId}/password`
- `PATCH /api/admin/users/{userId}/storage-quota`
- `PATCH /api/admin/users/{userId}/max-upload-size`
- `POST /api/admin/users/{userId}/password/reset`
- `GET /api/admin/files`
- `DELETE /api/admin/files/{fileId}`
- `GET /api/admin/storage-policies`
- `POST /api/admin/storage-policies`
- `PUT /api/admin/storage-policies/{policyId}`
- `PATCH /api/admin/storage-policies/{policyId}/status`
- `POST /api/admin/storage-policies/migrations`
- `GET /api/v2/files/search`
- `GET /api/v2/files/events`
- `/api/v2/files/upload-sessions/**`
- `/api/v2/tasks/**`
- `/api/v2/shares/**`

当前后端没有对应管理接口，因此前端不应生成实页面：

- group 管理
- node 管理
- filesystem 管理
- oauth client 管理
- devices / webdav 管理

## 当前前端的结构问题

1. 文件页承担了过多系统级能力，入口挤在一个页面里。
2. 任务没有独立页，用户只能在文件页角落看最近任务。
3. 分享没有独立“我的分享”页，创建完就只剩 toast 和公开页。
4. 管理台只有 `users/files/storagePolicies` 三个 resource，且 `storagePolicies` 还是只读。
5. 管理台用户操作仍在使用 `window.prompt` 和 `window.confirm`。
6. 桌面和移动的页面地图不是同一套产品结构。

## 产品总图

### 桌面端页面

- `/login`
- `/overview`
- `/files`
- `/tasks`
- `/shares`
- `/recycle-bin`
- `/transfer`
- `${FILE_SHARE_ROUTE_PREFIX}/:token`
- `/games`
- `/games/:gameId`
- `/admin/*`

### 移动端页面

- `/login`
- `/overview`
- `/files`
- `/tasks`
- `/shares`
- `/recycle-bin`
- `/transfer`
- `${FILE_SHARE_ROUTE_PREFIX}/:token`
- `/admin/*` 仅保留“暂未适配”说明页

### 管理台页面

当前应只存在这四个管理面板：

- `dashboard`
- `users`
- `files`
- `storage-policies`

## 设计总原则

1. 这是工作台，不是 landing page。
2. 文件列表永远是网盘主视觉中心。
3. 常用按钮常驻，低频按钮进菜单，系统级能力独立成页面。
4. 不要卡片套卡片。
5. 不要紫色、紫蓝渐变、光球背景。
6. 圆角收敛到 8px 以内。
7. 长文件名、路径、错误信息都必须可截断或换行，不能撑破布局。
8. 页面优先使用 grid / flex 做稳定结构，不靠脚本测量。

## 页面构造说明

### 1. 桌面全局 Layout

目标文件：

- `front/src/components/layout/Layout.tsx`

导航应为：

- 总览
- 网盘
- 任务
- 分享
- 快传
- 游戏
- 后台

规则：

- `任务` 和 `分享` 必须升为一级导航。
- `后台` 只对 admin 可见。
- 上传进度面板继续作为全局浮层存在，但不能遮挡文件页右栏或管理台主要操作区。

### 2. 桌面文件页 `/files`

目标文件：

- `front/src/pages/files/FilesPage.tsx`
- `front/src/pages/files/FilesToolbar.tsx`
- `front/src/pages/files/FilesMainPane.tsx`
- `front/src/pages/files/FilesTaskPanel.tsx`

结构应为三栏：

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Layout                                                               │
├──────────────┬──────────────────────────────────────┬────────────────┤
│ 目录 Rail    │ 文件工作区                            │ 右侧 Inspector │
│ 目录树       │ 面包屑 + Toolbar + 列表/网格           │ 详情/操作/任务 │
│ 回收站       │ 搜索结果或当前目录                     │ 媒体信息       │
└──────────────┴──────────────────────────────────────┴────────────────┘
```

#### 左侧目录 Rail

职责：

- 目录树
- 根目录入口
- 回收站入口

规则：

- 回收站固定在底部。
- 目录树区域独立滚动。
- 折叠时退化成 icon rail。

接口：

- `GET /api/files/list?path=...`

#### 中央文件工作区

顶部第一层按钮：

- 面包屑
- 搜索框
- 刷新
- 列表/网格切换

顶部第二层按钮：

- 上传文件
- 上传文件夹
- 新建文件夹

选中文件后的主动作：

- 下载
- 重命名
- 移动
- 复制
- 删除

“更多”菜单动作：

- 分享
- 提取媒体信息
- 查看详情

接口绑定：

- 上传：`POST /api/v2/files/upload-sessions` 及其后续 `prepare/content/parts/complete`
- 新建目录：`POST /api/files/mkdir`
- 下载：`GET /api/files/download/{id}` 或 `GET /api/files/download/{id}/url`
- 重命名：`PATCH /api/files/{id}/rename`
- 移动：`PATCH /api/files/{id}/move`
- 复制：`POST /api/files/{id}/copy`
- 删除：`DELETE /api/files/{id}`
- 搜索：`GET /api/v2/files/search`

显示模式：

- 默认显示当前目录列表
- 搜索中切换到“搜索结果模式”
- 空目录显示可执行空状态
- 错误状态显示重试

交互规则：

- 搜索结果不能污染目录缓存
- SSE 只刷新当前目录
- 列表/网格切换不能丢失选中态

#### 右侧 Inspector

Tab：

- `详情`
- `操作`
- `任务`
- `媒体`

`详情`：

- 名称
- 类型
- 路径
- 大小
- 创建时间

`操作`：

- 下载
- 分享
- 重命名
- 移动
- 复制
- 删除
- 创建媒体信息任务

`任务`：

- 最近后台任务
- 刷新
- 取消
- 重试失败任务
- 查看全部任务

`媒体`：

- 已提取 metadata 时展示详情
- 未提取时展示空状态和“创建提取任务”

接口：

- `GET /api/v2/tasks`
- `DELETE /api/v2/tasks/{id}`
- `POST /api/v2/tasks/{id}/retry`
- `POST /api/v2/tasks/media-metadata`

### 3. 我的任务页 `/tasks`

目标文件：

- `front/src/pages/Tasks.tsx`
- `front/src/mobile-pages/MobileTasks.tsx`

这是独立页面，不再是文件页附属块。

页面区块：

- 标题区
- 刷新按钮
- 自动刷新开关
- 任务筛选区
- 任务列表

列表项必须显示：

- 任务类型
- 状态
- phase
- progressPercent
- failureCategory
- startedAt / finishedAt
- 相关文件或相关路径

动作：

- 刷新
- 取消任务
- 重试失败任务
- 跳到相关文件目录

接口：

- `GET /api/v2/tasks`
- `GET /api/v2/tasks/{id}`
- `DELETE /api/v2/tasks/{id}`
- `POST /api/v2/tasks/{id}/retry`

### 4. 我的分享页 `/shares`

目标文件：

- `front/src/pages/Shares.tsx`
- `front/src/mobile-pages/MobileShares.tsx`

这是独立页面，不再把“我的分享”塞回文件页提示信息。

每个分享项展示：

- 分享名或源文件名
- 是否有密码
- 是否允许下载
- 是否允许导入
- 过期时间
- 下载次数 / 上限
- 查看公开页
- 删除

接口：

- `GET /api/v2/shares/mine`
- `DELETE /api/v2/shares/{id}`

### 5. 回收站页 `/recycle-bin`

目标文件：

- `front/src/pages/RecycleBin.tsx`

构造要求：

- 与文件页保持同源视觉
- 重点展示原路径、删除时间、到期时间
- 恢复为主动作

接口：

- `GET /api/files/recycle-bin`
- `POST /api/files/recycle-bin/{fileId}/restore`

### 6. 总览页 `/overview`

目标文件：

- `front/src/pages/Overview.tsx`

这不是营销页，而是工作台入口。

应有区块：

- 账号与存储摘要
- 最近文件
- 最近快传
- 最近任务摘要
- Android APK 入口
- 快捷入口区

快捷入口至少包含：

- 进入网盘
- 上传文件
- 打开快传
- 查看任务
- 查看分享

### 7. 快传页 `/transfer`

目标文件：

- `front/src/pages/Transfer.tsx`
- `front/src/pages/TransferReceive.tsx`

构造要求：

- 在线发送、离线发送、接收、我的离线快传记录必须分区
- 二维码、分享链接、取件码属于结果区，不属于主操作区
- 状态与进度优先于说明文案

### 8. 登录页 `/login`

目标文件：

- `front/src/pages/Login.tsx`

构造要求：

- 第一屏只放登录、注册、邀请码说明、直达快传入口
- 不做产品介绍大版面
- 密码策略与错误提示贴近输入区

### 9. 公开分享页

目标文件：

- `front/src/pages/FileShare.tsx`
- `front/src/mobile-pages/MobileFileShare.tsx`

职责：

- 展示公开分享内容
- 密码校验
- 下载
- 导入网盘

接口：

- `GET /api/v2/shares/{token}`
- `POST /api/v2/shares/{token}/verify-password`
- `GET /api/v2/shares/{token}?download=1`
- `POST /api/v2/shares/{token}/import`

页面必须明确展示：

- 是否有密码
- 是否允许下载
- 是否允许导入
- 是否过期
- 下载次数限制

## 管理台构造说明

### 1. 管理台总览 `/admin`

目标文件：

- `front/src/admin/dashboard.tsx`

区块：

- 汇总指标
- 邀请码
- 离线快传容量上限
- 最近活跃用户
- 快速跳转区

按钮：

- 刷新
- 复制邀请码
- 更新离线快传容量
- 跳转用户管理
- 跳转文件管理
- 跳转存储策略

接口：

- `GET /api/admin/summary`
- `PATCH /api/admin/settings/offline-transfer-storage-limit`

### 2. 用户管理 `/admin/users`

目标文件：

- `front/src/admin/users-list.tsx`

必须从“表格 + prompt/confirm”改成“表格 + 对话框表单”。

操作：

- 角色分配
- 修改密码
- 重置密码
- 存储上限
- 单文件上限
- 封禁/解封

接口：

- `GET /api/admin/users`
- `PATCH /api/admin/users/{userId}/role`
- `PATCH /api/admin/users/{userId}/status`
- `PUT /api/admin/users/{userId}/password`
- `PATCH /api/admin/users/{userId}/storage-quota`
- `PATCH /api/admin/users/{userId}/max-upload-size`
- `POST /api/admin/users/{userId}/password/reset`

硬性要求：

- 不允许继续使用 `window.prompt`
- 不允许继续使用 `window.confirm`

### 3. 文件管理 `/admin/files`

目标文件：

- `front/src/admin/files-list.tsx`

职责：

- 查文件
- 按文件名 / 路径 / 用户筛选
- 删除文件
- 查看基础详情

接口：

- `GET /api/admin/files`
- `DELETE /api/admin/files/{fileId}`

说明：

- 当前后端没有更多文件管理写接口，所以不要伪造“编辑文件元数据”“更改对象存储”等按钮。

### 4. 存储策略 `/admin/storage-policies`

目标文件：

- `front/src/admin/storage-policies-list.tsx`
- 配套 create/edit/status/migration 组件

这是当前管理台最重要的缺口页。

页面必须覆盖：

- 策略列表
- 新建策略
- 编辑策略
- 启停策略
- 创建迁移任务

列表字段：

- 名称
- 类型
- endpoint
- bucket
- region
- prefix
- credential mode
- enabled
- default
- max size
- capabilities

行级动作：

- 编辑
- 启用/停用
- 发起迁移

接口：

- `GET /api/admin/storage-policies`
- `POST /api/admin/storage-policies`
- `PUT /api/admin/storage-policies/{policyId}`
- `PATCH /api/admin/storage-policies/{policyId}/status`
- `POST /api/admin/storage-policies/migrations`

UI 约束：

- 默认策略不能停用，要显式禁用或给出明确提示
- 迁移任务创建成功后，要给出“去任务页查看进度”的明确入口

## 移动端构造说明

### 1. 移动全局结构

目标文件：

- `front/src/mobile-components/MobileLayout.tsx`
- `front/src/MobileApp.tsx`

底部导航应为：

- 总览
- 网盘
- 任务
- 分享
- 快传

管理台不进底部导航。

### 2. 移动文件页 `/files`

目标文件：

- `front/src/mobile-pages/files/MobileFilesPage.tsx`

结构：

- 顶部路径栏
- 搜索按钮
- 列表主体
- 底部主操作栏
- Action Sheet
- 任务 Sheet

主动作：

- 上传
- 新建文件夹

选中后动作：

- 下载
- 重命名
- 移动
- 复制
- 删除
- 分享
- 提取媒体信息

要求：

- 搜索、任务、文件动作不能再挤在一个区域
- 以底部 sheet 为主，不依赖 hover 或右键模型

### 3. 移动任务页与移动分享页

目标文件：

- `front/src/mobile-pages/MobileTasks.tsx`
- `front/src/mobile-pages/MobileShares.tsx`

规则：

- 单列列表
- 顶部刷新
- 每项动作明确
- 不要求桌面同等信息密度，但不能缺失核心能力

## 前端 helper 结构要求

前端 API helper 必须按领域分组：

- `front/src/lib/api.ts`：通用 HTTP、认证、基础请求封装
- `front/src/lib/upload-session.ts`：上传会话
- `front/src/lib/background-tasks.ts`：后台任务
- `front/src/lib/file-search.ts`：文件搜索
- `front/src/lib/file-events.ts`：文件事件 SSE
- `front/src/lib/shares-v2.ts`：分享二期
- `front/src/lib/admin-users.ts`：管理员用户动作
- `front/src/lib/admin-storage-policies.ts`：管理员存储策略动作

页面里不应该再散落写死的 `/api/admin/...` 和 `/api/v2/...` 字符串。

## 按钮到接口映射

### 文件区

- 上传文件 / 文件夹：`/api/v2/files/upload-sessions/**`
- 新建文件夹：`POST /api/files/mkdir`
- 刷新目录：`GET /api/files/list`
- 搜索：`GET /api/v2/files/search`
- 下载：`GET /api/files/download/{id}` 或 `GET /api/files/download/{id}/url`
- 重命名：`PATCH /api/files/{id}/rename`
- 移动：`PATCH /api/files/{id}/move`
- 复制：`POST /api/files/{id}/copy`
- 删除：`DELETE /api/files/{id}`
- 创建媒体信息任务：`POST /api/v2/tasks/media-metadata`

### 任务区

- 列表：`GET /api/v2/tasks`
- 详情：`GET /api/v2/tasks/{id}`
- 取消：`DELETE /api/v2/tasks/{id}`
- 重试：`POST /api/v2/tasks/{id}/retry`

### 分享区

- 我的分享：`GET /api/v2/shares/mine`
- 删除分享：`DELETE /api/v2/shares/{id}`
- 公开分享详情：`GET /api/v2/shares/{token}`
- 密码校验：`POST /api/v2/shares/{token}/verify-password`
- 公开下载：`GET /api/v2/shares/{token}?download=1`
- 导入网盘：`POST /api/v2/shares/{token}/import`

### 管理台

- summary：`GET /api/admin/summary`
- 离线快传容量：`PATCH /api/admin/settings/offline-transfer-storage-limit`
- 用户列表：`GET /api/admin/users`
- 用户角色：`PATCH /api/admin/users/{userId}/role`
- 用户状态：`PATCH /api/admin/users/{userId}/status`
- 用户密码：`PUT /api/admin/users/{userId}/password`
- 用户存储上限：`PATCH /api/admin/users/{userId}/storage-quota`
- 用户单文件上限：`PATCH /api/admin/users/{userId}/max-upload-size`
- 重置密码：`POST /api/admin/users/{userId}/password/reset`
- 文件列表：`GET /api/admin/files`
- 删除文件：`DELETE /api/admin/files/{fileId}`
- 策略列表：`GET /api/admin/storage-policies`
- 新建策略：`POST /api/admin/storage-policies`
- 编辑策略：`PUT /api/admin/storage-policies/{policyId}`
- 启停策略：`PATCH /api/admin/storage-policies/{policyId}/status`
- 创建迁移：`POST /api/admin/storage-policies/migrations`

## 禁止生成的结果

1. 不要生成 landing page。
2. 不要生成功能介绍卡片墙。
3. 不要把任务、分享、系统设置继续塞回文件页。
4. 不要为后端不存在的 group/node/filesystem/oauth/device 管理接口生成实页面。
5. 不要继续用 `window.prompt` 和 `window.confirm` 做核心管理操作。
6. 不要使用紫色和紫蓝渐变。
7. 不要继续使用大圆角和玻璃装饰压过内容。

## 最终产品地图

桌面端：

- `/overview`
- `/files`
- `/tasks`
- `/shares`
- `/transfer`
- `/games`
- `/admin`

移动端：

- `/overview`
- `/files`
- `/tasks`
- `/shares`
- `/transfer`

管理台：

- `dashboard`
- `users`
- `files`
- `storage-policies`

这就是当前项目下一阶段前端应对齐的构造终态。它吸收了 Cloudreve 的产品分层方式，但完全受限于你当前仓库真实已有的后端接口，不引入任何伪功能。
