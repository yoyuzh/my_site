# Backend Migration Batch 1 Low-Risk Tranche Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有 HTTP 路由和运行行为的前提下，先完成第一批低风险高收益迁移：补上架构守卫，消除 `ops.admin` 的 `application -> web DTO` 依赖，以及消除 `platform.storage.api -> files.upload` 的契约泄漏。

**Target Architecture Baseline:** 本计划的唯一目标架构以 `backend-next/` 文档组为准，而不是以当前 `backend/` 里的历史实现形态为准。执行时必须同时服从以下文档：

- `backend-next/archtecture.md`
- `backend-next/api-reference.md`
- `docs/backend-next/module-dependency-whitelist.md`
- `docs/backend-next/directory-responsibilities.md`
- `docs/backend-next/rule-ownership-matrix.md`

**Architecture:** 本批次只处理“错误接缝”，不触碰 `files.core`、`files.storage`、`transfer` 的大规模拆分，也不改数据库表结构。实现方式是先用 ArchUnit 把当前两类违规固化成失败用例，再把跨层 DTO 和跨上下文 enum 收口到各自模块拥有的 `application/api` 契约，确保依赖方向回到 `backend-next` 规定的模块边界，最后用现有单测、集成测和架构测试锁住结果。

**Tech Stack:** Java 17, Spring Boot 3.3.8, Maven, JUnit 5, Mockito, ArchUnit, Spring MVC, Spring Security.

---

## Scope Check

这个仓库当前的架构迁移包含多个相互独立的子系统，不能一次写成一个可直接执行的大计划。这个文件只覆盖第一批可执行子计划：

- `ops.admin`: 去掉 `AdminStorageGovernanceService` 对 `internal.web` request DTO 的直接依赖
- `platform.storage`: 去掉 `platform.storage.api` 对 `files.upload.UploadSessionUploadMode` 的直接依赖
- `architecture`: 为以上两条新增回归守卫

本批次的边界判定，以 `backend-next` 目标模块关系为准：

- `ops.admin` 只能经其他上下文的 `api` 协作，不能让 `application` 反向依赖自己的 `internal.web`
- `platform.storage.api` 必须由 `platform.storage` 自己拥有，不得泄漏到 `files.upload` 的类型
- 本批次即使保留 upload 侧兼容适配，也只能把适配留在 `files.upload` 自己内部，不能把旧类型继续放在平台契约上

后续必须拆成独立计划再执行的上下文：

- `files.search`
- `files.upload`
- `files.workspace`
- `files.content`
- `identity.access`
- `files.sharing`
- `transfer`
- `platform.job`
- 横切遗留 `files.core` / `files.storage`

## File Structure

### New files

- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStoragePolicyUpsertInput.java`
  - `ops.admin` application 层输入对象，替代 web request 进入 application
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStoragePolicyMigrationInput.java`
  - 迁移任务创建输入对象，替代 web request 进入 application
- `backend/src/main/java/com/yoyuzh/platform/storage/api/StorageUploadMode.java`
  - `platform.storage` 自己拥有的上传模式契约

### Modified files

- `backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java`
  - 增加 `ops.admin.application` 不得依赖 `ops.admin.internal.web` 的守卫
- `backend/src/test/java/com/yoyuzh/architecture/Task3PlatformSeamArchitectureTest.java`
  - 增加 `platform.storage.api` 不得依赖 `files.upload..` 的守卫
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceService.java`
  - 改用 application 输入对象
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminStoragePolicyController.java`
  - 负责 request -> application input 的映射
- `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceServiceTest.java`
  - 以 application 输入对象为入口验证行为
- `backend/src/main/java/com/yoyuzh/platform/storage/api/UploadModePolicy.java`
  - 返回 `StorageUploadMode`
- `backend/src/main/java/com/yoyuzh/platform/storage/internal/application/RuntimeUploadModePolicy.java`
  - 生产 `StorageUploadMode`
- `backend/src/main/java/com/yoyuzh/files/upload/UploadPolicyResolver.java`
  - 在 upload 模块内部把 `StorageUploadMode` 适配为 `UploadSessionUploadMode`
- `backend/src/test/java/com/yoyuzh/platform/storage/internal/application/RuntimeUploadModePolicyTest.java`
  - 校验平台上传模式契约
- `backend/src/test/java/com/yoyuzh/files/upload/UploadPolicyResolverTest.java`
  - 校验 upload 侧适配逻辑

### Existing files intentionally out of scope

- `backend/src/main/java/com/yoyuzh/files/core/FileService.java`
- `backend/src/main/java/com/yoyuzh/files/storage/*.java`
- `backend/src/main/java/com/yoyuzh/transfer/*.java`
- `backend/src/main/java/com/yoyuzh/files/search/*.java`

