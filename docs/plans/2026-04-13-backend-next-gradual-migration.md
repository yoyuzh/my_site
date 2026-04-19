# Backend-Next Gradual Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gradually migrate the current backend from `backend/src/main/java/com/yoyuzh/{auth,files,admin,config,common,transfer,api}` toward the modular destination map that already exists under `backend-next/src/main/java/com/yoyuzh`, without breaking existing HTTP routes or deployability.

**Architecture:** Use the current `backend-next/` folder structure plus `backend-next/archtecture.md` as the target package map, but migrate inside the live `backend/` runtime first. Keep controllers and public routes stable, materialize matching module APIs and internal layers in `backend/`, move rule ownership inward, then delete compatibility shells only after runtime tests and architecture gates are both green.

**Tech Stack:** Java 17, Spring Boot 3.3.8, Maven, JUnit 5, ArchUnit, Spring MVC, Spring Security, Spring Data JPA, Redis, existing `backend/` tests, `backend-next/` architecture-gate tests.

**backend-next positioning:** `backend-next/` already contains the target module/layer directory skeleton, AGENTS ownership hints, package markers, and enforcement tests; it is not a second runtime implementation.

## Existing Backend-Next Skeleton Snapshot

The current `backend-next/src/main/java/com/yoyuzh` tree is the migration destination map and should be treated as more concrete than this plan when naming target packages:

- top-level roots already exist for `boot`, `shared/kernel`, `infra/*`, `identity/access`, `files/{workspace,content,upload,sharing,search}`, `transfer`, `platform/{job,storage}`, `ops/admin`, and `app/android`
- business modules already expose the target split of `api` plus `internal/{application,domain,infra,web}` directories
- most of these directories currently contain ownership docs and marker classes rather than runtime code, which is intentional at this phase

**Execution rule:** if any later task wording conflicts with the existing `backend-next/src/main/java/com/yoyuzh/**` folder structure, the folder structure wins and the plan must be updated in the same turn.

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

For Tasks 2-9, every `new backend/src/main/java/com/yoyuzh/...` path below means: materialize the matching module and layer names that already exist under `backend-next/src/main/java/com/yoyuzh/...`; do not invent alternate roots or collapse layers for convenience.

## Migration Invariants

- Keep all existing public backend routes unchanged until final cleanup.
- Do not let any module depend directly on another module's `internal`.
- Do not move upload finalization truth out of `files.workspace` and `files.content`.
- Do not let `ops.admin` call core repositories directly.
- Treat the existing `backend-next/src/main/java/com/yoyuzh/**` directory tree as the destination contract for target module names and layer names.
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
- `backend-next/src/test/java/com/yoyuzh/architecture/BackendNextStructureTest.java`

**Completion definition:**
- mapping and layering rules exist and are enforced
- the current `backend-next` folder skeleton is explicitly covered by structure tests
- web-to-repository direct dependency checks are executable
- `backend-next` remains gate-only
- architecture tests are green

**Prohibitions:**
- do not add runtime implementation code to `backend-next`
- do not weaken ArchUnit rules for convenience
- do not hide gaps with permissive empty-rule settings

**Verification:** `cd backend-next && mvn test -Dtest=BackendNextStructureTest,BackendNextArchitectureRulesTest,BackendLegacyToTargetMappingTest,BackendPackageLayeringRuleTest`

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

**Current progress note:**
- the first live slice is already in place: admin capability evaluation now routes through `identity.access.api.AdminAccessPolicy`
- session rotation now routes through `identity.access.api.IdentitySessionPolicy`
- admin continuity protection now routes through `identity.access.api.AdminAccessContinuityGuard`
- registration admission now routes through `identity.access.api.RegistrationAdmissionPolicy`
- dev-login role assignment now routes through `identity.access.api.DevLoginRoleResolver`
- profile update uniqueness admission now routes through `identity.access.api.ProfileUpdateAdmissionPolicy`
- login admission and auth-failure translation now route through `identity.access.api.LoginAdmissionPolicy`
- fresh credential issuance, session rotation persistence, and refresh-token reissue orchestration now route through `identity.access.api.IdentityCredentialIssuer`
- password-change validation, global session invalidation, refresh-token revocation, and post-change credential reissue now route through `identity.access.api.PasswordChangePolicy`
- refresh-token issue/rotate/revoke ownership now routes through `identity.access.api.IdentityRefreshTokenManager`, while legacy `RefreshTokenService` acts as a compatibility shell
- global credential invalidation for password/admin account-state changes now routes through `identity.access.api.IdentityCredentialRevocationPolicy`
- legacy `AdminAccessEvaluator` and `AuthSessionPolicy` currently act as compatibility adapters while `AuthService` and `AdminUserGovernanceService` stay route-stable
- remaining Task 2 work should continue from these contracts instead of adding new identity rule logic back into `auth` or `admin`
- the next candidate work after Task 2 is to move on to Task 3 platform capability extraction, not to add new identity/security rules back into `auth`, `admin`, or `config`

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

