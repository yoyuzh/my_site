# Workspace Move And Appearance Design

## Goal

Implement a complete workspace organization flow with four user-facing outcomes:

1. Files, folders, and multi-selection can be dragged into another folder.
2. The `整理` menu exposes a centered `移动到` dialog for choosing a destination folder.
3. Files and folders support account-persistent custom emoji icons.
4. Folders support account-persistent custom colors.

Name conflicts during move must not overwrite by default. The user must be able to choose:

- auto rename conflicting items and continue
- skip conflicting items

## Scope

In scope:

- `frontend/src/pages/Files.tsx`
- workspace file/folder presentation components under `frontend/src/components/files` and `frontend/src/components/workspace`
- frontend API helpers and types for move, batch move, and appearance updates
- backend workspace file metadata and mutation APIs under `backend/src/main/java/com/yoyuzh/files/workspace/**`
- backend tests covering move conflicts and appearance persistence

Out of scope:

- file overwrite or replace semantics
- arbitrary custom color picker support
- unrelated file preview, share, upload, or recycle-bin redesign
- changing route groups outside existing workspace ownership

## User Experience

### Drag move

Drag move should feel closer to a desktop file manager than a default browser drag-and-drop interaction.

Behavior:

- drag starts only after a short hold-and-move delay of about `120ms - 160ms`
- files, folders, and multi-selection can be dragged
- valid drop targets include folder cards in the content area and folder nodes in the sidebar tree
- hovering over a collapsed sidebar folder for about `400ms - 500ms` auto-expands it
- invalid targets such as moving a folder into itself or its descendant show a forbidden state before drop

### Drag preview

The pointer should use a custom drag overlay instead of the browser default preview.

Overlay content:

- the item icon for single-item drag
- a count badge for multi-selection
- the custom emoji when present
- the filename for single-item drag when space allows

Motion treatment:

- the overlay follows the pointer with slight drag lag to preserve a pulled feeling
- overlay tilt responds to pointer velocity
- tilt should be clamped to roughly `-10deg` to `+10deg`
- tilt should ease back toward neutral as movement slows

### Move dialog

The `整理 -> 移动到` action opens a centered modal similar to the provided reference.

Behavior:

- works for a single file, a single folder, or multi-selection
- left side shows folder tree
- right side shows destination folder contents with folders emphasized as selectable targets
- footer shows the current destination path
- confirm action performs the move
- drag-drop conflicts reuse the same centered move flow instead of falling back to browser alerts or prompts

### Appearance editing

The `整理` menu also exposes `自定义图标`.

Behavior:

- files can edit `customEmoji`
- folders can edit `customEmoji` and `folderColor`
- the dialog includes current preview, emoji input/selection, save, and clear actions
- folder color uses a fixed palette instead of free-form color input
- saved appearance updates must reflect in the list, detail panel, drag overlay, and folder tree

### Conflict handling

Move conflicts must not silently fail and must not overwrite existing items.

Required outcomes:

- if a move creates a name conflict, the user can choose `自动重命名后移动`
- if a move creates a name conflict, the user can choose `跳过重名项`
- batch move should return a summary such as success count, renamed count, and skipped count

## Frontend Design

### Page orchestration

`Files.tsx` should remain the orchestration layer for:

- current path and visible listing state
- selection state
- move dialog open state
- appearance dialog open state
- drag session state
- conflict retry flow
- refresh of current listing and folder tree

The page should not absorb every animation and dialog detail directly.

### Suggested component split

Recommended additions:

- `MoveItemsDialog`
- `AppearanceDialog`
- `WorkspaceDragOverlay`
- `useWorkspaceDragMove`

Responsibilities:

- `MoveItemsDialog` handles destination browsing, confirmation, and conflict continuation
- `AppearanceDialog` edits emoji and folder color
- `WorkspaceDragOverlay` renders the custom drag preview and tilt animation
- `useWorkspaceDragMove` owns delayed drag start, drop-target detection, and auto-expand timing

### Frontend API additions

Frontend helpers should add:

- batch move
- move retry with conflict strategy
- appearance update for a workspace node

Existing single-item move can remain in place but should align with the new response model so the page can treat drag move and dialog move consistently.

### Gemini implementation boundary

Frontend implementation should be constrained to the workspace-related frontend surface:

- `frontend/src/pages/Files.tsx`
- `frontend/src/components/files/**`
- `frontend/src/components/workspace/**`
- `frontend/src/lib/files.ts`
- `frontend/src/api/types.ts`

This keeps the Gemini-owned work bounded to the file-management UI instead of unrelated frontend areas.

