# File Share And Transfer Save Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users create URL-based netdisk share links that another logged-in user can import into their own netdisk, and let transfer receivers save received files directly into their netdisk.

**Architecture:** Add a small share-link domain under `backend/src/main/java/com/yoyuzh/files` so the backend can issue secret share tokens, expose share metadata, and import the shared file into the recipient’s storage without routing the payload through the browser. On the frontend, add share actions to the Files page, a public `/share/:token` import page, and a reusable netdisk-upload helper that Transfer receive actions can call to persist downloaded blobs into the current user’s storage.

**Tech Stack:** Spring Boot 3.3.8 + Java 17 + JPA, React 19 + Vite + TypeScript, existing file storage abstraction, existing frontend Node test runner and Maven tests.

---

### Task 1: Define Backend Share-Link API Contract

**Files:**
- Modify: `backend/src/test/java/com/yoyuzh/files/FileServiceTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/FileShareControllerIntegrationTest.java`

- [ ] **Step 1: Write failing tests for creating a share link, reading share metadata, and importing a shared file into another user’s netdisk**
- [ ] **Step 2: Run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test` to verify the new tests fail**
- [ ] **Step 3: Implement the minimal backend API surface to satisfy the tests**
- [ ] **Step 4: Re-run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test`**

### Task 2: Add Backend Share-Link Persistence And Import Logic

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/FileShareLink.java`
- Create: `backend/src/main/java/com/yoyuzh/files/FileShareLinkRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/files/CreateFileShareLinkResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/FileShareDetailsResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/ImportSharedFileRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileController.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`

- [ ] **Step 1: Add the share-link entity/repository and DTOs**
- [ ] **Step 2: Extend `FileService` with share creation, share lookup, and recipient import logic**
- [ ] **Step 3: Expose authenticated create/import endpoints and a public share-details endpoint in `FileController`**
- [ ] **Step 4: Keep directory handling explicit; only add behavior required by the tests**

### Task 3: Add Frontend Share-Link Helpers And Tests

**Files:**
- Create: `front/src/lib/file-share.ts`
- Create: `front/src/lib/file-share.test.ts`
- Modify: `front/src/lib/types.ts`

- [ ] **Step 1: Write failing frontend helper tests for share-link building/parsing and import payload helpers**
- [ ] **Step 2: Run `cd front && npm run test` to verify failure**
- [ ] **Step 3: Implement minimal share helper wrappers against the backend API**
- [ ] **Step 4: Re-run `cd front && npm run test`**

### Task 4: Add Public Share Import Page

**Files:**
- Create: `front/src/pages/FileShare.tsx`
- Modify: `front/src/App.tsx`
- Modify: `front/src/pages/Login.tsx`

- [ ] **Step 1: Add failing tests for any pure helper logic used by the share page and login redirect flow**
- [ ] **Step 2: Run `cd front && npm run test` to verify failure**
- [ ] **Step 3: Implement `/share/:token`, showing share metadata publicly and allowing authenticated users to import into their netdisk**
- [ ] **Step 4: Add login redirect-back handling only as needed for this route**

### Task 5: Add Share Actions To Netdisk And Save-To-Netdisk For Transfer

**Files:**
- Modify: `front/src/pages/Files.tsx`
- Create: `front/src/lib/netdisk-upload.ts`
- Create: `front/src/lib/netdisk-upload.test.ts`
- Modify: `front/src/pages/TransferReceive.tsx`

- [ ] **Step 1: Write failing helper tests for saving a browser `File` into netdisk**
- [ ] **Step 2: Run `cd front && npm run test` to verify failure**
- [ ] **Step 3: Add a share action in the Files page that creates/copies a share URL**
- [ ] **Step 4: Add “存入网盘” actions in transfer receive for completed files**
- [ ] **Step 5: Re-run `cd front && npm run test`**

### Task 6: Full Verification

**Files:**
- Modify only if validation reveals defects

- [ ] **Step 1: Run `cd front && npm run test`**
- [ ] **Step 2: Run `cd front && npm run lint`**
- [ ] **Step 3: Run `cd front && npm run build`**
- [ ] **Step 4: Run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test`**
- [ ] **Step 5: Run `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn package`**
- [ ] **Step 6: Deploy frontend with `node scripts/deploy-front-oss.mjs` only after all checks pass**
