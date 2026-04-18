# Admin Settings Deliverability Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make admin settings behavior reviewable and shippable by removing known contract breaks (test breakage, fake mutability, and process-local settings state).

**Architecture:** Replace process-local `AtomicReference` settings state with a DB-backed single-row state model, then enforce that only truly writable sections mutate effective runtime behavior. Keep API shape stable, but ensure `writeSupported` flags match actual behavior and tests prove it.

**Tech Stack:** Java 17, Spring Boot 3.3.8, Spring Data JPA, H2 integration tests, Maven Surefire.

---

### Task 1: Lock Current Regression Baseline

**Files:**
- Modify: `backend/src/test/java/com/yoyuzh/auth/AuthServiceTest.java`

**Step 1: Verify `AuthServiceTest` uses `AdminRuntimeSettingsService` mock only where needed**

Expected: invite-code stubbing is scoped to registration-path tests, no global unnecessary stubbing.

**Step 2: Run focused test**

Run: `mvn "-Dtest=AuthServiceTest" test`  
Expected: PASS

**Step 3: Commit**

```bash
git add backend/src/test/java/com/yoyuzh/auth/AuthServiceTest.java
git commit -m "test: fix auth service invite-code settings stubbing"
```

### Task 2: Persist Runtime Settings in Database

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/admin/AdminRuntimeSettingsState.java`
- Create: `backend/src/main/java/com/yoyuzh/admin/AdminRuntimeSettingsStateRepository.java`
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminRuntimeSettingsService.java`

**Step 1: Write failing integration test for restart-safe settings**

Test target: update settings via service, then read snapshot again from a fresh service instance backed by DB state.  
Expected before implementation: fail because state is in-memory only.

**Step 2: Implement single-row persistent state entity + repository**

Implementation rules:
- ID fixed to `1L`
- include all fields currently in `AdminRuntimeSettingsService.State`
- add `updatedAt` with `@PrePersist/@PreUpdate`
- repository includes `findByIdForUpdate` with pessimistic lock

**Step 3: Refactor `AdminRuntimeSettingsService` to DB-backed state**

Implementation rules:
- replace `AtomicReference<State>`
- keep normalization behavior
- initialize row with current default values when missing
- `snapshot()`, `update(...)`, `isInviteCodeRequired()`, `reset()` use repository-backed state

**Step 4: Run focused tests**

Run: `mvn "-Dtest=AdminRuntimeSettingsServiceIntegrationTest" test`  
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/admin/AdminRuntimeSettingsState.java backend/src/main/java/com/yoyuzh/admin/AdminRuntimeSettingsStateRepository.java backend/src/main/java/com/yoyuzh/admin/AdminRuntimeSettingsService.java backend/src/test/java/com/yoyuzh/admin/AdminRuntimeSettingsServiceIntegrationTest.java
git commit -m "feat: persist admin runtime settings in database"
```

### Task 3: Enforce Mutability Contract in Update Endpoint

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminMutableSettingsService.java`
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminConfigSnapshotService.java`

**Step 1: Write failing test for read-only sections not mutating effective state**

Test target: `PUT /api/admin/settings` with updates in read-only sections (`site`, `userSession`, `queue`, `server`, etc.), then `GET /api/admin/settings` should keep old values for those sections.

**Step 2: Implement effective-state merge in `AdminMutableSettingsService`**

Implementation rules:
- only `registration` and `transfer` are effective writable sections
- preserve existing runtime values for read-only sections during update
- keep invite code update and offline-transfer-limit update behavior

**Step 3: Ensure `writeSupported` remains aligned**

Check: `AdminConfigSnapshotService.getSettings()` exposes writable=true only for effective writable sections.

**Step 4: Run focused tests**

Run: `mvn "-Dtest=AdminMutableSettingsServiceTest,AdminControllerIntegrationTest" test`  
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/admin/AdminMutableSettingsService.java backend/src/main/java/com/yoyuzh/admin/AdminConfigSnapshotService.java backend/src/test/java/com/yoyuzh/admin/AdminMutableSettingsServiceTest.java backend/src/test/java/com/yoyuzh/admin/AdminControllerIntegrationTest.java
git commit -m "fix: enforce admin settings mutability contract"
```

### Task 4: Keep managementRoles Authorization Test-Covered

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminAccessEvaluator.java`
- Create/Modify: `backend/src/test/java/com/yoyuzh/admin/AdminAccessEvaluatorTest.java`

**Step 1: Verify authorization uses runtime `managementRoles`**

Test targets:
- config `["ADMIN"]` allows `ROLE_ADMIN`
- config `["ADMIN"]` rejects `ROLE_MODERATOR`
- role matching case-insensitive

**Step 2: Run focused tests**

Run: `mvn "-Dtest=AdminAccessEvaluatorTest,AdminControllerIntegrationTest" test`  
Expected: PASS

**Step 3: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/admin/AdminAccessEvaluator.java backend/src/test/java/com/yoyuzh/admin/AdminAccessEvaluatorTest.java
git commit -m "fix: apply management roles to admin authorization"
```

### Task 5: Full Verification and Follow-up Review

**Files:**
- Modify: `memory.md`

**Step 1: Run frontend type-check**

Run: `npm run lint` (in `front/`)  
Expected: PASS

**Step 2: Run backend full test suite**

Run: `mvn test` (in `backend/`)  
Expected: PASS

**Step 3: Update project memory for major behavior changes**

Add summary for:
- DB-backed admin runtime settings
- mutability contract enforcement
- management role authorization behavior

**Step 4: Perform follow-up review pass**

Review targets:
- remaining fake-write paths
- persistence consistency risks
- controller boundary and module ownership risks

**Step 5: Commit**

```bash
git add memory.md
git commit -m "docs: update memory for admin settings deliverability fixes"
```
