# Multi User Platform Upgrade Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前“邀请制多用户个人盘”稳定后，把系统升级到最小可用的“空间 + 成员角色 + 文件 ACL + 站内共享 + 审计”平台模型，同时不破坏现有个人网盘、公开分享、快传和管理台主链路。

**Architecture:** 继续保留现有 `portal_user + StoredFile + FileBlob/FileEntity` 主模型，不推翻个人网盘语义，而是在其上增加 `Space`、`SpaceMember`、`FilePermissionEntry` 和 `AuditLog` 这四层扩展。个人网盘先通过“每个用户自动拥有一个 personal space”兼容到新模型，桌面端和管理台先接入空间与权限入口，移动端只保兼容、不在本阶段新增完整协作 UI。

**Tech Stack:** Spring Boot 3.3.8, Spring Data JPA, H2/MySQL, React 19, TypeScript, Vite 6, Tailwind CSS v4, existing `/api/v2` patterns, existing `mvn test`, `npm run test`, `npm run lint`, @test-driven-development.

---

## Scope And Sequencing

- 本计划默认在当前 `2026-04-08-cloudreve-inspired-upgrade-and-refactor.md` 的既定升级、尤其是阶段 6 和全站前端重设计完成并合入后执行。
- 本计划只覆盖桌面 Web、现有管理台、后端模型/API、现有测试体系，不在本阶段实现移动端协作页、WebDAV 权限联动、组织/部门层级、团队回收站、第三方 OAuth scope 细化。
- 所有新能力优先挂在 `/api/v2/**`；旧 `/api/files/**`、`/api/admin/**` 只做必要兼容，不强制一次性重写。
- 执行中始终使用 @test-driven-development：先写失败测试，再补最小实现，再跑最小验证，最后补全集验证。

### Task 1: Add Space Domain Skeleton And Default Personal Space

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/space/Space.java`
- Create: `backend/src/main/java/com/yoyuzh/files/space/SpaceType.java`
- Create: `backend/src/main/java/com/yoyuzh/files/space/SpaceRole.java`
- Create: `backend/src/main/java/com/yoyuzh/files/space/SpaceMember.java`
- Create: `backend/src/main/java/com/yoyuzh/files/space/SpaceRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/files/space/SpaceMemberRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/files/space/SpaceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/auth/User.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFile.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java`
- Create: `backend/src/test/java/com/yoyuzh/files/space/SpaceServiceTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/FileServiceTest.java`

- [ ] **Step 1: Write the failing space-domain tests**

Run: `cd backend && mvn test -Dtest=SpaceServiceTest,FileServiceTest`
Expected: FAIL because `Space`, `SpaceMember`, and `StoredFile.space` support do not exist yet.

- [ ] **Step 2: Add the minimal schema and repository layer**

Implement:
- `Space` with `PERSONAL` and `COLLABORATIVE` types
- `SpaceMember` with `OWNER`, `MANAGER`, `EDITOR`, `VIEWER`
- `StoredFile.space` as nullable-to-required migration target
- repository methods for “default personal space by user” and “space membership by user”

- [ ] **Step 3: Add default personal-space bootstrap**

Implement `SpaceService.ensurePersonalSpace(User)` so:
- existing users can lazily receive a personal space
- new users automatically get one after registration/bootstrap
- files created through current private flows can still resolve to a valid space

- [ ] **Step 4: Re-run the targeted backend tests**

Run: `cd backend && mvn test -Dtest=SpaceServiceTest,FileServiceTest`
Expected: PASS for personal-space bootstrap and `StoredFile` compatibility assertions.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/space backend/src/main/java/com/yoyuzh/auth/User.java backend/src/main/java/com/yoyuzh/files/StoredFile.java backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java backend/src/test/java/com/yoyuzh/files/space/SpaceServiceTest.java backend/src/test/java/com/yoyuzh/files/FileServiceTest.java
git commit -m "feat(files): add space domain skeleton"
```

