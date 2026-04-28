# Files Upload Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a cancelable upload task queue to the `Files` page and replace move/delete blocking feedback with bottom-right toasts and move-success actions.

**Architecture:** Keep the existing `POST /files/upload` route and wrap it in a frontend-only serial upload queue powered by `AbortController`. Expose the queue through a bottom-right panel plus a top-right topbar trigger, and centralize move/delete/upload feedback in a toast host rendered from `Files.tsx`.

**Tech Stack:** React 18, TypeScript, TanStack Query, MUI, Axios, lucide-react

---

## File Structure

### Modify

- `frontend/src/api/client.ts`
  - Keep `apiRequest` backward compatible while allowing request-level `signal`.
- `frontend/src/lib/files.ts`
  - Extend `uploadFile(...)` to accept an optional `AbortSignal`.
- `frontend/src/components/Topbar.tsx`
  - Add an optional right-side action slot for page-scoped task triggers.
- `frontend/src/components/DashboardLayout.tsx`
  - Pass the optional topbar action slot through to `Topbar`.
- `frontend/src/pages/Files.tsx`
  - Replace direct upload mutation orchestration with queue orchestration.
  - Render the upload panel and toast host.
  - Replace move/delete `alert(...)` completion feedback with toast state updates.

### Create

- `frontend/src/hooks/useUploadQueue.ts`
  - Own serial queue execution, per-task state, cancel-one, cancel-all, and queue visibility state.
- `frontend/src/components/files/UploadTaskTrigger.tsx`
  - Render the top-right upload task icon with active/waiting badge.
- `frontend/src/components/files/UploadTaskPanel.tsx`
  - Render the bottom-right collapsible upload queue UI with per-task and cancel-all actions.
- `frontend/src/components/files/WorkspaceActionToastHost.tsx`
  - Render bottom-right move/delete/upload toasts, including move-success `查看` and `恢复`.

### Validation

- Repo-approved command: `cd frontend && npm run lint`
- Manual verification only for interaction behavior because the repo does not define a frontend test runner command.

---

### Task 1: Add Abort-Aware Upload Plumbing

**Files:**
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/lib/files.ts`
- Test: `cd frontend && npm run lint`

- [ ] **Step 1: Extend `uploadFile` to accept `AbortSignal`**

```ts
export async function uploadFile(path: string, file: File, signal?: AbortSignal) {
  const params = new URLSearchParams({ path });
  const formData = new FormData();
  formData.append('file', file);
  return apiRequest<FileItem>({
    url: `/files/upload?${params.toString()}`,
    method: 'POST',
    data: formData,
    signal,
  });
}
```

- [ ] **Step 2: Keep `apiRequest` signal-compatible without changing existing call sites**

```ts
export type ApiRequestConfig = AxiosRequestConfig & {
  authRequired?: boolean;
  rawResponse?: boolean;
};

export async function apiRequest<T>(config: RetryableApiRequestConfig): Promise<T> {
  const requestConfig: RetryableApiRequestConfig = {
    _retryOnAuthFailure: true,
    ...config,
    headers: buildHeaders(config),
  };
  // Existing request flow stays the same. Axios already honors `signal`.
}
```

- [ ] **Step 3: Run lint to verify the upload helper still compiles**

Run: `cd frontend && npm run lint`  
Expected: `tsc --noEmit` completes without errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/client.ts frontend/src/lib/files.ts
git commit -m "refactor: add abort-aware file upload helper"
```

---

### Task 2: Build The Upload Queue State Layer

**Files:**
- Create: `frontend/src/hooks/useUploadQueue.ts`
- Test: `cd frontend && npm run lint`

- [ ] **Step 1: Create queue task types and hook contract**

