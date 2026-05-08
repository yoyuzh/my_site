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

## Dev Container

仓库根目录现在提供了后端专用的 `.devcontainer/devcontainer.json`。

用途：

- 使用 JDK 17 + Maven 的开发容器打开后端
- 默认把工作目录定位到 `backend/`
- 默认转发 `8080`
- 默认注入一个本地开发用 `APP_JWT_SECRET`
- 默认启用 `SPRING_PROFILES_ACTIVE=dev`
- 默认把 dev 环境的 H2 文件数据库挂到持久化 Docker volume `my-site-backend-h2-data`

进入容器后，仍按仓库已有命令运行：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

如果你需要读取仓库根目录的 `.env`，它仍然和 `backend/` 一起挂载在同一个 workspace 下，可通过 `../.env` 访问。

当前 dev container 会把 `SPRING_DATASOURCE_URL` 覆盖到 `/var/lib/my-site-h2/yoyuzh_portal_dev`。只要不删除对应的 Docker volume，重建后端容器后开发数据会保留。

## Production Docker

仓库现在提供了一套后端生产容器化配置：

- `backend/Dockerfile`：构建 Spring Boot 生产镜像
- `backend/.env.docker.example`：容器运行环境变量模板
- `docker-compose.backend.yml`：本地或新环境联调用的 `backend + mysql` 示例编排

### 1. 准备容器环境变量

先复制模板：

```bash
cp backend/.env.docker.example backend/.env.docker
```

至少要填写：

- `APP_JWT_SECRET`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

如果你想复用宿主机已有 MySQL，而不是启动 compose 里的 `mysql` 服务，把 `SPRING_DATASOURCE_URL` 改成：

```env
SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/yoyuzh_portal?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
```

### 2. 运行方式

本地一把拉起 `backend + mysql`：

```bash
docker compose -f docker-compose.backend.yml up --build -d
```

如果只启动后端容器，并复用宿主机现有 MySQL：

```bash
docker compose -f docker-compose.backend.yml up --build -d backend
```

### 3. 镜像内默认行为

- 默认使用 `prod` profile
- 默认监听 `0.0.0.0:8080`
- 默认关闭 Redis
- 默认把本地文件存储写到容器内 `/app/storage`

### 4. 数据库说明

- `docker-compose.backend.yml` 里的 `mysql` 服务会挂载 `backend/sql/mysql-init.sql`
- 后端如果继续使用本地文件系统存储，建议保留命名 volume `my-site-backend-storage`
- 如果生产环境使用对象存储，把 `YOYUZH_STORAGE_PROVIDER` 和相关 S3 变量切到 `.env.docker`

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
- 方便和仓库里的 `frontend/` 前端直接联调

JWT 启动要求：

- `app.jwt.secret` 不能为空
- 不允许使用默认占位值
- 至少需要 32 字节强密钥
- 仓库内的 `application.yml` / `application-dev.yml` 只从环境变量 `APP_JWT_SECRET` 读取，不再内置可直接启动的默认 secret

## 测试

后端测试当前分为三层：

- 单元测试：JUnit 5 + Mockito
- Web / 控制器测试：MockMvc
- 集成测试：Spring Boot 上下文测试，部分用例可通过 Testcontainers 使用真实 PostgreSQL

常用命令：

```bash
mvn test
```

如需生成 JaCoCo 覆盖率报告：

```bash
mvn test jacoco:report
```

报告输出位置：

- `backend/target/site/jacoco/index.html`

说明：

- MockMvc 测试已经作为默认 Web 测试栈使用。
- Testcontainers 用例在本机可用 Docker 时执行；如果 Docker 不可用，标记为 `disabledWithoutDocker` 的容器测试会自动跳过，不阻塞其他后端测试。
- 当前 Maven 构建已接入 JaCoCo 插件，覆盖率报告输出到 `backend/target/site/jacoco/`。

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
