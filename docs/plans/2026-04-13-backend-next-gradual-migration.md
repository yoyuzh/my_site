# Backend-Next Gradual Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gradually migrate the current backend from `backend/src/main/java/com/yoyuzh/{auth,files,admin,config,common,transfer,api}` to the target modular architecture defined by `backend-next/archtecture.md` without breaking existing HTTP routes or deployability.

**Architecture:** Use `backend-next/archtecture.md` as the target package map, but migrate inside the live `backend/` runtime first. Keep controllers and public routes stable, introduce new module APIs and internal layers beside the old packages, move rule ownership inward, then delete compatibility shells only after runtime tests and architecture gates are both green.

**Tech Stack:** Java 17, Spring Boot 3.3.8, Maven, JUnit 5, ArchUnit, Spring MVC, Spring Security, Spring Data JPA, Redis, existing `backend/` tests, `backend-next/` architecture-gate tests.

**backend-next positioning:** `backend-next/` only contains architecture gates, package markers, and enforcement tests; it is not a second runtime implementation.

---

## Legacy To Target Mapping

| Legacy package / area | Target module |
| --- | --- |
| `com.yoyuzh.auth` | `com.yoyuzh.identity.access` |
| `com.yoyuzh.files.core` | `com.yoyuzh.files.workspace` + `com.yoyuzh.files.content` |
| `com.yoyuzh.files.upload` | `com.yoyuzh.files.upload` |
| `com.yoyuzh.files.share` + `com.yoyuzh.api.v2.shares` | `com.yoyuzh.files.sharing` |
| `com.yoyuzh.files.search` + `FileSearchV2Controller` | `com.yoyuzh.files.search` |
| `com.yoyuzh.files.policy` | `com.yoyuzh.platform.storage` |
| `com.yoyuzh.files.tasks` + task v2 controllers | `com.yoyuzh.platform.job` |
| `com.yoyuzh.transfer` | `com.yoyuzh.transfer` |
| `com.yoyuzh.admin` | `com.yoyuzh.ops.admin` |
| `com.yoyuzh.config` | `com.yoyuzh.boot` + `com.yoyuzh.app.android` |
| `com.yoyuzh.common` | `com.yoyuzh.shared.kernel` + `com.yoyuzh.infra` |

## Migration Invariants

- Keep all existing public backend routes unchanged until final cleanup.
- Do not let any module depend directly on another module's `internal`.
- Do not move upload finalization truth out of `files.workspace` and `files.content`.
- Do not let `ops.admin` call core repositories directly.
- Prefer creating new target modules inside `backend/src/main/java/com/yoyuzh/...` first.
- Every phase must end with real repo-backed verification.

## Phase Release Gate

- all legacy routes unchanged
- backend integration tests green
- architecture tests green
- no new direct dependency on forbidden legacy roots
- application boots locally against real profile

## Task 1: Strengthen Migration Guardrails

**Files:**
- `backend-next/src/test/java/com/yoyuzh/architecture/BackendNextArchitectureRulesTest.java`
- `backend-next/src/test/java/com/yoyuzh/architecture/BackendLegacyToTargetMappingTest.java`
- `backend-next/src/test/java/com/yoyuzh/architecture/BackendPackageLayeringRuleTest.java`

**Completion definition:**
- mapping and layering rules exist and are enforced
- web-to-repository direct dependency checks are executable
- `backend-next` remains gate-only
- architecture tests are green

**Prohibitions:**
- do not add runtime implementation code to `backend-next`
- do not weaken ArchUnit rules for convenience
- do not hide gaps with permissive empty-rule settings

**Verification:** `cd backend-next && mvn test -Dtest=BackendLegacyToTargetMappingTest,BackendPackageLayeringRuleTest`

## Task 2: Establish Identity.Access As The First Migrated Module

**Files:**
- new `backend/src/main/java/com/yoyuzh/identity/access/**`
- `backend/src/main/java/com/yoyuzh/auth/AuthService.java`
- `backend/src/main/java/com/yoyuzh/auth/AuthController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminAccessEvaluator.java`

**Completion definition:**
- old auth controllers remain stable
- new identity APIs are introduced and used by legacy entrypoints
- account/session rules are owned by `identity.access`
- legacy `AuthService` only keeps orchestration and compatibility responsibility
- no new code reaches `identity.access.internal` from outside the module
- related tests are green

**Prohibitions:**
- do not keep new session rules in controllers
- do not let `AdminAccessEvaluator` keep owning identity truth
- do not expose other modules to `identity.access.internal`

**Verification:** `cd backend && mvn test -Dtest=RegisterHandlerTest,SessionExclusivityPolicyTest,AuthServiceTest,AdminAccessEvaluatorTest`

## Task 3: Extract Platform.Storage And Platform.Job Before File Splits

**Files:**
- new `backend/src/main/java/com/yoyuzh/platform/storage/**`
- new `backend/src/main/java/com/yoyuzh/platform/job/**`
- `backend/src/main/java/com/yoyuzh/files/policy/StoragePolicyService.java`
- `backend/src/main/java/com/yoyuzh/files/tasks/BackgroundTaskCommandService.java`
- `backend/src/main/java/com/yoyuzh/api/v2/tasks/BackgroundTaskV2Controller.java`

