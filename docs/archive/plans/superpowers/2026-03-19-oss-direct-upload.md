# OSS Direct Upload Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move file content storage to OSS and let the frontend upload/download file bytes directly against OSS while the backend keeps auth and metadata control.

**Architecture:** Introduce a backend storage abstraction with `local` and `oss` implementations, then add signed upload/download APIs so the browser can PUT file bytes to OSS and fetch signed download links. File list, mkdir, rename, and delete stay authenticated through the backend, with OSS-backed rename/delete implemented as object copy/delete plus metadata updates.

**Tech Stack:** Spring Boot 3, React/Vite, Aliyun OSS SDK, existing JWT auth, MySQL metadata tables.

---

### Task 1: Storage Abstraction

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/storage/FileContentStorage.java`
- Create: `backend/src/main/java/com/yoyuzh/files/storage/LocalFileContentStorage.java`
- Create: `backend/src/main/java/com/yoyuzh/files/storage/OssFileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/config/FileStorageProperties.java`
- Modify: `backend/pom.xml`

- [ ] Add nested storage configuration for `local` and `oss`
- [ ] Add a content-storage interface for upload, signed upload URL, signed download URL, rename, and delete
- [ ] Implement local storage compatibility for dev
- [ ] Implement OSS object-key storage for prod

### Task 2: File Service Integration

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileController.java`
- Create: `backend/src/main/java/com/yoyuzh/files/InitiateUploadRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/files/InitiateUploadResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/CompleteUploadRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/files/DownloadUrlResponse.java`

- [ ] Add backend APIs for upload initiation, upload completion, and signed download URLs
- [ ] Route rename/delete/mkdir through the active storage implementation
- [ ] Keep metadata updates transactional

### Task 3: Frontend Direct Transfer

**Files:**
- Modify: `front/src/pages/Files.tsx`
- Modify: `front/src/lib/api.ts`
- Modify: `front/src/lib/types.ts`

- [ ] Replace backend proxy upload with signed direct upload flow
- [ ] Replace streamed backend download with signed URL download flow
- [ ] Preserve current upload progress UI and retry behavior

### Task 4: Tests

**Files:**
- Modify: `backend/src/test/java/com/yoyuzh/files/FileServiceTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/OssFileContentStorageTest.java`
- Modify: `front/src/lib/api.test.ts`

- [ ] Cover service behavior with mocked storage
- [ ] Cover OSS object-key rename/delete behavior
- [ ] Cover frontend direct upload state changes and network retries

### Task 5: Verification

**Files:**
- Modify: `backend/README.md`

- [ ] Run targeted backend tests
- [ ] Run frontend tests, lint, and build
- [ ] Document OSS bucket CORS requirement and any one-time migration follow-up
