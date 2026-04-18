# API 参考文档

本文档是当前后端接口边界的快速参考，不是 OpenAPI 替代品。  
目标是帮助开发、重构、测试和 Code Review 快速回答三个问题：

- 这个能力现在走哪组接口
- 这组接口是否需要登录或管理员身份
- 这组接口当前属于稳定主线、兼容保留，还是正在迁移中的边界

如果接口细节和本文档冲突，以当前控制器和安全配置为准：

- `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`
- `backend/src/main/java/com/yoyuzh/**/**/*Controller.java`

## 1. 接口分层总览

### 1.1 公开接口

无需登录即可访问：

- `/api/auth/**`
- `/api/app/android/**`
- `GET /api/v2/site/ping`
- `GET /api/v2/shares/{token}`
- `GET /api/v2/shares/{token}?download`
- `POST /api/v2/shares/{token}/verify-password`
- `/api/transfer/**`
- `GET /api/files/share-links/{token}`
- `/`

注意：

- `/api/transfer/**` 在安全层面是公开的，但其中“发送离线快传”“查看自己的离线快传”“导入到网盘”等操作会在控制器或业务层再要求登录。

### 1.2 登录后接口

需要 Bearer Token：

- `/api/user/**`
- `/api/files/**`
- `/api/v2/files/**`
- `/api/v2/tasks/**`
- `POST /api/v2/shares`
- `GET /api/v2/shares/mine`
- `DELETE /api/v2/shares/{id}`
- `POST /api/v2/shares/{token}/import`

### 1.3 管理接口

- `/api/admin/**`

要求：

- 已登录
- 通过 `@adminAccessEvaluator.isAdmin(authentication)` 校验

当前管理员事实源：

- 当前由 `@adminAccessEvaluator.isAdmin(authentication)` 统一判定。
- 实际判定基于认证里的角色 authority，而不是用户名白名单。
- 当前允许进入 `/api/admin/**` 的角色集合来自运行时设置 `registration.managementRoles`。
- 规范化后的默认值为 `MODERATOR` 与 `ADMIN`；接口也接受 `ROLE_MODERATOR` / `ROLE_ADMIN`，保存时会自动归一化为不带前缀的角色名。
- 因此这里不再依赖 `app.admin.usernames` 作为管理权限来源。

## 2. 认证与用户模块

控制器：

- `backend/src/main/java/com/yoyuzh/auth/AuthController.java`
- `backend/src/main/java/com/yoyuzh/auth/DevAuthController.java`
- `backend/src/main/java/com/yoyuzh/auth/UserController.java`

### 2.1 `POST /api/auth/register`

用途：

- 注册并直接签发登录态

关键规则：

- 需要邀请码
- 用户名、邮箱、手机号必须唯一
- 成功注册后自动创建默认目录
- 支持可选请求头 `X-Yoyuzh-Client: desktop|mobile`

### 2.2 `POST /api/auth/login`

用途：

- 普通登录

关键规则：

- 支持可选 `X-Yoyuzh-Client`
- 同一用户桌面端和移动端可以同时在线
- 同一客户端类型再次登录会挤掉旧会话

### 2.3 `POST /api/auth/refresh`

用途：

- 刷新 access token 和 refresh token

关键规则：

- refresh token 会旋转，旧 token 失效
- 默认按 `desktop` 兜底
- 若 refresh token 本身带有客户端类型，则沿用原客户端类型

### 2.4 `POST /api/auth/dev-login`

用途：

- 开发环境免密登录

约束：

- 仅 `dev` profile 下可用
- 支持可选 `X-Yoyuzh-Client`

### 2.5 `GET /api/user/profile`

用途：

- 获取当前登录用户资料

### 2.6 `PUT /api/user/profile`

用途：

- 修改个人资料

关键规则：

- 邮箱、手机号仍需保持唯一

### 2.7 `POST /api/user/password`

用途：

- 用户自行修改密码

关键规则：

- 旧密码必须正确
- 修改后旧会话和旧 refresh token 失效
- 会重新签发新的登录态

### 2.8 头像接口

- `POST /api/user/avatar/upload/initiate`
- `POST /api/user/avatar/upload`
- `POST /api/user/avatar/upload/complete`
- `GET /api/user/avatar/content`

用途：

- 用户头像上传与读取

关键规则：

- 头像属于资料域，不属于普通网盘目录
- 支持直传初始化和代理上传
- 完成上传时会替换旧头像引用

