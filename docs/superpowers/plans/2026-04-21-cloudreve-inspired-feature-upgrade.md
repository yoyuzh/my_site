# Cloudreve-Inspired Feature Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the next set of `my_site` netdisk product capabilities inspired by Cloudreve, using `my_site` native API contracts instead of Cloudreve `/api/v4` compatibility.

**Architecture:** Keep the backend as a modular monolith: site runtime config in `boot`, account and user settings in `identity.access`, file tree actions in `files.workspace`, physical content derivatives in `files.content`, share policy in `files.sharing`, async work in `platform.job`, and governance entrypoints in `ops.admin`. The frontend continues to use `front/src/lib/*` and existing pages; `third_party/cloudreve-frontend` is reference material only and must not become a runtime dependency.

**Tech Stack:** Spring Boot 3.3.8, Java 17, Maven, JUnit 5, MockMvc, React 19, Vite 6, TypeScript, Tailwind CSS v4.

---

## Scope Check

This is a master upgrade plan, not a Cloudreve compatibility plan. It deliberately excludes these Cloudreve-specific systems from implementation: `/api/v4` compatibility, Cloudreve node/slave-node, Cloudreve entity admin, Cloudreve OAuth provider/client management, WebDAV account management, WOPI integration, remote download, Passkey/WebAuthn, and Cloudreve storage callback protocols.

The plan is split into independently shippable product increments:

1. Site runtime config for frontend bootstrapping.
2. User capacity and settings aggregation.
3. File detail, batch operations, and favorite files.
4. Thumbnail read API as a content capability.
5. Share statistics and download limits.
6. Async task progress shape and search-index rebuild entrypoint.
7. Admin surface alignment for the new capabilities.

Each increment should be implemented in a fresh branch or worktree and committed before moving to the next increment.

## File Structure

### Backend files to create

- `backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigController.java`  
  Public API for frontend runtime configuration.

- `backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigResponse.java`  
  Stable DTO returned to the frontend.

- `backend/src/test/java/com/yoyuzh/boot/web/SiteRuntimeConfigControllerTest.java`  
  MockMvc tests for public site runtime config.

- `backend/src/main/java/com/yoyuzh/identity/access/api/UserCapacityResponse.java`  
  Public DTO for current-user storage usage.

- `backend/src/main/java/com/yoyuzh/identity/access/api/UserSettingsResponse.java`  
  Public DTO for current-user preferences.

- `backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java`  
  File detail DTO shaped for product UI.

- `backend/src/main/java/com/yoyuzh/files/workspace/api/BatchFileOperationRequest.java`  
  Shared request DTO for batch delete, restore, move, and copy.

- `backend/src/main/java/com/yoyuzh/files/workspace/api/FavoriteFileResponse.java`  
  DTO for pinned/favorite files.

- `backend/src/main/java/com/yoyuzh/files/content/api/ThumbnailResponse.java`  
  DTO for generated or existing thumbnail URLs.

- `backend/src/main/java/com/yoyuzh/files/content/internal/web/ThumbnailController.java`  
  Authenticated thumbnail read endpoint.

- `backend/src/test/java/com/yoyuzh/files/content/internal/web/ThumbnailControllerTest.java`  
  Controller tests for thumbnail behavior.

- `backend/src/main/java/com/yoyuzh/files/sharing/api/ShareStatsResponse.java`  
  DTO for share visit/download counters and limits.

- `backend/src/main/java/com/yoyuzh/files/sharing/internal/web/UpdateSharePolicyV2Request.java`  
  Request DTO for share expiry, password, and download-limit changes.

- `backend/src/main/java/com/yoyuzh/platform/job/api/TaskProgressResponse.java`  
  Stable task progress DTO for frontend task detail pages.

### Backend files to modify

- `backend/src/main/java/com/yoyuzh/boot/security/SecurityConfig.java`  
  Permit public site runtime config endpoint.

- `backend/src/main/java/com/yoyuzh/identity/access/internal/web/UserController.java`  
  Add current-user capacity and settings endpoints.

- `backend/src/main/java/com/yoyuzh/identity/access/internal/application/AuthService.java`  
  Read and update user settings in the identity module.

- `backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/StoredFile.java`  
  Add favorite/pinned state only if not already represented by metadata.

- `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java`  
  Add focused queries for file detail, favorites, and user storage usage reuse.

- `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`  
  Add product-level file detail, batch operations, and favorite operations.

- `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`  
  Expose file detail, batch operations, and favorite operations under `/api/files`.

- `backend/src/main/java/com/yoyuzh/files/sharing/internal/domain/FileShareLink.java`  
  Add download limit and counters if absent.

- `backend/src/main/java/com/yoyuzh/files/sharing/internal/application/RuntimeSharingApi.java`  
  Enforce share download limits and expose share stats.

- `backend/src/main/java/com/yoyuzh/files/sharing/internal/web/ShareV2Controller.java`  
  Expose share stats and policy update endpoints.

- `backend/src/main/java/com/yoyuzh/platform/job/internal/application/BackgroundTaskService.java`  
  Normalize progress response data for frontend.

- `backend/src/main/java/com/yoyuzh/platform/job/internal/web/BackgroundTaskV2Controller.java`  
  Expose task progress and search-index rebuild entrypoints.

### Frontend files to create

- `front/src/lib/site-config.ts`  
  Frontend API client and type for site runtime config.

- `front/src/lib/user-settings.ts`  
  Frontend API client and type for user capacity and settings.

- `front/src/lib/file-detail.ts`  
  Frontend API client and type for file detail and favorites.

- `front/src/lib/share-stats.ts`  
  Frontend API client and type for share stats and policy updates.

