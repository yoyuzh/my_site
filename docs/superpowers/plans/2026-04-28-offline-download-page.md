# Offline Download Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the offline-download placeholder route with a real frontend workspace for creating, monitoring, and managing remote download tasks.

**Architecture:** Reuse the existing remote-download data layer and task progress helpers, add a dedicated page for active/history task grouping, and keep full remote-download detail interactions inside the page without requiring navigation to the global tasks screen.

**Tech Stack:** React 19, TypeScript, Vite, TanStack Query, MUI, existing dashboard layout and task helper utilities.

---

## File Structure Map

- Create: `frontend/src/pages/OfflineDownloads.tsx`
- Create or extract if helpful: `frontend/src/components/offline-downloads/OfflineDownloadDetailPanel.tsx`
- Create or extract if helpful: `frontend/src/components/offline-downloads/OfflineDownloadTaskList.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/Tasks.tsx` only if shared remote-download detail code should be extracted instead of duplicated

## Task 1: Add the dedicated offline downloads page shell

**Files:**
- Create: `frontend/src/pages/OfflineDownloads.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] Build a new dashboard page component that loads remote downloads, groups them into active and history buckets, and manages the currently selected task id.
- [ ] Replace the `/dashboard/offline-downloads` route target in `frontend/src/App.tsx` so it renders the new page instead of `DashboardUnderConstruction`.
- [ ] Keep the page inside `DashboardLayout` and preserve existing dashboard navigation behavior.

## Task 2: Implement creation entry and list/detail selection flow

**Files:**
- Modify: `frontend/src/pages/OfflineDownloads.tsx`
- Reuse: `frontend/src/components/files/CreateRemoteDownloadDialog.tsx`

- [ ] Add a top action area with a primary “新建离线下载” button that opens the existing create dialog.
- [ ] After successful creation, invalidate the existing remote-download and tasks queries, then auto-select the newly created task.
- [ ] Render active tasks separately from history tasks and keep history in a collapsed section by default.

## Task 3: Reuse full remote-download detail behavior in-page

**Files:**
- Create or extract: `frontend/src/components/offline-downloads/OfflineDownloadDetailPanel.tsx`
- Modify if extracting shared logic: `frontend/src/pages/Tasks.tsx`

- [ ] Render in-page detail for the selected task, including status, phase, source type, engine type, target path, selected/imported counts, failure message, and progress state.
- [ ] Support canceling unfinished tasks with the existing `cancelRemoteDownload` mutation.
- [ ] Support BT candidate file selection with the existing `selectRemoteDownloadFiles` mutation.
- [ ] Avoid copy-pasting large `Tasks.tsx` blocks if a small shared component can be extracted cleanly.

## Task 4: Cover empty/error states and run repo-approved validation

**Files:**
- Modify: `frontend/src/pages/OfflineDownloads.tsx`

- [ ] Add clear empty states for “no tasks at all” and “history only”.
- [ ] Add page-level error messaging and a retry path for failed list loads.
- [ ] Run `cd frontend && npm run lint` and record whether it passes.