## 3. Android 发布接口

控制器：

- `backend/src/main/java/com/yoyuzh/config/AndroidReleaseController.java`

### 3.1 `GET /api/app/android/latest`

用途：

- 获取最新 Android 安装包元信息

特点：

- 公开接口
- 读取后端当前发布元数据

### 3.2 下载接口

- `GET /api/app/android/download`
- `GET /api/app/android/download/{fileName}`

用途：

- 下载当前或指定名称的 Android 安装包

特点：

- 公开接口

## 4. 网盘主接口（旧主线，仍在使用）

控制器：

- `backend/src/main/java/com/yoyuzh/files/core/FileController.java`

这组接口仍然是当前网盘主业务入口，尤其是：

- 列表
- 目录创建
- 文件操作
- 下载
- 回收站
- 旧分享接口

### 4.1 上传接口

- `POST /api/files/upload`
- `POST /api/files/upload/initiate`
- `POST /api/files/upload/complete`

用途：

- 兼容旧上传流程

当前定位：

- 仍可用
- 但新上传主线已经转向 v2 上传会话

关键规则：

- 路径和文件名必须合法
- 同目录禁止重名
- 受系统限制、用户限制、默认存储策略限制共同约束

### 4.2 目录与列表

- `POST /api/files/mkdir`
- `GET /api/files/list`
- `GET /api/files/recent`

用途：

- 创建目录
- 分页列目录
- 最近文件

关键规则：

- `path` 必须是合法目录路径
- 创建目录时根目录 `/` 不能再次创建

### 4.3 下载

- `GET /api/files/download/{fileId}`
- `GET /api/files/download/{fileId}/url`

用途：

- 下载文件或目录压缩包
- 获取可直接下载的 URL

关键规则：

- 目录下载会被压缩为 zip
- 普通文件优先返回直链或重定向
- 特定公开安装包走专用下载逻辑

### 4.4 文件操作

- `PATCH /api/files/{fileId}/rename`
- `PATCH /api/files/{fileId}/move`
- `POST /api/files/{fileId}/copy`
- `DELETE /api/files/{fileId}`

用途：

- 重命名
- 移动
- 复制
- 删除

关键规则：

- `DELETE` 语义是“移入回收站”，不是立即物理删除
- 目录不能移动或复制到自己或自己的子目录
- 所有写操作都要经过同名冲突和配额判断

### 4.5 回收站

- `GET /api/files/recycle-bin`
- `POST /api/files/recycle-bin/{fileId}/restore`

用途：

- 查看回收站根条目
- 恢复回收站条目

关键规则：

- 恢复按整组恢复
- 恢复前检查原路径冲突和配额

### 4.6 旧分享接口

- `POST /api/files/{fileId}/share-links`
- `GET /api/files/share-links/{token}`
- `POST /api/files/share-links/{token}/import`

用途：

- 旧版分享创建、查看、导入

当前定位：

- 仍存在
- 但新分享主线已经转向 `/api/v2/shares/**`

注意：

- 旧接口和 v2 分享接口能力并不完全一致
- 后续开发应优先确认是否继续沿用这套接口

## 5. v2 文件接口

控制器：

- `backend/src/main/java/com/yoyuzh/api/v2/files/UploadSessionV2Controller.java`
- `backend/src/main/java/com/yoyuzh/api/v2/files/FileSearchV2Controller.java`
- `backend/src/main/java/com/yoyuzh/api/v2/files/FileEventsV2Controller.java`

这组接口当前承担的是“新能力”而不是完整替代旧 `/api/files/**`。

### 5.1 v2 上传会话

- `POST /api/v2/files/upload-sessions`
- `GET /api/v2/files/upload-sessions/{sessionId}`
- `DELETE /api/v2/files/upload-sessions/{sessionId}`
- `GET /api/v2/files/upload-sessions/{sessionId}/prepare`
- `POST /api/v2/files/upload-sessions/{sessionId}/content`
- `GET /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}/prepare`
- `PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}`
- `POST /api/v2/files/upload-sessions/{sessionId}/complete`

用途：

- 统一上传会话生命周期

上传模式：

- `PROXY`
- `DIRECT_SINGLE`
- `DIRECT_MULTIPART`

关键规则：

- 上传模式由默认存储策略能力决定
- 会话只属于创建者
- 已完成、已取消、已过期、已失败会话不能继续上传
- multipart 必须先准备分片上传，再记录分片，再完成会话

### 5.2 文件搜索

