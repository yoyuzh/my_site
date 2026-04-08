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
- `com.yoyuzh.files`
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

- `backend/src/main/java/com/yoyuzh/files/FileController.java`
- `backend/src/main/java/com/yoyuzh/files/FileService.java`
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
- 2026-04-08 阶段 3 第二小步补充：上传会话新增完成状态机。`UploadSessionService.completeOwnedSession()` 会复用旧 `FileService.completeUpload()` 完成对象确认、目录补齐、配额/冲突校验和 `FileBlob + StoredFile + FileEntity.VERSION` 双写落库，然后把会话标记为 `COMPLETED`；失败时标记 `FAILED`，过期时标记 `EXPIRED`。当前仍没有独立 v2 分片内容写入端点。
- 2026-04-08 阶段 3 第三小步补充：上传会话新增 part 状态记录。`UploadSessionService.recordUploadedPart()` 会校验会话归属、状态、过期时间和 part 范围，把 `etag/size/uploadedAt` 写入 `uploadedPartsJson`，并将新会话推进到 `UPLOADING`。当前实现是会话状态跟踪，不是跨存储驱动的分片内容写入/合并实现。