```ts
export type UploadTaskStatus = 'waiting' | 'uploading' | 'success' | 'cancelled' | 'error';

export interface UploadQueueTask {
  id: string;
  file: File;
  filename: string;
  targetPath: string;
  status: UploadTaskStatus;
  errorMessage: string | null;
  createdAt: number;
  startedAt: number | null;
  finishedAt: number | null;
}

export interface UseUploadQueueOptions {
  onUpload: (task: UploadQueueTask, signal: AbortSignal) => Promise<void>;
  onTaskSuccess?: (task: UploadQueueTask) => void;
  onTaskError?: (task: UploadQueueTask, error: unknown) => void;
}
```

- [ ] **Step 2: Implement serial runner and queue mutations**

```ts
const controllersRef = useRef<Record<string, AbortController>>({});

const enqueue = useCallback((items: Array<{ file: File; targetPath: string }>) => {
  setTasks((current) => [
    ...current,
    ...items.map(({ file, targetPath }) => ({
      id: crypto.randomUUID(),
      file,
      filename: file.name,
      targetPath,
      status: 'waiting' as const,
      errorMessage: null,
      createdAt: Date.now(),
      startedAt: null,
      finishedAt: null,
    })),
  ]);
  setPanelOpen(true);
}, []);

const cancelTask = useCallback((taskId: string) => {
  controllersRef.current[taskId]?.abort();
  setTasks((current) =>
    current.map((task) =>
      task.id === taskId && (task.status === 'waiting' || task.status === 'uploading')
        ? { ...task, status: 'cancelled', finishedAt: Date.now() }
        : task,
    ),
  );
}, []);
```

- [ ] **Step 3: Handle active upload completion and abort mapping**

```ts
try {
  await onUpload(nextTask, controller.signal);
  setTasks((current) =>
    current.map((task) =>
      task.id === nextTask.id ? { ...task, status: 'success', finishedAt: Date.now() } : task,
    ),
  );
} catch (error) {
  const cancelled = error instanceof DOMException && error.name === 'AbortError';
  setTasks((current) =>
    current.map((task) =>
      task.id === nextTask.id
        ? {
            ...task,
            status: cancelled ? 'cancelled' : 'error',
            errorMessage: cancelled ? null : error instanceof Error ? error.message : '上传失败',
            finishedAt: Date.now(),
          }
        : task,
    ),
  );
}
```

- [ ] **Step 4: Expose derived counts and panel state**

```ts
const summary = useMemo(() => {
  const waiting = tasks.filter((task) => task.status === 'waiting').length;
  const uploading = tasks.filter((task) => task.status === 'uploading').length;
  const finished = tasks.filter((task) =>
    ['success', 'cancelled', 'error'].includes(task.status),
  ).length;
  return { waiting, uploading, finished, total: tasks.length };
}, [tasks]);
```

- [ ] **Step 5: Run lint to verify the hook compiles**

Run: `cd frontend && npm run lint`  
Expected: `tsc --noEmit` completes without errors

- [ ] **Step 6: Commit**

```bash
git add frontend/src/hooks/useUploadQueue.ts
git commit -m "feat: add files upload queue state"
```

---

### Task 3: Add Upload Queue UI And Topbar Trigger

**Files:**
- Modify: `frontend/src/components/Topbar.tsx`
- Modify: `frontend/src/components/DashboardLayout.tsx`
- Create: `frontend/src/components/files/UploadTaskTrigger.tsx`
- Create: `frontend/src/components/files/UploadTaskPanel.tsx`
- Modify: `frontend/src/pages/Files.tsx`
- Test: `cd frontend && npm run lint`

- [ ] **Step 1: Add an optional topbar action slot**

```tsx
interface TopbarProps {
  meta?: string;
  actionSlot?: React.ReactNode;
}

const Topbar: React.FC<TopbarProps> = ({ meta, actionSlot }) => {
  return (
    <header className="topbar-shell fixed inset-x-0 top-0 z-50">
      <div className="mx-auto flex h-[68px] max-w-[1600px] items-center justify-between px-4 lg:px-6">
        <Link to="/" className="min-w-0">
          <BrandMark size={38} subtitle="Personal Cloud" textClassName="hidden sm:block" />
        </Link>

        <div className="flex items-center gap-4">
          {meta ? <span className="text-xs font-semibold ...">{meta}</span> : null}
          {actionSlot}
          {/* existing theme toggle and user menu */}
        </div>
      </div>
    </header>
  );
};
```

