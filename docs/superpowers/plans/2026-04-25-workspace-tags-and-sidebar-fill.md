# Workspace Tags And Sidebar Fill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend-persisted workspace tags plus file-page tag management UI, and make dashboard sidebar/file surfaces fill the remaining page height.

**Architecture:** Keep tag ownership inside `files.workspace`. Persist a user-owned tag library and file-tag relations in `files.workspace.internal.domain/infra`, expose authenticated `/api/files/**` tag endpoints from `internal.web`, and consume them in the frontend file page through the existing API layer. Layout fixes stay in shared dashboard shell components so sidebar/content height behavior is consistent across pages.

**Tech Stack:** Spring Boot 3.3, Spring MVC, Spring Data JPA, React, TypeScript, Vite, MUI, React Query, Axios

---

### Task 1: Backend tag persistence and API

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/WorkspaceTag.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/WorkspaceFileTag.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/WorkspaceTagRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/WorkspaceFileTagRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceTagResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/CreateWorkspaceTagCommand.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/UpdateWorkspaceTagCommand.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/CreateWorkspaceTagRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/UpdateWorkspaceTagRequest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/web/FileTagControllerIntegrationTest.java`

- [ ] Step 1: Write failing integration tests for tag CRUD and file-tag assignment APIs.
- [ ] Step 2: Run `cd /Users/mac/Documents/my_site/backend && mvn -Dtest=FileTagControllerIntegrationTest test` and verify the new tests fail for missing endpoints/types.
- [ ] Step 3: Add workspace tag entities, repositories, API DTOs, service methods, and controller endpoints with ownership kept in `files.workspace`.
- [ ] Step 4: Re-run `cd /Users/mac/Documents/my_site/backend && mvn -Dtest=FileTagControllerIntegrationTest test` until green.
- [ ] Step 5: Refactor only enough to keep DTO assembly and service logic readable without moving rule ownership out of `files.workspace`.

### Task 2: Backend detail/list shaping for frontend consumption

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileMetadataResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/web/FileTagControllerIntegrationTest.java`

- [ ] Step 1: Extend the failing integration test to assert tag data is present on file detail and tag list queries needed by the UI.
- [ ] Step 2: Run `cd /Users/mac/Documents/my_site/backend && mvn -Dtest=FileTagControllerIntegrationTest test` and confirm the response-shape assertions fail first.
- [ ] Step 3: Add the minimal response fields and assembly code so file detail and tag endpoints expose stable frontend-ready tag payloads.
- [ ] Step 4: Re-run the focused backend test and then `cd /Users/mac/Documents/my_site/backend && mvn test` for workspace-facing regression coverage.

### Task 3: Frontend tag management and layout fill

**Files:**
- Modify: `frontend/src/lib/files.ts`
- Modify: `frontend/src/pages/Files.tsx`
- Create: `frontend/src/components/files/FileTagsManagerDialog.tsx`
- Modify: `frontend/src/components/workspace/WorkspaceSidebar.tsx`
- Modify: `frontend/src/components/DashboardLayout.tsx`
- Test: `frontend` typecheck/build via existing commands

- [ ] Step 1: Add frontend API functions for tag CRUD and file-tag assignment/removal, then wire failing UI state in `Files.tsx`.
- [ ] Step 2: Run `cd /Users/mac/Documents/my_site/frontend && npm run lint` and confirm type errors until the new UI wiring is completed.
- [ ] Step 3: Implement the tag manager dialog and context-menu behavior: no tags -> `管理标签`; has tags -> tag rows + bottom `管理标签`.
- [ ] Step 4: Adjust dashboard shell/sidebar height rules so sidebar and file content area fill the remaining page height consistently.
- [ ] Step 5: Re-run `cd /Users/mac/Documents/my_site/frontend && npm run lint` and `cd /Users/mac/Documents/my_site/frontend && npm run build` until green.

### Task 4: End-to-end verification

**Files:**
- No new source files expected

- [ ] Step 1: Run `cd /Users/mac/Documents/my_site/backend && mvn test`.
- [ ] Step 2: Run `cd /Users/mac/Documents/my_site/frontend && npm run lint`.
- [ ] Step 3: Run `cd /Users/mac/Documents/my_site/frontend && npm run build`.
- [ ] Step 4: Summarize any remaining limitations, especially unfinished submenu behaviors or API gaps.