### Task 2: Route Existing File Ownership Through Spaces Without Breaking Private Disk

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileSearchService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/ShareV2Service.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/BackgroundTaskService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java`
- Create: `backend/src/test/java/com/yoyuzh/files/StoredFileSpaceCompatibilityTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/FileSearchServiceTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/BackgroundTaskServiceTest.java`

- [ ] **Step 1: Write failing compatibility tests for old personal-disk flows**

Run: `cd backend && mvn test -Dtest=StoredFileSpaceCompatibilityTest,FileSearchServiceTest,BackgroundTaskServiceTest`
Expected: FAIL because current create/search/task flows still assume “user owns everything directly”.

- [ ] **Step 2: Thread `spaceId` through internal file creation and lookup**

Implement the smallest compatibility rules:
- private files default to current user personal space
- current search/list/task flows keep returning the same personal files as before
- share import, archive/extract, upload complete, and metadata tasks preserve the owning space

- [ ] **Step 3: Keep old API semantics stable**

Do not change:
- existing personal path semantics
- public share token behavior
- anonymous transfer behavior
- current admin file listing shape

Only add internal `space` routing needed for future collaborative flows.

- [ ] **Step 4: Re-run targeted compatibility tests**

Run: `cd backend && mvn test -Dtest=StoredFileSpaceCompatibilityTest,FileSearchServiceTest,BackgroundTaskServiceTest`
Expected: PASS with no regression on existing personal-disk behavior.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/FileService.java backend/src/main/java/com/yoyuzh/files/FileSearchService.java backend/src/main/java/com/yoyuzh/files/ShareV2Service.java backend/src/main/java/com/yoyuzh/files/BackgroundTaskService.java backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java backend/src/test/java/com/yoyuzh/files/StoredFileSpaceCompatibilityTest.java backend/src/test/java/com/yoyuzh/files/FileSearchServiceTest.java backend/src/test/java/com/yoyuzh/files/BackgroundTaskServiceTest.java
git commit -m "refactor(files): route private disk through personal spaces"
```