- [ ] **Step 2: Thread the action slot through `DashboardLayout`**

```tsx
interface DashboardLayoutProps {
  children: React.ReactNode;
  title: string;
  hideHeader?: boolean;
  registerDropTarget?: (el: HTMLElement | null, path: string) => void;
  activeDropTarget?: string | null;
  topbarActionSlot?: React.ReactNode;
}

const DashboardLayout: React.FC<DashboardLayoutProps> = ({ topbarActionSlot, ...props }) => {
  return (
    <div className="flex h-screen flex-col ...">
      <Topbar meta={title} actionSlot={topbarActionSlot} />
      {/* existing layout */}
    </div>
  );
};
```

- [ ] **Step 3: Create the top-right task trigger**

```tsx
interface UploadTaskTriggerProps {
  activeCount: number;
  waitingCount: number;
  onClick: () => void;
}

export function UploadTaskTrigger({ activeCount, waitingCount, onClick }: UploadTaskTriggerProps) {
  const total = activeCount + waitingCount;
  return (
    <button
      type="button"
      aria-label="Open upload tasks"
      onClick={onClick}
      className="relative flex h-10 w-10 items-center justify-center rounded-full ..."
    >
      <Upload size={18} />
      {total > 0 ? <span className="absolute -right-1 -top-1 ...">{total}</span> : null}
    </button>
  );
}
```

- [ ] **Step 4: Create the bottom-right queue panel**

```tsx
interface UploadTaskPanelProps {
  open: boolean;
  tasks: UploadQueueTask[];
  summary: { total: number; waiting: number; uploading: number; finished: number };
  onToggleOpen: () => void;
  onCancelTask: (taskId: string) => void;
  onCancelAll: () => void;
  onClearFinished: () => void;
}
```

```tsx
<div className="fixed bottom-5 right-5 z-40 w-[360px] max-w-[calc(100vw-24px)]">
  <div className="rounded-3xl border border-white/60 bg-white/95 shadow-2xl backdrop-blur-xl dark:border-white/10 dark:bg-[#10131C]/95">
    <div className="flex items-center justify-between px-4 py-3">
      <div>
        <p className="text-sm font-bold">上传任务</p>
        <p className="text-xs text-slate-500">等待 {summary.waiting} · 上传中 {summary.uploading}</p>
      </div>
      <div className="flex items-center gap-2">
        <button onClick={onCancelAll}>全部取消</button>
        <button onClick={onToggleOpen}>收起</button>
      </div>
    </div>
    {open ? (
      <div className="max-h-[320px] overflow-auto px-3 pb-3">
        {tasks.map((task) => (
          <div key={task.id} className="mb-2 rounded-2xl border border-slate-200/70 px-3 py-2">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">{task.filename}</p>
                <p className="text-xs text-slate-500">{task.status}</p>
              </div>
              {task.status === 'waiting' || task.status === 'uploading' ? (
                <button onClick={() => onCancelTask(task.id)}>取消</button>
              ) : null}
            </div>
          </div>
        ))}
      </div>
    ) : null}
  </div>
</div>
```

- [ ] **Step 5: Wire the queue hook into `Files.tsx`**

```tsx
const uploadQueue = useUploadQueue({
  onUpload: async (task, signal) => {
    await uploadFile(task.targetPath, task.file, signal);
  },
  onTaskSuccess: () => {
    refreshCurrentListing();
  },
});
```

