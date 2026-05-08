# Proxy Chunked Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-request local upload flow with a proxy chunked upload session flow that supports reliable large-file uploads, per-chunk concurrency, explicit server-side merging state, and safer failure handling.

**Architecture:** Reuse the existing `/api/v2/files/upload-sessions` domain model as the control plane. For proxy uploads, add a new per-part content endpoint and temporary part storage in the local storage backend. The frontend upload queue will switch from whole-file requests to session-driven chunk uploads, with progress derived from chunk state and a distinct `merging` phase after all chunks are sent.

**Tech Stack:** Spring Boot 3.3 / Java 17 / JPA / existing upload session services, Vite + React + TypeScript, Axios, local filesystem storage.

---

## File Structure

### Backend
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Controller.java`
  - Expose proxy part content upload endpoint for session part data.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
  - Add proxy-part upload orchestration, completion validation, merge lifecycle, and cleanup hooks.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionStateMachine.java`
  - Tighten allowed transitions for chunked proxy uploads and `COMPLETING` phase.
- Modify: `backend/src/main/java/com/yoyuzh/files/content/api/FileContentStorage.java`
  - Add temporary part storage, merge, and cleanup contracts.
- Modify: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/LocalFileContentStorage.java`
  - Persist session parts under a temp directory, merge parts into final blob, delete temp parts, emit concise upload logs.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionRuntimeStateService.java`
  - Keep runtime state updates aligned with proxy part completion and merging.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/RedisUploadSessionRuntimeStateService.java`
  - Add explicit `merging` phase writes without regressing existing direct multipart behavior.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2StrategyResponse.java`
  - Surface proxy-part upload route template for frontend.
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Response.java`
  - Ensure response shape supports frontend chunk orchestration without inference.
- Test: `backend/src/test/java/com/yoyuzh/files/upload/UploadSessionServiceTest.java` or the nearest existing upload session test package
  - Cover proxy chunk upload, part validation, merge, cancel, and expiry cleanup.
- Test: `backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2ControllerTest.java` or nearest existing controller test package
  - Cover new endpoint contract and ownership checks.

### Frontend
- Modify: `frontend/src/lib/files.ts`
  - Add upload session creation, part upload, session polling, completion, and cancel APIs.
- Modify: `frontend/src/api/types.ts`
  - Extend upload session and strategy types for proxy chunked mode.
- Modify: `frontend/src/hooks/useUploadQueue.ts`
  - Replace single-request file uploads with session-driven chunk scheduling, chunk-level concurrency, merging phase, and safer stall detection.
- Modify: `frontend/src/components/files/UploadTaskPanel.tsx`
  - Render `merging` state and chunk-aware progress/speed language.
- Modify: `frontend/src/pages/Files.tsx`
  - Keep file picker integration intact while adapting to new queue behavior.
- Modify: `frontend/src/pages/AccountSettings.tsx`
  - Keep thread-count setting semantics aligned with chunk concurrency.

### Verification
- Run: `cd backend && mvn test`
- Run: `cd frontend && npm run lint`
- Manual: local large-file upload through the existing UI with 1-thread and 2-thread settings.

---

### Task 1: Backend API Contract For Proxy Parts

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Controller.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2StrategyResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Response.java`
- Test: `backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2ControllerTest.java`

- [ ] **Step 1: Write the failing controller test for proxy part upload**

```java
@Test
void uploadProxyPartShouldAcceptMultipartFileForOwnedSession() throws Exception {
    mockMvc.perform(multipart("/api/v2/files/upload-sessions/{sessionId}/parts/{partIndex}/content", "session-1", 0)
            .file(new MockMultipartFile("file", "chunk.bin", "application/octet-stream", new byte[]{1, 2, 3}))
            .header(HttpHeaders.AUTHORIZATION, bearerToken())
            .with(request -> {
                request.setMethod("POST");
                return request;
            }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").value("session-1"));
}
```

- [ ] **Step 2: Run backend test to verify it fails**

Run: `cd backend && mvn test -Dtest=UploadSessionV2ControllerTest`
Expected: FAIL because `/parts/{partIndex}/content` is not mapped yet.

