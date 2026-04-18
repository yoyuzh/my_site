# 2026-04-11 企业级目标架构重构计划

## 1. 计划目标

本计划用于把当前仓库从“围绕现有功能堆叠的全栈实现”重构到 [architecture.md](C:/Users/yoyuz/Documents/code/my_site/docs/architecture.md) 定义的目标态企业架构。

目标不是做一次大爆炸式重写，而是按阶段把当前代码收敛到以下稳定形态：

1. 统一身份与授权模型
2. 工作空间与内容资产彻底分离
3. 分享与快传成为两个独立业务域
4. 上传成为内容接入机制，不再直接等同于文件业务
5. 后台任务成为统一异步处理底座
6. 存储策略成为系统级治理能力
7. 管理端成为治理入口，不再夹杂业务规则事实源

## 2. 当前基线

### 2.1 后端当前包结构

- `auth`
- `files.core`
- `files.upload`
- `files.share`
- `files.search`
- `files.events`
- `files.tasks`
- `files.storage`
- `files.policy`
- `transfer`
- `admin`
- `common`
- `config`
- `api.v2`

### 2.2 前端当前结构

- `pages`
- `admin`
- `lib`
- `components`
- `hooks`
- `mobile-pages`
- `mobile-components`

### 2.3 已完成的重构基础

根据 `memory.md`，以下基础性工作已经完成，可以作为本计划起点：

1. `BackgroundTaskRetryPolicy`、`BackgroundTaskStateManager`、`BackgroundTaskStateKeys` 已从 `BackgroundTaskService` 中抽离
2. `UploadPolicyResolver` 与 `UploadSessionStateMachine` 已从上传会话主服务中抽离
3. `AuthSessionPolicy` 已从 `AuthService` 中抽离
4. 文件事件链路已拆出：
   - `FileEventService`
   - `FileEventDispatcher`
   - `FileEventPayloadCodec`
5. 自动媒体元数据任务的 `correlationId` 幂等已经具备数据库级唯一约束

这说明当前代码已经有了第一批“规则抽离”的基础，但还没有进入真正的领域边界重组阶段。

## 3. 重构范围

本计划覆盖：

- 后端领域重组
- 后端 API 收口
- 权限模型统一
- 内容资产模型统一
- 管理端权限和设置边界统一
- 前端按业务域重组

本计划不覆盖：

- UI 视觉重设计
- Android 原生壳单独重写
- 全量 API 一次性废弃
- 数据库从零重建

## 4. 目标态模块映射

### 4.1 后端目标模块

目标态后端按业务域划分为：

- `identity-access`
- `workspace`
- `content-asset`
- `sharing`
- `transfer`
- `async-job`
- `storage-governance`
- `operations-admin`
- `common-kernel`

### 4.2 当前到目标的主要映射

| 当前模块 | 目标模块 | 说明 |
| --- | --- | --- |
| `auth` | `identity-access` | 保留认证能力，但要升级成统一账号、会话、角色、授权模型 |
| `files.core` | `workspace` + `content-asset` | 当前最需要拆分的模块 |
| `files.upload` | `content-asset` + `storage-governance` | 上传接入和存储策略不应继续混在文件业务里 |
| `files.share` | `sharing` | 需要收口旧分享与 v2 分享语义 |
| `transfer` | `transfer` | 保留域名，但拆分在线/离线子域和导入边界 |
| `files.tasks` | `async-job` | 升级为统一异步底座 |
| `files.events` | `workspace` + `async-job` 支撑层 | 事件不是独立业务域，应下沉为基础能力 |
| `files.policy` + `files.storage` | `storage-governance` | 统一存储策略、能力、迁移和对象落点 |
| `admin` | `operations-admin` | 从“全能 service”重构成治理入口 |
| `api.v2` | 各域 `api` 层 | 最终应按领域归属，而不是平行于领域存在 |

### 4.3 前端目标模块

目标态前端按业务域收口为：

- `account`
- `workspace`
- `sharing`
- `transfer`
- `admin`
- `common`

当前的 `pages`、`mobile-pages`、`lib` 需要逐步按领域拆解，而不是继续按“页面+工具库”累计。

## 5. 重构总策略

### 5.1 先抽规则，再拆边界

先把高价值业务规则从胖 Service 中抽成稳定规则对象，再移动目录和模块边界。  
这样可以避免“边拆边改语义”。

### 5.2 先统一模型，再收口接口

先统一后端领域模型，再决定哪些旧接口保留、哪些 v2 接口成为唯一主线。  
不要先删接口，再倒逼领域重构。