## Backend Design

### Ownership

Rule ownership remains in `files.workspace`.

- move legality stays in workspace mutation and path policy code
- conflict handling stays in workspace mutation logic
- custom emoji and folder color are workspace node metadata

This aligns with `files.workspace` owning logical tree behavior and same-directory naming rules.

### Metadata persistence

Extend workspace node metadata with:

- `customEmoji` for files and folders
- `folderColor` for folders only, `null` for files

These values must be account-persistent and returned anywhere the frontend needs a workspace node representation.

### Move API changes

Keep the existing single-item route and add structured conflict handling rather than route churn.

Recommended changes:

- extend `PATCH /api/files/{fileId}/move`
- add `POST /api/files/batch/move` for multiple file ids

Request shape should include:

- `targetPath`
- optional `conflictStrategy`

Initial supported strategies:

- `AUTO_RENAME`
- `SKIP`

If the move cannot complete without a strategy decision, backend should return a structured conflict result instead of only a plain failure message.

Recommended response model:

- `status: SUCCESS` with moved, renamed, and skipped summary counts
- `status: CONFLICT` with conflicting item ids and names
- `status: INVALID_TARGET` for self or descendant directory moves

### Appearance API

Add a workspace appearance update endpoint for a node.

Capabilities:

- file: update or clear `customEmoji`
- folder: update or clear `customEmoji`
- folder: update or clear `folderColor`

Validation:

- reject `folderColor` updates for non-folder nodes
- validate emoji length and storage format
- allow clearing values without requiring a separate delete route

### Read-model coverage

Appearance fields should be included in:

- file list responses
- file detail responses
- search results where workspace items are returned
- folder tree data used by the sidebar and move dialog

## Data Flow

### Drag move

1. User presses on a selected item or selection set.
2. Frontend waits for the drag-start delay and minimum movement threshold.
3. `WorkspaceDragOverlay` appears and follows the pointer.
4. User hovers a valid folder target in the grid or sidebar tree.
5. User drops the selection.
6. Frontend calls move API.
7. Backend returns success summary or conflict result.
8. If conflict exists, frontend opens conflict choice in the move flow.
9. Frontend retries with `AUTO_RENAME` or `SKIP`.
10. Current listing and folder tree refresh.

### Move dialog

1. User opens `整理 -> 移动到`.
2. Frontend opens `MoveItemsDialog`.
3. User chooses a destination folder.
4. Frontend submits move request.
5. Backend returns success summary or conflict result.
6. Frontend offers conflict action and retries if needed.
7. Frontend refreshes affected directories and keeps the current path stable when possible.

### Appearance update

1. User opens `整理 -> 自定义图标`.
2. Frontend opens `AppearanceDialog`.
3. User edits emoji and, for folders, color.
4. Frontend submits appearance update.
5. Backend persists metadata.
6. Frontend updates list rows, detail panel, folder tree, and drag overlay source data.

## Error Handling

- moving into the same directory or a descendant directory returns a clear invalid-target result
- invalid conflict strategy returns validation error
- batch move must return actionable results instead of ambiguous partial failure text
- appearance updates for unauthorized or missing nodes return standard not-found or access-denied semantics
- folder color on a file node returns validation error
- frontend must not fall back to overwrite or silent rename without explicit user choice

## Testing

Backend:

- single-item move without conflict
- batch move without conflict
- move conflict with `AUTO_RENAME`
- move conflict with `SKIP`
- move rejection for self or descendant target
- appearance update for file emoji
- appearance update for folder emoji and color
- appearance clear behavior

Frontend:

- `cd frontend && npm run lint`
- compile-time verification for new dialog state and response types
- targeted tests for move conflict retry flow
- targeted tests for appearance field mapping
- targeted tests for drag state transitions and delayed drag start

Manual verification:

- drag one file into a folder
- drag one folder into another valid folder
- drag multiple selected items into a folder
- drag into sidebar tree target
- trigger conflict and choose auto rename
- trigger conflict and choose skip
- verify invalid self/descendant move is blocked
- save emoji for file and folder, then refresh
- save folder color, then refresh and check folder tree

## Risks And Constraints

- default HTML5 drag image behavior is not sufficient for the requested drag feel; a custom overlay is required
- adding appearance metadata must stay aligned across all workspace read models or the UI will look inconsistent
- batch move summaries must be explicit enough for the frontend to show meaningful completion feedback
- backend currently has single-item move behavior but not the full conflict-resolution contract, so both move endpoints should be upgraded to the response model defined in this spec before the Gemini-owned frontend work begins
