# 架构文档

本文档用于描述 `yoyuzh.xyz` 当前的系统结构、模块边界、关键流程和部署方式，便于后续窗口快速建立整体上下文。

## 1. 系统概览

项目是一个前后端分离的全栈站点，核心由三部分组成：

1. React 前端站点
2. Spring Boot 后端 API
3. 文件存储层（本地文件系统或 OSS）

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

关键入口：

- `front/src/App.tsx`
- `front/src/lib/api.ts`
- `front/src/components/layout/Layout.tsx`

主要页面：

- `front/src/pages/Login.tsx`
- `front/src/pages/Overview.tsx`
- `front/src/pages/Files.tsx`
- `front/src/pages/Transfer.tsx`
- `front/src/pages/TransferReceive.tsx`
- `front/src/pages/FileShare.tsx`

### 2.2 后端

路径：

- `backend/`

核心职责：

- 认证与 JWT 鉴权
- 网盘元数据与文件流转
- 快传信令与会话状态
- 管理台 API
- OSS / 本地存储抽象

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
- `scripts/`: 前端 OSS 发布、存储迁移和本地辅助脚本

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
- 头像上传
- 单设备登录控制
- 邀请码消费与轮换

关键实现说明：

- access token 使用 JWT
- refresh token 持久化到数据库
- 当前会话通过 `activeSessionId + JWT sid claim` 绑定
- 新登录会挤掉旧设备

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
- 分享链接与导入
- 前端树状目录导航

关键实现说明：

- 文件元数据在数据库
- 文件内容走存储层抽象
- 支持本地磁盘和 OSS
- 前端会缓存目录列表和最后访问路径

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
- 离线快传会把文件内容落到站点存储，线上环境使用 OSS，默认保留 7 天并支持重复接收

### 3.4 管理台模块

核心文件：

- `backend/src/main/java/com/yoyuzh/admin/AdminController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminService.java`
- `front/src/admin/*`

职责：

- 管理用户
- 管理文件
- 查看邀请码

关键实现说明：

- 管理台依赖后端 summary/users/files 接口
- 当前邀请码由后端返回给管理台展示

## 4. 关键业务流程

### 4.1 登录流程

1. 前端登录页调用 `/api/auth/login`
2. 后端鉴权成功后签发 access token + refresh token
3. 后端刷新 `activeSessionId`
4. 前端本地存储 `portal-session`
5. 后续请求通过 `Authorization: Bearer <token>` 访问
6. JWT 过滤器校验 token、用户状态和会话 ID 是否仍匹配

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
3. 如果存储支持直传，则浏览器直接上传到 OSS
4. 前端再调用 `/api/files/upload/complete`
5. 如果直传失败，会回退到代理上传接口 `/api/files/upload`

### 4.4 文件分享流程

1. 登录用户创建分享链接
2. 后端生成 token
3. 公开用户通过 `/share/:token` 查看详情
4. 登录用户可以导入到自己的网盘

### 4.5 快传流程

1. 发送端登录后创建快传会话
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

### 6.2 单设备登录

当前实现不是只撤销 refresh token，而是同时控制 access token：

- 用户表记录 `activeSessionId`
- JWT 里包含 `sid`
- 过滤器每次请求都会比对当前用户的 `activeSessionId`
- 新登录成功后，旧设备 token 会失效

## 7. 存储架构

抽象层：

- `backend/src/main/java/com/yoyuzh/files/storage/FileContentStorage.java`

实现方向：

- 本地文件系统
- OSS

设计目的：

- 让文件元数据逻辑与底层存储解耦
- 上传、下载、复制、移动都通过统一抽象收口

## 8. 部署架构

### 8.1 前端

- 构建工具：Vite
- 发布方式：OSS 静态资源发布
- 发布脚本：`node scripts/deploy-front-oss.mjs`

### 8.2 后端

- 打包方式：`mvn package`
- 产物：`backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar`
- 线上通常采用 jar + systemd 方式运行

当前已知线上信息：

- 服务名：`my-site-api.service`
- 运行包路径：`/opt/yoyuzh/yoyuzh-portal-backend.jar`

## 9. 开发注意事项

- 仓库根目录没有 `package.json`，不要在根目录执行 `npm`
- 前端命令只从 `front/package.json` 读取
- 后端命令只从 `backend/pom.xml` 读取
- 前端 `npm run lint` 实际是 `tsc --noEmit`
- 后端没有单独 lint 命令
- 本仓库大量使用 Lombok，VS Code 若出现“final 字段未初始化”之类误报，优先检查 Lombok 扩展、Java Language Server 和 annotation processor

## 10. 新窗口建议阅读顺序

后续新窗口进入仓库时，建议顺序：

1. `memory.md`
2. `docs/architecture.md`
3. `docs/api-reference.md`
4. `AGENTS.md`

如果要继续某个具体功能，再进入对应模块的：

- 前端页面文件
- 后端 Controller / Service
- 紧邻测试文件
