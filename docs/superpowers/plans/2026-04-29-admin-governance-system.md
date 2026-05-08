# Admin Governance System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `my_site` 网盘项目上，逐步建设一套可维护、可扩展、安全的后台治理体系，把系统配置、存储源、上传策略、任务监控、审计日志和运维入口纳入统一后台，而不引入重型后台框架或破坏现有业务链路。

**Architecture:** 继续沿用当前模块化单体结构，前端复用现有 `frontend/src/pages/admin/*` 与 `frontend/src/components/AdminLayout.tsx` 风格，后端继续以 `backend/src/main/java/com/yoyuzh/ops/admin/**` 作为治理编排入口，并通过显式 API 调用 `identity.access`、`files.upload`、`files.workspace`、`files.content`、`platform.storage`、`platform.job`、`transfer` 等领域模块。配置治理采用“代码注册 schema + 数据库存值/历史”的轻量模型，Nacos/Apollo/Spring Boot Admin 只作为可选适配或设计参考，不作为基础依赖。

**Tech Stack:** Spring Boot 3.3.8, Java 17, Spring Security, Spring Data JPA, Maven, React 19, Vite 6, TypeScript, React Query, Tailwind CSS v4.

---

## Scope Check

这次需求覆盖多个独立子系统，不能作为一次性大改实现。按 `superpowers:writing-plans` 的拆分原则，这份文档是“主实施计划”，负责：

1. 列清每个子项目的目标、边界、依赖和交付顺序。
2. 明确哪些能力复用现有结构，哪些能力新增最小骨架。
3. 给后续每个阶段的详细实现计划提供执行顺序和约束。

这份计划不直接规定每一个实现步骤里的代码块，而是作为后续子计划的母版。真正进入编码前，每个阶段应再产出一份更细的执行计划。

## File Structure

### 现有前端相关目录

- `frontend/src/App.tsx`
  - 当前 admin 路由入口。
- `frontend/src/components/AdminLayout.tsx`
  - 当前后台布局、导航与风格基座。
- `frontend/src/pages/admin/**`
  - 现有后台页面。
- `frontend/src/api/queries.ts`
  - 现有 admin 查询入口。
- `frontend/src/api/mutations.ts`
  - 现有 admin 写接口入口。
- `frontend/src/api/types.ts`
  - 现有 admin DTO 类型。

### 现有后端相关目录

- `backend/src/main/java/com/yoyuzh/ops/admin/**`
  - 当前后台治理入口、查询服务、写服务、审计与运行时配置。
- `backend/src/main/java/com/yoyuzh/platform/storage/**`
  - 当前存储策略与运行时存储能力。
- `backend/src/main/java/com/yoyuzh/files/upload/**`
  - 上传会话、上传策略解析、上传链路控制面。
- `backend/src/main/java/com/yoyuzh/files/workspace/**`
  - 文件树与空间容量治理。
- `backend/src/main/java/com/yoyuzh/files/content/**`
  - 物理内容、对象落点、衍生物能力。
- `backend/src/main/java/com/yoyuzh/platform/job/**`
  - 后台任务运行时。
- `backend/src/main/java/com/yoyuzh/transfer/**`
  - 离线下载与传输任务。
- `backend/src/main/java/com/yoyuzh/identity/access/**`
  - 用户、角色、认证、admin 访问控制。
- `backend/src/main/resources/application.yml`
  - 当前启动级和部分运行时配置来源。

### 计划新增或重点扩展的区域

- `frontend/src/pages/admin/config/**`
  - 配置中心页面组。
- `frontend/src/pages/admin/storage/**`
  - 存储源管理页面组。
- `frontend/src/pages/admin/upload-policy/**`
  - 上传策略页面组。
- `frontend/src/pages/admin/task-center/**`
  - 任务中心页面组。
- `frontend/src/pages/admin/logs/**`
  - 审计、登录、异常日志页面组。
- `frontend/src/components/admin/**`
  - 后台通用列表、表单、确认框、详情抽屉组件。
