# WebDAV Access Design

## Goal

External hosts can mount a user's personal cloud drive through WebDAV and perform the first practical read/write workflow:

- list directories
- download files
- upload or overwrite files
- create directories
- move or rename nodes
- delete nodes through the existing workspace lifecycle

The first version exposes only the authenticated user's own workspace root. Shared folders, transfer sessions, team spaces, public shares, WebDAV locks, and sync-conflict reconciliation are out of scope.

## Current Project Context

The backend is a modular monolith. File tree truth belongs to `files.workspace`, physical content truth belongs to `files.content`, upload ingress belongs to `files.upload`, and storage-policy decisions belong to `platform.storage`.

The repository already contains:

- `files.content.internal.infra.storage.WebDavFileContentStorage`, which is a WebDAV client-backed storage adapter.
- `files.upload.internal.application.UploadSessionTusService`, which supports Tus-backed upload ingress for local and WebDAV storage policies.
- `SecurityConfig`, which currently allows authenticated `/api/v2/files/**` and `/api/files/**` routes but denies unknown routes.

This feature is a different concern: it adds a public WebDAV protocol surface for external clients. It must not be confused with using WebDAV as an internal storage backend.

## Recommended Module Boundary

Create a new backend module package:

```text
backend/src/main/java/com/yoyuzh/files/webdav
├── api
└── internal
    ├── application
    ├── domain
    └── web
```

`files.webdav` owns only WebDAV protocol adaptation:

- request method dispatch for `OPTIONS`, `PROPFIND`, `GET`, `PUT`, `DELETE`, `MKCOL`, `MOVE`
- WebDAV XML response assembly
- WebDAV path decoding and normalization into workspace paths
- WebDAV status mapping such as `207 Multi-Status`, `201 Created`, `204 No Content`, `409 Conflict`, and `423 Locked` only if locks are added later
- Basic Auth entry for WebDAV-specific credentials
- integration orchestration through module `api` contracts

It must not own:

- workspace path legality
- duplicate-name rules
- final node creation
- physical blob registration
- storage placement or upload-mode policy
- share or transfer semantics

## Authentication Boundary

Use WebDAV-specific application passwords with HTTP Basic Auth.

The first implementation adds `identity.access.api.IdentityWebDavCredentialApi` and an internal identity implementation that can:

- validate a username and WebDAV application password
- reject banned users
- return `IdentityAuthenticatedUser`

The application password is stored hashed, not reversible. Normal browser JWTs remain unchanged. Normal account passwords are not reused for WebDAV.

Management UI/API for rotating WebDAV passwords is not required in the first backend implementation plan unless explicitly requested. The first backend plan may include a minimal authenticated API for issuing/replacing the user's WebDAV password because external clients need a credential to connect.

## WebDAV Path Semantics

Mount root:

```text
/dav
```

Workspace mapping:

```text
/dav                  -> workspace root "/"
/dav/Docs             -> workspace node "/Docs"
/dav/Docs/a.txt       -> workspace file named "a.txt" under "/Docs"
```

Rules:

- Percent-decode path segments once.
- Reject `..`, empty middle segments, control characters, and paths that normalize outside root.
- A trailing slash is accepted for directories.
- `PROPFIND /dav` with `Depth: 0` returns the root pseudo-resource.
- `PROPFIND /dav` with `Depth: 1` returns root plus immediate children.
- `Depth: infinity` is rejected with `403 Forbidden` in the first version.

## Required Workspace API Additions

The current workspace APIs are close but do not yet provide all path-oriented operations needed by WebDAV without touching `files.workspace.internal`.

Add path-oriented contracts to `files.workspace.api`:

- `WorkspacePathNodeApi`
  - `Optional<WorkspaceFileSnapshot> findOwnedActiveNodeByPath(Long userId, String normalizedLogicalPath)`
  - `PageResponse<FileMetadataResponse> listOwnedDirectory(Long userId, String normalizedDirectoryPath, int page, int size)`
- `WorkspacePathDownloadApi`
  - `WorkspaceDownloadResult downloadOwnedFileByPath(Long userId, String normalizedLogicalPath)`
