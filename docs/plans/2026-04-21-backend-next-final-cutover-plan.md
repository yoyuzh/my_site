# Backend-Next Final Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前 `backend/` 运行时代码中完成剩余 `backend-next` 架构迁移，清掉 Task 5-9 的遗留断口，并把运行时包结构、依赖方向、验证结果统一拉到最终 cutover 状态。

**Architecture:** 不再新增第二套实现，也不继续做“平行旧架构维护”。执行方式是基于现有已迁移工作树，逐任务验证 `files.upload`、`files.sharing/files.search`、`transfer`、`ops.admin`、`boot/shared/infra` 的目标结构，修复断口后跑阶段验证，最后做一次全量 cutover 审计并清除仍存活的旧根依赖。

**Tech Stack:** Java 17, Spring Boot 3.3.8, Maven, JUnit 5, Mockito, ArchUnit, Spring MVC, Spring Security, Spring Data JPA.

---

## Scope Check

完整迁移仍然是多个独立子系统，不能再写成一个“单任务大改”。这份计划只覆盖当前剩余的最终 cutover 范围：

- `files.upload`：只保留 ingress/session/process control
- `files.sharing` 与 `files.search`：彻底经显式 API 暴露
- `transfer`：停止跨上下文泄漏
- `ops.admin`：只做治理编排
- `boot/shared.kernel/infra`：完成基础层归位并清理旧根
- 最终全量验证与 legacy-root 审计

## File Structure

### 重点修改目录

- `backend/src/main/java/com/yoyuzh/files/upload/**`
  - upload 目标模块，继续收口 `api/internal/{application,domain,infra,web}`
- `backend/src/main/java/com/yoyuzh/files/sharing/**`
  - sharing 目标模块，替代遗留 `files.share`
- `backend/src/main/java/com/yoyuzh/files/search/**`
  - search 目标模块，替代遗留 `files.events` 和平铺 search 入口
- `backend/src/main/java/com/yoyuzh/transfer/**`
  - transfer 目标模块，整理 `api/internal/*`
- `backend/src/main/java/com/yoyuzh/ops/admin/**`
  - admin 治理入口与 orchestration
- `backend/src/main/java/com/yoyuzh/boot/**`
- `backend/src/main/java/com/yoyuzh/shared/kernel/**`
- `backend/src/main/java/com/yoyuzh/infra/**`
  - 最终基础层归位与 legacy root 清理

### 重点验证文件

- `backend/src/test/java/com/yoyuzh/architecture/Task5UploadIngressArchitectureTest.java`
- `backend/src/test/java/com/yoyuzh/architecture/Task6SharingSearchArchitectureTest.java`
- `backend/src/test/java/com/yoyuzh/architecture/Task7TransferArchitectureTest.java`
- `backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java`
- `backend/src/test/java/com/yoyuzh/architecture/Task9BootSharedInfraArchitectureTest.java`

---

## Task 1: Reconcile Upload As Ingress Only

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/application/RuntimeUploadCompletionApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/application/RuntimeUploadTargetPolicy.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/core/FileController.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/core/FileService.java`
- Test: `backend/src/test/java/com/yoyuzh/architecture/Task5UploadIngressArchitectureTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2ControllerTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/upload/UploadSessionServiceTest.java`

- [x] **Step 1: Run upload tranche verification first**

Run:

```bash
cd backend && mvn test -Dtest=Task5UploadIngressArchitectureTest,UploadSessionV2ControllerTest,UploadSessionServiceTest,RuntimeUploadCompletionApiTest -q
```

Expected:

- 如果迁移已完整，直接 PASS
- 如果仍失败，失败点只能是 upload 对 `files.core` / `workspace` / `content` 的 ownership 泄漏或 web/application 分层问题

- [x] **Step 2: Keep upload completion only as content/workspace handoff**

Target code shape:

```java
public RegisteredContentFile completeStoredBlob(UploadCompletionCommand command) {
    fileContentStorage.completeBlobUpload(command.objectKey(), command.contentType(), command.size());
    workspacePathPolicy.ensureDirectoryHierarchy(command.userId(), command.normalizedPath());
    FileBlob blob = createAndSaveBlob(command.objectKey(), command.contentType(), command.size());
    return contentRegistrationApi.registerBlob(new ContentRegistrationCommand(
            command.userId(),
            command.normalizedPath(),
            command.filename(),
            command.contentType(),
            command.size(),
            new ContentBlobReference(blob.getObjectKey(), blob.getContentType(), blob.getSize())
    ));
}
```

- [x] **Step 3: Keep target validation only on upload boundary**

Target code shape:

```java
public ValidatedUploadTarget validateUpload(Long userId,
                                            long userMaxUploadSizeBytes,
                                            long userStorageQuotaBytes,
                                            String path,
                                            String filename,
                                            long size) {
    String normalizedPath = workspacePathPolicy.normalizeDirectoryPath(path);
    String normalizedFilename = workspacePathPolicy.normalizeLeafName(filename);
    workspacePathPolicy.ensureNodeNameAvailable(userId, normalizedPath, normalizedFilename, "同目录下文件已存在");
    return new ValidatedUploadTarget(normalizedPath, normalizedFilename, effectiveMaxUploadSize);
}
```

- [x] **Step 4: Re-run upload tranche verification**

Run:

```bash
cd backend && mvn test -Dtest=Task5UploadIngressArchitectureTest,UploadSessionV2ControllerTest,UploadSessionServiceTest,RuntimeUploadCompletionApiTest -q
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/upload \
        backend/src/main/java/com/yoyuzh/files/core/FileController.java \
        backend/src/main/java/com/yoyuzh/files/core/FileService.java \
        backend/src/test/java/com/yoyuzh/architecture/Task5UploadIngressArchitectureTest.java \
        backend/src/test/java/com/yoyuzh/files/upload
