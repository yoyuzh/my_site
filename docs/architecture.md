# 架构文档

本文档用于描述 `yoyuzh.xyz` 当前的系统结构、模块边界、关键流程和部署方式，便于后续窗口快速建立整体上下文。

## 1. 系统概览

项目是一个前后端分离的全栈站点，核心由三部分组成：

1. React 前端站点
2. Spring Boot 后端 API
3. 文件存储层（本地文件系统或 S3 兼容对象存储）

当前前端除了作为 Web 站点发布外，也已支持通过 Capacitor 打包成 Android WebView 壳应用。

业务主线已经从旧教务方向切换为：

- 账号系统
- 个人网盘
- 快传
- 管理台

## 2. 仓库结构与职责

### 2.1 前端

路径：

- `front/`

核心职责：

- 页面路由与交互
- 登录态管理
- 网盘 UI 与缓存
- 快传发/收流程
- 管理台前端
- 生产环境 API 基址拼装与调用
- Android WebView 壳的静态资源承载与 Capacitor 同步

关键入口：

- `front/src/App.tsx`
- `front/src/MobileApp.tsx`
- `front/src/main.tsx`
- `front/src/lib/api.ts`
- `front/src/components/layout/Layout.tsx`
- `front/capacitor.config.ts`
- `front/android/`

主要页面：

- `front/src/pages/Login.tsx`
- `front/src/pages/Overview.tsx`
- `front/src/pages/Files.tsx`
- `front/src/pages/Transfer.tsx`
- `front/src/pages/TransferReceive.tsx`
- `front/src/pages/FileShare.tsx`
- `front/src/mobile-pages/*`

### 2.2 后端

路径：

- `backend/`

核心职责：

- 认证与 JWT 鉴权
- 网盘元数据与文件流转
- 快传信令与会话状态
- 管理台 API
- S3 兼容对象存储 / 本地存储抽象

后端包结构：

- `com.yoyuzh.auth`
- `com.yoyuzh.files.core`
- `com.yoyuzh.files.upload`
- `com.yoyuzh.files.share`
- `com.yoyuzh.files.search`
- `com.yoyuzh.files.events`
- `com.yoyuzh.files.tasks`
- `com.yoyuzh.files.storage`
- `com.yoyuzh.files.policy`
- `com.yoyuzh.transfer`
- `com.yoyuzh.admin`
- `com.yoyuzh.config`
- `com.yoyuzh.common`

启动类：

- `backend/src/main/java/com/yoyuzh/PortalBackendApplication.java`

### 2.3 文档与脚本

- `docs/`: 实现计划与补充文档
- `docs/agents/`: 补充性的 agent / handoff 文档；根目录 `CLAUDE.md` 与 `AGENTS.md` 仍是入口
- `scripts/`: 前端静态站发布、对象存储迁移和本地辅助脚本

## 3. 模块划分

### 3.1 认证模块

核心文件：

- `backend/src/main/java/com/yoyuzh/auth/AuthController.java`
- `backend/src/main/java/com/yoyuzh/auth/AuthService.java`
- `backend/src/main/java/com/yoyuzh/auth/JwtTokenProvider.java`
- `backend/src/main/java/com/yoyuzh/config/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/yoyuzh/auth/RefreshTokenService.java`

职责：

- 注册、登录、刷新登录态
- 用户资料查询和修改
- 用户自行修改密码
- 头像上传
- 按客户端类型拆分的登录会话控制
- 邀请码消费与轮换

关键实现说明：

- access token 使用 JWT
- refresh token 持久化到数据库
- 当前会话通过“客户端类型 + 会话 ID”绑定：JWT 同时携带 `sid` 和 `client` claim
- 用户表分别记录桌面端与移动端活跃会话；桌面端仍同步回写旧的 `activeSessionId` 以兼容存量逻辑
- 同账号现在允许桌面端与移动端同时在线，但同一端类型再次登录仍会挤掉旧会话
- 当前密码策略统一为“至少 8 位且包含大写字母”

### 3.2 网盘模块

核心文件：

