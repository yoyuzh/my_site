# Transfer Module Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the fast-transfer module so the website has one coherent transfer flow, with cleaner frontend boundaries, a cleaner backend session model, and route generation that matches the site router mode.

**Architecture:** Keep the current product behavior of “authenticated sender page + public receiver page + backend signaling only”, but split protocol/state concerns away from route UI. On the frontend, centralize transfer URL building, protocol message helpers, and sender/receiver session orchestration so the pages become thinner. On the backend, split the current monolithic in-memory service into small transfer domain objects while preserving the same HTTP API.

**Tech Stack:** Vite 6, React 19, TypeScript, Spring Boot 3.3, Java 17, node:test, JUnit 5, MockMvc.

---

### Task 1: Lock down route-aware transfer URLs

**Files:**
- Modify: `front/src/pages/transfer-state.test.ts`
- Modify: `front/src/pages/transfer-state.ts`
- Modify: `front/src/App.tsx`

- [ ] **Step 1: Write the failing test**

Add tests asserting:
- browser mode share URL => `https://host/t?session=abc`
- hash mode share URL => `https://host/#/t?session=abc`

- [ ] **Step 2: Run test to verify it fails**

Run: `cd front && npm run test`

- [ ] **Step 3: Write minimal implementation**

Introduce a router-mode-aware URL builder and update the app router to respect `VITE_ROUTER_MODE`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd front && npm run test`

### Task 2: Extract shared frontend transfer protocol and session helpers

**Files:**
- Create: `front/src/lib/transfer-protocol.ts`
- Create: `front/src/lib/transfer-runtime.ts`
- Modify: `front/src/lib/transfer.ts`
- Modify: `front/src/pages/Transfer.tsx`
- Modify: `front/src/pages/TransferReceive.tsx`

- [ ] **Step 1: Write the failing test**

Add tests for pure protocol helpers:
- sender meta message encoding
- receiver payload parsing
- progress/URL helpers that no longer live inside page components

- [ ] **Step 2: Run test to verify it fails**

Run: `cd front && npm run test`

- [ ] **Step 3: Write minimal implementation**

Move WebRTC protocol constants, message parsing/encoding, and repeated session setup logic out of page components. Keep pages focused on route UI and user actions.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd front && npm run test`

### Task 3: Thin down backend transfer service

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/transfer/TransferRole.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/TransferSession.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/TransferSessionStore.java`
- Modify: `backend/src/main/java/com/yoyuzh/transfer/TransferService.java`
- Add/Modify Test: `backend/src/test/java/com/yoyuzh/transfer/TransferControllerIntegrationTest.java`

- [ ] **Step 1: Write the failing test**

Add focused tests that lock current session behavior:
- pickup code validation
- receiver join only emits one `peer-joined`
- signals route to the opposite queue

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test`

- [ ] **Step 3: Write minimal implementation**

Extract session state and store responsibilities from `TransferService`, leaving `TransferService` as orchestration only.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test`

### Task 4: Reconnect the module cleanly to the site

**Files:**
- Modify: `front/src/pages/Overview.tsx`
- Modify: `front/src/components/layout/Layout.tsx`
- Modify: `scripts/oss-deploy-lib.mjs`
- Modify: `scripts/oss-deploy-lib.test.mjs`

- [ ] **Step 1: Verify transfer entry points still match the refactored routes**

Confirm overview CTA, sidebar nav, and public receiver route all align on the same URL helpers.

- [ ] **Step 2: Verify the deployment aliases still cover the public transfer route**

Run: `node scripts/oss-deploy-lib.test.mjs`

- [ ] **Step 3: Apply any minimal cleanup**

Remove duplicated hardcoded route strings if they remain.

### Task 5: Full verification

**Files:**
- No code changes required unless failures appear

- [ ] **Step 1: Run frontend tests**

Run: `cd front && npm run test`

- [ ] **Step 2: Run frontend typecheck**

Run: `cd front && npm run lint`

- [ ] **Step 3: Run frontend build**

Run: `cd front && npm run build`

- [ ] **Step 4: Run backend tests**

Run: `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn test`

- [ ] **Step 5: Run backend package**

Run: `cd backend && /Users/mac/.local/tools/apache-maven-3.9.11/bin/mvn package`