**Current progress note:**
- upload-mode truth now routes through `platform.storage.api.UploadModePolicy`
- effective max-upload-size resolution now routes through `platform.storage.api.UploadConstraintPolicy`
- default storage-policy snapshot and default-policy id resolution now route through `platform.storage.api.StoragePolicyQuery`
- legacy `StoragePolicyService` now delegates upload-mode resolution instead of owning it
- legacy `StoragePolicyService` and `files.upload.UploadPolicyResolver` now delegate upload-size constraint resolution instead of owning it
- legacy `StoragePolicyService` now also acts as the compatibility shell for default-policy snapshot reads exposed through `platform.storage.api.StoragePolicyQuery`
- `UploadSessionService` now consumes upload-mode decisions through `StoragePolicyService.resolveUploadMode(...)`, so the live upload path already follows `platform.storage`
- `FileUploadRulesService` now consumes effective max-upload-size decisions through `StoragePolicyService.resolveEffectiveMaxUploadSize(...)`, so the live upload validation path already follows `platform.storage`
- `UploadSessionService`, `FileUploadRulesService`, and `FileService` now read default storage policy and capabilities through the shared default-policy snapshot entry instead of manually pairing `ensureDefaultPolicy()` with `readCapabilities(...)`
- `ContentAssetBindingService`, `FileEntityBackfillService`, and `AdminConfigSnapshotService` now consume default-policy snapshot/id reads through `platform.storage.api.StoragePolicyQuery`, so content/admin paths no longer need direct default-policy query ownership in `files.policy`
- async retry truth now routes through `platform.job.api.AsyncJobRetryPolicy`
- legacy `BackgroundTaskRetryPolicy` now acts as a compatibility shell over `platform.job.api`
- task command entrypoints now route through `platform.job.api.BackgroundTaskCommandGateway`
- legacy `BackgroundTaskCommandService` now acts as a compatibility shell over `platform.job.api`
- worker/startup-recovery execution entrypoints now route through `platform.job.api.BackgroundTaskExecutionGateway`
- legacy `BackgroundTaskWorker` and `BackgroundTaskStartupRecovery` now consume `platform.job.api` instead of touching execution orchestration directly
- remaining Task 3 work should continue by moving more storage capability decisions and background-task orchestration into `platform.storage` and `platform.job`, not by adding new policy truth back into `files.policy` or `files.tasks`

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

**Current progress note:**
- `files.workspace.api.WorkspaceDirectoryApi` now exists as the first live workspace bridge for directory creation and directory-page loading
- `files.workspace.internal.application.RuntimeWorkspaceDirectoryApi` now owns the first compatibility slice for `mkdir` and `list` orchestration, while legacy `FileService` delegates to it
- `files.workspace.api.WorkspaceMutationApi` now exists as the next live workspace bridge for `rename` and `move`, and legacy `FileService` delegates those mutations through `files.workspace` instead of keeping the persistence/remap logic inline
- `files.content.api.ContentRegistrationApi` now exists as the first live content bridge for blob-backed file registration
- `files.content.internal.application.RuntimeContentRegistrationApi` now owns the first compatibility slice for persisted file metadata registration plus primary-entity binding, while legacy `FileService.saveFileMetadata(...)` delegates to it
- `files.content.api.ContentDuplicationApi` now exists as the next live content bridge for blob-backed file duplication, and legacy `FileService.copy()` now delegates copied file persistence plus primary-entity binding through that seam instead of holding the binding logic locally
- live backend ArchUnit coverage now includes `Task4ABridgeArchitectureTest`, which guards that the new workspace/content `api` packages stay away from `internal` and that Task 4A bridge internals are currently consumed only by the legacy `files.core` compatibility shell, with `FileService` now required to depend on `WorkspaceDirectoryApi`, `WorkspaceMutationApi`, and `ContentRegistrationApi`
- legacy `FileService` still owns broader recycle/copy/share/archive orchestration, but Task 4A is now sufficiently bridged and further workspace lifecycle extraction should continue in Task 4B instead of adding more creation/query ownership back into `files.core`
- Task 4A completion definition is now met: the live workspace/content API seam exists, legacy controllers remain stable, compatibility routing goes through the new APIs, and the bridge is covered by both runtime tests and ArchUnit gates

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

