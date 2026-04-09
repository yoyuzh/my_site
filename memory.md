## 任务目标
一句话:记录当前仓库、线上环境、最近实现和开发注意事项，方便后续继续协作与接手。

## 当前状态
- 已完成:
  - 项目主线已经从旧教务模块切换为“网盘 + 快传 + 管理台”结构
  - 快传模块已整合进主站，支持取件码、分享链接、P2P 传输、部分文件接收、ZIP 下载、存入网盘
  - 网盘已支持上传、下载、重命名、删除、移动、复制、公开分享、接收快传后存入
  - 注册改成邀请码机制，邀请码单次使用后自动刷新，并在管理台展示与复制
  - 同账号现已允许桌面端与移动端同时在线，但同一端类型仍只保留一个有效会话；同端再次登录会在下一次受保护请求时挤掉旧会话
  - 后端已补生产 CORS，默认放行 `https://yoyuzh.xyz` 与 `https://www.yoyuzh.xyz`
  - 线上文件存储与前端静态托管已迁到多吉云对象存储，后端通过临时密钥 API 获取短期 S3 会话访问底层 COS 兼容桶
  - 管理台 dashboard 已显示总存储量、下载流量、今日请求次数、快传使用量、离线快传占用和请求折线图，并支持调整离线快传总上限
  - 管理台用户列表已显示每个用户的已用空间 / 配额，表格也已收紧
  - 游戏页已接入 `/race/`、`/t_race/`，带站内播放器、退出按钮和友情链接
  - 2026-04-02 已统一密码策略为“至少 8 位且包含大写字母”，并补测试确认管理员改密后旧密码失效、新密码生效
  - 2026-04-02 已放开未登录直达快传：登录页可直接进入快传，匿名用户可发在线快传；2026-04-03 又放开了离线接收，因此匿名用户现在可发在线快传、接收在线快传、接收离线快传，但发离线和把离线文件存入网盘仍要求登录
  - 2026-04-02 快传发送页已新增“我的离线快传”区域：登录用户可查看自己未过期的离线快传记录，并点开弹层重新查看取件码、二维码和分享链接
  - 2026-04-02 已将“我的离线快传”后端接口正式部署到生产，`/api/transfer/sessions/offline/mine` 在线可用，未登录访问会返回 `401`
  - 2026-04-02 前端主入口已按屏幕宽度自动切换桌面壳与移动壳，宽度小于 768px 时渲染 `MobileApp`
  - 2026-04-02 移动端 `MobileFiles` 与 `MobileTransfer` 已发布与桌面一致的动态光晕背景，不再使用纯黑静态底色
  - 2026-04-02 网盘存储模型已改为“`StoredFile` 逻辑元数据 + `FileBlob` 物理对象引用”；新上传写入全局 `blobs/...` key，分享导入与网盘复制都会直接复用同一个 blob，不再复制物理文件
  - 2026-04-02 后端启动时会自动把旧 `portal_file.storage_name` 数据回填到新的 `blob_id` 引用；管理台 `totalStorageBytes` 现已按 `FileBlob` 汇总真实物理占用，而不是按逻辑文件行数重复累加
  - 2026-04-02 18:43 CST 已将共享 blob 改造后的后端 jar 部署到生产；`my-site-api.service` 重启成功，`https://api.yoyuzh.xyz/swagger-ui.html` 仍可访问
  - 2026-04-02 19:08 CST 已将“上传落库失败时自动删除已写入 blob”修复部署到生产；当前普通上传、直传完成、外部导入在元数据保存失败时都会回滚底层 `blobs/...` 对象，避免再产生孤儿 blob
  - 2026-04-02 管理台 summary 已新增“最近 7 天上线记录”：JWT 鉴权成功后会按天去重记录上线用户，保留 7 天并返回每天人数与用户名单
  - 2026-04-02 管理台“今日请求折线图”已改为只展示当天已过去的小时；例如当天只到 07 点时，曲线只会覆盖 00:00-07:00，点位也缩成小圆点
  - 2026-04-03 已在 `front/` 接入 Capacitor，生成 `front/android` Android 工程并成功产出调试 APK：`front/android/app/build/outputs/apk/debug/app-debug.apk`
  - 2026-04-03 快传前端已支持通过 `VITE_TRANSFER_ICE_SERVERS_JSON` 追加自定义 ICE / TURN 服务器；当前默认仍只有 STUN，因此跨运营商或手机蜂窝网络的在线 P2P 传输仍依赖后续补 TURN 才能稳定
  - 2026-04-03 Android 打包已确认走“Vite 产物 -> `npx cap sync android` -> Gradle `assembleDebug`”链路；当前应用包名为 `xyz.yoyuzh.portal`
  - 2026-04-03 Android WebView 壳内的前端 API 基址已改成运行时判断：Web 站点继续走相对 `/api`，Capacitor `localhost` 壳在 `http://localhost` 与 `https://localhost` 下都会默认直连 `https://api.yoyuzh.xyz/api`，避免 APK 把请求误打到应用内本地地址；后端 CORS 也同步放行了 `https://localhost`
  - 2026-04-03 由于这台机器直连 `dl.google.com` / Android Maven 仓库会 TLS 握手失败，Android 构建已改走阿里云 Google Maven 镜像，并通过 `redirector.gvt1.com` 手动落本机 SDK 包
  - 2026-04-03 总览页已新增 Android APK 下载入口；当前 Web 总览已改走后端公开下载口 `https://api.yoyuzh.xyz/api/app/android/download`，不再直接指向前端静态桶
  - 2026-04-03 鉴权链路已按客户端类型拆分会话：前端请求会带 `X-Yoyuzh-Client`，后端分别维护桌面和移动的活跃 `sid` 与 refresh token 集合，因此桌面 Web 与移动端 APK 可同时登录；移动端总览页在 Capacitor 原生壳内会显示“检查更新”，通过探测 OSS 上 APK 最新修改时间并直接跳转下载链接完成更新
  - 2026-04-03 前端 OSS 发布脚本现已收口为“只发布 `front/dist` 静态站”，不再上传 APK
  - 2026-04-03 已新增仓库根脚本 `node scripts/deploy-android-release.mjs`，只负责把 APK 与 `android/releases/latest.json` 上传到 Android 独立对象路径；`node scripts/deploy-android-apk.mjs` 会在前端静态站发布后自动调用它
  - 2026-04-03 Android 更新链路已改为“APK 存在文件桶独立路径 `android/releases/`，后端 `/api/app/android/latest` 读取 `android/releases/latest.json` 返回带版本号的后端下载地址，`/api/app/android/download` 直接分发 APK 字节流”；这样 App 内检查更新和 Web 下载都不会再误用前端静态桶旧包，也不依赖对象存储预签名下载
  - 2026-04-03 网盘已新增回收站：`DELETE /api/files/{id}` 现在会把文件或整个目录树软删除进回收站，默认保留 10 天；前端桌面网盘页在左侧目录栏最下方新增“回收站”入口，移动端网盘页头也可进入回收站查看并恢复
  - 2026-04-05 Git 远程已从 GitHub 迁到自建私有 Gitea：`https://git.yoyuzh.xyz/yoyuz/my_site.git`；当前本地 `main` 已推到新的 `origin/main`
  - 2026-04-06 已把本地项目密钥和部署元信息统一收口到仓库根目录 `.env`，模板文件改为 `.env.example`；前端 / Android 发布脚本现在优先读取 `.env`，旧 `.env.oss.local` 只作为兼容回退，不再作为主入口
  - 2026-04-06 已删除根目录 `账号密码.txt`，服务器 SSH 登录信息改为放在根目录 `.env`
  - 2026-04-06 已把补充型 handoff 文档收口到 `docs/agents/handoff.md`；`CLAUDE.md` 继续保留在根目录作为 agent 入口，额外的 `NEXT_CODEX_HANDOFF.md` 与目录说明文档已删除
  - 2026-04-06 已确认前端当前只是在源码层使用 `front/src/components/ui/*` 组件，不依赖根目录 `shadcn` CLI；因此已删除根目录 `package.json`、`package-lock.json`、`components.json` 和根目录 `node_modules`
  - 根目录 README 已重写为中文公开版 GitHub 风格
  - VS Code 工作区已补 `.vscode/settings.json`、`.vscode/extensions.json`、`lombok.config`，并在 `backend/pom.xml` 显式声明了 Lombok annotation processor
