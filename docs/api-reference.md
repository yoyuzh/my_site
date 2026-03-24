# API 接口文档

本文档用于快速了解 `yoyuzh.xyz` 当前后端 API 的职责、鉴权方式和主要接口分组。

## 1. 基本约定

### 基础路径

- 后端接口统一以 `/api` 开头
- 本地开发默认地址：`http://localhost:8080`

### 返回格式

大部分接口返回统一结构：

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

常见含义：

- `code = 0`：成功
- `code = 1000`：参数校验失败
- `code = 1001`：未登录
- `code = 1002`：权限不足
- `code = 1003`：业务对象不存在、邀请码错误、取件码失效等业务失败

### 鉴权方式

- 采用 `Authorization: Bearer <accessToken>`
- `refreshToken` 通过 `/api/auth/refresh` 换取新的登录态
- 当前实现为“单账号单设备在线”
  - 新设备登录后，旧设备的 access token 会失效

### 权限分层

- 公开接口：
  - `/api/auth/**`
  - `/api/transfer/**`
  - `GET /api/files/share-links/{token}`
- 登录后接口：
  - `/api/user/**`
  - `/api/files/**`
  - `/api/admin/**`

## 2. 认证模块

控制器：

- `backend/src/main/java/com/yoyuzh/auth/AuthController.java`
- `backend/src/main/java/com/yoyuzh/auth/DevAuthController.java`
- `backend/src/main/java/com/yoyuzh/auth/UserController.java`

### 2.1 注册

`POST /api/auth/register`

说明：

- 使用邀请码注册
- 注册成功后直接返回登录态
- 邀请码成功使用后会自动刷新

请求重点字段：

- `username`
- `email`
- `phoneNumber`
- `password`
- `confirmPassword`
- `inviteCode`

### 2.2 登录

`POST /api/auth/login`

请求字段：

- `username`
- `password`

返回字段：

- `token`
- `accessToken`
- `refreshToken`
- `user`

### 2.3 刷新登录态

`POST /api/auth/refresh`

请求字段：

- `refreshToken`

说明：

- 刷新后会返回新的 access token 与 refresh token
- 当前系统会让旧 refresh token 失效

### 2.4 开发环境登录

`POST /api/auth/dev-login`

说明：

- 仅用于开发联调
- 是否可用取决于当前环境配置

### 2.5 获取用户资料

`GET /api/user/profile`

### 2.6 更新用户资料

`PUT /api/user/profile`

### 2.7 修改密码

`POST /api/user/password`

说明：

- 成功后会重新签发新的登录态
- 同时会顶掉旧设备会话

### 2.8 头像相关

- `POST /api/user/avatar/upload/initiate`
- `POST /api/user/avatar/upload`
- `POST /api/user/avatar/upload/complete`
- `GET /api/user/avatar/content`

说明：

- 支持初始化直传
- 支持代理上传
- 最终通过完成接口落库

## 3. 网盘模块

控制器：

- `backend/src/main/java/com/yoyuzh/files/FileController.java`

### 3.1 上传相关

- `POST /api/files/upload`
- `POST /api/files/upload/initiate`
- `POST /api/files/upload/complete`

说明：

- 兼容普通上传和 OSS 直传
- 前端会优先尝试“初始化上传 -> 直传/代理 -> 完成上传”

### 3.2 目录与列表

- `POST /api/files/mkdir`
- `GET /api/files/list`
- `GET /api/files/recent`

说明：

- `list` 支持 `path`、`page`、`size`
- 当前前端会在网盘页缓存目录内容和最后访问路径

### 3.3 下载

- `GET /api/files/download/{fileId}`
- `GET /api/files/download/{fileId}/url`

说明：

- 普通文件优先获取下载 URL
- 文件夹可走 ZIP 下载

### 3.4 文件操作

- `PATCH /api/files/{fileId}/rename`
- `PATCH /api/files/{fileId}/move`
- `POST /api/files/{fileId}/copy`
- `DELETE /api/files/{fileId}`

