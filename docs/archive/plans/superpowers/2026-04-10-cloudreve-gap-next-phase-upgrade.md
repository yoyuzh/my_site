# Cloudreve Gap Next-Phase Upgrade Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` when implementing this plan. Keep the checkbox state updated as work lands.

**Goal:** 在不偏离当前产品方向的前提下，把项目中“对比 Cloudreve 仍明显缺失”的能力拆成可执行的后续升级阶段，优先补齐最能提升网盘完成度和平台化能力的部分，而不是盲目追平 Cloudreve 的全部生态。

**Repository:** `C:\Users\yoyuz\Documents\code\my_site`

**Decision:** 当前项目已经完成 v2 上传会话、存储策略、分享二期、搜索骨架、SSE 文件事件、后台任务骨架、回收站、Android 壳和前后端视觉重构。后续计划只覆盖“尚未完成且仍值得做”的能力，不重复规划已经落地的阶段。

## 1. Current Baseline

下列能力已完成，不应重新当作“待做”：

- v2 upload session 已支持 `PROXY` / `DIRECT_SINGLE` / `DIRECT_MULTIPART`
- 存储策略管理、迁移任务和策略能力声明已落地
- 分享二期、文件搜索、文件事件 SSE、后台任务框架已落地
- 回收站、媒体元数据任务、桌面端任务面板已落地
- 前后端 UI 已完成一次系统性重构

当前仍明确未完成或仅完成一半的点：

- 后端尚未接入 Redis；当前没有 Spring Cache，也没有跨实例缓存/会话总线
- 移动端文件搜索未接入
- `ARCHIVE` / `EXTRACT` 前端入口未接入，移动端任务入口也未接入
- 旧下载/分享详情/ZIP/回收站读取路径仍依赖 `StoredFile.blob`，尚未切到 `primaryEntity`
- 仅有媒体元数据提取，没有缩略图、视频时长、预览资源管线
- 没有 WebDAV
- 没有远程离线下载器能力
- 没有 OIDC / OAuth scope / 桌面同步客户端协议
- 不建议当前阶段直接做完整 WOPI / Office 在线协作

## 2. Scope And Priority

### P0: 先补齐现有平台里的断点

这部分不做新产品线，只把已经有骨架但没闭环的能力补完整：

1. Redis 基础接入与缓存边界落地
2. 移动端搜索接入
3. `ARCHIVE` / `EXTRACT` 前端入口
4. 移动端任务入口
5. 旧读取路径从 `StoredFile.blob` 迁到 `primaryEntity`

### Admin Console Alignment

参考成熟项目的后台目录，当前项目后续管理台不应只停留在 `dashboard / users / files / storage-policies` 四类资源，而应逐步演进为以下信息架构：

1. 面板首页
2. 参数设置
3. 文件系统
4. 存储策略
5. 节点
6. 用户组
7. 用户
8. 文件
9. 文件 Blob
10. 分享
11. 后台任务
12. 订单
13. 事件
14. 滥用举报
15. OAuth 应用

其中适合当前项目近期推进的只有：

- 参数设置
- 文件系统
- 存储策略
- 用户
- 文件
- 文件 Blob
- 分享
- 后台任务
- OAuth 应用（先预留，不急于完整实现）

当前阶段明确延后：

- 节点
- 用户组
- 订单
- 事件独立审计中心
- 滥用举报

### P1: 预览与媒体管线

这是最值得补的 Cloudreve 差距：

1. 图片缩略图
2. 视频 poster / 时长
3. 文件列表与详情中的预览消费
4. 失败状态与 metadata 持久化

### P2: WebDAV 最小可用版

只做单用户私有网盘最小读写，不提前做复杂共享挂载、锁协商和第三方 scope。

### P3: 生态扩展 backlog

这部分先保留为后续阶段，不在最近一轮升级中直接开工：

1. 远程离线下载
2. OIDC / OAuth scope
3. 桌面同步客户端协议
4. WOPI / Office 在线协作

## 3. Non-Goals

- 不把项目改造成 Cloudreve 克隆
- 不新增与当前业务方向不一致的组织/团队协作大系统
- 不在当前阶段接入完整 WOPI
- 不为了“对齐功能表”而重做现有快传业务
- 不引入仓库中不存在的验证命令

---

## 4. Stage 1: Redis Cache Foundation

**Goal:** 为后端引入 Redis，先解决真正适合走缓存或跨实例共享的状态，不把所有内存结构机械搬过去。

