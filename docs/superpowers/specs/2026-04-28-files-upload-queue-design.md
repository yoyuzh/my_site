# Files Upload Queue And Workspace Toast Design

## Goal

Implement two focused workspace interaction upgrades in the `Files` page:

1. Add a visible upload task queue for file uploads started from `Files`.
2. Replace move and delete blocking prompts with bottom-right toast feedback.

This phase only targets upload cancellation, not resumable upload.

Required upload outcomes:

- each selected file becomes its own upload task
- the user can cancel one task
- the user can cancel the whole queue
- the current task list is reachable from both the bottom-right queue surface and a top-right header icon

Required move and delete outcomes:

- move and delete actions show progress toasts instead of blocking `alert(...)`
- timeout is surfaced as a dedicated task-timeout toast
- success is surfaced as a success toast
- move success toast exposes `查看` and `恢复` actions

## Scope

In scope:

- `frontend/src/pages/Files.tsx`
- shared header surface under `frontend/src/components/Topbar.tsx`
- new upload queue UI components under `frontend/src/components/files/**`
- frontend helper logic under `frontend/src/lib/files.ts` when needed for abort-aware upload calls

Out of scope:

- resumable upload
- multipart upload session integration
- offline download task UI
- backend route changes
- cross-refresh task persistence

## User Experience

### Upload queue surface

The upload queue should feel like a lightweight desktop transfer tray rather than a modal workflow.

Behavior:

- a compact queue card is pinned to the bottom-right corner of the `Files` page
- the card shows aggregate state such as total tasks, active task, and whether the queue is idle, uploading, or completed
- the card can collapse to a compact summary and expand to show individual tasks
- the top-right header shows an upload-task icon button when the user is inside the authenticated app shell
- clicking the header icon opens the same queue panel concept so the queue remains discoverable even when the page is scrolled

### Upload task behavior

Each file selected for upload becomes an independent task with a local task record.

States:

- `waiting`
- `uploading`
- `success`
- `cancelled`
- `error`

Behavior:

- uploads remain serial to match the current implementation and keep cancellation predictable
- cancelling the active task aborts the underlying browser request immediately
- cancelling a waiting task removes it from execution and marks it cancelled in the visible history
- cancelling the whole queue aborts the active request and marks all waiting tasks cancelled
- successful tasks remain visible until the user dismisses or clears the queue
- upload completion should still refresh the current listing as files finish

### Move and delete toast behavior

Move and delete feedback should no longer interrupt the user with browser-native dialogs after the action has already started.

Behavior:

- when delete is submitted, a bottom-right toast appears with `正在删除...`
- when move is submitted, a bottom-right toast appears with `正在移动...`
- if the request exceeds the timeout threshold, the toast changes to `任务超时`
- if the request succeeds, the toast changes to `任务成功`
- move success toast includes `查看` and `恢复` icon actions

`查看` behavior:

- if the move target differs from the current folder, navigate to the destination folder
- if already in the destination folder, refresh the current listing instead of adding a separate highlight requirement in this phase

`恢复` behavior:

- restore means issuing a reverse move using the `fromPath` values returned by the successful move result
- restore is only guaranteed within the current session and current queue of toasts
- restore should operate on moved items only, not skipped items

## Frontend Design

### Files page orchestration

`Files.tsx` remains the page orchestrator for:

- current folder and visible listing state
- upload queue state
- move and delete mutation state
- toast lifecycle state
- queue-triggered listing refreshes

To keep `Files.tsx` from growing further, the UI details should move out into small focused helpers and components.

### Suggested component split

Recommended additions:

- `UploadTaskPanel`
- `UploadTaskTrigger`
- `WorkspaceActionToastHost`
- `useUploadQueue`

Responsibilities:

- `UploadTaskPanel` renders the bottom-right queue tray and expanded task list
- `UploadTaskTrigger` renders the top-right header icon and unread/active count badge
- `WorkspaceActionToastHost` renders move/delete/upload status toasts and action buttons
- `useUploadQueue` owns task state, serial execution, abort controllers, and cancel APIs

### Upload queue state model

Each upload task record should include:

- local task id
- filename
- destination path
- source `File`
- status
- optional error message
- created time
- optional started time
- optional finished time
- optional `AbortController`

Queue-level derived state should include:

- active task id
- waiting count
- uploading count
- finished count
- whether the panel is open or collapsed

### Header integration

The current top bar is shared across dashboard pages, so the queue trigger should be injected in a way that does not hard-code `Files` behavior into unrelated pages.

Recommended approach:

