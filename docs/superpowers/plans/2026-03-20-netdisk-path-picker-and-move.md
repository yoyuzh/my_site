# Netdisk Path Picker And Move Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users choose a destination path in a centered modal when saving files into the netdisk, and add a real move-file/move-folder capability inside the netdisk.

**Architecture:** Add a backend move endpoint in the existing `files` domain so both local storage and OSS-backed storage can relocate files safely. On the frontend, introduce a reusable netdisk path picker modal that can browse existing folders and reuse it from transfer save flows, share import flows, and the new move action in the Files page.

**Tech Stack:** Spring Boot 3.3.8 + Java 17 + JPA, React 19 + Vite + TypeScript, Tailwind CSS v4, existing file storage abstraction and Node test runner.

---

### Task 1: Add Backend Move API Contract

**Files:**
- Modify: `backend/src/test/java/com/yoyuzh/files/FileServiceTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/FileShareControllerIntegrationTest.java`

- [ ] **Step 1: Write failing backend tests for moving a file to another directory and moving a folder while preserving descendants**
- [ ] **Step 2: Run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test` to verify the new tests fail**
- [ ] **Step 3: Implement the minimal backend API surface to satisfy the tests**
- [ ] **Step 4: Re-run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test`**

### Task 2: Implement Backend Move Logic

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/MoveFileRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileController.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/FileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/LocalFileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/OssFileContentStorage.java`

- [ ] **Step 1: Add a move request DTO and controller endpoint for `PATCH /api/files/{fileId}/move`**
- [ ] **Step 2: Extend the repository and service with destination-directory validation, duplicate-name protection, and self/descendant move guards**
- [ ] **Step 3: Add storage-layer support for moving a file across directories while reusing existing directory move behavior**
- [ ] **Step 4: Keep the implementation narrow to existing netdisk semantics: move into an existing directory only**

### Task 3: Add Frontend Path Selection Helpers And Tests

**Files:**
- Create: `front/src/lib/netdisk-paths.ts`
- Create: `front/src/lib/netdisk-paths.test.ts`
- Modify: `front/src/lib/netdisk-upload.ts`
- Modify: `front/src/lib/netdisk-upload.test.ts`

- [ ] **Step 1: Write failing helper tests for netdisk path splitting/joining and default transfer save paths**
- [ ] **Step 2: Run `cd front && npm run test` to verify failure**
- [ ] **Step 3: Implement minimal shared path helpers for the picker modal and save flows**
- [ ] **Step 4: Re-run `cd front && npm run test`**

### Task 4: Add Reusable Netdisk Path Picker Modal

**Files:**
- Create: `front/src/components/ui/NetdiskPathPickerModal.tsx`
- Modify: `front/src/pages/FileShare.tsx`
- Modify: `front/src/pages/TransferReceive.tsx`

- [ ] **Step 1: Replace inline save/import path entry with a centered modal path picker that browses existing folders**
- [ ] **Step 2: Reuse the same modal for transfer “存入网盘” and share import so the interaction stays consistent**
- [ ] **Step 3: Keep browsing lightweight by listing one directory level at a time and filtering to folders only**

### Task 5: Add Netdisk Move UI

**Files:**
- Modify: `front/src/pages/Files.tsx`
- Create only if needed: `front/src/lib/file-move.ts`

- [ ] **Step 1: Add a move action to the file list menu and detail sidebar**
- [ ] **Step 2: Reuse the path picker modal to choose the destination directory**
- [ ] **Step 3: Call the backend move endpoint, refresh the current listing, and clear or sync selection as needed**
- [ ] **Step 4: Surface move errors in the modal instead of failing silently**

### Task 6: Full Verification

**Files:**
- Modify only if validation reveals defects

- [ ] **Step 1: Run `cd front && npm run test`**
- [ ] **Step 2: Run `cd front && npm run lint`**
- [ ] **Step 3: Run `cd front && npm run build`**
- [ ] **Step 4: Run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test`**
- [ ] **Step 5: Run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn package`**
