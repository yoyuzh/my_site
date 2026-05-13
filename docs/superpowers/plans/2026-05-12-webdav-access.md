# WebDAV Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow external hosts to mount a user's personal cloud drive through WebDAV.

**Architecture:** Add `files.webdav` as a protocol-adapter module and keep all business truth in existing owners. WebDAV authenticates with identity-owned application passwords, then calls `files.workspace.api` path-oriented contracts for list, read, write, move, and delete; workspace continues to orchestrate content/upload completion through existing module APIs.

**Tech Stack:** Spring Boot 3.3.8, Java 17, Spring Security, Spring MVC, JPA, existing Maven test stack, XML DOM/StAX standard Java APIs.

---

## Source Documents

- Design: `docs/superpowers/specs/2026-05-12-webdav-access-design.md`
- Architecture: `backend-next/archtecture.md`
- API ownership: `backend-next/api-reference.md`
- Dependency whitelist: `docs/backend-next/module-dependency-whitelist.md`
- Directory responsibilities: `docs/backend-next/directory-responsibilities.md`
- Rule ownership: `docs/backend-next/rule-ownership-matrix.md`

## Scope

In scope:

- `Basic` authentication for `/dav/**` using WebDAV-specific application passwords.
- User-owned workspace root mounted at `/dav`.
- `OPTIONS`, `PROPFIND`, `HEAD`, `GET`, `PUT`, `MKCOL`, `DELETE`, `MOVE`.
- `Depth: 0` and `Depth: 1` for `PROPFIND`.
- First-version single-request `PUT` with existing max upload size and quota checks.
- Backend automated tests and manual external-client verification checklist.

Out of scope:

- Share/team/public mount surfaces.
- `COPY`, `LOCK`, `UNLOCK`.
- `Depth: infinity`.
- Partial upload, resumable WebDAV upload, or client sync conflict resolution.
- Frontend UI for managing WebDAV credentials unless explicitly added later.
- Deployment or reverse-proxy changes.

## File Structure

### Identity Access

- Create: `backend/src/main/java/com/yoyuzh/identity/access/api/IdentityWebDavCredentialApi.java`
  - Public identity contract consumed by WebDAV authentication.
- Create: `backend/src/main/java/com/yoyuzh/identity/access/api/IdentityWebDavCredentialIssueResult.java`
  - Result for issuing a WebDAV application password.
- Create: `backend/src/main/java/com/yoyuzh/identity/access/internal/domain/WebDavCredential.java`
  - Entity storing user id, hashed credential, enabled flag, timestamps.
- Create: `backend/src/main/java/com/yoyuzh/identity/access/internal/infra/WebDavCredentialRepository.java`
  - JPA repository for the credential entity.
- Create: `backend/src/main/java/com/yoyuzh/identity/access/internal/application/RuntimeIdentityWebDavCredentialApi.java`
  - Validates and issues WebDAV credentials.
- Create: `backend/src/test/java/com/yoyuzh/identity/access/internal/application/RuntimeIdentityWebDavCredentialApiTest.java`
  - Verifies issue, hash, validate, reject invalid, reject banned.

### Boot Security

- Create: `backend/src/main/java/com/yoyuzh/boot/security/WebDavBasicAuthenticationFilter.java`
  - Authenticates `/dav/**` with Basic credentials through `IdentityWebDavCredentialApi`.
- Modify: `backend/src/main/java/com/yoyuzh/boot/security/SecurityConfig.java`
  - Registers `/dav/**` as authenticated and inserts the WebDAV Basic filter before JWT auth.
- Create: `backend/src/test/java/com/yoyuzh/boot/security/WebDavBasicAuthenticationFilterTest.java`
  - Verifies missing, malformed, invalid, and valid Basic auth behavior.

### Workspace API

- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspacePathNodeApi.java`
  - Path-based query contract for WebDAV.
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspacePathDownloadApi.java`
  - Path-based download contract for WebDAV.
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspacePathWriteApi.java`
  - Path-based write/move/delete contract for WebDAV.
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WebDavWorkspacePutCommand.java`
  - Input stream command for WebDAV `PUT`.
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathNodeApi.java`
  - Finds and lists active owned nodes by logical path.
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathDownloadApi.java`
  - Downloads an owned file by logical path.
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathWriteApi.java`
  - Creates directory, writes file, moves, and recycles by logical path.
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/WorkspaceModuleConfiguration.java`
  - Registers the new workspace path API beans if constructor injection does not auto-register them.
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java`
  - Adds exact active node lookup by user/path/filename if existing method is insufficient for root pseudo-resource handling.
- Create: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathNodeApiTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathDownloadApiTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathWriteApiTest.java`

### WebDAV Module

- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/domain/WebDavPath.java`
  - Validated WebDAV path value object.
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/domain/WebDavDepth.java`
  - Parses and validates `Depth`.
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/application/WebDavPathMapper.java`
  - Maps `/dav/**` request paths to workspace logical paths.
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/application/WebDavPropertyService.java`
  - Builds WebDAV property models from workspace snapshots.
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/application/WebDavService.java`
  - Orchestrates WebDAV methods through workspace APIs.
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/web/WebDavController.java`
  - Spring MVC adapter for WebDAV methods and raw request/response behavior.
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/web/WebDavXmlWriter.java`
  - Writes `DAV:` `multistatus` XML.
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/web/WebDavHttpHeaders.java`
  - Header constants and helpers.
- Create: `backend/src/test/java/com/yoyuzh/files/webdav/internal/application/WebDavPathMapperTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/webdav/internal/application/WebDavPropertyServiceTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/webdav/internal/web/WebDavControllerTest.java`

## Task 1: Add Identity-Owned WebDAV Credentials

**Files:**

- Create: `backend/src/main/java/com/yoyuzh/identity/access/api/IdentityWebDavCredentialApi.java`
- Create: `backend/src/main/java/com/yoyuzh/identity/access/api/IdentityWebDavCredentialIssueResult.java`
- Create: `backend/src/main/java/com/yoyuzh/identity/access/internal/domain/WebDavCredential.java`
- Create: `backend/src/main/java/com/yoyuzh/identity/access/internal/infra/WebDavCredentialRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/identity/access/internal/application/RuntimeIdentityWebDavCredentialApi.java`
- Create: `backend/src/test/java/com/yoyuzh/identity/access/internal/application/RuntimeIdentityWebDavCredentialApiTest.java`

- [ ] **Step 1: Write failing credential API tests**

Create tests that cover:

```java
@Test
void shouldIssueAndValidateWebDavCredential() {
    IdentityWebDavCredentialIssueResult issued = api.issueOrReplaceCredential(userId);

    Optional<IdentityAuthenticatedUser> authenticated =
            api.authenticate(username, issued.plaintextPassword());

    assertThat(authenticated).isPresent();
    assertThat(authenticated.get().id()).isEqualTo(userId);
}

@Test
void shouldRejectInvalidWebDavCredential() {
    assertThat(api.authenticate(username, "wrong-password")).isEmpty();
}

@Test
void shouldRejectBannedUser() {
    banUser(userId);

    IdentityWebDavCredentialIssueResult issued = api.issueOrReplaceCredential(userId);

    assertThat(api.authenticate(username, issued.plaintextPassword())).isEmpty();
}

@Test
void shouldStoreOnlyHashedCredential() {
    IdentityWebDavCredentialIssueResult issued = api.issueOrReplaceCredential(userId);

    WebDavCredential stored = repository.findByUserId(userId).orElseThrow();

    assertThat(stored.getPasswordHash()).isNotEqualTo(issued.plaintextPassword());
    assertThat(passwordEncoder.matches(issued.plaintextPassword(), stored.getPasswordHash())).isTrue();
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeIdentityWebDavCredentialApiTest
```

Expected: compilation or test failure because the credential API and entity do not exist.

- [ ] **Step 3: Implement identity API contracts**

Create:

```java
package com.yoyuzh.identity.access.api;

import java.util.Optional;

public interface IdentityWebDavCredentialApi {

    IdentityWebDavCredentialIssueResult issueOrReplaceCredential(Long userId);

    Optional<IdentityAuthenticatedUser> authenticate(String username, String plaintextPassword);
}
```

```java
package com.yoyuzh.identity.access.api;

public record IdentityWebDavCredentialIssueResult(
        Long userId,
        String plaintextPassword
) {
}
```

- [ ] **Step 4: Implement credential entity and repository**

Create a JPA entity with fields:

```java
Long id;
Long userId;
String passwordHash;
boolean enabled;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

Repository contract:

```java
Optional<WebDavCredential> findByUserId(Long userId);
```

- [ ] **Step 5: Implement `RuntimeIdentityWebDavCredentialApi`**

Implementation rules:

- Generate a random credential using `SecureRandom`.
- Store only `PasswordEncoder.encode(rawPassword)`.
- Resolve users through `UserRepository.findByUsername(...)` and `UserRepository.findById(...)`.
- Return empty when user is missing, banned, credential missing, disabled, or password mismatch.
- Convert to `IdentityAuthenticatedUser` using the same field mapping as `RuntimeIdentityAuthenticationApi`.

- [ ] **Step 6: Run focused test and verify pass**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeIdentityWebDavCredentialApiTest
```

Expected: pass.

## Task 2: Add `/dav/**` Basic Authentication

**Files:**

- Create: `backend/src/main/java/com/yoyuzh/boot/security/WebDavBasicAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/yoyuzh/boot/security/SecurityConfig.java`
- Create: `backend/src/test/java/com/yoyuzh/boot/security/WebDavBasicAuthenticationFilterTest.java`

- [ ] **Step 1: Write failing filter tests**

Test cases:

```java
@Test
void shouldIgnoreNonDavRequests() {
    request.setRequestURI("/api/files/list");

    filter.doFilter(request, response, chain);

    verifyNoInteractions(identityWebDavCredentialApi);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
}

@Test
void shouldRejectDavRequestWithoutBasicHeader() {
    request.setRequestURI("/dav");

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("WWW-Authenticate")).contains("Basic");
}

@Test
void shouldAuthenticateDavRequestWithValidCredential() {
    request.setRequestURI("/dav");
    request.addHeader("Authorization", basic(username, password));
    when(identityWebDavCredentialApi.authenticate(username, password)).thenReturn(Optional.of(authenticatedUser));

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
}
```

- [ ] **Step 2: Run focused test and verify failure**

Run:

```bash
cd backend && mvn test -Dtest=WebDavBasicAuthenticationFilterTest
```

Expected: compilation failure because the filter does not exist.

- [ ] **Step 3: Implement the filter**

Rules:

- Apply only to `/dav` and `/dav/**`.
- Decode HTTP Basic credentials as UTF-8.
- Authenticate through `IdentityWebDavCredentialApi`.
- Put an `AuthenticatedUserPrincipal` into `SecurityContextHolder` on success.
- Return `401` with `WWW-Authenticate: Basic realm="yoyuzh-webdav"` on failure.
- Do not write JSON `ApiResponse` bodies for WebDAV authentication failures.

- [ ] **Step 4: Register security route**

Modify `SecurityConfig`:

```java
.requestMatchers("/dav", "/dav/**").authenticated()
```

Register the filter before `JwtAuthenticationFilter`.

- [ ] **Step 5: Run focused test and verify pass**

Run:

```bash
cd backend && mvn test -Dtest=WebDavBasicAuthenticationFilterTest
```

Expected: pass.

## Task 3: Add Workspace Path Query And Download APIs

**Files:**

- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspacePathNodeApi.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspacePathDownloadApi.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathNodeApi.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathDownloadApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/workspace/internal/infra/StoredFileRepository.java`
- Create: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathNodeApiTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathDownloadApiTest.java`

- [ ] **Step 1: Write failing path query tests**

Cover:

```java
@Test
void shouldReturnRootPseudoNode() {
    Optional<WorkspaceFileSnapshot> root = api.findOwnedActiveNodeByPath(userId, "/");

    assertThat(root).isPresent();
    assertThat(root.get().directory()).isTrue();
    assertThat(root.get().path()).isEqualTo("/");
}

@Test
void shouldFindOwnedFileByLogicalPath() {
    createFile(userId, "/Docs", "a.txt");

    Optional<WorkspaceFileSnapshot> file = api.findOwnedActiveNodeByPath(userId, "/Docs/a.txt");

    assertThat(file).isPresent();
    assertThat(file.get().filename()).isEqualTo("a.txt");
}

@Test
void shouldNotFindOtherUsersFileByPath() {
    createFile(otherUserId, "/", "a.txt");

    assertThat(api.findOwnedActiveNodeByPath(userId, "/a.txt")).isEmpty();
}
```

- [ ] **Step 2: Write failing path download tests**

Cover:

```java
@Test
void shouldDownloadOwnedFileByLogicalPath() {
    createFileWithBlob(userId, "/", "a.txt", "hello".getBytes(UTF_8));

    WorkspaceDownloadResult result = api.downloadOwnedFileByPath(userId, "/a.txt");

    assertThat(result.redirect()).isFalse();
    assertThat(result.body()).isEqualTo("hello".getBytes(UTF_8));
}

@Test
void shouldRejectDirectoryDownloadByPath() {
    createDirectory(userId, "/", "Docs");

    assertThatThrownBy(() -> api.downloadOwnedFileByPath(userId, "/Docs"))
            .isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 3: Run focused tests and verify failure**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeWorkspacePathNodeApiTest,RuntimeWorkspacePathDownloadApiTest
```

Expected: compilation failure because APIs do not exist.

- [ ] **Step 4: Implement API contracts**

```java
public interface WorkspacePathNodeApi {
    Optional<WorkspaceFileSnapshot> findOwnedActiveNodeByPath(Long userId, String normalizedLogicalPath);
    PageResponse<FileMetadataResponse> listOwnedDirectory(Long userId, String normalizedDirectoryPath, int page, int size);
}
```

```java
public interface WorkspacePathDownloadApi {
    WorkspaceDownloadResult downloadOwnedFileByPath(Long userId, String normalizedLogicalPath);
}
```

- [ ] **Step 5: Implement runtime query and download**

Rules:

- Root `/` is a pseudo-directory and is not stored as a `StoredFile`.
- For non-root paths, split parent path and leaf name using `WorkspacePathPolicy`.
- Use owned active lookup only.
- Directory listing delegates to existing directory listing behavior.
- Download delegates to existing `FileService.download(userId, fileId)` after path lookup.

- [ ] **Step 6: Run focused tests and verify pass**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeWorkspacePathNodeApiTest,RuntimeWorkspacePathDownloadApiTest
```

Expected: pass.

## Task 4: Add Workspace Path Write API

**Files:**

- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WorkspacePathWriteApi.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/api/WebDavWorkspacePutCommand.java`
- Create: `backend/src/main/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathWriteApi.java`
- Create: `backend/src/test/java/com/yoyuzh/files/workspace/internal/application/RuntimeWorkspacePathWriteApiTest.java`

- [ ] **Step 1: Write failing path write tests**

Cover:

```java
@Test
void shouldCreateDirectoryByPath() {
    FileMetadataResponse response = api.createDirectoryByPath(userId, "/Docs");

    assertThat(response.directory()).isTrue();
    assertThat(response.filename()).isEqualTo("Docs");
}

@Test
void shouldPutNewFileByPath() {
    WebDavWorkspacePutCommand command = command(user, "/Docs/a.txt", "text/plain", bytes("hello"), false);

    FileMetadataResponse response = api.putFileByPath(command);

    assertThat(response.filename()).isEqualTo("a.txt");
    assertThat(response.size()).isEqualTo(5);
}

@Test
void shouldOverwriteExistingFileWhenRequested() {
    putExistingFile(userId, "/Docs/a.txt", "old");

    FileMetadataResponse response = api.putFileByPath(command(user, "/Docs/a.txt", "text/plain", bytes("new"), true));

    assertThat(response.size()).isEqualTo(3);
}

@Test
void shouldRejectOverwriteWhenTargetIsDirectory() {
    createDirectory(userId, "/", "Docs");

    assertThatThrownBy(() -> api.putFileByPath(command(user, "/Docs", "text/plain", bytes("x"), true)))
            .isInstanceOf(BusinessException.class);
}

@Test
void shouldMoveByPath() {
    putExistingFile(userId, "/Docs/a.txt", "hello");

    WorkspaceMutationResult result = api.moveByPath(userId, "/Docs/a.txt", "/Docs/b.txt", false);

    assertThat(result.file().filename()).isEqualTo("b.txt");
}

@Test
void shouldRecycleByPath() {
    putExistingFile(userId, "/Docs/a.txt", "hello");

    WorkspaceLifecycleResult result = api.recycleByPath(userId, "/Docs/a.txt");

    assertThat(result.file().filename()).isEqualTo("a.txt");
}
```

- [ ] **Step 2: Run focused test and verify failure**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeWorkspacePathWriteApiTest
```

Expected: compilation failure because the write API does not exist.

- [ ] **Step 3: Implement write contracts**

```java
public record WebDavWorkspacePutCommand(
        WorkspaceUserContext user,
        String normalizedLogicalPath,
        String contentType,
        long size,
        InputStream content,
        boolean overwrite
) {
}
```

```java
public interface WorkspacePathWriteApi {
    FileMetadataResponse createDirectoryByPath(Long userId, String normalizedLogicalPath);
    FileMetadataResponse putFileByPath(WebDavWorkspacePutCommand command);
    WorkspaceMutationResult moveByPath(Long userId, String fromLogicalPath, String toLogicalPath, boolean overwrite);
    WorkspaceLifecycleResult recycleByPath(Long userId, String normalizedLogicalPath);
}
```

- [ ] **Step 4: Implement path write service**

Rules:

- Create directory delegates to `WorkspaceDirectoryApi.createDirectory(...)`.
- `PUT` validates parent directory through `WorkspacePathPolicy`.
- `PUT` enforces quota and max upload size through existing workspace/upload rule services.
- Store the request body through existing content/upload completion contracts instead of inserting a workspace row directly from WebDAV code.
- Overwrite means replace an existing file at the same logical path after validating it is not a directory.
- `MOVE` maps source and destination logical paths to existing workspace rename/move operations.
- `DELETE` delegates to existing recycle lifecycle.

- [ ] **Step 5: Run focused test and verify pass**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeWorkspacePathWriteApiTest
```

Expected: pass.

## Task 5: Add WebDAV Path And Property Domain

**Files:**

- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/domain/WebDavPath.java`
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/domain/WebDavDepth.java`
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/application/WebDavPathMapper.java`
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/application/WebDavPropertyService.java`
- Create: `backend/src/test/java/com/yoyuzh/files/webdav/internal/application/WebDavPathMapperTest.java`
- Create: `backend/src/test/java/com/yoyuzh/files/webdav/internal/application/WebDavPropertyServiceTest.java`

- [ ] **Step 1: Write failing path mapper tests**

Cover:

```java
@Test
void shouldMapDavRootToWorkspaceRoot() {
    assertThat(mapper.toWorkspacePath("/dav")).isEqualTo("/");
}

@Test
void shouldDecodePercentEncodedSegmentsOnce() {
    assertThat(mapper.toWorkspacePath("/dav/Docs/a%20b.txt")).isEqualTo("/Docs/a b.txt");
}

@Test
void shouldRejectParentTraversal() {
    assertThatThrownBy(() -> mapper.toWorkspacePath("/dav/../secret"))
            .isInstanceOf(BusinessException.class);
}

@Test
void shouldRejectDepthInfinity() {
    assertThatThrownBy(() -> WebDavDepth.parse("infinity"))
            .isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 2: Write failing property service tests**

Cover directory and file property models:

```java
@Test
void shouldMarkDirectoryResourceTypeCollection() {
    WebDavResourceProperties properties = service.fromSnapshot(directorySnapshot);

    assertThat(properties.collection()).isTrue();
    assertThat(properties.contentLength()).isZero();
}

@Test
void shouldIncludeFileLengthContentTypeAndStableEtag() {
    WebDavResourceProperties properties = service.fromSnapshot(fileSnapshot);

    assertThat(properties.contentLength()).isEqualTo(5);
    assertThat(properties.contentType()).isEqualTo("text/plain");
    assertThat(properties.etag()).contains(String.valueOf(fileSnapshot.id()));
}
```

- [ ] **Step 3: Run focused tests and verify failure**

Run:

```bash
cd backend && mvn test -Dtest=WebDavPathMapperTest,WebDavPropertyServiceTest
```

Expected: compilation failure.

- [ ] **Step 4: Implement mapper and property model**

Rules:

- Accept `/dav` and `/dav/**`.
- Normalize trailing slash but preserve root `/`.
- Reject invalid path traversal and control characters.
- Parse `Depth` default as `infinity` only where WebDAV spec defaults apply, then reject unsupported infinity with a clear business exception.
- Build ETags from file id, size, and created timestamp.

- [ ] **Step 5: Run focused tests and verify pass**

Run:

```bash
cd backend && mvn test -Dtest=WebDavPathMapperTest,WebDavPropertyServiceTest
```

Expected: pass.

## Task 6: Implement WebDAV Controller And XML Responses

**Files:**

- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/application/WebDavService.java`
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/web/WebDavController.java`
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/web/WebDavXmlWriter.java`
- Create: `backend/src/main/java/com/yoyuzh/files/webdav/internal/web/WebDavHttpHeaders.java`
- Create: `backend/src/test/java/com/yoyuzh/files/webdav/internal/web/WebDavControllerTest.java`

- [ ] **Step 1: Write failing MockMvc tests**

Cover:

```java
@Test
void optionsShouldAdvertiseDavMethods() throws Exception {
    mockMvc.perform(options("/dav").with(validWebDavBasic()))
            .andExpect(status().isOk())
            .andExpect(header().string("DAV", "1"))
            .andExpect(header().string("Allow", containsString("PROPFIND")));
}

@Test
void propfindDepthOneShouldReturnMultiStatus() throws Exception {
    mockMvc.perform(request("PROPFIND", "/dav").header("Depth", "1").with(validWebDavBasic()))
            .andExpect(status().isMultiStatus())
            .andExpect(content().contentTypeCompatibleWith("application/xml"))
            .andExpect(xpath("/*[local-name()='multistatus']").exists());
}

@Test
void getShouldDownloadFile() throws Exception {
    mockMvc.perform(get("/dav/Docs/a.txt").with(validWebDavBasic()))
            .andExpect(status().isOk())
            .andExpect(content().bytes("hello".getBytes(UTF_8)));
}

@Test
void putShouldCreateFile() throws Exception {
    mockMvc.perform(put("/dav/Docs/a.txt").with(validWebDavBasic())
                    .contentType("text/plain")
                    .content("hello"))
            .andExpect(status().isCreated());
}

@Test
void mkcolShouldCreateDirectory() throws Exception {
    mockMvc.perform(request("MKCOL", "/dav/NewFolder").with(validWebDavBasic()))
            .andExpect(status().isCreated());
}

@Test
void moveShouldRenameFile() throws Exception {
    mockMvc.perform(request("MOVE", "/dav/Docs/a.txt").with(validWebDavBasic())
                    .header("Destination", "http://localhost/dav/Docs/b.txt")
                    .header("Overwrite", "F"))
            .andExpect(status().isCreated());
}

@Test
void deleteShouldRecycleNode() throws Exception {
    mockMvc.perform(delete("/dav/Docs/a.txt").with(validWebDavBasic()))
            .andExpect(status().isNoContent());
}
```

- [ ] **Step 2: Run focused controller test and verify failure**

Run:

```bash
cd backend && mvn test -Dtest=WebDavControllerTest
```

Expected: compilation failure.

- [ ] **Step 3: Implement service orchestration**

Rules:

- Resolve current user id from `AuthenticatedUserPrincipal`.
- `OPTIONS` does not call workspace.
- `PROPFIND` calls path node/list APIs and returns a WebDAV model.
- `GET` and `HEAD` call path node/download APIs.
- `PUT` builds a `WorkspaceUserContext` from authenticated principal and calls `WorkspacePathWriteApi.putFileByPath(...)`.
- `MKCOL` calls `createDirectoryByPath(...)`.
- `DELETE` calls `recycleByPath(...)`.
- `MOVE` requires a `Destination` under `/dav`; reject cross-root destinations.

- [ ] **Step 4: Implement XML writer**

Use Java XML APIs. Output:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>/dav/Docs/a.txt</D:href>
    <D:propstat>
      <D:prop>
        <D:displayname>a.txt</D:displayname>
        <D:getcontentlength>5</D:getcontentlength>
        <D:getcontenttype>text/plain</D:getcontenttype>
        <D:getetag>"1-5"</D:getetag>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>
```

- [ ] **Step 5: Map WebDAV errors**

Mapping:

- missing node -> `404`
- invalid parent -> `409`
- unsupported depth/method -> `403` or `405`
- duplicate target without overwrite -> `412`
- permission failure -> `403`
- unauthenticated -> `401`

- [ ] **Step 6: Run focused controller test and verify pass**

Run:

```bash
cd backend && mvn test -Dtest=WebDavControllerTest
```

Expected: pass.

## Task 7: Integration And Regression Verification

**Files:**

- Source changes only if tests expose a defect.

- [ ] **Step 1: Run focused backend WebDAV suite**

Run:

```bash
cd backend && mvn test -Dtest=RuntimeIdentityWebDavCredentialApiTest,WebDavBasicAuthenticationFilterTest,RuntimeWorkspacePathNodeApiTest,RuntimeWorkspacePathDownloadApiTest,RuntimeWorkspacePathWriteApiTest,WebDavPathMapperTest,WebDavPropertyServiceTest,WebDavControllerTest
```

Expected: pass.

- [ ] **Step 2: Run full backend test suite**

Run:

```bash
cd backend && mvn test
```

Expected: pass.

- [ ] **Step 3: Manual external-client verification**

Start backend in dev profile:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Use a generated WebDAV application password for a test user.

macOS:

```bash
mount_webdav https://<host>/dav /Volumes/yoyuzh-webdav
```

Windows:

```powershell
net use Z: https://<host>/dav /user:<username> <webdav-password>
```

Verify:

- list root
- create a folder
- upload `hello.txt`
- download/open `hello.txt`
- rename `hello.txt`
- move it into a folder
- delete it

## Self-Review

- Spec coverage: the plan covers authentication, `/dav/**` routing, path mapping, WebDAV method handling, XML responses, workspace API boundaries, and verification.
- Module boundary: `files.webdav` consumes `identity.access.api` and `files.workspace.api`; it does not import `files.workspace.internal`, `files.content.internal`, or `files.upload.internal`.
- Command validity: all verification commands are existing backend Maven commands from `backend/`.
- Explicit exclusions: locks, shares, teams, partial upload, and frontend credential UI are deferred.
- Risk: `PUT` overwrite behavior may require careful implementation inside `files.workspace` to avoid leaving orphaned blobs. The implementation must reuse existing content cleanup and workspace lifecycle paths where possible.