- 进行中:
  - 继续观察 VS Code Java/Lombok 误报是否完全消失
  - 后续如果再做 README/开源化展示，可以继续补 banner、截图和架构图
- 待开始:
  - 如果用户继续提需求，优先沿当前网站主线迭代，不再回到旧教务方向

## 已做决策
| 决策 | 理由 | 排除的方案及原因|
|---|---|---|
| 用快传模块替换旧教务模块 | 当前产品方向已经转向文件流转和个人站点工具集合 | 继续保留教务逻辑: 已不符合当前站点定位，维护成本高 |
| 快传采用“后端信令 + 浏览器 P2P 传输” | 文件内容不走自有服务器带宽，体验更接近局域/点对点传输 | 走服务器中转: 会增加服务器流量和实现复杂度 |
| 网盘文件改成“共享 blob + `StoredFile` 引用” | 分享导入、网盘复制、重命名、移动都不应再触发物理对象复制，删除时也需要按最后引用回收真实对象 | 继续把物理 key 绑定 `userId/path/storageName`: 会导致转存和复制永远写出第二份对象，浪费存储 |
| 快传接收页收口回原 `/transfer` 页面 | 用户不需要单独进入专门的接收页面，入口更统一 | 独立接收页: 路径分散、用户心智更差 |
| 网盘侧边栏改成单一树状目录结构 | 更像真实网盘，层级关系清晰 | 保留“快速访问 + 目录”双区块: 结构割裂 |
| 注册邀请码改成单次使用后自动刷新 | 更适合私域邀请式注册，管理台也能直接查看当前邀请码 | 固定邀请码: 容易扩散且不可控 |
| 登录态通过“按客户端类型拆分的会话 ID + JWT sid/client claim”实现 | 桌面 Web 和移动 APK 可以同时在线，但同一端再次登录仍会立即挤掉旧 access token，而不仅仅是旧 refresh token | 只保留全局单会话: 会让桌面/移动互相顶下线；只撤销 refresh token: 旧 access token 仍会继续有效一段时间 |
| 前端发布继续使用 `node scripts/deploy-front-oss.mjs` | 仓库已有正式静态站发布脚本，现已切到多吉云临时密钥 + S3 兼容上传流程 | 手动上传对象存储: 容易出错，也不利于复用 |
| 后端发布继续采用“本地打包 + SSH/ SCP 上传 jar + systemd 重启” | 当前线上就按这个方式运行 | 自创部署脚本: 仓库里没有现成正式脚本，容易和现网偏离 |
| 主站 CORS 默认放行 `https://yoyuzh.xyz` 与 `https://www.yoyuzh.xyz` | 前端生产环境托管在独立静态站域名下，必须允许主站跨域调用后端 API | 仅保留 localhost: 会导致生产站调用 API 时被浏览器拦截 |
| 文件存储切到多吉云对象存储并使用临时密钥 | 后端、前端发布和迁移脚本都可统一走 S3 兼容协议，同时减少长期静态密钥暴露 | 继续使用阿里云 OSS 固定密钥: 已不符合当前多吉云接入方式 |
| 密码策略放宽到“至少 8 位且包含大写字母” | 降低注册和管理员改密阻力，同时保留最基础的复杂度门槛 | 继续要求大小写 + 数字 + 特殊字符: 对当前站点用户而言过重，且已导致后台改密体验不一致 |
| 匿名用户仅开放在线快传，不开放离线快传 | 允许登录页直接进入快传，同时避免匿名用户占用站点持久存储 | 匿名也开放离线快传: 会增加滥用风险和存储成本 |
| 已登录用户可以在快传页回看自己的离线快传记录 | 离线快传有效期长达 7 天，用户需要在不重新上传的情况下再次查看取件码和分享链接 | 只在刚创建成功时展示一次取件信息: 用户丢失取件码后无法自助找回 |
| 前端主入口按宽度自动切换到移动壳 | 不需要单独维护 `/m` 路由，用户在小屏设备上直接进入移动端布局 | 独立 `/m` 路由: 需要额外记忆入口且与主站状态分叉 |
| 管理台上线记录按“JWT 鉴权成功的每日去重用户”统计，并只保留 7 天 | 后台需要回答“每天多少人上线、具体是谁”，同时不必引入更重的行为埋点系统 | 只统计登录接口: 无法覆盖 refresh 之后的真实活跃访问；无限保留历史: 超出当前管理需求 |
| Android 客户端先采用 Capacitor 包裹现有前端站点 | 现有 React/Vite 页面、鉴权和 API 调用可以直接复用，成本最低 | 重新单写原生 Android WebView 壳: 会引入额外原生维护面；改成 React Native / Flutter: 超出当前需求 |
| APK 发布通过前端 OSS 脚本额外上传稳定对象 key，而不是进入 `front/dist` | 既能让总览页长期使用固定下载地址，也能避免 `npx cap sync android` 把旧 APK 再次塞进新的 APK 资产里 | 把 APK 直接放进 `front/public` 或 `front/dist`: 会污染前端静态产物，并可能导致 Android 包体递归膨胀 |
| 网盘删除采用“回收站软删除 + 10 天过期清理” | 用户删错文件后需要可恢复，同时共享 blob 仍要等最后引用真正过期后才删除底层对象 | 继续立即物理删除: 不可恢复且误删成本高；额外建独立归档表: 当前需求下实现过重 |