本批次不碰这些文件，避免把“低风险接缝修正”升级成“高风险上下文拆分”。

## Task 1: Add Batch-1 Architecture Guards

**Files:**
- Modify: `backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/architecture/Task3PlatformSeamArchitectureTest.java`

- [x] **Step 1: Write the failing architecture rules**

```java
@Test
void adminStorageGovernanceServiceMustNotDependOnAdminWebDtos() {
    ArchRule rule = noClasses()
            .that()
            .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.yoyuzh.ops.admin.internal.web..");

    rule.check(classes);
}
```

```java
ArchRule storageApiMustNotDependOnUploadInternals = noClasses()
        .that()
        .resideInAnyPackage("com.yoyuzh.platform.storage.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.yoyuzh.files.upload..");
```

- [x] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && mvn test -Dtest=Task3PlatformSeamArchitectureTest,Task8OpsAdminArchitectureTest
```

Expected:

- `Task8OpsAdminArchitectureTest` fails because `AdminStorageGovernanceService` still imports `AdminStoragePolicyUpsertRequest` and `AdminStoragePolicyMigrationCreateRequest`
- `Task3PlatformSeamArchitectureTest` fails because `platform.storage.api.UploadModePolicy` still imports `com.yoyuzh.files.upload.UploadSessionUploadMode`

- [x] **Step 3: Keep the new rules in place without weakening existing checks**

```java
ArchRule uploadPoliciesNoUploadPackageLeakRule = noClasses()
        .that()
        .resideInAnyPackage("com.yoyuzh.platform.storage.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.yoyuzh.files.upload..");

ArchRule adminStorageGovernanceNoWebDtoRule = noClasses()
        .that()
        .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.yoyuzh.ops.admin.internal.web..");
```

- [x] **Step 4: Re-run the architecture tests after Tasks 2 and 3 are complete**

Run:

```bash
cd backend && mvn test -Dtest=Task3PlatformSeamArchitectureTest,Task8OpsAdminArchitectureTest
```

Expected: PASS

- [x] **Step 4.1: Check the result against backend-next rules, not only against green tests**

Acceptance focus:

- `ops.admin.internal.application` 不再依赖 `ops.admin.internal.web`
- `platform.storage.api` 不再依赖 `files.upload..`
- 若仍需兼容旧 upload 枚举，适配代码必须留在 `files.upload` 内部，而不是回流到 `platform.storage`

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/yoyuzh/architecture/Task3PlatformSeamArchitectureTest.java \
        backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java
git commit -m "test: add low-risk migration architecture guards"
```

## Task 2: Decouple Ops.Admin Application From Web DTOs

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStoragePolicyUpsertInput.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStoragePolicyMigrationInput.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminStoragePolicyController.java`
- Modify: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceServiceTest.java`

- [x] **Step 1: Write the failing unit test against application-owned input types**

```java
AdminStoragePolicyResponse response = adminStorageGovernanceService.createStoragePolicy(
        new AdminStoragePolicyUpsertInput(
                " Archive Bucket ",
                StoragePolicyType.S3_COMPATIBLE,
                "archive-bucket",
                "https://s3.example.com",
                "auto",
                true,
                "archive/",
                StoragePolicyCredentialMode.STATIC,
                20_480L,
                defaultCapabilities(20_480L),
                true
        )
);
```

```java
BackgroundTaskView task = adminStorageGovernanceService.createStoragePolicyMigrationTask(
        userId,
        new AdminStoragePolicyMigrationInput(3L, 4L, "migration-1")
);
```

- [x] **Step 2: Run the unit test to verify it fails**

Run:

```bash
cd backend && mvn test -Dtest=AdminStorageGovernanceServiceTest
```

Expected:

- compile failure or test failure with `cannot find symbol` for `AdminStoragePolicyUpsertInput`
- compile failure or test failure with `cannot find symbol` for `AdminStoragePolicyMigrationInput`

- [x] **Step 3: Create application input records and switch service signatures**

```java
package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyType;

public record AdminStoragePolicyUpsertInput(
        String name,
        StoragePolicyType type,
        String bucketName,
        String endpoint,
        String region,
        boolean privateBucket,
        String prefix,
        StoragePolicyCredentialMode credentialMode,
        long maxSizeBytes,
        StoragePolicyCapabilities capabilities,
        boolean enabled
) {
}
```

```java
package com.yoyuzh.ops.admin.internal.application;

public record AdminStoragePolicyMigrationInput(
        Long sourcePolicyId,
        Long targetPolicyId,
        String correlationId
) {
}
```

