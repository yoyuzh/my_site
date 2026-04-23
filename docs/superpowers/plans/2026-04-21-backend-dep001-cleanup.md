# Backend DEP-001 Cleanup Plan

> Use with `superpowers:executing-plans`. This plan continues the backend review remediation after the first pass fixed the direct review examples but left the broader cross-module `internal` dependency debt.

## Goal

Eliminate production cross-module imports of another module's `internal` package in `backend/src/main/java`, then enforce the rule with ArchUnit so new code cannot regress.

The current incomplete state after the first remediation pass:

- Identity public API leak was fixed by replacing exposed `User` / `UserRole` with API DTOs.
- Transfer's flagged runtime import/session/quota paths were moved behind API seams.
- Capacity usage now reads workspace storage usage through an API port.
- `BackgroundTaskV2Controller` no longer maps the `BackgroundTask` domain entity directly.
- `mvn test` passed with 586 tests.
- A production scan still finds 98 cross-module `*.internal.*` imports, mostly from historical JPA relationships and boot/security wiring.

## Non-Goals

- Do not touch frontend.
- Do not update `backend-next/archtecture.md` or `docs/architecture.md`.
- Do not add compatibility branches, duplicate paths, or fallback behavior.
- Do not enforce a global ArchUnit rule until the production scan is clean.

## Validation Commands

Run from the repository root unless noted:

```bash
python3 scripts/check-backend-internal-deps.py
cd backend && mvn -q -DskipTests compile
cd backend && mvn -Dtest=Task2IdentityAccessArchitectureTest,Task3PlatformSeamArchitectureTest,Task7TransferArchitectureTest test
cd backend && mvn test
```

If `scripts/check-backend-internal-deps.py` does not exist yet, create it in Task 1.

## Task 1: Add a Reproducible Dependency Scan

Files:

- Create `scripts/check-backend-internal-deps.py`
- Modify or create an architecture test only after the scan is clean

Steps:

- [x] Create a small scanner that reports production imports where source module and target module differ and target package contains `.internal.`.
- [x] Exclude tests and generated/target output.
- [x] Print count plus file, line, source module, target module, and import.
- [x] Run the scanner and use its output as the working backlog.

Verification:

```bash
python3 scripts/check-backend-internal-deps.py
```

Expected for now: non-zero output matching the remaining debt.

## Task 2: Remove Boot-Owned Metrics and Configuration Internal Dependencies

Files:

- Modify `backend/src/main/java/com/yoyuzh/boot/...`
- Add API ports in `ops.admin.api`, `platform.storage.api`, and `app.android.api` as needed
- Add runtime implementations inside owning modules

Steps:

- [x] Move configuration-property exposure used by boot to owning-module API contracts or boot-local configuration.
- [x] Replace security request metrics dependencies on `AdminMetricsService` with an admin API port.
- [x] Keep token and authentication behavior unchanged.
- [x] Run focused security and boot tests.

Note: `CustomUserDetailsService` cannot be fully cleaned in this task because existing controllers and services still pass `identity.access.internal.domain.User` through public request flows. That dependency is intentionally moved to Task 3, where all current-user call paths are converted together to `userId` / API snapshots.

Verification:

```bash
cd backend && mvn -Dtest=SecurityConfigTest,JwtAuthenticationFilterTest,JwtTokenProviderTest test
python3 ../scripts/check-backend-internal-deps.py
```

## Task 3: Replace Cross-Module Identity Entity References with IDs/API Snapshots

Files:

- Modify `boot.security.CustomUserDetailsService`
- Modify `files.workspace`, `files.content`, `files.upload`, `files.sharing`, `files.search`, `platform.job`
- Use `identity.access.api.IdentityUserDirectoryApi` / stable API DTOs for display data and ownership checks

Steps:

- [x] Add a boot-safe current-user API shape if `IdentityUserSnapshot` is not sufficient for authentication/session checks.
- [x] Replace controller calls to `loadDomainUser(...)` with `loadUserId(...)` or API snapshots.
- [ ] Replace non-identity module entity fields of type `User` with scalar `userId`, `ownerId`, or `createdByUserId`.
- [ ] Update repositories and queries to use scalar IDs.
- [ ] Use identity API snapshots only when username/email/display fields are needed.
- [ ] Update tests to build domain objects through scalar IDs.

Verification:

```bash
cd backend && mvn -Dtest=AuthServiceTest,FileServiceTest,UploadSessionServiceTest,RuntimeSharingApiTest,BackgroundTaskServiceTest test
python3 ../scripts/check-backend-internal-deps.py
```

## Task 4: Separate Workspace and Content Persistence Boundaries

Files:

- Modify `files.workspace.internal.domain.StoredFile`
- Modify `files.content.internal.domain.FileEntity` and `StoredFileEntity`
- Add or extend `files.content.api` and `files.workspace.api` contracts as needed

Steps:

- [x] Replace `StoredFile` references to `FileBlob` and `FileEntity` with `blobId` and `primaryEntityId`.
- [x] Replace `StoredFileEntity` reference to `StoredFile` with `storedFileId`.
- [x] Move content metadata lookup and relation creation behind `files.content.api`.
- [x] Move workspace node lookup needed by content behind `files.workspace.api`.
- [x] Update file copy/import/share/download flows to use API references.

Verification:

```bash
cd backend && mvn -Dtest=FileServiceTest,RuntimeContentAssetApiTest,RuntimeContentRegistrationApiTest,RuntimeSharingApiTest test
python3 ../scripts/check-backend-internal-deps.py
```

## Task 5: Decouple Platform Job and Storage/Admin Runtime Paths

Files:

- Modify `platform.job.internal.application.*Handler`
- Modify storage admin/migration services
- Add API commands/views for workspace/content operations needed by jobs

Steps:

- [x] Replace job handlers' direct access to workspace/content/identity internals with API calls.
- [x] Replace platform storage admin inspection dependencies on content internals with content API queries.
- [x] Replace admin settings/config access to storage internals with platform storage API contracts.

Verification:

```bash
cd backend && mvn -Dtest=BackgroundTaskServiceTest,MediaMetadataBackgroundTaskHandlerTest,AdminConfigSnapshotServiceTest,StoragePolicyServiceTest test
python3 ../scripts/check-backend-internal-deps.py
```

## Task 6: Enforce DEP-001 Globally

Files:

- Modify `backend/src/test/java/com/yoyuzh/architecture/...`

Steps:

- [x] Add a global ArchUnit rule: production classes must not depend on a different module's `internal` package.
- [x] Allow same-module internal layering.
- [x] Keep explicit existing focused architecture tests for review findings.
- [x] Ensure the scanner reports zero production violations.

Verification:

```bash
python3 scripts/check-backend-internal-deps.py
cd backend && mvn -Dtest=*ArchitectureTest test
cd backend && mvn test
```

## Execution Notes

- Work task-by-task. Do not proceed to the next task while compile or focused tests fail.
- If a task reveals a missing API contract, add the narrowest owning-module API needed for the caller.
- Prefer scalar IDs across module boundaries. Do not expose JPA entities through `api`.
- Keep public API DTOs immutable records where possible.
