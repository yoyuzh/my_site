# File Open-With Layer And Viewers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Cloudreve-style open-with layer so every file open action flows through a viewer registry, recommended/open-all chooser, account-backed defaults, and configured builtin/custom/wopi viewer inventory.

**Architecture:** The backend owns account-backed open-with preferences and the system viewer configuration contract. The frontend owns viewer matching, recommendations, open pipeline orchestration, selector UI, settings UI, and concrete viewer runtimes.

**Tech Stack:** Spring Boot 3.3.8, Java 17, Maven, Vite 6, React 19-style codebase running React 18, TypeScript, MUI, TanStack Query.

---

## File Structure

Backend additions and modifications:

- Modify `backend/src/main/java/com/yoyuzh/identity/access/internal/domain/User.java`: persist account viewer preferences JSON.
- Modify `backend/src/main/java/com/yoyuzh/identity/access/api/UserSettingsResponse.java`: expose `defaultOpenWithByExt`.
- Create `backend/src/main/java/com/yoyuzh/identity/access/api/UpdateUserSettingsRequest.java`: update settings and open-with preferences.
- Modify `backend/src/main/java/com/yoyuzh/identity/access/internal/application/AuthService.java`: read/write settings atomically.
- Modify `backend/src/main/java/com/yoyuzh/identity/access/internal/web/UserController.java`: add `PUT /api/user/settings`.
- Modify `backend/src/main/resources/dev-h2-preinit.sql`: add default-open-with column for dev H2 compatibility.
- Create `backend/src/main/java/com/yoyuzh/files/workspace/api/FileViewerConfigResponse.java`: frontend viewer config contract.
- Create `backend/src/main/java/com/yoyuzh/files/workspace/api/FileViewerDefinition.java`: viewer metadata contract.
- Create `backend/src/main/java/com/yoyuzh/files/workspace/api/FileViewerTemplate.java`: new-file template contract.
- Create `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileViewerConfigService.java`: static default viewer inventory and default mapping.
- Modify `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`: add viewer config endpoint.
- Create backend tests near `backend/src/test/java/com/yoyuzh/identity/access/internal/application` and `backend/src/test/java/com/yoyuzh/files/workspace/internal/application`.

Frontend additions and modifications:

- Modify `frontend/src/api/types.ts`: add viewer config and settings types.
- Modify `frontend/src/lib/user-settings.ts`: add update user settings helper.
- Modify `frontend/src/lib/files.ts`: add viewer config and WOPI session helpers.
- Create `frontend/src/lib/file-viewers.ts`: viewer indexing, matching, recommendations, default config normalization.
- Create `frontend/src/lib/file-open-preferences.ts`: account preference helpers.
- Create `frontend/src/components/files/OpenWithDialog.tsx`: recommended list plus expand-all selector.
- Create `frontend/src/components/files/FileViewerHost.tsx`: route chosen viewer to concrete runtime.
- Create `frontend/src/components/files/CustomViewerFrame.tsx`: URL/iframe runtime for custom viewers.
- Create `frontend/src/components/files/WopiViewerFrame.tsx`: session-backed WOPI runtime shell.
- Modify `frontend/src/components/files/FilesPreviewDialog.tsx`: become builtin runtime for supported viewers rather than the direct open entry.
- Modify `frontend/src/pages/Files.tsx`: wire double-click, context menu, new-file auto-open, and selector state into the open pipeline.
- Modify `frontend/src/pages/AccountSettings.tsx`: add file default-open-with management.

## Task 1: Backend User Open-With Preferences

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/internal/domain/User.java`
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/api/UserSettingsResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/identity/access/api/UpdateUserSettingsRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/internal/application/AuthService.java`
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/internal/web/UserController.java`
- Modify: `backend/src/main/resources/dev-h2-preinit.sql`
- Test: `backend/src/test/java/com/yoyuzh/identity/access/internal/application/AuthServiceUserSettingsTest.java`

- [ ] **Step 1: Write failing backend tests**

Create tests proving `getSettings` returns an empty `defaultOpenWithByExt` map by default and `updateSettings` persists, replaces, and clears extension mappings.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -q -Dtest=AuthServiceUserSettingsTest test`

Expected: fails because update request and preference field do not exist.

- [ ] **Step 3: Implement user settings persistence**

Add a text column on `User` for JSON preferences, parse it with Jackson in `AuthService`, and expose `PUT /api/user/settings`.