```java
public AdminStoragePolicyResponse createStoragePolicy(AdminStoragePolicyUpsertInput input) { ... }

public AdminStoragePolicyResponse updateStoragePolicy(Long policyId, AdminStoragePolicyUpsertInput input) { ... }

public BackgroundTaskView createStoragePolicyMigrationTask(Long userId, AdminStoragePolicyMigrationInput input) { ... }
```

- [x] **Step 4: Update the controller to do request-to-input mapping only**

```java
@PostMapping("/storage-policies")
public ApiResponse<AdminStoragePolicyResponse> createStoragePolicy(
        @Valid @RequestBody AdminStoragePolicyUpsertRequest request) {
    return ApiResponse.success(adminStorageGovernanceService.createStoragePolicy(toInput(request)));
}

@PostMapping("/storage-policies/migrations")
public ApiResponse<BackgroundTaskResponse> createStoragePolicyMigrationTask(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @RequestBody AdminStoragePolicyMigrationCreateRequest request) {
    Long userId = userDetailsService.loadDomainUser(userDetails.getUsername()).getId();
    return ApiResponse.success(toTaskResponse(
            adminStorageGovernanceService.createStoragePolicyMigrationTask(userId, toInput(request))
    ));
}
```

```java
private AdminStoragePolicyUpsertInput toInput(AdminStoragePolicyUpsertRequest request) {
    return new AdminStoragePolicyUpsertInput(
            request.name(),
            request.type(),
            request.bucketName(),
            request.endpoint(),
            request.region(),
            request.privateBucket(),
            request.prefix(),
            request.credentialMode(),
            request.maxSizeBytes(),
            request.capabilities(),
            request.enabled()
    );
}

private AdminStoragePolicyMigrationInput toInput(AdminStoragePolicyMigrationCreateRequest request) {
    return new AdminStoragePolicyMigrationInput(
            request.sourcePolicyId(),
            request.targetPolicyId(),
            request.correlationId()
    );
}
```

- [x] **Step 5: Run service and controller verification**

Run:

```bash
cd backend && mvn test -Dtest=AdminStorageGovernanceServiceTest,AdminControllerIntegrationTest,Task8OpsAdminArchitectureTest
```

Expected:

- `AdminStorageGovernanceServiceTest` passes with application-owned input records
- `AdminControllerIntegrationTest` still passes because HTTP contract and controller behavior remain unchanged
- `Task8OpsAdminArchitectureTest` passes with the new no-`application -> web` rule

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStoragePolicyUpsertInput.java \
        backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStoragePolicyMigrationInput.java \
        backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceService.java \
        backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminStoragePolicyController.java \
        backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceServiceTest.java \
        backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java
git commit -m "refactor: decouple admin storage governance from web dto inputs"
```

## Task 3: Seal Platform.Storage Upload Mode Contract

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/platform/storage/api/StorageUploadMode.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/api/UploadModePolicy.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/internal/application/RuntimeUploadModePolicy.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadPolicyResolver.java`
- Modify: `backend/src/test/java/com/yoyuzh/platform/storage/internal/application/RuntimeUploadModePolicyTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/upload/UploadPolicyResolverTest.java`

- [x] **Step 1: Write the failing tests against a platform-owned upload mode**

```java
StorageUploadMode uploadMode = policy.resolveUploadMode(new StoragePolicyCapabilities(
        true,
        true,
        true,
        true,
        false,
        true,
        true,
        false,
        1024L
));

assertThat(uploadMode).isEqualTo(StorageUploadMode.DIRECT_MULTIPART);
```

```java
when(uploadModePolicy.resolveUploadMode(any())).thenReturn(StorageUploadMode.DIRECT_SINGLE);

UploadSessionUploadMode uploadMode = uploadPolicyResolver.resolveUploadMode(capabilities);

assertThat(uploadMode).isEqualTo(UploadSessionUploadMode.DIRECT_SINGLE);
```

- [x] **Step 2: Run the tests to verify they fail**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeUploadModePolicyTest,UploadPolicyResolverTest
```

Expected:

- compile failure or test failure because `StorageUploadMode` does not exist yet
- compile failure or test failure because `UploadModePolicy` still returns `UploadSessionUploadMode`

- [x] **Step 3: Create the platform-owned enum and update the policy API**

```java
package com.yoyuzh.platform.storage.api;

public enum StorageUploadMode {
    PROXY,
    DIRECT_SINGLE,
    DIRECT_MULTIPART
}
```

```java
package com.yoyuzh.platform.storage.api;

public interface UploadModePolicy {

    StorageUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities);
}
```

```java
@Service
public class RuntimeUploadModePolicy implements UploadModePolicy {