## 待解决问题
- [ ] VS Code 若仍报 `final 字段未在构造器初始化` 之类错误，优先判断为 Lombok / Java Language Server 误报，而不是源码真实错误
- [ ] `front/README.md` 仍是旧模板风格说明，当前真实入口说明以根目录 `README.md` 为准，后续可继续整理
- [ ] 前端构建仍有 chunk size warning，目前不阻塞发布，但后续可以考虑做更细的拆包
- [ ] 线上前端 bundle 当前仍内嵌 `https://api.yoyuzh.xyz/api`，API 子域名异常时会直接表现为“网络异常/登录失败”
- [ ] 当前 Android 工程里的 Google Maven 镜像改动有一部分落在生成/依赖文件中；如果后续升级 Capacitor 或重新 `npm install`，需要重新确认 `front/android/build.gradle`、`front/android/capacitor-cordova-android-plugins/build.gradle`、`front/node_modules/@capacitor/android/capacitor/build.gradle` 的仓库源仍指向可访问镜像
- [ ] 根目录目前仍有 `开发测试账号.md`、`需求文档.md`、`模板/` 等非运行时资料，后续如需继续瘦身可再决定是否迁入 `docs/` 或单独资料目录

## 关键约束
(只写这个任务特有的限制，区别于项目通用规则)
- 仓库根目录没有 `package.json`，不要在根目录执行 `npm` 命令
- 前端真实命令以 `front/package.json` 为准；`npm run lint` 实际是 `tsc --noEmit`
- 后端真实命令以 `backend/pom.xml` / `backend/README.md` 为准；常用的是 `mvn test` 和 `mvn package`
- 修改文件时默认用 `apply_patch`
- 根目录 `.env` 现在是本地密钥、部署参数和服务器 SSH 元信息的统一入口；`.env.example` 是模板，`.env.oss.local` 不再作为主入口
- 已知线上后端服务名是 `my-site-api.service`
- 已知线上后端运行包路径是 `/opt/yoyuzh/yoyuzh-portal-backend.jar`
- 已知新服务器公网 IP 是 `1.14.49.201`
- 已知线上后端额外配置文件是 `/opt/yoyuzh/application-prod.yml`，环境变量文件是 `/opt/yoyuzh/app.env`
- 2026-04-01 已将线上文件桶与前端桶切到多吉云对象存储，后端配置走多吉云临时密钥 API
- 2026-04-02 部署验证：`http://yoyuzh.xyz/` 返回 200，`https://yoyuzh.xyz/` 返回 200，`https://api.yoyuzh.xyz/swagger-ui.html` 最终返回 200，前端资源 `https://yoyuzh.xyz/assets/AdminApp-C9j3tmPO.js` 返回 200
- 2026-04-02 后端服务重启后为 active，启动时间为 `2026-04-02 12:14:25 CST`
- 2026-04-02 再次部署后端，`my-site-api.service` 启动时间更新为 `2026-04-02 17:26:16 CST`，生产接口 `/api/transfer/sessions/offline/mine` 返回已恢复正常
- 2026-04-02 再次发布前端，移动端背景修复对应资源为 `index-DdEYkdGD.js`、`index-qIc3rBab.css`、`AdminApp-DFQ6SlBP.js`
- 2026-04-02 共享 blob 上线前检查：生产库普通文件里 `storage_name` 为空的脏数据数量为 0，总普通文件数为 55
- 2026-04-02 新 blob 模型依赖应用启动时的 `FileBlobBackfillService` 把旧 `storage_name` 行回填到 `blob_id`；如线上表里存在缺少 `storage_name` 且 `blob_id` 为空的历史脏数据，启动会直接失败并暴露该文件 ID
- 2026-04-02 共享 blob 上线后校验：`portal_file.blob_id` 列已存在，普通文件 `blob_id IS NULL` 数量为 0，`portal_file_blob` 当前共有 54 条记录
- 2026-04-02 18:45 CST 线上上传报 `Column 'storage_name' cannot be null`，已定位为旧表结构未把 `portal_file.storage_name` 放宽为可空；已在线执行 `ALTER TABLE portal_file MODIFY storage_name varchar(255) NULL` 修复
- 2026-04-02 19:08 CST 再次发布后端，`my-site-api.service` 启动时间更新为 `2026-04-02 19:08:14 CST`，`https://api.yoyuzh.xyz/swagger-ui.html` 再次确认返回 `200`
- 2026-04-04 私有 `apk/ipa` 下载链路已改为“后端鉴权后返回短时 `https://api.yoyuzh.xyz/_dl/...` 链接，Nginx `secure_link` 校验通过后再代理到 `dl.yoyuzh.xyz` 对象域名”；这样安装包不再走默认 `*.myqcloud.com` 域名，也不再暴露长期可用的公开 `dl` 直链
- 2026-04-04 12:48 CST 已将私有 `apk/ipa` 的 `/_dl` 短时签名修复重新部署到生产；`my-site-api.service` 重启成功，`https://api.yoyuzh.xyz/swagger-ui/index.html` 返回 `200`，带签名的 `https://api.yoyuzh.xyz/_dl/...` 实测返回 `200 OK`
- 2026-04-05 Git 远程 `origin` 已改为私有 Gitea 仓库 `https://git.yoyuzh.xyz/yoyuz/my_site.git`，默认分支 `main` 已建立对 `origin/main` 的跟踪
- 2026-04-05 仓库当前不再把密码文件、本地环境变量文件和前端生产环境文件视为必须忽略项；提交前要主动区分“想入库的私有配置”与“仍应保留本地的临时产物”
- Android 本机构建当前默认 SDK 根目录为 `/Users/mac/Library/Android/sdk`
- Android 本地打包命令链：
  - `cd front && npm run build`
  - `cd front && npx cap sync android`
  - `cd front/android && ./gradlew assembleDebug`
