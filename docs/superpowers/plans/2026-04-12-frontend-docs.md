# Frontend Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write two repo-aligned frontend docs: one component selection guide tailored to this project and one page orchestration guide that enumerates every current page, buttons, and admin settings item.

**Architecture:** Read the live frontend routes, shared shells, admin pages, and supporting API shapes first, then write the docs directly from code so the output reflects the real UI instead of intentions. Keep the component guide opinionated about which OSS components fit the project while keeping the page orchestration guide strictly descriptive about what exists today.

**Tech Stack:** Markdown, Vite 6, React 19, TypeScript, repo docs under `docs/`

---

### Task 1: Inventory Current Frontend Routes And Admin Surfaces

**Files:**
- Modify: `docs/superpowers/plans/2026-04-12-frontend-docs.md`
- Read: `front/src/App.tsx`
- Read: `front/src/components/layout/Layout.tsx`
- Read: `front/src/mobile-components/MobileLayout.tsx`
- Read: `front/src/admin/*.tsx`
- Read: `front/src/pages/**/*.tsx`
- Read: `front/src/transfer/pages/TransferPage.tsx`
- Read: `front/src/lib/admin*.ts`

- [ ] **Step 1: Capture the route and admin page inventory**

```text
Public routes:
- /login
- /share/:token

App routes:
- /overview
- /files
- /tasks
- /shares
- /recycle-bin
- /transfer

Admin routes:
- /admin/dashboard
- /admin/settings
- /admin/filesystem
- /admin/storage-policies
- /admin/users
- /admin/files
- /admin/file-blobs
- /admin/shares
- /admin/tasks
- /admin/oauth-apps
```

- [ ] **Step 2: Verify the route inventory against the code**

Run:

```bash
sed -n '1,220p' front/src/App.tsx
```

Expected: route declarations matching the list above.

- [ ] **Step 3: Capture the implemented admin settings and placeholder pages**

```text
Implemented admin pages:
- dashboard
- storage-policies
- users
- files

Placeholder admin pages:
- settings
- filesystem
- file-blobs
- shares
- tasks
- oauth-apps
```

- [ ] **Step 4: Verify the admin page status directly**

Run:

```bash
sed -n '1,340p' front/src/admin/settings.tsx
sed -n '1,340p' front/src/admin/filesystem.tsx
sed -n '1,340p' front/src/admin/fileblobs.tsx
sed -n '1,340p' front/src/admin/shares.tsx
sed -n '1,340p' front/src/admin/tasks.tsx
sed -n '1,340p' front/src/admin/oauthapps.tsx
```

Expected: simple placeholder components with only a title and no real controls.

- [ ] **Step 5: Commit the inventory checkpoint**

```bash
git add docs/superpowers/plans/2026-04-12-frontend-docs.md
git commit -m "docs: add frontend docs planning inventory"
```

### Task 2: Write The Frontend Component Selection Guide

**Files:**
- Create: `docs/frontend-component-guide.md`
- Read: `front/src/components/layout/Layout.tsx`
- Read: `front/src/mobile-components/MobileLayout.tsx`
- Read: `front/src/components/upload/UploadCenter.tsx`
- Read: `front/src/components/tasks/TaskSummaryPanel.tsx`
- Read: `front/src/pages/files/FilesPage.tsx`
- Read: `front/src/admin/storage-policies-list.tsx`

- [ ] **Step 1: Write the document header and replacement principles**

```md
# 前端组件选型与替换指南

## 1. 文档目标

这份文档用于回答两个问题：

1. 当前 `front/` 已经有哪些前端基础组件与页面壳
2. 哪些“不完善但不承载业务规则”的前端组件适合用成熟开源组件替换
```

- [ ] **Step 2: Add the current in-repo component baseline**

```md
## 3. 当前项目已有的核心基础组件

### 3.1 应用外壳

- `front/src/components/layout/Layout.tsx`
- `front/src/mobile-components/MobileLayout.tsx`
- `front/src/admin/AdminLayout.tsx`

### 3.2 全局状态与共用组件

- `front/src/components/ThemeProvider.tsx`
- `front/src/components/ThemeToggle.tsx`
- `front/src/components/upload/UploadCenter.tsx`
- `front/src/components/tasks/TaskSummaryPanel.tsx`
- `front/src/components/media/FileThumbnail.tsx`
```

- [ ] **Step 3: Add the recommended OSS component matrix**

```md
### 4.1 `@tanstack/react-table`
- 适合替换管理台和列表型页面表格

### 4.3 `react-dropzone`
- 适合替换网盘和快传的文件选择入口

### 4.4 `react-arborist`
- 适合替换网盘目录树

### 4.7 `recharts`
- 适合替换后台总览图表层

### 4.8 `vidstack`
- 适合后续文件预览里的音视频播放器
```

