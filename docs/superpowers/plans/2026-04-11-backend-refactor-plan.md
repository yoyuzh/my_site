# 后端重构方案草案

## 一、需要重构的模块

1. 在线快传模块
   - 相关代码：`transfer/TransferService`、`transfer/TransferSessionStore`、`transfer/TransferSession`

2. 离线快传模块
   - 相关代码：`transfer/TransferService`、`OfflineTransferSessionRepository`、存储额度相关 admin 指标逻辑

3. 异步任务模块
   - 相关代码：`files/tasks/BackgroundTaskService`、`BackgroundTaskWorker`、`BackgroundTaskRepository`、`BackgroundTask`

4. Broker 消息模块
   - 相关代码：`common/broker/*`、`MediaMetadataTaskBrokerPublisher`、`MediaMetadataTaskBrokerConsumer`

5. 文件事件模块
   - 相关代码：`files/events/FileEventService`、`RedisFileEventPubSubPublisher`、`RedisFileEventPubSubListener`

6. 上传会话模块
   - 相关代码：`files/upload/UploadSessionService`、`UploadSessionRuntimeStateService`、`RedisUploadSessionRuntimeStateService`

7. 认证会话模块
   - 相关代码：`auth/AuthService`、`RefreshTokenService`、`AuthTokenInvalidationService`、`config/JwtAuthenticationFilter`

8. Admin 设置模块
   - 相关代码：`admin/AdminService`、settings/filesystem 相关 DTO 和 controller

## 二、每个模块现在的问题

### 1. 在线快传模块
- 业务目标看起来是“支持 Redis + 多实例 + 分布式锁”。
- 但当前锁只包读取，不包“读取后修改再保存”整个事务。
- Redis 模式下是整对象覆盖写，存在并发覆盖和信令丢失问题。
- “仅允许一个接收方加入”是业务规则，但现在不能稳定保证。

### 2. 离线快传模块
- 会话、文件上传、ready 状态、存储额度检查都耦合在 `TransferService`。
- 存储额度检查不是原子行为，并发上传可能突破上限。
- 上传状态推进和额度校验缺少统一状态机。

### 3. 异步任务模块
- 任务创建、claim、lease、heartbeat、retry、manual retry、状态 JSON 解析都混在一个 service。
- `correlationId` 被当作幂等键使用，但没有数据库级保证。
- attempt/retry 语义虽然有实现，但规则没有被明确定义和隔离。
- `public_state_json/private_state_json` 既是业务状态，又承担运行态和展示态，职责混杂。

### 4. Broker 消息模块
- 当前消费语义是“先 pop 再处理”，天然是 at-most-once。
- 但业务又想靠 `correlationId` 做去重，更像 at-least-once。
- 消息可靠性、重试、死信、幂等边界都没有单独建模。
- broker 只是技术实现，但现在承担了业务可靠性语义。

### 5. 文件事件模块
- 本地 SSE 推送、事件持久化、跨实例广播在一个 service 中耦合。
- listener 对坏消息直接抛异常，容错策略不清晰。
- 本地订阅过滤规则、跨实例复制规则、事件 payload 规则没有独立边界。

### 6. 上传会话模块
- DB 状态和 Redis runtime 状态并存，但权威关系没有被结构化表达。
- Redis runtime 读写失败基本是静默处理，观测和告警语义不明确。
- 上传模式判断、会话状态推进、远端对象清理、运行态刷新都在一个 service 里。

### 7. 认证会话模块
- access token 吊销、refresh token 轮换、active session 切换、封禁/改密后的清理都存在，但没有统一策略层。
- “立即失效”的定义不够严格，尤其是时间边界处理。
- 桌面端/移动端的单端活跃规则散落在多个方法中。

### 8. Admin 设置模块
- 可写设置、运行态快照、环境配置读取混在一个聚合响应里。
- `writeSupported` 只能提示能力，不能从结构上表达“配置源”和“运行态”区别。
- 业务上已经有“可持久化设置”和“只读快照”两类概念，但代码层还没完全分开。

## 三、应该如何拆分职责