- Android 一键发包命令：
  - `node scripts/deploy-android-apk.mjs`
- Android 调试 APK 当前输出路径：`front/android/app/build/outputs/apk/debug/app-debug.apk`
- Android APK 独立发包命令：
  - `node scripts/deploy-android-release.mjs`
- 服务器登录信息保存在根目录 `.env`，不要把内容写进文档或对外输出

## 参考资料
(相关链接、文档片段、背景资料)
- 根目录说明: `README.md`
- 后端说明: `backend/README.md`
- 仓库协作规范: `AGENTS.md`
- agent / handoff 补充文档: `docs/agents/handoff.md`
- 前端/后端工作区配置: `.vscode/settings.json`、`.vscode/extensions.json`
- Lombok 配置: `lombok.config`
- 最近关键实现位置:
  - 分端会话登录: `backend/src/main/java/com/yoyuzh/auth/AuthService.java`
  - JWT 会话校验: `backend/src/main/java/com/yoyuzh/auth/JwtTokenProvider.java`
  - JWT 过滤器: `backend/src/main/java/com/yoyuzh/config/JwtAuthenticationFilter.java`
  - CORS 配置: `backend/src/main/java/com/yoyuzh/config/CorsProperties.java`、`backend/src/main/resources/application.yml`
  - 密码策略: `backend/src/main/java/com/yoyuzh/auth/PasswordPolicy.java`
  - 网盘树状目录: `front/src/pages/Files.tsx`、`front/src/pages/files-tree.ts`
  - 快传接收页: `front/src/pages/TransferReceive.tsx`
  - 未登录快传权限: `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`、`backend/src/main/java/com/yoyuzh/transfer/TransferService.java`
  - 离线快传历史与详情弹层: `front/src/pages/Transfer.tsx`、`front/src/pages/transfer-state.ts`
  - 移动端入口切换: `front/src/main.tsx`、`front/src/MobileApp.tsx`、`front/src/lib/app-shell.ts`
  - 管理员改密接口: `backend/src/main/java/com/yoyuzh/admin/AdminService.java`
  - 管理台统计与 7 天上线记录: `backend/src/main/java/com/yoyuzh/admin/AdminMetricsService.java`、`backend/src/main/java/com/yoyuzh/admin/AdminDailyActiveUserEntity.java`、`backend/src/main/java/com/yoyuzh/config/JwtAuthenticationFilter.java`
  - 管理台 dashboard 展示与请求折线图: `front/src/admin/dashboard.tsx`、`front/src/admin/dashboard-state.ts`
  - 网盘 blob 模型与回填: `backend/src/main/java/com/yoyuzh/files/core/FileService.java`、`backend/src/main/java/com/yoyuzh/files/core/FileBlob.java`、`backend/src/main/java/com/yoyuzh/files/core/FileBlobBackfillService.java`
  - 网盘回收站与恢复: `backend/src/main/java/com/yoyuzh/files/core/FileService.java`、`backend/src/main/java/com/yoyuzh/files/core/FileController.java`、`backend/src/main/java/com/yoyuzh/files/core/StoredFile.java`、`front/src/pages/RecycleBin.tsx`、`front/src/pages/recycle-bin-state.ts`
  - 前端生产 API 基址: `front/.env.production`
  - Capacitor Android 入口与配置: `front/capacitor.config.ts`、`front/android/`