- `WorkspacePathWriteApi`
  - `FileMetadataResponse createDirectoryByPath(Long userId, String normalizedLogicalPath)`
  - `FileMetadataResponse putFileByPath(WebDavWorkspacePutCommand command)`
  - `WorkspaceMutationResult moveByPath(Long userId, String fromLogicalPath, String toLogicalPath, boolean overwrite)`
  - `WorkspaceLifecycleResult recycleByPath(Long userId, String normalizedLogicalPath)`

These API additions are owned and implemented inside `files.workspace`. They may internally call existing workspace services and upload/content APIs. `files.webdav` consumes only these contracts.

## Upload And Content Flow

`PUT /dav/path/file.ext` must not write directly to storage and then insert a file row from the WebDAV module.

The first implementation should route `PUT` through a workspace-owned path write use case:

1. `files.webdav` authenticates and normalizes the WebDAV path.
2. `files.webdav` calls `WorkspacePathWriteApi.putFileByPath(...)`.
3. `files.workspace` validates parent path, duplicate or overwrite behavior, quota, and path legality.
4. `files.workspace` uses existing upload/content completion contracts to register stored content and create or replace the workspace node.
5. `files.content` remains the owner of blob references and content registration.

For the first version, full resumable upload over WebDAV is out of scope. A WebDAV `PUT` body is accepted as a single request and may be bounded by existing max file size policy.

## Method Support

Initial method matrix:

| Method | Scope | Expected status |
| --- | --- | --- |
| `OPTIONS` | advertise DAV support and methods | `200 OK` |
| `PROPFIND` | root or owned active node, `Depth: 0` or `1` | `207 Multi-Status` |
| `GET` | owned active file only | `200 OK` or redirect when existing download API redirects |
| `HEAD` | owned active file metadata | `200 OK` |
| `PUT` | create or overwrite a file | `201 Created` or `204 No Content` |
| `MKCOL` | create directory | `201 Created` |
| `DELETE` | recycle node using workspace lifecycle | `204 No Content` |
| `MOVE` | move or rename within `/dav` | `201 Created` or `204 No Content` |

Unsupported in first version:

- `COPY`
- `LOCK`
- `UNLOCK`
- partial/range upload
- `Depth: infinity`
- cross-user or share mount

## XML Response Rules

`PROPFIND` returns `application/xml; charset=UTF-8` with `DAV:` namespace and `multistatus` response entries. The first implementation should support these properties:

- `displayname`
- `resourcetype`
- `getcontentlength`
- `getcontenttype`
- `getetag`
- `creationdate`
- `getlastmodified`

Missing optional properties should be returned in a separate `propstat` with `404` only if the client explicitly requests properties outside the supported set.

## Security And Operational Notes

- Add a dedicated Basic Auth filter for `/dav/**`.
- Keep JWT auth untouched for existing REST APIs.
- Return WebDAV-compatible XML or empty error statuses where common clients expect them. Do not wrap WebDAV responses in `ApiResponse`.
- Do not log full WebDAV paths when they may contain user-sensitive filenames unless logs sanitize or truncate them.
- Keep the first implementation single-node friendly. Distributed WebDAV lock support is explicitly deferred.

## Testing Strategy

Backend tests:

- unit tests for path normalization and XML assembly
- identity application-password verification tests
- workspace path API tests for find/list/put/move/delete by logical path
- MockMvc tests for `OPTIONS`, `PROPFIND`, `GET`, `PUT`, `MKCOL`, `DELETE`, `MOVE`
- security tests that `/dav/**` rejects missing or invalid Basic credentials and accepts valid WebDAV credentials

Manual verification:

- mount from macOS Finder or `mount_webdav`
- mount from Windows "Map network drive" or `net use`
- verify list, upload small file, download, create folder, rename, move, delete

## Open Constraints

The first version assumes the backend is directly reachable by the external host over HTTPS. Reverse proxy configuration is deployment-specific and should be handled outside this code plan unless the user asks for deployment changes.