- extend `Topbar` with an optional action slot or task-trigger prop
- `DashboardLayout` passes the slot through
- `Files.tsx` provides the upload task trigger only for the files surface

This keeps the shared top bar reusable while still putting the entry at the requested top-right location.

### Upload request integration

The current `uploadFile(path, file)` helper is not abort-aware.

Recommended change:

- extend the helper to accept an optional `AbortSignal`
- keep the existing route `POST /files/upload`
- keep one HTTP request per file

The queue runner should:

1. take the next waiting task
2. create an `AbortController`
3. call `uploadFile(targetPath, file, signal)`
4. mark the task as `success`, `cancelled`, or `error`
5. continue to the next waiting task unless the queue was fully cancelled

### Toast model

Workspace toast entries should be explicit state objects instead of one-off strings.

Each toast entry should include:

- id
- kind such as `move` or `delete`
- phase such as `loading`, `success`, `timeout`, `error`
- title text
- optional detail text
- optional action payload
- created time

Recommended timeout handling:

- wrap move and delete requests with a frontend timeout guard
- treat timeout as a user-visible state even if the underlying request eventually resolves later
- keep timeout copy explicit and non-ambiguous

### Move success undo model

The current move response already carries `fromPath` and `toPath` per item, which is enough to support a one-click restore for this phase.

Restore flow:

1. read the move-success toast payload
2. collect moved items whose `skipped` flag is false and `fromPath` is present
3. group by original parent path when needed
4. issue reverse move calls back to each original path
5. show a follow-up toast for restore success or failure

For this phase:

- restore only covers items whose successful move result includes a usable `fromPath`
- restore does not try to reconstruct renamed conflicts beyond what the move API already reports
- if a reverse move hits a new conflict, surface the backend message in toast form and stop silent fallback

## Data Flow

### Upload queue

1. User selects one or more files from upload, paste, or create-file flows that already end in upload.
2. Frontend creates one queue task per file.
3. Queue panel becomes visible and shows the new tasks.
4. Queue runner starts the first waiting task if nothing is currently uploading.
5. Active upload resolves into `success`, `cancelled`, or `error`.
6. On success, frontend refreshes the current listing.
7. Runner advances to the next waiting task until none remain or the queue was fully cancelled.

### Single task cancel

1. User clicks cancel on the active or waiting task.
2. If waiting, task becomes `cancelled` immediately.
3. If uploading, abort controller cancels the request.
4. Task becomes `cancelled`.
5. Runner advances to the next waiting task.

### Whole queue cancel

1. User clicks cancel-all from the queue panel.
2. Active request is aborted if present.
3. All waiting tasks become `cancelled`.
4. Queue stops advancing until new tasks are added.

### Move toast with actions

1. User confirms move from dialog or drag/drop flow.
2. Frontend shows `正在移动...` toast immediately.
3. Move request resolves.
4. Success toast replaces the loading toast.
5. User may click `查看` to navigate to destination.
6. User may click `恢复` to trigger reverse move.

### Delete toast

1. User confirms delete mode from the existing delete dialog.
2. Frontend shows `正在删除...` toast immediately.
3. Delete request resolves into success, timeout, or error.
4. Toast updates in place and listing refresh remains unchanged.

## Error Handling

- upload abort should map to `cancelled`, not `error`
- upload network failures should show task-specific error text
- queue cancellation should not clear successful tasks
- move and delete timeout should show `任务超时` even if the backend response is absent
- move restore failures should surface a dedicated failure toast and should not silently retry with different behavior
- invalid move targets and backend validation errors should reuse toast feedback instead of `alert(...)`

## Testing

Frontend:

- `cd frontend && npm run lint`

Manual verification:

- upload multiple files and cancel a waiting task
- upload multiple files and cancel the active task
- upload multiple files and cancel the whole queue
- verify the bottom-right queue panel and top-right trigger stay in sync
- move one item and use the success toast `查看`
- move one item and use the success toast `恢复`
- delete one item and verify progress and success toast states
- simulate or force a timeout path for move or delete and verify `任务超时`

Validation gaps:

- the repo does not define frontend automated interaction tests for this surface
- backend timeout simulation is not part of the checked-in command set, so timeout verification may require a local manual probe

## Risks And Constraints

- the current upload API is whole-file request based, so this phase can cancel but cannot resume
- `Files.tsx` is already large, so queue and toast logic must be extracted enough to avoid another large state tangle
- shared top-bar integration should stay optional so non-files pages do not gain dead task UI
- restore depends on the move result payload for the current session and is not intended as a durable undo history