- [ ] **Step 3: Add the new controller route and strategy field wiring**

```java
@PostMapping("/{sessionId}/parts/{partIndex}/content")
public ApiV2Response<UploadSessionV2Response> uploadPartContent(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String sessionId,
                                                                @PathVariable int partIndex,
                                                                @RequestPart("file") MultipartFile file) {
    Long user = userDetailsService.loadUserId(userDetails.getUsername());
    return ApiV2Response.success(toResponse(uploadSessionService.uploadOwnedPartContent(user, sessionId, partIndex, file)));
}
```

```java
case PROXY -> new UploadSessionV2StrategyResponse(
        null,
        null,
        sessionBasePath + "/parts/{partIndex}/content",
        null,
        sessionBasePath + "/complete",
        "file"
);
```

- [ ] **Step 4: Run backend test to verify it passes**

Run: `cd backend && mvn test -Dtest=UploadSessionV2ControllerTest`
Expected: PASS for the new route and existing upload session responses remain green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Controller.java \
  backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2StrategyResponse.java \
  backend/src/main/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2Response.java \
  backend/src/test/java/com/yoyuzh/files/upload/internal/web/UploadSessionV2ControllerTest.java
git commit -m "feat: add proxy chunk upload endpoint"
```

### Task 2: Backend Storage Contract For Temporary Parts

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/content/api/FileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/LocalFileContentStorage.java`
- Test: `backend/src/test/java/com/yoyuzh/files/content/internal/infra/storage/LocalFileContentStorageTest.java`

- [ ] **Step 1: Write the failing storage test for temp part merge**

```java
@Test
void mergeUploadPartsShouldConcatenateChunksInIndexOrder() {
    storage.storeUploadPart("session-1", 0, new MockMultipartFile("file", "0.part", "application/octet-stream", new byte[]{1, 2}));
    storage.storeUploadPart("session-1", 1, new MockMultipartFile("file", "1.part", "application/octet-stream", new byte[]{3, 4}));

    storage.mergeUploadParts("session-1", List.of(0, 1), "uploads/blob-1");

    assertThat(storage.readBlob("uploads/blob-1")).containsExactly(1, 2, 3, 4);
}
```

- [ ] **Step 2: Run backend test to verify it fails**

Run: `cd backend && mvn test -Dtest=LocalFileContentStorageTest`
Expected: FAIL because temp part APIs do not exist yet.

- [ ] **Step 3: Add storage contract and local implementation**

```java
void storeUploadPart(String sessionId, int partIndex, MultipartFile file);
void mergeUploadParts(String sessionId, List<Integer> orderedPartIndexes, String objectKey);
void deleteUploadParts(String sessionId);
boolean uploadPartExists(String sessionId, int partIndex);
```

```java
@Override
public void mergeUploadParts(String sessionId, List<Integer> orderedPartIndexes, String objectKey) {
    Path target = resolveObjectKey(objectKey);
    createDirectories(target.getParent());
    try (OutputStream outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        for (Integer partIndex : orderedPartIndexes) {
            Files.copy(resolveUploadPartPath(sessionId, partIndex), outputStream);
        }
    } catch (IOException ex) {
        throw new BusinessException(ErrorCode.UNKNOWN, "File merge failed");
    }
}
```

- [ ] **Step 4: Run backend test to verify it passes**

Run: `cd backend && mvn test -Dtest=LocalFileContentStorageTest`
Expected: PASS for ordered merge, cleanup, and part existence checks.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/content/api/FileContentStorage.java \
  backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/LocalFileContentStorage.java \
  backend/src/test/java/com/yoyuzh/files/content/internal/infra/storage/LocalFileContentStorageTest.java
git commit -m "feat: add temporary upload part storage"
```

### Task 3: Backend Upload Session Service For Proxy Chunk Flow

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionStateMachine.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/RedisUploadSessionRuntimeStateService.java`
- Test: `backend/src/test/java/com/yoyuzh/files/upload/UploadSessionServiceTest.java`

- [ ] **Step 1: Write the failing service test for proxy part completion and merge**

