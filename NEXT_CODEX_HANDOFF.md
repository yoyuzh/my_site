# 项目交接说明

更新时间：2026-03-18  
项目根目录：`/Users/mac/Documents/my_site`

## 1. 项目概况

这是一个前后端分离的个人门户项目：

- 前端：`front/`
- 后端：`backend/`
- 线上主站：`https://yoyuzh.xyz`
- 线上 API：`https://api.yoyuzh.xyz`

主要功能：

- 登录 / 注册
- 网盘文件列表与最近文件
- 教务相关接口（课表 / 成绩）

## 2. 当前线上架构

当前建议保持的生产架构是：

- `yoyuzh.xyz`：继续走 OSS / ESA，负责静态站点
- `api.yoyuzh.xyz`：直接指向后端服务器，不要继续走 ESA 代理

原因：

- 主站静态资源走 ESA 没问题
- API 一旦走 ESA，之前出现过：
  - `525 Origin SSL Handshake Error`
  - `ERR_CONNECTION_CLOSED`
  - `ERR_EMPTY_RESPONSE`
- 当前最稳方案是“静态站加速，API 直连”

## 3. 当前前端生产配置

文件：

- `front/.env.production`

当前应保持为：

```env
VITE_API_BASE_URL="https://api.yoyuzh.xyz/api"
VITE_ROUTER_MODE="hash"
VITE_ENABLE_DEV_LOGIN="false"
```

说明：

- 不要再切回同域 `/api`，除非以后重新正确配置边缘转发
- 目前生产前端已经恢复为直连 `api.yoyuzh.xyz`

## 4. 当前已完成的前端改动

### 4.1 登录页

`front/src/pages/Login.tsx`

- 已替换为模板版登录 / 注册页
- 登录调用：`POST /auth/login`
- 注册调用：`POST /auth/register`
- 登录成功后写入 session 并跳转 `/overview`

### 4.2 网络错误处理

`front/src/lib/api.ts`

- 对网络错误统一包装为更清晰的前端错误
- 登录和只读接口有轻量重试机制
- 当前策略是“尽量兜底，但不要把登录拖到 7-8 秒”

### 4.3 登录成功与总览初始化失败拆分提示

相关文件：

- `front/src/lib/session.ts`
- `front/src/pages/Overview.tsx`
- `front/src/pages/overview-state.ts`

已做：

- 登录成功后会标记一次“post-login pending”
- `/overview` 初始化失败时，会提示“登录已成功，但总览加载失败”
- 避免把 overview 并发初始化失败误判成登录失败

## 5. 当前后端与服务器状态

通过 SSH 已确认：

- `my-site-api.service` 正常运行
- 后端本机 `127.0.0.1:8080` 正常
- 服务器本机直打登录接口返回 `200`
- Nginx 正常反代 `api.yoyuzh.xyz -> 127.0.0.1:8080`

服务器上关键配置：

- Nginx：`/etc/nginx/sites-enabled/my-site-api`
- 后端配置：`/opt/yoyuzh/application-prod.yml`
- 服务名：`my-site-api.service`

## 6. 线上排查结论

### 6.1 已确认不是后端业务慢

在服务器本机测试结果：

- `POST http://127.0.0.1:8080/api/auth/login` 约 `95ms`
- `POST https://api.yoyuzh.xyz/api/auth/login` 从服务器发起约 `681ms`

所以：

- 后端本身不是“登录 5 秒”的根因

### 6.2 之前登录很慢的主要原因

更像是以下问题叠加：

- 旧 DNS / 旧代理链路未收敛
- API 域名曾被 ESA 代理，导致 TLS / 回源问题
- 浏览器前链路偶发 `ERR_CONNECTION_CLOSED`

### 6.3 当前更可信的状态

服务器日志已经看到真实浏览器请求成功：

- `OPTIONS /api/auth/login` => `200`
- `POST /api/auth/login` => `200`
- `/api/user/profile` => `200`
- `/api/files/recent` => `200`
- `/api/files/list` => `200`
- `/api/cqu/*` => `200`