- [ ] **Step 4: Save the component guide**

Run:

```bash
sed -n '1,260p' docs/frontend-component-guide.md
```

Expected: the document contains the current component baseline, replacement principles, recommended libraries, and adoption priorities.

- [ ] **Step 5: Commit**

```bash
git add docs/frontend-component-guide.md
git commit -m "docs: add frontend component selection guide"
```

### Task 3: Write The Frontend Page Orchestration Guide

**Files:**
- Create: `docs/frontend-page-orchestration.md`
- Read: `front/src/pages/Login.tsx`
- Read: `front/src/pages/Overview.tsx`
- Read: `front/src/pages/files/FilesPage.tsx`
- Read: `front/src/pages/Tasks.tsx`
- Read: `front/src/pages/Shares.tsx`
- Read: `front/src/pages/RecycleBin.tsx`
- Read: `front/src/transfer/pages/TransferPage.tsx`
- Read: `front/src/pages/FileShare.tsx`
- Read: `front/src/admin/*.tsx`
- Read: `front/src/lib/admin-users.ts`
- Read: `front/src/lib/admin-storage-policies.ts`

- [ ] **Step 1: Write the route inventory and shell sections**

```md
# 前端页面编排文档

## 2. 路由总表

### 2.1 公共页面
- `/login`
- `/share/:token`

### 2.2 主应用页面
- `/overview`
- `/files`
- `/tasks`
- `/shares`
- `/recycle-bin`
- `/transfer`
```

- [ ] **Step 2: Add every user-facing page with regions and buttons**

```md
## 6.2 网盘页 `/files`

### 顶部工具区按钮
- `上传文件`
- `新建文件夹`
- `刷新`

### 右侧文件详情栏按钮
- `下载`
- `创建分享`
- `重命名`
- `移动`
- `删除`
```

- [ ] **Step 3: Add every admin page and enumerate every current setting item**

```md
## 7.4 存储策略 `/admin/storage-policies`

### 弹窗表单字段
- `策略名称`
- `驱动协议`
- `端点地址`
- `桶名称`
- `对象大小上限（字节）`

### 布尔设置项
- `私有桶`
- `启用`
- `直传`
- `分片上传`
- `签名下载`
- `代理下载`
- `需要 CORS`
- `内网端点`
```

- [ ] **Step 4: Explicitly call out placeholder admin pages and API-backed but unexposed settings**

```md
### 当前前端未暴露但 API 已存在的管理项
- `updateUserMaxUploadSize`
- `resetUserPassword`
```

- [ ] **Step 5: Save the page orchestration guide**

Run:

```bash
sed -n '1,320p' docs/frontend-page-orchestration.md
```

Expected: the document lists every current page, every visible button, and every current admin setting item, while marking placeholder admin pages clearly.

- [ ] **Step 6: Commit**

```bash
git add docs/frontend-page-orchestration.md
git commit -m "docs: add frontend page orchestration guide"
```

### Task 4: Self-Review And Repo Validation

**Files:**
- Modify: `docs/frontend-component-guide.md`
- Modify: `docs/frontend-page-orchestration.md`
- Modify: `docs/superpowers/plans/2026-04-12-frontend-docs.md`

- [ ] **Step 1: Verify the docs mention real files and only real commands**

Run:

```bash
rg -n "npm test|npm typecheck|TODO|TBD|implement later" docs/frontend-component-guide.md docs/frontend-page-orchestration.md docs/superpowers/plans/2026-04-12-frontend-docs.md
```

Expected: no matches.

- [ ] **Step 2: Verify admin settings coverage against the real code**

Run:

```bash
sed -n '1,420p' front/src/admin/storage-policies-list.tsx
sed -n '1,360p' front/src/admin/users-list.tsx
sed -n '1,360p' front/src/admin/files-list.tsx
```

Expected: every visible control in those files is represented in `docs/frontend-page-orchestration.md`.

- [ ] **Step 3: Verify git sees the new docs**

Run:

```bash
git status --short
```

Expected:

```text
A  docs/frontend-component-guide.md
A  docs/frontend-page-orchestration.md
A  docs/superpowers/plans/2026-04-12-frontend-docs.md
```

- [ ] **Step 4: Commit the final docs set**

```bash
git add docs/frontend-component-guide.md docs/frontend-page-orchestration.md docs/superpowers/plans/2026-04-12-frontend-docs.md
git commit -m "docs: add frontend documentation set"
```
