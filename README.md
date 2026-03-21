# yoyuzh.xyz

个人站点项目，包含前端门户、Spring Boot 后端、个人网盘、快传模块和后台管理台。

## 项目特性

- 用户注册、登录、JWT 鉴权
- 邀请码注册，邀请码单次使用后自动轮换
- 同账号仅允许一台设备同时登录
- 个人网盘：上传、下载、重命名、删除、移动、复制、分享
- 快传：取件码、分享链接、浏览器间 P2P 传输、接收后存入网盘
- 管理台：用户管理、文件管理、邀请码查看与复制

## 技术栈

- 前端：Vite 6、React 19、TypeScript、Tailwind CSS v4
- 后端：Spring Boot 3.3、Java 17、Maven
- 数据库：MySQL 8.x
- 对象存储：本地文件系统或阿里云 OSS
- 部署：前端 OSS 静态发布，后端 jar + systemd

## 仓库结构

```text
.
├── front/      前端站点
├── backend/    Spring Boot 后端
├── docs/       计划与文档
├── scripts/    部署与辅助脚本
├── 模板/       页面参考模板
└── data/       本地数据或辅助文件
```

## 核心页面

- `/overview` 总览页
- `/files` 网盘
- `/transfer` 快传
- `/games` 小游戏
- `/admin/*` 管理台

## 本地开发

### 1. 启动后端

进入 `backend/` 后运行：

```bash
APP_JWT_SECRET=<至少32字节的随机密钥> mvn spring-boot:run
```

如果需要本地开发环境：

```bash
APP_JWT_SECRET=<至少32字节的随机密钥> mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

说明：

- `dev` 环境默认使用 H2
- 默认端口是 `8080`
- Swagger 地址是 `http://localhost:8080/swagger-ui.html`

### 2. 启动前端

进入 `front/` 后运行：

```bash
npm run dev
```

说明：

- 默认端口是 `3000`
- 本地前端通过 `front/vite.config.ts` 里的代理访问后端

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

注意：

- 不要在仓库根目录执行 `npm` 命令，这里没有根 `package.json`
- 前端的 `npm run lint` 实际执行的是 `tsc --noEmit`

## 环境变量

### 后端

至少需要：

```env
APP_JWT_SECRET=<至少32字节的随机密钥>
```

可选：

```env
APP_ADMIN_USERNAMES=admin1,admin2
APP_AUTH_REGISTRATION_INVITE_CODE=<初始化邀请码种子>
```

如果启用 OSS：

```env
YOYUZH_STORAGE_PROVIDER=oss
YOYUZH_OSS_ENDPOINT=...
YOYUZH_OSS_BUCKET=...
YOYUZH_OSS_ACCESS_KEY_ID=...
YOYUZH_OSS_ACCESS_KEY_SECRET=...
```

### 前端

前端生产发布脚本会读取 `.env.oss.local` 或环境变量中的 OSS 配置。

参考文件：

- `.env.oss.example`
- `.env.oss.local`

## 发布方式

### 前端发布

在仓库根目录执行：

```bash
node scripts/deploy-front-oss.mjs
```

可选：

```bash
node scripts/deploy-front-oss.mjs --dry-run
node scripts/deploy-front-oss.mjs --skip-build
```

### 后端发布

1. 在 `backend/` 下执行：

```bash
mvn package
```

2. 将生成的 jar 上传到服务器：

```text
backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar
```

3. 替换服务器上的运行包并重启 systemd 服务

当前线上流程是：

- 远端目录：`/opt/yoyuzh`
- 运行包：`/opt/yoyuzh/yoyuzh-portal-backend.jar`
- 服务名：`my-site-api.service`

## 业务说明

### 网盘

- 支持文件和文件夹上传
- 支持移动到、复制到
- 支持生成公开分享链接
- 支持从快传接收结果直接存入网盘

### 快传

- 文件内容默认走浏览器间 P2P DataChannel
- 后端负责会话、取件码和信令交换
- 接收端可选择部分文件接收，也可整包 ZIP 下载

### 登录与邀请码

- 注册必须填写邀请码
- 邀请码会在成功注册后自动刷新
- 同一账号新设备登录后，旧设备会在下次访问受保护接口时失效

## 相关文档

- [backend/README.md](./backend/README.md)
- [front/README.md](./front/README.md)
- [AGENTS.md](./AGENTS.md)
- `docs/superpowers/plans/`

## 备注

- `front/README.md` 是历史模板文件，项目当前真实启动与发布方式以本 README 和 `AGENTS.md` 为准
- 如果需要快速了解当前实现，建议先看 `front/src/pages/` 和 `backend/src/main/java/com/yoyuzh/`