## 2026-04-08 阶段 1 升级记录

- 已按 Cloudreve 对照升级工程书落地第一阶段最小骨架：后端新增 `/api/v2/site/ping`、`ApiV2Response`、`ApiV2ErrorCode`、`ApiV2Exception` 与 v2 专用异常处理器，旧 `/api/**` 响应模型暂不替换。
- 前端 `front/src/lib/api.ts` 新增 `X-Yoyuzh-Client-Id` 约定和 `apiV2Request()`，内部 API 请求会携带稳定 client id；外部签名上传 URL 不携带该头。
- 修正 `.gitignore` 中 `storage/` 误忽略任意层级 `storage` 包的问题，改为只忽略仓库根 `/storage/` 和本地运行数据 `/backend/storage/`，否则 `backend/src/main/java/com/yoyuzh/files/storage/*` 会被误隐藏。

## 2026-04-08 阶段 2 第一小步记录

- 已新增文件实体模型二期的兼容表模型：`FileEntity`、`StoredFileEntity`、`FileEntityType`，并在 `StoredFile` 上新增 `primaryEntity` 与 `updatedAt`。
- 已新增 `FileEntityBackfillService`，启动后在旧 `FileBlob` 仍保留的前提下，把已有 `StoredFile.blob` 只增量映射到 `FileEntity.VERSION` 与 `StoredFile.primaryEntity`；现有下载、复制、移动、分享、回收站读写路径暂不切换。
- 当时阶段未删除 `FileBlob`，未切换前端，也还未引入上传会话二期。
## 2026-04-08 阶段 2 第二小步记录

