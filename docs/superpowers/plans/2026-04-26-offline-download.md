# Offline Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Cloudreve-style offline download with `aria2 + qBittorrent`, including task creation, task progress, BT file selection, cancel flow, and automatic import into the user's workspace.

**Architecture:** Keep offline download business truth in `transfer`, use `platform.job` only for async execution and progress snapshots, and reuse existing workspace/content import boundaries for final file creation. Route `HTTP/HTTPS` sources to `aria2`, route `magnet` and `.torrent` sources to `qBittorrent`, and persist a `RemoteDownload` record that is linked to `BackgroundTaskType.REMOTE_DOWNLOAD`.

**Tech Stack:** Spring Boot 3.3, Java 17, JPA/Hibernate, existing background task worker infrastructure, React 19, Vite, TypeScript, TanStack Query.

---

## File Structure Map

### Backend files to create

- `backend/src/main/java/com/yoyuzh/transfer/api/RemoteDownloadApi.java`
- `backend/src/main/java/com/yoyuzh/transfer/api/CreateRemoteDownloadCommand.java`
- `backend/src/main/java/com/yoyuzh/transfer/api/RemoteDownloadListItemResponse.java`
- `backend/src/main/java/com/yoyuzh/transfer/api/RemoteDownloadDetailResponse.java`
- `backend/src/main/java/com/yoyuzh/transfer/api/SelectRemoteDownloadFilesCommand.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadTask.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadStatus.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadSourceType.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/domain/DownloadEngineType.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadCandidateFile.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/infra/RemoteDownloadTaskRepository.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/application/RemoteDownloadService.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/application/RuntimeRemoteDownloadApi.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/application/RemoteDownloadBackgroundTaskPayload.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/application/RemoteDownloadImportService.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/infra/Aria2Client.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/infra/QbittorrentClient.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/infra/DownloaderProperties.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/web/RemoteDownloadController.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/web/CreateRemoteDownloadRequest.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/web/SelectRemoteDownloadFilesRequest.java`
- `backend/src/main/java/com/yoyuzh/platform/job/internal/application/RemoteDownloadBackgroundTaskHandler.java`

### Backend files to modify

- `backend/src/main/java/com/yoyuzh/transfer/internal/web/TransferController.java`
- `backend/src/main/java/com/yoyuzh/transfer/api/TransferSessionApi.java`
- `backend/src/main/java/com/yoyuzh/transfer/internal/application/RuntimeTransferSessionApi.java`
- `backend/src/main/java/com/yoyuzh/platform/job/internal/application/BackgroundTaskWorker.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`

### Backend tests

- `backend/src/test/java/com/yoyuzh/transfer/internal/web/RemoteDownloadControllerTest.java`
- `backend/src/test/java/com/yoyuzh/transfer/internal/application/RuntimeRemoteDownloadApiTest.java`
- `backend/src/test/java/com/yoyuzh/transfer/internal/application/RemoteDownloadServiceTest.java`
- `backend/src/test/java/com/yoyuzh/transfer/internal/application/RemoteDownloadImportServiceTest.java`
- `backend/src/test/java/com/yoyuzh/platform/job/internal/application/RemoteDownloadBackgroundTaskHandlerTest.java`
- `backend/src/test/java/com/yoyuzh/transfer/internal/infra/RemoteDownloadTaskRepositoryIntegrationTest.java`

### Frontend files to create

- `frontend/src/components/files/CreateRemoteDownloadDialog.tsx`
- `frontend/src/lib/remote-downloads.ts`

### Frontend files to modify

- `frontend/src/api/types.ts`
- `frontend/src/api/queries.ts`
- `frontend/src/pages/Files.tsx`
- `frontend/src/pages/Tasks.tsx`
- `frontend/src/lib/tasks.ts`

---

