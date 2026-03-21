# yoyuzh.xyz

一个基于 React + Spring Boot 构建的个人站点项目，包含个人网盘、浏览器快传、账号系统与后台管理台。

## 项目简介

这个仓库是 `yoyuzh.xyz` 的完整源码。

它不是一个单页 Demo，而是一个持续迭代中的全栈个人网站，覆盖了前端门户、后端 API、文件存储、账号体系和管理功能。

## 功能特性

### 用户系统

- 用户注册与登录
- 邀请码注册
- 同账号仅允许一台设备同时登录
- 个人资料管理

### 网盘

- 文件上传、文件夹上传
- 下载、重命名、删除
- 文件与文件夹移动
- 文件与文件夹复制
- 树状目录浏览
- 分享链接生成与导入

### 快传

- 取件码分享
- 分享链接打开
- 浏览器间 P2P 传输
- 接收端可选择部分文件接收
- 支持整包 ZIP 下载
- 支持接收后直接存入网盘

### 管理台

- 用户管理
- 文件管理
- 注册邀请码查看

## 技术栈

### 前端

- React 19
- TypeScript
- Vite 6
- Tailwind CSS v4

### 后端

- Java 17
- Spring Boot 3.3
- Spring Security
- Spring Data JPA
- Maven

### 存储与基础设施

- MySQL 8.x
- 本地文件系统 / 阿里云 OSS
- OSS 静态资源发布

## 仓库结构

```text
.
├── backend/      Spring Boot 后端
├── front/        React 前端
├── docs/         计划与文档
├── scripts/      部署与辅助脚本
├── data/         本地数据或辅助文件
└── 模板/         页面参考模板
```

## 快速开始

### 环境要求

- Node.js
- npm
- JDK 17+
- Maven 3.9+
- MySQL 8.x

### 1. 启动后端

```bash
cd backend
APP_JWT_SECRET=<至少32字节的密钥> mvn spring-boot:run
```

如果要使用本地开发配置：

```bash
cd backend
APP_JWT_SECRET=<至少32字节的密钥> mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

说明：

- 默认端口：`8080`
- Swagger：`http://localhost:8080/swagger-ui.html`
- `dev` 环境默认使用 H2，更适合本地联调

### 2. 启动前端

```bash
cd front
npm run dev
```

说明：

- 默认端口：`3000`
- 本地前端代理配置位于 `front/vite.config.ts`

## 常用命令

### 前端

```bash
cd front
npm run dev
npm run build
npm run preview
npm run clean
npm run lint
npm run test
```

### 后端

```bash
cd backend
mvn spring-boot:run
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test
mvn package
```

## 环境变量

### 后端必填

```env
APP_JWT_SECRET=<至少32字节的密钥>
```

### 后端可选

```env
APP_ADMIN_USERNAMES=admin1,admin2
APP_AUTH_REGISTRATION_INVITE_CODE=<初始化邀请码种子>
```

### OSS 相关

```env
YOYUZH_STORAGE_PROVIDER=oss
YOYUZH_OSS_ENDPOINT=...
YOYUZH_OSS_BUCKET=...
YOYUZH_OSS_ACCESS_KEY_ID=...
YOYUZH_OSS_ACCESS_KEY_SECRET=...
```

### 前端发布配置

前端发布脚本会从环境变量或 `.env.oss.local` 中读取 OSS 配置。

参考文件：

- `.env.oss.example`
- `.env.oss.local`

## 部署

### 前端发布

在仓库根目录执行：

```bash
node scripts/deploy-front-oss.mjs
```

可选参数：

```bash
node scripts/deploy-front-oss.mjs --dry-run
node scripts/deploy-front-oss.mjs --skip-build
```

### 后端发布

先打包：

```bash
cd backend
mvn package
```

生成产物：

```text
backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar
```

然后将 jar 上传到服务器，并按你的 systemd / SSH 流程重启服务。

## 项目说明

- 注册需要邀请码
- 邀请码在成功注册后会自动刷新
- 同账号新设备登录后，旧设备会在下次访问受保护接口时失效
- 快传走“后端信令 + 浏览器 P2P 传输”模式

## 相关文档

- [backend/README.md](./backend/README.md)
- [front/README.md](./front/README.md)
- [AGENTS.md](./AGENTS.md)
- `docs/superpowers/plans/`

## 当前状态

项目正在持续迭代中，定位是一个真实运行中的个人站点，而不是最小演示项目。
