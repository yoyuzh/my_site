下面这份是**工程级**的 TODO List（Markdown），按“能上线”的路径拆好了：里程碑 → 任务 → 验收点。你前端已经做了一部分，就从 **FE-Desktop / FE-Apps** 里把已完成的勾上即可。

---

# Web Desktop 项目工程 TODO（可上线版）

> 维护规则：
>
> * 每个任务尽量做到“可交付 + 可验收”。
> * 任务粒度：1~4 小时能完成为宜。
> * 每周至少推进一个 Milestone 到可演示状态。

---

## 0. 里程碑总览

* [ ] **M0：工程骨架就绪（能跑通 dev / staging）**
* [ ] **M1：账号体系 + 桌面壳可用（基础可演示）**
* [ ] **M2：网盘 MVP（OSS 直传闭环）**
* [ ] **M3：分享/审计/配额/管理后台（上线门槛）**
* [ ] **M4：Campus BFF 接 Rust API（课表/成绩缓存降级）**
* [ ] **M5：论坛/地图完善 + 监控告警 + 上线演练**

---

## 1. M0 工程骨架就绪

### Repo / 工程结构

* [ ] 初始化 mono-repo 或多 repo 结构（建议：`frontend/` `backend/` `infra/`）
* [ ] 统一 lint/format（ESLint/Prettier + 后端 formatter）
* [ ] 统一 commit 规范（可选：commitlint）
* [ ] 统一环境变量模板：`.env.example`（前后端分开）
* [ ] 基础 README：本地启动、部署、配置项说明

### 本地开发环境

* [ ] docker-compose：db + redis + backend + (可选) nginx
* [ ] 一键启动脚本：`make dev` / `npm run dev:all`
* [ ] staging 配置：独立域名/反代/证书（哪怕自签）

### 基础 CI（至少跑检查）

* [ ] PR 触发：lint + typecheck + unit test（最小集合）
* [ ] build 产物：frontend build / backend build

**验收点**

* [ ] 新电脑 clone 后 30 分钟内能跑起来（含 db）

---

## 2. M1 账号体系 + 桌面壳

### BE-Auth

* [ ] 用户注册/登录（JWT + refresh 或 session 二选一）
* [ ] 密码加密（argon2/bcrypt）
* [ ] `GET /auth/me`
* [ ] 登录失败限流（例如 5 次/5 分钟）
* [ ] 基础用户状态：normal / banned
* [ ] request_id 全链路（middleware）

### FE-Auth

* [ ] 登录/注册/找回页面
* [ ] token/会话续期策略
* [ ] 全局错误处理（统一 toast + request_id）

### FE-Desktop（你已做一部分：这里把你已有的勾上）

* [ ] 桌面布局：图标/分组/壁纸/主题
* [ ] 窗口系统：打开/关闭/最小化/最大化/拖拽/层级
* [ ] 最近使用 / 收藏
* [ ] 全局搜索：应用搜索（先做）
* [ ] 通知中心壳（先只做 UI）

### BE-Desktop

* [ ] user_settings 表：layout/theme/wallpaper
* [ ] `GET /desktop/settings` / `PUT /desktop/settings`
* [ ] `GET /desktop/apps`（服务端下发应用配置，方便后续开关）

**验收点**

* [ ] 新用户登录后能看到桌面；布局修改刷新后不丢
* [ ] 被封禁用户无法登录（提示明确）

---

## 3. M2 网盘 MVP（OSS 直传闭环）

### BE-Drive 元数据

* [ ] files 表（user_id, parent_id, name, size, mime, oss_key, deleted_at…）
* [ ] 目录增删改查：create folder / rename / move / list
* [ ] 软删除 + 回收站 list/restore
* [ ] 文件名净化（防 XSS/路径注入）

### BE-OSS 直传

* [ ] `POST /drive/upload/init`：生成 oss_key + STS/Policy（带过期时间）
* [ ] 分片策略：chunk_size / multipart（建议直接支持）
* [ ] `POST /drive/upload/complete`：写入元数据（校验 size/etag）
* [ ] `GET /drive/download/{id}`：签名 URL（短期有效）
* [ ] 下载审计：记录 download_sign

### FE-Drive

* [ ] 文件列表：分页/排序/面包屑
* [ ] 上传：小文件 + 大文件分片 + 断点续传
* [ ] 上传队列：暂停/继续/失败重试
* [ ] 预览：图片/PDF/文本
* [ ] 删除/恢复/彻底删除（回收站）
* [ ] 文件搜索（文件名）

**验收点**

* [ ] 上传→列表出现→预览/下载→删除→回收站恢复闭环
* [ ] 网络断开后能续传（至少同一次会话内）

---

## 4. M3 分享 / 审计 / 配额 / 管理后台（上线门槛）

### BE-Share

* [ ] 创建分享：有效期、提取码、权限（预览/下载）
* [ ] 分享访问页：`GET /share/{token}`
* [ ] 下载：`POST /share/{token}/download`（校验提取码后返回签名 URL）
* [ ] 撤销分享：立即失效
* [ ] 分享访问审计（ip/ua/time/count）

