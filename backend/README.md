# yoyuzh-portal-backend

`yoyuzh.xyz` 的 Spring Boot 3.x 后端，提供：

- 用户注册、登录、JWT 鉴权、用户信息接口
- 个人网盘上传、下载、删除、目录管理、分页列表
- 快传会话与浏览器间 P2P 信令接口
- Swagger 文档、统一异常、日志输出

## 环境要求

- JDK 17+
- Maven 3.9+
- 生产环境使用 MySQL 8.x 或 openGauss

## 启动

默认配置：

```bash
APP_JWT_SECRET=<至少32字节的随机密钥> \
mvn spring-boot:run
```

本地联调建议使用 `dev` 环境：

```bash
APP_JWT_SECRET=<至少32字节的随机密钥> \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` 环境特点：

- 数据库使用 H2 文件库
- 方便和 `vue/` 前端直接联调

JWT 启动要求：

- `app.jwt.secret` 不能为空
- 不允许使用默认占位值
- 至少需要 32 字节强密钥
- 仓库内的 `application.yml` / `application-dev.yml` 只从环境变量 `APP_JWT_SECRET` 读取，不再内置可直接启动的默认 secret

## 访问地址

- Swagger: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`（仅 `dev` 环境）

## 数据库脚本

- MySQL: `sql/mysql-init.sql`
- openGauss: `sql/opengauss-init.sql`

## 旧库升级

如果服务器数据库是按旧版脚本初始化的，旧教务相关字段和表可以保留但不会再被当前代码使用。新环境请直接使用最新初始化脚本，不再创建教务缓存表。

MySQL:

```sql
DROP TABLE IF EXISTS portal_course;
DROP TABLE IF EXISTS portal_grade;
```

openGauss:

```sql
DROP TABLE IF EXISTS portal_course;
DROP TABLE IF EXISTS portal_grade;
```

## 主要接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/user/profile`
- `POST /api/files/upload`
- `POST /api/files/upload/initiate`
- `POST /api/files/upload/complete`
- `POST /api/files/mkdir`
- `GET /api/files/list`
- `GET /api/files/download/{fileId}`
- `GET /api/files/download/{fileId}/url`
- `DELETE /api/files/{fileId}`
- `POST /api/transfer/sessions`
- `GET /api/transfer/sessions/lookup`
- `POST /api/transfer/sessions/{sessionId}/join`
- `POST /api/transfer/sessions/{sessionId}/signals`
- `GET /api/transfer/sessions/{sessionId}/signals`

## OSS 直传说明

生产环境如果启用：

```env
YOYUZH_STORAGE_PROVIDER=oss
YOYUZH_OSS_ENDPOINT=https://oss-ap-northeast-1.aliyuncs.com
YOYUZH_OSS_BUCKET=your-bucket
YOYUZH_OSS_ACCESS_KEY_ID=...
YOYUZH_OSS_ACCESS_KEY_SECRET=...
```

前端会先调用后端拿签名上传地址，再由浏览器直接把文件内容传到 OSS。为保证浏览器可以直传，请在 OSS Bucket 上放行站点域名对应的 CORS 规则，至少允许：

- Origin: `https://yoyuzh.xyz`
- Methods: `PUT`, `GET`, `HEAD`
- Headers: `Content-Type`, `x-oss-*`

如果生产环境里曾经存在“数据库元数据已经在 OSS 模式下运行，但本地磁盘里没有对应文件”的历史数据，需要额外做一次对象迁移或元数据修复；否则旧记录在重命名/删除时仍可能失败。