- [ ] **Step 4: Run tests and verify pass**

Run: `cd backend && mvn -q -Dtest=AuthServiceUserSettingsTest test`

Expected: pass.

## Task 2: Backend Viewer Config Contract

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileViewerConfigResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileViewerDefinition.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileViewerTemplate.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileViewerConfigService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/FileViewerConfigServiceTest.java`

- [ ] **Step 1: Write failing config test**

Assert the default config contains builtin `image`, `photopea`, `code-monaco`, `drawio`, `markdown`, `video`, `pdf`, `epub`, `music`, `excalidraw`, `archive`, plus custom Google viewer and WOPI Microsoft viewer.

- [ ] **Step 2: Run test and verify failure**

Run: `cd backend && mvn -q -Dtest=FileViewerConfigServiceTest test`

Expected: fails because config service does not exist.

- [ ] **Step 3: Implement config service and endpoint**

Add `GET /api/files/viewers/config` returning `fileViewers`, `defaultViewerMapping`, and templates for `txt`, `md`, `drawio`, `excalidraw`.

- [ ] **Step 4: Run test and verify pass**

Run: `cd backend && mvn -q -Dtest=FileViewerConfigServiceTest test`

Expected: pass.

## Task 3: Frontend Viewer Matching Core

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/lib/file-viewers.ts`
- Create: `frontend/src/lib/file-open-preferences.ts`
- Modify: `frontend/src/lib/files.ts`
- Modify: `frontend/src/lib/user-settings.ts`

- [ ] **Step 1: Add TypeScript types and pure helpers**

Add viewer config, viewer type, template, preference, and recommendation helpers.

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run lint`

Expected: passes after helper implementation.

## Task 4: Open-With Selector UI

**Files:**
- Create: `frontend/src/components/files/OpenWithDialog.tsx`
- Create: `frontend/src/components/files/FileViewerHost.tsx`
- Create: `frontend/src/components/files/CustomViewerFrame.tsx`
- Create: `frontend/src/components/files/WopiViewerFrame.tsx`
- Modify: `frontend/src/components/files/FilesPreviewDialog.tsx`

- [ ] **Step 1: Implement selector state and rendering**

The dialog shows recommended viewers first and an `展开所有方式` control that reveals all matching viewers.

- [ ] **Step 2: Implement viewer host**

Route builtin viewers to existing preview/editor behavior, custom viewers to iframe/new-window behavior, and WOPI viewers to a session-backed frame.

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npm run lint`

Expected: pass.

## Task 5: Files Page Integration

**Files:**
- Modify: `frontend/src/pages/Files.tsx`
- Modify: `frontend/src/components/files/FilesExplorerSurface.tsx`

- [ ] **Step 1: Replace direct preview opening**

Double-click, right-click `打开`, and right-click `打开方式` enter the open pipeline instead of directly setting `previewFile`.

- [ ] **Step 2: Preserve new-file direct open**

New `txt`, `md`, `drawio`, and `excalidraw` files open immediately with their configured template viewer.

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npm run lint`

Expected: pass.

## Task 6: Account Settings UI

**Files:**
- Modify: `frontend/src/pages/AccountSettings.tsx`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/lib/user-settings.ts`

- [ ] **Step 1: Add file open defaults section**

Show per-extension defaults, allow choosing a viewer, clearing one extension, and clearing all.

- [ ] **Step 2: Persist through user settings API**

Use `PUT /api/user/settings` and refresh local session-visible settings.

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npm run lint`

Expected: pass.

## Task 7: Verification

**Files:**
- No source ownership; verification only.

- [ ] **Step 1: Backend focused tests**

Run: `cd backend && mvn -q -Dtest=AuthServiceUserSettingsTest,FileViewerConfigServiceTest,FileServiceTest,FileServiceEdgeCaseTest test`

- [ ] **Step 2: Frontend typecheck**

Run: `cd frontend && npm run lint`

- [ ] **Step 3: Frontend build**

Run: `cd frontend && npm run build`

- [ ] **Step 4: Manual smoke**

Start the existing frontend dev server with `cd frontend && npm run dev` if it is not already running, then verify:

- `txt / md / drawio / excalidraw` new-file direct open
- double-click opens recommended open-with dialog when no default exists
- right-click `打开方式` ignores saved default
- account settings can clear single and all open-with defaults