- `GET /api/v2/files/search`

用途：

- 组合条件搜索当前用户文件

支持条件：

- 名称
- 类型 `file|directory|all`
- 大小上下界
- 创建时间上下界
- 更新时间上下界
- 分页

### 5.3 文件事件流

- `GET /api/v2/files/events`

用途：

- SSE 订阅文件变更事件

关键参数：

- `path`，默认 `/`
- 可选请求头 `X-Yoyuzh-Client-Id`

## 6. v2 分享接口

控制器：

- `backend/src/main/java/com/yoyuzh/api/v2/shares/ShareV2Controller.java`

这是当前更完整的分享接口组。

### 6.1 创建与管理

- `POST /api/v2/shares`
- `GET /api/v2/shares/mine`
- `DELETE /api/v2/shares/{id}`

用途：

- 创建分享
- 查看自己创建的分享
- 删除自己的分享

关键规则：

- 当前只支持文件，不支持目录
- 可设置密码、过期时间、最大次数、是否允许下载、是否允许导入

### 6.2 公共访问

- `GET /api/v2/shares/{token}`
- `GET /api/v2/shares/{token}?download`
- `POST /api/v2/shares/{token}/verify-password`

用途：

- 查看分享
- 下载分享
- 验证密码

关键规则：

- 公开可访问
- 过期或次数耗尽后失效
- 有密码时需要先验密，或下载时携带密码

### 6.3 导入分享

- `POST /api/v2/shares/{token}/import`

用途：

- 把分享文件导入自己的网盘

关键规则：

- 必须登录
- 受 `allowImport` 开关控制
- 当前实现中会消耗同一个分享次数额度

## 7. 快传接口

控制器：

- `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`

### 7.1 会话创建与查询

- `POST /api/transfer/sessions`
- `GET /api/transfer/sessions/lookup`
- `POST /api/transfer/sessions/{sessionId}/join`

用途：

- 创建在线或离线快传会话
- 通过取件码查找会话
- 加入在线会话或确认离线会话

关键规则：

- 创建在线快传允许匿名
- 创建离线快传要求登录
- 在线快传只允许首个接收者加入

### 7.2 离线快传文件

- `GET /api/transfer/sessions/offline/mine`
- `POST /api/transfer/sessions/{sessionId}/files/{fileId}/content`
- `GET /api/transfer/sessions/{sessionId}/files/{fileId}/download`
- `POST /api/transfer/sessions/{sessionId}/files/{fileId}/import`

用途：

- 查看自己的离线快传记录
- 上传离线快传文件
- 下载离线快传文件
- 导入离线快传文件到网盘

关键规则：

- 查看自己的离线快传记录需要登录
- 上传离线快传文件需要登录且必须是发送者本人
- 下载可匿名，但会话必须已 `ready`
- 导入需要登录

### 7.3 在线快传信令

- `POST /api/transfer/sessions/{sessionId}/signals`
- `GET /api/transfer/sessions/{sessionId}/signals`

用途：

- WebRTC/在线快传信令交换

特点：

- 公开访问
- 仅对在线会话有意义

## 8. v2 后台任务接口

控制器：

- `backend/src/main/java/com/yoyuzh/api/v2/tasks/BackgroundTaskV2Controller.java`

### 8.1 列表与详情

- `GET /api/v2/tasks`
- `GET /api/v2/tasks/{id}`

用途：

- 查看当前用户自己的后台任务

### 8.2 取消与重试

- `DELETE /api/v2/tasks/{id}`
- `POST /api/v2/tasks/{id}/retry`

用途：

- 取消自己的任务
- 重试失败任务

关键规则：

- 只有失败任务可以重试

### 8.3 创建任务

- `POST /api/v2/tasks/archive`
- `POST /api/v2/tasks/extract`
- `POST /api/v2/tasks/media-metadata`

用途：

- 为当前用户文件创建归档、解压、媒体元数据任务

关键规则：

- 任务目标文件必须属于当前用户
- 提交的 `path` 必须与文件当前逻辑路径一致
- 不同任务类型对文件类型有额外要求

## 9. 管理接口

控制器：

- `backend/src/main/java/com/yoyuzh/admin/AdminOverviewController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminSettingsController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminUserController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminResourceController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminTaskController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminStoragePolicyController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminAuditController.java`

当前管理端覆盖的是真实治理能力，不只是统计看板。

### 9.1 概览与系统设置