```tsx
<DashboardLayout
  title={categoryMeta?.title ?? '文件 Files'}
  registerDropTarget={registerDropTarget}
  activeDropTarget={activeDropTarget}
  topbarActionSlot={
    <UploadTaskTrigger
      activeCount={uploadQueue.summary.uploading}
      waitingCount={uploadQueue.summary.waiting}
      onClick={uploadQueue.openPanel}
    />
  }
>
  {/* existing files page */}
  <UploadTaskPanel
    open={uploadQueue.panelOpen}
    tasks={uploadQueue.tasks}
    summary={uploadQueue.summary}
    onToggleOpen={uploadQueue.togglePanel}
    onCancelTask={uploadQueue.cancelTask}
    onCancelAll={uploadQueue.cancelAll}
    onClearFinished={uploadQueue.clearFinished}
  />
</DashboardLayout>
```

- [ ] **Step 6: Replace direct `uploadMutation` callers with queue enqueue calls**

```tsx
function enqueueUploads(files: File[], folderPath?: string) {
  const targetPath = folderPath ? joinDirectoryPath(currentPath, folderPath) : currentPath;
  uploadQueue.enqueue(files.map((file) => ({ file, targetPath })));
}
```

```tsx
onChange={(event) => {
  const files = Array.from(event.target.files ?? []);
  if (files.length > 0) {
    enqueueUploads(files);
  }
  event.target.value = '';
}}
```

- [ ] **Step 7: Run lint to verify queue UI wiring**

Run: `cd frontend && npm run lint`  
Expected: `tsc --noEmit` completes without errors

- [ ] **Step 8: Commit**

```bash
git add \
  frontend/src/components/Topbar.tsx \
  frontend/src/components/DashboardLayout.tsx \
  frontend/src/components/files/UploadTaskTrigger.tsx \
  frontend/src/components/files/UploadTaskPanel.tsx \
  frontend/src/pages/Files.tsx
git commit -m "feat: add files upload task tray"
```

---

### Task 4: Add Workspace Toast Host And Replace Move/Delete Alerts

**Files:**
- Create: `frontend/src/components/files/WorkspaceActionToastHost.tsx`
- Modify: `frontend/src/pages/Files.tsx`
- Test: `cd frontend && npm run lint`

- [ ] **Step 1: Define toast entry types and timeout wrapper in `Files.tsx`**

```ts
type WorkspaceToastKind = 'move' | 'delete' | 'restore' | 'upload';
type WorkspaceToastPhase = 'loading' | 'success' | 'timeout' | 'error';

interface WorkspaceToastEntry {
  id: string;
  kind: WorkspaceToastKind;
  phase: WorkspaceToastPhase;
  title: string;
  detail?: string | null;
  moveResult?: MoveResponse | null;
  movedItems?: FileItem[] | null;
  targetPath?: string | null;
}

async function withTimeout<T>(promise: Promise<T>, timeoutMs = 15000): Promise<T> {
  return await Promise.race([
    promise,
    new Promise<T>((_, reject) => {
      window.setTimeout(() => reject(new Error('TASK_TIMEOUT')), timeoutMs);
    }),
  ]);
}
```

- [ ] **Step 2: Create a toast host component with action buttons**

```tsx
interface WorkspaceActionToastHostProps {
  toasts: WorkspaceToastEntry[];
  onClose: (toastId: string) => void;
  onViewMove: (toast: WorkspaceToastEntry) => void;
  onRestoreMove: (toast: WorkspaceToastEntry) => void;
}
```

```tsx
<div className="fixed bottom-5 right-5 z-50 flex w-[380px] max-w-[calc(100vw-24px)] flex-col gap-3">
  {toasts.map((toast) => (
    <div key={toast.id} className="rounded-3xl border border-white/60 bg-white/95 p-4 shadow-2xl ...">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-bold">{toast.title}</p>
          {toast.detail ? <p className="mt-1 text-xs text-slate-500">{toast.detail}</p> : null}
        </div>
        <button onClick={() => onClose(toast.id)}>关闭</button>
      </div>
      {toast.kind === 'move' && toast.phase === 'success' ? (
        <div className="mt-3 flex items-center gap-2">
          <button onClick={() => onViewMove(toast)}>查看</button>
          <button onClick={() => onRestoreMove(toast)}>恢复</button>
        </div>
      ) : null}
    </div>
  ))}
</div>
```