```java
@Test
void completeProxySessionShouldMergeAllUploadedPartsAndMarkCompleted() {
    UploadSession session = createProxySession(16L, 8L, 2);

    service.uploadOwnedPartContent(USER_ID, session.getSessionId(), 0, multipart("file", new byte[]{1,2,3,4,5,6,7,8}));
    service.uploadOwnedPartContent(USER_ID, session.getSessionId(), 1, multipart("file", new byte[]{9,10,11,12,13,14,15,16}));

    UploadSession completed = service.completeOwnedSession(USER_ID, session.getSessionId());

    assertThat(completed.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
    assertThat(localStorage.readBlob(session.getObjectKey())).hasSize(16);
}
```

- [ ] **Step 2: Run backend test to verify it fails**

Run: `cd backend && mvn test -Dtest=UploadSessionServiceTest`
Expected: FAIL because proxy per-part upload and merge validation are not implemented.

- [ ] **Step 3: Implement proxy part upload orchestration**

```java
@Transactional
public UploadSession uploadOwnedPartContent(Long userId, String sessionId, int partIndex, MultipartFile file) {
    UploadSession session = getOwnedSession(userId, sessionId);
    ensureProxyPartReceivable(session, partIndex, file);
    fileContentStorage.storeUploadPart(session.getSessionId(), partIndex, file);
    UploadSession savedSession = markProxyPartUploaded(session, partIndex, file.getSize());
    uploadSessionRuntimeStateService.markUploading(savedSession, calculateUploadedBytes(savedSession), countUploadedParts(savedSession), savedSession.getUpdatedAt());
    return savedSession;
}
```

```java
private void ensureAllProxyPartsUploaded(UploadSession session) {
    for (int partIndex = 0; partIndex < session.getChunkCount(); partIndex++) {
        if (!fileContentStorage.uploadPartExists(session.getSessionId(), partIndex)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload part is missing");
        }
    }
}
```

```java
if (resolveUploadMode(session) == UploadSessionUploadMode.PROXY) {
    ensureAllProxyPartsUploaded(session);
    uploadSessionRuntimeStateService.markUploading(completingSession, completingSession.getSize(), completingSession.getChunkCount(), completingSession.getUpdatedAt());
    fileContentStorage.mergeUploadParts(session.getSessionId(), orderedPartIndexes(session), session.getObjectKey());
    fileContentStorage.deleteUploadParts(session.getSessionId());
}
```

- [ ] **Step 4: Run backend test to verify it passes**

Run: `cd backend && mvn test -Dtest=UploadSessionServiceTest`
Expected: PASS for proxy part upload, merge, cancel cleanup, and runtime-state updates.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java \
  backend/src/main/java/com/yoyuzh/files/upload/UploadSessionStateMachine.java \
  backend/src/main/java/com/yoyuzh/files/upload/RedisUploadSessionRuntimeStateService.java \
  backend/src/test/java/com/yoyuzh/files/upload/UploadSessionServiceTest.java
git commit -m "feat: support proxy chunk upload sessions"
```

### Task 4: Backend Logging And Expiry Cleanup For Proxy Parts

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/LocalFileContentStorage.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/upload/UploadSessionCleanupScheduler.java`
- Test: `backend/src/test/java/com/yoyuzh/files/upload/UploadSessionCleanupSchedulerTest.java`

- [ ] **Step 1: Write the failing cleanup test for expired proxy parts**

```java
@Test
void pruneExpiredUploadSessionsShouldDeleteProxyPartFiles() {
    UploadSession expired = createExpiredProxySession();
    localStorage.storeUploadPart(expired.getSessionId(), 0, multipart("file", new byte[]{1,2,3}));

    scheduler.pruneExpiredUploadSessions();

    assertThat(localStorage.uploadPartExists(expired.getSessionId(), 0)).isFalse();
}
```

- [ ] **Step 2: Run backend test to verify it fails**

Run: `cd backend && mvn test -Dtest=UploadSessionCleanupSchedulerTest`
Expected: FAIL because expiry does not remove proxy temp parts yet.

- [ ] **Step 3: Add cleanup path and concise diagnostics**

