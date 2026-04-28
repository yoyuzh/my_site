# Recycle Bin Table And Delete Modes Design

## Goal

Implement a complete recycle-bin workflow with two user-facing outcomes:

1. The recycle-bin page should use a table-style layout that matches the provided reference.
2. File deletion should offer two explicit modes:
   - move to recycle bin
   - delete permanently

Permanent delete must support both single-item and batch deletion.

## Scope

In scope:

- `frontend/src/pages/RecycleBin.tsx`
- the frontend delete flow in `frontend/src/pages/Files.tsx`
- frontend API helpers and types for recycle-bin permanent delete and delete mode selection
- backend file delete endpoints and workspace file service methods
- backend recycle-bin delete behavior for single-item and batch-item permanent deletion

Out of scope:

- unrelated visual redesign of the workspace
- admin delete behavior changes
- recycle-bin search, sorting, or filtering
- changing the existing recycle retention policy

## User Experience

### Recycle-bin page

The recycle-bin page should render as a table-like list with stable columns:

- `名称`
- `大小`
- `过期时间`
- `原始位置`
- `操作`

Behavior:

- the page itself should not become the scroll container
- the list region should own scrolling
- pagination stays at the bottom of the page content
- each row shows restore and permanent-delete actions
- original location is shown from the stored recycle source path
- expiration is shown as a clear date/time, not only inline descriptive prose

### Delete flow

When deleting from the main files page, the user should choose between:

- `移到回收站`
- `直接删除`

This choice should work for:

- a single file or directory
- batch selection

The copy should make the consequence clear:

- recycle-bin delete is recoverable until expiration
- permanent delete is irreversible

## Frontend Design

### Recycle-bin table

`RecycleBin.tsx` will move from stacked cards to a table-style surface built with the existing local styling primitives instead of introducing a new design system dependency.

Data needed per row:

- `filename`
- `size`
- `expiresAt`
- `path` as original location
- `directory`
- `id`

Actions:

- restore button
- permanent delete button

### Delete confirmation

`Files.tsx` currently uses `window.confirm(...)` for delete. That is not enough for two delete modes.

Replace it with a small controlled dialog that:

- shows item count or file name
- presents both delete modes as distinct actions
- defaults to the safer recycle-bin path
- supports single-item and batch-item deletion through the same dialog state

### API additions

Frontend API helpers will add:

- permanent delete for recycle-bin items
- delete mode support for single delete
- delete mode support for batch delete

The current delete callers should keep working after the refactor by explicitly choosing the recycle-bin mode from the new dialog instead of silently relying on current backend behavior.

## Backend Design

### Delete modes

Current behavior:

- `DELETE /api/files/{fileId}` moves the item to recycle bin
- `POST /api/files/batch/delete` loops through the same recycle behavior

Target behavior:

- both endpoints accept a delete mode
- `RECYCLE` preserves current behavior
- `PERMANENT` deletes the owned active item immediately

Recommended contract:

- keep `DELETE /api/files/{fileId}` and add query param `mode`
- keep `POST /api/files/batch/delete` and extend request payload with `mode`

This keeps route churn low while making the choice explicit.

### Recycle-bin permanent delete

Add a dedicated endpoint for recycle-bin permanent delete so the frontend row action is clear and does not overload restore routes.

Recommended contract:

- `DELETE /api/files/recycle-bin/{fileId}`

Behavior:

- only accepts items already in recycle bin
- validates ownership
- deletes the recycle group rooted at that recycle-bin entry
- removes related file records
- releases blob references through existing content lifecycle APIs

For directories, deleting the recycle root should permanently delete the whole recycle group.

### Service-layer behavior

`FileService` will gain explicit methods for:

- deleting active files with `RECYCLE` or `PERMANENT` mode
- batch delete with delete mode
- permanently deleting recycle-bin items

The permanent path should reuse existing ownership checks, content cleanup hooks, and activity invalidation where applicable, without adding fallback branches.

## Data Flow

### Active file delete

1. User opens delete dialog from files page.
2. User chooses recycle or permanent delete.
3. Frontend calls single or batch delete endpoint with mode.
4. Backend validates ownership.
5. Backend either recycles or permanently deletes.
6. Frontend refreshes file list state.

### Recycle-bin permanent delete

1. User clicks permanent delete on a recycle-bin row.
2. Frontend confirms irreversible action.
3. Frontend calls recycle-bin permanent delete endpoint.
4. Backend validates ownership and recycle-bin status.
5. Backend deletes the recycle group and associated blob references.
6. Frontend refetches recycle-bin data and keeps pagination stable.

## Error Handling

- invalid delete mode returns a standard validation error
- permanent delete on a non-recycle item returns file-not-found or invalid-state semantics
- batch permanent delete should fail clearly if an item is unauthorized
- frontend dialog should surface backend errors without silently falling back to recycle mode

## Testing

Backend:

- single delete with `RECYCLE`
- single delete with `PERMANENT`
- batch delete with `RECYCLE`
- batch delete with `PERMANENT`
- recycle-bin permanent delete for file
- recycle-bin permanent delete for directory root and group cleanup

Frontend:

- `cd frontend && npm run lint`
- verify recycle-bin table layout compiles
- verify delete dialog state compiles for single and batch flows

Manual verification:

- delete one item to recycle bin, confirm it appears in recycle bin
- permanently delete one active item, confirm it does not appear in recycle bin
- permanently delete one recycle-bin item, confirm it disappears immediately
- batch delete multiple items with permanent mode

## Risks And Constraints

- the frontend reference is visually darker than the current recycle-bin page; implementation should match structure and density first while staying consistent with the app shell
- permanent delete must not bypass content reference cleanup
- batch permanent delete should not leave partial UI state unclear if one item fails