### BE-Quota & RateLimit

* [ ] 用户配额：总容量、单文件大小、日上传/日下载
* [ ] 配额校验：upload/init、complete、download/sign
* [ ] 限流：登录、绑定校园、成绩刷新、签名下载、分享访问

### BE-Audit

* [ ] audit_logs：关键操作埋点（upload_init/upload_complete/download_sign/share_create…）
* [ ] 查询接口：按 user/action/time 过滤（管理员）

### Admin（最小管理后台）

* [ ] 用户管理：封禁/解封
* [ ] 配额配置：默认值 + 单用户覆盖（可选）
* [ ] OSS 配置：bucket/STS 策略（至少可查看）
* [ ] 审计查询页

**验收点**

* [ ] 超配额时前后端提示一致且不可绕过
* [ ] 分享链接可用、可撤销、访问可审计
* [ ] 管理员能查到关键操作日志

---

## 5. M4 Campus BFF（接 Rust API：课表/成绩）

> 核心：**平台后端不让前端直连 Rust API**，统一做鉴权、缓存、熔断、错误码映射。

### BE-Campus 绑定与凭据

* [ ] `POST /campus/bind`：绑定校园账号（加密存储 credential / 或保存 rust session_token）
* [ ] `POST /campus/unbind`：解绑并删除凭据
* [ ] 凭据加密：密钥不入库（env + KMS 可选）
* [ ] 绑定/查询限流（防封控）

### BE-Campus Rust API 网关层

* [ ] Rust API client：超时、重试（只读）、熔断
* [ ] 健康检查：/healthz 探测 + 指标
* [ ] DTO 适配层：Rust 返回字段变化不直接打爆前端
* [ ] 错误码映射：Rust error → 平台 error code

### BE-Campus 缓存与降级

* [ ] campus_cache：课表/成绩 TTL（课表 12h，成绩 24h）
* [ ] 手动刷新冷却时间（成绩建议更长）
* [ ] Rust 不可用时返回缓存 + 标注更新时间

### FE-Campus

* [ ] 绑定页面（学号/密码或 token）
* [ ] 课表周视图/日视图
* [ ] 成绩学期视图 + 列表
* [ ] “刷新”按钮（带冷却提示）
* [ ] “数据更新时间 / 当前为缓存”提示

**验收点**

* [ ] Rust API 挂了：仍能展示缓存且不白屏
* [ ] 频繁刷新会被限流并提示

---

## 6. M5 论坛/地图完善 + 监控告警 + 上线演练

### Forum（按 Rust API 能力）

* [ ] 板块列表/帖子列表/详情/评论
* [ ] 发帖/评论（幂等键 Idempotency-Key）
* [ ] 内容风控：频率限制 + 基础敏感词（最小）
* [ ] 举报入口（最小）
* [ ] 通知：回复/提及（站内通知）

### Map

* [ ] POI 展示：分类 + 搜索
* [ ] 地图 SDK 接入（Leaflet/高德/腾讯择一）
* [ ] POI 缓存 7d + 更新策略
* [ ]（可选）POI 后台维护

### Observability（上线前必须补）

* [ ] 指标：API 错误率、P95、Rust 成功率、OSS 上传失败率
* [ ] 日志：结构化 + request_id
* [ ] 告警：Rust 健康异常、错误率激增、DB/Redis 异常
* [ ] 错误追踪：Sentry 或同类（可选但强建议）

### 安全加固（上线前必做清单）

* [ ] CSP/安全头（X-Frame-Options 等）
* [ ] 上传文件类型限制 + 文件名净化
* [ ] 权限回归测试：越权访问用例全覆盖
* [ ] Secrets 全部迁移到安全配置（不进仓库）

### 上线演练

* [ ] staging 环境全链路演练（含 OSS、Rust API）
* [ ] 灰度发布流程（最小：可回滚）
* [ ] 数据库备份与恢复演练
* [ ] 压测（最少测下载签名/列表/校园查询）

**验收点**

* [ ] staging → prod 一键发布可回滚
* [ ] 关键告警触发能收到（邮件/IM 随便一种）

---

## 7. 你当前“前端已做一部分”的对齐清单（快速标记）

把你已经完成的模块在这里勾上，方便我后续给你拆“下一步最优先做什么”：

* [ ] 桌面图标布局
* [ ] 窗口拖拽/层级
* [ ] 应用打开/关闭/最小化
* [ ] 主题/壁纸
* [ ] 网盘 UI（列表/上传面板/预览）
* [ ] 校园 UI（课表/成绩/论坛/地图）
* [ ] 游戏应用容器

---

## 8. 最小上线 Checklist（不做这些别上线）

* [ ] 后端鉴权与资源隔离（不可只靠前端）
* [ ] OSS 长期密钥不下发前端（只给 STS/签名）
* [ ] 下载签名短期有效 + 审计
* [ ] 限流（登录/绑定/校园刷新/签名下载/分享访问）
* [ ] Rust API 超时/熔断/缓存降级
* [ ] 结构化日志 + request_id
* [ ] staging 环境演练 + 回滚方案