```java
private void cleanupProxyUploadParts(UploadSession session) {
    if (resolveUploadMode(session) == UploadSessionUploadMode.PROXY) {
        fileContentStorage.deleteUploadParts(session.getSessionId());
    }
}
```

```java
log.info("upload part stored sessionId={} partIndex={} size={}B", sessionId, partIndex, size);
log.info("upload merge started sessionId={} partCount={}", sessionId, orderedPartIndexes.size());
log.info("upload merge finished sessionId={} objectKey={}", sessionId, objectKey);
```

- [ ] **Step 4: Run backend test to verify it passes**

Run: `cd backend && mvn test -Dtest=UploadSessionCleanupSchedulerTest`
Expected: PASS and no regression in existing expiry behavior.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/content/internal/infra/storage/LocalFileContentStorage.java \
  backend/src/main/java/com/yoyuzh/files/upload/UploadSessionService.java \
  backend/src/main/java/com/yoyuzh/files/upload/UploadSessionCleanupScheduler.java \
  backend/src/test/java/com/yoyuzh/files/upload/UploadSessionCleanupSchedulerTest.java
git commit -m "feat: clean proxy upload parts on expiry"
```

### Task 5: Frontend Session APIs And Type Wiring

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/lib/files.ts`

- [ ] **Step 1: Add the missing frontend upload session types**

```ts
export type UploadSessionStrategy = {
  prepareSingleUrl: string | null;
  proxyContentUrl: string | null;
  proxyPartContentUrl: string | null;
  completeUrl: string | null;
  fileField: string | null;
};

export type UploadSessionRuntimeState = {
  phase: string;
  uploadedBytes: number;
  uploadedPartCount: number;
  progressPercent: number | null;
  lastUpdatedAt: string | null;
  expiresAt: string | null;
};
```

- [ ] **Step 2: Verify TypeScript fails before API functions are added**

Run: `cd frontend && npm run lint`
Expected: FAIL after types are referenced by new queue code and helper signatures are still missing.

- [ ] **Step 3: Add upload-session API helpers**

```ts
export async function createUploadSession(payload: { path: string; filename: string; contentType: string; size: number }) {
  return apiRequest<UploadSessionResponse>({
    url: '/v2/files/upload-sessions',
    method: 'POST',
    data: payload,
  });
}

export async function uploadProxyPart(sessionId: string, partIndex: number, blob: Blob, signal?: AbortSignal, onProgress?: (p: { loaded: number; total: number }) => void) {
  const formData = new FormData();
  formData.append('file', blob, `part-${partIndex}.bin`);
  return apiRequest<UploadSessionResponse>({
    url: `/v2/files/upload-sessions/${sessionId}/parts/${partIndex}/content`,
    method: 'POST',
    data: formData,
    signal,
    timeout: 0,
    onUploadProgress: (event) => onProgress?.({ loaded: event.loaded, total: event.total ?? blob.size }),
  });
}
```

- [ ] **Step 4: Run TypeScript verification**

Run: `cd frontend && npm run lint`
Expected: PASS for upload session types and helper function signatures.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/lib/files.ts
git commit -m "feat: add upload session client APIs"
```

### Task 6: Frontend Upload Queue Refactor To Chunk Scheduling

**Files:**
- Modify: `frontend/src/hooks/useUploadQueue.ts`
- Modify: `frontend/src/pages/Files.tsx`

- [ ] **Step 1: Replace the queue data model with chunk-aware task state**

```ts
export type UploadStatus = 'waiting' | 'uploading' | 'merging' | 'success' | 'cancelled' | 'error';

export interface UploadPartState {
  index: number;
  size: number;
  uploadedBytes: number;
  status: 'waiting' | 'uploading' | 'success' | 'error';
  startedAt?: number;
  lastByteChangeAt?: number;
}
```

- [ ] **Step 2: Make TypeScript fail before the new scheduler is fully wired**

Run: `cd frontend && npm run lint`
Expected: FAIL because old whole-file upload flow no longer satisfies the task model.

- [ ] **Step 3: Implement session-driven scheduling**

```ts
const session = await createUploadSession({
  path: originalTask.path,
  filename: originalTask.file.name,
  contentType: originalTask.file.type || 'application/octet-stream',
  size: originalTask.file.size,
});