因此：

- 现在如果仍有个别客户端不稳定，优先怀疑本地 DNS / 浏览器缓存 / 本地网络链路

## 7. DNS / ESA 方面的重要结论

### 7.1 过去踩过的坑

不要再轻易做下面这件事：

- 让 `yoyuzh.xyz/api/*` 通过 ESA 回源到 API

之前已经明确踩到：

- `/api/*` 误回 OSS，报 `403 NonCnameForbidden`
- 回源 HTTPS 握手失败，报 `525 Origin SSL Handshake Error`

### 7.2 当前建议

- `yoyuzh.xyz`：可以继续 ESA
- `api.yoyuzh.xyz`：不要走 ESA 代理

### 7.3 用户侧现象

曾出现：

- 无痕模式能登录
- 本机 `dig` 还查到旧的 `198.18.0.148`

这说明某一阶段存在 DNS 传播不一致。  
如果下一个 Codex 遇到“浏览器能用，命令行不行”的情况，先查 DNS 链路，不要直接改代码。

## 8. OSS 前端部署方式

已经写好自动部署脚本：

- `scripts/deploy-front-oss.mjs`
- 配套库：`scripts/oss-deploy-lib.mjs`
- 配置模板：`.env.oss.example`

### 8.1 本地使用方式

先准备：

```bash
cp .env.oss.example .env.oss.local
```

然后填入 OSS 参数。

### 8.2 发布命令

```bash
./scripts/deploy-front-oss.mjs
```

### 8.3 只看将要上传什么

```bash
./scripts/deploy-front-oss.mjs --skip-build --dry-run
```

### 8.4 部署逻辑

脚本会：

- 读取 `.env.oss.local`
- 构建 `front/dist`
- 上传到 OSS
- 自动设置缓存头

缓存策略：

- `index.html` => `no-cache`
- `assets/*` => `public,max-age=31536000,immutable`

## 9. 测试账号

开发测试账号文档：

- `开发测试账号.md`

常用账号：

- `portal-demo / portal123456`

注意：

- 这些开发账号只在特定环境下才会自动初始化
- 如果线上账号密码不对，不要默认认为后端坏了

## 10. SSH 与敏感信息

有 SSH 凭据文件：

- `账号密码.txt`

下一个 Codex 可以读取该文件用于 SSH，但不要在普通交互回复里直接回显其中的明文密码。

## 11. 推荐的排查顺序

如果后续又出现“登录失败 / 网络连接异常”，按这个顺序排：

1. 先查前端当前生产包是否正确
   - 看 `https://yoyuzh.xyz/` 的 `index.html`
   - 确认引用的是最新构建产物

2. 再查 API 域名是否直连服务器
   - `dig +short api.yoyuzh.xyz`
   - `curl -vkI https://api.yoyuzh.xyz/`

3. 再查服务器本机和 systemd
   - `systemctl status my-site-api`
   - `curl http://127.0.0.1:8080/...`

4. 最后查 Nginx access/error log
   - `/var/log/nginx/access.log`
   - `/var/log/nginx/error.log`

不要上来就改前端逻辑。

## 12. 当前最重要的改进建议

### 短期建议

- 保持 API 直连，不再给 `api.yoyuzh.xyz` 套 ESA
- 用现有自动部署脚本发布前端

### 中期建议

- 给后端加一个明确的健康检查接口，比如 `/api/healthz`
- 给 Nginx access log 加 upstream timing 和 request id

### 长期建议

- 如果未来还想做同域 `/api`，要单独做一轮边缘转发设计
- 先确保：
  - 源站类型正确
  - 不会回 OSS
  - 不会再发生 `525`

## 13. 给下一个 Codex 的一句话总结

当前项目已经从“链路混乱”恢复到“后端基本正常、主站正常、前端直连 API”的状态。  
接手时优先维持现状，不要贸然重新启用 ESA 的 `/api` 回源方案。
