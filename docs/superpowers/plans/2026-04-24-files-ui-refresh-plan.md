# Files UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the `frontend` file page into a more Cloudreve-like workspace surface while keeping the existing `my_site` API layer and current file interactions intact.

**Architecture:** Keep `frontend/src/pages/Files.tsx` as the state and API container, but move the presentation into a few focused file-page components. Reuse the current MUI runtime and existing file query/mutation logic, and limit the rewrite to layout, visual hierarchy, and motion so backend behavior and route contracts remain unchanged.

**Tech Stack:** React 18, Vite, TypeScript, MUI 6, React Query, existing `frontend/src/api/**` and `frontend/src/lib/files.ts`

---

### Task 1: Split the file page into focused presentation units

**Files:**
- Modify: `frontend/src/pages/Files.tsx`
- Create: `frontend/src/components/files/FilesTopBar.tsx`
- Create: `frontend/src/components/files/FilesSelectionBar.tsx`
- Create: `frontend/src/components/files/FilesExplorerSurface.tsx`
- Create: `frontend/src/components/files/FilesPreviewDialog.tsx`
- Reuse: `frontend/src/components/files/FileDetailsRail.tsx`

- [ ] Extract the current top toolbar into `FilesTopBar.tsx`, with two visual rows: primary actions and secondary context.
- [ ] Extract the selected-state action bar into `FilesSelectionBar.tsx`, keeping open/detail/download/share/delete actions driven by props.
- [ ] Extract the grid/list rendering shell into `FilesExplorerSurface.tsx`, leaving `Files.tsx` responsible only for state, handlers, and data wiring.
- [ ] Move preview dialog rendering into `FilesPreviewDialog.tsx`, preserving the current preview and download URL logic.
- [ ] Update `Files.tsx` imports and state wiring so it becomes the page orchestrator rather than a giant all-in-one UI file.

### Task 2: Redesign the information hierarchy and surface composition

**Files:**
- Modify: `frontend/src/pages/Files.tsx`
- Modify: `frontend/src/components/files/FilesTopBar.tsx`
- Modify: `frontend/src/components/files/FilesSelectionBar.tsx`
- Modify: `frontend/src/components/files/FilesExplorerSurface.tsx`
- Modify: `frontend/src/components/files/FileDetailsRail.tsx`

- [ ] Turn the current stacked `Paper` blocks into a unified workspace composition with a cleaner top area, a single explorer surface, and a steadier right-side detail rail.
- [ ] Rework the top bar so breadcrumb, actions, search, result count, and view switch feel like one connected control system instead of separate cards.
- [ ] Rework the selection bar so it appears as a lifted batch-action layer with stronger emphasis than the surrounding chrome.
- [ ] Rework list and grid item styling so they read as file-browser surfaces rather than generic admin cards and rows.
- [ ] Tweak `FileDetailsRail.tsx` only as needed so it visually matches the new page hierarchy.

### Task 3: Add restrained motion and state transitions

**Files:**
- Modify: `frontend/src/components/files/FilesSelectionBar.tsx`
- Modify: `frontend/src/components/files/FilesExplorerSurface.tsx`
- Modify: `frontend/src/components/files/FilesPreviewDialog.tsx`
- Modify: `frontend/src/pages/Files.tsx`

- [ ] Add a short page-entry transition for the main file workspace.
- [ ] Add list/grid item reveal motion that stays lightweight and does not change interaction semantics.
- [ ] Add show/hide transitions for the selection bar and details rail.
- [ ] Keep motion bounded to MUI-supported transitions or simple CSS transitions; do not introduce a new animation library.

### Task 4: Validate behavior did not regress

**Files:**
- Verify: `frontend/src/pages/Files.tsx`
- Verify: `frontend/src/components/files/*.tsx`

- [ ] Confirm directory open still works through double-click, right-click menu open, and top selection bar open.
- [ ] Confirm preview, search focus via `/`, list/grid toggle, and details rail still work after the split.
- [ ] Run `cd frontend && npm run lint`.
- [ ] Run `cd frontend && npm run build`.
- [ ] Review the diff for accidental API-layer or unrelated admin-page changes before stopping.
