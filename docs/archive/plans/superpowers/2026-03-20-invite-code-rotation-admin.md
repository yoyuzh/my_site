# Invite Code Rotation Admin Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make registration invite codes single-use, rotate them after each successful registration, and show the current invite code in the admin dashboard with copy support.

**Architecture:** Persist the current invite code in backend storage instead of relying on a fixed runtime property at validation time. Consume and rotate the code inside the registration transaction, then expose the current code through the existing admin summary API so the admin dashboard can render and copy it without introducing an extra management surface.

**Tech Stack:** Spring Boot 3.3, Spring Data JPA, H2/MySQL, React 19, Vite, MUI, existing `apiRequest` helpers, existing backend/frontend test runners.

---

### Task 1: Persist invite code state on the backend

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/auth/RegistrationInviteState.java`
- Create: `backend/src/main/java/com/yoyuzh/auth/RegistrationInviteStateRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/auth/RegistrationInviteService.java`
- Modify: `backend/src/main/java/com/yoyuzh/auth/AuthService.java`
- Modify: `backend/src/main/java/com/yoyuzh/config/RegistrationProperties.java`

- [ ] Write a failing backend test that proves a successful registration rotates the invite code and invalidates the previous one.
- [ ] Run the backend auth test to verify the new expectation fails for the current static invite code implementation.
- [ ] Implement the persisted invite code state, bootstrap behavior, and transaction-safe consume-and-rotate flow.
- [ ] Re-run the backend auth test until it passes.

### Task 2: Expose the current invite code to admins

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminService.java`
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminSummaryResponse.java`
- Modify: `backend/src/test/java/com/yoyuzh/admin/AdminControllerIntegrationTest.java`

- [ ] Write a failing admin integration test that expects the summary API to include the current invite code.
- [ ] Run the admin integration test to verify it fails before the API change.
- [ ] Extend the summary response with the current invite code metadata.
- [ ] Re-run the admin integration test until it passes.

### Task 3: Show and copy the code from the admin dashboard

**Files:**
- Modify: `front/src/lib/types.ts`
- Create: `front/src/admin/dashboard-state.ts`
- Create: `front/src/admin/dashboard-state.test.ts`
- Modify: `front/src/admin/dashboard.tsx`

- [ ] Write a failing frontend test for dashboard helpers that maps the summary payload into invite-code display state.
- [ ] Run the frontend test to verify it fails before the new helper exists.
- [ ] Implement the helper and update the dashboard UI to render the code, refresh the summary, and copy the current code.
- [ ] Re-run the targeted frontend test until it passes.

### Task 4: Verify the full flow

**Files:**
- Modify only if verification reveals gaps.

- [ ] Run `cd backend && mvn test`
- [ ] Run `cd front && npm run test`
- [ ] Run `cd front && npm run lint`
- [ ] Summarize behavior changes and any deployment follow-up needed for the user.