**Why now:**

- 当前后端没有 `spring-boot-starter-data-redis`
- `TransferSessionStore` 仍是进程内 `ConcurrentHashMap`
- `FileEventService` 的订阅和广播只在单实例内有效
- `DogeCloudS3SessionProvider` 只有本机进程内临时会话缓存
- 你已经明确希望 Redis 承担登录态 / token 黑名单、热门目录缓存、分布式锁、上传状态缓存和小规模队列 broker

**Files likely involved:**

- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/java/com/yoyuzh/config/*`
- `backend/src/main/java/com/yoyuzh/transfer/*`
- `backend/src/main/java/com/yoyuzh/files/events/*`
- `backend/src/main/java/com/yoyuzh/files/storage/*`
- `backend/src/test/java/com/yoyuzh/**`
- `docs/architecture.md`
- `docs/api-reference.md`
- `memory.md`

- [x] **Step 1: 接入 Spring Data Redis 与配置骨架**
  - 新增 Redis 依赖
  - 在 `application.yml` / `application-dev.yml` 加入 `spring.data.redis.*` 与 `app.redis.*`
  - Redis 必须允许关闭，不强制 dev 环境依赖外部服务

- [x] **Step 2: 明确缓存分层，不做一锅炖**
  - `Spring Cache`：用于热门目录和热点只读查询
  - `Redis KV`：用于登录态派生状态、token 黑名单、快传会话、上传状态
  - `Redis Lock`：用于分布式锁
  - `Redis Pub/Sub`：用于多实例文件事件分发
  - `Redis List/Stream` 或轻量队列表：用于小规模 broker
  - 不把 JPA 实体直接无脑全量缓存

- [x] **Step 3: 接入登录态 / token 黑名单**
  - 保持当前 JWT + refresh token 主模型不变
  - 新增 access token / refresh token 撤销或踢下线后的 Redis 黑名单能力
  - 黑名单 TTL 与 token 剩余有效期对齐，避免永久堆积
  - 用户改密、封禁、管理员重置密码、同端挤下线等场景统一走这套失效机制

- [x] **Step 4: 接入热门目录缓存**
  - 优先缓存 `/api/files/list` 的热点目录页结果，而不是所有目录
  - 缓存 key 至少包含 `userId + path + page + size + sort context`
  - 文件创建、删除、移动、重命名、恢复、导入、上传完成后精准失效相关目录
  - 不让搜索结果、回收站列表和任务列表混进同一套 key

- [x] **Step 5: 接入分布式锁**
  - 先覆盖会发生并发冲突或重复执行风险的路径
  - 优先考虑上传完成、存储策略迁移、后台任务 claim / retry、回收站恢复、目录批量导入
  - 锁必须带 TTL 和 owner 标识，避免死锁
  - 不用 Redis 锁去替代数据库事务

- [x] **Step 6: 接入上传状态缓存**
  - 用于保存上传中的短生命周期状态，而不是取代数据库中的最终事实
  - 适合承载 chunk 进度、最近心跳、瞬时速度、前端轮询状态
  - `UploadSession` 仍保留数据库持久化和最终完成语义
  - Redis 状态过期后不应影响已完成或已失败的最终结果判断

- [x] **Step 7: 引入小规模队列 broker**
  - 目标不是替代当前数据库任务系统，而是给轻量异步链路和跨实例触发提供 broker
  - 优先承载文件事件转发、缩略图触发、媒体处理触发、低成本异步通知
  - 当前规模下可接受 Redis broker，但要明确“不是高可靠消息系统”
  - 大任务最终状态仍以数据库 `BackgroundTask` 为准
  - 2026-04-10 首批落地先收敛到“媒体文件落库后的 `MEDIA_META` 自动触发”，文件事件跨实例广播仍留给 Step 9 的 Redis pub/sub

- [x] **Step 8: 把 `TransferSessionStore` 改成 Redis 支撑的 session store**
  - 替换当前本地 `ConcurrentHashMap`
  - 保持过期清理和 pickup code 查询语义不变
  - 让在线快传在多实例下仍可 lookup/join
  - 2026-04-10 当前实现为“Redis 启用时在线快传 session 走 Redis KV，关闭时自动回退到进程内存”，离线快传仍继续走数据库持久化链路

- [x] **Step 9: 接入文件事件跨实例分发**
  - 保留当前单实例 emitter 管理
  - 新增 Redis pub/sub，把事务提交后的文件事件广播到其他实例
  - 避免把 `SseEmitter` 本身存进 Redis

- [x] **Step 10: 评估并最小落地 Spring Cache**
  - 优先考虑热门目录、`admin summary`、存储策略列表、Android 最新发布元数据等高读低写接口
  - 每个缓存都要有明确失效策略，不能只加 `@Cacheable`

- [x] **Step 11: 审慎处理 DogeCloud 临时 S3 会话缓存**
  - 若多实例下重复拉临时 token 成本可接受，则保留本地内存缓存
  - 若需要跨实例复用，再单独加 Redis 缓存，不与业务缓存混用

- [x] **Step 12: 验证**
  - `cd backend && mvn test`
  - 手动验证无 Redis 时应用仍可启动
  - 手动验证启用 Redis 后快传在线会话可创建、lookup、join、过期
  - 手动验证踢下线 / 改密后旧 token 失效
  - 手动验证热门目录缓存命中与目录变更后失效
  - 手动验证多实例下文件事件能跨实例到达
  - 手动验证任务重复 claim 不会发生明显并发冲突

**Exit criteria:**

- Redis 成为可选但可用的基础设施
- 登录态 / token 黑名单已接入 Redis
- 至少一个真实热点目录查询接入 Redis/Spring Cache
- 至少一个高风险并发路径接入分布式锁
- 上传中的短生命周期状态已进入 Redis
- 小规模 broker 已承担至少一类轻量异步触发
- 在线快传会话不再依赖单进程内存
- 文件事件具备跨实例扩展边界

---

## Admin Track: Backend Management Surface

**Goal:** 按更成熟的后台目录，把当前项目后端管理能力补成“资源可观测、可管理、可扩展”的体系，而不是继续把所有管理功能堆进单一 summary 页面。

### Admin-B1: Parameter Settings

**Goal:** 新增“参数设置”资源，集中管理当前散落在配置和管理台中的系统开关。

**Recommended internal sections:**

1. 站点信息
2. 用户会话
3. 验证码
4. 媒体处理
5. 增值服务
6. 邮件
7. 队列
8. 外观
9. 事件
10. 服务器

**Current-project recommendation:**

- 近期应实现：
  - 站点信息
  - 用户会话
  - 媒体处理
  - 队列
  - 外观
  - 服务器
- 可先留空壳或只读：
  - 验证码
  - 邮件
  - 事件
- 当前不建议投入：
  - 增值服务

**Suggested scope:**

- 注册与邀请策略
- 离线快传总上限
- 默认上传大小限制
- 站点显示参数
- 媒体处理开关
- Redis / runtime 只读状态

- [x] **Step 1: 设计参数设置 DTO 与权限边界**
- [ ] **Step 2: 先拆站点信息子分组**
  - 站点名称
  - 站点描述
  - 主站点 URL
  - 备用站点 URL
  - 页脚代码
  - 登录公告 / 站点公告
  - 使用条款 / 隐私政策链接
- [ ] **Step 3: 设计用户会话子分组**
  - access / refresh 生命周期
  - 同端挤下线策略
  - token 黑名单开关与 TTL 策略
  - 登录安全相关策略
- [ ] **Step 4: 设计媒体处理子分组**
  - 媒体元数据提取开关
  - 缩略图开关
  - 视频 poster / 时长提取策略
  - 第三方依赖状态只读信息
- [ ] **Step 5: 设计队列子分组**
  - broker 类型
  - worker 并发
  - 失败重试预算
  - 队列健康状态只读信息
- [ ] **Step 6: 设计外观与服务器子分组**
  - 前端品牌化字段
  - CDN / 静态资源缓存参数
  - 服务器运行信息、Redis 状态、存储后端状态
- [x] **Step 2: 暴露管理员参数读取与更新接口**
- [x] **Step 3: 只允许修改当前可安全热更新的参数**
- [x] **Step 4: 文档化哪些配置仍需环境变量或重启**

### Admin-B2: File System

**Goal:** 把“文件系统”作为独立后台资源，而不是只在文件列表里做删除。

**Recommended internal sections:**

1. 参数设置
2. 全文搜索
3. 文件图标
4. 文件浏览应用
5. 自定义属性

**Current-project recommendation:**

- 近期应实现：
  - 参数设置
  - 文件图标
  - 自定义属性
- 可先做只读骨架：
  - 文件浏览应用
- 当前延后：
  - 全文搜索

**Suggested scope:**

- 默认存储后端概览
- 上传模式能力矩阵
- 媒体处理能力状态
- 热门目录 / 缓存状态概览
- WebDAV 预留状态

- [ ] **Step 1: 设计文件系统只读总览接口**
- [ ] **Step 2: 设计文件系统参数设置子分组**
  - 文档在线编辑最大大小
  - 回收站扫描间隔
  - 文件 Blob 回收间隔
  - 静态资源缓存 TTL
  - 文件列表分页方式
  - 最大分页大小
  - 最大批量操作数量
  - 最大递归搜索数量
  - 地图提供商
- [ ] **Step 3: 设计文件图标子分组**
  - 扩展名到图标的映射策略
  - 前端图标主题扩展点
  - 自定义 mime/icon 映射入口
- [ ] **Step 4: 设计文件浏览应用子分组**
  - 当前阶段只读显示已接入的浏览/预览能力
  - 后续为 WOPI / Office / 媒体浏览器预留入口
- [ ] **Step 5: 设计自定义属性子分组**
  - 标签 schema
  - metadata key 命名约束
  - 可搜索属性白名单
- [ ] **Step 6: 暴露上传模式、缓存、媒体处理、WebDAV 状态**
- [ ] **Step 7: 管理台可按模块查看文件系统运行态**

### Admin-B3: File Blob

**Goal:** 既然项目已经有 `FileBlob` / `FileEntity`，后台必须能查看物理对象层，而不是只看逻辑文件。

**Suggested scope:**

- Blob / Entity 基本信息
- object key
- storage policy
- reference count
- orphan 风险
- 派生实体类型：`VERSION` / `THUMBNAIL` / `TRANSCODE`

- [ ] **Step 1: 增加管理员 Blob/Entity 列表接口**
- [ ] **Step 2: 支持按用户、策略、对象 key、实体类型过滤**
- [ ] **Step 3: 标注高风险条目，如引用异常、迁移失败残留**

### Admin-B4: Share

**Goal:** 分享需要成为独立后台资源，便于管理滥用和过期内容。

- [ ] **Step 1: 管理员分享列表接口**
- [ ] **Step 2: 支持按用户、文件名、token、是否密码保护、是否过期过滤**
- [ ] **Step 3: 支持管理员撤销分享**

### Admin-B5: Background Tasks

**Goal:** 后台任务不能只在用户视角可见，管理员也要能看全局任务池。

- [ ] **Step 1: 增加管理员任务列表与详情接口**
- [ ] **Step 2: 支持按任务类型、状态、失败分类、租约状态过滤**
- [ ] **Step 3: 支持查看任务归属用户、重试信息、锁/worker 信息**

### Admin-B6: OAuth Apps

**Goal:** 为未来 WebDAV / 第三方客户端 / OIDC 留出后台资源位。

- [ ] **Step 1: 当前阶段只预留数据模型与只读列表边界**
- [ ] **Step 2: 不急于完整实现授权流程**
- [ ] **Step 3: 在文档中明确这是后续阶段入口**

---

## 5. Stage 2: Close Existing v2 Gaps

**Goal:** 把现有架构中的“半完成状态”补成真正可用的闭环，优先提升当前产品完成度。

**Files likely involved:**

- `front/src/pages/Files.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/lib/file-search.ts`
- `front/src/lib/file-events.ts`
- `front/src/lib/upload-session.ts`
- `front/src/lib/api.ts`
- `front/src/mobile-components/*`
- `backend/src/main/java/com/yoyuzh/files/core/*`
- `backend/src/main/java/com/yoyuzh/files/tasks/*`
- `backend/src/main/java/com/yoyuzh/files/search/*`
- `backend/src/test/java/com/yoyuzh/files/**`
- `front/src/**/*.test.ts`

- [ ] **Step 1: 移动端接入 v2 文件搜索**
  - 复用现有 `front/src/lib/file-search.ts`
  - 保持与桌面端相同的查询参数和空态行为
  - 不把搜索结果写回目录缓存

- [ ] **Step 2: 桌面端补齐 `ARCHIVE` / `EXTRACT` 入口**
  - 从当前选中文件直接创建任务
  - 在任务面板中区分 `ARCHIVE`、`EXTRACT`、`MEDIA_META`
  - 错误态展示后端返回的任务失败原因

- [ ] **Step 3: 移动端补齐任务入口**
  - 至少支持查看最近任务
  - 支持取消 `QUEUED` / `RUNNING`
  - 支持为选中文件创建 `MEDIA_META`
  - 如交互成本可控，再接 `ARCHIVE` / `EXTRACT`

- [ ] **Step 4: 把旧读取路径从 `StoredFile.blob` 迁到 `primaryEntity`**
  - 覆盖下载、ZIP、分享详情、回收站、媒体元数据读取等旧路径
  - 保留兼容 fallback，直到历史数据回填验证完成
  - 明确哪些 API 已完全不依赖 `blob`

- [ ] **Step 5: 更新相关测试**
  - 后端补读取路径切换和任务入口相关测试
  - 前端补移动端搜索和任务面板交互测试

- [ ] **Step 6: 验证**
  - `cd backend && mvn test`
  - `cd front && npm run test`
  - `cd front && npm run lint`
  - `cd front && npm run build`

**Exit criteria:**

- 桌面与移动端都能搜索
- 桌面端能直接发起 archive/extract
- 移动端至少能消费任务能力
- 旧读取链路完成 `primaryEntity` 主读切换

---

## 6. Stage 3: Thumbnail And Rich Media Pipeline

**Goal:** 把后台任务骨架扩展成真正可感知的媒体处理系统，这是当前项目相对 Cloudreve 最有价值的缺口。

**Files likely involved:**

- `backend/src/main/java/com/yoyuzh/files/tasks/*`
- `backend/src/main/java/com/yoyuzh/files/storage/*`
- `backend/src/main/java/com/yoyuzh/files/core/*`
- `backend/src/main/java/com/yoyuzh/files/policy/*`
- `backend/src/main/java/com/yoyuzh/files/**/FileMetadata*.java`
- `front/src/pages/Files.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`
- `front/src/lib/types.ts`
- `front/src/components/**/*`
- `backend/src/test/java/com/yoyuzh/files/**`

- [ ] **Step 1: 增加 `THUMBNAIL` 任务类型与派生实体写入**
  - 使用 `FileEntity` 挂接缩略图实体，不再把缩略图视作普通文件
  - 失败时写入可辨识 metadata，避免前端无限重试

- [ ] **Step 2: 图片缩略图生成**
  - 为常见图片格式生成小尺寸预览
  - 支持本地存储和当前 S3 兼容策略

- [ ] **Step 3: 视频 poster 和时长提取**
  - 至少落地视频时长
  - poster 可以先做单帧封面，不要求完整 HLS

- [ ] **Step 4: 前端列表和详情接入缩略图**
  - 图片和视频列表显示缩略图
  - 详情侧栏显示时长、尺寸、编码等已有 metadata
  - 缩略图不可用时回退到文件图标

- [ ] **Step 5: 能力与策略对齐**
  - 明确哪些策略支持原生缩略图、哪些需要代理生成
  - 管理台可查看缩略图相关 capability 和任务状态

- [ ] **Step 6: 验证**
  - `cd backend && mvn test`
  - `cd front && npm run test`
  - `cd front && npm run lint`
  - `cd front && npm run build`

**Exit criteria:**

- 上传图片后可自动生成缩略图
- 上传视频后可得到最小媒体信息和 poster/时长
- 文件列表和详情可真实消费这些资源

---

## 7. Stage 4: Metadata, Labels, And Search Expansion

**Goal:** 让 `FileMetadata` 不再只是骨架，真正承担标签、预览状态和搜索过滤。

**Files likely involved:**

- `backend/src/main/java/com/yoyuzh/files/search/*`
- `backend/src/main/java/com/yoyuzh/files/core/*`
- `backend/src/main/java/com/yoyuzh/files/**/FileMetadata*.java`
- `backend/src/test/java/com/yoyuzh/files/search/*`
- `front/src/lib/file-search.ts`
- `front/src/pages/Files.tsx`
- `front/src/mobile-pages/MobileFiles.tsx`

- [ ] **Step 1: 明确 metadata key 规范**
  - `media:*`
  - `thumb:*`
  - `tag:*`
  - `sys:*`

- [ ] **Step 2: 扩展 v2 搜索过滤能力**
  - 标签过滤
  - 媒体类型过滤
  - 缩略图/预览状态过滤

- [ ] **Step 3: 前端增加高级搜索入口**
  - 桌面端先做
  - 移动端至少支持基础筛选

- [ ] **Step 4: 为后续 `shared_with_me` / `trash` 风格视图保留统一查询边界**
  - 当前不必完全复制 Cloudreve File URI
  - 但内部查询层应避免继续散落在多套 service 方法里

- [ ] **Step 5: 验证**
  - `cd backend && mvn test`
  - `cd front && npm run test`
  - `cd front && npm run lint`

**Exit criteria:**

- `FileMetadata` 真正进入用户能力层
- 搜索不再只停留在文件名和固定字段

---

## 8. Stage 5: WebDAV Minimum Viable Support

**Goal:** 提供最小可用的 WebDAV 能力，优先服务系统文件管理器挂载和简单同步场景。

**Files likely involved:**

- `backend/src/main/java/com/yoyuzh/config/*`
- `backend/src/main/java/com/yoyuzh/files/core/*`
- `backend/src/main/java/com/yoyuzh/files/upload/*`
- `backend/src/main/java/com/yoyuzh/auth/*`
- `backend/src/test/java/com/yoyuzh/**`
- `docs/api-reference.md`
- `docs/architecture.md`

- [ ] **Step 1: 设计 WebDAV 路径边界**
  - 首阶段只暴露当前登录用户自己的网盘根目录
  - 不做共享目录挂载

- [ ] **Step 2: 实现最小方法集**
  - `PROPFIND`
  - `GET`
  - `PUT`
  - `DELETE`
  - `MKCOL`
  - `MOVE`

- [ ] **Step 3: 复用现有文件服务和上传会话**
  - WebDAV `PUT` 不绕过容量检查、权限检查和存储策略
  - 避免做一套独立写入链路

- [ ] **Step 4: 明确认证方式**
  - 首阶段优先 Basic + token/应用密码式接入，避免直接复用浏览器 JWT 语义
  - 认证模型必须先文档化，再写实现

- [ ] **Step 5: 验证**
  - `cd backend && mvn test`
  - 手动验证 Windows 或 macOS WebDAV 客户端的列目录、上传小文件、下载、创建目录、删除

**Exit criteria:**

- 常见 WebDAV 客户端能完成最小读写
- 不引入绕过现有业务规则的旁路实现

---

## 9. Deferred Backlog

这些项目保留到 Stage 4 之后重新评估，不在当前一轮升级中直接实现：

- [ ] **Remote Download**
  - 目标：类似 Cloudreve 的离线下载器
  - 前置：后台任务、存储策略、文件实体、容量模型稳定

- [ ] **OIDC / OAuth Scope**
  - 目标：第三方客户端和更细粒度授权
  - 前置：WebDAV 或开放客户端需求真实存在[text](app://-/index.html?hostId%3Dlocal)

- [ ] **Desktop Sync Protocol**
  - 目标：桌面同步客户端
  - 前置：文件事件、Redis 跨实例广播、冲突策略、WebDAV 或专有同步协议边界明确

- [ ] **WOPI / Office Online**
  - 目标：在线 Office 协作
  - 前置：权限、锁、预览、第三方接入边界成熟

## 10. Recommended Execution Order

1. Stage 1: 先接 Redis 基础设施
2. Stage 2: 再补现有闭环断点
3. Stage 3: 再做缩略图和 richer media
4. Stage 4: 让 metadata/search 真正变成平台能力
5. Stage 5: 最后接 WebDAV
5. Deferred backlog: 仅在真实需求出现后再启动

## 11. Documentation Follow-Up

每完成一个阶段，都必须同步更新：

- `memory.md`
- `docs/architecture.md`
- `docs/api-reference.md`

更新内容至少包括：

- 新增能力边界
- 已废弃或迁移的旧路径
- 验证命令与已知限制
- 任何新的部署或运行时前置条件
## 2026-04-10 Stage 1 Step 9 Landing Note

- 已落地 `FileEventCrossInstancePublisher` + Redis pub/sub listener/publisher：本实例继续维护本地 `SseEmitter` 集合，提交后先做本地广播，再向 `app.redis.namespaces.file-events` 对应 topic 发布事件。
- 远端实例收到消息后只做本地 SSE 投递，不重复写 `FileEvent` 表；同实例消息按 `instanceId` 忽略，避免本机回环重复推送。
- Redis 关闭时自动回退为原有单实例本地广播语义。
## 2026-04-10 Stage 1 Step 10 Landing Note

- 宸插皢 `AdminService.listStoragePolicies()` 鎺ュ叆 `admin:storage-policies` Spring Cache锛屽苟鍦?`createStoragePolicy/updateStoragePolicy/updateStoragePolicyStatus` 涓夋潯绠＄悊鍐欒矾寰勪笂鍋?all-entries eviction锛岀‘淇濆悗鍙板瓨鍌ㄧ瓥鐣ュ垪琛ㄥ懡涓紦瀛樺悗浠嶈兘鍦ㄧ畝鍗曞啓鎿嶄綔鍚庣珛鍗虫仮澶嶄负鏂版暟鎹€?
- 宸插皢 `AndroidReleaseService.getLatestRelease()` 鎺ュ叆 `android:release` Spring Cache锛屽綋鍓嶉噰鐢?TTL 鍨嬪け鏁堢瓥鐣ワ紝鍥犱负 release metadata 鏇存柊鏉ヨ嚜瀵硅薄瀛樺偍澶栭儴鍙戝竷鑴氭湰锛屼粨搴撳唴娌℃湁鍚屾簮鍐欏叆璺緞銆?
- `admin summary` 缁忚瘎浼板悗鏆備笉鎺ュ叆 Spring Cache锛屽洜涓哄叾鍚屾椂鍖呭惈 request count銆乨aily active users銆乭ourly timeline 绛夐珮棰戠粺璁★紝鍋氭樉寮忓け鏁堝緢闅句繚璇佽涔夊共鍑€锛屽洜姝ゅ湪杩欎竴姝ユ槑纭帓闄ゃ€?
## 2026-04-10 Stage 1 Step 10 Clarification

- `AdminService.listStoragePolicies()` now uses Spring Cache `admin:storage-policies`.
- Successful storage policy create, update, and status-change writes evict that cache explicitly.
- `AndroidReleaseService.getLatestRelease()` now uses Spring Cache `android:release`.
- Android release metadata refresh is TTL-based because `android/releases/latest.json` is updated by the external release publish script.
- `admin summary` was evaluated and intentionally left uncached because it includes high-churn metrics without a clean explicit invalidation boundary.
## 2026-04-10 Stage 1 Step 11 Clarification

- `DogeCloudS3SessionProvider` remains an in-process runtime cache and is not moved into Redis.
- The cached value is a live `S3FileRuntimeSession` containing `S3Client` and `S3Presigner`, so cross-instance Redis reuse would add serialization and lifecycle complexity without clear payoff.
- Current semantics remain: each backend instance refreshes its own temporary session only when the cached credentials enter the one-minute refresh window.
- This means multi-instance deployments may fetch duplicate temporary credentials, but the current cost was judged acceptable relative to the extra complexity of a Redis-backed shared credential cache.
- Tests now explicitly cover cache reuse plus refresh-time and close-time resource cleanup in `DogeCloudS3SessionProviderTest`.
## 2026-04-10 Stage 1 Step 12 Clarification

- Stage 1 validation is complete for the current local environment.
- Full backend verification passed with `cd backend && mvn test`, for a total of 294 passing tests.
- A no-Redis boot-path check also passed under the `dev` profile when the required `APP_JWT_SECRET` environment variable was supplied and `APP_REDIS_ENABLED=false`.
- The local boot verification was captured by starting the backend on port `18081`, confirming that Tomcat started and the application reached the `Started PortalBackendApplication` log line.
- Two earlier local startup failures were confirmed as environment issues rather than Redis regressions: one missing `APP_JWT_SECRET`, and one unrelated port `8080` conflict caused by another local Java process.
- Remaining validation still requires external environment support: a real Redis instance for cache/pubsub/broker/session end-to-end checks, and at least two backend instances for cross-instance event/session propagation checks.

## 2026-04-10 Stage 1 Step 12 Manual Redis Validation Addendum

- Stage 1 manual validation was continued in a real local Redis plus dual-backend setup (`dev` profile, ports `18081` and `18082`) after the initial closeout note.
- The backend suite is now green at 301 passing tests after fixing four real Redis/manual-integration regressions discovered during that validation.
- Fix 1: `RedisFileEventPubSubPublisher` and `RedisFileEventPubSubListener` now mark the intended constructor for Spring injection, which unblocked Redis-enabled startup.
- Fix 2: `AuthTokenInvalidationService` now stores and compares access-token revocation cutoffs in epoch seconds, with compatibility handling for earlier millisecond values.
- Fix 3: Redis-backed file list cache now uses the application `ObjectMapper` for Java time serialization and converts generic cache payload maps back into `CachedFileListPage` on cache reads.
- Fix 4: `portal_file.storage_name` is now populated for both directory creation and normal file upload metadata writes, which unblocked real upload/manual event flows against the current schema.
- Manual verification that succeeded in the real Redis plus two-instance setup:
- Re-login invalidates the previous access token and refresh token across instances, while the newest token remains valid.
- Online transfer sessions remain discoverable from the second instance even after the first instance is stopped, which confirms shared runtime state rather than same-process false positives.
- Uploading `image/png` on instance A emits `CREATED` SSE on instance B and auto-creates a queued `MEDIA_META` task visible from instance B.
- Directory list behavior was rechecked through real APIs: repeated `GET /api/files/list` remained stable after the cache fixes, and a subsequent directory mutation was immediately reflected by a fresh list response.
- One environment observation remains open: direct `redis-cli --scan` inspection did not surface the expected Redis keys during local probing, even though cross-instance runtime behavior proved that Redis-backed sharing was active. Treat the runtime behavior checks as the stronger validation result for now.

## 2026-04-11 Admin Next-Phase Backend Landing Note

- The next backend-phase admin batch is now landed.
- Implemented admin operational APIs:
- `GET /api/admin/file-blobs`
- `GET /api/admin/shares`
- `DELETE /api/admin/shares/{shareId}`
- `GET /api/admin/tasks`
- `GET /api/admin/tasks/{taskId}`
- The blob admin endpoint is intentionally `FileEntity`-centric and adds operator-facing anomaly signals: `blobMissing`, `orphanRisk`, and `referenceMismatch`.
- The task admin endpoint adds backend-owned parsing for `failureCategory`, `retryScheduled`, `workerOwner`, and `leaseState` so the frontend does not need to infer them from raw JSON.
- Integration and service coverage were expanded in `AdminControllerIntegrationTest` and `AdminServiceTest`, and the storage-policy cache test was kept aligned with the current constructor/dependency graph.
- Verification passed with targeted admin tests and full backend regression:
- `cd backend && mvn -Dtest=AdminControllerIntegrationTest,AdminServiceTest,AdminServiceStoragePolicyCacheTest test`
- `cd backend && mvn test`
- Full backend result after this landing note: 304 tests passed.

## 2026-04-11 Admin Next-Phase Backend Landing Note 2

- Admin-B1 and Admin-B2 have now both started with read-only backend surfaces.
- Implemented:
- `GET /api/admin/settings`
- `GET /api/admin/filesystem`
- `GET /api/admin/settings` currently stops at backend-owned observation: invite-code state, configured admin usernames, JWT/user-session timing, Redis/token-blacklist availability, queue cadence, and storage/Redis runtime mode.
- `GET /api/admin/filesystem` currently stops at operational observation: default policy snapshot, resolved upload-mode matrix, effective max file size, metadata/thumbnail capability flags, cache backend/TTL status, aggregate file/blob/entity counts, and reserved-off `WebDAV` state.
- This batch intentionally does not introduce writable parameter settings yet. Hot-update safety boundaries and persistent admin writes remain part of the next Admin-B1 follow-up.
- Verification passed in WSL with:
- `cd backend && mvn -Dtest=AdminControllerIntegrationTest,AdminServiceTest,AdminServiceStoragePolicyCacheTest test`
- `cd backend && mvn test`

## 2026-04-11 Admin Next-Phase Backend Landing Note 3

- Admin-B1 has now moved beyond read-only snapshots into the first bounded write path.
- Implemented:
- `PATCH /api/admin/settings/registration/invite-code`
- `POST /api/admin/settings/registration/invite-code/rotate`
- `GET /api/admin/settings` now also returns per-section `writeSupported` flags and a `transfer` section exposing the persisted offline-transfer storage limit.
- Current hot-update boundary is explicit:
- writable now: current registration invite code, offline transfer storage limit;
- still read-only/runtime-derived: admin usernames, JWT lifetimes, Redis enablement and TTL policy, queue backend/cadence, storage provider, and other environment-bound server settings.
- The invite-code write path is deliberately backed by the existing `RegistrationInviteState` row instead of introducing a generic mutable config store.
- Verification passed in WSL with:
- `cd backend && mvn -Dtest=AdminControllerIntegrationTest,AdminServiceTest,AdminServiceStoragePolicyCacheTest test`
- `cd backend && mvn test`
- Full backend result after this batch: 310 tests passed.