说明：

- `move` 用于移动到目标路径
- `copy` 用于复制到目标路径
- 文件和文件夹都支持移动 / 复制

### 3.5 分享链接

- `POST /api/files/{fileId}/share-links`
- `GET /api/files/share-links/{token}`
- `POST /api/files/share-links/{token}/import`

说明：

- 已登录用户可为自己的文件或文件夹创建分享链接
- 公开访客可查看分享详情
- 登录用户可将分享内容导入自己的网盘

## 4. 快传模块

控制器：

- `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`

### 4.1 创建会话

`POST /api/transfer/sessions`

说明：

- 创建快传会话需要发送端登录
- 请求体必须区分 `mode`
  - `ONLINE`: 在线快传，15 分钟有效，只能被接收一次
  - `OFFLINE`: 离线快传，7 天有效，文件会落到站点存储并可被重复接收
- 返回会话 ID、取件码、模式、过期时间和文件清单

### 4.2 通过取件码查找会话

`GET /api/transfer/sessions/lookup?pickupCode=xxxxxx`

说明：

- 接收端通过 6 位取件码查找会话

### 4.3 加入会话

`POST /api/transfer/sessions/{sessionId}/join`

说明：

- 在线快传会占用一次性会话
- 离线快传返回可下载文件清单，不需要建立 P2P 通道

### 4.4 信令交换

- `POST /api/transfer/sessions/{sessionId}/signals`
- `GET /api/transfer/sessions/{sessionId}/signals`

说明：

- 后端负责 WebRTC 信令交换
- 文件内容本身不经过后端
- 实际文件通过浏览器 DataChannel 进行 P2P 传输
- 该组接口仅用于 `ONLINE` 模式

### 4.5 上传离线快传文件

`POST /api/transfer/sessions/{sessionId}/files/{fileId}/content`

说明：

- 需要发送端登录
- 发送端把离线文件内容上传到站点存储
- 线上环境会把离线文件落到 OSS

### 4.6 下载离线快传文件

`GET /api/transfer/sessions/{sessionId}/files/{fileId}/download`

说明：

- 公开接口
- 离线文件在有效期内可以被重复下载

### 4.7 存入网盘

`POST /api/transfer/sessions/{sessionId}/files/{fileId}/import`

说明：

- 需要登录
- 把离线快传文件导入到当前用户网盘

## 5. 管理台模块

控制器：

- `backend/src/main/java/com/yoyuzh/admin/AdminController.java`

### 5.1 总览

`GET /api/admin/summary`

返回内容包括：

- 用户总数
- 文件总数
- 当前邀请码

### 5.2 用户管理

- `GET /api/admin/users`
- `PATCH /api/admin/users/{userId}/role`
- `PATCH /api/admin/users/{userId}/status`
- `PUT /api/admin/users/{userId}/password`
- `POST /api/admin/users/{userId}/password/reset`

说明：

- 可调整用户角色
- 可封禁用户
- 可重置或直接设置密码
- 封禁/改密会使原登录态失效

### 5.3 文件管理

- `GET /api/admin/files`
- `DELETE /api/admin/files/{fileId}`

## 6. 前端公开路由与接口关系

前端入口在：

- `front/src/App.tsx`

主要页面：

- `/login`
- `/overview`
- `/files`
- `/transfer`
- `/share/:token`
- `/admin/*`

接口关系：

- 登录页：调用 `/api/auth/login`、`/api/auth/register`
- 网盘页：调用 `/api/files/**`
- 快传页：调用 `/api/transfer/**`
- 分享页：调用 `/api/files/share-links/{token}` 和导入接口
- 管理台：调用 `/api/admin/**`

## 7. 建议阅读顺序

后续新窗口如果要接手后端功能，建议按这个顺序看：

1. `memory.md`
2. `docs/architecture.md`
3. `docs/api-reference.md`
4. `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`
5. 对应业务模块的 `Controller + Service`
