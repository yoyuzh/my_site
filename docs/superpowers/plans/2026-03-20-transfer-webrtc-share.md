# Transfer WebRTC Share Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current mock transfer page into a real QR-to-webpage sharing flow where a sender opens `/transfer`, the receiver opens a public share URL, and the browsers exchange files over WebRTC P2P.

**Architecture:** Add a minimal backend signaling layer under `backend/src/main/java/com/yoyuzh/transfer` using in-memory session storage with short TTL. Keep the sender workspace inside the authenticated portal, add a public receiver route in the frontend, and exchange SDP / ICE over authenticated-or-public HTTP endpoints while the actual file bytes move through `RTCDataChannel`.

**Tech Stack:** React 19 + Vite + TypeScript, Spring Boot 3.3.8 + Java 17, WebRTC `RTCPeerConnection`, existing OSS deploy script, Maven tests, Node test runner.

---

### Task 1: Define Share URL And Pure Frontend Protocol Helpers

**Files:**
- Modify: `front/src/pages/transfer-state.ts`
- Modify: `front/src/pages/transfer-state.test.ts`
- Modify: `front/src/App.tsx`

- [ ] **Step 1: Write failing tests for share URL and protocol helpers**
- [ ] **Step 2: Run `cd front && npm run test` to verify the new tests fail**
- [ ] **Step 3: Implement minimal helpers for public share URLs, protocol message typing, and code parsing**
- [ ] **Step 4: Run `cd front && npm run test` to verify the helpers pass**

### Task 2: Add Backend Signaling Session APIs

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/transfer/TransferController.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/TransferService.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/TransferSessionStore.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/*.java` DTOs for create/join/poll/post signal
- Modify: `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/TransferControllerIntegrationTest.java`
- Test: `backend/src/test/java/com/yoyuzh/config/SecurityConfigTest.java`

- [ ] **Step 1: Write failing backend integration tests for session creation, public join, offer/answer exchange, ICE polling, and access rules**
- [ ] **Step 2: Run `cd backend && mvn test` to verify the transfer tests fail for the expected missing endpoints**
- [ ] **Step 3: Implement the minimal in-memory signaling service and public `/api/transfer/**` endpoints**
- [ ] **Step 4: Run `cd backend && mvn test` to verify backend green**

### Task 3: Replace Mock Transfer UI With Sender Workspace

**Files:**
- Modify: `front/src/pages/Transfer.tsx`
- Create: `front/src/lib/transfer-client.ts` if needed for request wrappers
- Test: `front/src/pages/transfer-state.test.ts`

- [ ] **Step 1: Add failing tests for sender-side state transitions that now depend on created share sessions instead of mock codes**
- [ ] **Step 2: Run `cd front && npm run test` to verify failure**
- [ ] **Step 3: Implement sender-side session creation, QR/share URL generation, and WebRTC offer / data channel sending**
- [ ] **Step 4: Run `cd front && npm run test` to verify green**

### Task 4: Add Public Receiver Page

**Files:**
- Create: `front/src/pages/TransferReceive.tsx`
- Modify: `front/src/App.tsx`
- Modify: `front/src/pages/Transfer.tsx`

- [ ] **Step 1: Add failing tests for public share route parsing or receiver helper logic**
- [ ] **Step 2: Run `cd front && npm run test` to verify failure**
- [ ] **Step 3: Implement the public receiver page, session join flow, answer/ICE exchange, and browser download assembly**
- [ ] **Step 4: Run `cd front && npm run test` to verify green**

### Task 5: Make OSS Publish Recognize Public Share Routes

**Files:**
- Modify: `scripts/oss-deploy-lib.mjs`
- Modify: `scripts/oss-deploy-lib.test.mjs`

- [ ] **Step 1: Write failing tests for new SPA aliases such as `t` or `transfer/receive`**
- [ ] **Step 2: Run `node scripts/oss-deploy-lib.test.mjs` only if already used elsewhere; otherwise verify through existing frontend build and test coverage**
- [ ] **Step 3: Implement the minimal alias updates**
- [ ] **Step 4: Re-run the relevant checked-in verification command**

### Task 6: Full Verification And Release

**Files:**
- Modify only if verification reveals issues

- [ ] **Step 1: Run `cd front && npm run test`**
- [ ] **Step 2: Run `cd front && npm run lint`**
- [ ] **Step 3: Run `cd front && npm run build`**
- [ ] **Step 4: Run `cd backend && mvn test`**
- [ ] **Step 5: Run `cd backend && mvn package`**
- [ ] **Step 6: Deploy frontend with `node scripts/deploy-front-oss.mjs`**
- [ ] **Step 7: Deploy backend jar to the discovered production host and restart `my-site-api.service` using the real server procedure**