- [ ] **Step 3: Replace move mutation completion alerts with toast updates**

```tsx
const moveToastId = crypto.randomUUID();
pushToast({
  id: moveToastId,
  kind: 'move',
  phase: 'loading',
  title: '正在移动...',
});

const result = await withTimeout(
  items.length === 1 ? moveFile(items[0].id, targetPath) : batchMoveFiles(items.map((item) => item.id), targetPath),
);

replaceToast(moveToastId, {
  phase: 'success',
  title: '任务成功',
  detail: summarizeMoveResult(result),
  moveResult: result,
  movedItems: items,
  targetPath,
});
```

- [ ] **Step 4: Replace delete mutation completion status with toast updates**

```tsx
const deleteToastId = crypto.randomUUID();
pushToast({
  id: deleteToastId,
  kind: 'delete',
  phase: 'loading',
  title: '正在删除...',
});

await withTimeout(batchDeleteFiles(fileIds, mode));

replaceToast(deleteToastId, {
  phase: 'success',
  title: '任务成功',
  detail: `已处理 ${fileIds.length} 个项目`,
});
```

- [ ] **Step 5: Implement move-toast `查看` and `恢复` handlers**

```tsx
function handleViewMoveToast(toast: WorkspaceToastEntry) {
  if (toast.targetPath && toast.targetPath !== currentPath) {
    handlePathChange(toast.targetPath);
    return;
  }
  refreshCurrentListing();
}
```

```tsx
async function handleRestoreMoveToast(toast: WorkspaceToastEntry) {
  const restorable = toast.moveResult?.items.filter((item) => !item.skipped && item.fromPath);
  if (!restorable || restorable.length === 0) {
    return;
  }

  for (const item of restorable) {
    await moveFile(item.fileId, item.fromPath!);
  }

  pushToast({
    id: crypto.randomUUID(),
    kind: 'restore',
    phase: 'success',
    title: '任务成功',
    detail: '已恢复到原位置',
  });
  refreshCurrentListing();
}
```

- [ ] **Step 6: Render the toast host from `Files.tsx`**

```tsx
<WorkspaceActionToastHost
  toasts={workspaceToasts}
  onClose={dismissToast}
  onViewMove={handleViewMoveToast}
  onRestoreMove={handleRestoreMoveToast}
/>
```

- [ ] **Step 7: Run lint to verify toast integration**

Run: `cd frontend && npm run lint`  
Expected: `tsc --noEmit` completes without errors

- [ ] **Step 8: Manual verification**

Run locally in the app and verify:

- upload 3 files, cancel one waiting task
- upload 2 files, cancel the active task
- upload 3 files, click `全部取消`
- move one item and click `查看`
- move one item and click `恢复`
- delete one item and confirm the loading then success toast

- [ ] **Step 9: Commit**

```bash
git add \
  frontend/src/components/files/WorkspaceActionToastHost.tsx \
  frontend/src/pages/Files.tsx
git commit -m "feat: add files action toasts"
```

---

## Self-Review

### Spec coverage

- Upload queue panel: covered by Task 2 and Task 3
- Top-right upload trigger: covered by Task 3
- Single cancel and whole-queue cancel: covered by Task 2 and Task 3
- Move/delete bottom-right toasts: covered by Task 4
- Move success `查看` and `恢复`: covered by Task 4
- Abort-aware upload helper: covered by Task 1

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain
- Every file path is explicit
- Every code-changing step includes a concrete snippet
- Validation commands only use repo-approved commands

### Type consistency

- Upload queue uses `UploadQueueTask` and `UploadTaskStatus` consistently
- Toast host uses `WorkspaceToastEntry`, `WorkspaceToastKind`, and `WorkspaceToastPhase` consistently
- Upload helper signature is `uploadFile(path, file, signal?)` in both the plumbing and queue tasks

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-28-files-upload-queue.md`.

Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

The user has already asked to start execution immediately, so proceed with inline execution for this repo unless redirected.