- 文件写入路径开始双写 `FileBlob + FileEntity.VERSION`：普通代理上传、直传完成、外部文件导入、分享导入，以及网盘复制复用 blob 时，都会给新 `StoredFile` 写入 `primaryEntity` 并创建 `StoredFileEntity(PRIMARY)` 关系。
- 当前仍不切换读取路径：下载、ZIP、分享详情、回收站等旧业务继续依赖 `StoredFile.blob`，`primaryEntity` 只作为后续版本、缩略图、转码、存储策略迁移的兼容数据。
- 为避免新关系表阻塞现有删除和测试清理，`StoredFileEntity -> StoredFile` 使用数据库级删除级联；`FileEntity.createdBy` 删除用户时置空，保留物理实体审计数据但不阻塞用户清理。
- 2026-04-08 阶段 3 第一小步：新增后端上传会话二期最小骨架，包含 `UploadSession`、`UploadSessionStatus`、`UploadSessionRepository`、`UploadSessionService`，以及受保护的 `/api/v2/files/upload-sessions` 创建、查询、取消接口；旧 `/api/files/upload/**` 上传链路暂不切换，前端上传队列暂不改动。
- 2026-04-08 阶段 3 第二小步：新增 `POST /api/v2/files/upload-sessions/{sessionId}/complete`，v2 上传会话可从 `CREATED` 进入 `COMPLETING` 并复用旧 `FileService.completeUpload()` 完成 `FileBlob + StoredFile + FileEntity.VERSION` 落库，成功后标记 `COMPLETED`；取消、失败、过期会话不能完成。实际分片内容上传和前端上传队列仍未切换。
- 2026-04-08 阶段 3 第三小步：新增 `PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}`，用于记录当前用户上传会话的 part 元数据到 `uploadedPartsJson`，并把会话状态从 `CREATED` 推进到 `UPLOADING`；该接口只记录 `etag/size` 等状态，不承担真正的对象存储分片内容写入或合并。
- 2026-04-08 阶段 3 第四小步：`UploadSessionService` 新增定时过期清理，按小时扫描 `CREATED/UPLOADING/COMPLETING` 且已过期的会话，尝试删除对应临时 `blobs/...` 对象，并把会话标记为 `EXPIRED`；`COMPLETED/CANCELLED/FAILED/EXPIRED` 不在本轮清理范围内。
- 2026-04-08 multipart 评估结论：暂不把 v2 上传会话直接接入真实对象存储分片写入/合并。当前 `FileContentStorage` 仍是单对象上传/校验抽象，缺少 multipart uploadId、part URL 预签名、complete/abort 语义；立即接入会把上传会话写死在当前多吉云 S3 配置上，并让过期清理误以为 `deleteBlob` 能释放未完成分片。下一步先做阶段 4 存储策略与能力声明骨架，再按 `multipartUpload` 能力接 S3 multipart。
- 2026-04-08 阶段 4 第一小步：新增 `StoragePolicy`、`StoragePolicyType`、`StoragePolicyCredentialMode`、`StoragePolicyCapabilities` 与 `StoragePolicyService`，启动时把当前 `app.storage.provider` 映射成一条默认策略；当时本地策略声明 `serverProxyDownload=true`、`multipartUpload=false`，多吉云/S3 兼容策略也先声明为 `directUpload=true`、`signedDownloadUrl=true`、`requiresCors=true`、`multipartUpload=false`。新 v2 上传会话会记录默认 `storagePolicyId`，但旧上传下载路径和前端上传队列仍未切换。
- 2026-04-08 合并 `files/storage` 补提交后修复：`S3FileContentStorage` 改为复用 `DogeCloudS3SessionProvider` / `DogeCloudTmpTokenClient` 获取并缓存运行期 `S3Client` 与 `S3Presigner`，保留生产构造器 `S3FileContentStorage(FileStorageProperties)`，同时提供测试用注入构造器；S3 直传、签名下载、上传校验、读旧对象键 fallback、rename/move/copy、离线快传对象读写继续通过 `FileContentStorage` 统一抽象。
- 2026-04-08 阶段 4 第二小步：新写入和回填生成的 `FileEntity.VERSION` 会记录默认 `StoragePolicy.id` 到 `storagePolicyId`，让物理实体可以追踪归属存储策略；复用已有 `FileEntity` 时只增加引用计数，不覆盖历史实体策略字段。旧 `/api/files/**` 读取路径仍继续依赖 `StoredFile.blob`。
- 2026-04-08 阶段 4 第三小步：新增管理员只读存储策略查看能力，后端暴露 `GET /api/admin/storage-policies`，前端管理台新增“存储策略”资源列表和能力矩阵展示；该接口只返回白名单 DTO 与结构化 `StoragePolicyCapabilities`，不暴露凭证，也不支持新增/编辑/启停/删除策略。
- 2026-04-08 阶段 5 第一小步：新增用户侧 v2 文件搜索最小闭环，后端暴露受保护的 `GET /api/v2/files/search`，复用 `StoredFile` 查询当前用户未删除文件，支持 `name`、`type=file|directory|folder|all`、`sizeGte/sizeLte`、`createdGte/createdLte`、`updatedGte/updatedLte` 与分页；同时新增 `FileMetadata` / `FileMetadataRepository` 扩展表骨架，暂不迁移回收站字段、暂不接入标签/metadata 过滤、暂不改前端上传队列和旧 `/api/files/**` 行为。
- 2026-04-08 阶段 5 第二小步：前端桌面端接入最小搜索下游，新增 `front/src/lib/file-search.ts` 和 `front/src/lib/file-search.test.ts`，桌面 `front/src/pages/Files.tsx` 可通过 v2 search 单独搜索并展示结果，不写入 `getFilesListCacheKey(...)`，也不影响原有目录缓存和上传主链路；移动端暂未接入搜索，后续可按同一 helper 补入。
- 2026-04-08 阶段 5 第三小步：新增分享二期后端最小骨架。`FileShareLink` 增加 `passwordHash`、`expiresAt`、`maxDownloads`、`downloadCount`、`viewCount`、`allowImport`、`allowDownload`、`shareName`；新增 `com.yoyuzh.api.v2.shares` 与 `ShareV2Service`，提供 v2 创建、公开读取、密码校验、导入、我的分享列表和删除。公开访问包括 `GET /api/v2/shares/{token}`、`POST /api/v2/shares/{token}/verify-password`，以及 `GET /api/v2/shares/{token}?download=1` 下载入口；后者会统一校验过期时间、密码、`allowDownload` 和 `maxDownloads`，成功后复用现有下载链路并递增 `downloadCount`。创建、导入、我的分享、删除仍需登录；v2 导入仍会先校验过期时间、密码、`allowImport` 和 `maxDownloads`，再复用旧导入持久化链路；旧 `/api/files/share-links/**` 继续兼容。
- 2026-04-08 阶段 5 第四小步：新增文件事件流前后端最小闭环。后端落地 `FileEvent` / `FileEventType` / `FileEventRepository` / `FileEventService`，并提供受保护的 `GET /api/v2/files/events?path=/` SSE 入口；当前可按用户广播、按路径前缀过滤、按 `X-Yoyuzh-Client-Id` 抑制自身事件，首次连接会收到 `READY` 事件。前端新增 fetch-stream 版 `front/src/lib/file-events.ts`，不直接使用无法带鉴权头的原生 `EventSource`；桌面 `Files` 与移动 `MobileFiles` 已订阅当前目录事件，收到文件变更后失效当前目录缓存并刷新列表，搜索结果状态不被清空。
- 2026-04-09 阶段 5 第五小步：上传会话二期后端接入真实 multipart。`FileContentStorage` 新增 `createMultipartUpload/prepareMultipartPartUpload/completeMultipartUpload/abortMultipartUpload` 抽象，`S3FileContentStorage` 用预签名 `UploadPart` 和 `Complete/AbortMultipartUpload` 落地实现；默认 S3 存储策略能力改为 `multipartUpload=true`。`UploadSession` 新增 `multipartUploadId`，创建会话时若默认策略支持 multipart 会立即初始化 uploadId；v2 会话响应新增 `multipartUpload`，并开放 `GET /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}/prepare` 返回单分片直传地址。完成会话时会先按已记录 part 元数据提交 multipart complete，再复用旧 `FileService.completeUpload()` 落库；过期清理也会对未完成 multipart 执行 abort。前端上传队列仍未切到这条新链路。
- 2026-04-08 阶段 6 第一步：新增后台任务框架与 worker 最小骨架。后端新增 `BackgroundTask` / `BackgroundTaskType` / `BackgroundTaskStatus` / `BackgroundTaskRepository` / `BackgroundTaskService`，并暴露受保护的 `GET /api/v2/tasks`、`GET /api/v2/tasks/{id}`、`DELETE /api/v2/tasks/{id}` 以及 `POST /api/v2/tasks/archive`、`POST /api/v2/tasks/extract`、`POST /api/v2/tasks/media-metadata` 创建接口；任务创建入口会校验 `fileId` 属于当前用户、未删除、请求 `path` 匹配服务端派生逻辑路径，并按任务类型限制目录、zip-compatible 解压源和媒体文件，任务 state 使用服务端文件信息。
- 2026-04-09 阶段 6 第二步：`MEDIA_META` 之外的后台任务开始真实化。`ARCHIVE` 任务现在会派生 `outputPath/outputFilename`，由 `ArchiveBackgroundTaskHandler` 复用 `FileService.buildArchiveBytes(...)` 把目录或单文件打成 zip，并通过 `importExternalFile(...)` 写回同级目录；`EXTRACT` 任务现在会派生 `outputPath/outputDirectoryName`，由 `ExtractBackgroundTaskHandler` 读取 zip-compatible 归档、剥离共享根目录、支持单文件归档直接恢复到父目录，并通过 `FileService.importExternalFilesAtomically(...)` 在预检冲突后批量落库，失败时清理已写入的 `blobs/...`，避免留下孤儿 blob。worker 仍按 `QUEUED -> RUNNING -> COMPLETED/FAILED` 驱动，当前未实现非 zip 解压格式、缩略图/视频时长，以及 archive/extract 的前端入口。
- 2026-04-09 阶段 6 第三步：后台任务新增最小 progress 字段，但仍不做假百分比。`BackgroundTaskService` 现在会在 `publicStateJson` 里统一维护 `phase`：创建时为 `queued`，claim 后为 `running`，worker 开始执行时按任务类型细化成 `archiving` / `extracting` / `extracting-metadata`，完成/失败/取消时分别收口为 `completed` / `failed` / `cancelled`。`GET /api/v2/tasks/**` 会直接透出这些阶段；`BackgroundTaskV2ControllerIntegrationTest` 也已覆盖 archive/extract 完成态、extract 失败态和取消态的 phase 回读。
- 2026-04-09 阶段 6 第六步：`ARCHIVE/EXTRACT` 后台任务补了真实条目计数进度。worker 现在会把 progress reporter 传入 handler；`ARCHIVE` 会按实际写入 zip entry 推进 `processedFileCount/totalFileCount` 与 `processedDirectoryCount/totalDirectoryCount`，`EXTRACT` 会按实际创建目录和导入文件推进同一组字段。重试和启动恢复仍按 `privateStateJson` 重建公开 state，因此这些运行期计数字段不会被错误保留到下一次执行。
- 2026-04-09 阶段 6 第四步：后台任务补了最小手动重试闭环。后端新增 `POST /api/v2/tasks/{id}/retry`，只允许当前用户把自己 `FAILED` 状态的任务重新置回 `QUEUED`；重试时会清空 `finishedAt/errorMessage`，按 `privateStateJson` 重建公开 state，并把 `publicStateJson.phase` 重置为 `queued`，不会保留失败时写入的 `worker` 等瞬时字段。
- 2026-04-09 阶段 6 第五步：后台任务补了服务启动时的 `RUNNING` 恢复。最初版本会在 `ApplicationReadyEvent` 后直接把遗留 `RUNNING` 任务重排回 `QUEUED`；2026-04-09 晚些时候又升级为只回收 lease 已过期或旧数据里缺少 lease 的 `RUNNING` 任务，避免多实例场景误抢活跃 worker。
- 2026-04-09 阶段 6 第七步：后台任务补了保守的自动重试/退避骨架。`BackgroundTask` 现在有 `attemptCount/maxAttempts/nextRunAt`；最初 `ARCHIVE`、`EXTRACT`、`MEDIA_META` 都默认最多执行 3 次，worker claim 时会递增 `attemptCount`。同日后续又升级为按任务类型区分预算与退避：`ARCHIVE` 最多 4 次、`EXTRACT` 最多 3 次、`MEDIA_META` 最多 2 次；失败分类从布尔可重试升级为 `UNSUPPORTED_INPUT/DATA_STATE/TRANSIENT_INFRASTRUCTURE/RATE_LIMITED/UNKNOWN`，公开 state 会写入 `failureCategory` 与 `retryDelaySeconds`，并按类别和任务类型决定是否自动回队列及退避时长。
- 2026-04-09 阶段 6 第八步：后台任务补了运行期 heartbeat 与多实例 lease。`BackgroundTask` 现在持久化 `leaseOwner/leaseExpiresAt/heartbeatAt`；worker 每次 claim 会写入唯一 `workerOwner` 并续租，运行中 progress/完成/失败都会刷新 heartbeat。`ARCHIVE/EXTRACT` 的公开 state 现已附带真实 `progressPercent`，`MEDIA_META` 会暴露 `metadataStage`；多实例下会先回收 lease 过期的 `RUNNING` 任务，再领取 `QUEUED` 任务，旧 worker 若丢失 owner 则不会再覆盖新状态。
- 2026-04-09 桌面端 `Files` 已补最近 10 条后台任务面板，支持查看状态、取消 `QUEUED/RUNNING` 任务，并可为当前选中文件创建媒体信息提取任务；移动端和 archive/extract 的前端入口暂未接入。
- 2026-04-09 files 后端结构清理：`backend/src/main/java/com/yoyuzh/files` 不再平铺大部分领域类，现已按职责重组为 `core/upload/share/search/events/tasks/storage/policy` 八个子包；类名、接口路径、数据库表名/字段名和现有测试语义保持不变，主要是通过 package 重组、import 修正和测试路径同步降低后续继续演进 upload/share/search/events/tasks/storage-policy 的维护摩擦。
- 2026-04-09 存储策略管理后端继续收口：管理员接口已从只读 `GET /api/admin/storage-policies` 扩展到 `POST /api/admin/storage-policies`、`PUT /api/admin/storage-policies/{policyId}`、`PATCH /api/admin/storage-policies/{policyId}/status` 和 `POST /api/admin/storage-policies/migrations`。当前支持新增、编辑、启停非默认策略，并可创建 `STORAGE_POLICY_MIGRATION` 后台任务；默认策略不能停用，仍不支持删除策略或切换默认策略。
- 2026-04-09 存储策略与上传路径后端继续推进：`STORAGE_POLICY_MIGRATION` 现已从 skeleton 升级为“当前活动存储后端内的真实迁移”。worker 会限制源/目标策略必须同类型，读取旧 `FileBlob` 对象字节，写入新的 `policies/{targetPolicyId}/blobs/...` object key，同步更新 `FileBlob.objectKey` 与 `FileEntity.VERSION(objectKey, storagePolicyId)`，并在事务提交后异步清理旧对象；若处理中失败，会删除本轮新写对象并依赖事务回滚元数据。与此同时，v2 upload session 现在会按默认策略能力决策 `uploadMode=PROXY|DIRECT_SINGLE|DIRECT_MULTIPART`：`directUpload=false` 时走 `POST /api/v2/files/upload-sessions/{sessionId}/content` 代理上传，`directUpload=true && multipartUpload=false` 时走 `GET /api/v2/files/upload-sessions/{sessionId}/prepare` 单请求直传，`multipartUpload=true` 时继续走现有分片 prepare/record/complete 链路；会话响应还会附带 `strategy`，把当前模式下的后续后端入口模板显式返回给前端；旧 `/api/files/upload/initiate` 也会尊重默认策略的 `directUpload/maxObjectSize`。
- 2026-04-09 前端 files 上传链路已切到 v2 upload session：桌面端 `FilesPage`、移动端 `MobileFilesPage` 和 `saveFileToNetdisk()` 现在统一通过 `front/src/lib/upload-session.ts` 走 `create/get/cancel/prepare/content/part-prepare/part-record/complete` 全套 helper，并按后端返回的 `uploadMode + strategy` 自动选择 `PROXY / DIRECT_SINGLE / DIRECT_MULTIPART`。旧 `/api/files/upload/**` 当前仍保留给头像等非 files 子系统入口使用。