### Task 3: Add V2 Space APIs And Membership Management

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/api/v2/spaces/SpaceV2Controller.java`
- Create: `backend/src/main/java/com/yoyuzh/api/v2/spaces/SpaceResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/api/v2/spaces/SpaceMemberResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/api/v2/spaces/CreateSpaceRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/api/v2/spaces/AddSpaceMemberRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/api/v2/spaces/UpdateSpaceMemberRoleRequest.java`
- Modify: `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/yoyuzh/auth/UserDetailsServiceImpl.java`
- Create: `backend/src/test/java/com/yoyuzh/api/v2/spaces/SpaceV2ControllerIntegrationTest.java`

- [ ] **Step 1: Write failing API tests for creating and listing spaces**

Run: `cd backend && mvn test -Dtest=SpaceV2ControllerIntegrationTest`
Expected: FAIL because `/api/v2/spaces/**` endpoints and security wiring do not exist.

- [ ] **Step 2: Implement the minimal V2 space endpoints**

Implement:
- `POST /api/v2/spaces`
- `GET /api/v2/spaces`
- `GET /api/v2/spaces/{id}`
- `POST /api/v2/spaces/{id}/members`
- `PATCH /api/v2/spaces/{id}/members/{userId}`
- `DELETE /api/v2/spaces/{id}/members/{userId}`

Rules:
- only authenticated users can list their spaces
- only `OWNER`/`MANAGER` can manage members
- personal spaces cannot remove the owner or add arbitrary members

- [ ] **Step 3: Return DTOs only**

Do not expose JPA entities directly. Keep API outputs small:
- space identity
- type
- display name
- current user role
- member summaries

- [ ] **Step 4: Re-run targeted V2 API tests**

Run: `cd backend && mvn test -Dtest=SpaceV2ControllerIntegrationTest`
Expected: PASS for create/list/member add/remove/role change rules.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/api/v2/spaces backend/src/main/java/com/yoyuzh/config/SecurityConfig.java backend/src/main/java/com/yoyuzh/auth/UserDetailsServiceImpl.java backend/src/test/java/com/yoyuzh/api/v2/spaces/SpaceV2ControllerIntegrationTest.java
git commit -m "feat(api): add v2 space and member management"
```

### Task 4: Add File ACL Entries And Permission Evaluation

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/files/permission/FilePermissionEntry.java`
- Create: `backend/src/main/java/com/yoyuzh/files/permission/FilePermissionRole.java`
- Create: `backend/src/main/java/com/yoyuzh/files/permission/FilePermissionSubjectType.java`
- Create: `backend/src/main/java/com/yoyuzh/files/permission/FilePermissionEntryRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/files/permission/FilePermissionService.java`
- Create: `backend/src/main/java/com/yoyuzh/files/permission/FileAccessEvaluator.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileSearchService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/ShareV2Service.java`
- Create: `backend/src/test/java/com/yoyuzh/files/permission/FilePermissionServiceTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/FileServiceEdgeCaseTest.java`

- [ ] **Step 1: Write failing ACL tests**

Run: `cd backend && mvn test -Dtest=FilePermissionServiceTest,FileServiceEdgeCaseTest`
Expected: FAIL because file access is still governed only by owner/admin checks.

- [ ] **Step 2: Implement the minimal ACL model**

Implement:
- subject types: `USER`, `SPACE`
- permission roles: `VIEWER`, `EDITOR`, `MANAGER`
- inheritance flag for directory-to-descendant lookup

Rules:
- owner/personal-space owner always has full access
- collaborative space role provides the default baseline
- explicit file ACL can grant additional access to a user

- [ ] **Step 3: Enforce ACL at service boundaries**

Apply permission checks to:
- list directory
- upload into folder
- rename/move/copy/delete
- create public share
- import into target folder
- start background tasks on a file

- [ ] **Step 4: Re-run the ACL and edge-case tests**

Run: `cd backend && mvn test -Dtest=FilePermissionServiceTest,FileServiceEdgeCaseTest`
Expected: PASS with clear denials for users outside the owning space or without explicit grants.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/files/permission backend/src/main/java/com/yoyuzh/files/FileService.java backend/src/main/java/com/yoyuzh/files/FileSearchService.java backend/src/main/java/com/yoyuzh/files/ShareV2Service.java backend/src/test/java/com/yoyuzh/files/permission/FilePermissionServiceTest.java backend/src/test/java/com/yoyuzh/files/FileServiceEdgeCaseTest.java
git commit -m "feat(files): add acl-based permission evaluation"
```

### Task 5: Add In-App Sharing And “Shared With Me” V2 Views

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/api/v2/files/ShareFileToUserV2Request.java`
- Create: `backend/src/main/java/com/yoyuzh/api/v2/files/SharedFileV2Response.java`
- Create: `backend/src/main/java/com/yoyuzh/api/v2/files/FilePermissionsV2Controller.java`
- Modify: `backend/src/main/java/com/yoyuzh/api/v2/files/FileSearchV2Controller.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/permission/FilePermissionService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java`
- Create: `backend/src/test/java/com/yoyuzh/api/v2/files/FilePermissionsV2ControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/files/FileShareControllerIntegrationTest.java`

- [ ] **Step 1: Write failing tests for in-app share and shared-with-me listing**

Run: `cd backend && mvn test -Dtest=FilePermissionsV2ControllerIntegrationTest,FileShareControllerIntegrationTest`
Expected: FAIL because there is no station-internal share grant endpoint or “shared with me” query.

- [ ] **Step 2: Add minimal in-app sharing endpoints**

Implement:
- `GET /api/v2/files/{fileId}/permissions`
- `PUT /api/v2/files/{fileId}/permissions`
- `DELETE /api/v2/files/{fileId}/permissions/{entryId}`
- `GET /api/v2/files/shared-with-me`

Rules:
- only `MANAGER` or owner can grant/revoke
- station-internal share writes ACL entries, not public share tokens
- `shared-with-me` excludes files already owned through the current user’s own personal space

- [ ] **Step 3: Keep public sharing separate**

Do not merge station-internal sharing into `FileShareLink`.
Continue using:
- `FileShareLink` for public token shares
- `FilePermissionEntry` for logged-in user-to-user sharing

- [ ] **Step 4: Re-run targeted integration tests**

Run: `cd backend && mvn test -Dtest=FilePermissionsV2ControllerIntegrationTest,FileShareControllerIntegrationTest`
Expected: PASS for grant, revoke, and `shared-with-me` listing behavior.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/api/v2/files backend/src/main/java/com/yoyuzh/files/permission/FilePermissionService.java backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java backend/src/test/java/com/yoyuzh/api/v2/files/FilePermissionsV2ControllerIntegrationTest.java backend/src/test/java/com/yoyuzh/files/FileShareControllerIntegrationTest.java
git commit -m "feat(files): add in-app sharing and shared-with-me"
```

### Task 6: Add Cross-Cutting Audit Logs And Admin Audit Read API

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/common/audit/AuditLogEntry.java`
- Create: `backend/src/main/java/com/yoyuzh/common/audit/AuditAction.java`
- Create: `backend/src/main/java/com/yoyuzh/common/audit/AuditLogEntryRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/common/audit/AuditLogService.java`
- Create: `backend/src/main/java/com/yoyuzh/admin/AdminAuditLogResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminController.java`
- Modify: `backend/src/main/java/com/yoyuzh/admin/AdminService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/space/SpaceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/permission/FilePermissionService.java`
- Modify: `backend/src/test/java/com/yoyuzh/admin/AdminControllerIntegrationTest.java`
- Create: `backend/src/test/java/com/yoyuzh/common/audit/AuditLogServiceTest.java`

- [ ] **Step 1: Write failing audit tests**

Run: `cd backend && mvn test -Dtest=AuditLogServiceTest,AdminControllerIntegrationTest`
Expected: FAIL because no audit entity/service exists and admin cannot query audit logs.

- [ ] **Step 2: Implement the smallest useful audit model**

Persist at least:
- actor user id
- action type
- target type
- target id
- summary text
- created at

Log these events:
- space create/member change
- file permission grant/revoke
- public share create/delete
- file delete/restore

- [ ] **Step 3: Add admin read API only**

Implement a read-only admin endpoint:
- `GET /api/admin/audit-logs?page=0&size=20&query=...`

Do not add end-user audit UI in this phase.

- [ ] **Step 4: Re-run audit tests**

Run: `cd backend && mvn test -Dtest=AuditLogServiceTest,AdminControllerIntegrationTest`
Expected: PASS for audit persistence and admin listing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yoyuzh/common/audit backend/src/main/java/com/yoyuzh/admin/AdminController.java backend/src/main/java/com/yoyuzh/admin/AdminService.java backend/src/main/java/com/yoyuzh/files/FileService.java backend/src/main/java/com/yoyuzh/files/space/SpaceService.java backend/src/main/java/com/yoyuzh/files/permission/FilePermissionService.java backend/src/test/java/com/yoyuzh/admin/AdminControllerIntegrationTest.java backend/src/test/java/com/yoyuzh/common/audit/AuditLogServiceTest.java
git commit -m "feat(admin): add audit log backbone"
```

### Task 7: Add Desktop-Web Space, Permission, And Shared-With-Me UI

**Files:**
- Modify: `front/src/lib/types.ts`
- Modify: `front/src/lib/api.ts`
- Create: `front/src/lib/spaces.ts`
- Create: `front/src/lib/spaces.test.ts`
- Create: `front/src/lib/file-permissions.ts`
- Create: `front/src/lib/file-permissions.test.ts`
- Modify: `front/src/App.tsx`
- Modify: `front/src/components/layout/Layout.tsx`
- Modify: `front/src/pages/files/FilesDirectoryRail.tsx`
- Modify: `front/src/pages/files/FilesToolbar.tsx`
- Modify: `front/src/pages/files/useFilesDirectoryState.ts`
- Create: `front/src/pages/Spaces.tsx`
- Create: `front/src/pages/SharedWithMe.tsx`
- Create: `front/src/pages/files/SpaceMembersDialog.tsx`
- Create: `front/src/pages/files/FilePermissionsDialog.tsx`
- Create: `front/src/pages/spaces-state.ts`
- Create: `front/src/pages/spaces-state.test.ts`
- Create: `front/src/pages/shared-with-me-state.ts`
- Create: `front/src/pages/shared-with-me-state.test.ts`

- [ ] **Step 1: Write failing frontend helper tests**

Run: `cd front && npm run test -- src/lib/spaces.test.ts src/lib/file-permissions.test.ts src/pages/spaces-state.test.ts src/pages/shared-with-me-state.test.ts`
Expected: FAIL because the new API helpers and page state modules do not exist.

- [ ] **Step 2: Add typed API helpers first**

Implement the smallest helpers for:
- list/create spaces
- list/update members
- get/update file permissions
- list shared-with-me files

Do not start with JSX-heavy pages before helpers and tests exist.

- [ ] **Step 3: Add desktop-only navigation and management UI**

Implement:
- `Spaces` page for list/create/member management
- `SharedWithMe` page for station-internal shares
- file page dialogs for members and permissions
- a space switcher in the file rail or toolbar

Rules:
- keep current private disk route working
- personal space remains default landing target
- mobile app can stay unchanged in this phase

- [ ] **Step 4: Re-run targeted frontend tests**

Run: `cd front && npm run test -- src/lib/spaces.test.ts src/lib/file-permissions.test.ts src/pages/spaces-state.test.ts src/pages/shared-with-me-state.test.ts`
Expected: PASS with helpers and page state stabilized before broad UI verification.

- [ ] **Step 5: Commit**

```bash
git add front/src/lib/types.ts front/src/lib/api.ts front/src/lib/spaces.ts front/src/lib/spaces.test.ts front/src/lib/file-permissions.ts front/src/lib/file-permissions.test.ts front/src/App.tsx front/src/components/layout/Layout.tsx front/src/pages/files/FilesDirectoryRail.tsx front/src/pages/files/FilesToolbar.tsx front/src/pages/files/useFilesDirectoryState.ts front/src/pages/Spaces.tsx front/src/pages/SharedWithMe.tsx front/src/pages/files/SpaceMembersDialog.tsx front/src/pages/files/FilePermissionsDialog.tsx front/src/pages/spaces-state.ts front/src/pages/spaces-state.test.ts front/src/pages/shared-with-me-state.ts front/src/pages/shared-with-me-state.test.ts
git commit -m "feat(front): add spaces and shared-with-me ui"
```

### Task 8: Full Verification And Documentation Handoff

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/api-reference.md`
- Modify: `memory.md`
- Modify only if verification reveals gaps: `backend/**`, `front/**`

- [ ] **Step 1: Run full backend verification**

Run: `cd backend && mvn test`
Expected: PASS with no regressions in auth, files, tasks, shares, admin, and API v2 tests.

- [ ] **Step 2: Run full frontend verification**

Run: `cd front && npm run test`
Expected: PASS with no regressions in files, transfer, admin, and new spaces/shared state tests.

- [ ] **Step 3: Run frontend type/lint verification**

Run: `cd front && npm run lint`
Expected: PASS with no TypeScript errors.

- [ ] **Step 4: Update project memory and architecture docs**

Document:
- space model
- member roles
- ACL rules
- in-app sharing vs public share split
- admin audit log availability
- mobile deferred scope

- [ ] **Step 5: Final commit**

```bash
git add docs/architecture.md docs/api-reference.md memory.md
git commit -m "docs: record multi-user platform phase 2 architecture"
```

## Deferred Explicitly

- 移动端 `MobileFiles` / `MobileOverview` / `MobileApp` 的空间协作 UI
- 组织/部门/用户组层级，不在本计划混入
- WebDAV、OAuth scope、桌面同步客户端权限联动
- 团队回收站、空间级生命周期策略、空间级存储策略切换
- 文件评论、审批流、在线协作文档

## Success Criteria

- 现有个人网盘用户登录后仍能像今天一样使用自己的私有文件空间。
- 每个用户可看到自己的 personal space，且可创建 collaborative space。
- collaborative space 支持最小成员角色与目录/文件 ACL。
- 站内用户可通过 ACL 被共享文件，并在 “Shared With Me” 中看到结果。
- 审计日志能覆盖空间、权限、分享、删除/恢复等关键动作。
- 旧公开分享、快传、上传会话、后台任务和管理台文件列表不被打断。