### 1. 在线快传模块
建议拆成：
- `OnlineTransferSessionService`
  - 负责业务规则：join、postSignal、poll、过期判断
- `OnlineTransferSessionRepository`
  - 负责会话持久化/加载
- `OnlineTransferSessionLockManager`
  - 负责会话级原子执行
- `OnlineTransferSessionAggregate`
  - 负责接收方唯一、信令队列、cursor 等领域行为

目标：
- 所有读改写保存通过一个原子入口完成
- service 不直接操作 Redis 细节

### 2. 离线快传模块
建议拆成：
- `OfflineTransferSessionService`
- `OfflineTransferStorageQuotaService`
- `OfflineTransferFileStorageService`

目标：
- 会话状态推进和额度规则分离
- 上传文件、ready 判定、容量扣减有清晰边界

### 3. 异步任务模块
建议拆成：
- `BackgroundTaskCommandService`
  - 创建、取消、人工重试
- `BackgroundTaskExecutionService`
  - claim、heartbeat、complete、fail
- `BackgroundTaskRetryPolicy`
  - retryable、attempt、delay 计算
- `BackgroundTaskStateCodec`
  - JSON <-> typed state
- `BackgroundTaskIdempotencyService`
  - correlationId 规则

目标：
- “任务业务规则”和“任务运行机制”分离
- state JSON 不再到处手写 merge/remove

### 4. Broker 消息模块
建议拆成：
- `BrokerMessagePublisher`
- `BrokerMessageConsumer`
- `BrokerDeliveryPolicy`
- `BrokerMessageHandler`

目标：
- 明确消息语义是 at-least-once
- 可靠性和业务处理解耦
- 去重不靠消费者临时判断，而靠任务幂等层

### 5. 文件事件模块
建议拆成：
- `FileEventRecorder`
  - 落库
- `FileEventDispatcher`
  - 本地 SSE 分发
- `FileEventReplicationPublisher`
  - 跨实例发布
- `FileEventReplicationConsumer`
  - 跨实例消费
- `FileEventPayloadCodec`
  - payload 结构规则

目标：
- 本地分发和跨实例同步分离
- 坏消息只影响当前消息，不影响整体监听

### 6. 上传会话模块
建议拆成：
- `UploadSessionLifecycleService`
  - create/cancel/complete/prune
- `UploadSessionContentService`
  - prepare/upload/part-record
- `UploadSessionRuntimeViewService`
  - 运行态读取
- `UploadSessionRuntimeStateStore`
  - Redis/no-op 存储实现
- `UploadPolicyResolver`
  - 上传模式、大小限制、策略能力决策

目标：
- DB 生命周期和 Redis 观测态分离
- runtime state 明确为辅助层

### 7. 认证会话模块
建议拆成：
- `AuthSessionPolicy`
  - 单端活跃、吊销、生效边界
- `AccessTokenRevocationService`
- `RefreshTokenRotationService`
- `UserSessionRotationService`

目标：
- 改密/封禁/登录/刷新都走同一套会话规则
- 避免规则散落在 `AuthService` 和 admin 逻辑里

### 8. Admin 设置模块
建议拆成：
- `AdminConfigSnapshotService`
  - 只读运行态
- `AdminMutableSettingsService`
  - 可写设置
- `AdminSettingsFacade`
  - 对外聚合

目标：
- 区分“系统当前状态”和“可持久化业务设置”
- 减少 admin service 持续膨胀

## 四、哪些逻辑应抽到公共规则层

这些不该继续散在各 service 里：

### 1. 幂等规则层
- `correlationId` 生成规则
- 同一业务动作是否允许重复创建任务
- 幂等冲突时返回什么结果

### 2. 状态机规则层
- 在线快传会话状态推进
- 离线快传 ready 条件
- 上传会话状态推进
- 后台任务状态推进

### 3. 并发规则层
- 会话级锁
- 额度检查的原子更新
- 任务 claim/lease 规则

### 4. 重试规则层
- 哪些错误可重试
- attemptCount 如何累积
- 自动重试和人工重试的区别
- backoff 计算规则

### 5. 认证会话规则层
- 单端活跃规则
- 改密/封禁/重置密码后的清理规则
- access token / refresh token 失效边界