- `backend/src/main/java/com/yoyuzh/files/core/FileController.java`
- `backend/src/main/java/com/yoyuzh/files/core/FileService.java`
- `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
- `backend/src/main/java/com/yoyuzh/files/share/ShareV2Service.java`
- `backend/src/main/java/com/yoyuzh/files/search/FileSearchService.java`
- `backend/src/main/java/com/yoyuzh/files/events/FileEventService.java`
- `backend/src/main/java/com/yoyuzh/files/tasks/BackgroundTaskService.java`
- `backend/src/main/java/com/yoyuzh/files/policy/StoragePolicyService.java`
- `backend/src/main/java/com/yoyuzh/api/v2/files/UploadSessionV2Controller.java`
- `backend/src/main/java/com/yoyuzh/files/storage/*`
- `front/src/pages/Files.tsx`

职责：

- 文件/文件夹上传、下载、删除、重命名
- 目录创建与分页列表
- 移动、复制
- 回收站列表、恢复与过期清理
- 分享链接与导入
- 前端树状目录导航

关键实现说明：

- `com.yoyuzh.files` 已按职责拆成 `core/upload/share/search/events/tasks/storage/policy` 八个子包，控制器路径、数据库表结构、接口路径和前端调用方式保持不变；这次调整只做包重组与引用修正，不改业务语义
- 文件元数据在数据库
- 文件内容通过独立 `FileBlob` 实体映射到底层对象；`StoredFile` 只负责用户、目录、文件名、路径、分享关系等逻辑元数据
- 新文件的物理对象 key 使用全局 `blobs/...` 命名，不再把 `userId/path` 编进对象 key
- 支持本地磁盘和 S3 兼容对象存储
- 分享导入与网盘复制会直接复用源文件的 `FileBlob`，不会再次写入字节内容
- 文件重命名、移动只更新 `StoredFile` 元数据，不会移动底层对象
- 删除文件时不会立刻物理删除，而是把 `StoredFile` 及其目录树标记为回收站条目；根条目会记录 `deletedAt`、原始父路径和回收分组 ID，回收站保留期固定为 10 天
- 回收站恢复会把整组条目恢复到原路径，并在恢复前检查同名冲突和用户剩余配额
- 定时清理任务会删除超过 10 天的回收站条目；只有当某个 `FileBlob` 的最后一个逻辑引用随之消失时，才真正删除底层对象
- 应用启动时会把旧 `portal_file.storage_name` 行自动回填到新的 `blob_id` 引用，保证存量数据能继续读取
- 当前线上网盘文件存储已切到多吉云对象存储，后端先通过多吉云临时密钥 API 换取短期 S3 会话，再访问底层 COS 兼容桶
- v2 上传会话后端现已按默认策略能力明确区分三种上传模式：`PROXY`、`DIRECT_SINGLE`、`DIRECT_MULTIPART`。默认 S3 策略会走 `DIRECT_MULTIPART`，在创建会话时初始化 `multipartUploadId`，分片上传通过预签名 `UploadPart` 直传对象存储，完成时先提交 multipart complete，再复用旧 `FileService.completeUpload()` 落库；若默认策略 `directUpload=true` 但 `multipartUpload=false`，则通过 `GET /api/v2/files/upload-sessions/{sessionId}/prepare` 返回整文件直传信息；若 `directUpload=false`，则通过 `POST /api/v2/files/upload-sessions/{sessionId}/content` 走代理上传。当前会话响应还会附带 `strategy`，把当前模式下应调用的后续接口模板显式返回给前端，减少前端自己硬编码 `uploadMode -> endpoint` 映射
- 前端 files 子系统上传入口现已消费这套 v2 upload session：桌面端 `FilesPage`、移动端 `MobileFilesPage` 和 `saveFileToNetdisk()` 统一通过共享 helper 按 `uploadMode + strategy` 自动选路，并在 multipart 模式下逐片调用 `prepare -> direct upload -> record -> complete`；因此网盘上传主链路已经不再依赖旧 `/api/files/upload/**`
- 前端会缓存目录列表和最后访问路径
- 桌面网盘页在左侧树状目录栏底部固定展示回收站入口；移动端在网盘页顶部提供回收站入口；两端共用独立 `RecycleBin` 页面调用 `/api/files/recycle-bin` 与恢复接口

Android 壳补充说明：

- Android 客户端当前使用 Capacitor 直接承载 `front/dist`，不单独维护原生业务页面
- 当前包名是 `xyz.yoyuzh.portal`
- 前端 API 基址在 Web 与 Android 壳上分开解析：网页继续走相对 `/api`，Capacitor `localhost` 壳在 `http://localhost` 与 `https://localhost` 下都默认改走 `https://api.yoyuzh.xyz/api`
- 后端 CORS 默认放行 `http://localhost`、`https://localhost`、`http://127.0.0.1`、`https://127.0.0.1` 与 `capacitor://localhost`，以兼容 Web 开发环境和 Android WebView 壳
- Web 端构建完成后，通过 `npx cap sync android` 把静态资源复制到 `front/android/app/src/main/assets/public`
- Android 调试包当前通过 `cd front/android && ./gradlew assembleDebug` 生成，输出路径是 `front/android/app/build/outputs/apk/debug/app-debug.apk`
- 仓库根目录已提供一键脚本 `node scripts/deploy-android-apk.mjs`，会串起前端构建、Capacitor 同步、Gradle 打包、前端静态站发布与 Android 独立发包，并在 `cap sync` 之后自动补回 Android 插件工程里的 Google Maven 镜像配置
- `node scripts/deploy-android-release.mjs` 会把 APK 和 `android/releases/latest.json` 上传到 Android 独立对象路径；默认复用文件桶 scope，不再写入前端静态桶
- 前端总览页在 Web 环境下不再直接指向静态桶里的 APK，而是跳到后端公开下载入口 `https://api.yoyuzh.xyz/api/app/android/download`
- Capacitor 原生壳内的移动端总览页会改为“检查更新”入口；前端通过后端 `/api/app/android/latest` 获取更新信息，后端从文件桶里的 `android/releases/latest.json` 读取版本元数据，并返回带版本号的后端下载地址；真正下载时由 `/api/app/android/download` 直接回传 APK 字节流
- 私有网盘里的 `apk/ipa` 不再直接暴露对象存储默认域名，也不直接暴露长期有效的自定义域名直链；后端会返回短时 `https://api.yoyuzh.xyz/_dl/...` 下载地址，由 `api.yoyuzh.xyz` 上的 Nginx `secure_link` 做签名和过期校验，再代理到 `dl.yoyuzh.xyz`
- 由于当前开发机直连 `dl.google.com` 与 Google Android Maven 仓库存在 TLS 握手失败，本地 Android 构建仓库源已切到可访问镜像；如果后续重新生成 Capacitor 工程，需要重新确认镜像配置仍存在

### 3.3 快传模块

核心文件：

- `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`
- `backend/src/main/java/com/yoyuzh/transfer/TransferService.java`
- `backend/src/main/java/com/yoyuzh/transfer/TransferSession.java`
- `front/src/pages/Transfer.tsx`
- `front/src/pages/TransferReceive.tsx`
- `front/src/lib/transfer-runtime.ts`
- `front/src/lib/transfer-protocol.ts`

职责：

- 创建快传会话
- 生成取件码与分享链接
- WebRTC 信令交换
- 浏览器端文件发送与接收
- 接收后下载或存入网盘

关键实现说明：

- 后端只做信令和会话状态，不中转文件内容
- 文件内容走浏览器 DataChannel
- 接收端支持部分文件选择
- 多文件或文件夹可走 ZIP 下载
- 在线快传是一次性浏览器 P2P 传输，首个接收者进入后即占用该会话
- 离线快传会把文件内容落到站点存储，线上环境使用多吉云对象存储，默认保留 7 天并支持重复接收
- 登录页提供直达快传入口；匿名用户允许创建在线快传、接收在线快传和接收离线快传，离线快传的发送以及“存入网盘”仍要求登录
- 已登录发送端可在快传页查看自己未过期的离线快传记录，并重新打开取件码 / 二维码 / 分享链接详情弹层
- 生产环境当前已经部署 `GET /api/transfer/sessions/offline/mine`，用于驱动“我的离线快传”列表
- 前端默认内置 STUN 服务器，并支持通过 `VITE_TRANSFER_ICE_SERVERS_JSON` 追加 TURN / ICE 配置；未配置 TURN 时，跨运营商或手机蜂窝网络下的在线 P2P 直连不保证成功

### 3.4 管理台模块

核心文件：

- `backend/src/main/java/com/yoyuzh/admin/AdminController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminService.java`
- `front/src/admin/*`

职责：

- 管理用户
- 管理文件
- 查看邀请码
- 展示总存储量、下载流量、今日请求次数、快传使用量、离线快传占用、请求折线图和最近 7 天上线记录
- 调整离线快传总上限

关键实现说明：

- 管理台依赖后端 summary/users/files 接口
- 当前邀请码由后端返回给管理台展示
- 用户列表会展示每个用户的已用空间 / 配额
- 管理员修改用户密码后，旧密码应立即失效，新密码可直接重新登录
- 管理台当前已可查看、新增、编辑并启停非默认 `StoragePolicy`，也可创建 `STORAGE_POLICY_MIGRATION` 后台任务；策略能力继续以结构化 `StoragePolicyCapabilities` 持久化和回显。当前迁移任务会在“当前活动存储后端”内复制对象数据到新的 target-policy object key、更新 `FileBlob/FileEntity.VERSION` 元数据，并在事务提交后清理旧对象；但仍不支持跨不同运行时后端类型的真正 provider 级迁移。默认策略切换和策略删除仍未落地
- JWT 过滤器在受保护接口鉴权成功后，会把当天首次上线的用户写入管理统计表，只保留最近 7 天
- 管理台请求折线图只渲染当天已发生的小时，不再为未来小时补空点

## 4. 关键业务流程

补充说明：

- 前端主入口会在 `main.tsx` 按屏幕宽度选择桌面壳或移动壳
- 当前规则为：宽度小于 `768px` 时渲染 `MobileApp`，否则渲染桌面 `App`
- 移动端 `MobileFiles` 与 `MobileTransfer` 独立维护页面级动态光晕层，视觉上与桌面端网盘/快传保持同一背景语言

### 4.1 登录流程

1. 前端登录页调用 `/api/auth/login`
2. 后端鉴权成功后签发 access token + refresh token
3. 前端同时上送 `X-Yoyuzh-Client` 标记当前是 `desktop` 还是 `mobile`
4. 后端按客户端类型刷新对应的活跃会话 ID 与 refresh token 集合
5. 前端本地存储 `portal-session`
6. 后续请求通过 `Authorization: Bearer <token>` 访问，并继续带上 `X-Yoyuzh-Client`
7. JWT 过滤器校验 token、用户状态，以及当前客户端类型对应的会话 ID 是否仍匹配

补充说明：

- 前端生产构建当前仍会把 API 基址固化为 `https://api.yoyuzh.xyz/api`
- 因此前端登录、刷新、受保护接口访问都依赖 `api.yoyuzh.xyz` 这条独立 API 子域名链路
- 若该子域名在某些网络环境下 TLS/SNI 不稳定，前端会直接表现为“网络异常”或“登录失败”

### 4.2 邀请码注册流程

1. 用户提交注册信息与邀请码
2. 后端验证用户名、邮箱、手机号唯一性
3. 邀请码服务校验当前邀请码
4. 注册成功后自动轮换邀请码
5. 返回登录态

### 4.3 网盘上传流程

1. 前端在 `Files` 页面选择文件或文件夹
2. 前端优先调用 `/api/files/upload/initiate`
3. 后端为新文件预留一个全局 blob object key（`blobs/...`）并返回给前端
4. 如果存储支持直传，则浏览器直接把字节上传到该 blob key
5. 前端再调用 `/api/files/upload/complete`
6. 如果直传失败，会回退到代理上传接口 `/api/files/upload`
7. 后端创建 `FileBlob`，再创建指向该 blob 的 `StoredFile`

### 4.4 文件分享流程

1. 登录用户创建分享链接
2. 后端生成 token
3. 公开用户通过 `/share/:token` 查看详情
4. 登录用户导入时会新建自己的 `StoredFile`
5. 若源对象是普通文件，则新条目直接复用源 `FileBlob`，不会复制物理内容

### 4.5 快传流程

1. 发送端可在登录后或未登录状态下创建在线快传会话
2. 若是在线模式，后端返回 `sessionId + pickupCode` 并保留 15 分钟的一次性会话
3. 接收端通过取件码或分享链接加入在线会话
4. 双方通过 `/api/transfer/.../signals` 交换 offer / answer / ice
5. DataChannel 建立后传输文件内容
6. 接收端可直接下载或存入网盘

### 4.6 离线快传流程

1. 发送端登录后创建离线快传会话
2. 后端生成 `sessionId + pickupCode`，并为每个文件创建离线存储槽位
3. 发送端把文件上传到站点存储
4. 上传完成后，会话变为可接收状态并保留 7 天
5. 接收端通过取件码或分享链接打开会话
6. 接收端可直接下载离线文件，也可登录后存入网盘
7. 文件在有效期内不会因一次接收而被删除，过期后由后端清理任务自动销毁

补充说明：

- 离线快传只有“创建会话 / 上传文件 / 存入网盘”要求登录；匿名用户可以查找、加入和下载离线快传
- 匿名用户进入 `/transfer` 时默认落在发送页，但仅会看到在线模式
- 登录用户可通过 `/api/transfer/sessions/offline/mine` 拉取自己仍在有效期内的离线快传会话，用于在快传页回看历史取件信息

### 4.7 管理员改密流程

1. 管理台调用 `PUT /api/admin/users/{userId}/password`
2. 后端按统一密码规则校验新密码
3. 后端重算密码哈希并写回用户表
4. 后端刷新桌面端与移动端全部活跃会话，并撤销该用户全部 refresh token
5. 旧密码后续登录应失败，新密码登录成功

## 5. 前端路由架构

路由入口：

- `front/src/App.tsx`

主要路由：

- `/login`
- `/overview`
- `/files`
- `/transfer`
- `/share/:token`
- `/admin/*`

说明：

- `/transfer` 同时承担发送端和接收端入口
- `/share/:token` 是公开文件分享页
- `/admin/*` 为懒加载管理台

## 6. 安全模型

### 6.1 访问控制

由 `SecurityConfig` 控制：

- `/api/auth/**` 公开
- `/api/transfer/**` 公开
- `GET /api/files/share-links/{token}` 公开
- `/api/files/**`、`/api/user/**`、`/api/admin/**` 需登录

### 6.2 分端单会话登录

当前实现不是只撤销 refresh token，而是同时控制 access token，并按客户端类型拆分：

- 前端会在鉴权与上传请求里附带 `X-Yoyuzh-Client: desktop|mobile`
- 用户表记录 `desktopActiveSessionId` 与 `mobileActiveSessionId`
- JWT 里同时包含 `sid` 和 `client`
- 过滤器每次请求都会按 token 里的 `client` 去比对对应端的活跃会话 ID
- 桌面端与移动端可以同时在线，但同一端再次登录成功后，该端旧 token 会失效

## 7. 存储架构

抽象层：

- `backend/src/main/java/com/yoyuzh/files/storage/FileContentStorage.java`

实现方向：

- 本地文件系统
- S3 兼容对象存储

设计目的：

- 让文件元数据逻辑与底层存储解耦
- 上传、下载、复制、移动都通过统一抽象收口

当前线上状态：

- 生产环境文件桶已切到多吉云对象存储
- 后端通过多吉云临时密钥 API 获取短期 `accessKeyId / secretAccessKey / sessionToken`
- 实际对象访问走 S3 兼容协议，底层 endpoint 为 COS 兼容地址
- 普通文件下载仍采用“后端鉴权后返回签名 URL，浏览器直连对象存储下载”的主链路
- 私有 `apk/ipa` 下载是例外：后端只负责返回短时签名的 `/_dl` 地址，真正文件流量经过服务器 Nginx 反向代理到 `dl.yoyuzh.xyz`，不经过 Spring Boot 业务进程

## 8. 部署架构

### 8.1 前端

- 构建工具：Vite
- 发布方式：对象存储静态站发布
- 发布脚本：`node scripts/deploy-front-oss.mjs`

### 8.2 后端

- 打包方式：`mvn package`
- 产物：`backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar`
- 线上通常采用 jar + systemd 方式运行

当前已知线上信息：

- 服务名：`my-site-api.service`
- 运行包路径：`/opt/yoyuzh/yoyuzh-portal-backend.jar`
- 额外配置文件：`/opt/yoyuzh/application-prod.yml`
- 环境变量文件：`/opt/yoyuzh/app.env`
- 2026-04-02 已重新部署，服务状态为 `active (running)`

## 9. 开发注意事项

- 仓库根目录没有 `package.json`，不要在根目录执行 `npm`
- 前端命令只从 `front/package.json` 读取
- 后端命令只从 `backend/pom.xml` 读取
- 根目录 `.env` 是当前统一的本地密钥与部署配置入口；`.env.example` 是模板，旧 `.env.oss.local` 仅保留兼容回退
- 前端 `npm run lint` 实际是 `tsc --noEmit`
- 后端没有单独 lint 命令
- 本仓库大量使用 Lombok，VS Code 若出现“final 字段未初始化”之类误报，优先检查 Lombok 扩展、Java Language Server 和 annotation processor

## 10. 新窗口建议阅读顺序

后续新窗口进入仓库时，建议顺序：

1. `memory.md`
2. `docs/architecture.md`
3. `docs/api-reference.md`
4. `AGENTS.md`
5. `CLAUDE.md`

如果要继续某个具体功能，再进入对应模块的：

- 前端页面文件
- 后端 Controller / Service
- 紧邻测试文件

如果需要额外的交接背景，再补读：

- `docs/agents/handoff.md`
## 2026-04-08 API v2 阶段 1 补充

- 后端新增 `com.yoyuzh.api.v2` 作为新版 API 的独立边界，当前只暴露公开健康检查 `GET /api/v2/site/ping`。
- v2 边界使用独立的 `ApiV2Response`、`ApiV2ErrorCode` 和 `ApiV2ExceptionHandler`，暂不替换旧 `com.yoyuzh.common.ApiResponse`。
- 前端 `front/src/lib/api.ts` 通过 `apiV2Request()` 访问 `/api/v2/**`，并为内部 API 请求附带稳定的 `X-Yoyuzh-Client-Id`，用于后续文件事件流和客户端事件去重。

## 2026-04-08 文件实体模型二期第一小步

- `StoredFile` 仍是用户可见文件/目录元数据的主模型，现阶段继续保留 `blob_id` 读取路径。
- 新增 `FileEntity` 作为更通用的物理实体模型，当前先从 `FileBlob` 回填 `VERSION` 类型实体；后续版本、缩略图、转码、头像等派生对象会挂到同一实体体系。
- 新增 `StoredFileEntity` 作为逻辑文件和物理/派生实体的关系表；当前只写入 `PRIMARY` 关系，不切换旧业务读写。
- `FileEntityBackfillService` 在 `FileBlobBackfillService` 之后运行，只处理 `blob` 已存在但 `primaryEntity` 为空的普通文件，保证重复启动不会重复迁移已完成行。

## 2026-04-08 文件实体模型二期第二小步

- 文件写入路径已经从“只写 `FileBlob`”扩展为“继续写 `FileBlob`，同时写 `FileEntity.VERSION` 和 `StoredFileEntity(PRIMARY)`”。覆盖普通上传、直传完成、外部导入、分享导入和网盘复制。
- `StoredFile.blob` 仍是当前生产读取路径；`StoredFile.primaryEntity` 与关系表暂时只作为兼容迁移数据，不影响旧 `/api/files/**` DTO 和前端调用。
- `portal_stored_file_entity.stored_file_id` 随 `portal_file` 删除级联清理；`portal_file_entity.created_by` 在用户删除时置空，避免实体审计关系阻塞用户清理。
- 2026-04-08 阶段 3 第一小步补充：后端新增上传会话二期最小骨架。`UploadSession` 记录用户、目标路径、文件名、对象键、分片大小、分片数量、状态、过期时间和已上传分片占位 JSON；`/api/v2/files/upload-sessions` 目前只提供创建、查询、取消会话，不承接实际分片内容上传，也不替换旧 `/api/files/upload/**` 生产链路。
- 2026-04-08 阶段 3 第二小步补充：上传会话新增完成状态机。`UploadSessionService.completeOwnedSession()` 会复用旧 `FileService.completeUpload()` 完成对象确认、目录补齐、配额/冲突校验和 `FileBlob + StoredFile + FileEntity.VERSION` 双写落库，然后把会话标记为 `COMPLETED`；失败时标记 `FAILED`，过期时标记 `EXPIRED`。当时仍没有独立 v2 分片内容写入端点。
- 2026-04-08 阶段 3 第三小步补充：上传会话新增 part 状态记录。`UploadSessionService.recordUploadedPart()` 会校验会话归属、状态、过期时间和 part 范围，把 `etag/size/uploadedAt` 写入 `uploadedPartsJson`，并将新会话推进到 `UPLOADING`。当前实现是会话状态跟踪，不是跨存储驱动的分片内容写入/合并实现。
- 2026-04-08 阶段 3 第四小步补充：上传会话新增定时过期清理。`UploadSessionService.pruneExpiredSessions()` 每小时扫描未完成且已过期的 `CREATED/UPLOADING/COMPLETING` 会话，尝试删除 `objectKey` 对应的临时 blob，然后标记为 `EXPIRED`。已完成文件不参与清理，避免误删已经落库的生产对象。
- 2026-04-08 阶段 4 第一小步补充：后端新增存储策略骨架。`StoragePolicyService` 作为 `CommandLineRunner` 在启动时确保存在默认策略，并把当前 `FileStorageProperties` 映射为 `LOCAL` 或 `S3_COMPATIBLE` 策略及 `StoragePolicyCapabilities` JSON；当时能力声明里的 `multipartUpload=false` 用于明确真实对象存储分片写入/合并还没有启用。`UploadSession.storagePolicyId` 开始记录默认策略 ID，但旧 `/api/files/**` 生产路径当时仍不切换。
- 2026-04-08 `files/storage` 合并补充：S3 存储实现拆出多吉云临时密钥客户端与运行期会话提供器。`S3FileContentStorage` 现在通过 `S3SessionProvider.currentSession()` 获取当前 bucket、`S3Client` 和 `S3Presigner`，避免每次操作重复内联多吉云 token 解析逻辑；测试环境可直接注入 mock S3 client/presigner。当时该改动还没有引入 multipart，仍是单对象 PUT/HEAD/GET/COPY/DELETE 路径。
- 2026-04-08 阶段 4 第二小步补充：`FileService` 在创建新的 `FileEntity.VERSION` 时会通过 `StoragePolicyService.ensureDefaultPolicy()` 写入默认 `storagePolicyId`；`FileEntityBackfillService` 对历史 `FileBlob` 回填新实体时也写入同一默认策略。复用已有实体时保持原策略字段不变，只增加引用计数，避免在兼容迁移阶段覆盖历史数据。
- 2026-04-08 阶段 4 第三小步补充：管理台新增只读存储策略列表。`AdminController` 暴露 `GET /api/admin/storage-policies`，`AdminService` 通过白名单 DTO 返回策略基础字段和结构化 `StoragePolicyCapabilities`；前端 `react-admin` 新增 `storagePolicies` 资源展示能力矩阵。该能力只做配置可视化，不改变旧上传下载路径，也不暴露凭证或提供策略编辑能力。
- 2026-04-09 存储策略管理补充：`AdminController` 现已补 `POST /api/admin/storage-policies`、`PUT /api/admin/storage-policies/{policyId}`、`PATCH /api/admin/storage-policies/{policyId}/status` 和 `POST /api/admin/storage-policies/migrations`。当前允许新增、编辑、启停非默认策略，并沿用 `StoragePolicyCapabilities` 作为强类型能力声明；迁移接口会为管理员创建 `STORAGE_POLICY_MIGRATION` 后台任务，worker 只校验源/目标策略并重算候选 `FileEntity.VERSION` / `StoredFile` 数量，不直接移动对象数据。默认策略仍不能被停用，也还不支持删除策略或切换默认策略。
- 2026-04-09 存储策略迁移补充：`StoragePolicyMigrationBackgroundTaskHandler` 现在会在当前活动存储后端内执行真实对象迁移。它要求源/目标策略类型一致且与运行时后端匹配，复制旧 object key 的字节内容到新的 `policies/{targetPolicyId}/blobs/...` key，更新 `FileBlob.objectKey` 与 `FileEntity.VERSION.storagePolicyId/objectKey`，并在事务提交后清理旧对象；若中途失败，会删除本轮新写对象，依赖事务回滚数据库状态。
- 2026-04-09 上传会话二期补充：`FileContentStorage` 抽象已新增 `createMultipartUpload/prepareMultipartPartUpload/completeMultipartUpload/abortMultipartUpload`；`S3FileContentStorage` 基于预签名 `UploadPart` 与 S3 `Complete/AbortMultipartUpload` 实现真实 multipart。`UploadSession` 新增 `multipartUploadId`，`UploadSessionService.createSession()` 会在默认策略声明 `multipartUpload=true` 时初始化 uploadId，并通过 `GET /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}/prepare` 返回单分片直传地址。会话完成时先按 `uploadedPartsJson` 提交 multipart complete，再复用旧上传完成链路落库；过期清理则改为优先 abort 未完成 multipart。

## 2026-04-08 阶段 5 文件搜索第一小步

- 后端新增 `FileMetadata` 与 `FileMetadataRepository`，作为后续标签、缩略图状态、媒体属性和自定义属性的统一扩展表骨架。当前阶段只建表与仓储入口，不迁移现有回收站字段，也不改变旧 `/api/files/**` DTO。
- 后端新增 `FileSearchService` 与 `GET /api/v2/files/search`，按当前登录用户查询未删除的 `StoredFile`，支持文件名、文件/目录类型、大小、创建时间、更新时间和分页过滤。
- 搜索结果复用现有 `FileMetadataResponse`，因此旧网盘列表、下载、分享、回收站和上传链路不受影响；前端用户侧搜索 UI 和 metadata/tag 过滤留到后续小步接入。

## 2026-04-08 阶段 5 文件搜索第二小步

- 前端桌面端新增独立搜索模式：`front/src/lib/file-search.ts` 复用 `apiV2Request('/files/search', ...)`，并在 `front/src/lib/file-search.test.ts` 覆盖参数编码、空参数跳过和 v2 数据解包。
- `front/src/pages/Files.tsx` 同时保留目录视图和搜索结果视图，搜索结果不写入 `getFilesListCacheKey(...)`，也不影响原有目录缓存和上传主链路；移动端文件页暂未接入搜索。

## 2026-04-08 阶段 5 分享二期后端最小骨架

- 旧分享仍保留在 `/api/files/share-links/**`，用于兼容当前前端公开分享页和旧导入路径。
- 新 v2 分享位于 `com.yoyuzh.api.v2.shares` 与 `ShareV2Service`；`FileShareLink` 新增 `passwordHash`、`expiresAt`、`maxDownloads`、`downloadCount`、`viewCount`、`allowImport`、`allowDownload`、`shareName` 策略字段。
- 公开端点包括 `GET /api/v2/shares/{token}`、`POST /api/v2/shares/{token}/verify-password`，以及 `GET /api/v2/shares/{token}?download=1`；创建、导入、我的分享列表和删除仍需要登录。
- 密码分享在校验前隐藏 `file` 详情；v2 导入会在复用旧导入落库链路前校验过期时间、密码、`allowImport` 和 `maxDownloads`。v2 下载也会统一校验过期时间、密码、`allowDownload` 和 `maxDownloads`，成功后复用现有文件下载链路并递增 `downloadCount`。

## 2026-04-08 阶段 5 文件事件流最小闭环

- 后端新增 `FileEvent` / `FileEventType` / `FileEventRepository` / `FileEventService`，并暴露受保护的 `GET /api/v2/files/events` SSE 入口。
- 当前事件流以用户为广播边界，支持 `path` 前缀过滤和 `X-Yoyuzh-Client-Id` 自身事件抑制；首次连接会收到 `READY` 事件。
- `FileService` 只在上传、导入、复制、移动、重命名、删除、恢复这些核心变更点记录最小事件。
- 前端新增 `front/src/lib/file-events.ts`，通过 fetch stream 复用鉴权和 `X-Yoyuzh-Client-Id` 请求头，不直接使用原生 `EventSource`；桌面 `Files` 与移动 `MobileFiles` 已在当前目录订阅事件，收到变更后失效当前目录缓存并刷新列表。

## 2026-04-08 阶段 6 任务框架与 worker 后端最小骨架

- 后端新增 `BackgroundTask` / `BackgroundTaskType` / `BackgroundTaskStatus` / `BackgroundTaskRepository` / `BackgroundTaskService`，用于承载后续压缩、解压、缩略图、媒体元数据和清理类后台工作。
- 新增受保护的 `/api/v2/tasks/**`：`GET /api/v2/tasks`、`GET /api/v2/tasks/{id}`、`DELETE /api/v2/tasks/{id}`、`POST /api/v2/tasks/{id}/retry`，以及 `POST /api/v2/tasks/archive`、`POST /api/v2/tasks/extract`、`POST /api/v2/tasks/media-metadata` 创建接口。
- 任务创建入口集中在 `BackgroundTaskService` 校验 `StoredFile`：`fileId` 必须属于当前用户且未删除，请求 `path` 必须匹配由 `StoredFile.path + filename` 派生的真实逻辑路径；`ARCHIVE` 允许文件和目录，`EXTRACT` 当前只允许 zip-compatible 文件（`.zip/.jar/.war` 或 zip/java archive 内容类型），`MEDIA_META` 仅允许媒体类文件。任务 public/private state 使用服务端派生的 `fileId`、`path`、`filename`、`directory`、`contentType`、`size`；其中 `ARCHIVE` 还会写入 `outputPath/outputFilename`，`EXTRACT` 会写入 `outputPath/outputDirectoryName`。
- 当前实现新增了 worker 调度与多实例 lease：定时先回收 lease 已过期的 `RUNNING` 任务，再扫描少量 `QUEUED` 任务，通过状态条件更新完成 claim，并写入持久化 `leaseOwner/leaseExpiresAt/heartbeatAt` 与公开 `workerOwner/heartbeatAt/leaseExpiresAt/startedAt`。运行中所有 progress/完成/失败更新都要求 owner 匹配，丢失 lease 的旧 worker 不会覆盖新状态。
- `MEDIA_META` 任务会进入独立 handler 写入基础媒体元数据与图片宽高，并在公开 state 写入 `metadataStage`；`ARCHIVE` 任务会调用 `FileService.buildArchiveBytes(...)` 生成 zip 并回写同级目录；`EXTRACT` 任务会读取 zip-compatible 归档、剥离共享根目录或把单文件直接恢复到父目录，再通过 `FileService.importExternalFilesAtomically(...)` 做预检、批量导入和失败 blob 清理。
- `BackgroundTaskService` 还会在 `publicStateJson` 里统一维护最小进度阶段 `phase`：创建时是 `queued`，claim 后进入 `running`，worker 开始执行时按任务类型细化成 `archiving` / `extracting` / `extracting-metadata`，完成/失败/取消时再收口为 `completed` / `failed` / `cancelled`。
- `ARCHIVE` 与 `EXTRACT` 任务现在会在运行和完成阶段暴露真实条目计数：`processedFileCount/totalFileCount`、`processedDirectoryCount/totalDirectoryCount`，并基于真实总量计算 `progressPercent`。其中 `ARCHIVE` 按实际写入 zip entry 推进，`EXTRACT` 按实际创建目录和导入文件推进；`MEDIA_META` 则暴露阶段型 `metadataStage`。
- 当前 `POST /api/v2/tasks/{id}/retry` 已支持最小手动重试：只有 `FAILED` 任务可以被当前用户重置回 `QUEUED`，并清空 `finishedAt/errorMessage`，按 `privateStateJson` 重建公开 state，同时把 `attemptCount` 重置回 0。
- `BackgroundTaskStartupRecovery` 现在只会在服务启动完成后回收 lease 已过期或历史上缺少 lease 的 `RUNNING` 任务，恢复时按 `privateStateJson` 重建公开 state；不会再无条件重排所有 `RUNNING` 任务。
- worker 现在会按失败分类和任务类型做自动重试：失败会归到 `UNSUPPORTED_INPUT`、`DATA_STATE`、`TRANSIENT_INFRASTRUCTURE`、`RATE_LIMITED`、`UNKNOWN`；其中 `ARCHIVE` 默认最多 4 次、`EXTRACT` 最多 3 次、`MEDIA_META` 最多 2 次，公开 state 会暴露 `attemptCount/maxAttempts/retryScheduled/nextRetryAt/retryDelaySeconds/lastFailureMessage/lastFailureAt/failureCategory`。
- 当前仍不包含非 zip 解压格式、缩略图/视频时长任务，以及 archive/extract 的前端入口。
- 桌面端 `front/src/pages/Files.tsx` 已接入最近 10 条后台任务查看与取消入口，并可为当前选中文件创建 `MEDIA_META` 任务；移动端与 archive/extract 的前端入口仍未接入。
## 11. UI 视觉系统与主题引擎 (2026-04-10 升级)

### 11.1 设计语言：Stitch Glassmorphism

全站视觉系统已全面转向“Stitch”玻璃拟态 (Glassmorphism) 风格，其核心特征包括：

- **全局背景 (Aurora)**：在 `index.css` 中定义了 `bg-aurora`，结合颜色渐变与动态光晕产生深邃的底色。
- **玻璃面板 (.glass-panel)**：核心 UI 容器均使用半透明背景 (`bg-white/40` 或 `bg-black/40`)、高饱和背景模糊 (`backdrop-blur-xl`) 和细腻的白色线条边框 (`border-white/20`)。
- **浮动质感**：通过 `rounded-3xl` 或 `rounded-[2rem]` 的大圆角和外阴影增强层叠感。

### 11.2 主题管理 (Theme Engine)

系统内建了一套完整的主题上下文，主要路径为：

- `front/src/components/ThemeProvider.tsx`：提供 `light | dark | system` 主题状态切换与持久化，通过操作 `html` 根节点的 `class` 实现。
- `front/src/components/ThemeToggle.tsx`：全局主题切换按钮组件。
- `front/src/lib/utils.ts`：提供 `cn()` 工具函数，用于处理 Tailwind 类的动态组合与主题适配。

### 11.3 模块适配情况

- **用户侧**：网盘、快传、分享详情、任务列表、回收站均已完成适配。所有表格、卡片和导航栏均已升级为玻璃态。
- **移动端**：`MobileLayout` 实现了一套悬浮式玻璃顶部标题栏与底部导航栏，并保持与桌面端一致的光晕背景。
- **管理侧**：Dashboard 大盘指标卡片、用户列表、文件审计列表和存储策略列表均已同步升级。

## 12. Redis Foundation (2026-04-10)

- 后端已引入 Spring Data Redis 与 Spring Cache，但 Redis 仍是可选基础设施：`app.redis.enabled=false` 时，应用会回退到 no-op token 失效服务与 `NoOpCacheManager`，本地与 dev 环境不需要外部 Redis 也能正常启动与测试。
- Redis 配置拆成两层：
  - `spring.data.redis.*`：连接参数。
  - `app.redis.*`：业务 key prefix、TTL buffer、cache TTL 与命名空间。
- 当前声明的 Redis 命名空间包括：`cache`、`auth`、`transfer-sessions`、`upload-state`、`locks`、`file-events`、`broker`。本轮真正落地使用的是 `auth`，其余属于后续 Stage 1 边界预留。
- 当前声明的 Spring Cache 名称包括：`files:list`、`admin:summary`、`admin:storage-policies`、`android:release`。本轮只完成了缓存边界与 TTL 骨架，尚未把具体读路径接到这些 cache。
- 认证链路新增 Redis 失效层：
  - access token：按 `userId + clientType` 记录“在此时间点之前签发的 token 失效”。
  - refresh token：按 token hash 写入黑名单，TTL 与剩余有效期对齐。
- `JwtAuthenticationFilter` 现在会先检查 access token 是否已被 Redis 失效层拒绝，再继续执行原有的 JWT 校验、用户加载与 `sid` 会话匹配。
- `AuthService` 与 `AdminService` 的同端重登、改密、封禁、管理员重置密码路径，现已统一调用这层服务；`RefreshTokenService` 在轮换、过期拒绝与批量撤销时也会同步刷新 refresh token 黑名单。
## 12.1 Redis Foundation Batch 2 (2026-04-10)

- `FileService.list(...)` 现已通过 `FileListDirectoryCacheService` 接入可选 Redis 热目录缓存，当前只缓存 `/api/files/list` 的目录分页结果，不混入搜索、回收站或后台任务列表。
- 热目录缓存使用 `files:list` Spring Cache 命名空间，真实缓存 key 由 `userId + normalized path + page + size + fixed sort context + directory version` 组成；目录版本存放在 Redis KV 中，按目录粒度增量失效，避免全局清空。
- 目录列表失效点已经覆盖 `mkdir`、上传完成、外部导入、回收站删除、回收站恢复、重命名、移动、复制与默认目录补齐，所有变更最终都归一到 `touchDirectoryListings(...)`。
- 分布式锁新增 `DistributedLockService` 抽象与 Redis 实现，当前第一批只落在 `FileService.restoreFromRecycleBin(...)`，锁 key 为 `files:recycle-restore:{fileId}`，通过 `SETNX + TTL + owner token` 获取并用 Lua compare-and-delete 释放。
- 上传会话运行态新增 `UploadSessionRuntimeStateService` 抽象与 Redis 实现，短生命周期状态写入 `upload-state` 命名空间；数据库里的 `UploadSession` 继续承担最终事实，Redis 只承载创建中、上传中、完成中这类运行态快照。
- `UploadSessionV2Controller` 已把运行态映射到响应体 `runtime` 字段，便于前端轮询时直接读取 phase、已上传字节数、分片数、进度百分比与过期时间，而不需要额外拼装临时状态。

## 12.2 Redis Foundation Batch 3 (2026-04-10)

- 轻量 broker 已新增 `LightweightBrokerService` 抽象：Redis 启用时使用 `RedisLightweightBrokerService` 把消息写入 Redis list；Redis 关闭时回退到 `InMemoryLightweightBrokerService`，继续支持本地单实例开发与测试。
- 这层 broker 明确只服务“小规模、低成本、可接受保守语义”的异步触发，不承担高可靠消息系统职责；任务最终状态、重试、幂等与用户可见结果仍以数据库 `BackgroundTask` 为准。
- 当前 broker 使用 `app.redis.namespaces.broker` 命名空间，首个 topic 为 `media-metadata-trigger`，消息体只携带最小触发上下文：`userId`、`fileId`、`correlationId`。
- `FileService.saveFileMetadata(...)` 现在会在媒体文件元数据落库后通过 `MediaMetadataTaskBrokerPublisher` 做 after-commit 发布；非媒体文件、目录、缺少必要主键信息的条目不会进入 broker。
- `MediaMetadataTaskBrokerConsumer` 通过定时 drain 方式消费 broker 消息，并调用 `BackgroundTaskService.createQueuedAutoMediaMetadataTask(...)` 创建 `MEDIA_META` 任务；该入口会先按 `correlationId` 去重，再校验文件仍存在、未删除且仍属于媒体文件，避免重复建任务。
- 这批实现的目标是“让轻量 broker 先承担一类真实异步触发”，而不是替代现有 `BackgroundTask` worker，也不覆盖文件事件跨实例广播；后者仍归 Stage 1 Step 9 处理。

## 12.3 Redis Foundation Batch 4 (2026-04-10)

- 在线快传 session 已从进程内 `ConcurrentHashMap` 提升为可选 Redis 支撑：`TransferSessionStore` 在 Redis 启用时把 session JSON 与 `pickupCode -> sessionId` 映射写入 `transfer-sessions` 命名空间，关闭时自动回退到原有内存模式。
- Redis key 当前按 `session:{sessionId}` 与 `pickup:{pickupCode}` 组织，TTL 与 session `expiresAt` 对齐并附带 `app.redis.ttlBufferSeconds` 缓冲；因此 Redis 只承载在线快传的短生命周期运行态，不替代离线快传数据库模型。
- `TransferSession` 新增内部快照序列化形状，用于保留 `receiverJoined`、信令队列、cursor 和文件清单等运行期状态；`joinSession`、`postSignal` 在修改在线 session 后会重新写回 store，避免 Redis 模式下只改内存副本而不持久化。
- `TransferService.nextPickupCode()` 现在复用 `TransferSessionStore.nextPickupCode()`；Redis 启用时 pickup code 会先在 Redis 映射 key 上做短 TTL 预留，降低多实例并发创建在线快传 session 时的冲突概率。
- 当前 Step 8 只覆盖在线快传 session 的跨实例 lookup/join 基础能力；离线快传仍继续使用 `OfflineTransferSessionRepository`，文件事件广播也仍留在 Step 9。
## 12.4 Redis Foundation Batch 5 (2026-04-10)

- 文件事件跨实例分发现在落地在 Redis pub/sub，而不是把 `SseEmitter` 或订阅状态搬进 Redis。每个实例仍只在本地维护 `userId -> subscriptions` 的内存映射，SSE 过滤逻辑继续由 `FileEventService` 负责。
- `FileEventService.record(...)` 现在仍然先写 `FileEvent` 表；事务提交后会先向本实例订阅者投递，再通过 `FileEventCrossInstancePublisher` 把最小事件快照发布到 `keyPrefix:file-events:pubsub` topic。
- Redis 开启时，`RedisFileEventPubSubPublisher` 会附带当前实例 `instanceId`；`RedisFileEventPubSubListener` 在收到消息后会忽略同实例回环消息，只把远端事件重建成 `FileEvent` 并交回 `FileEventService.broadcastReplicatedEvent(...)` 做本地 SSE 投递。
- 这条链路的目标是“跨实例转发已提交的文件事件”，不是高可靠消息系统：它不重放历史事件，不替代 `FileEvent` 表持久化，也不承担断线补偿；真正的事件审计事实源仍然是数据库。
- Redis 关闭时，`NoOpFileEventCrossInstancePublisher` 会让行为自动回退为原有单实例本地广播，dev 与本地测试环境不需要额外 Redis 也能继续运行。
## 12.5 Redis Foundation Batch 6 (2026-04-10)

- Spring Cache 鍦ㄨ繖涓€鎵规寮忔帴鍏ヤ簡涓ょ被楂樿浣庡啓璇昏矾寰勶細`AdminService.listStoragePolicies()` 浣跨敤 `admin:storage-policies`锛宍AndroidReleaseService.getLatestRelease()` 浣跨敤 `android:release`銆?
- 瀛樺偍绛栫暐鍒楄〃鐨勭紦瀛樺け鏁堢偣鏄槑纭殑绠＄悊鍐欒矾寰勶細鍒涘缓銆佺紪杈戙€佸惎鍋滈兘鍦?`AdminService` 涓婄洿鎺?evict锛屼笉鎶婂叾浠栫敤鎴疯矾寰勬垨鏂囦欢璇昏矾寰勬贩杩涘悓涓€ cache銆?
- Android release metadata 鍒欐槸 TTL 椹卞姩鐨勭紦瀛橈細鏁版嵁婧愪粛鏄璞″瓨鍌ㄧ殑 `android/releases/latest.json`锛屽悗绔彧缂撳瓨鏋勫缓鍚庣殑 `AndroidReleaseResponse`锛屼笉缂撳瓨 APK 鍒嗗彂瀛楄妭娴併€?
- `admin summary` 缁忚瘎浼板悗鏆備笉鎺ュ叆缂撳瓨锛屽洜涓鸿繖涓?DTO 鍚屾椂缁勫悎浜嗛珮棰戝彉鍖栫殑 request metrics銆佹瘡鏃ユ椿璺冪敤鎴风粺璁″拰閭€璇风爜绛夊€硷紝鐩墠娌℃湁涓€涓共鍑€鐨勬樉寮忓け鏁堣竟鐣岄€傚悎鎶婂畠鏀惧叆 Spring Cache銆?
## 12.5 Redis Foundation Batch 6 Clarification (2026-04-10)

- Spring Cache is now active on two high-read, low-write backend read paths.
- `AdminService.listStoragePolicies()` uses cache `admin:storage-policies`.
- `AndroidReleaseService.getLatestRelease()` uses cache `android:release`.
- Storage policy cache invalidation is explicit and tied to admin create, update, and status-change writes.
- Android release metadata uses TTL-based refresh because the source of truth is object storage metadata at `android/releases/latest.json`, updated by the release publish script rather than an in-app write path.
- APK byte streaming remains uncached; only the metadata response is cached.
- `admin summary` remains uncached by design because it mixes several high-churn metrics and does not yet have a clean invalidation boundary.
## 12.6 Redis Foundation Batch 7 Clarification (2026-04-10)

- `DogeCloudS3SessionProvider` intentionally remains a per-instance in-memory cache instead of moving to Redis.
- The cached object is not just raw temporary credentials; it is a live runtime session containing `S3Client` and `S3Presigner`, both of which have local lifecycle and cleanup semantics.
- Because of that, a Redis-backed shared cache would either have to cache only raw credential material and rebuild SDK clients locally anyway, or attempt to share values that are not meaningful across JVM instances.
- The current design keeps refresh ownership local to each backend instance: if cached credentials are still outside the one-minute refresh window, the existing runtime session is reused; once inside that window, the old runtime session is closed and a fresh one is fetched and rebuilt.
- This leaves some duplicate DogeCloud temporary-token fetches in multi-instance deployments, but the current plan judges that cost lower than the added complexity and secret-handling surface of a Redis shared-credential cache.
## 12.7 Redis Foundation Batch 8 Clarification (2026-04-10)

- Stage 1 validation closed with two local checks: full backend test regression and a Redis-disabled `dev` boot-path check.
- The local boot-path check matters because Redis integration is optional by design. With `APP_REDIS_ENABLED=false`, the application still starts as a normal single-instance backend once mandatory base config such as `APP_JWT_SECRET` is present.
- In the validated local path, the backend started successfully on an alternate port (`18081`) under the `dev` profile, using H2 and no Redis dependency.
- Therefore the current architecture boundary remains unchanged: Redis augments cache, pub/sub, lock, broker, and short-lived runtime state when enabled, but it is not a required baseline component for local development or single-instance fallback.
- The architecture still has explicit environment-bound gaps that were not closed in-process: real Redis reliability/TTL observation and cross-instance propagation timing for file events, lightweight broker delivery, upload runtime state, and transfer-session sharing.

## 12.8 Manual Redis Validation Clarification (2026-04-10)

- The later manual validation pass did exercise real local Redis plus two backend instances, so several Stage 1 architecture claims are now locally runtime-validated rather than only unit/integration-tested.
- Verified runtime behaviors:
- auth token invalidation survives cross-instance login churn;
- online transfer runtime state survives loss of the creating instance;
- file events can cross instances through the SSE path when a real uploaded file triggers a `CREATED` event;
- the lightweight broker can auto-create a queued `MEDIA_META` task after a media upload and that task is visible from the peer instance.
- The Redis file list cache architecture also needed one implementation detail clarified: Spring Cache may hand back generic decoded maps from Redis, so `RedisFileListDirectoryCacheService` now treats cache-value reconstruction as an application concern instead of assuming a strongly typed cache provider result.
- The persistence model also still carries `portal_file.storage_name` as a required column in the live schema, so even after blob/entity migration work the backend must continue writing a non-null legacy storage name for directories and uploaded files until a later schema migration explicitly removes that requirement.
- One environment gap remains: local `redis-cli` key inspection did not reveal the expected keys during probing even while cross-instance behavior proved shared runtime state was active. That means the current architectural confidence comes from observable runtime behavior, not from direct local key-space inspection.

## Debugging Discipline

- Use short bounded probes first when validating network, dependency, or startup issues. Prefer commands such as `curl --max-time`, `mvn -q`, `mvn dependency:get`, `apt-get update`, and similar narrow checks before launching long-running downloads or full test runs.
- Do not wait indefinitely on a stalled download or progress indicator. If a command appears stuck, stop and re-check DNS, proxy inheritance, mirror reachability, and direct-vs-proxy routing before retrying.
- For WSL debugging, verify the proxy path and the direct path separately, then choose the shortest working route. Do not assume a mirror problem until the network path has been isolated.
- Use domestic mirrors as a delivery optimization, not as a substitute for diagnosis. First determine whether the failure is caused by DNS, proxy configuration, upstream availability, or the mirror itself.

## 12.9 Admin Backend Surface Clarification (2026-04-11)

- The admin module now covers four distinct backend inspection domains:
- user and summary management;
- logical file management;
- storage policy management and migration task creation;
- operational inspection for file blobs, shares, and background tasks.
- `GET /api/admin/file-blobs` is architected around `FileEntity` plus `StoredFileEntity` relations instead of around `StoredFile` rows. This keeps the admin surface aligned with the newer object/entity model and lets operators inspect storage-policy ownership, reference counts, and missing-object anomalies before the legacy read path is retired.
- `GET /api/admin/shares` and `DELETE /api/admin/shares/{shareId}` sit on top of `FileShareLinkRepository` and are intended as operational controls for share hygiene rather than end-user sharing flows.
- `GET /api/admin/tasks` and `GET /api/admin/tasks/{taskId}` sit on top of `BackgroundTaskRepository` and parse structured fields out of `publicStateJson` so the admin UI can inspect failure category, retry scheduling, worker owner, and lease freshness without re-implementing backend parsing rules.
- This batch does not change the current production read-path boundary: download, share detail, recycle-bin, and zip flows still read from `StoredFile.blob`, while `FileEntity` and `StoredFile.primaryEntity` continue to carry migration-oriented metadata for newer admin and storage-policy workflows.