**Current progress note:**
- `files.workspace.api.WorkspacePathPolicy` and `files.workspace.internal.application.RuntimeWorkspacePathPolicy` now own path normalization, same-directory conflict checks, directory hierarchy materialization, existing-directory validation, and recycle-restore target validation
- legacy `WorkspaceNodeRulesService` is now a compatibility delegate to `WorkspacePathPolicy`, so upload/core callers no longer keep path legality truth inline
- `files.workspace.api.WorkspaceLifecycleApi` and `files.workspace.internal.application.RuntimeWorkspaceLifecycleApi` now own workspace copy, recycle, and restore lifecycle orchestration
- legacy `FileService` now delegates `copy`, `delete`, and `restoreFromRecycleBin` through `WorkspaceLifecycleApi`, while keeping quota checks, cache invalidation, locking, and event publication as compatibility orchestration
- live backend ArchUnit coverage now includes `Task4BWorkspaceOwnershipArchitectureTest`, which requires `FileService` to depend on `WorkspaceLifecycleApi` and `WorkspaceNodeRulesService` to depend on `WorkspacePathPolicy`
- Task 4B completion definition is now met; further workspace slimming should continue by deleting remaining compatibility shells only after later tasks stabilize

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

**Current progress note:**
- `files.content.api.ContentAssetApi` and `files.content.internal.application.RuntimeContentAssetApi` now own content asset reuse, primary entity relation persistence, default content storage capability reads, and primary-entity backfill
- `RuntimeContentRegistrationApi` now delegates primary-entity creation/reuse and relation persistence through `ContentAssetApi`, so content registration no longer keeps its own asset/version truth inline
- legacy `ContentAssetBindingService` and `FileEntityBackfillService` are now compatibility delegates to `ContentAssetApi` instead of owning `FileEntity` and `StoredFileEntity` business logic directly
- legacy `FileService` now reads default content storage capabilities through `ContentAssetApi`, so content capability truth is no longer held in `files.core`
- live backend ArchUnit coverage now includes `Task4CContentOwnershipArchitectureTest`, which requires `FileService`, `ContentAssetBindingService`, and `FileEntityBackfillService` to depend on `ContentAssetApi`
- Task 4C completion definition is now met; the next step should move on to Task 5 upload-ingress narrowing instead of adding new content asset truth back into `files.core`

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
- `files.upload` in `backend/` materially matches the `backend-next` target shape, with explicit `api` plus `internal/{application,domain,infra,web}` ownership points created before new rule migration continues
- upload completion is routed through workspace/content APIs
- upload owns session/process control only
- legacy upload entrypoints depend on `files.upload.api` contracts instead of reaching back into `files.core` rule services for new ownership
- no new code in upload directly owns final node creation or final content registration
- related tests are green

