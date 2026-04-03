# Shared File Blob Storage Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将网盘文件模型改造成 `StoredFile -> FileBlob` 引用关系，让分享导入与网盘复制复用同一份底层对象，而不是再次写入物理文件。

**Architecture:** 新增独立 `FileBlob` 实体承载真实对象 key、大小和内容类型，`StoredFile` 只保留逻辑目录元数据并引用 `FileBlob`。网盘上传为每个新文件创建新 blob，分享导入和文件复制直接复用 blob；删除时按引用是否归零决定是否删除底层对象。补一个面向旧 `portal_file.storage_name` 数据的一次性回填路径，避免线上旧数据在新模型下失联。

**Tech Stack:** Spring Boot 3.3.8, Spring Data JPA, Java 17, Maven, H2 tests, local filesystem storage, S3-compatible object storage

---

### Task 1: Define Blob Data Model

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/FileBlob.java`
- Create: `backend/src/main/java/com/yoyuzh/files/FileBlobRepository.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFile.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java`

- [ ] **Step 1: Write the failing tests**

Add/update backend tests that expect file copies and share imports to preserve a shared blob id rather than a per-user storage name.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/mac/Documents/my_site/backend && mvn test -Dtest=FileServiceTest,FileShareControllerIntegrationTest`
Expected: FAIL because `StoredFile` does not yet expose blob references and current logic still duplicates file content.

- [ ] **Step 3: Write minimal implementation**

Add `FileBlob` entity/repository, move file-object ownership from `StoredFile.storageName` to `StoredFile.blob`, and add repository helpers for blob reference counting / physical size queries.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/mac/Documents/my_site/backend && mvn test -Dtest=FileServiceTest,FileShareControllerIntegrationTest`
Expected: PASS for data-model expectations.

### Task 2: Refactor File Storage Flow To Blob Keys

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/FileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/LocalFileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/S3FileContentStorage.java`
- Test: `backend/src/test/java/com/yoyuzh/files/FileServiceTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/FileServiceEdgeCaseTest.java`

- [ ] **Step 1: Write the failing tests**

Add/update tests for:
- upload creates a new blob
- share import reuses the source blob and does not store bytes again
- file copy reuses the source blob and does not copy bytes again
- deleting a non-final reference keeps the blob/object alive
- deleting the final reference removes the blob/object

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/mac/Documents/my_site/backend && mvn test -Dtest=FileServiceTest,FileServiceEdgeCaseTest`
Expected: FAIL because current service still calls `readFile/storeImportedFile/copyFile/moveFile/renameFile` for ordinary files.

- [ ] **Step 3: Write minimal implementation**

Refactor file upload/download/import/copy/delete to operate on blob object keys. Keep directory behavior metadata-only. Ensure file rename/move/copy no longer trigger physical object mutations, and deletion only removes the object when the last `StoredFile` reference disappears.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/mac/Documents/my_site/backend && mvn test -Dtest=FileServiceTest,FileServiceEdgeCaseTest`
Expected: PASS.

### Task 3: Backfill Old File Rows Into Blob References

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/FileBlobBackfillService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java`
- Test: `backend/src/test/java/com/yoyuzh/files/FileShareControllerIntegrationTest.java`

- [ ] **Step 1: Write the failing test**

Add an integration-path expectation that persisted file rows still download/import correctly after the blob model change.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/mac/Documents/my_site/backend && mvn test -Dtest=FileShareControllerIntegrationTest`
Expected: FAIL because legacy `portal_file` rows created without blobs can no longer resolve file content.

- [ ] **Step 3: Write minimal implementation**

Add a startup/on-demand backfill that creates `FileBlob` rows for existing non-directory files using their legacy object key and attaches them to `StoredFile` rows with missing blob references.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/mac/Documents/my_site/backend && mvn test -Dtest=FileShareControllerIntegrationTest`
Expected: PASS.

### Task 4: Update Docs And Full Verification

**Files:**
- Modify: `memory.md`
- Modify: `docs/architecture.md`
- Modify: `docs/api-reference.md` (only if API semantics changed)

- [ ] **Step 1: Document the new storage model**

Record that logical file metadata now points to shared blobs, that share import and copy reuse blobs, and that physical keys are global rather than user-path keys.

- [ ] **Step 2: Run full backend verification**

Run: `cd /Users/mac/Documents/my_site/backend && mvn test`
Expected: PASS.

- [ ] **Step 3: Summarize migration requirement**

In the final handoff, call out that old production data must be backfilled to `FileBlob` rows before or during rollout; otherwise pre-existing files cannot resolve blob references under the new model.
