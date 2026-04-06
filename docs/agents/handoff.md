# 项目交接补充

更新时间：2026-04-06

这份文档只记录补充性的交接和运维提示；一切当前状态仍优先以 `memory.md`、`docs/architecture.md`、`docs/api-reference.md` 为准。

## 当前主线

- 产品方向已经从旧教务切到“网盘 + 快传 + 管理台 + Android 壳”
- 前端静态站发布走 `node scripts/deploy-front-oss.mjs`
- Android APK 发包走 `node scripts/deploy-android-apk.mjs`
- 后端交付仍是 `cd backend && mvn package` 后手动上传 jar 并重启服务

## 本地配置整理

- 根目录 `.env` 已收口多吉云 API 凭据、服务器 SSH 信息和常用部署元信息
- `.env.example` 作为模板保留在仓库中
- 旧的 `.env.oss.local` 仅作为兼容回退读取，不再是主入口

## 线上运维关键信息

- 后端服务名：`my-site-api.service`
- 后端 jar 路径：`/opt/yoyuzh/yoyuzh-portal-backend.jar`
- 后端环境变量文件：`/opt/yoyuzh/app.env`
- 后端额外配置文件：`/opt/yoyuzh/application-prod.yml`
- API 域名：`https://api.yoyuzh.xyz`
- 主站域名：`https://yoyuzh.xyz`

## 遇到线上登录/网络异常时先看什么

1. 前端当前生产包是否是最新资源
2. `api.yoyuzh.xyz` 的 DNS / TLS / 反向代理是否正常
3. `my-site-api.service` 是否正常运行
4. 服务端和 Nginx 日志里是否已有真实请求

不要在没有确认链路问题前先改前端业务逻辑。