### 5.3 先后端领域，后前端跟随

这轮重构必须以后端领域边界为主。  
前端模块化重组应跟随后端稳定领域，而不是反过来。

### 5.4 每阶段都要可回归验证

每个阶段必须能在当前仓库命令范围内验证：

- `cd backend && mvn test`
- `cd front && npm run lint`
- `cd front && npm run build`

前端当前没有稳定的测试执行链路，因此阶段验收主要依赖类型检查、构建和关键后端回归。

## 6. 阶段计划

## 阶段 0：冻结目标语义和兼容边界

### 目标

在开始大规模结构调整前，先冻结必须保持一致的业务语义和兼容边界。

### 产出

1. 唯一的角色模型：`VISITOR / MEMBER / OPERATOR / ADMIN`
2. 唯一的资源模型：`WorkspaceNode` 与 `ContentAsset` 分离
3. 唯一的管理权限事实源
4. 分享与快传的边界定义
5. 旧接口与新接口的兼容期说明

### 当前代码关注点

- `auth/*`
- `admin/AdminAccessEvaluator.java`
- `config/SecurityConfig.java`
- `files/core/FileService.java`
- `files/share/ShareV2Service.java`
- `transfer/TransferService.java`

### 必须先确认的决策

1. 是否废弃“管理员用户名白名单决定权限”这条规则
2. 是否保留旧 `/api/files/share-links/**`
3. 是否保留旧 `/api/files/upload/**`
4. `maxDownloads` 是否继续同时约束导入和下载
5. `MODERATOR` 是否升级为 `OPERATOR`，还是直接移除

### 验收标准

- 目标语义形成一份可执行的决策基线
- 所有后续代码改动都不再重新定义这些问题

## 阶段 1：统一身份与授权域

### 目标

把当前 `auth + admin 权限入口 + session 规则` 重构为独立的 `identity-access` 域。

### 需要解决的问题

1. `User.role` 与 admin 白名单的双事实源
2. access token 吊销、refresh token 旋转、活跃会话切换分散在不同位置
3. 管理权限和资源权限没有统一授权模型

### 主要动作

1. 引入统一授权上下文
2. 抽出角色判断和资源授权入口
3. 让 `AuthSessionPolicy` 成为唯一会话规则入口
4. 让 admin 鉴权走角色/权限模型，而不是用户名白名单
5. 把 `DevAuthController` 对正式授权模型的影响限制在开发环境边界内

### 涉及文件

- `backend/src/main/java/com/yoyuzh/auth/*`
- `backend/src/main/java/com/yoyuzh/admin/AdminAccessEvaluator.java`
- `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`

### 阶段结果

- 管理台权限不再依赖用户名硬编码
- 登录态失效和客户端会话规则由统一策略控制

### 验收标准

- 所有受保护接口都能通过统一授权入口判断
- 改密、封禁、重登、刷新不再各写一套会话切换逻辑
- `cd backend && mvn test` 通过

## 阶段 2：拆分工作空间域与内容资产域

### 目标

把当前 `files.core` 这个胖模块拆成两个稳定业务域：

- `workspace`
- `content-asset`

### 需要解决的问题

1. `StoredFile` 同时承担路径、逻辑生命周期、物理内容引用
2. `FileBlob` / `FileEntity` / `StoredFile` 三层模型没有清晰主从边界
3. 删除、复制、导入、恢复同时跨逻辑层和物理层

### 目标模型

- `WorkspaceNode` 负责：
  - 层级
  - 名称
  - 所有权
  - 回收站生命周期
- `ContentAsset / ContentVersion` 负责：
  - 物理对象
  - 版本
  - 派生资产
  - 内容不可变性

### 主要动作

1. 给 `files.core` 划清“逻辑节点操作”和“内容对象操作”
2. 让复制、导入、恢复优先操作逻辑节点绑定，而不是直接操作底层对象
3. 把目录树逻辑从内容对象读写中剥离
4. 让最终清理只依赖内容引用与保留策略

### 涉及文件

- `backend/src/main/java/com/yoyuzh/files/core/*`
- `backend/src/main/java/com/yoyuzh/files/storage/*`
- `backend/src/main/java/com/yoyuzh/files/policy/*`

### 阶段结果

- 路径、目录、回收站归工作空间域
- 内容对象、版本、派生产物归内容资产域

### 验收标准

- 普通文件操作不再直接驱动物理对象行为
- 内容版本成为稳定对象，逻辑节点只是引用它
- `cd backend && mvn test` 通过

## 阶段 3：收口上传与存储治理

