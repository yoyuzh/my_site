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

### Task 9: Replace Hand-Styled Admin Search And Filter Inputs

**Files:**
- Modify: `front/src/admin/users-list.tsx`
- Modify: `front/src/admin/files-list.tsx`
- Modify: `front/src/admin/shares.tsx`
- Modify: `front/src/admin/fileblobs.tsx`
- Modify: `front/src/admin/tasks.tsx`
- Modify: `front/src/admin/audits.tsx`

- [ ] **Step 1: Replace search and filter inputs with the shared admin input**

Target:

```text
- 用户策略: 搜索框
- 文件治理: 文件名 / 所属用户
- 分享治理: 用户 / 文件名 / Token
- 对象治理: 用户 / 策略 ID / 对象键
- 任务监控: 多个筛选输入
- 审计日志: actor / action / targetType / targetId
```

- [ ] **Step 2: Verify Batch 9**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.

### Task 8: Replace Hand-Styled Admin Text And Number Inputs

**Files:**
- Add: `front/src/components/admin/AdminInput.tsx`
- Modify: `front/src/admin/users-list.tsx`

> Current execution scope for this batch is limited to `users-list.tsx`.

- [x] **Step 1: Add reusable admin input primitives**

Expected:

```text
- expose shared admin text/number input styling
- support form registration and controlled usage
- keep current focus, disabled, and validation affordances
```

- [x] **Step 2: Replace editable inputs in configuration-heavy pages**

Target:

```text
- 用户策略: 存储配额 / 最大上传限制 / 手动密码
```

- [x] **Step 3: Verify Batch 8**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.

### Task 6: Replace Hand-Rolled Admin Select Controls

**Files:**
- Modify: `front/package.json`
- Modify: `front/package-lock.json`
- Add: `front/src/components/admin/AdminSelect.tsx`
- Modify: `front/src/admin/shares.tsx`
- Modify: `front/src/admin/fileblobs.tsx`
- Modify: `front/src/admin/tasks.tsx`
- Modify: `front/src/admin/storage-policies-list.tsx`

- [x] **Step 1: Add a reusable admin select wrapper based on Radix Select**

Expected:

```text
- install an OSS select dependency
- expose a shared admin-styled select wrapper
- support placeholder, controlled value, and option groups used by admin pages
```

- [x] **Step 2: Replace filter selects in governance pages**

Target:

```text
- 分享治理: 密码保护 / 过期状态
- 对象治理: 实体类型
- 任务监控: 每页条数
```

- [x] **Step 3: Replace modal selects in storage policies**

Target:

```text
- 新建 / 编辑策略: 驱动协议
- 发起迁移: 目标策略选择
```

- [x] **Step 4: Verify Batch 6**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.

### Task 7: Replace The Remaining Admin Form Select

**Files:**
- Modify: `front/src/admin/users-list.tsx`

- [x] **Step 1: Replace the user role field with the shared admin select**

Target:

```text
- 用户策略: role 字段
- 保持 react-hook-form 校验和提交逻辑不变
```

- [x] **Step 2: Verify Batch 7**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.

### Task 5: Replace Hand-Rolled Admin Modal Shells

**Files:**
- Modify: `front/package.json`
- Modify: `front/package-lock.json`
- Add: `front/src/components/admin/AdminDialog.tsx`
- Modify: `front/src/admin/storage-policies-list.tsx`
- Modify: `front/src/admin/users-list.tsx`

- [x] **Step 1: Add a reusable admin dialog wrapper based on Radix Dialog**

Expected:

```text
- install an OSS dialog dependency
- expose a shared admin-styled modal/sheet wrapper
- support centered modal and right-side panel layouts
```

- [x] **Step 2: Replace storage policy form and migration overlays**

Target:

```text
- 新建 / 编辑策略弹层
- 发起迁移弹层
```

- [x] **Step 3: Replace the user strategy editor shell**

Target:

```text
- 用对话框 / 侧栏承载用户策略编辑
- 保留当前表单与快捷操作逻辑
```

- [x] **Step 4: Verify Batch 5**

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

### Task 4: Replace Native Admin Alerts And Confirms

**Files:**
- Modify: `front/package.json`
- Modify: `front/package-lock.json`
- Add: `front/src/components/admin/AdminAlertDialog.tsx`
- Modify: `front/src/admin/settings.tsx`
- Modify: `front/src/admin/shares.tsx`
- Modify: `front/src/admin/files-list.tsx`
- Modify: `front/src/admin/filesystem.tsx`
- Modify: `front/src/admin/fileblobs.tsx`
- Modify: `front/src/admin/audits.tsx`

- [x] **Step 1: Add a reusable admin alert dialog wrapper**

Expected:

```text
- install an OSS dialog dependency
- expose a shared admin-styled alert dialog wrapper
- keep current Tailwind visual system intact
```

- [x] **Step 2: Replace destructive confirm actions with in-app dialogs**

Target:

```text
- 系统设置: 轮换邀请码
- 分享治理: 删除分享
- 文件治理: 彻底删除
```

- [x] **Step 3: Replace remaining admin alert feedback with page-level notices**

Target:

```text
- 文件系统: 复制失败
- 对象实体: 复制失败
- 审计日志: 复制失败
- 分享治理: 复制成功 / 失败
```

- [x] **Step 4: Verify Batch 4**

Run:

```bash
cd front && npm run lint
cd front && npm run build
```

Expected: both commands pass.
