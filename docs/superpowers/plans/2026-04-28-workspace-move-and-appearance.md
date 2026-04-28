# Workspace Move And Appearance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add drag-and-drop move, centered move dialog, conflict retry with auto-rename or skip, and persistent emoji/color appearance for workspace files and folders.

**Architecture:** Keep workspace rule ownership in `files.workspace`. Extend workspace node metadata and move contracts in the backend first, then update the file-management UI so drag move, move dialog, and appearance editing all share the same backend truth and response model.

**Tech Stack:** Spring Boot 3.3, Java 17, JPA/Hibernate, React, TypeScript, Vite, MUI, TanStack Query, Gemini CLI for the bounded frontend slice.

---

## File Structure Map

### Backend files to create

- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceAppearanceUpdateRequest.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceAppearanceUpdateResponse.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveConflictStrategy.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveOutcomeStatus.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveItemResult.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveResult.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/BatchMoveFileRequest.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/UpdateWorkspaceAppearanceRequest.java`

### Backend files to modify

- `backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/StoredFile.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/FileMetadataResponse.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMutationApi.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMutationResult.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceMutationApi.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceDirectoryApi.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceFileSearchApi.java`
- `backend/src/main/java/com/yoyuzh/files/search/internal/application/RuntimeFileSearchApi.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/MoveFileRequest.java`
- `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`

### Backend tests

- `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceMutationApiTest.java`
- `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/FileServiceTest.java`

### Frontend files to create

- `frontend/src/components/files/MoveItemsDialog.tsx`
- `frontend/src/components/files/AppearanceDialog.tsx`
- `frontend/src/components/files/WorkspaceDragOverlay.tsx`
- `frontend/src/hooks/useWorkspaceDragMove.ts`

### Frontend files to modify

- `frontend/src/api/types.ts`
- `frontend/src/lib/files.ts`
- `frontend/src/pages/Files.tsx`
- `frontend/src/components/files/FilesExplorerSurface.tsx`
- `frontend/src/components/workspace/WorkspaceFolderTree.tsx`
- `frontend/src/components/workspace/WorkspaceFolderTreeNode.tsx`

---

### Task 1: Add Backend Move Contracts And Appearance Metadata

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveConflictStrategy.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveOutcomeStatus.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveItemResult.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMoveResult.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/StoredFile.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileMetadataResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceMutationApiTest.java`

- [ ] Add failing mutation tests that cover conflict result, auto-rename, skip, and appearance fields.
- [ ] Extend `StoredFile` with `customEmoji` and `folderColor`, plus small helper methods for updating and clearing appearance.
- [ ] Extend `FileMetadataResponse` and `FileDetailResponse` to carry `customEmoji` and `folderColor`.
- [ ] Add backend move result enums and DTOs that can represent `SUCCESS`, `CONFLICT`, and `INVALID_TARGET`.
- [ ] Run `cd backend && mvn -Dtest=RuntimeWorkspaceMutationApiTest test`.

### Task 2: Implement Backend Move Logic, Batch Move, And Appearance Update

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceAppearanceUpdateRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceAppearanceUpdateResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/BatchMoveFileRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/UpdateWorkspaceAppearanceRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMutationApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceMutationResult.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceMutationApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/MoveFileRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/FileServiceTest.java`

- [ ] Add failing service tests for batch move summaries and appearance update validation.
- [ ] Update move requests to use `targetPath` and optional `conflictStrategy`.
- [ ] Implement single-item move result handling in `RuntimeWorkspaceMutationApi`, including structured conflict responses and descendant-target rejection.
- [ ] Implement batch move and appearance update methods in `FileService` and expose them from `FileController`.
- [ ] Run `cd backend && mvn -Dtest=RuntimeWorkspaceMutationApiTest,FileServiceTest test`.

### Task 3: Propagate Appearance Fields Through Backend Read Models

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceDirectoryApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceFileSearchApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/search/internal/application/RuntimeFileSearchApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/FileServiceTest.java`

- [ ] Update list, detail, and search response builders to include `customEmoji` and `folderColor`.
- [ ] Verify folder-tree list data now carries appearance fields.
- [ ] Run `cd backend && mvn -Dtest=FileServiceTest test`.

### Task 4: Delegate Frontend Workspace UI To Gemini

**Files:**
- Create: `frontend/src/components/files/MoveItemsDialog.tsx`
- Create: `frontend/src/components/files/AppearanceDialog.tsx`
- Create: `frontend/src/components/files/WorkspaceDragOverlay.tsx`
- Create: `frontend/src/hooks/useWorkspaceDragMove.ts`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/lib/files.ts`
- Modify: `frontend/src/pages/Files.tsx`
- Modify: `frontend/src/components/files/FilesExplorerSurface.tsx`
- Modify: `frontend/src/components/workspace/WorkspaceFolderTree.tsx`
- Modify: `frontend/src/components/workspace/WorkspaceFolderTreeNode.tsx`

- [ ] Prepare a Gemini prompt that locks edits to `frontend/` and describes the move dialog, drag overlay, conflict retry flow, emoji editing, and folder color support.
- [ ] Run Gemini with the repo-approved verification command `cd frontend && npm run lint`.
- [ ] Review Gemini’s diff for scope, consistency with backend contracts, and preservation of existing selection and folder-tree behavior.

### Task 5: Integrate, Verify, And Clean Up

**Files:**
- Modify: any touched backend/frontend files as needed after integration feedback
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceMutationApiTest.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/FileServiceTest.java`

- [ ] Resolve integration mismatches between backend contracts and frontend request/response types.
- [ ] Run `cd backend && mvn -Dtest=RuntimeWorkspaceMutationApiTest,FileServiceTest test`.
- [ ] Run `cd frontend && npm run lint`.
- [ ] Re-read the approved design spec and confirm drag move, move dialog, conflict choices, emoji persistence, and folder color persistence are all represented in the code paths.

## Self-Review

- This plan covers backend metadata, move contracts, batch move, appearance update, frontend workspace UI, Gemini delegation, and repo-approved verification.
- No placeholder tasks remain; each task points to exact files and commands.
- The key shared names are fixed in this plan as `customEmoji`, `folderColor`, `targetPath`, `conflictStrategy`, and `WorkspaceMoveResult`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-28-workspace-move-and-appearance.md`.

The user already requested direct execution in the current workspace, so proceed with inline execution using this plan instead of waiting for an execution-mode choice.