### 目标

把上传从“文件业务的一部分”收敛为“内容接入机制”，并把存储策略升级为系统级治理域。

### 需要解决的问题

1. 旧上传和 v2 上传双轨并存
2. 上传规则在 `FileService` 和 `UploadSessionService` 中重复
3. 上传模式由实现细节驱动，而不是标准化能力模型驱动

### 主要动作

1. 让 `UploadPolicyResolver` 成为唯一上传规则入口
2. 让上传完成只产生 `ContentVersion`
3. 工作空间节点创建放到上传完成后的业务编排层
4. 收口旧 `/api/files/upload/**` 到兼容层，新的正式能力只保留 v2 上传会话
5. 统一 `StoragePolicy`、能力矩阵、对象大小限制和迁移约束

### 涉及文件

- `backend/src/main/java/com/yoyuzh/files/upload/*`
- `backend/src/main/java/com/yoyuzh/files/policy/*`
- `backend/src/main/java/com/yoyuzh/files/storage/*`
- `backend/src/main/java/com/yoyuzh/files/core/FileService.java`
- `backend/src/main/java/com/yoyuzh/api/v2/files/UploadSessionV2Controller.java`

### 阶段结果

- 上传成为统一接入层
- 存储治理成为独立域

### 验收标准

- 上传规则只有一套事实源
- v2 上传成为正式主线
- `cd backend && mvn test` 通过

## 阶段 4：收口分享域

### 目标

把分享从当前“旧接口 + v2 接口并存”收敛为统一的 `sharing` 域。

### 需要解决的问题

1. 分享存在旧接口和 v2 接口双轨
2. 分享额度、密码、导入和下载权限没有形成统一策略模型
3. 分享导入逻辑过于依赖底层文件实现

### 主要动作

1. 定义统一的 `ShareGrant` 模型
2. 把分享的访问、下载、导入权限归到同一个策略对象
3. 明确“分享对外公开的是逻辑节点，不是底层内容对象”
4. 旧分享接口保留兼容包装层，内部转发到统一分享域

### 涉及文件

- `backend/src/main/java/com/yoyuzh/files/share/*`
- `backend/src/main/java/com/yoyuzh/api/v2/shares/*`
- `backend/src/main/java/com/yoyuzh/files/core/FileController.java`
- `backend/src/main/java/com/yoyuzh/files/core/FileService.java`

### 阶段结果

- 分享只有一套规则和一套内部模型

### 验收标准

- 分享创建、访问、下载、导入都通过统一服务入口
- 旧接口只剩兼容职责
- `cd backend && mvn test` 通过

## 阶段 5：拆分快传域

### 目标

把当前 `transfer` 模块拆成真正的传输域，而不是继续由 `TransferService` 统管在线、离线、上传、导入、容量判断。

### 需要解决的问题

1. 在线快传和离线快传当前共用一个胖 Service
2. 离线快传 ready、上传、容量、导入耦合严重
3. 快传与分享的边界仍然容易混淆

### 主要动作

1. 拆出：
   - `OnlineTransferService`
   - `OfflineTransferService`
   - `OfflineTransferQuotaService`
   - `TransferImportService`
2. 在线快传只保留临时连接语义
3. 离线快传只保留临时托管语义
4. 导入动作通过工作空间/内容资产编排层完成

### 涉及文件

- `backend/src/main/java/com/yoyuzh/transfer/*`
- `front/src/lib/transfer.ts`
- `front/src/pages/Transfer.tsx`

### 阶段结果

- 在线和离线快传成为两个清晰子域
- 快传导入不再直接绑死旧文件实现

### 验收标准

- 在线和离线业务规则拆分完成
- 离线容量和 ready 条件成为独立规则入口
- `cd backend && mvn test` 通过

## 阶段 6：统一异步任务域

### 目标

把当前 `files.tasks` 从“文件附属模块”升级为平台级 `async-job` 域。

### 需要解决的问题

1. 任务模型仍然偏文件中心
2. 任务状态 JSON 和运行机制还没有完全 typed 化
3. 任务类型与业务域之间的边界仍不清晰

### 主要动作

1. 把任务分成：
   - 命令入口
   - 执行入口
   - 重试策略
   - 状态表示
   - 幂等入口
2. 用 typed state 逐步替代广泛的 JSON merge
3. 把任务从文件附属实现提升为可服务多个业务域的统一底座

### 涉及文件

- `backend/src/main/java/com/yoyuzh/files/tasks/*`
- `backend/src/main/java/com/yoyuzh/common/broker/*`
- `backend/src/main/java/com/yoyuzh/api/v2/tasks/*`

