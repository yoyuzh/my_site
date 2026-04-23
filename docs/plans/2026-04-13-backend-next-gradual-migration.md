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
- auth/user web entrypoints are now physically aligned to the target module shape: `AuthController`, `DevAuthController`, and `UserController` live under `identity.access.internal.web`, while the current auth request/response DTOs (`AuthResponse`, `LoginRequest`, `RefreshTokenRequest`, `RegisterRequest`, `UpdateUser*Request`, `UserProfileResponse`) now live under `identity.access.api`
- refresh-token and invite-code persistence types are now physically aligned too: `RefreshToken` and `RegistrationInviteState` live under `identity.access.internal.domain`, while `RefreshTokenRepository` and `RegistrationInviteStateRepository` live under `identity.access.internal.infra`; `RuntimeIdentityRefreshTokenManager`, `RegistrationInviteService`, admin integration coverage, and auth integration tests were rewired to those target-layer owners
- legacy `AuthService` now consumes those moved identity-access API contracts as a compatibility shell, so the surviving `com.yoyuzh.auth` root has been reduced to compatibility services, security helpers, and the remaining identity/service adapters instead of still owning controller/DTO/persistence entry surfaces
- live backend ArchUnit coverage now includes `Task2IdentityAccessArchitectureTest`, which requires the moved auth/user controllers to exist under `identity.access.internal.web`, requires the moved DTOs to exist under `identity.access.api`, requires the moved refresh/invite persistence types to exist under `identity.access.internal.{domain,infra}`, and blocks recreating the legacy `com.yoyuzh.auth.dto` root or old `com.yoyuzh.auth.{AuthController,DevAuthController,UserController,RefreshToken,RefreshTokenRepository,RegistrationInviteState,RegistrationInviteStateRepository}` entrypoints
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
- `backend/src/main/java/com/yoyuzh/platform/storage/internal/{application,domain,infra}/StoragePolicy*.java`
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
- default storage-policy snapshot, policy-id reads, and policy-capability reads now route through `platform.storage.api.StoragePolicyQuery`
- `StoragePolicy` entity, `StoragePolicyRepository`, and `StoragePolicyService` have been physically moved from legacy `files.policy` into `platform.storage.internal.{domain,infra,application}`
- `platform.storage.api.DefaultStoragePolicySnapshot` now exposes only `policyId`, `policyMaxSizeBytes`, and `StoragePolicyCapabilities`, so the storage API no longer leaks the internal storage-policy JPA entity
- `StoragePolicyService` and `files.upload.UploadPolicyResolver` now delegate upload-size and upload-mode decisions through `platform.storage.api` contracts instead of re-owning those rules
- `UploadSessionService` now resolves stored-session upload mode through `StoragePolicyQuery.readPolicyCapabilities(...)` instead of reaching back through the storage-policy entity/service pair
- `FileUploadRulesService` and legacy `FileService` now consume storage limits through `platform.storage.api.StoragePolicyQuery` plus `UploadConstraintPolicy`, so files-side compatibility code no longer depends on the moved storage-policy entity/repository/service internals
- `UploadSessionService`, `FileUploadRulesService`, and `FileService` now read default storage policy and capabilities through the shared default-policy snapshot entry instead of manually pairing `ensureDefaultPolicy()` with `readCapabilities(...)`
- `ContentAssetBindingService`, `FileEntityBackfillService`, and `AdminConfigSnapshotService` now consume storage-policy snapshot/id data through platform-storage APIs, so content/admin paths no longer need direct default-policy query ownership in `files.policy`
- async retry truth now routes through `platform.job.api.AsyncJobRetryPolicy`
- legacy `BackgroundTaskRetryPolicy` now acts as a compatibility shell over `platform.job.api`
- `BackgroundTaskType`, `BackgroundTaskStatus`, and `BackgroundTaskFailureCategory` are now owned by `platform.job.api`, so `api/v2/tasks`, `ops.admin`, retry policy, and execution gateway entrypoints no longer depend on legacy `files.tasks` enums as cross-module contracts
- live backend ArchUnit coverage now includes `Task3PlatformSeamArchitectureTest`, which requires task public enums to live under `platform.job.api`, prevents recreating `com.yoyuzh.files.tasks`, and keeps task/admin entrypoints pointed at platform-job APIs rather than the old files-task root
- `platform.job.api.BackgroundTaskLifecycleApi` plus `platform.job.api.BackgroundTaskView` now provide the v2 task endpoint contract, and `api/v2/tasks/BackgroundTaskV2Controller` no longer depends directly on legacy `files.tasks` entity/service types
- `RuntimeBackgroundTaskLifecycleApi` now hosts the mapping from `platform.job.internal.application.BackgroundTaskService` to API view objects, so task endpoint read/write model exposure is concentrated in `platform.job.internal.application`
- `platform.job.api.BackgroundTaskLifecycleApi` now also owns the broker-driven `createQueuedAutoMediaMetadataTask` seam, and `platform.job.internal.application.MediaMetadataTaskBrokerConsumer` no longer depends on legacy `BackgroundTaskCommandService`
- legacy `platform.job.api.BackgroundTaskCommandGateway`, `platform.job.internal.application.RuntimeBackgroundTaskCommandGateway`, and `files.tasks.BackgroundTaskCommandService` have been removed, so Task 3 no longer keeps a duplicate command seam that exposed `files.tasks.BackgroundTask` as a cross-module contract
- `platform.job.api.AsyncJobRetryPolicy` now evaluates remaining attempts using scalar retry state (`attemptCount`/`maxAttempts`) instead of importing `files.tasks.BackgroundTask`, so the retry-policy API no longer leaks legacy task entity coupling across module boundaries
- worker/startup execution orchestration now depends on `platform.job.internal.application.BackgroundTaskExecutionGateway` (module-internal seam) instead of a `platform.job.api` contract, so `platform.job.api` no longer exposes execution methods typed with a runtime task entity
- `ops.admin` storage migration enqueue flow now uses `platform.job.api.BackgroundTaskLifecycleApi` and `BackgroundTaskView`, so `AdminStoragePolicyController` and `AdminStorageGovernanceService` no longer expose legacy `files.tasks.BackgroundTask` in admin entrypoint contracts
- `platform.storage.api.StoragePolicyDescriptor` now exists as the module-owned read model for policy id/name/type/enabled/max-size metadata, and `platform.job.internal.application.StoragePolicyMigrationBackgroundTaskHandler` now reads source/target policy metadata through `StoragePolicyQuery` instead of depending directly on moved `platform.storage.internal` entity/repository types
- the former `files.tasks` runtime package has now been physically moved under `platform.job.internal`: runtime orchestration/handlers under `internal.application`, the `BackgroundTask` JPA entity under `internal.domain`, and `BackgroundTaskRepository` under `internal.infra`
- `Task3PlatformSeamArchitectureTest` now asserts the storage-policy runtime owner classes live under `platform.storage.internal`, prevents recreating `com.yoyuzh.files.policy`, prevents `platform.storage.api` from depending on storage internals or legacy policy/auth packages, and gates key files/upload/transfer consumers away from storage-policy internals
- remaining Task 3 work should continue by moving more storage capability decisions and background-task orchestration into `platform.storage` and `platform.job`, not by adding new policy truth back into `files.policy` or `files.tasks`