### Frontend files to modify

- `front/src/App.tsx`  
  Load site config during app bootstrap if no existing bootstrap owner is present.

- `front/src/account/pages/LoginPage.tsx`  
  Use site config for title, registration visibility, and login text.

- `front/src/workspace/pages/OverviewPage.tsx`  
  Display capacity and favorite files.

- `front/src/workspace/pages/FilesPage.tsx`  
  Add file detail, batch operation, favorite, and thumbnail integration points.

- `front/src/sharing/pages/SharesPage.tsx`  
  Display share stats and download limits.

- `front/src/operations-admin/pages/settings/index.tsx`  
  Surface site runtime settings when admin settings support is added.

---

## Task 1: Site Runtime Config

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigController.java`
- Create: `backend/src/test/java/com/yoyuzh/boot/web/SiteRuntimeConfigControllerTest.java`
- Modify: `backend/src/main/java/com/yoyuzh/boot/security/SecurityConfig.java`
- Create: `front/src/lib/site-config.ts`
- Modify: `front/src/account/pages/LoginPage.tsx`

- [ ] **Step 1: Write the backend controller test**

Create `backend/src/test/java/com/yoyuzh/boot/web/SiteRuntimeConfigControllerTest.java`:

```java
package com.yoyuzh.boot.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteRuntimeConfigControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SiteRuntimeConfigController()).build();
    }

    @Test
    void shouldExposePublicRuntimeConfig() throws Exception {
        mockMvc.perform(get("/api/v2/site/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.siteName").value("Yoyuzh 网盘"))
                .andExpect(jsonPath("$.data.registrationEnabled").value(true))
                .andExpect(jsonPath("$.data.passwordLoginEnabled").value(true))
                .andExpect(jsonPath("$.data.captchaEnabled").value(false))
                .andExpect(jsonPath("$.data.apiVersion").value("v2"));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend && mvn -Dtest=SiteRuntimeConfigControllerTest test
```

Expected: FAIL because `SiteRuntimeConfigController` does not exist.

- [ ] **Step 3: Add the runtime config DTO**

Create `backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigResponse.java`:

```java
package com.yoyuzh.boot.web;

public record SiteRuntimeConfigResponse(
        String siteName,
        String siteDescription,
        boolean registrationEnabled,
        boolean passwordLoginEnabled,
        boolean captchaEnabled,
        String apiVersion
) {

    public static SiteRuntimeConfigResponse defaults() {
        return new SiteRuntimeConfigResponse(
                "Yoyuzh 网盘",
                "个人网盘与快速传输平台",
                true,
                true,
                false,
                "v2"
        );
    }
}
```

- [ ] **Step 4: Add the public controller**

Create `backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigController.java`:

```java
package com.yoyuzh.boot.web;

import com.yoyuzh.shared.kernel.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/site")
public class SiteRuntimeConfigController {

    @GetMapping("/config")
    public ApiResponse<SiteRuntimeConfigResponse> config() {
        return ApiResponse.success(SiteRuntimeConfigResponse.defaults());
    }
}
```

- [ ] **Step 5: Permit the public config endpoint**

Modify `backend/src/main/java/com/yoyuzh/boot/security/SecurityConfig.java` in the existing public matcher section by adding:

```java
.requestMatchers(HttpMethod.GET, "/api/v2/site/config")
.permitAll()
```

Keep the existing `GET /api/v2/site/ping` permit rule in place.

- [ ] **Step 6: Run focused backend verification**

Run:

```bash
cd backend && mvn -Dtest=SiteRuntimeConfigControllerTest,SecurityConfigTest test
```

Expected: PASS.

- [ ] **Step 7: Add the frontend API client**

Create `front/src/lib/site-config.ts`:

```ts
import { fetchApi } from './api';

export type SiteRuntimeConfig = {
  siteName: string;
  siteDescription: string;
  registrationEnabled: boolean;
  passwordLoginEnabled: boolean;
  captchaEnabled: boolean;
  apiVersion: string;
};

export function getSiteRuntimeConfig() {
  return fetchApi<SiteRuntimeConfig>('/v2/site/config', {
    auth: false,
  });
}
```

- [ ] **Step 8: Wire login page text to runtime config**

Modify `front/src/account/pages/LoginPage.tsx` so it calls `getSiteRuntimeConfig()` on mount and uses:

```ts
const fallbackSiteName = 'Yoyuzh 网盘';
```

Use the loaded `siteName` for the visible page title and hide the registration link when `registrationEnabled` is false.

- [ ] **Step 9: Run frontend type verification**

Run:

```bash
cd front && npm run lint
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigResponse.java \
  backend/src/main/java/com/yoyuzh/boot/web/SiteRuntimeConfigController.java \
  backend/src/test/java/com/yoyuzh/boot/web/SiteRuntimeConfigControllerTest.java \
  backend/src/main/java/com/yoyuzh/boot/security/SecurityConfig.java \
  front/src/lib/site-config.ts \
  front/src/account/pages/LoginPage.tsx
git commit -m "feat: add site runtime config"
```

---

## Task 2: User Capacity And Settings Aggregation

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/identity/access/api/UserCapacityResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/identity/access/api/UserSettingsResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/internal/application/AuthService.java`
- Modify: `backend/src/main/java/com/yoyuzh/identity/access/internal/web/UserController.java`
- Test: `backend/src/test/java/com/yoyuzh/identity/access/internal/web/UserControllerSettingsTest.java`
- Create: `front/src/lib/user-settings.ts`
- Modify: `front/src/workspace/pages/OverviewPage.tsx`

- [ ] **Step 1: Write the controller test**

Create `backend/src/test/java/com/yoyuzh/identity/access/internal/web/UserControllerSettingsTest.java`:

```java
package com.yoyuzh.identity.access.internal.web;

import com.yoyuzh.identity.access.api.UserCapacityResponse;
import com.yoyuzh.identity.access.api.UserSettingsResponse;
import com.yoyuzh.identity.access.internal.application.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerSettingsTest {

    private AuthService authService;
    private MockMvc mockMvc;
    private UserDetails principal;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(authService)).build();
        principal = User.withUsername("demo").password("ignored").authorities(List.of()).build();
    }

    @Test
    void shouldExposeCapacity() throws Exception {
        when(authService.getCapacity("demo"))
                .thenReturn(new UserCapacityResponse(1024L, 256L, 768L, 128L));

        mockMvc.perform(get("/api/user/capacity").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBytes").value(1024))
                .andExpect(jsonPath("$.data.usedBytes").value(256))
                .andExpect(jsonPath("$.data.availableBytes").value(768))
                .andExpect(jsonPath("$.data.maxUploadSizeBytes").value(128));
    }

    @Test
    void shouldExposeSettings() throws Exception {
        when(authService.getSettings("demo"))
                .thenReturn(new UserSettingsResponse("demo", "zh-CN", "system", false));

        mockMvc.perform(get("/api/user/settings").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("demo"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.data.preferredTheme").value("system"))
                .andExpect(jsonPath("$.data.disableViewSync").value(false));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend && mvn -Dtest=UserControllerSettingsTest test
```

Expected: FAIL because `UserCapacityResponse`, `UserSettingsResponse`, `AuthService#getCapacity`, `AuthService#getSettings`, and the controller endpoints do not exist.

- [ ] **Step 3: Add the response DTOs**

Create `backend/src/main/java/com/yoyuzh/identity/access/api/UserCapacityResponse.java`:

```java
package com.yoyuzh.identity.access.api;

public record UserCapacityResponse(
        long totalBytes,
        long usedBytes,
        long availableBytes,
        long maxUploadSizeBytes
) {
}
```

Create `backend/src/main/java/com/yoyuzh/identity/access/api/UserSettingsResponse.java`:

```java
package com.yoyuzh.identity.access.api;

public record UserSettingsResponse(
        String displayName,
        String preferredLanguage,
        String preferredTheme,
        boolean disableViewSync
) {
}
```

- [ ] **Step 4: Add service methods in identity**

Modify `backend/src/main/java/com/yoyuzh/identity/access/internal/application/AuthService.java` to add:

```java
public UserCapacityResponse getCapacity(String username) {
    var user = findUserByUsername(username);
    long totalBytes = user.getStorageQuotaBytes();
    long usedBytes = 0L;
    long availableBytes = Math.max(0L, totalBytes - usedBytes);
    return new UserCapacityResponse(totalBytes, usedBytes, availableBytes, user.getMaxUploadSizeBytes());
}

public UserSettingsResponse getSettings(String username) {
    var user = findUserByUsername(username);
    return new UserSettingsResponse(
            user.getDisplayName(),
            user.getPreferredLanguage(),
            "system",
            false
    );
}
```

This first pass intentionally reports `usedBytes = 0L` from identity because final storage usage truth belongs to `files.workspace`. Task 3 adds workspace-backed usage through a module API instead of making identity read file repositories.

- [ ] **Step 5: Expose user endpoints**

Modify `backend/src/main/java/com/yoyuzh/identity/access/internal/web/UserController.java`:

```java
@Operation(summary = "获取当前用户容量")
@GetMapping("/capacity")
public ApiResponse<?> capacity(@AuthenticationPrincipal UserDetails userDetails) {
    return ApiResponse.success(authService.getCapacity(userDetails.getUsername()));
}

@Operation(summary = "获取当前用户设置")
@GetMapping("/settings")
public ApiResponse<?> settings(@AuthenticationPrincipal UserDetails userDetails) {
    return ApiResponse.success(authService.getSettings(userDetails.getUsername()));
}
```

- [ ] **Step 6: Run focused backend verification**

Run:

```bash
cd backend && mvn -Dtest=UserControllerSettingsTest,AuthServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Add the frontend user settings client**

Create `front/src/lib/user-settings.ts`:

```ts
import { fetchApi } from './api';

export type UserCapacity = {
  totalBytes: number;
  usedBytes: number;
  availableBytes: number;
  maxUploadSizeBytes: number;
};

export type UserSettings = {
  displayName: string;
  preferredLanguage: string;
  preferredTheme: 'system' | 'light' | 'dark';
  disableViewSync: boolean;
};

export function getUserCapacity() {
  return fetchApi<UserCapacity>('/user/capacity');
}

export function getUserSettings() {
  return fetchApi<UserSettings>('/user/settings');
}
```

- [ ] **Step 8: Display capacity on overview**

Modify `front/src/workspace/pages/OverviewPage.tsx` to call `getUserCapacity()` and render used/total bytes using the existing formatter from `front/src/lib/format.ts`.

Use this state shape:

```ts
const [capacity, setCapacity] = useState<UserCapacity | null>(null);
```

- [ ] **Step 9: Run verification**

Run:

```bash
cd backend && mvn -Dtest=UserControllerSettingsTest test
cd front && npm run lint
```

Expected: both PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/identity/access/api/UserCapacityResponse.java \
  backend/src/main/java/com/yoyuzh/identity/access/api/UserSettingsResponse.java \
  backend/src/main/java/com/yoyuzh/identity/access/internal/application/AuthService.java \
  backend/src/main/java/com/yoyuzh/identity/access/internal/web/UserController.java \
  backend/src/test/java/com/yoyuzh/identity/access/internal/web/UserControllerSettingsTest.java \
  front/src/lib/user-settings.ts \
  front/src/workspace/pages/OverviewPage.tsx
git commit -m "feat: expose user capacity and settings"
```

---

## Task 3: File Detail, Batch Operations, And Favorite Files

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/BatchFileOperationRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/FavoriteFileResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/StoredFile.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`
- Test: `backend/src/test/java/com/yoyuzh/files/workspace/internal/web/FileProductCapabilityControllerTest.java`
- Create: `front/src/lib/file-detail.ts`
- Modify: `front/src/workspace/pages/FilesPage.tsx`

- [ ] **Step 1: Write controller tests for the new product endpoints**

Create `backend/src/test/java/com/yoyuzh/files/workspace/internal/web/FileProductCapabilityControllerTest.java` with MockMvc coverage for:

```java
mockMvc.perform(get("/api/files/{fileId}/detail", 1L).principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.favorite").value(false));

mockMvc.perform(post("/api/files/batch/delete")
        .principal(principal)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"fileIds\":[1,2]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

mockMvc.perform(put("/api/files/{fileId}/favorite", 1L).principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fileId").value(1))
        .andExpect(jsonPath("$.data.favorite").value(true));
```

Use `@MockBean` or standalone Mockito setup matching the existing `FileShareControllerIntegrationTest` style in the same package.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend && mvn -Dtest=FileProductCapabilityControllerTest test
```

Expected: FAIL because the new request/response DTOs and endpoints do not exist.

- [ ] **Step 3: Add API DTOs**

Create `backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java`:

```java
package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;

public record FileDetailResponse(
        Long id,
        String filename,
        String path,
        long size,
        String contentType,
        boolean directory,
        boolean favorite,
        boolean shared,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

Create `backend/src/main/java/com/yoyuzh/files/workspace/api/BatchFileOperationRequest.java`:

```java
package com.yoyuzh.files.workspace.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchFileOperationRequest(
        @NotEmpty List<Long> fileIds,
        String targetPath
) {
}
```

Create `backend/src/main/java/com/yoyuzh/files/workspace/api/FavoriteFileResponse.java`:

```java
package com.yoyuzh.files.workspace.api;

public record FavoriteFileResponse(
        Long fileId,
        boolean favorite
) {
}
```

- [ ] **Step 4: Add favorite state to StoredFile**

Modify `backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/StoredFile.java` by adding:

```java
@Column(nullable = false)
private boolean favorite;

public boolean isFavorite() {
    return favorite;
}

public void setFavorite(boolean favorite) {
    this.favorite = favorite;
}
```

If the entity already has a metadata-backed favorite flag when implementing this task, use that existing field and do not add a duplicate column.

- [ ] **Step 5: Add repository queries**

Modify `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java`:

```java
List<StoredFile> findTop20ByUserIdAndFavoriteTrueAndDeletedFalseOrderByUpdatedAtDesc(Long userId);

Optional<StoredFile> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
```

- [ ] **Step 6: Add service methods**

Modify `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java`:

```java
public FileDetailResponse detail(User user, Long fileId) {
    StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedFalse(fileId, user.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
    return toDetailResponse(file, false);
}

public void batchDelete(User user, List<Long> fileIds) {
    for (Long fileId : fileIds) {
        delete(user, fileId);
    }
}

public FavoriteFileResponse setFavorite(User user, Long fileId, boolean favorite) {
    StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedFalse(fileId, user.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
    file.setFavorite(favorite);
    storedFileRepository.save(file);
    return new FavoriteFileResponse(file.getId(), file.isFavorite());
}

public List<FavoriteFileResponse> listFavorites(User user) {
    return storedFileRepository.findTop20ByUserIdAndFavoriteTrueAndDeletedFalseOrderByUpdatedAtDesc(user.getId())
            .stream()
            .map(file -> new FavoriteFileResponse(file.getId(), true))
            .toList();
}
```

Add a private mapper:

```java
private FileDetailResponse toDetailResponse(StoredFile file, boolean shared) {
    return new FileDetailResponse(
            file.getId(),
            file.getFilename(),
            file.getPath(),
            file.getSize(),
            file.getContentType(),
            file.isDirectory(),
            file.isFavorite(),
            shared,
            file.getCreatedAt(),
            file.getUpdatedAt()
    );
}
```

- [ ] **Step 7: Expose endpoints in FileController**

Modify `backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java`:

```java
@GetMapping("/{fileId}/detail")
public ApiResponse<FileDetailResponse> detail(@AuthenticationPrincipal UserDetails userDetails,
                                               @PathVariable Long fileId) {
    return ApiResponse.success(fileService.detail(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId));
}

@PostMapping("/batch/delete")
public ApiResponse<Void> batchDelete(@AuthenticationPrincipal UserDetails userDetails,
                                      @Valid @RequestBody BatchFileOperationRequest request) {
    fileService.batchDelete(userDetailsService.loadDomainUser(userDetails.getUsername()), request.fileIds());
    return ApiResponse.success();
}

@GetMapping("/favorites")
public ApiResponse<List<FavoriteFileResponse>> favorites(@AuthenticationPrincipal UserDetails userDetails) {
    return ApiResponse.success(fileService.listFavorites(userDetailsService.loadDomainUser(userDetails.getUsername())));
}

@PutMapping("/{fileId}/favorite")
public ApiResponse<FavoriteFileResponse> favorite(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long fileId) {
    return ApiResponse.success(fileService.setFavorite(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId, true));
}

@DeleteMapping("/{fileId}/favorite")
public ApiResponse<FavoriteFileResponse> unfavorite(@AuthenticationPrincipal UserDetails userDetails,
                                                    @PathVariable Long fileId) {
    return ApiResponse.success(fileService.setFavorite(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId, false));
}
```

- [ ] **Step 8: Add frontend client**

Create `front/src/lib/file-detail.ts`:

```ts
import { fetchApi } from './api';
import type { FileItem } from './files';

export type FileDetail = FileItem & {
  favorite: boolean;
  shared: boolean;
  updatedAt: string;
};

export type FavoriteFile = {
  fileId: number;
  favorite: boolean;
};

export function getFileDetail(fileId: number) {
  return fetchApi<FileDetail>(`/files/${fileId}/detail`);
}

export function batchDeleteFiles(fileIds: number[]) {
  return fetchApi<void>('/files/batch/delete', {
    method: 'POST',
    body: JSON.stringify({ fileIds }),
  });
}

export function listFavoriteFiles() {
  return fetchApi<FavoriteFile[]>('/files/favorites');
}

export function setFileFavorite(fileId: number, favorite: boolean) {
  return fetchApi<FavoriteFile>(`/files/${fileId}/favorite`, {
    method: favorite ? 'PUT' : 'DELETE',
  });
}
```

- [ ] **Step 9: Wire FilesPage interactions**

Modify `front/src/workspace/pages/FilesPage.tsx` to:

- call `getFileDetail(file.id)` when opening the file details sidebar;
- call `batchDeleteFiles(selectedIds)` from the existing multi-selection delete action;
- call `setFileFavorite(file.id, true)` and `setFileFavorite(file.id, false)` from a star/favorite action.

Use a fixed icon button for favorite actions so long filenames do not resize the row.

- [ ] **Step 10: Run verification**

Run:

```bash
cd backend && mvn -Dtest=FileProductCapabilityControllerTest,FileServiceTest test
cd front && npm run lint
```

Expected: both PASS.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/workspace/api/FileDetailResponse.java \
  backend/src/main/java/com/yoyuzh/files/workspace/api/BatchFileOperationRequest.java \
  backend/src/main/java/com/yoyuzh/files/workspace/api/FavoriteFileResponse.java \
  backend/src/main/java/com/yoyuzh/files/workspace/internal/domain/StoredFile.java \
  backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java \
  backend/src/main/java/com/yoyuzh/files/workspace/internal/application/FileService.java \
  backend/src/main/java/com/yoyuzh/files/workspace/internal/web/FileController.java \
  backend/src/test/java/com/yoyuzh/files/workspace/internal/web/FileProductCapabilityControllerTest.java \
  front/src/lib/file-detail.ts \
  front/src/workspace/pages/FilesPage.tsx
git commit -m "feat: add file detail batch actions and favorites"
```

---

## Task 4: Thumbnail Read API

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/content/api/ThumbnailResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/content/internal/web/ThumbnailController.java`
- Test: `backend/src/test/java/com/yoyuzh/files/content/internal/web/ThumbnailControllerTest.java`
- Modify: `front/src/components/media/FileThumbnail.tsx`
- Modify: `front/src/lib/files.ts`

- [ ] **Step 1: Write controller test for thumbnail URL fallback**

Create `backend/src/test/java/com/yoyuzh/files/content/internal/web/ThumbnailControllerTest.java`:

```java
package com.yoyuzh.files.content.internal.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ThumbnailControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThumbnailController()).build();
    }

    @Test
    void shouldReturnPlaceholderThumbnailWhenNoDerivativeExists() throws Exception {
        mockMvc.perform(get("/api/v2/files/{fileId}/thumbnail", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value(42))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.url").value(""));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend && mvn -Dtest=ThumbnailControllerTest test
```

Expected: FAIL because `ThumbnailController` and `ThumbnailResponse` do not exist.

- [ ] **Step 3: Add the thumbnail DTO**

Create `backend/src/main/java/com/yoyuzh/files/content/api/ThumbnailResponse.java`:

```java
package com.yoyuzh.files.content.api;

public record ThumbnailResponse(
        Long fileId,
        boolean available,
        String url
) {

    public static ThumbnailResponse unavailable(Long fileId) {
        return new ThumbnailResponse(fileId, false, "");
    }
}
```

- [ ] **Step 4: Add a read endpoint**

Create `backend/src/main/java/com/yoyuzh/files/content/internal/web/ThumbnailController.java`:

```java
package com.yoyuzh.files.content.internal.web;

import com.yoyuzh.files.content.api.ThumbnailResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/files")
public class ThumbnailController {

    @GetMapping("/{fileId}/thumbnail")
    public ApiResponse<ThumbnailResponse> thumbnail(@PathVariable Long fileId) {
        return ApiResponse.success(ThumbnailResponse.unavailable(fileId));
    }
}
```

This endpoint starts as a stable contract. Actual derivative generation should be added under `files.content` after product UI can consume the contract.

- [ ] **Step 5: Run backend verification**

Run:

```bash
cd backend && mvn -Dtest=ThumbnailControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Add frontend thumbnail client**

Modify `front/src/lib/files.ts`:

```ts
export type ThumbnailResponse = {
  fileId: number;
  available: boolean;
  url: string;
};

export function getThumbnail(fileId: number) {
  return fetchApi<ThumbnailResponse>(`/v2/files/${fileId}/thumbnail`);
}
```

- [ ] **Step 7: Update thumbnail component**

Modify `front/src/components/media/FileThumbnail.tsx` so it:

- calls `getThumbnail(file.id)` for images and videos;
- uses `response.url` only when `response.available` is true;
- falls back to the existing file-type icon when unavailable.

- [ ] **Step 8: Run verification**

Run:

```bash
cd backend && mvn -Dtest=ThumbnailControllerTest test
cd front && npm run lint
```

Expected: both PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/content/api/ThumbnailResponse.java \
  backend/src/main/java/com/yoyuzh/files/content/internal/web/ThumbnailController.java \
  backend/src/test/java/com/yoyuzh/files/content/internal/web/ThumbnailControllerTest.java \
  front/src/components/media/FileThumbnail.tsx \
  front/src/lib/files.ts
git commit -m "feat: add thumbnail read contract"
```

---

## Task 5: Share Stats And Download Limits

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/sharing/api/ShareStatsResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/sharing/internal/web/UpdateSharePolicyV2Request.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/sharing/internal/domain/FileShareLink.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/sharing/internal/application/RuntimeSharingApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/sharing/internal/web/ShareV2Controller.java`
- Test: `backend/src/test/java/com/yoyuzh/files/sharing/internal/web/ShareV2ControllerIntegrationTest.java`
- Create: `front/src/lib/share-stats.ts`
- Modify: `front/src/sharing/pages/SharesPage.tsx`

- [ ] **Step 1: Add failing integration tests**

Extend `backend/src/test/java/com/yoyuzh/files/sharing/internal/web/ShareV2ControllerIntegrationTest.java` with tests for:

```java
mockMvc.perform(get("/api/v2/shares/{token}/stats", token).with(user("demo")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.visits").isNumber())
        .andExpect(jsonPath("$.data.downloads").isNumber());

mockMvc.perform(patch("/api/v2/shares/{id}/policy", shareId)
        .with(user("demo"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"maxDownloads\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend && mvn -Dtest=ShareV2ControllerIntegrationTest test
```

Expected: FAIL because the stats and policy endpoints do not exist.

- [ ] **Step 3: Add share DTOs**

Create `backend/src/main/java/com/yoyuzh/files/sharing/api/ShareStatsResponse.java`:

```java
package com.yoyuzh.files.sharing.api;

public record ShareStatsResponse(
        String token,
        long visits,
        long downloads,
        Long maxDownloads,
        boolean downloadLimitReached
) {
}
```

Create `backend/src/main/java/com/yoyuzh/files/sharing/internal/web/UpdateSharePolicyV2Request.java`:

```java
package com.yoyuzh.files.sharing.internal.web;

import jakarta.validation.constraints.Positive;

public record UpdateSharePolicyV2Request(
        @Positive Long maxDownloads
) {
}
```

- [ ] **Step 4: Extend the share aggregate**

Modify `backend/src/main/java/com/yoyuzh/files/sharing/internal/domain/FileShareLink.java` with fields:

```java
@Column(nullable = false)
private long visitCount;

@Column(nullable = false)
private long downloadCount;

@Column(name = "max_downloads")
private Long maxDownloads;
```

Add methods:

```java
public void recordVisit() {
    this.visitCount++;
}

public void recordDownload() {
    if (isDownloadLimitReached()) {
        throw new IllegalStateException("分享下载次数已达上限");
    }
    this.downloadCount++;
}

public boolean isDownloadLimitReached() {
    return maxDownloads != null && downloadCount >= maxDownloads;
}
```

- [ ] **Step 5: Add sharing service operations**

Modify `backend/src/main/java/com/yoyuzh/files/sharing/internal/application/RuntimeSharingApi.java`:

```java
public ShareStatsResponse getStats(String token) {
    FileShareLink share = findByToken(token);
    return new ShareStatsResponse(
            share.getToken(),
            share.getVisitCount(),
            share.getDownloadCount(),
            share.getMaxDownloads(),
            share.isDownloadLimitReached()
    );
}

public ShareV2Response updatePolicy(Long ownerUserId, Long shareId, Long maxDownloads) {
    FileShareLink share = findOwnedShare(ownerUserId, shareId);
    share.setMaxDownloads(maxDownloads);
    fileShareLinkRepository.save(share);
    return toResponse(share, false);
}
```

- [ ] **Step 6: Expose share endpoints**

Modify `backend/src/main/java/com/yoyuzh/files/sharing/internal/web/ShareV2Controller.java`:

```java
@GetMapping("/{token}/stats")
public ApiResponse<ShareStatsResponse> stats(@PathVariable String token) {
    return ApiResponse.success(sharingApi.getStats(token));
}

@PatchMapping("/{id}/policy")
public ApiResponse<ShareV2Response> updatePolicy(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody UpdateSharePolicyV2Request request) {
    return ApiResponse.success(sharingApi.updatePolicy(currentUserId(userDetails), id, request.maxDownloads()));
}
```

- [ ] **Step 7: Add frontend share client**

Create `front/src/lib/share-stats.ts`:

```ts
import { fetchApi } from './api';

export type ShareStats = {
  token: string;
  visits: number;
  downloads: number;
  maxDownloads: number | null;
  downloadLimitReached: boolean;
};

export function getShareStats(token: string) {
  return fetchApi<ShareStats>(`/v2/shares/${encodeURIComponent(token)}/stats`);
}

export function updateSharePolicy(id: number, maxDownloads: number | null) {
  return fetchApi(`/v2/shares/${id}/policy`, {
    method: 'PATCH',
    body: JSON.stringify({ maxDownloads }),
  });
}
```

- [ ] **Step 8: Display stats in share management**

Modify `front/src/sharing/pages/SharesPage.tsx` to show visits, downloads, and remaining downloads for each share where stats are loaded.

- [ ] **Step 9: Run verification**

Run:

```bash
cd backend && mvn -Dtest=ShareV2ControllerIntegrationTest test
cd front && npm run lint
```

Expected: both PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/sharing/api/ShareStatsResponse.java \
  backend/src/main/java/com/yoyuzh/files/sharing/internal/web/UpdateSharePolicyV2Request.java \
  backend/src/main/java/com/yoyuzh/files/sharing/internal/domain/FileShareLink.java \
  backend/src/main/java/com/yoyuzh/files/sharing/internal/application/RuntimeSharingApi.java \
  backend/src/main/java/com/yoyuzh/files/sharing/internal/web/ShareV2Controller.java \
  backend/src/test/java/com/yoyuzh/files/sharing/internal/web/ShareV2ControllerIntegrationTest.java \
  front/src/lib/share-stats.ts \
  front/src/sharing/pages/SharesPage.tsx
git commit -m "feat: add share stats and download limits"
```

---

## Task 6: Task Progress And Search Index Rebuild Entry

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/platform/job/api/TaskProgressResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/job/internal/application/BackgroundTaskService.java`
- Modify: `backend/src/main/java/com/yoyuzh/platform/job/internal/web/BackgroundTaskV2Controller.java`
- Test: `backend/src/test/java/com/yoyuzh/platform/job/internal/web/BackgroundTaskV2ControllerIntegrationTest.java`
- Modify: `front/src/lib/background-tasks.ts`
- Modify: `front/src/common/pages/TasksPage.tsx`
- Modify: `front/src/operations-admin/pages/monitoring/tasks.tsx`

- [ ] **Step 1: Add failing task progress tests**

Extend `backend/src/test/java/com/yoyuzh/platform/job/internal/web/BackgroundTaskV2ControllerIntegrationTest.java` with:

```java
mockMvc.perform(get("/api/v2/tasks/{id}/progress", taskId).with(user("demo")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.taskId").value(taskId))
        .andExpect(jsonPath("$.data.progressPercent").isNumber());

mockMvc.perform(post("/api/v2/tasks/search-index/rebuild").with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend && mvn -Dtest=BackgroundTaskV2ControllerIntegrationTest test
```

Expected: FAIL because progress and search-index rebuild endpoints do not exist.

- [ ] **Step 3: Add progress DTO**

Create `backend/src/main/java/com/yoyuzh/platform/job/api/TaskProgressResponse.java`:

```java
package com.yoyuzh.platform.job.api;

public record TaskProgressResponse(
        Long taskId,
        String status,
        int progressPercent,
        long processedItems,
        long totalItems,
        String message
) {
}
```

- [ ] **Step 4: Add service methods**

Modify `backend/src/main/java/com/yoyuzh/platform/job/internal/application/BackgroundTaskService.java`:

```java
public TaskProgressResponse getProgress(Long taskId) {
    BackgroundTask task = getTaskOrThrow(taskId);
    long total = Math.max(0L, task.getTotalFileCount());
    long processed = Math.max(0L, task.getProcessedFileCount());
    int percent = total == 0L ? 0 : (int) Math.min(100L, processed * 100L / total);
    return new TaskProgressResponse(task.getId(), task.getStatus().name(), percent, processed, total, task.getMessage());
}

public BackgroundTask createSearchIndexRebuildTask(Long requestedByUserId) {
    return createSystemTask("SEARCH_INDEX_REBUILD", requestedByUserId);
}
```

Use the existing task creation method names in `BackgroundTaskService`; if the class already exposes a more specific create method, call that method and keep the returned `BackgroundTask` contract.

- [ ] **Step 5: Expose controller endpoints**

Modify `backend/src/main/java/com/yoyuzh/platform/job/internal/web/BackgroundTaskV2Controller.java`:

```java
@GetMapping("/{id}/progress")
public ApiResponse<TaskProgressResponse> progress(@PathVariable Long id) {
    return ApiResponse.success(backgroundTaskService.getProgress(id));
}

@PostMapping("/search-index/rebuild")
public ApiResponse<?> rebuildSearchIndex(@AuthenticationPrincipal UserDetails userDetails) {
    Long userId = currentUserId(userDetails);
    return ApiResponse.success(backgroundTaskService.createSearchIndexRebuildTask(userId));
}
```

- [ ] **Step 6: Add frontend task progress client**

Modify `front/src/lib/background-tasks.ts`:

```ts
export type TaskProgress = {
  taskId: number;
  status: string;
  progressPercent: number;
  processedItems: number;
  totalItems: number;
  message: string;
};

export function getTaskProgress(taskId: number) {
  return fetchApi<TaskProgress>(`/v2/tasks/${taskId}/progress`);
}

export function rebuildSearchIndex() {
  return fetchApi('/v2/tasks/search-index/rebuild', {
    method: 'POST',
  });
}
```

- [ ] **Step 7: Wire task pages**

Modify `front/src/common/pages/TasksPage.tsx` and `front/src/operations-admin/pages/monitoring/tasks.tsx` to display `progressPercent`, `processedItems`, and `totalItems` when a task is selected.

In the admin tasks page, add a command button that calls `rebuildSearchIndex()` and then refreshes the task list.

- [ ] **Step 8: Run verification**

Run:

```bash
cd backend && mvn -Dtest=BackgroundTaskV2ControllerIntegrationTest,BackgroundTaskServiceTest test
cd front && npm run lint
```

Expected: both PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/platform/job/api/TaskProgressResponse.java \
  backend/src/main/java/com/yoyuzh/platform/job/internal/application/BackgroundTaskService.java \
  backend/src/main/java/com/yoyuzh/platform/job/internal/web/BackgroundTaskV2Controller.java \
  backend/src/test/java/com/yoyuzh/platform/job/internal/web/BackgroundTaskV2ControllerIntegrationTest.java \
  front/src/lib/background-tasks.ts \
  front/src/common/pages/TasksPage.tsx \
  front/src/operations-admin/pages/monitoring/tasks.tsx
git commit -m "feat: expose task progress and search rebuild"
```

---

## Task 7: Admin Alignment For New Capabilities

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminMetricsService.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminResourceGovernanceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminOverviewController.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminResourceController.java`
- Test: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminMetricsServiceTest.java`
- Test: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminResourceGovernanceServiceTest.java`
- Modify: `front/src/operations-admin/pages/overview/index.tsx`
- Modify: `front/src/operations-admin/pages/governance/files.tsx`
- Modify: `front/src/operations-admin/pages/governance/shares.tsx`

- [ ] **Step 1: Add admin metrics tests**

Extend `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminMetricsServiceTest.java` with assertions that summary includes:

```java
assertThat(summary.favoriteFileCount()).isGreaterThanOrEqualTo(0);
assertThat(summary.shareDownloadCount()).isGreaterThanOrEqualTo(0);
assertThat(summary.activeTaskCount()).isGreaterThanOrEqualTo(0);
```

- [ ] **Step 2: Add admin governance tests**

Extend `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminResourceGovernanceServiceTest.java` with a test that admin file rows expose favorite and thumbnail state:

```java
assertThat(fileView.favorite()).isFalse();
assertThat(fileView.thumbnailAvailable()).isFalse();
```

- [ ] **Step 3: Run focused tests and verify they fail**

Run:

```bash
cd backend && mvn -Dtest=AdminMetricsServiceTest,AdminResourceGovernanceServiceTest test
```

Expected: FAIL because the admin DTOs do not include the new fields yet.

- [ ] **Step 4: Add admin summary fields**

Modify the admin summary DTO used by `AdminMetricsService` to include:

```java
long favoriteFileCount,
long shareDownloadCount,
long activeTaskCount
```

Populate these values in `AdminMetricsService` using existing module APIs or focused repository query services. `ops.admin` must call module `api` contracts and must not read another module's `internal` repositories directly.

- [ ] **Step 5: Add admin file and share row fields**

Modify the admin file view DTO used by `AdminResourceGovernanceService` to include:

```java
boolean favorite,
boolean thumbnailAvailable
```

Modify the admin share view DTO to include:

```java
long visits,
long downloads,
Long maxDownloads
```

- [ ] **Step 6: Expose the new fields through existing admin controllers**

Keep the existing routes:

- `GET /api/admin/summary`
- `GET /api/admin/files`
- `GET /api/admin/shares`

Do not add Cloudreve-style routes such as `/api/v4/admin/file` or `/api/v4/admin/share`.

- [ ] **Step 7: Update admin frontend pages**

Modify:

- `front/src/operations-admin/pages/overview/index.tsx` to render favorite file count, share downloads, and active tasks.
- `front/src/operations-admin/pages/governance/files.tsx` to show favorite and thumbnail state.
- `front/src/operations-admin/pages/governance/shares.tsx` to show visits, downloads, and max downloads.

- [ ] **Step 8: Run verification**

Run:

```bash
cd backend && mvn -Dtest=AdminMetricsServiceTest,AdminResourceGovernanceServiceTest test
cd front && npm run lint
```

Expected: both PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminMetricsService.java \
  backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminResourceGovernanceService.java \
  backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminOverviewController.java \
  backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminResourceController.java \
  backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminMetricsServiceTest.java \
  backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminResourceGovernanceServiceTest.java \
  front/src/operations-admin/pages/overview/index.tsx \
  front/src/operations-admin/pages/governance/files.tsx \
  front/src/operations-admin/pages/governance/shares.tsx
git commit -m "feat: align admin views with product capabilities"
```

---

## Final Verification

- [ ] Run backend targeted tests from all tasks:

```bash
cd backend && mvn -Dtest=SiteRuntimeConfigControllerTest,UserControllerSettingsTest,FileProductCapabilityControllerTest,ThumbnailControllerTest,ShareV2ControllerIntegrationTest,BackgroundTaskV2ControllerIntegrationTest,AdminMetricsServiceTest,AdminResourceGovernanceServiceTest test
```

Expected: PASS.

- [ ] Run broader backend verification:

```bash
cd backend && mvn test
```

Expected: PASS.

- [ ] Run frontend verification:

```bash
cd front && npm run lint
cd front && npm run test
cd front && npm run build
```

Expected: all PASS.

- [ ] Run a manual smoke check:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd front && npm run dev
```

Expected:

- Login page shows site runtime config values.
- Overview page shows storage capacity.
- File list supports opening details, batch delete, and favorite toggling.
- File thumbnails fall back to file-type icons when unavailable.
- Share list shows visits and downloads after data exists.
- Task pages show progress fields.
- Admin overview and governance pages show the new fields.

## Self-Review

- Spec coverage: This plan covers Cloudreve-inspired product capabilities that fit `my_site`: site config, user capacity/settings, file detail, batch actions, favorites, thumbnails, share stats/limits, task progress, search-index rebuild entrypoint, and admin visibility.
- Exclusions: This plan excludes Cloudreve protocol compatibility, WebDAV, Passkey, OAuth provider/client, WOPI, remote download, Cloudreve node, and Cloudreve entity because those are separate product decisions.
- Placeholder scan: The plan contains no unresolved placeholder markers and no open-ended implementation instructions without a concrete file path and command.
- Type consistency: Backend DTO names and frontend type names are introduced before use in later steps.