git commit -m "refactor: narrow upload to ingress-only ownership"
```

## Task 2: Reconcile Sharing And Search As First-Class Modules

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/sharing/**`
- Modify: `backend/src/main/java/com/yoyuzh/files/search/**`
- Modify: `backend/src/main/java/com/yoyuzh/files/core/FileController.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/core/FileService.java`
- Test: `backend/src/test/java/com/yoyuzh/architecture/Task6SharingSearchArchitectureTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/search/FileSearchServiceTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/sharing/internal/web/ShareV2ControllerIntegrationTest.java`

- [x] **Step 1: Run sharing/search tranche verification first**

Run:

```bash
cd backend && mvn test -Dtest=Task6SharingSearchArchitectureTest,FileSearchServiceTest,FileSearchV2ControllerTest,ShareV2ControllerIntegrationTest -q
```

Expected:

- PASS if `files.share` / `files.events` root 已完全退出
- FAIL only on sharing/search module-boundary regressions

- [x] **Step 2: Keep sharing/search entrypoints behind module APIs**

Target code shape:

```java
public class FileSearchService {

    private final FileSearchApi fileSearchApi;

    public PageResponse<FileMetadataResponse> search(Long userId, SearchFilesQuery query) {
        return fileSearchApi.search(userId, query);
    }
}
```

```java
public class ShareV2Controller {

    private final SharingApi sharingApi;
}
```

- [x] **Step 3: Remove surviving legacy-root dependencies**

Hard bans:

```java
noClasses()
    .that()
    .resideOutsideOfPackages("com.yoyuzh.files.sharing..", "com.yoyuzh.files.search..", "com.yoyuzh.files.core..", "com.yoyuzh.boot..")
    .should()
    .dependOnClassesThat()
    .resideInAnyPackage("com.yoyuzh.files.share..", "com.yoyuzh.files.events..");
```

- [x] **Step 4: Re-run sharing/search tranche verification**

Run:

```bash
cd backend && mvn test -Dtest=Task6SharingSearchArchitectureTest,FileSearchServiceTest,FileSearchV2ControllerTest,ShareV2ControllerIntegrationTest -q
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/sharing \
        backend/src/main/java/com/yoyuzh/files/search \
        backend/src/test/java/com/yoyuzh/architecture/Task6SharingSearchArchitectureTest.java \
        backend/src/test/java/com/yoyuzh/files/search \
        backend/src/test/java/com/yoyuzh/files/sharing
git commit -m "refactor: route sharing and search through target module seams"
```

