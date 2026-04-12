# Admin OSS Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the most error-prone hand-rolled admin UI primitives with mature OSS components, starting with configuration forms.

**Architecture:** Do the replacement in two batches. Batch 1 replaces form state and validation on configuration-heavy pages with `react-hook-form`. Batch 2 replaces hand-built table state/rendering on admin resource lists with `@tanstack/react-table`. Keep backend contracts unchanged and preserve the existing visual system.

**Tech Stack:** React 19, TypeScript, Vite 6, Tailwind CSS v4, `react-hook-form`, `@tanstack/react-table`

---

### Task 1: Replace Configuration Forms With `react-hook-form`

**Files:**
- Modify: `front/package.json`
- Modify: `front/package-lock.json`
- Modify: `front/src/admin/settings.tsx`
- Modify: `front/src/admin/users-list.tsx`

- [x] **Step 1: Add `react-hook-form` to the frontend**

Run:

```bash
cd front && npm install react-hook-form
```

Expected: `front/package.json` and `front/package-lock.json` include `react-hook-form`.

- [x] **Step 2: Migrate `系统设置` editable sections to `react-hook-form`**

Target:

```text
- invite code form
- offline transfer limit form
- validation and submit lifecycle
```

Expected: remove page-local draft state for editable settings where `react-hook-form` can own the fields.

- [x] **Step 3: Migrate `用户策略` editor panel to `react-hook-form`**

Target:

```text
- role
- storageQuotaBytes
- maxUploadSizeBytes
- manualPassword
```

Expected: replace manual draft state and ad hoc validation with form-driven state and explicit submit handlers.

- [x] **Step 4: Verify Batch 1**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.

### Task 2: Replace Admin List Tables With `@tanstack/react-table`

**Files:**
- Modify: `front/package.json`
- Modify: `front/package-lock.json`
- Modify: `front/src/admin/users-list.tsx`
- Modify: `front/src/admin/storage-policies-list.tsx`

- [x] **Step 1: Add `@tanstack/react-table` to the frontend**

Run:

```bash
cd front && npm install @tanstack/react-table
```

Expected: `front/package.json` and `front/package-lock.json` include `@tanstack/react-table`.

- [x] **Step 2: Migrate `用户策略` table rendering to TanStack**

Expected:

```text
- columns defined declaratively
- row rendering stays visually consistent
- action cells preserve current behavior
```

- [x] **Step 3: Migrate `存储策略` table rendering to TanStack**

Expected:

```text
- existing columns preserved
- no backend contract changes
- current modal / action flows preserved
```

- [x] **Step 4: Verify Batch 2**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.

### Task 3: Extend TanStack Table To Share Governance

**Files:**
- Modify: `front/src/admin/shares.tsx`

- [x] **Step 1: Migrate `分享治理` table rendering to TanStack**

Expected:

```text
- existing columns preserved
- no backend contract changes
- copy / open / delete actions preserved
- filter form and empty state preserved
```

- [x] **Step 2: Verify Batch 3**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.
