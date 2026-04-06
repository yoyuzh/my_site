# yoyuzh.xyz

一个基于 React + Spring Boot 构建的个人站点项目，包含个人网盘、浏览器快传、账号系统与后台管理台。

## 项目简介

这个仓库是 `yoyuzh.xyz` 的完整源码。

它不是一个单页 Demo，而是一个持续迭代中的全栈个人网站，覆盖了前端门户、后端 API、文件存储、账号体系和管理功能。

## 功能特性

### 用户系统

- 用户注册与登录
- 邀请码注册
- 同账号支持桌面端与移动端同时在线
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
- 本地文件系统 / 多吉云 S3 兼容对象存储
- S3 兼容静态资源发布

## 仓库结构

```text
.
├── backend/      Spring Boot 后端
├── front/        React 前端
├── docs/         计划与文档
├── docs/agents/  agent 补充交接文档
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

先准备根目录环境变量：

```bash
cp .env.example .env
```

填好 `APP_JWT_SECRET` 后，在当前 shell 里加载：

```bash
set -a
source .env
set +a
```

```bash
cd backend
mvn spring-boot:run
```

如果要使用本地开发配置：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
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

推荐把本地密钥和部署配置统一维护在仓库根目录 `.env`，模板在 `.env.example`。

### 后端必填

```env
APP_JWT_SECRET=<至少32字节的密钥>
```

### 后端可选

```env
APP_ADMIN_USERNAMES=admin1,admin2
APP_AUTH_REGISTRATION_INVITE_CODE=<初始化邀请码种子>
```

### S3 相关

```env
YOYUZH_STORAGE_PROVIDER=s3
YOYUZH_DOGECLOUD_API_BASE_URL=https://api.dogecloud.com
YOYUZH_DOGECLOUD_API_ACCESS_KEY=...
YOYUZH_DOGECLOUD_API_SECRET_KEY=...
YOYUZH_DOGECLOUD_STORAGE_SCOPE=yoyuzh-files
YOYUZH_DOGECLOUD_STORAGE_TTL_SECONDS=3600
YOYUZH_DOGECLOUD_S3_REGION=automatic
```

### 前端发布配置

前端发布脚本会优先从仓库根目录 `.env` 读取多吉云 API 凭据，再动态换取临时 S3 密钥；旧 `.env.oss.local` 只保留兼容回退读取。前端静态桶应填写逻辑桶名 `yoyuzh-front`，不要直接把底层 `s3Bucket` 写死到配置里。

常用变量：

```env
YOYUZH_DOGECLOUD_API_ACCESS_KEY=...
YOYUZH_DOGECLOUD_API_SECRET_KEY=...
YOYUZH_DOGECLOUD_FRONT_SCOPE=yoyuzh-front
YOYUZH_DOGECLOUD_FRONT_TTL_SECONDS=3600
YOYUZH_DOGECLOUD_FRONT_PREFIX=
YOYUZH_DOGECLOUD_STORAGE_SCOPE=yoyuzh-files
YOYUZH_DOGECLOUD_ANDROID_SCOPE=yoyuzh-files
YOYUZH_ANDROID_RELEASE_PREFIX=android/releases
```

参考文件：

- `.env.example`
- `.env`

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

### Android APK 发包

在仓库根目录执行：

```bash
node scripts/deploy-android-apk.mjs
```

这个脚本会自动完成以下步骤：

- `cd front && npm run build`
- `cd front && npx cap sync android`
- 自动补回 Android 插件工程里的 Google Maven 镜像配置
- `cd front/android && ./gradlew assembleDebug`
- `node scripts/deploy-front-oss.mjs --skip-build`
- `node scripts/deploy-android-release.mjs`

如果只想重新上传 APK，不想重发前端静态站，可以直接执行：

```bash
node scripts/deploy-android-release.mjs
```

### 阿里云 OSS 到多吉云 S3 迁移

静态站点桶或文件桶需要整桶迁移时，可在仓库根目录执行：

```bash
node scripts/migrate-aliyun-oss-to-s3.mjs \
  --source-bucket=<阿里云源 Bucket> \
  --source-access-key-id=<阿里云 AccessKeyId> \
  --source-access-key-secret=<阿里云 AccessKeySecret> \
  --target-scope=<多吉云逻辑桶名> \
  --target-api-access-key=<多吉云 AccessKey> \
  --target-api-secret-key=<多吉云 SecretKey>
```

可选参数：

```bash
node scripts/migrate-aliyun-oss-to-s3.mjs --dry-run
node scripts/migrate-aliyun-oss-to-s3.mjs --prefix=race/
node scripts/migrate-aliyun-oss-to-s3.mjs --overwrite
node scripts/migrate-aliyun-oss-to-s3.mjs --target-api-base-url=https://api.dogecloud.com
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