### 阶段结果

- 异步任务成为独立业务底座

### 验收标准

- 新任务类型不再需要复制粘贴状态处理逻辑
- 任务状态推进和重试规则有唯一实现
- `cd backend && mvn test` 通过

## 阶段 7：重构管理域

### 目标

把当前 `admin` 从“聚合各种系统能力的大 Service”重构成真正的 `operations-admin` 治理入口。

### 需要解决的问题

1. 可写设置与运行时快照混在一起
2. 管理能力和业务领域对象之间缺少稳定的边界
3. 权限、审计和治理动作没有形成一致模型

### 主要动作

1. 拆出：
   - `AdminConfigSnapshotService`
   - `AdminMutableSettingsService`
   - `AdminUserGovernanceService`
   - `AdminStorageGovernanceService`
   - `AdminAuditService`
2. 让管理端只调用各领域的治理接口，而不是直接承载业务规则
3. 把审计作为显式能力引入

### 涉及文件

- `backend/src/main/java/com/yoyuzh/admin/*`

### 阶段结果

- 管理域从“胖 service”变成治理编排层

### 验收标准

- 设置、治理、审计分离
- 管理端不再承担业务事实源
- `cd backend && mvn test` 通过

## 阶段 8：前端按业务域重组

### 目标

让前端结构跟随后端领域，而不是继续沿 `pages + lib + mobile-pages` 演进。

### 主要动作

1. 把 `pages` 和 `mobile-pages` 按域拆成：
   - `account`
   - `workspace`
   - `sharing`
   - `transfer`
   - `admin`
   - `common`
2. 把 `lib` 中的接口调用和状态逻辑迁移到领域内部
3. 统一桌面端和移动端页面的业务入口，只保留展示层差异

### 涉及文件

- `front/src/pages/*`
- `front/src/mobile-pages/*`
- `front/src/lib/*`
- `front/src/admin/*`
- `front/src/App.tsx`

### 阶段结果

- 前端与后端都围绕同一领域模型组织

### 验收标准

- `cd front && npm run lint` 通过
- `cd front && npm run build` 通过

## 7. 先后顺序与依赖

严格顺序建议如下：

1. 阶段 0：冻结语义
2. 阶段 1：身份与授权
3. 阶段 2：工作空间 / 内容资产拆分
4. 阶段 3：上传 / 存储治理收口
5. 阶段 4：分享域收口
6. 阶段 5：快传域拆分
7. 阶段 6：异步任务域统一
8. 阶段 7：管理域重构
9. 阶段 8：前端域化重组

依赖关系：

- 身份与授权必须先于管理域重构
- 工作空间 / 内容资产拆分必须先于分享和快传彻底收口
- 上传 / 存储治理必须先于内容资产域稳定
- 前端域化必须放在后端领域边界稳定之后

## 8. 每阶段的兼容要求

### 后端兼容要求

- 每个阶段优先保持现有 API 对外行为稳定
- 旧接口可以保留兼容层，但内部必须逐步转发到目标域模型
- 不在同一阶段同时做“领域重组 + 外部 API 大改”

### 数据兼容要求

- 不允许把现有数据迁移风险和领域重构耦合在同一批次
- 引入新模型时，优先通过双写、映射或兼容读过渡

### 前端兼容要求

- 路由和核心页面入口在后端领域稳定前尽量保持
- 先迁移数据访问层，再迁移页面结构

## 9. 验收与验证命令

每个后端阶段完成后至少执行：

```powershell
cd backend
mvn test
```

每个前端阶段完成后至少执行：

```powershell
cd front
npm run lint
npm run build
```

如果阶段只改后端，不额外要求前端构建。  
如果阶段只改前端，也应确认依赖的 API 契约没有被破坏。

## 10. 本轮建议先开做的具体批次

如果从当前代码直接开工，建议先做下面三个批次：

### 批次 A：身份与授权统一

- 去掉 admin 白名单作为最终权限事实源
- 统一 `AuthSessionPolicy` 的会话失效入口
- 让 `/api/admin/**` 接入统一授权判断

### 批次 B：工作空间与内容资产拆分第一刀

- 先在 `files.core` 内部分出“工作空间节点规则”和“内容资产规则”
- 不急着改数据库表名，先改代码归属

### 批次 C：上传与分享收口

- 让上传只产生内容接入结果
- 让分享只依赖逻辑节点授权
- 旧上传和旧分享接口退化为兼容层

这三个批次完成后，整套系统才真正具备继续深度重构的稳定底盘。