### 6. 事件与消息规则层
- file event payload 结构
- listener 遇到坏消息如何处理
- broker 消息投递语义

### 7. 配置语义规则层
- “运行态快照”
- “持久化设置”
- “环境配置只读项”

## 五、推荐重构顺序

### 第一阶段：先统一规则，再动结构
1. 先定四个核心语义
   - 在线快传并发语义
   - 异步任务幂等语义
   - broker 投递语义
   - 认证会话失效语义

2. 把这些规则抽成独立策略/规则类
   - 先不大改 controller 和 repository

### 第二阶段：先改最危险的一致性问题
1. 在线快传
   - 把读改写保存收敛到单个原子入口
2. 异步任务幂等
   - 让 `correlationId` 成为真正约束
3. broker 消费可靠性
   - 不再“先丢消息再处理”

### 第三阶段：收敛状态机和运行机制
1. 后台任务状态机拆分
2. 上传会话 lifecycle/runtime 拆分
3. 文件事件 recorder/dispatcher/replication 拆分

### 第四阶段：清理配置和管理侧语义
1. admin settings 拆成 snapshot + mutable settings
2. auth session 规则统一落到策略层
3. 去掉 service 中分散的规则分支

## 六、建议优先落地的重构任务列表

1. 先定义并固定“统一业务规则”接口
   - 不改功能，只固化规则边界

2. 重构在线快传
   - 先解决原子性问题

3. 重构后台任务幂等和 broker
   - 先解决重复任务和消息丢失问题

4. 重构后台任务状态机
   - 把 retry/lease/attempt 语义统一

5. 重构文件事件模块
   - 提高跨实例容错

6. 重构上传会话模块
   - 明确 DB 状态与 Redis runtime 的关系

7. 最后整理 auth/admin
   - 收口规则，减少横向重复逻辑

## 必须先达成一致的核心规则

1. 在线快传必须是“原子读改写”，不能接受覆盖写丢信令
2. 自动媒体任务必须以“任务幂等”保证最终唯一，不能靠先查再插
3. broker 语义统一为“至少投递一次 + 消费端幂等”
4. 后台任务 `attemptCount`、自动重试、人工重试必须统一定义
5. 上传会话中 DB 状态是权威状态，Redis 只做辅助观测
6. 文件事件坏消息必须隔离处理，不能拖垮整个 listener
7. 认证相关的“立即失效”必须有明确且统一的规则
8. admin 设置必须区分“运行态快照”和“可写业务设置”

## 2026-04-11 Execution Status

Completed in the first implementation batch:

1. Extracted task rule components from `BackgroundTaskService`
   - `BackgroundTaskRetryPolicy`
   - `BackgroundTaskStateManager`
   - `BackgroundTaskStateKeys`

2. Split file-event orchestration from local dispatch
   - `FileEventService` now coordinates persistence and after-commit publishing
   - `FileEventDispatcher` owns local SSE subscription and delivery
   - `FileEventPayloadCodec` owns payload shaping
   - malformed Redis pub/sub payloads are isolated and dropped by the listener

3. Split upload rules from upload-session orchestration
   - `UploadPolicyResolver` owns upload-mode and size/chunk rules
   - `UploadSessionStateMachine` owns lifecycle transitions
   - `UploadSessionService` now coordinates repository + runtime-state updates around those rules

4. Extracted auth session rotation rules
   - `AuthSessionPolicy` now owns per-client session rotation semantics used by `AuthService`

Verification:

- `cd backend && mvn "-Dtest=BackgroundTaskRetryPolicyTest,UploadSessionStateMachineTest,AuthSessionPolicyTest,FileEventServiceTest,RedisFileEventPubSubListenerTest,BackgroundTaskServiceTest,UploadSessionServiceTest,AuthServiceTest" test`
- `cd backend && mvn test`

Current remaining high-value work from this plan:

- deeper background-task command/execution separation
- offline-transfer quota atomicity split
- broader file-event replication boundary cleanup
- admin snapshot vs mutable-settings facade split
- further auth/admin rule consolidation
