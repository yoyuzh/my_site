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
  - 网盘 blob 模型与回填: `backend/src/main/java/com/yoyuzh/files/FileService.java`、`backend/src/main/java/com/yoyuzh/files/FileBlob.java`、`backend/src/main/java/com/yoyuzh/files/FileBlobBackfillService.java`
  - 网盘回收站与恢复: `backend/src/main/java/com/yoyuzh/files/FileService.java`、`backend/src/main/java/com/yoyuzh/files/FileController.java`、`backend/src/main/java/com/yoyuzh/files/StoredFile.java`、`front/src/pages/RecycleBin.tsx`、`front/src/pages/recycle-bin-state.ts`
  - 前端生产 API 基址: `front/.env.production`
  - Capacitor Android 入口与配置: `front/capacitor.config.ts`、`front/android/`
## 2026-04-08 阶段 1 升级记录

- 已按 Cloudreve 对照升级工程书落地第一阶段最小骨架：后端新增 `/api/v2/site/ping`、`ApiV2Response`、`ApiV2ErrorCode`、`ApiV2Exception` 与 v2 专用异常处理器，旧 `/api/**` 响应模型暂不替换。
- 前端 `front/src/lib/api.ts` 新增 `X-Yoyuzh-Client-Id` 约定和 `apiV2Request()`，内部 API 请求会携带稳定 client id；外部签名上传 URL 不携带该头。
- 修正 `.gitignore` 中 `storage/` 误忽略任意层级 `storage` 包的问题，改为只忽略仓库根 `/storage/` 和本地运行数据 `/backend/storage/`，否则 `backend/src/main/java/com/yoyuzh/files/storage/*` 会被误隐藏。

## 2026-04-08 阶段 2 第一小步记录

- 已新增文件实体模型二期的兼容表模型：`FileEntity`、`StoredFileEntity`、`FileEntityType`，并在 `StoredFile` 上新增 `primaryEntity` 与 `updatedAt`。
- 已新增 `FileEntityBackfillService`，启动后在旧 `FileBlob` 仍保留的前提下，把已有 `StoredFile.blob` 只增量映射到 `FileEntity.VERSION` 与 `StoredFile.primaryEntity`；现有下载、复制、移动、分享、回收站读写路径暂不切换。
- 当前阶段未删除 `FileBlob`，未切换前端，未引入上传会话二期。
## 2026-04-08 阶段 2 第二小步记录

- 文件写入路径开始双写 `FileBlob + FileEntity.VERSION`：普通代理上传、直传完成、外部文件导入、分享导入，以及网盘复制复用 blob 时，都会给新 `StoredFile` 写入 `primaryEntity` 并创建 `StoredFileEntity(PRIMARY)` 关系。
- 当前仍不切换读取路径：下载、ZIP、分享详情、回收站等旧业务继续依赖 `StoredFile.blob`，`primaryEntity` 只作为后续版本、缩略图、转码、存储策略迁移的兼容数据。
- 为避免新关系表阻塞现有删除和测试清理，`StoredFileEntity -> StoredFile` 使用数据库级删除级联；`FileEntity.createdBy` 删除用户时置空，保留物理实体审计数据但不阻塞用户清理。
- 2026-04-08 阶段 3 第一小步：新增后端上传会话二期最小骨架，包含 `UploadSession`、`UploadSessionStatus`、`UploadSessionRepository`、`UploadSessionService`，以及受保护的 `/api/v2/files/upload-sessions` 创建、查询、取消接口；旧 `/api/files/upload/**` 上传链路暂不切换，前端上传队列暂不改动。
- 2026-04-08 阶段 3 第二小步：新增 `POST /api/v2/files/upload-sessions/{sessionId}/complete`，v2 上传会话可从 `CREATED` 进入 `COMPLETING` 并复用旧 `FileService.completeUpload()` 完成 `FileBlob + StoredFile + FileEntity.VERSION` 落库，成功后标记 `COMPLETED`；取消、失败、过期会话不能完成。实际分片内容上传和前端上传队列仍未切换。
- 2026-04-08 阶段 3 第三小步：新增 `PUT /api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}`，用于记录当前用户上传会话的 part 元数据到 `uploadedPartsJson`，并把会话状态从 `CREATED` 推进到 `UPLOADING`；该接口只记录 `etag/size` 等状态，不承担真正的对象存储分片内容写入或合并。
- 2026-04-08 阶段 3 第四小步：`UploadSessionService` 新增定时过期清理，按小时扫描 `CREATED/UPLOADING/COMPLETING` 且已过期的会话，尝试删除对应临时 `blobs/...` 对象，并把会话标记为 `EXPIRED`；`COMPLETED/CANCELLED/FAILED/EXPIRED` 不在本轮清理范围内。
