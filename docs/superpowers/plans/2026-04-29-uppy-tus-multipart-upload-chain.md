# Uppy Tus Multipart Upload Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the main `Files` page upload chain so the frontend uses `Uppy`, S3-compatible and OSS-backed policies use multipart upload, local and WebDAV-backed policies use Tus, MySQL persists upload sessions, and Redis holds high-frequency runtime progress.

**Architecture:** Keep `files.upload` as the ingress control plane and preserve the existing ownership split: `platform.storage` decides strategy, `files.upload` owns sessions, `files.workspace` owns final node creation, and `files.content` owns stored blob truth. Extend the current v2 upload-session flow instead of inventing a second upload API: multipart-backed policies keep the existing session/create-prepare-record-complete shape, while Tus-backed policies expose a session-scoped Tus endpoint plus completion handoff into the existing upload completion seam.

**Tech Stack:** Spring Boot 3.3 / Java 17 / JPA / Redis / tus-java-server / AWS S3 SDK / Aliyun OSS Java SDK / Sardine WebDAV client / React / TypeScript / Uppy.

---

## Scope

In scope:

- `frontend/src/pages/Files.tsx` main upload entry
- `frontend/src/hooks/useUploadQueue.ts` and upload task UI components
- `backend/src/main/java/com/yoyuzh/files/upload/**`
- `backend/src/main/java/com/yoyuzh/platform/storage/**`
- `backend/src/main/java/com/yoyuzh/files/content/**` storage adapters

Out of scope for this pass:

- avatar upload
- transfer/offline upload
- legacy `/api/files/upload/**` removal
- admin UI redesign for storage policy editing

## File Structure

### Backend

- Modify: `backend/pom.xml`
  - Add Tus and storage-adapter dependencies.
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/api/StoragePolicyType.java`
  - Add `OSS_SDK` and `WEBDAV`.
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/api/StorageRuntimeProperties.java`
  - Add runtime config contracts for OSS and WebDAV.
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/internal/infra/FileStorageProperties.java`
  - Bind new runtime properties.
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/internal/application/StoragePolicyService.java`
  - Seed correct capabilities and default policy behavior.
- Modify: `backend/src/main/java/com/yoyuzh/files/content/api/FileContentStorage.java`
  - Expose Tus-finish handoff and provider capability methods without breaking multipart flows.
- Modify: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/DefaultContentStorageFactory.java`
  - Resolve `LOCAL`, `S3_COMPATIBLE`, `OSS_SDK`, `WEBDAV` adapters.
- Create: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/OssSdkFileContentStorage.java`
  - Aliyun OSS adapter.
- Create: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/WebDavFileContentStorage.java`
  - WebDAV adapter.
- Create: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionTusController.java`
  - Session-scoped Tus endpoint.
- Create: `backend/src/main/java/com/yoyuzh/files/upload/internal/application/UploadSessionTusService.java`
  - Tus upload service wrapper, metadata binding, completion handoff.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
  - Emit Tus strategy metadata for Tus-backed sessions and accept Tus completion.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Controller.java`
  - Surface Tus strategy fields and complete/cancel hooks.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Response.java`
  - Add richer strategy payload.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2StrategyResponse.java`
  - Add Tus URL/headers/resume fields.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/RedisUploadSessionRuntimeStateService.java`
  - Track Tus offsets/phases in Redis.
- Test: `backend/src/test/java/com/yoyuzh/platform/storage/internal/application/StoragePolicyServiceTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/upload/UploadSessionServiceTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2ControllerTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionTusControllerTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/content/internal/infra/storage/OssSdkFileContentStorageTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/content/internal/infra/storage/WebDavFileContentStorageTest.java`

### Frontend

- Modify: `frontend/package.json`
  - Add `@uppy/core`, `@uppy/dashboard`, `@uppy/react`, `@uppy/tus`, `@uppy/aws-s3`.
- Modify: `frontend/src/api/types.ts`
  - Add upload-session and strategy types.
- Modify: `frontend/src/lib/files.ts`
  - Add upload-session create/read/complete/cancel helpers and multipart/Tus-specific calls.
- Modify: `frontend/src/hooks/useUploadQueue.ts`
  - Replace raw per-file `POST /files/upload` with `Uppy`-driven session orchestration.
- Modify: `frontend/src/components/files/UploadTaskPanel.tsx`
  - Show Uppy/Tus/multipart progress and resume states.
- Modify: `frontend/src/pages/Files.tsx`
  - Mount the Uppy-based queue instead of the current direct uploader.

## Task 1: Expand Storage Strategy Model

**Files:**

- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/api/StoragePolicyType.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/api/StorageRuntimeProperties.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/internal/infra/FileStorageProperties.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/storage/internal/application/StoragePolicyService.java`
- Test: `backend/src/test/java/com/yoyuzh/platform/storage/internal/application/StoragePolicyServiceTest.java`

- [ ] Add `OSS_SDK` and `WEBDAV` storage policy types and runtime property groups.
- [ ] Update default-policy capability mapping so `S3_COMPATIBLE` and `OSS_SDK` resolve to direct multipart upload while `LOCAL` and `WEBDAV` resolve to Tus-backed resumable upload.
- [ ] Extend storage policy tests to assert the capability matrix and upload-mode resolution for all four providers.
- [ ] Run: `cd backend && mvn test -Dtest=StoragePolicyServiceTest,RuntimeUploadModePolicyTest`

## Task 2: Add OSS SDK And WebDAV Content Adapters

**Files:**

- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/yoyuzh/files/content/api/FileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/DefaultContentStorageFactory.java`
- Create: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/OssSdkFileContentStorage.java`
- Create: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/WebDavFileContentStorage.java`
- Create: `backend/src/test/java/com/yoyuzh/files/content/internal/infra/storage/OssSdkFileContentStorageTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/content/internal/infra/storage/WebDavFileContentStorageTest.java`

- [ ] Add `com.aliyun.oss:aliyun-sdk-oss` and a WebDAV client dependency, keeping the existing AWS SDK path untouched for `S3_COMPATIBLE`.
- [ ] Implement an OSS adapter that supports create multipart upload, presigned part preparation, complete multipart upload, blob read/delete, and direct download URL behavior aligned with the existing `PreparedUpload` contract.
- [ ] Implement a WebDAV adapter that stores completed Tus payloads to WebDAV paths and supports blob read/delete/download operations needed by current file APIs.
- [ ] Update the content-storage factory to dispatch by runtime provider instead of the current `s3`/fallback split.
- [ ] Run: `cd backend && mvn test -Dtest=OssSdkFileContentStorageTest,WebDavFileContentStorageTest,S3FileContentStorageTest`

## Task 3: Add Tus Upload Ingress For Local And WebDAV Sessions

**Files:**

- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/RedisUploadSessionRuntimeStateService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Controller.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Response.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2StrategyResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/upload/internal/application/UploadSessionTusService.java`
- Create: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionTusController.java`
- Create: `backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionTusControllerTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/upload/UploadSessionServiceTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2ControllerTest.java`

- [ ] Add `me.desair.tus:tus-java-server` and wrap it in a session-owned controller that authenticates the caller, binds the Tus upload to an existing `UploadSession`, and stores the runtime offset into Redis.
- [ ] Extend the upload-session strategy payload so Tus-backed sessions expose the Tus endpoint URL, required headers, and completion route without inventing a second session model.
- [ ] On Tus completion, hand the assembled blob back into the existing `UploadCompletionApi` so final workspace node creation and content registration stay in their owner modules.
- [ ] Keep `completeOwnedSession(...)` idempotent for both multipart and Tus-backed sessions.
- [ ] Run: `cd backend && mvn test -Dtest=UploadSessionServiceTest,UploadSessionV2ControllerTest,UploadSessionTusControllerTest`

## Task 4: Switch Files Upload Queue To Uppy

**Files:**

- Modify: `frontend/package.json`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/lib/files.ts`
- Modify: `frontend/src/hooks/useUploadQueue.ts`
- Modify: `frontend/src/components/files/UploadTaskPanel.tsx`
- Modify: `frontend/src/pages/Files.tsx`

- [ ] Add the minimal Uppy packages needed for this repo: core, dashboard UI, Tus uploader, and AWS S3 multipart uploader.
- [ ] Model the backend upload-session response in `frontend/src/api/types.ts`, including multipart and Tus strategy fields.
- [ ] Replace the current `uploadFile(...)` queue path with a session-first runner:
  - create upload session
  - inspect strategy
  - for multipart strategies, let Uppy upload through the returned part URLs and then call session complete
  - for Tus strategies, let Uppy `@uppy/tus` talk to the session Tus endpoint and then call session complete
- [ ] Preserve the existing queue panel, cancellation affordances, and `uploadConcurrency` setting by mapping them onto Uppy limits instead of deleting the current UX.
- [ ] Run: `cd frontend && npm run lint`

## Task 5: End-To-End Verification

**Files:**

- No source changes expected unless verification exposes a bug.

- [ ] Run: `cd backend && mvn test`
- [ ] Run: `cd frontend && npm run lint`
- [ ] Run: `cd frontend && npm run build`
- [ ] Manual verification:
  - local/default policy upload from `Files`
  - S3-compatible policy upload from `Files`
  - cancel one upload and cancel queue
  - refresh page during Tus upload and confirm resumable behavior

## Self-Review

- This plan covers the requested protocol matrix, adapter matrix, state split, and Uppy frontend switch.
- The first implementation pass intentionally limits scope to the `Files` upload surface so the upload-architecture change lands without dragging avatar and transfer uploads into the same migration.