- `GET /api/admin/summary`
- `GET /api/admin/settings`
- `PATCH /api/admin/settings/registration/invite-code`
- `POST /api/admin/settings/registration/invite-code/rotate`
- `GET /api/admin/filesystem`
- `PATCH /api/admin/settings/offline-transfer-storage-limit`

用途：

- 查看全局指标
- 查看系统设置快照
- 修改邀请码
- 轮换邀请码
- 查看文件系统与存储能力
- 修改离线快传总容量上限

注意：

- 当前管理台“可写设置”边界很窄，很多设置只是只读快照

### 9.2 用户治理

- `GET /api/admin/users`
- `PATCH /api/admin/users/{userId}/role`
- `PATCH /api/admin/users/{userId}/status`
- `PUT /api/admin/users/{userId}/password`
- `PATCH /api/admin/users/{userId}/storage-quota`
- `PATCH /api/admin/users/{userId}/max-upload-size`
- `POST /api/admin/users/{userId}/password/reset`

用途：

- 用户查询与治理

关键规则：

- 封禁用户会让旧登录态失效
- 管理员修改用户密码会让旧登录态失效
- 密码仍需满足密码规则

### 9.3 文件、分享、任务、实体

- `GET /api/admin/files`
- `DELETE /api/admin/files/{fileId}`
- `GET /api/admin/file-blobs`
- `GET /api/admin/shares`
- `DELETE /api/admin/shares/{shareId}`
- `GET /api/admin/tasks`
- `GET /api/admin/tasks/{taskId}`

用途：

- 全局文件治理
- 全局分享治理
- 全局任务治理
- 全局文件实体 / blob 视图

### 9.4 存储策略

- `GET /api/admin/storage-policies`
- `POST /api/admin/storage-policies`
- `PUT /api/admin/storage-policies/{policyId}`
- `PATCH /api/admin/storage-policies/{policyId}/status`
- `POST /api/admin/storage-policies/migrations`

用途：

- 查询、创建、修改、启停存储策略
- 创建迁移任务

关键规则：

- 默认策略不能被禁用
- 迁移当前只支持同运行时后端类型
- 目标策略必须已启用

## 10. 站点健康接口

控制器：

- `backend/src/main/java/com/yoyuzh/api/v2/site/SiteV2Controller.java`
- `backend/src/main/java/com/yoyuzh/config/ApiRootController.java`

### 10.1 `GET /api/v2/site/ping`

用途：

- v2 站点健康检查

### 10.2 `GET /`

用途：

- 根路径可用性检查

## 11. 当前接口演进状态

### 11.1 当前稳定主线

- 认证与用户资料：`/api/auth/**`、`/api/user/**`
- 网盘主操作：`/api/files/**`
- 快传：`/api/transfer/**`
- 管理台：`/api/admin/**`
- 新分享主线：`/api/v2/shares/**`
- 新上传主线：`/api/v2/files/upload-sessions/**`
- 新任务主线：`/api/v2/tasks/**`

### 11.2 当前并存边界

- 旧分享：`/api/files/share-links/**`
- 新分享：`/api/v2/shares/**`
- 旧上传：`/api/files/upload/**`
- 新上传：`/api/v2/files/upload-sessions/**`

这些边界当前是“并存”，不是“完全迁移完成”。

## 12. 接口层已知风险

1. 管理员权限规则不由单一领域字段决定，容易在前后端或测试里误判。
2. 旧分享和 v2 分享并存，后续加需求时容易加错接口组。
3. 旧上传与 v2 上传会话并存，规则变更时容易只改一边。
4. `/api/transfer/**` 安全层公开、业务层局部要求登录，这个边界必须在测试里显式覆盖。
5. 当前没有一份单独文档明确“哪些接口是兼容保留，哪些接口是后续唯一主线”，后续若继续演进应优先补这条决策。

## 13. 2026-04-11 Admin 审计能力补充

管理台新增审计查询接口（只读）：

- `GET /api/admin/audits`

用途：

- 查询管理端治理写操作的审计日志；
- 支持按 `actorQuery`、`actionType`、`targetType`、`targetId` 过滤；
- 返回字段包含 actor、action、target、summary、detailsJson、createdAt。

当前接入审计记录的写路径：

- 设置治理：邀请码更新/轮换、离线快传容量上限更新
- 用户治理：角色、封禁、密码、配额、上传上限、重置密码
- 资源治理：删除分享、删除文件
- 存储治理：策略创建、更新、启停、迁移任务创建