const parts = sliceFileIntoParts(originalTask.file, session.chunkSize);
await uploadPartsWithPool(taskId, session, parts, uploadConcurrency);
await completeUploadSession(session.sessionId);
```

```ts
function shouldAbortPart(part: UploadPartState, now: number) {
  return part.status === 'uploading' && part.lastByteChangeAt != null && now - part.lastByteChangeAt >= STALL_TIMEOUT_MS;
}
```

```ts
function deriveTaskProgress(parts: UploadPartState[], fileSize: number) {
  const uploadedBytes = parts.reduce((sum, part) => sum + part.uploadedBytes, 0);
  return {
    uploadedBytes,
    progress: fileSize > 0 ? Math.min(100, Math.round((uploadedBytes / fileSize) * 100)) : 0,
  };
}
```

- [ ] **Step 4: Run TypeScript verification**

Run: `cd frontend && npm run lint`
Expected: PASS with no references left to the old single-request upload path in the queue.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useUploadQueue.ts frontend/src/pages/Files.tsx
git commit -m "feat: schedule uploads by chunk session"
```

### Task 7: Frontend UI States For Merging And Safer Speed Semantics

**Files:**
- Modify: `frontend/src/components/files/UploadTaskPanel.tsx`
- Modify: `frontend/src/components/files/UploadTaskTrigger.tsx`

- [ ] **Step 1: Update task panel language for merging**

```tsx
{task.status === 'uploading' && '正在上传...'}
{task.status === 'merging' && '服务器合并中...'}
{task.status === 'waiting' && '等待中'}
{task.status === 'success' && '上传成功'}
```

- [ ] **Step 2: Remove misleading zero-speed wording during merge**

```tsx
<span>
  {task.status === 'uploading' ? `${formatBytes(task.speedBytesPerSecond)}/s` : ''}
  {task.status === 'merging' ? '处理中' : ''}
</span>
```

- [ ] **Step 3: Run TypeScript verification**

Run: `cd frontend && npm run lint`
Expected: PASS with `merging` state rendered everywhere upload status is displayed.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/files/UploadTaskPanel.tsx frontend/src/components/files/UploadTaskTrigger.tsx
git commit -m "feat: show merging state in upload panel"
```

### Task 8: End-To-End Verification In Local Dev

**Files:**
- Modify: none unless verification reveals defects

- [ ] **Step 1: Run backend verification suite**

Run: `cd backend && mvn test`
Expected: PASS for all backend tests including upload session coverage.

- [ ] **Step 2: Run frontend verification**

Run: `cd frontend && npm run lint`
Expected: PASS with no TypeScript errors.

- [ ] **Step 3: Manual local verification with a large file**

Run the existing project locally, then verify in the browser:
- Upload a file larger than 100 MB with thread count `1`
- Upload a file larger than 100 MB with thread count `2`
- Confirm per-file progress advances by chunk
- Confirm a completed upload enters `服务器合并中...` before `上传成功`
- Confirm active tasks remain at the top and completed tasks move to the bottom
- Confirm a stalled part fails only after 30 seconds without byte movement for that part

Expected:
- No false `0 B/s` terminal state during server merge
- No final-stage network error when bytes are already fully sent
- Large-file uploads succeed locally through proxy chunked flow

- [ ] **Step 4: Commit final verification-only note if any small fixes were needed**

```bash
git add <only-if-verification-revealed-fixes>
git commit -m "fix: polish proxy chunk upload verification issues"
```

## Self-Review

- Spec coverage: the plan covers session control plane reuse, proxy chunk transport, local temp-part storage, merge state, cleanup, frontend chunk scheduling, and verification.
- Placeholder scan: removed generic placeholders; every task points to concrete files, commands, and expected outcomes.
- Type consistency: plan consistently uses `proxy part content`, `merging`, `chunk concurrency`, and existing `UploadSession` / `UploadSessionV2` terminology.