**Prohibitions:**
- do not leave upload-mode truth in legacy upload/file services
- do not leave retry rules spread across controllers, workers, and services
- do not create new direct dependencies on `files.policy` or `files.tasks` internals

**Verification:** `cd backend && mvn test -Dtest=Task3PlatformSeamArchitectureTest,StoragePolicyServiceTest,RuntimeStoragePolicyAdminApiTest,UploadPolicyResolverTest,RuntimeUploadTargetPolicyTest,UploadSessionServiceTest,RuntimeTransferImportApiTest,BackgroundTaskServiceTest,BackgroundTaskV2ControllerIntegrationTest`

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
- `files.workspace.api` contracts now accept scalar `userId` inputs instead of exposing legacy `auth.User`; runtime adapters create module-internal user references only where persistence associations still require them
- `files.content.api.ContentRegistrationCommand` now also accepts scalar `userId`, so content registration/duplication APIs no longer expose legacy auth entities across the module seam
- live backend ArchUnit coverage now includes `Task4ABridgeArchitectureTest`, which guards that the new workspace/content `api` packages stay away from `internal`, forbids those `api` packages from depending on `auth..`, and verifies that Task 4A bridge internals are currently consumed only by the legacy `files.core` compatibility shell, with `FileService` now required to depend on `WorkspaceDirectoryApi`, `WorkspaceMutationApi`, and `ContentRegistrationApi`
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
- `files.content.api.ContentAssetApi` now takes scalar `userId` input for primary-entity creation/reuse instead of leaking legacy `auth.User`; runtime content code adapts that ID only inside `files.content.internal.application`
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
- `UploadTargetPolicy` now accepts scalar `userId`, max-upload-size, and storage-quota snapshots instead of exposing legacy `auth.User`, keeping identity/entity shape outside the upload API contract
- `files.upload.internal.application.RuntimeUploadTargetPolicy` now owns that target-validation truth through `WorkspacePathPolicy`, `StoragePolicyQuery`, and `UploadConstraintPolicy`, so `UploadSessionService` no longer depends on legacy `WorkspaceNodeRulesService` or `FileUploadRulesService`
- `files.upload.api.UploadCompletionApi` and `files.upload.api.UploadCompletionCommand` now exist as the upload-module completion seam for persisted blob finalization
- `UploadCompletionCommand` now carries scalar `userId`, and upload completion hands off final workspace/content truth through `WorkspacePathPolicy` and `ContentRegistrationApi` without leaking legacy auth entities across the API seam
- `files.upload.internal.application.RuntimeUploadCompletionApi` now owns blob completion, directory materialization, blob registration, and content-registration handoff through `WorkspacePathPolicy` plus `ContentRegistrationApi`
- legacy `UploadSessionService` now delegates session-finalization through `UploadCompletionApi` and keeps only upload-session/process orchestration
- legacy `FileService.completeUpload(...)` now delegates final blob completion through `UploadCompletionApi`, so the legacy `/api/files/upload/complete` path also flows through the upload-module seam instead of holding finalization logic inline
- `backend/src/main/java/com/yoyuzh/files/upload/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live upload module materially matches the `backend-next` destination shape instead of stopping at a flat package
- live backend ArchUnit coverage now includes `Task5UploadIngressArchitectureTest`, which requires `UploadSessionService` to depend on `UploadTargetPolicy`, requires `FileService` to depend on `UploadCompletionApi`, forbids `UploadSessionService` from depending on legacy `WorkspaceNodeRulesService` or `FileUploadRulesService`, and forbids `files.upload.api..` from depending on `auth..`
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
- file-event runtime ownership has now been pulled out of the legacy `files.events` root and into `files.search`: `files.search.api.FileEventApi`, `FileEventRecordCommand`, and public `FileEventType` now define the module seam, while SSE dispatch, persisted event state, and Redis pub/sub replication live under `files.search.internal.{application,domain,infra}`
- `FileEventsV2Controller` and legacy `FileService` now both depend on `files.search.api.FileEventApi` instead of reaching into a legacy `files.events` service root, so file-event streaming and publication no longer require cross-module internal imports
- `files.sharing.api.SharingApi`, `CreateShareCommand`, and `ImportShareCommand` now exist as the sharing-module seam for create/view/verify/import/download/list/delete operations, and `files.sharing.internal.application.RuntimeSharingApi` now owns the current runtime sharing orchestration
- `backend/src/main/java/com/yoyuzh/files/sharing/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live sharing module materially matches the `backend-next` destination shape instead of stopping at a flat package; `FileShareLink` and `FileShareLinkRepository` have been physically moved into `files.sharing.internal.{domain,infra}`
- legacy share-link compatibility DTOs `CreateFileShareLinkResponse`, `FileShareDetailsResponse`, and `ImportSharedFileRequest` have now been moved out of `files.share` into `files.sharing.api`, so old `/api/files/share-links` and transfer-import controllers no longer depend on the legacy share DTO root
- `ShareV2Controller` now depends directly on `SharingApi` and maps request DTOs into sharing-module commands instead of routing through legacy `ShareV2Service`
- old `/api/files/share-links` compatibility endpoints now call `files.sharing.api.SharingApi` directly, and shared-file import/download ownership lives inside `RuntimeSharingApi` instead of routing back through a legacy `ShareV2Service` or `FileService.importSharedFile(...)`
- the legacy `com.yoyuzh.files.share` runtime root has now been removed completely: `ShareV2Service` is deleted, `FileController` routes through `files.sharing.api`, and `Task6SharingSearchArchitectureTest` now blocks recreating both `com.yoyuzh.files.share..` and `com.yoyuzh.files.events..`
- live backend ArchUnit coverage now includes `Task6SharingSearchArchitectureTest`, which requires `FileSearchService` plus the sharing/search web entrypoints and legacy `/api/files/share-links` controller to depend on `files.sharing.api.SharingApi`, `files.search.api.FileSearchApi`, and `files.search.api.FileEventApi`, requires `FileService` to publish file events through `files.search.api.FileEventApi`, and blocks recreating the legacy `com.yoyuzh.files.events` / `com.yoyuzh.files.share` runtime roots
- `files.search.api.FileSearchApi` now accepts `userId` scalar input instead of exposing `auth.User` as an API contract; `RuntimeFileSearchApi`, `FileSearchService`, and `FileSearchV2Controller` have been rewired accordingly, and `Task6SharingSearchArchitectureTest` now also forbids `files.search.api..` from depending on `auth..`
- `files.sharing.api.SharingApi` now also accepts `userId` scalar input for owner/recipient operations instead of exposing `auth.User`; v2 sharing controllers and legacy `ShareV2Service` compatibility calls route through that seam, and `Task6SharingSearchArchitectureTest` now forbids `files.sharing.api..` from depending on `auth..`
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
- `backend/src/main/java/com/yoyuzh/transfer/internal/application/TransferService.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/application/TransferImportService.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/web/TransferController.java`

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
- `ops.admin.api` now also owns the current admin governance contract records that were still sitting in the flat legacy root: `AdminPasswordResetResponse`, `AdminUserResponse`, `AdminFileBlobResponse`, `AdminFileResponse`, `AdminShareResponse`, `AdminOfflineTransferStorageLimitResponse`, `AdminRegistrationInviteCodeResponse`, `AdminSettingsResponse`, and `AdminSettingsUpdateRequest` have moved out of `com.yoyuzh.admin`
- `ops.admin.internal.application.RuntimeAdminSettingsGovernanceApi`, `RuntimeAdminUserGovernanceApi`, and `RuntimeAdminResourceGovernanceApi` now own the current runtime admin orchestration while delegating to the existing legacy admin services/query services
- `ops.admin.internal.application.AdminTaskQueryService` now loads owner identity summaries through `identity.access.api.IdentityUserDirectoryApi` instead of directly depending on `auth.UserRepository`, so admin task read models keep identity lookup behind module APIs
- `ops.admin.internal.application.AdminAuditService` now resolves actor user snapshots through `identity.access.api.IdentityUserDirectoryApi` instead of directly depending on `auth.UserRepository`, so admin audit identity hydration also follows the identity module API seam
- `identity.access.api.IdentityAdminSummaryApi` now exists and `identity.access.internal.application.RuntimeIdentityAdminSummaryApi` now owns admin-summary identity reads (`countUsersAsAdmin`, `currentInviteCode`), so `ops.admin.internal.application.AdminInspectionQueryService` no longer directly depends on `auth.UserRepository` or `auth.RegistrationInviteService`
- `transfer.api.TransferAdminMetricsApi` now exists and `transfer.internal.application.RuntimeTransferAdminMetricsApi` now owns admin-summary offline storage reads (`currentOfflineStorageBytes`), so `ops.admin.internal.application.AdminInspectionQueryService` no longer directly depends on `transfer.OfflineTransferSessionRepository`
- `ops.admin.internal.application.AdminConfigSnapshotService` now also reads registration invite, file/blob/entity overview counts through `identity.access.api.IdentityAdminSummaryApi`, `files.workspace.api.WorkspaceAdminGovernanceApi`, and `files.content.api.ContentAdminInspectionApi`; it no longer directly depends on `auth.RegistrationInviteService`, `files.core.StoredFileRepository`, `files.core.FileBlobRepository`, or `files.core.FileEntityRepository`
- `ops.admin.internal.application.AdminMutableSettingsService` now reads/updates/rotates invite codes through `identity.access.api.IdentityAdminSummaryApi`; it no longer directly depends on `auth.RegistrationInviteService` for mutable registration governance
- `files.workspace.api.WorkspaceAdminGovernanceApi` plus `WorkspaceAdminFileSnapshot` now own the admin file-delete seam for workspace governance, and `ops.admin.internal.application.AdminResourceGovernanceService` no longer directly depends on `files.core.StoredFileRepository` or `files.core.FileService`
- `files.workspace.api.WorkspaceAdminGovernanceApi` now also includes `listFilesAsAdmin` with `WorkspaceAdminFileQuery` / `WorkspaceAdminFileView` plus `countFilesAsAdmin`, and `ops.admin.internal.application.AdminInspectionQueryService` no longer maps admin file read models from `files.core.StoredFile` directly or reads file counts through `files.core.StoredFileRepository`
- `files.content.api.ContentAdminInspectionApi` now exists with `ContentAdminFileBlobQuery` / `ContentAdminFileBlobView` plus `ContentEntityType` and `totalBlobSize`, and `files.content.internal.application.RuntimeContentAdminInspectionApi` now owns the admin file-blob read-model assembly plus blob-size summary read that previously lived in `ops.admin`
- `files.sharing.api.SharingApi` now includes `deleteShareAsAdmin` with `SharingAdminShareSnapshot`, and `ops.admin.internal.application.AdminResourceGovernanceService` no longer directly depends on `files.share.FileShareLinkRepository` for share-delete governance
- `files.sharing.api.SharingApi` now also includes `listSharesAsAdmin` with `SharingAdminShareQuery` / `SharingAdminShareView`, and `ops.admin.internal.application.AdminInspectionQueryService` no longer directly depends on `files.share.FileShareLinkRepository` for share-list read models
- `ops.admin.api.AdminFileEntityType` now owns the admin file-blob entity-type contract, and `AdminResourceGovernanceApi` / `AdminFileBlobResponse` no longer expose `files.core.FileEntityType` directly; runtime mapping now happens inside `RuntimeAdminResourceGovernanceApi` and `AdminInspectionQueryService`
- `ops.admin.api.AdminUserRole` now owns the admin user-role contract, and `AdminUserGovernanceApi` / `AdminUserResponse` / `AdminUserRoleUpdateRequest` no longer expose `auth.UserRole` directly; runtime mapping now happens inside `RuntimeAdminUserGovernanceApi` and `AdminUserGovernanceService`
- `identity.access.api.IdentityAdminUserGovernanceApi` now exists with `IdentityAdminUserQuery` / `IdentityAdminUserView`, and `identity.access.internal.application.RuntimeIdentityAdminUserGovernanceApi` now owns admin user list/role/status/password/quota governance operations that were previously implemented directly in `ops.admin.internal.application.AdminUserGovernanceService`
- `ops.admin.internal.application.AdminUserGovernanceService` now orchestrates user governance only through `identity.access.api.IdentityAdminUserGovernanceApi` plus `files.workspace.api.WorkspaceAdminGovernanceApi`; it no longer directly depends on `auth.UserRepository`, `auth.PasswordPolicy`, `auth.UserRole`, `files.core.StoredFileRepository`, or `identity.access.api.IdentityCredentialRevocationPolicy`
- `files.workspace.api.WorkspaceAdminGovernanceApi` now also owns admin user-storage read seams (`loadUsedStorageBytesByUserId`, `loadUsedStorageBytesByUserIds`), so `ops.admin` no longer reads per-user used storage through `files.core.StoredFileRepository`
- `backend/src/main/java/com/yoyuzh/ops/admin/internal/{domain,infra,web}` now exist as tracked target-layer packages so the live admin governance module materially matches the `backend-next` destination shape instead of stopping at the flat legacy `com.yoyuzh.admin` root
- `AdminSettingsController`, `AdminUserController`, and `AdminResourceController` now depend directly on `ops.admin.api` contracts instead of reaching into legacy `AdminMutableSettingsService`, `AdminUserGovernanceService`, `AdminInspectionQueryService`, or `AdminResourceGovernanceService`
- the live backend admin web slice is now physically split instead of staying flat: `AdminOverviewController`, `AdminAuditController`, `AdminSettingsController`, `AdminUserController`, `AdminResourceController`, `AdminTaskController`, `AdminStoragePolicyController`, `AdminAccessEvaluator`, `ApiRequestMetricsFilter`, and the admin request DTOs used by those controllers now live under `ops.admin.internal.web`
- the live backend admin persistence slice is now also physically split: audit log entities/repositories, metrics state/timeline persistence, daily-active-user persistence, and runtime-settings persistence now live under `ops.admin.internal.infra` instead of the flat legacy `com.yoyuzh.admin` root
- the live backend admin application slice is now also physically split: `AdminAuditService`, `AdminAuditQueryService`, `AdminConfigSnapshotService`, `AdminInspectionQueryService`, `AdminMetricsService`, `AdminMutableSettingsService`, `AdminResourceGovernanceService`, `AdminRuntimeSettingsService`, `AdminStorageGovernanceService`, `AdminStoragePolicyQueryService`, `AdminTaskQueryService`, `AdminUserGovernanceService`, plus the admin read models/enums/helpers they own now live under `ops.admin.internal.application`
- the flat legacy production root `backend/src/main/java/com/yoyuzh/admin` is now emptied, so new admin implementation work has to land in `ops.admin.api` or `ops.admin.internal.*` instead of reusing the old flat package
- `platform.job.api.BackgroundTaskAdminQueryApi` plus `AdminBackgroundTaskQuery` / `AdminBackgroundTaskView` now own the admin task-list/read seam, and `ops.admin.internal.application.AdminTaskQueryService` no longer depends directly on `files.tasks.BackgroundTaskRepository` or `files.tasks.BackgroundTask`
- live backend ArchUnit coverage now includes `Task8OpsAdminArchitectureTest`, which requires the admin controllers to depend on `ops.admin.api`, forbids them from depending on the moved admin application services directly, asserts that the moved admin governance contract records are owned by `ops.admin.api`, and asserts that the moved admin `application`, `web`, and `infra` classes no longer live under legacy `com.yoyuzh.admin`
- `Task8OpsAdminArchitectureTest` now also requires `AdminResourceGovernanceService` to orchestrate through `files.workspace.api.WorkspaceAdminGovernanceApi` and `files.sharing.api.SharingApi`, while forbidding direct dependency on legacy `files.core..` / `files.share..` packages
- `Task8OpsAdminArchitectureTest` now also requires `AdminInspectionQueryService` to load summary/file/share read models through `identity.access.api.IdentityAdminSummaryApi`, `transfer.api.TransferAdminMetricsApi`, `files.workspace.api.WorkspaceAdminGovernanceApi`, `files.content.api.ContentAdminInspectionApi`, and `files.sharing.api.SharingApi`, while forbidding direct dependency on legacy `auth.UserRepository`, `auth.RegistrationInviteService`, `transfer.OfflineTransferSessionRepository`, `files.core.StoredFile`, `files.core.StoredFileRepository`, `files.core.FileEntityRepository`, `files.core.FileBlobRepository`, `files.core.StoredFileEntityRepository`, and `files.share..` repositories/entities
- `Task8OpsAdminArchitectureTest` now also requires `AdminConfigSnapshotService` to read settings/filesystem summary through `identity.access.api.IdentityAdminSummaryApi`, `files.workspace.api.WorkspaceAdminGovernanceApi`, and `files.content.api.ContentAdminInspectionApi`, while forbidding direct dependency on legacy `auth.UserRepository`, `auth.RegistrationInviteService`, `files.core.StoredFileRepository`, `files.core.FileBlobRepository`, and `files.core.FileEntityRepository`
- `Task8OpsAdminArchitectureTest` now also requires `AdminMutableSettingsService` to depend on `identity.access.api.IdentityAdminSummaryApi` and forbids direct dependency on legacy `auth.RegistrationInviteService`
- `Task8OpsAdminArchitectureTest` now also requires `AdminUserGovernanceService` to depend on `identity.access.api.IdentityAdminUserGovernanceApi` and `files.workspace.api.WorkspaceAdminGovernanceApi`, forbids it from depending on `auth..` and `files.core.StoredFileRepository`, and forbids `RuntimeAdminUserGovernanceApi` from depending on legacy `auth..`
- `platform.storage.api.StoragePolicyAdminApi` now exists with `StoragePolicyAdminUpsertCommand`, `StoragePolicyAdminView`, and `StoragePolicyMigrationCandidate`, and `platform.storage.internal.application.RuntimeStoragePolicyAdminApi` now owns admin-facing storage-policy list/create/update/status/migration-candidate logic that previously lived directly in `ops.admin`
- `platform.storage.api` now also owns storage policy contract enums/value records (`StoragePolicyType`, `StoragePolicyCredentialMode`, `StoragePolicyCapabilities`) used by admin governance APIs, so `ops.admin` request/response contracts no longer leak legacy `files.policy` types as cross-module DTO signatures
- `ops.admin.internal.application.AdminStoragePolicyQueryService` and `AdminStorageGovernanceService` now orchestrate storage-policy governance through `platform.storage.api.StoragePolicyAdminApi` only; they no longer directly depend on the former `files.policy` repository/service, the moved `platform.storage.internal` storage-policy entity/repository/service, `files.core.FileEntityRepository`, or `files.core.StoredFileEntityRepository`
- `ops.admin.internal.application.AdminConfigSnapshotService` now also reads default storage-policy snapshot through `platform.storage.api.StoragePolicyAdminApi` instead of `platform.storage.api.StoragePolicyQuery`, and no longer depends directly on legacy `files.policy` entity/capability types
- `platform.job.api.BackgroundTaskLifecycleApi` now exposes `createQueuedTaskByUserId(...)`, and `ops.admin.internal.application.AdminStorageGovernanceService` now enqueues storage-policy migration tasks through that user-id seam, so admin storage governance no longer depends directly on `auth.User` as an orchestration contract
- `Task8OpsAdminArchitectureTest` now also requires `AdminStoragePolicyQueryService` and `AdminStorageGovernanceService` to depend on `platform.storage.api.StoragePolicyAdminApi`, and forbids those admin storage services from depending on legacy `files.policy..`, moved storage-policy internal entity/repository/service classes, and `files.core..` packages directly
- `Task8OpsAdminArchitectureTest` now also requires `AdminConfigSnapshotService` to depend on `platform.storage.api.StoragePolicyAdminApi` and forbids direct `files.policy..` dependency there, so admin filesystem snapshot assembly is aligned with module API seams end-to-end
- `Task8OpsAdminArchitectureTest` now also forbids `AdminStorageGovernanceService` from depending directly on legacy `auth..`, so admin storage migration orchestration is now constrained to module API-level user identity contracts
- Task 8 completion definition is now met for the current migration phase: routes stay stable, governance orchestration now enters through explicit `ops.admin.api` seams, application/web/infra ownership is physically aligned with `backend-next`, and no new `ops.admin` code reaches core repositories or core module internals directly
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
- `app.android.api.AndroidReleaseQueryApi` now exists as the first live Task 9 seam, and `app.android.internal.application.RuntimeAndroidReleaseQueryApi` now owns Android release metadata loading, package download URL construction, and cache-backed runtime orchestration directly instead of delegating to legacy `config.AndroidReleaseService`
- Android release entry wiring is now fully aligned to the target `app.android` shape: `app.android.internal.web.AndroidReleaseController`, `app.android.api.AndroidReleaseResponse`, `app.android.api.AndroidReleaseDownload`, `app.android.internal.domain.AndroidReleaseMetadata`, and `app.android.internal.infra.AndroidReleaseProperties` have replaced the legacy `config.AndroidRelease*` classes
- `shared.kernel.ApiResponse`, `PageResponse`, `BusinessException`, and `ErrorCode` now own the former root `common` global contracts, and `boot.web.GlobalExceptionHandler` now owns global exception mapping instead of the old flat `common` package
- `boot.security.SecurityConfig`, `JwtAuthenticationFilter`, `JwtProperties`, and `CorsProperties` now own the former security/config wiring that used to live under the flat legacy `config` root, so security edge wiring has started moving into the target `boot` area
- the remaining reusable security helpers have now been physically aligned there too: `JwtTokenProvider`, `AuthTokenInvalidationService`, `NoOpAuthTokenInvalidationService`, and `CustomUserDetailsService` now live under `boot.security`, runtime callers/tests have been rewired, and `Task9BootSharedInfraArchitectureTest` now blocks recreating their old `com.yoyuzh.auth.*` locations
- `boot.web.ApiRootController` and `OpenApiConfig`, plus top-level `boot.RestClientConfig`, `boot.SchedulingConfiguration`, and `boot.FileStorageConfiguration`, now own the remaining pure bootstrapping/runtime wiring that used to live under legacy `config`
- `platform.storage.internal.infra.FileStorageProperties` now owns the former storage property holder that used to live under legacy `config`, and the dependent runtime/test code has been rewired to that target package so storage runtime wiring now follows `platform.storage` instead of the flat legacy root
- `identity.access.internal.infra.RegistrationProperties` and `identity.access.internal.infra.AdminProperties` now own the former registration/admin property holders that used to live under legacy `config`, and `RegistrationInviteService` plus `PortalBackendApplication` have been rewired accordingly
- `backend/src/main/java/com/yoyuzh/boot`, `shared/kernel`, `infra`, `infra/{broker,cache,client,lock}`, `identity/access/internal/infra`, `platform/storage/internal/infra`, and `app/android/internal/{domain,infra,web}` now exist as tracked target-layer packages so Task 9 has a concrete runtime destination map in `backend/`, not only in `backend-next/`
- `infra.cache.AppRedisProperties`, `RedisConfiguration`, and `RedisCacheNames` now own the Redis/cache technical configuration that used to live under legacy `config`, and the dependent runtime/test code has been rewired to those `infra.cache` types
- `infra.broker.RedisLightweightBrokerGateway` / `InMemoryLightweightBrokerGateway` and `infra.lock.RedisDistributedLockGateway` / `NoOpDistributedLockGateway` now own the concrete broker/lock implementations directly, so `RuntimeLightweightBrokerGateway`, `RuntimeDistributedLockGateway`, and the legacy `common.broker` / `common.lock` packages have been deleted instead of remaining as a second compatibility layer under `common`
- the remaining legacy-root test residue has now been physically aligned too: the old `backend/src/test/java/com/yoyuzh/admin` package has been moved into `ops.admin.internal.{application,web}`, and the empty `backend/src/main/java/com/yoyuzh/{common,config}` plus `backend/src/test/java/com/yoyuzh/{common,config,admin}` directories have been removed from the source tree
- live backend verification is green through a refreshed 4A-9 regression slice plus auth/security/cache/exception coverage (`290` tests), so the new `shared.kernel`, `boot.web`, `boot.security`, `identity.access.internal.infra`, `platform.storage.internal.infra`, `infra.cache`, `app.android`, and direct `infra.{broker,lock}` implementations are now protected by both behavior tests and Task 9 ArchUnit rules
- full live-backend regression is now green (`cd backend && mvn test`, `504` tests), and the stale `FileServiceEdgeCaseTest` directory-create expectations were aligned with the already-established runtime contract before that full green run
- with the latest tree cleanup, the only surviving `com.yoyuzh.config` / `com.yoyuzh.common` references in `backend/src/test` are the negative assertions inside Task 9 ArchUnit tests
- `backend-next` gate tests have also been rerun after the latest Task 9 tree cleanup (`cd backend-next && mvn test`) and are currently green (`10` tests), so the target-side structure/mapping checks now have fresh evidence again
- a direct `mvn spring-boot:run` probe now reaches Tomcat/context startup on the default profile and then fails on the local MySQL connection declared in `backend/src/main/resources/application.yml`, so the remaining Task 9 checklist blocker is current-machine real-profile database availability rather than the boot/shared/infra refactor itself
- Task 9 is now in final-cutover audit mode: legacy `config` production code is empty, `common` runtime files are gone, and the remaining work is to keep shrinking legacy business-root contract ownership (`auth/files/admin`) before scheduling any larger compatibility-shell deletion round
- `platform.job.api.BackgroundTaskLifecycleApi` no longer exposes `auth.User` in public contracts: owned-task list/get/cancel/retry plus queued-file/queued-task creation now use `userId` scalar inputs, `RuntimeBackgroundTaskLifecycleApi` and `BackgroundTaskV2Controller` were rewired to that seam, and `Task3PlatformSeamArchitectureTest` now also forbids `platform.job.api..` from depending on `auth..` so this cutover is architecture-gated going forward
- top-level `com.yoyuzh.api.v2` has now been removed from runtime source: upload/search/event controllers live under `files.{upload,search}.internal.web`, share controllers and web request DTOs under `files.sharing.internal.web`, task controllers under `platform.job.internal.web`, site ping under `boot.web`, and the v2 protocol envelope/error handling now lives under `boot.web.v2`; `Task9BootSharedInfraArchitectureTest` prevents recreating the top-level `api` root
- `com.yoyuzh.files.tasks` has now also been removed from runtime source: background-task runtime code lives under `platform.job.internal.application`, the runtime task entity lives under `platform.job.internal.domain`, and its repository lives under `platform.job.internal.infra`; the old `files/.DS_Store` stray file was removed during the same tree cleanup
- `com.yoyuzh.files.policy` has now also been removed from runtime source: storage-policy runtime service lives under `platform.storage.internal.application`, the storage-policy JPA entity under `platform.storage.internal.domain`, and its repository under `platform.storage.internal.infra`; `DefaultStoragePolicySnapshot` was tightened so `platform.storage.api` stays DTO/value-contract only
- `com.yoyuzh.auth` has now also shed its legacy DTO/controller, refresh/invite persistence, and shared security-helper surface: those entry classes live under `identity.access.{api,internal.web,internal.domain,internal.infra}` and `boot.security`, `Task2IdentityAccessArchitectureTest` plus `Task9BootSharedInfraArchitectureTest` block recreating the old roots, and the remaining `auth` package is now mostly compatibility services plus user-centric entities that still need later scalar-contract cleanup
- after the latest API/files/task-root cleanup, full live-backend regression is green again (`cd backend && mvn test`, `561` tests)

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