- `backend/src/main/java/com/yoyuzh/ops/admin/api/**`
  - 新增稳定 admin DTO 与门面接口。
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/**`
  - Config Schema、配置值、配置历史编排。
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/storage/**`
  - 存储源治理编排。
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/task/**`
  - 任务中心治理编排。
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/**`
  - 配置值、历史、发布记录存储。
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/log/**`
  - 登录日志、异常日志持久化。

---

## Project Catalog

### Project 0: Admin Foundation

**Goal:** 把当前后台从“若干独立页面”升级成“统一组件体系”，为后续配置、存储、任务、日志页面提供共用壳。

**Depends on:** 无，最先落地。

**Frontend ownership:**
- `frontend/src/components/AdminLayout.tsx`
- `frontend/src/components/admin/**`
- `frontend/src/pages/admin/**`
- `frontend/src/api/{queries,mutations,types}.ts`

**Backend ownership:**
- `backend/src/main/java/com/yoyuzh/ops/admin/api/**`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/**`

**Deliverables:**
- [ ] 统一后台导航分组，不再继续沿用 Cloudreve 命名误导。
- [ ] 新增后台列表页壳，统一分页、筛选、空态、错误态、刷新。
- [ ] 新增后台动态表单壳，统一分组、字段提示、只读态、敏感态。
- [ ] 新增高危操作确认组件，统一删除、启停、回滚、重试交互。
- [ ] 新增审计详情抽屉或弹层，统一展示变更摘要与风险提示。

**Non-goals:**
- [ ] 不在这一阶段引入数据库菜单系统。
- [ ] 不在这一阶段改视觉风格。

**Verification:**
- [ ] 前端类型检查使用 `cd frontend && npm run lint`。
- [ ] 核对现有 admin 页面路由仍可进入。

### Project 1: Config Schema Backbone

**Goal:** 建立配置 schema 元数据机制，让后台能够根据字段类型和元信息动态渲染配置表单。

**Depends on:** Project 0。

**Backend ownership:**
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/**`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/**`
- `backend/src/main/java/com/yoyuzh/ops/admin/api/**`

**Frontend ownership:**
- `frontend/src/components/admin/form/**`
- `frontend/src/pages/admin/config/**`
- `frontend/src/api/types.ts`

**Schema responsibilities:**
- `key`
- `group`
- `subgroup`
- `title`
- `description`
- `type`
- `defaultValue`
- `options`
- `required`
- `editable`
- `sensitive`
- `encrypted`
- `restartRequired`
- `validationRules`
- `permissionCode`
- `scope`
- `source`

**Supported field types:**
- `string`
- `number`
- `boolean`
- `select`
- `multi_select`
- `password`
- `textarea`
- `json`
- `url`
- `path`
- `size`
- `duration`
- `cron`

**Deliverables:**
- [ ] 设计配置 schema 注册接口，默认用代码注册而不是先做可视化 schema 管理。
- [ ] 设计配置值存储模型，支持当前值、来源、加密标记和更新时间。
- [ ] 设计配置历史模型，支持变更前后值、操作人、时间、说明。
- [ ] 提供 schema 查询 API 和 value 查询 API。
- [ ] 前端实现根据 schema 渲染字段的动态表单层。

**Non-goals:**
- [ ] 不把 schema 自己做成低代码平台。
- [ ] 不让启动级敏感配置进入普通运行时配置页。

**Verification:**
- [ ] 后端增加配置 schema 与配置值服务测试，使用 `cd backend && mvn test`。
- [ ] 前端动态字段渲染通过 `cd frontend && npm run lint` 保证类型正确。

### Project 2: System Config Center

**Goal:** 基于 Config Schema 建设真正的系统配置中心。

**Depends on:** Project 1。

**Current reuse points:**
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminRuntimeSettingsService.java`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminConfigSnapshotService.java`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/AdminRuntimeSettingsState.java`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminSettingsController.java`
- `frontend/src/pages/admin/AdminSetting.tsx`

**Config groups in scope:**
- `site`
- `security/session`
- `upload`
- `share`
- `media-processing`
- `queue-cache`
- `appearance`
- `capacity-retention`

**Deliverables:**
- [ ] 将当前 `AdminSetting` 从硬编码 tab 表单升级为 schema 驱动页面。
- [ ] 支持分组显示、默认值提示、字段说明、校验错误提示。
- [ ] 支持敏感字段脱敏回显。
- [ ] 支持“恢复默认值”。
- [ ] 支持“修改后刷新缓存”。
- [ ] 支持“查看历史”和“回滚到历史版本”。

**Out of scope values:**
- [ ] DB URL、Redis 地址、JWT 密钥、对象存储 SecretKey、Nacos 地址和第三方密钥继续留在环境变量或安全配置中。

**Verification:**
- [ ] 后端设置读写接口测试。
- [ ] 前端配置页类型检查。
- [ ] 手工确认现有注册邀请码与离线下载限制能力不回归。

### Project 3: Storage Source Management

**Goal:** 在现有存储策略基础上演进出独立的存储源管理能力。

**Depends on:** Project 0，可与 Project 2 并行设计，但实现上晚于 Project 2。

**Current reuse points:**
- `backend/src/main/java/com/yoyuzh/platform/storage/internal/domain/StoragePolicy.java`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminStoragePolicyController.java`
- `frontend/src/pages/admin/AdminPolicy.tsx`

**Storage types in scope:**
- `LOCAL`
- `S3`
- `OSS`
- `WEBDAV`

**Capabilities:**
- 启用/禁用
- 设为默认
- 测试连接
- 能力描述
- 脱敏展示
- 敏感字段独立处理

**Deliverables:**
- [ ] 定义存储源页面与 DTO，避免把 secret 明文下发前端。
- [ ] 把当前 `StoragePolicy` 模型补充为更清晰的“连接信息 + 能力信息 + 策略信息”。
- [ ] 补齐 LOCAL、S3、OSS、WEBDAV 的字段 schema。
- [ ] 实现测试连接入口。
- [ ] 审计存储源新增、编辑、启停、默认切换。

**Non-goals:**
- [ ] 不在第一版做跨云厂商所有细节能力。
- [ ] 不在日志里打印 access key、secret、password。

**Verification:**
- [ ] 后端存储源读写与测试连接服务测试。
- [ ] 前端页面类型检查。

### Project 4: Upload Policy Management

**Goal:** 把上传相关运行时控制项从零散配置收敛成独立策略能力。

**Depends on:** Project 1，推荐在 Project 2 之后。

**Backend ownership:**
- `backend/src/main/java/com/yoyuzh/files/upload/**`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/**`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/**`

**Frontend ownership:**
- `frontend/src/pages/admin/upload-policy/**`
- `frontend/src/components/admin/form/**`

**Policy fields in scope:**
- 最大文件大小
- 分片大小
- 上传并发数
- 是否允许断点续传
- 是否允许秒传
- 是否启用完整性校验
- 临时上传会话过期时间
- 允许/禁止文件类型
- 默认用户容量

**Deliverables:**
- [ ] 增加“系统默认上传策略”页面。
- [ ] 后端提供 resolved policy 预览能力，供页面查看当前最终生效值。
- [ ] 上传链路统一读取解析后的策略而不是散落常量。
- [ ] 为后续按角色/按用户扩展预留模型，但第一版只做系统级策略。

**Non-goals:**
- [ ] 第一版不做复杂规则引擎。

**Verification:**
- [ ] 上传策略解析器测试。
- [ ] 不回归现有上传会话链路。

### Project 5: Task Center

**Goal:** 统一后台任务、传输任务和后续上传任务的治理视图。

**Depends on:** Project 0，可在 Project 3 和 4 之后开始。

**Current reuse points:**
- `backend/src/main/java/com/yoyuzh/platform/job/internal/domain/BackgroundTask.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadTask.java`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminTaskController.java`
- `frontend/src/pages/admin/AdminTask.tsx`

**Task views in scope:**
- 后台任务列表
- 失败任务
- 任务详情
- 传输任务
- 过期任务清理
- 人工重试入口

**Deliverables:**
- [ ] 后端提供统一任务中心查询 DTO，而不是让前端直接解析不同任务 JSON。
- [ ] 增加失败分类、租约状态、重试次数等治理字段展示。
- [ ] 提供失败任务重试。
- [ ] 提供过期任务或僵尸任务清理。
- [ ] 为 tus、multipart、S3 multipart、OSS multipart 预留扩展字段。

**Non-goals:**
- [ ] 不在第一版统一所有底层任务表。

**Verification:**
- [ ] 后端任务查询/重试/清理测试。
- [ ] 前端任务中心页面类型检查。

### Project 6: Audit, Login Log, Exception Log

**Goal:** 把操作审计、登录日志、异常日志拆分成明确治理能力。

**Depends on:** Project 0。

**Current reuse points:**
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/AdminAuditLogEntity.java`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminAuditController.java`

**Deliverables:**
- [ ] 继续扩展操作审计范围，覆盖配置、存储源、上传策略、任务操作。
- [ ] 新增登录日志模型和查询 API。
- [ ] 新增异常日志模型和查询 API。
- [ ] 明确日志脱敏策略。
- [ ] 所有高危操作都要带审计记录。

**Non-goals:**
- [ ] 不做完整 SIEM 平台。

**Verification:**
- [ ] 后端日志存储与查询测试。
- [ ] 手工抽查敏感字段没有进入日志详情。

### Project 7: Permission Refinement

**Goal:** 在现有 admin 访问控制上增加页面级与操作级权限粒度。

**Depends on:** Project 0。

**Current reuse points:**
- `backend/src/main/java/com/yoyuzh/boot/security/SecurityConfig.java`
- `backend/src/main/java/com/yoyuzh/identity/access/api/AdminAccessPolicy.java`
- `frontend/src/lib/session.ts`

**Permission codes in scope:**
- `admin.config.read`
- `admin.config.write`
- `admin.storage.read`
- `admin.storage.write`
- `admin.storage.test`
- `admin.upload-policy.read`
- `admin.upload-policy.write`
- `admin.task.read`
- `admin.task.retry`
- `admin.task.cleanup`
- `admin.audit.read`
- `admin.log.read`
- `admin.monitor.read`

**Deliverables:**
- [ ] 页面级权限过滤。
- [ ] 操作级按钮权限过滤。
- [ ] 服务端强校验，不能只靠前端隐藏。
- [ ] 后续数据库菜单前，先用代码注册式能力清单。

**Non-goals:**
- [ ] 第一版不做完整 RuoYi 式菜单权限平台。

**Verification:**
- [ ] 后端权限集成测试。
- [ ] 手工核对不同角色下 admin 页面与操作可见性。

### Project 8: Scheduler Governance

**Goal:** 给现有 `@Scheduled` 任务增加可治理入口，但保持轻量和安全。

**Depends on:** Project 5，或至少在任务中心稳定后开始。

**Current reuse points:**
- `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionCleanupScheduler.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RecycleBinCleanupScheduler.java`
- `backend/src/main/java/com/yoyuzh/platform/job/internal/application/BackgroundTaskWorker.java`

**Deliverables:**
- [ ] 提供只读任务注册表。
- [ ] 展示任务状态、上次执行、下次执行。
- [ ] 支持启停和手工触发一次。
- [ ] 审计所有调度治理动作。

**Non-goals:**
- [ ] 第一版不开放任意 cron 编辑。

**Verification:**
- [ ] 调度治理 API 测试。
- [ ] 手工验证启停与单次触发不影响原有默认调度。

### Project 9: Config History, Publish, Rollback

**Goal:** 借鉴 Apollo 的发布、历史和回滚思路，让运行时配置具备生产治理能力。

**Depends on:** Project 1 和 2。

**Deliverables:**
- [ ] 每次配置变更形成版本。
- [ ] 区分草稿、已发布状态。
- [ ] 支持查看 diff。
- [ ] 支持发布说明。
- [ ] 支持回滚。

**Non-goals:**
- [ ] 第一版不做多环境、多集群、灰度发布。

**Verification:**
- [ ] 配置版本与回滚测试。
- [ ] 审计日志联动校验。

### Project 10: Optional Nacos Adapter

**Goal:** 为未来集中配置中心接入提供可选能力，不让基础启动强依赖 Nacos。

**Depends on:** Project 1、2、9。

**Deliverables:**
- [ ] 抽象 `ConfigProvider` 或等价接口。
- [ ] 本地 DB 仍作为默认 provider。
- [ ] 为 Nacos 增加同步状态与错误状态模型。
- [ ] 不改变本地开发和默认部署方式。

**Non-goals:**
- [ ] 不要求所有环境必须接入 Nacos。

**Verification:**
- [ ] 保证未接 Nacos 时系统行为不变。

### Project 11: Optional Ops Monitor Entry

**Goal:** 预留 Spring Boot Admin / Actuator 风格监控入口，但与业务后台分离。

**Depends on:** Project 0，可在后期单独推进。

**Deliverables:**
- [ ] 设计独立监控菜单分组。
- [ ] 可选接入健康检查、JVM、线程、缓存、日志级别等入口。
- [ ] 严格限制暴露范围与权限。

**Non-goals:**
- [ ] 不把业务配置页和 JVM/线程监控混在一起。

**Verification:**
- [ ] 监控入口权限与可见性检查。

---

## Delivery Waves

### Wave 1: Backbone

- [ ] Project 0: Admin Foundation
- [ ] Project 1: Config Schema Backbone

**Exit criteria:**
- [ ] 后台已有统一列表/表单基础壳。
- [ ] schema 和 value 的后端骨架已存在。
- [ ] 前端已能根据基础 schema 渲染字段。

### Wave 2: Runtime Governance

- [ ] Project 2: System Config Center
- [ ] Project 6: Audit, Login Log, Exception Log
- [ ] Project 7: Permission Refinement

**Exit criteria:**
- [ ] 运行时配置可分组查看和修改。
- [ ] 高危操作有审计。
- [ ] 权限已细化到页面和动作级。

### Wave 3: Storage And Upload Governance

- [ ] Project 3: Storage Source Management
- [ ] Project 4: Upload Policy Management

**Exit criteria:**
- [ ] 存储源可治理、可测试连接、可审计。
- [ ] 上传策略进入后台治理，上传链路读取统一策略。

### Wave 4: Task And Scheduler Governance

- [ ] Project 5: Task Center
- [ ] Project 8: Scheduler Governance

**Exit criteria:**
- [ ] 后台任务和传输任务可筛选、查看、重试、清理。
- [ ] 核心调度任务有治理入口。

### Wave 5: Production Governance Extensions

- [ ] Project 9: Config History, Publish, Rollback
- [ ] Project 10: Optional Nacos Adapter
- [ ] Project 11: Optional Ops Monitor Entry

**Exit criteria:**
- [ ] 配置具备版本、发布、回滚能力。
- [ ] 可选配置中心适配不影响默认启动。
- [ ] 运维监控入口独立成面。

---

## Risk Controls

- [ ] 任何启动级敏感配置不得默认进入普通后台页面。
- [ ] 任何 secret、token、password、access key 不得明文下发前端。
- [ ] 任何 secret、token、password、access key 不得写入普通日志和审计详情。
- [ ] `ops.admin` 不得绕过领域模块边界直接修改底层真相。
- [ ] 所有新增后台写接口都必须经过服务端权限校验。
- [ ] 所有高危操作都必须提供确认与审计。
- [ ] 任何阶段都不应破坏当前上传、下载、登录、权限、文件管理主链路。

## Plan Follow-Ups

- [ ] Wave 1 开始前，单独产出 `Admin Foundation + Config Schema` 详细执行计划。
- [ ] Wave 2 开始前，单独产出 `System Config Center` 详细执行计划。
- [ ] Wave 3 开始前，单独产出 `Storage Source Management` 和 `Upload Policy Management` 详细执行计划。
- [ ] Wave 4 开始前，单独产出 `Task Center` 和 `Scheduler Governance` 详细执行计划。
- [ ] Wave 5 开始前，按真实规模再决定是否为 Nacos 和 Ops Monitor 单独立项。

## Self-Review

### Spec coverage

- 已覆盖系统配置管理、存储源管理、上传策略管理、上传/后台任务监控、日志与审计、配置中心适配、运维监控预留、权限控制、敏感信息保护。
- 已明确哪些配置适合迁入后台，哪些不适合。
- 已明确参考 RuoYi、JEECG、Nacos、Apollo、Spring Boot Admin 的借鉴方式，但不直接强依赖这些体系。

### Placeholder scan

- 本计划没有使用 “TBD” 或 “以后再说” 作为交付项。
- 可选项已明确标记为 optional，不会和基础能力混淆。

### Type consistency

- 本计划统一使用 `Config Schema`、`System Config Center`、`Storage Source Management`、`Upload Policy Management`、`Task Center` 这些固定项目名，供后续详细计划复用。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-29-admin-governance-system.md`.

Two execution options:

1. Subagent-Driven (recommended) - 使用 `superpowers:subagent-driven-development`，按项目或按 wave 派发子任务，逐项 review。
2. Inline Execution - 使用 `superpowers:executing-plans`，在当前会话按 wave 执行并设置检查点。