## Task 3: Reconcile Transfer Layering

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/transfer/**`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspacePathPolicy.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/content/api/ContentRegistrationApi.java`
- Test: `backend/src/test/java/com/yoyuzh/architecture/Task7TransferArchitectureTest.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/TransferServiceTest.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/application/RuntimeTransferImportApiTest.java`

- [x] **Step 1: Run transfer tranche verification first**

Run:

```bash
cd backend && mvn test -Dtest=Task7TransferArchitectureTest,TransferServiceTest,TransferControllerIntegrationTest,RuntimeTransferImportApiTest -q
```

Expected:

- transfer 仅经 `transfer.api` 暴露
- import 只走 `WorkspacePathPolicy` 和 `ContentRegistrationApi`

- [x] **Step 2: Keep transfer import behind workspace/content seams**

Target code shape:

```java
RegisteredContentFile storedFile = contentRegistrationApi.registerBlob(new ContentRegistrationCommand(
        recipient.getId(),
        normalizedPath,
        normalizedFilename,
        readyFile.contentType(),
        readyFile.size(),
        new ContentBlobReference(blob.getObjectKey(), blob.getContentType(), blob.getSize())
));
```

- [x] **Step 3: Prevent transfer from re-owning file/share logic**

Hard bans:

```java
noClasses()
    .that()
    .resideInAnyPackage("com.yoyuzh.transfer..")
    .should()
    .dependOnClassesThat()
    .resideInAnyPackage("com.yoyuzh.files.share..", "com.yoyuzh.files.workspace.internal..");
```

- [x] **Step 4: Re-run transfer tranche verification**

Run:

```bash
cd backend && mvn test -Dtest=Task7TransferArchitectureTest,TransferServiceTest,TransferControllerIntegrationTest,RuntimeTransferImportApiTest -q
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/transfer \
        backend/src/test/java/com/yoyuzh/architecture/Task7TransferArchitectureTest.java \
        backend/src/test/java/com/yoyuzh/transfer
git commit -m "refactor: layer transfer behind target module contracts"
```

## Task 4: Reconcile Ops.Admin To Governance-Only APIs

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/**`
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/api/**`
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/api/**`
- Modify: `backend/src/main/java/com/yoyuzh/platform/job/api/**`
- Test: `backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java`
- Test: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminControllerIntegrationTest.java`

- [x] **Step 1: Run ops.admin tranche verification first**

Run:

```bash
cd backend && mvn test -Dtest=Task8OpsAdminArchitectureTest,AdminControllerIntegrationTest,AdminMutableSettingsServiceTest,AdminUserGovernanceServiceTest -q
```

Expected:

- admin controllers 只经 `ops.admin.api`
- application 只经其他模块 `api`

- [x] **Step 2: Keep admin orchestration on APIs only**

Target code shape:

```java
public final class AdminStorageGovernanceService {

    private final StoragePolicyAdminApi storagePolicyAdminApi;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    private final AdminAuditService adminAuditService;
}
```

- [x] **Step 3: Remove remaining direct bypasses**

Hard bans:

```java
noClasses()
    .that()
    .resideInAnyPackage("com.yoyuzh.ops.admin.internal.application..")
    .should()
    .dependOnClassesThat()
    .resideInAnyPackage("com.yoyuzh.auth..", "com.yoyuzh.files.core..", "com.yoyuzh.platform.storage.internal..");
```

- [x] **Step 4: Re-run ops.admin tranche verification**

Run:

```bash
cd backend && mvn test -Dtest=Task8OpsAdminArchitectureTest,AdminControllerIntegrationTest,AdminMutableSettingsServiceTest,AdminUserGovernanceServiceTest -q
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin \
        backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java \
        backend/src/test/java/com/yoyuzh/ops/admin
git commit -m "refactor: tighten ops admin to governance-only seams"
```

## Task 5: Final Boot/Shared/Infra Cutover And Legacy Root Audit

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/boot/**`
- Modify: `backend/src/main/java/com/yoyuzh/shared/kernel/**`
- Modify: `backend/src/main/java/com/yoyuzh/infra/**`
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/**`
- Modify: `backend/src/main/java/com/yoyuzh/platform/job/**`
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/**`
- Test: `backend/src/test/java/com/yoyuzh/architecture/Task9BootSharedInfraArchitectureTest.java`
- Test: `backend-next/src/test/java/com/yoyuzh/architecture/**`

- [x] **Step 1: Run live backend full regression**

Run:

```bash
cd backend && mvn test -q
```

Expected: PASS

- [x] **Step 2: Run backend-next gate tests**

Run:

```bash
cd backend-next && mvn test -q
```

Expected: PASS

- [x] **Step 3: Audit surviving legacy roots**

Run:

```bash
cd backend && rg -n "package com\\.yoyuzh\\.(auth|files\\.core|files\\.policy|files\\.tasks|admin|config|common)\\b" src/main/java
```

Expected:

- 只剩明确兼容壳，不能再有新的业务真相

- [x] **Step 4: Remove or freeze remaining compatibility shells**

Target decision rule:

```java
// Compatibility shell allowed only when:
// 1. public route must stay stable
// 2. real rule ownership already lives in target module api/internal
// 3. shell contains orchestration only, no business truth
```

Audit result:

- `backend` 全量回归已通过，`backend-next` gate 也已通过
- `com.yoyuzh.auth..` 与 `com.yoyuzh.files.core..` 仍存在物理包路径
- 当前 cutover 判定口径是“真实规则所有权已迁到目标模块，遗留根包只保留兼容壳”，不是“包路径必须在本轮物理清零”

- [x] **Step 5: Run final cutover verification**

Run:

```bash
cd backend && mvn test -q
cd ../backend-next && mvn test -q
```

Expected: PASS / PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/boot \
        backend/src/main/java/com/yoyuzh/shared \
        backend/src/main/java/com/yoyuzh/infra \
        backend/src/test/java/com/yoyuzh/architecture/Task9BootSharedInfraArchitectureTest.java \
        backend-next/src/test/java/com/yoyuzh/architecture
git commit -m "refactor: complete backend-next final cutover"
```

## Self-Review

- **Spec coverage:** 这份计划只覆盖剩余 Task 5-9 与最终 cutover，不重复已经完成并提交的 admin-storage/content seam tranche。
- **Placeholder scan:** 没有 `TODO/TBD/similar to` 占位词；每个任务都有明确命令和代码形状。
- **Type consistency:** 当前 plan 统一使用 `ContentBlobReference`、`StorageUploadMode`、`AdminStoragePolicyUpsertInput`、`TransferSessionApi` 等当前仓库已存在或已落定的命名，没有再引入另一套别名。
