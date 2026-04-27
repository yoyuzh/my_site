# Media Category Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder `图片` / `视频` / `音乐` / `文档` dashboard entries with Cloudreve-style media category views that reuse the existing file manager layout and show recursive results across the user's whole drive.

**Architecture:** Extend the existing `/api/v2/files/search` flow with a `category` filter owned by `files.search`, then let the existing React file page run in either directory mode or category-search mode. Keep the current `Files` page as the single explorer surface and route the four media entries into that page with category-specific configuration.

**Tech Stack:** Spring Boot 3.3 / JPA / JUnit 5 / Mockito, React + TypeScript + React Query + MUI, existing repo commands `cd backend && mvn test` and `cd frontend && npm run lint`

---

### Task 1: Add backend category search contract

**Files:**
- Modify: `backend/src/test/java/com/yoyuzh/files/search/internal/web/FileSearchV2ControllerTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/search/internal/application/RuntimeFileSearchApiTest.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/search/internal/web/FileSearchV2Controller.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/search/api/SearchFilesQuery.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/search/internal/application/RuntimeFileSearchApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspaceFileSearchQuery.java`

- [ ] **Step 1: Write the failing controller and application tests for `category`**

Add assertions that `/api/v2/files/search?category=image&type=file` reaches `FileSearchApi` with the parsed category, and that `RuntimeFileSearchApi` passes the category through to `WorkspaceFileSearchQuery`.

- [ ] **Step 2: Run targeted backend tests to verify they fail**

Run:

```bash
cd /Users/mac/Documents/my_site/backend && mvn -Dtest=FileSearchV2ControllerTest,RuntimeFileSearchApiTest test
```

Expected: FAIL because `category` does not exist in the search contracts yet.

- [ ] **Step 3: Implement the minimal `category` contract**

Add a small enum-backed category field to `SearchFilesQuery` and `WorkspaceFileSearchQuery`, parse it in `FileSearchV2Controller`, validate unsupported values there, and delegate it unchanged through `RuntimeFileSearchApi`.

- [ ] **Step 4: Run the targeted backend tests again**

Run:

```bash
cd /Users/mac/Documents/my_site/backend && mvn -Dtest=FileSearchV2ControllerTest,RuntimeFileSearchApiTest test
```

Expected: PASS

### Task 2: Add workspace-level category filtering

**Files:**
- Modify: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceFileSearchApiTest.java` or create it if missing
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspaceFileSearchApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java`
- Optional create: `backend/src/main/java/com/yoyuzh/files/search/api/FileSearchCategory.java`

- [ ] **Step 1: Write failing backend tests for category matching**

Cover:
- image match by `contentType`
- audio/video/document fallback by filename extension when `contentType` is blank
- category searches exclude directories because the frontend will send `type=file`

- [ ] **Step 2: Run targeted tests to verify they fail**

Run:

```bash
cd /Users/mac/Documents/my_site/backend && mvn -Dtest=RuntimeWorkspaceFileSearchApiTest test
```

Expected: FAIL because workspace search does not filter by category yet.

- [ ] **Step 3: Implement minimal category filtering**

Keep ownership split intact:
- `files.search` owns category semantics
- `files.workspace` executes the filter against stored files

Implement a minimal query path that filters by known MIME prefixes first and by lowercase extension fallback second.

- [ ] **Step 4: Run the targeted workspace tests**

Run:

```bash
cd /Users/mac/Documents/my_site/backend && mvn -Dtest=RuntimeWorkspaceFileSearchApiTest test
```

Expected: PASS

### Task 3: Teach frontend queries about media category mode

**Files:**
- Modify: `frontend/src/api/queries.ts`
- Modify: `frontend/src/lib/files.ts`
- Modify: `frontend/src/api/types.ts`

- [ ] **Step 1: Add the frontend category query contract**

Introduce a shared frontend type for media categories and a helper that calls `/api/v2/files/search` with:

```text
category=<image|video|audio|document>
type=file
name=<optional search text>
page=<0-based>
size=<page size>
```

- [ ] **Step 2: Update `useFiles` to support directory mode and category-search mode**

Keep the existing directory behavior intact:
- blank search in normal file page still calls `/files/list`
- media category pages always call `/v2/files/search`

- [ ] **Step 3: Run frontend typecheck**

Run:

```bash
cd /Users/mac/Documents/my_site/frontend && npm run lint
```

Expected: PASS

### Task 4: Reuse the existing file page for media categories

**Files:**
- Modify: `frontend/src/pages/Files.tsx`
- Modify: `frontend/src/components/files/FilesTopBar.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Add a category mode to `Files.tsx`**

Let the page accept a prop such as:

```ts
mediaCategory?: 'image' | 'video' | 'audio' | 'document'
```

Behavior:
- normal file page keeps path-based browsing
- media category page fixes logical target path to `/`
- media category page uses category-search query source
- media category page keeps the same explorer, selection, menus, preview, and details rail

- [ ] **Step 2: Adjust the top bar for category-root breadcrumbs**

Add just enough configurability so the top bar can render:
- `根目录` + folder segments in directory mode
- a single root label like `图片` or `视频` in category mode

Do not invent fake nested directory breadcrumbs for category pages.

- [ ] **Step 3: Replace the placeholder routes**

Update `frontend/src/App.tsx` so:
- `/dashboard/images` renders `<Files mediaCategory="image" />`
- `/dashboard/videos` renders `<Files mediaCategory="video" />`
- `/dashboard/music` renders `<Files mediaCategory="audio" />`
- `/dashboard/documents` renders `<Files mediaCategory="document" />`

- [ ] **Step 4: Run frontend typecheck**

Run:

```bash
cd /Users/mac/Documents/my_site/frontend && npm run lint
```

Expected: PASS

### Task 5: Verify end-to-end regressions stay controlled

**Files:**
- No source changes required unless verification reveals breakage

- [ ] **Step 1: Run targeted backend tests for the changed search flow**

Run:

```bash
cd /Users/mac/Documents/my_site/backend && mvn -Dtest=FileSearchV2ControllerTest,RuntimeFileSearchApiTest,RuntimeWorkspaceFileSearchApiTest test
```

Expected: PASS

- [ ] **Step 2: Run the broader backend suite**

Run:

```bash
cd /Users/mac/Documents/my_site/backend && mvn test
```

Expected: PASS

- [ ] **Step 3: Run frontend typecheck one more time**

Run:

```bash
cd /Users/mac/Documents/my_site/frontend && npm run lint
```

Expected: PASS

- [ ] **Step 4: Manual smoke checklist**

Confirm locally:
- `/dashboard/files` still behaves as directory browsing
- `/dashboard/images` loads real files instead of placeholder content
- `/dashboard/videos`, `/dashboard/music`, `/dashboard/documents` do the same
- category page search narrows within the chosen category
- right-click menu, preview dialog, and detail rail still open
