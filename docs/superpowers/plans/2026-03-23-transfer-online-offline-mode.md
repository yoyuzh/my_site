# Transfer Online Offline Mode Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Distinguish quick transfer online and offline sending so online stays one-time P2P, while offline persists files for 7 days in storage and can be received repeatedly.

**Architecture:** Keep the current in-memory WebRTC signaling flow for online transfers and add a persistent offline transfer path in the backend. Offline transfers store file metadata plus storage references, expose repeatable public download/import behavior, and let the frontend branch between P2P receive and offline download based on the transfer mode returned by the API.

**Tech Stack:** Spring Boot 3.3.8, Java 17, JPA/H2 tests, Vite 6, React 19, TypeScript, existing OSS/local storage abstraction

---

### Task 1: Plan the transfer domain split

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/transfer/CreateTransferSessionRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/transfer/TransferSessionResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/transfer/LookupTransferSessionResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/TransferMode.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/OfflineTransfer*`

- [ ] **Step 1: Write the failing backend tests for mode-aware session responses**
- [ ] **Step 2: Run `cd backend && mvn test -Dtest=TransferSessionTest,TransferControllerIntegrationTest` and verify the new assertions fail for missing mode-aware behavior**
- [ ] **Step 3: Add the minimal transfer mode types, request fields, and response fields**
- [ ] **Step 4: Run the same Maven test command and verify the new mode assertions pass**

### Task 2: Add offline transfer storage and repeatable receive flow

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/FileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/LocalFileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/storage/OssFileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`
- Modify: `backend/src/main/java/com/yoyuzh/transfer/TransferService.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/OfflineTransfer*.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/TransferControllerIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests for offline create, upload, lookup, repeatable receive, and 7-day expiry metadata**
- [ ] **Step 2: Run `cd backend && mvn test -Dtest=TransferControllerIntegrationTest` and verify the offline scenarios fail for the expected missing endpoints/fields**
- [ ] **Step 3: Implement the minimal persistent offline transfer entities, repositories, service methods, and public download/import endpoints**
- [ ] **Step 4: Run `cd backend && mvn test -Dtest=TransferControllerIntegrationTest` and verify the offline scenarios pass**

### Task 3: Keep online transfers one-time

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/transfer/TransferSession.java`
- Modify: `backend/src/main/java/com/yoyuzh/transfer/TransferService.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/TransferSessionTest.java`

- [ ] **Step 1: Write the failing test that a second online receiver cannot join/re-receive**
- [ ] **Step 2: Run `cd backend && mvn test -Dtest=TransferSessionTest` and verify it fails for the current reusable online session behavior**
- [ ] **Step 3: Implement the minimal online single-receive guard**
- [ ] **Step 4: Run `cd backend && mvn test -Dtest=TransferSessionTest` and verify it passes**

### Task 4: Add frontend mode-aware API helpers and state

**Files:**
- Modify: `front/src/lib/types.ts`
- Modify: `front/src/lib/transfer.ts`
- Modify: `front/src/pages/transfer-state.ts`
- Test: `front/src/pages/transfer-state.test.ts`

- [ ] **Step 1: Write failing frontend tests for transfer mode options, request payloads, and helper text/state**
- [ ] **Step 2: Run `cd front && npm run test` and verify the new mode-aware tests fail**
- [ ] **Step 3: Implement the minimal frontend types and helpers for online/offline mode branching**
- [ ] **Step 4: Run `cd front && npm run test` and verify the helper tests pass**

### Task 5: Update the transfer send and receive pages

**Files:**
- Modify: `front/src/pages/Transfer.tsx`
- Modify: `front/src/pages/TransferReceive.tsx`
- Modify: `front/src/lib/transfer.ts`
- Test: `front/src/pages/transfer-state.test.ts`

- [ ] **Step 1: Add failing tests for send-mode selection and receive-mode branching where possible in existing frontend test files**
- [ ] **Step 2: Run `cd front && npm run test` and verify the new assertions fail**
- [ ] **Step 3: Implement the minimal UI and flow split so online stays P2P and offline uses backend-backed receive/download behavior**
- [ ] **Step 4: Run `cd front && npm run test` and verify the mode flow tests pass**

### Task 6: Verify and release

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/api-reference.md`

- [ ] **Step 1: Run `cd backend && mvn test`**
- [ ] **Step 2: Run `cd front && npm run test`**
- [ ] **Step 3: Run `cd front && npm run lint`**
- [ ] **Step 4: Run `cd front && npm run build`**
- [ ] **Step 5: Run `cd backend && mvn package`**
- [ ] **Step 6: Update the docs to describe online/offline transfer behavior and offline endpoints**
- [ ] **Step 7: Publish frontend with `node scripts/deploy-front-oss.mjs` from repo root**
- [ ] **Step 8: Upload `backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar` to the real server and restart the existing backend service**