**Current progress note:**
- `files.upload.api.UploadTargetPolicy` now exists as the upload-module entry for target validation, normalization, default-policy snapshot reads, duplicate-name checks, and quota/size admission
- `files.upload.internal.application.RuntimeUploadTargetPolicy` now owns that target-validation truth through `WorkspacePathPolicy`, `StoragePolicyQuery`, and `UploadConstraintPolicy`, so `UploadSessionService` no longer depends on legacy `WorkspaceNodeRulesService` or `FileUploadRulesService`
- `files.upload.api.UploadCompletionApi` and `files.upload.api.UploadCompletionCommand` now exist as the upload-module completion seam for persisted blob finalization
- `files.upload.internal.application.RuntimeUploadCompletionApi` now owns blob completion, directory materialization, blob registration, and content-registration handoff through `WorkspacePathPolicy` plus `ContentRegistrationApi`
- legacy `UploadSessionService` now delegates session-finalization through `UploadCompletionApi` and keeps only upload-session/process orchestration
- legacy `FileService.completeUpload(...)` now delegates final blob completion through `UploadCompletionApi`, so the legacy `/api/files/upload/complete` path also flows through the upload-module seam instead of holding finalization logic inline
- `backend/src/main/java/com/yoyuzh/files/upload/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live upload module materially matches the `backend-next` destination shape instead of stopping at a flat package
- live backend ArchUnit coverage now includes `Task5UploadIngressArchitectureTest`, which requires `UploadSessionService` to depend on `UploadTargetPolicy`, requires `FileService` to depend on `UploadCompletionApi`, and forbids `UploadSessionService` from depending on legacy `WorkspaceNodeRulesService` or `FileUploadRulesService`
- Task 5 completion definition is now met; the next step should move on to Task 6 sharing/search API extraction instead of adding new upload rule ownership back into `files.upload` root classes or `files.core`

**Prohibitions:**
- prohibit new formal node creation logic from residing inside `files.upload`
- prohibit upload from directly writing workspace/content repository implementations
- prohibit new upload code from introducing alternate roots or skipping the target `api/internal/*` layering for convenience
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
- `files.sharing` and `files.search` in `backend/` materially match the `backend-next` target shape, with explicit `api` plus `internal/{application,domain,infra,web}` ownership points created before new rule migration continues
- sharing and search are reachable through explicit module APIs
- old controllers remain stable
- share policy truth lives in `files.sharing`
- search orchestration no longer depends on hidden repository bypasses
- related tests are green

**Current progress note:**
- `files.search.api.FileSearchApi` and `files.search.api.SearchFilesQuery` now exist as the search-module seam for authenticated file retrieval, and `files.search.internal.application.RuntimeFileSearchApi` now owns the current runtime search orchestration
- legacy `FileSearchService` is now a compatibility shell that delegates to `FileSearchApi`, while `backend/src/main/java/com/yoyuzh/files/search/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live search module materially matches the `backend-next` destination shape instead of stopping at a flat package
- `FileSearchV2Controller` now depends directly on `FileSearchApi` and builds target query DTOs instead of routing through legacy `FileSearchService`
- `files.sharing.api.SharingApi`, `CreateShareCommand`, and `ImportShareCommand` now exist as the sharing-module seam for create/view/verify/import/download/list/delete operations, and `files.sharing.internal.application.RuntimeSharingApi` now owns the current runtime sharing orchestration
- legacy `ShareV2Service` is now a compatibility shell that delegates to `SharingApi`, while `backend/src/main/java/com/yoyuzh/files/sharing/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live sharing module materially matches the `backend-next` destination shape instead of stopping at a flat package
- `ShareV2Controller` now depends directly on `SharingApi` and maps request DTOs into sharing-module commands instead of routing through legacy `ShareV2Service`
- live backend ArchUnit coverage now includes `Task6SharingSearchArchitectureTest`, which requires legacy `ShareV2Service` and `FileSearchService` plus the v2 controllers to depend on `files.sharing.api.SharingApi` and `files.search.api.FileSearchApi`
- Task 6 completion definition is now met; the next step should move on to Task 7 transfer layering instead of adding new sharing/search rule ownership back into legacy `files.share` or flat `files.search` compatibility code

**Prohibitions:**
- do not let search query other modules' internal tables directly as new code
- do not leave share expiry/password/download/import truth scattered across controllers and services
- do not keep new sharing/search module code under legacy `files.share` or flat `files.search` packages once a target-layer home exists
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
- `transfer` in `backend/` materially matches the `backend-next` target shape, with explicit `api` plus `internal/{application,domain,infra,web}` ownership points created before new rule migration continues
- transfer receive/import policy is owned by `transfer` domain code
- old transfer routes remain stable
- transfer import uses workspace/content APIs instead of bypassing ownership
- no new transfer code depends on sharing/workspace internals directly
- related tests are green

**Current progress note:**
- `transfer.api.TransferSessionApi` now exists as the transfer-module seam for create/lookup/join/list/upload/download/import/signal/prune orchestration, and `transfer.internal.application.RuntimeTransferSessionApi` now owns the current runtime transfer flow
- legacy `TransferService` is now a compatibility shell that delegates to `TransferSessionApi`, while `backend/src/main/java/com/yoyuzh/transfer/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live transfer module materially matches the `backend-next` destination shape instead of stopping at the flat legacy root
- `TransferController` now depends directly on `TransferSessionApi` and maps HTTP requests into transfer-module commands instead of routing through legacy `TransferService`
- `transfer.api.TransferImportApi` and `TransferImportCommand` now exist as the transfer import seam, and `transfer.internal.application.RuntimeTransferImportApi` now owns offline-file import through `WorkspacePathPolicy`, `ContentRegistrationApi`, `StoragePolicyQuery`, and `UploadConstraintPolicy`
- legacy `TransferImportService` is now a compatibility shell that delegates to `TransferImportApi`, and transfer offline import no longer depends on legacy `FileService.importExternalFile(...)`
- live backend ArchUnit coverage now includes `Task7TransferArchitectureTest`, which requires `TransferController` and `TransferService` to depend on `TransferSessionApi`, requires `TransferImportService` to depend on `TransferImportApi`, forbids transfer import from depending on legacy `FileService`, and requires the runtime transfer import path to depend on `WorkspacePathPolicy` plus `ContentRegistrationApi`
- Task 7 completion definition is now met; the next step should move on to Task 8 `ops.admin` governance tightening instead of adding new transfer rule ownership back into the flat legacy `com.yoyuzh.transfer` root or `files.core`

**Prohibitions:**
- do not let transfer import create workspace/content truth through direct repositories
- do not move share semantics into transfer
- do not keep new transfer business rules in the flat legacy `com.yoyuzh.transfer` root once a target-layer home exists
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
- `ops.admin` in `backend/` materially matches the `backend-next` target shape, with explicit `api` plus `internal/{application,domain,infra,web}` ownership points created before new rule migration continues
- admin controllers remain stable
- admin orchestration goes through explicit module APIs
- read-only sections remain preserved where required
- no new admin code directly depends on core repositories or core `internal` packages
- related tests are green

**Current progress note:**
- `ops.admin.api.AdminSettingsGovernanceApi`, `AdminUserGovernanceApi`, and `AdminResourceGovernanceApi` now exist as the admin-governance seams for settings, user governance, and resource governance/read-model access
- `ops.admin.internal.application.RuntimeAdminSettingsGovernanceApi`, `RuntimeAdminUserGovernanceApi`, and `RuntimeAdminResourceGovernanceApi` now own the current runtime admin orchestration while delegating to the existing legacy admin services/query services
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live admin governance module materially matches the `backend-next` destination shape instead of stopping at the flat legacy `com.yoyuzh.admin` root
- `AdminSettingsController`, `AdminUserController`, and `AdminResourceController` now depend directly on `ops.admin.api` contracts instead of reaching into legacy `AdminMutableSettingsService`, `AdminUserGovernanceService`, `AdminInspectionQueryService`, or `AdminResourceGovernanceService`
- live backend ArchUnit coverage now includes `Task8OpsAdminArchitectureTest`, which requires the admin controllers to depend on `ops.admin.api` and forbids them from depending on the legacy governance/query services directly
- Task 8 completion definition is now met for the current migration phase: routes stay stable, governance orchestration now enters through explicit `ops.admin.api` seams, and no new `ops.admin` code reaches core repositories or core module internals directly
- the next step should move on to Task 9 boot/shared/infra cleanup and final cutover guardrails instead of adding new governance entry logic back into the flat legacy admin root

**Prohibitions:**
- do not let `ops.admin` directly mutate workspace/content/sharing/transfer internals
- do not let admin services become the new owner of identity, storage, or transfer rules
- do not keep new governance logic in the legacy `com.yoyuzh.admin` root once a target-layer home exists
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
- `boot`, `shared.kernel`, and `infra` in `backend/` materially match the `backend-next` destination map, and legacy roots no longer receive new business ownership
- legacy ownership roots no longer carry active business truth
- backend still boots and tests are green
- `backend-next` gate tests are green
- only then are compatibility shells eligible for deletion

**Current progress note:**
- `app.android.api.AndroidReleaseQueryApi` now exists as the first live Task 9 seam, and `app.android.internal.application.RuntimeAndroidReleaseQueryApi` now owns Android release query/download delegation while legacy `AndroidReleaseController` stays route-stable
- `AndroidReleaseController` now depends on `app.android.api.AndroidReleaseQueryApi` instead of reaching directly into legacy `AndroidReleaseService`, and live backend ArchUnit coverage now includes `Task9BootSharedInfraArchitectureTest` to guard that entry seam
- `backend/src/main/java/com/yoyuzh/boot`, `shared/kernel`, `infra`, `infra/{broker,cache,client,lock}`, and `app/android/internal/{domain,infra,web}` now exist as tracked target-layer packages so Task 9 has a concrete runtime destination map in `backend/`, not only in `backend-next/`
- `backend-next` gate tests are currently green (`cd backend-next && mvn test`), so the target architecture checks remain intact after Tasks 1-8 plus this first Task 9 slice
- Task 9 is started but not complete yet: deeper relocation/cleanup for legacy `common` and `config` ownership still remains before compatibility-shell deletion can be considered

**Prohibitions:**
- do not dump business-specific classes into `shared.kernel`
- do not move module-specific repository abstractions into global `infra`
- do not preserve legacy roots as parallel architecture once target homes exist and runtime/architecture gates are green
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
