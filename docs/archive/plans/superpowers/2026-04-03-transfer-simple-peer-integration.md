# Transfer Simple Peer Integration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-rolled online transfer WebRTC peer wiring with `simple-peer` while preserving the current pickup-code flow, backend signaling APIs, and offline transfer mode.

**Architecture:** Keep the existing product boundaries: Spring Boot remains a dumb signaling relay and session store, while the React frontend owns online-transfer peer creation and file streaming. Instead of manually managing `RTCPeerConnection`, ICE candidates, and SDP state across sender/receiver pages, introduce a thin `simple-peer` adapter that serializes peer signals through the existing `/api/transfer/sessions/{id}/signals` endpoints and reuses the current transfer control/data protocol.

**Tech Stack:** Vite 6, React 19, TypeScript, node:test, `simple-peer`, existing Spring Boot transfer signaling API.

---

### Task 1: Lock the new peer adapter contract with failing tests

**Files:**
- Create: `front/src/lib/transfer-peer.test.ts`
- Create: `front/src/lib/transfer-peer.ts`

- [ ] **Step 1: Write the failing test**

Add tests that assert:
- local `simple-peer` signal events serialize into a backend-friendly payload
- incoming backend signal payloads are routed back into the peer instance
- peer connection lifecycle maps to app-friendly callbacks without exposing raw browser SDP/ICE handling to pages

- [ ] **Step 2: Run test to verify it fails**

Run: `cd front && npm run test -- src/lib/transfer-peer.test.ts`

- [ ] **Step 3: Write minimal implementation**

Create a focused adapter around `simple-peer` with:
- sender/receiver construction
- `signal` event forwarding
- `connect`, `data`, `close`, and `error` callbacks
- send helpers used by the existing transfer pages

- [ ] **Step 4: Run test to verify it passes**

Run: `cd front && npm run test -- src/lib/transfer-peer.test.ts`

### Task 2: Replace online sender wiring in the desktop and mobile transfer pages

**Files:**
- Modify: `front/src/pages/Transfer.tsx`
- Modify: `front/src/mobile-pages/MobileTransfer.tsx`
- Modify: `front/src/lib/transfer.ts`

- [ ] **Step 1: Write the failing test**

Add or extend focused tests for the new signaling payload shape if needed so the sender path no longer depends on manual offer/answer/ICE branches.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd front && npm run test`

- [ ] **Step 3: Write minimal implementation**

Use the adapter in both sender pages:
- build an initiator peer instead of a raw `RTCPeerConnection`
- post serialized peer signals through the existing backend endpoint
- keep the current file manifest and binary chunk sending protocol

- [ ] **Step 4: Run test to verify it passes**

Run: `cd front && npm run test`

### Task 3: Replace online receiver wiring while keeping the current receive UX

**Files:**
- Modify: `front/src/pages/TransferReceive.tsx`

- [ ] **Step 1: Write the failing test**

Add or update tests around receiver signal handling and data delivery if gaps remain after Task 2.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd front && npm run test`

- [ ] **Step 3: Write minimal implementation**

Use the adapter in the receiver page:
- build a non-initiator peer
- feed backend-delivered signals into it
- keep the current control messages, archive flow, and netdisk save flow unchanged

- [ ] **Step 4: Run test to verify it passes**

Run: `cd front && npm run test`

### Task 4: Verification and release

**Files:**
- Modify if required by validation failures: `front/package.json`, `front/package-lock.json`

- [ ] **Step 1: Install the dependency**

Run: `cd front && npm install simple-peer @types/simple-peer`

- [ ] **Step 2: Run frontend tests**

Run: `cd front && npm run test`

- [ ] **Step 3: Run frontend typecheck**

Run: `cd front && npm run lint`

- [ ] **Step 4: Run frontend build**

Run: `cd front && npm run build`

- [ ] **Step 5: Publish the frontend**

Run: `node scripts/deploy-front-oss.mjs`
