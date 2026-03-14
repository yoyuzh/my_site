# yoyuzh-portal-backend

`yoyuzh.xyz` 的 Spring Boot 3.x 后端，提供：

- 用户注册、登录、JWT 鉴权、用户信息接口
- 个人网盘上传、下载、删除、目录管理、分页列表
- CQU 课表与成绩聚合接口
- Swagger 文档、统一异常、日志输出

## 环境要求

- JDK 17+
- Maven 3.9+
- 生产环境使用 MySQL 8.x 或 openGauss

## 启动

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
- CQU 接口返回 mock 数据
- 方便和 `vue/` 前端直接联调

## 访问地址

- Swagger: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`（仅 `dev` 环境）

## 数据库脚本

- MySQL: `sql/mysql-init.sql`
- openGauss: `sql/opengauss-init.sql`

## 主要接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/user/profile`
- `POST /api/files/upload`
- `POST /api/files/mkdir`
- `GET /api/files/list`
- `GET /api/files/download/{fileId}`
- `DELETE /api/files/{fileId}`
- `GET /api/cqu/schedule`
- `GET /api/cqu/grades`

## CQU 配置

部署到真实环境时修改：

```yaml
app:
  cqu:
    base-url: https://your-cqu-api
    require-login: false
    mock-enabled: false
```

当前 Java 后端保留了 HTTP 适配点；本地 `dev` 环境使用 mock 数据先把前后端链路跑通。