**Completion definition:**
- storage capability and upload-mode decisions are callable through `platform.storage.api`
- task scheduling and retry decisions are callable through `platform.job.api`
- legacy services delegate instead of owning the new policy truth
- later file-module migration no longer needs fresh dependencies on legacy policy/task internals
- related tests are green

**Prohibitions:**
- do not leave upload-mode truth in legacy upload/file services
- do not leave retry rules spread across controllers, workers, and services
- do not create new direct dependencies on `files.policy` or `files.tasks` internals

**Verification:** `cd backend && mvn test -Dtest=UploadModePolicyTest,AsyncJobRetryPolicyTest,StoragePolicyServiceTest,BackgroundTaskServiceTest,BackgroundTaskV2ControllerIntegrationTest`

## Task 4A: Introduce Workspace/Content APIs And Compatibility Bridge

**Files:**
- new `backend/src/main/java/com/yoyuzh/files/workspace/api/**`
- new `backend/src/main/java/com/yoyuzh/files/content/api/**`
- `backend/src/main/java/com/yoyuzh/files/core/FileService.java`

**Completion definition:**
- workspace/content APIs exist and form the new migration seam
- old controllers remain stable
- old `FileService` starts delegating through the new APIs
- no new code introduces fresh dependencies on `files.core` internals from outside compatibility code
- related tests are green

**Prohibitions:**
- do not move full rule ownership in this task
- do not introduce controller-to-repository shortcuts
- do not let new callers depend on `files.workspace.internal` or `files.content.internal`

**Verification:** `cd backend && mvn test -Dtest=FileServiceTest`

## Task 4B: Move Workspace Rules And Lifecycle Ownership

**Files:**
- new `backend/src/main/java/com/yoyuzh/files/workspace/internal/**`
- `backend/src/main/java/com/yoyuzh/files/core/WorkspaceNodeRulesService.java`
- `backend/src/main/java/com/yoyuzh/files/core/FileService.java`

**Completion definition:**
- workspace path legality, move/copy lifecycle, and recycle semantics are owned by `files.workspace`
- old controllers remain stable
- `WorkspaceNodeRulesService` is reduced toward compatibility and delegation
- no new code writes workspace rule truth in `files.core`
- related tests are green

**Prohibitions:**
- do not keep same-parent naming and subtree move rules in `FileService`
- do not put workspace rule ownership in controllers or admin services
- do not let upload own workspace final node semantics

**Verification:** `cd backend && mvn test -Dtest=WorkspacePathPolicyTest,WorkspaceNodeRulesServiceTest,FileServiceTest`

## Task 4C: Move Content Asset/Version Ownership And Shrink FileService

**Files:**
- new `backend/src/main/java/com/yoyuzh/files/content/internal/**`
- `backend/src/main/java/com/yoyuzh/files/core/ContentAssetBindingService.java`
- `backend/src/main/java/com/yoyuzh/files/core/FileService.java`

**Completion definition:**
- content asset, version immutability, and binding truth are owned by `files.content`
- old controllers remain stable
- `FileService` is reduced to orchestration and compatibility glue
- no new code writes content asset/version truth in `files.core`
- related tests are green

**Prohibitions:**
- do not leave content version immutability in old binding services
- do not let workspace or upload modules own content asset truth
- do not introduce new direct dependencies on legacy `files.core` internals for content registration

**Verification:** `cd backend && mvn test -Dtest=ContentVersionImmutabilityPolicyTest,ContentAssetBindingServiceTest,FileServiceTest`

## Task 5: Rebuild Upload As Ingress Only