    @Override
    public StorageUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
        if (!capabilities.directUpload()) {
            return StorageUploadMode.PROXY;
        }
        if (capabilities.multipartUpload()) {
            return StorageUploadMode.DIRECT_MULTIPART;
        }
        return StorageUploadMode.DIRECT_SINGLE;
    }
}
```

- [x] **Step 4: Keep the compatibility adapter inside files.upload**

```java
public UploadSessionUploadMode resolveUploadMode(StoragePolicyCapabilities capabilities) {
    return toUploadSessionMode(uploadModePolicy.resolveUploadMode(capabilities));
}

private UploadSessionUploadMode toUploadSessionMode(StorageUploadMode mode) {
    return switch (mode) {
        case PROXY -> UploadSessionUploadMode.PROXY;
        case DIRECT_SINGLE -> UploadSessionUploadMode.DIRECT_SINGLE;
        case DIRECT_MULTIPART -> UploadSessionUploadMode.DIRECT_MULTIPART;
    };
}
```

这一步之后：

- `platform.storage.api` 不再依赖 `files.upload..`
- `files.upload` 仍然可以保持现有 `UploadSessionUploadMode`，减少本批次改动面

- [x] **Step 5: Run contract and compatibility verification**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeUploadModePolicyTest,UploadPolicyResolverTest,UploadSessionServiceTest,Task3PlatformSeamArchitectureTest
```

Expected:

- `RuntimeUploadModePolicyTest` passes with `StorageUploadMode`
- `UploadPolicyResolverTest` passes并证明 upload 侧适配仍返回 `UploadSessionUploadMode`
- `UploadSessionServiceTest` passes，说明上传主链路未受影响
- `Task3PlatformSeamArchitectureTest` passes with the new no-upload dependency rule

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/platform/storage/api/StorageUploadMode.java \
        backend/src/main/java/com/yoyuzh/platform/storage/api/UploadModePolicy.java \
        backend/src/main/java/com/yoyuzh/platform/storage/internal/application/RuntimeUploadModePolicy.java \
        backend/src/main/java/com/yoyuzh/files/upload/UploadPolicyResolver.java \
        backend/src/test/java/com/yoyuzh/platform/storage/internal/application/RuntimeUploadModePolicyTest.java \
        backend/src/test/java/com/yoyuzh/files/upload/UploadPolicyResolverTest.java \
        backend/src/test/java/com/yoyuzh/architecture/Task3PlatformSeamArchitectureTest.java
git commit -m "refactor: move upload mode contract into platform storage api"
```

## Final Verification Gate

- [x] **Step 1: Run the full batch-1 verification set**

Run:

```bash
cd backend && mvn test -Dtest=Task3PlatformSeamArchitectureTest,Task8OpsAdminArchitectureTest,AdminStorageGovernanceServiceTest,AdminControllerIntegrationTest,RuntimeUploadModePolicyTest,UploadPolicyResolverTest,UploadSessionServiceTest
```

Expected: PASS

- [x] **Step 2: Confirm the two batch goals in code review**

Checklist:

- `AdminStorageGovernanceService` no longer imports `com.yoyuzh.ops.admin.internal.web..`
- `com.yoyuzh.platform.storage.api..` no longer imports `com.yoyuzh.files.upload..`
- no HTTP route changed under `/api/admin/storage-policies/**`
- no upload session behavior changed at the controller/service boundary

## Follow-up Plans Required After This File

Batch 1 完成后，不要直接跳进 `files.core` 大拆分。下一步按下面顺序分别写独立计划并执行：

1. `files.search` 旧根包收口计划
2. `files.upload` web/application 与旧根包分离计划
3. `files.workspace` 路径/目录/生命周期 ownership 计划
4. `files.content` asset/registration 契约收口计划
5. `identity.access` api 去 `auth.*` 泄漏计划
6. `transfer` api 与旧核心拆分计划

## Self-Review

- **Spec coverage:** 本计划覆盖了当前已确认的第一批低风险高收益范围：`ops.admin` DTO 接缝、`platform.storage` 上传模式契约接缝，以及对应架构守卫；没有把 `files.search`、`transfer`、`files.core` 等高风险子系统错误混入本批次。
- **Placeholder scan:** 文档里没有 `TODO`、`TBD`、`similar to` 之类占位词；所有步骤都给了明确文件、代码片段和命令。
- **Type consistency:** `AdminStoragePolicyUpsertInput` / `AdminStoragePolicyMigrationInput` / `StorageUploadMode` 在所有步骤中的命名保持一致；`UploadPolicyResolver` 明确作为 `StorageUploadMode -> UploadSessionUploadMode` 的兼容适配层。
