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

推荐先在仓库根目录准备并加载 `.env`：

```bash
cp .env.example .env
set -a
source ../.env
set +a
```

默认配置：

```bash
mvn spring-boot:run
```

本地联调建议使用 `dev` 环境：

```bash
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

## S3 兼容直传说明

生产环境如果启用：

```env
YOYUZH_STORAGE_PROVIDER=s3
YOYUZH_DOGECLOUD_API_BASE_URL=https://api.dogecloud.com
YOYUZH_DOGECLOUD_API_ACCESS_KEY=...
YOYUZH_DOGECLOUD_API_SECRET_KEY=...
YOYUZH_DOGECLOUD_STORAGE_SCOPE=yoyuzh-files
YOYUZH_DOGECLOUD_STORAGE_TTL_SECONDS=3600
YOYUZH_DOGECLOUD_S3_REGION=automatic
```

后端会先用多吉云服务端 API 换取 `OSS_FULL` 临时密钥，再生成浏览器直传和下载所需的 S3 预签名地址。`YOYUZH_DOGECLOUD_STORAGE_SCOPE` 需要填写多吉云逻辑桶名；按你当前环境，文件桶应填 `yoyuzh-files`，而不是底层 `s3Bucket`。

为保证浏览器可以直传，请在对象存储 Bucket 上放行站点域名对应的 CORS 规则，至少允许：

- Origin: `https://yoyuzh.xyz`
- Methods: `PUT`, `GET`, `HEAD`
- Headers: `Content-Type`, `x-amz-*`

后端运行时使用的是 AWS S3 Java SDK V2，适配多吉云文档中的 S3 兼容接入方式。如果生产环境里曾经存在“数据库元数据已经在对象存储模式下运行，但新 Bucket 里没有对应文件”的历史数据，需要额外做一次对象迁移；否则旧记录在重命名/删除时仍可能失败。