**Files:**
- new `backend/src/main/java/com/yoyuzh/files/upload/api/**`
- new `backend/src/main/java/com/yoyuzh/files/upload/internal/**`
- `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
- `backend/src/main/java/com/yoyuzh/api/v2/files/UploadSessionV2Controller.java`
- `backend/src/main/java/com/yoyuzh/files/core/FileController.java`

**Completion definition:**
- old controllers and routes remain stable
- upload completion is routed through workspace/content APIs
- upload owns session/process control only
- no new code in upload directly owns final node creation or final content registration
- related tests are green

**Prohibitions:**
- prohibit new formal node creation logic from residing inside `files.upload`
- prohibit upload from directly writing workspace/content repository implementations
- prohibit controller code from directly completing finalization ownership

**Verification:** `cd backend && mvn test -Dtest=CompleteUploadHandlerTest,UploadSessionV2ControllerTest,UploadSessionServiceTest,FileServiceUploadStorageNameTest`

## Task 6: Move Sharing And Search Behind Explicit APIs

**Files:**
- new `backend/src/main/java/com/yoyuzh/files/sharing/**`
- new `backend/src/main/java/com/yoyuzh/files/search/**`
- `backend/src/main/java/com/yoyuzh/files/share/ShareV2Service.java`
- `backend/src/main/java/com/yoyuzh/api/v2/shares/ShareV2Controller.java`
- `backend/src/main/java/com/yoyuzh/files/search/FileSearchService.java`
- `backend/src/main/java/com/yoyuzh/api/v2/files/FileSearchV2Controller.java`

**Completion definition:**
- sharing and search are reachable through explicit module APIs
- old controllers remain stable
- share policy truth lives in `files.sharing`
- search orchestration no longer depends on hidden repository bypasses
- related tests are green

**Prohibitions:**
- do not let search query other modules' internal tables directly as new code
- do not leave share expiry/password/download/import truth scattered across controllers and services
- do not add new dependencies on legacy `files.share` internals from outside compatibility code

**Verification:** `cd backend && mvn test -Dtest=ShareAccessPolicyTest,SearchFilesHandlerTest,ShareV2ControllerIntegrationTest,FileSearchServiceTest,FileSearchV2ControllerTest`

## Task 7: Layer Transfer And Stop Cross-Domain Leakage

**Files:**
- new `backend/src/main/java/com/yoyuzh/transfer/api/**`
- new `backend/src/main/java/com/yoyuzh/transfer/internal/**`
- `backend/src/main/java/com/yoyuzh/transfer/TransferService.java`
- `backend/src/main/java/com/yoyuzh/transfer/TransferImportService.java`
- `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`

**Completion definition:**
- transfer receive/import policy is owned by `transfer` domain code
- old transfer routes remain stable
- transfer import uses workspace/content APIs instead of bypassing ownership
- no new transfer code depends on sharing/workspace internals directly
- related tests are green

**Prohibitions:**
- do not let transfer import create workspace/content truth through direct repositories
- do not move share semantics into transfer
- do not let controllers own transfer receive eligibility rules

**Verification:** `cd backend && mvn test -Dtest=TransferReceivePolicyTest,ImportTransferHandlerTest,TransferServiceTest,TransferControllerIntegrationTest`

## Task 8: Rewire Ops.Admin To Governance-Only APIs

**Files:**
- new `backend/src/main/java/com/yoyuzh/ops/admin/**`
- `backend/src/main/java/com/yoyuzh/admin/AdminMutableSettingsService.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminUserGovernanceService.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminResourceGovernanceService.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminSettingsController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminUserController.java`
- `backend/src/main/java/com/yoyuzh/admin/AdminResourceController.java`

**Completion definition:**
- admin controllers remain stable
- admin orchestration goes through explicit module APIs
- read-only sections remain preserved where required
- no new admin code directly depends on core repositories or core `internal` packages
- related tests are green

**Prohibitions:**
- do not let `ops.admin` directly mutate workspace/content/sharing/transfer internals
- do not let admin services become the new owner of identity, storage, or transfer rules
- do not add direct repository bypasses for convenience

**Verification:** `cd backend && mvn test -Dtest=UpdateRuntimeSettingsHandlerTest,ManageUserGovernanceHandlerTest,AdminMutableSettingsServiceTest,AdminUserGovernanceServiceTest,AdminControllerIntegrationTest`

## Task 9: Move Boot, Shared, Infra, Then Delete Compatibility Shells

**Files:**
- new `backend/src/main/java/com/yoyuzh/boot/**`
- new `backend/src/main/java/com/yoyuzh/shared/kernel/**`
- new `backend/src/main/java/com/yoyuzh/infra/**`
- `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`
- `backend/src/main/java/com/yoyuzh/common/**`
- `backend-next/src/test/java/com/yoyuzh/architecture/BackendLegacyToTargetMappingTest.java`

**Completion definition:**
- boot/shared/infra responsibilities are separated according to target architecture
- legacy ownership roots no longer carry active business truth
- backend still boots and tests are green
- `backend-next` gate tests are green
- only then are compatibility shells eligible for deletion

**Prohibitions:**
- do not dump business-specific classes into `shared.kernel`
- do not move module-specific repository abstractions into global `infra`
- do not delete compatibility shells before runtime and architecture gates are both green

**Verification:** `cd backend && mvn test` and `cd ../backend-next && mvn test`

## Final Cutover Checklist

After Task 9 is green, perform this review before deleting legacy shells:

1. All existing `/api/auth/**`, `/api/user/**`, `/api/files/**`, `/api/v2/files/**`, `/api/v2/shares/**`, `/api/transfer/**`, `/api/v2/tasks/**`, and `/api/admin/**` contracts still match `backend-next/api-reference.md`, with `docs/api-reference.md` used only for remaining legacy-runtime detail checks.
2. No new code outside compatibility adapters depends on `com.yoyuzh.auth`, `com.yoyuzh.files.core`, `com.yoyuzh.files.policy`, `com.yoyuzh.files.tasks`, or `com.yoyuzh.admin`.
3. `backend/src/test` is green.
4. `backend-next/src/test` is green.
5. Local application boot against the real profile is green.
6. Only then schedule removal tasks for the legacy compatibility packages.
