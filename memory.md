## 任务目标
一句话:记录当前仓库、线上环境、最近实现和开发注意事项，方便后续继续协作与接手。

## 当前状态
- 已完成:
  - 项目主线已经从旧教务模块切换为“网盘 + 快传 + 管理台”结构
  - 快传模块已整合进主站，支持取件码、分享链接、P2P 传输、部分文件接收、ZIP 下载、存入网盘
  - 网盘已支持上传、下载、重命名、删除、移动、复制、公开分享、接收快传后存入
  - 注册改成邀请码机制，邀请码单次使用后自动刷新，并在管理台展示与复制
  - 同账号仅允许一台设备同时登录，旧设备会在下一次访问受保护接口时失效
  - 后端已补生产 CORS，默认放行 `https://yoyuzh.xyz` 与 `https://www.yoyuzh.xyz`，并已重新发布
  - 根目录 README 已重写为中文公开版 GitHub 风格
  - VS Code 工作区已补 `.vscode/settings.json`、`.vscode/extensions.json`、`lombok.config`，并在 `backend/pom.xml` 显式声明了 Lombok annotation processor
- 进行中:
  - 继续观察 VS Code Java/Lombok 误报是否完全消失
  - 继续排查 `api.yoyuzh.xyz` 在不同网络/设备下的 TLS/SNI 链路稳定性
  - 后续如果再做 README/开源化展示，可以继续补 banner、截图和架构图
- 待开始:
  - 如果用户继续提需求，优先沿当前网站主线迭代，不再回到旧教务方向

## 已做决策
| 决策 | 理由 | 排除的方案及原因|
|---|---|---|
| 用快传模块替换旧教务模块 | 当前产品方向已经转向文件流转和个人站点工具集合 | 继续保留教务逻辑: 已不符合当前站点定位，维护成本高 |
| 快传采用“后端信令 + 浏览器 P2P 传输” | 文件内容不走自有服务器带宽，体验更接近局域/点对点传输 | 走服务器中转: 会增加服务器流量和实现复杂度 |
| 快传接收页收口回原 `/transfer` 页面 | 用户不需要单独进入专门的接收页面，入口更统一 | 独立接收页: 路径分散、用户心智更差 |
| 网盘侧边栏改成单一树状目录结构 | 更像真实网盘，层级关系清晰 | 保留“快速访问 + 目录”双区块: 结构割裂 |
| 注册邀请码改成单次使用后自动刷新 | 更适合私域邀请式注册，管理台也能直接查看当前邀请码 | 固定邀请码: 容易扩散且不可控 |
| 单设备登录通过“用户当前会话 ID + JWT sid claim”实现 | 新登录能立即顶掉旧 access token，而不仅仅是旧 refresh token | 只撤销 refresh token: 旧 access token 仍会继续有效一段时间 |
| 前端发布继续使用 `node scripts/deploy-front-oss.mjs` | 仓库已有正式 OSS 发布脚本，流程稳定 | 手动上传 OSS: 容易出错，也不利于复用 |
| 后端发布继续采用“本地打包 + SSH/ SCP 上传 jar + systemd 重启” | 当前线上就按这个方式运行 | 自创部署脚本: 仓库里没有现成正式脚本，容易和现网偏离 |
| 主站 CORS 默认放行 `https://yoyuzh.xyz` 与 `https://www.yoyuzh.xyz` | 前端生产环境托管在 OSS 域名下，必须允许主站跨域调用后端 API | 仅保留 localhost: 会导致生产站调用 API 时被浏览器拦截 |

## 待解决问题
- [ ] VS Code 若仍报 `final 字段未在构造器初始化` 之类错误，优先判断为 Lombok / Java Language Server 误报，而不是源码真实错误
- [ ] `front/README.md` 仍是旧模板风格说明，当前真实入口说明以根目录 `README.md` 为准，后续可继续整理
- [ ] 前端构建仍有 chunk size warning，目前不阻塞发布，但后续可以考虑做更细的拆包
- [ ] `api.yoyuzh.xyz` 仍存在“同机房 IP 直连可用，但带域名 TLS/SNI 有时失败”的链路问题；这不是后端业务代码错误
- [ ] 线上前端 bundle 当前仍内嵌 `https://api.yoyuzh.xyz/api`，API 子域名异常时会直接表现为“网络异常/登录失败”

## 关键约束
(只写这个任务特有的限制，区别于项目通用规则)
- 仓库根目录没有 `package.json`，不要在根目录执行 `npm` 命令
- 前端真实命令以 `front/package.json` 为准；`npm run lint` 实际是 `tsc --noEmit`
- 后端真实命令以 `backend/pom.xml` / `backend/README.md` 为准；常用的是 `mvn test` 和 `mvn package`
- 修改文件时默认用 `apply_patch`
- 已知线上后端服务名是 `my-site-api.service`
- 已知线上后端运行包路径是 `/opt/yoyuzh/yoyuzh-portal-backend.jar`
- 已知新服务器公网 IP 是 `1.14.49.201`
- 2026-03-23 排障确认：`api.yoyuzh.xyz` 在部分网络下存在 TLS/SNI 握手异常，但后端服务与 nginx 正常，且 IP 直连加 `Host: api.yoyuzh.xyz` 时可正常返回
- 2026-03-23 实时日志确认：Mac 端 `202.202.9.243` 登录链路 `OPTIONS /api/auth/login -> POST /api/auth/login -> 后续 /api/*` 全部返回 200；手机失败时并不总能在服务端日志中看到对应登录请求
- 服务器登录信息保存在本地 `账号密码.txt`，不要把内容写进文档或对外输出

## 参考资料
(相关链接、文档片段、背景资料)
- 根目录说明: `README.md`
- 后端说明: `backend/README.md`
- 仓库协作规范: `AGENTS.md`
- 前端/后端工作区配置: `.vscode/settings.json`、`.vscode/extensions.json`
- Lombok 配置: `lombok.config`
- 最近关键实现位置:
  - 单设备登录: `backend/src/main/java/com/yoyuzh/auth/AuthService.java`
  - JWT 会话校验: `backend/src/main/java/com/yoyuzh/auth/JwtTokenProvider.java`
  - JWT 过滤器: `backend/src/main/java/com/yoyuzh/config/JwtAuthenticationFilter.java`
  - CORS 配置: `backend/src/main/java/com/yoyuzh/config/CorsProperties.java`、`backend/src/main/resources/application.yml`
  - 网盘树状目录: `front/src/pages/Files.tsx`、`front/src/pages/files-tree.ts`
  - 快传接收页: `front/src/pages/TransferReceive.tsx`
  - 前端生产 API 基址: `front/.env.production`
