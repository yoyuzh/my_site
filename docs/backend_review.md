# 后端代码 Review 报告

> Spring Boot 3.3.8 · Java 17 · 472 main 文件 · 63 test 文件

---

## 总体评价

项目整体架构意图清晰，围绕 **模块化单体 + 领域分层**（`api / web / application / domain / infra`）建立。安全基础设施（JWT、BCrypt、Spring Security）、异常处理（`GlobalExceptionHandler`）、测试分层（unit/integration/archunit）都已存在且较为完整。以下按严重程度分级列出发现的问题和建议。

---

## 🔴 严重问题（应立即修复）

### 1. `FileService` 是 God Class，违反 SRP

**文件**：`files/workspace/internal/application/FileService.java`（1673 行）

`FileService` 同时承担了：上传、下载、重命名、移动、复制、删除、回收站、打包压缩、解压、收藏、默认目录初始化、定时任务清理、下载签名生成、批量导入……

- 构造函数有 **18 个参数**，其中两个重载构造函数参数顺序不同、职责模糊，容易误传。
- 类内存在死代码路径：`validateUpload` / `ensureWithinStorageQuota` 有 `if (fileUploadRulesService != null)` 分支——`fileUploadRulesService` 永远不为 null（构造注入），但旧的手动实现备份仍残留。
- `normalizeExternalImportDirectories`、`normalizeExternalImportFiles`、`validateExternalImportBatch` 等私有方法已被 `ExternalImportRulesService` 取代，但旧代码未删除。

**建议**：将 archive/zip、secure-link signature、file-event recording 等职责提取为独立 Service。删除已委托出去的内部旧实现。

---

### 2. `OfflineTransferService` 在 application 层返回 `ResponseEntity`

**文件**：`transfer/internal/application/OfflineTransferService.java`，第 131 行

```java
public ResponseEntity<?> downloadOfflineFile(String sessionId, String fileId) {
    ...
    return ResponseEntity.status(302).location(URI.create(downloadUrl)).build();
```

`application` 层不应持有 HTTP 协议细节。`ResponseEntity` 属于 `web` 层契约，且已通过 `TransferSessionApi`（公共 API 接口）污染了模块边界。`TransferService` 也原样透传了该 `ResponseEntity`。

**建议**：抽象出 `OfflineDownloadResult`（类似已有的 `WorkspaceDownloadResult`），在 controller 层将其转换为 `ResponseEntity`。

---

### 3. 安全规则默认开放（`SecurityConfig`）

**文件**：`boot/security/SecurityConfig.java`，第 74–79 行

```java
.requestMatchers("/api/admin/**").authenticated()
...
.anyRequest().permitAll()   // 危险默认值
```

- `anyRequest().permitAll()` 意味着所有未命中前面规则的请求**默认开放**，包括将来新增的 API 端点。
- `/api/admin/**` 只有 `.authenticated()`，缺少角色约束。

**建议**：将 `anyRequest()` 改为 `.denyAll()`；给 `/api/admin/**` 加 `hasRole("ADMIN")` 约束。

---

### 4. `ErrorCode` 过于贫血，绝大多数错误用 `UNKNOWN`

**文件**：`shared/kernel/ErrorCode.java`（只有 4 个枚举值）

包括存储配额不足、文件名非法、pickup code 格式错误、session 过期等完全不同语义的错误，都使用 `ErrorCode.UNKNOWN`，导致前端无法做细粒度处理。

**建议**：扩充 `QUOTA_EXCEEDED`、`INVALID_INPUT`、`SESSION_EXPIRED`、`DUPLICATE_NAME` 等细粒度错误码。

---

## 🟡 中等问题（应在近期迭代修复）

### 5. `@Scheduled` 方法放在 `FileService` 中（架构错位）

`pruneExpiredRecycleBinItems()` 是调度任务，不应在 application service 中。应移到 `boot` 层或独立 job handler（参考 `platform.job` 的 `BackgroundTaskWorker`）。

---

### 6. `StoredFile.prePersist()` 使用 `LocalDateTime.now()` 而非 Clock

**文件**：`files/workspace/internal/domain/StoredFile.java`，第 77–89 行

`LocalDateTime.now()` 没有时区语义，且无法注入 `Clock` 进行测试控制。时区变化或测试时间行为时会产生隐藏 bug。

---

### 7. `TransferSessionStore.save()` 静默吞掉 `JsonProcessingException`

**文件**：`transfer/internal/infra/TransferSessionStore.java`，第 77 行

```java
} catch (JsonProcessingException ignored) {
}
```

序列化失败被静默吞掉，Redis 中的 session 不更新，但调用方误以为成功——会导致信号丢失、状态不一致。

**建议**：至少 `log.warn(...)` 记录异常，或抛出 `BusinessException`。

---

### 8. `OnlineTransferService.nextPickupCode()` 存在无限循环风险

**文件**：`transfer/internal/application/OnlineTransferService.java`，第 85–91 行

```java
do {
    pickupCode = sessionStore.nextPickupCode();
} while (offlineTransferSessionRepository.existsByPickupCode(pickupCode));
```

pickup code 空间耗尽或 Redis 异常时会无限循环。应加 retry 上限并在超限后抛出 `BusinessException`。

---

### 9. 代码重复：`normalizeLeafName` / `normalizeRelativePath` 被复制三次

以下三处有几乎完全相同的实现：
- `OnlineTransferService`
- `OfflineTransferService`
- `RuntimeWorkspacePathPolicy`（`normalizeLeafName`）

违反 DRY 原则，应提取为 `TransferPathNormalizer` 工具类。

---

## 🟢 建议/优化（技术债）

### 10. 混用构造函数注入和 `@Autowired(required = false)` 字段注入

```java
@Autowired(required = false)
private FileEventApi fileEventApi;
```

建议统一使用构造函数注入，null-safe 处理在注入点完成，提升可测试性。

### 11. `buildSecureLinkSignature` 使用 MD5

MD5 存在碰撞风险。建议改用 `HmacSHA256`（`javax.crypto.Mac`），secret 从配置注入。

### 12. `TransferService` 是纯委托层（可删除）

`TransferService` 只是 1:1 转发调用到 `TransferSessionApi`，无额外逻辑。可直接删除，让 controller 依赖接口。

---

## 测试覆盖缺口

| 模块 | 已覆盖 | 缺失 |
|---|---|---|
| `files.workspace` (FileService) | 上传/下载/rename/move/copy/delete/recycle | `normalizeBlobObjectKey` 路径穿越防护、`readZipCompatibleArchive` 恶意路径、`buildPublicPackageDownloadUrl` 签名格式、`detectCommonRootDirectoryName` 各场景 |
| `transfer` | TransferSession 基本流、OnlineTransferService 信号路由 | `OfflineTransferService` 权限校验、session 过期、pickup code 格式校验 |
| `security` | Auth 集成测试 | `anyRequest().permitAll()` 开放漏洞验证 |
| `files.workspace` (PathPolicy) | 创建层级、冲突检测 | `normalizeDirectoryPath` 对 `..` 的边界处理、`ensureExistingDirectoryPath` 路径穿越 |