### Task 1: Define Remote Download Domain Contracts

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/transfer/api/RemoteDownloadApi.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/api/CreateRemoteDownloadCommand.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/api/RemoteDownloadListItemResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/api/RemoteDownloadDetailResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/api/SelectRemoteDownloadFilesCommand.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadTask.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadStatus.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadSourceType.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/domain/DownloadEngineType.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/domain/RemoteDownloadCandidateFile.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/application/RemoteDownloadServiceTest.java`

- [ ] **Step 1: Write the failing domain behavior test**

```java
@Test
void shouldRouteSourceTypesToExpectedEngineAndStatus() {
    RemoteDownloadTask httpTask = RemoteDownloadTask.createHttp(
            7L,
            "/downloads",
            "https://example.com/demo.zip",
            "local-default"
    );

    assertThat(httpTask.getSourceType()).isEqualTo(RemoteDownloadSourceType.HTTP);
    assertThat(httpTask.getEngineType()).isEqualTo(DownloadEngineType.ARIA2);
    assertThat(httpTask.getStatus()).isEqualTo(RemoteDownloadStatus.PENDING);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=RemoteDownloadServiceTest test`

Expected: FAIL because `RemoteDownloadTask` and related types do not exist yet.

- [ ] **Step 3: Write the minimal domain contracts**

```java
public enum RemoteDownloadStatus {
    PENDING,
    SUBMITTED,
    FETCHING_METADATA,
    AWAITING_FILE_SELECTION,
    DOWNLOADING,
    IMPORTING,
    COMPLETED,
    FAILED,
    CANCELED
}
```

```java
public enum DownloadEngineType {
    ARIA2,
    QBITTORRENT
}
```

```java
public interface RemoteDownloadApi {
    RemoteDownloadDetailResponse create(Long userId, CreateRemoteDownloadCommand command);
    java.util.List<RemoteDownloadListItemResponse> listOwned(Long userId);
    RemoteDownloadDetailResponse getOwned(Long userId, Long id);
    RemoteDownloadDetailResponse selectFiles(Long userId, Long id, SelectRemoteDownloadFilesCommand command);
    RemoteDownloadDetailResponse cancel(Long userId, Long id);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=RemoteDownloadServiceTest test`

Expected: PASS for the new domain construction test.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/transfer/api backend/src/main/java/com/yoyuzh/transfer/internal/domain backend/src/test/java/com/yoyuzh/transfer/internal/application/RemoteDownloadServiceTest.java
git commit -m "feat: add remote download domain contracts"
```

### Task 2: Build Transfer-Side API, Persistence, and Controller

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/infra/RemoteDownloadTaskRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/application/RemoteDownloadService.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/application/RuntimeRemoteDownloadApi.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/web/RemoteDownloadController.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/web/CreateRemoteDownloadRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/web/SelectRemoteDownloadFilesRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/transfer/internal/web/TransferController.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/web/RemoteDownloadControllerTest.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/application/RuntimeRemoteDownloadApiTest.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/infra/RemoteDownloadTaskRepositoryIntegrationTest.java`

- [ ] **Step 1: Write the failing controller test**

```java
@Test
void shouldCreateRemoteDownloadTaskForHttpSource() throws Exception {
    when(remoteDownloadApi.create(eq(7L), any())).thenReturn(createdResponse());

    mockMvc.perform(multipart("/api/transfer/remote-downloads")
                    .param("sourceType", "HTTP")
                    .param("sourceValue", "https://example.com/demo.zip")
                    .param("targetPath", "/downloads")
                    .with(jwt())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=RemoteDownloadControllerTest test`

Expected: FAIL because `/api/transfer/remote-downloads` controller does not exist.

- [ ] **Step 3: Write minimal persistence and controller flow**

```java
@RestController
@RequestMapping("/api/transfer/remote-downloads")
@RequiredArgsConstructor
class RemoteDownloadController {

    private final RemoteDownloadApi remoteDownloadApi;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RemoteDownloadDetailResponse> create(@AuthenticationPrincipal UserDetails userDetails,
                                                            @ModelAttribute @Valid CreateRemoteDownloadRequest request) {
        Long userId = userDetailsService.loadUserId(userDetails.getUsername());
        return ApiResponse.success(remoteDownloadApi.create(userId, request.toCommand()));
    }
}
```

```java
public interface RemoteDownloadTaskRepository extends JpaRepository<RemoteDownloadTask, Long> {
    Optional<RemoteDownloadTask> findByIdAndUserId(Long id, Long userId);
    List<RemoteDownloadTask> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -Dtest=RemoteDownloadControllerTest,RuntimeRemoteDownloadApiTest,RemoteDownloadTaskRepositoryIntegrationTest test`

Expected: PASS for create/list/get/select/cancel contract coverage.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/transfer/internal/application backend/src/main/java/com/yoyuzh/transfer/internal/infra backend/src/main/java/com/yoyuzh/transfer/internal/web backend/src/test/java/com/yoyuzh/transfer/internal/web/RemoteDownloadControllerTest.java backend/src/test/java/com/yoyuzh/transfer/internal/application/RuntimeRemoteDownloadApiTest.java backend/src/test/java/com/yoyuzh/transfer/internal/infra/RemoteDownloadTaskRepositoryIntegrationTest.java
git commit -m "feat: add remote download transfer api"
```

### Task 3: Add Background Task Execution and Downloader Adapters

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/application/RemoteDownloadBackgroundTaskPayload.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/application/RemoteDownloadImportService.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/infra/Aria2Client.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/infra/QbittorrentClient.java`
- Create: `backend/src/main/java/com/yoyuzh/transfer/internal/infra/DownloaderProperties.java`
- Create: `backend/src/main/java/com/yoyuzh/platform/job/internal/application/RemoteDownloadBackgroundTaskHandler.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/job/internal/application/BackgroundTaskWorker.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Test: `backend/src/test/java/com/yoyuzh/platform/job/internal/application/RemoteDownloadBackgroundTaskHandlerTest.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/application/RemoteDownloadImportServiceTest.java`

- [ ] **Step 1: Write the failing background task handler test**

```java
@Test
void shouldSubmitHttpTaskToAria2AndReportDownloadingPhase() {
    when(remoteDownloadService.loadForExecution(11L)).thenReturn(httpTask());
    when(aria2Client.submitHttp("https://example.com/demo.zip", "local-default"))
            .thenReturn("gid-123");

    BackgroundTaskHandlerResult result = handler.handle(backgroundTask(11L));

    assertThat(result.publicStatePatch()).containsEntry("phase", "downloading");
    assertThat(result.publicStatePatch()).containsEntry("engineType", "ARIA2");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=RemoteDownloadBackgroundTaskHandlerTest test`

Expected: FAIL because the handler and downloader clients do not exist yet.

- [ ] **Step 3: Write minimal execution flow**

```java
@Component
class RemoteDownloadBackgroundTaskHandler implements BackgroundTaskHandler {

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.REMOTE_DOWNLOAD;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        RemoteDownloadTask remoteDownload = remoteDownloadService.loadForExecution(task.getId());
        if (remoteDownload.getEngineType() == DownloadEngineType.ARIA2) {
            String downloaderTaskId = aria2Client.submitHttp(remoteDownload.getSourceValue(), remoteDownload.getDownloadNodeId());
            remoteDownloadService.markSubmitted(remoteDownload.getId(), downloaderTaskId);
            progressReporter.report(Map.of("phase", "downloading", "engineType", "ARIA2"));
        }
        return new BackgroundTaskHandlerResult(Map.of("worker", "remote-download"));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -Dtest=RemoteDownloadBackgroundTaskHandlerTest,RemoteDownloadImportServiceTest test`

Expected: PASS for routing, progress patch, and import invocation coverage.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/platform/job/internal/application/RemoteDownloadBackgroundTaskHandler.java backend/src/main/java/com/yoyuzh/transfer/internal/application backend/src/main/java/com/yoyuzh/transfer/internal/infra backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml backend/src/test/java/com/yoyuzh/platform/job/internal/application/RemoteDownloadBackgroundTaskHandlerTest.java backend/src/test/java/com/yoyuzh/transfer/internal/application/RemoteDownloadImportServiceTest.java
git commit -m "feat: add remote download background execution"
```

### Task 4: Implement Frontend Creation Flow and Task Detail UI

**Files:**
- Create: `frontend/src/components/files/CreateRemoteDownloadDialog.tsx`
- Create: `frontend/src/lib/remote-downloads.ts`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/queries.ts`
- Modify: `frontend/src/pages/Files.tsx`
- Modify: `frontend/src/pages/Tasks.tsx`
- Modify: `frontend/src/lib/tasks.ts`

- [ ] **Step 1: Write the failing UI integration test or type-level usage expectations**

```ts
it('serializes http remote download request as multipart form data', async () => {
  const form = buildRemoteDownloadFormData({
    sourceType: 'HTTP',
    sourceValue: 'https://example.com/demo.zip',
    targetPath: '/downloads',
  });

  expect(form.get('sourceType')).toBe('HTTP');
  expect(form.get('sourceValue')).toBe('https://example.com/demo.zip');
  expect(form.get('targetPath')).toBe('/downloads');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm run lint`

Expected: FAIL with missing types/functions for remote download dialog and client helpers.

- [ ] **Step 3: Write minimal frontend flow**

```ts
export async function createRemoteDownload(payload: CreateRemoteDownloadPayload) {
  const form = new FormData();
  form.set('sourceType', payload.sourceType);
  form.set('targetPath', payload.targetPath);
  if (payload.sourceValue) form.set('sourceValue', payload.sourceValue);
  if (payload.torrentFile) form.set('torrentFile', payload.torrentFile);
  return apiRequest<RemoteDownloadDetail>({
    url: '/transfer/remote-downloads',
    method: 'POST',
    body: form,
  });
}
```

```tsx
<MenuItem onClick={() => setRemoteDownloadDialogOpen(true)}>
  <ListItemIcon><CloudDownload fontSize="small" /></ListItemIcon>
  <ListItemText>离线下载</ListItemText>
</MenuItem>
```

- [ ] **Step 4: Run frontend verification**

Run: `cd frontend && npm run lint`

Expected: PASS with the new types, dialog props, and task page rendering.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/files/CreateRemoteDownloadDialog.tsx frontend/src/lib/remote-downloads.ts frontend/src/api/types.ts frontend/src/api/queries.ts frontend/src/pages/Files.tsx frontend/src/pages/Tasks.tsx frontend/src/lib/tasks.ts
git commit -m "feat: add offline download ui"
```

### Task 5: Verify End-to-End Behavior

**Files:**
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/application/RuntimeRemoteDownloadApiTest.java`
- Test: `backend/src/test/java/com/yoyuzh/platform/job/internal/application/RemoteDownloadBackgroundTaskHandlerTest.java`
- Test: `backend/src/test/java/com/yoyuzh/transfer/internal/infra/RemoteDownloadTaskRepositoryIntegrationTest.java`

- [ ] **Step 1: Run focused backend tests**

Run: `cd backend && mvn -Dtest=RemoteDownloadControllerTest,RuntimeRemoteDownloadApiTest,RemoteDownloadServiceTest,RemoteDownloadImportServiceTest,RemoteDownloadBackgroundTaskHandlerTest,RemoteDownloadTaskRepositoryIntegrationTest test`

Expected: PASS across remote download contract, state, worker, and persistence coverage.

- [ ] **Step 2: Run full backend suite**

Run: `cd backend && mvn test`

Expected: PASS with no regressions in transfer, files, or job modules.

- [ ] **Step 3: Run frontend verification**

Run: `cd frontend && npm run lint`

Expected: PASS with no TypeScript errors.

- [ ] **Step 4: Smoke the running backend if needed**

Run: `curl --max-time 5 -i http://localhost:8080/api/v2/site/ping`

Expected: `HTTP/1.1 200` with `{"status":"ok","apiVersion":"v2"}` in the response body.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java frontend/src
git commit -m "test: verify offline download flow"
```
