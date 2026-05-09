# Mature Admin Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a mature, usable admin panel for `my_site` without adopting a heavy admin framework or weakening backend module boundaries.

**Architecture:** Keep the active `frontend/` React/Vite app and the existing `/api/admin/**` backend surface. The frontend will gain a native admin shell plus shared admin primitives backed by MUI, React Query, `@tanstack/react-table`, and `react-hook-form`. The backend will keep `ops.admin` as the governance entrypoint and call other domains only through their `api` packages; permissions and audit-write discipline are established before configuration and storage write features expand.

**Tech Stack:** Spring Boot 3.3.8, Java 17, Spring Security, Spring Data JPA, Maven, React 18, Vite 5, TypeScript, React Query, Axios, MUI 6, Tailwind CSS 3, lucide-react, TanStack Table, react-hook-form.

---

## Scope Check

This plan covers the first executable modernization track for the admin panel. It intentionally does not implement every future governance capability from `docs/superpowers/plans/2026-04-29-admin-governance-system.md`.

In scope:

- Replace the current scattered admin shell with a domain-grouped shell.
- Hide or de-emphasize unsupported Cloudreve-shaped pages such as `group`, `node`, and `oauth`.
- Add shared admin primitives for tables, filters, schema forms, confirmation dialogs, and status badges.
- Add the minimum backend/frontend permission model needed before expanding write actions.
- Establish audit write rules before storage/config write operations expand.
- Add read-only config schema endpoints and schema-driven frontend rendering.
- Later add DB-backed config values, history, and rollback after the read-only schema is stable.
- Modernize storage/upload governance around the existing `platform.storage.api` seam.

Out of scope for this plan:

- Replacing the app with `react-admin`, `refine`, or Ant Design Pro.
- Rewriting user-file workflows outside `/admin`.
- Introducing Nacos, Apollo, Spring Boot Admin, or a database-driven menu system.
- Moving secrets such as DB URL, Redis password, JWT secret, object storage secret keys, SSH keys, or deploy credentials into the admin panel.

## Execution Preconditions

- Execute implementation in a fresh worktree if possible. The current checkout may contain unrelated user changes.
- Do not run `npm` commands at the repository root.
- Frontend verification commands must be run from `frontend/`:
  - `npm run lint`
  - `npm run build`
- Backend verification commands must be run from `backend/`:
  - `mvn test`
  - `mvn package` only when packaging is needed.
- Backend work must keep the startup constraints from:
  - `backend-next/archtecture.md`
  - `backend-next/api-reference.md`
  - `docs/backend-next/module-dependency-whitelist.md`
  - `docs/backend-next/directory-responsibilities.md`
  - `docs/backend-next/rule-ownership-matrix.md`

## File Structure

### Frontend Files

- Modify: `frontend/package.json`
  - Add direct dependencies for `@tanstack/react-table` and `react-hook-form` if they are not already direct dependencies.
- Modify: `frontend/package-lock.json`
  - Keep lockfile consistent with `frontend/package.json`.
- Modify: `frontend/src/App.tsx`
  - Replace legacy admin route names with route groups aligned to real governance domains.
- Modify: `frontend/src/components/AdminLayout.tsx`
  - Either shrink this into a compatibility wrapper or replace it with `components/admin/AdminShell.tsx`.
- Create: `frontend/src/components/admin/AdminShell.tsx`
  - Own admin layout, grouped navigation, mobile navigation, active route highlighting, and return-to-dashboard command.
- Create: `frontend/src/components/admin/adminNavigation.ts`
  - Define route groups, labels, icons, required permissions, and unsupported-page visibility.
- Create: `frontend/src/components/admin/AdminPage.tsx`
  - Shared page frame with title, description, toolbar slot, error state, and loading state.
- Create: `frontend/src/components/admin/AdminDataTable.tsx`
  - Thin app-specific wrapper around TanStack Table and MUI/Tailwind table presentation.
- Create: `frontend/src/components/admin/AdminFilterBar.tsx`
  - Shared query/filter controls for admin list pages.
- Create: `frontend/src/components/admin/AdminConfirmDialog.tsx`
  - Shared confirmation dialog for destructive and governance actions.
- Create: `frontend/src/components/admin/AdminStatusBadge.tsx`
  - Shared status/risk badges.
- Create: `frontend/src/components/admin/AdminSchemaForm.tsx`
  - Schema-driven form layer using `react-hook-form` plus MUI `Controller`.
- Create: `frontend/src/components/admin/adminSchemaTypes.ts`
  - Frontend config schema field types and mapping helpers.
- Modify: `frontend/src/api/types.ts`
  - Add admin permission, config schema, audit, and schema-form DTOs.
- Modify: `frontend/src/api/queries.ts`
  - Add config schema and audit query hooks.
- Modify: `frontend/src/api/mutations.ts`
  - Add permission-safe admin mutations only when backed by real endpoints.
- Modify: `frontend/src/lib/session.ts`
  - Keep role-based admin access, then add optional permission-code helpers once backend exposes them.
- Modify: `frontend/src/pages/admin/AdminHome.tsx`
  - Move into the new shell and replace placeholder/chart copy with real summary cards or neutral empty states.
- Modify: `frontend/src/pages/admin/AdminSetting.tsx`
  - Migrate from hardcoded tabs toward schema-driven sections.
- Modify: `frontend/src/pages/admin/AdminUser.tsx`
  - Use `AdminDataTable`, `AdminConfirmDialog`, and permission-aware actions.
- Modify: `frontend/src/pages/admin/AdminPolicy.tsx`
  - Rename conceptually to storage policy governance and use shared primitives.
- Modify: `frontend/src/pages/admin/AdminFile.tsx`
  - Use shared table/filter/confirm primitives.
- Modify: `frontend/src/pages/admin/AdminBlob.tsx`
  - Use shared table/filter primitives.
- Modify: `frontend/src/pages/admin/AdminTask.tsx`
  - Use shared table/filter/status primitives.
- Modify: `frontend/src/pages/admin/AdminShare.tsx`
  - Use shared table/filter/confirm primitives.
- Modify: `frontend/src/pages/admin/AdminFileSystem.tsx`
  - Keep as system/storage status or merge into the system route.
- Modify: `frontend/src/pages/admin/AdminGroup.tsx`
  - Remove from active navigation or convert to unsupported-page redirect.
- Modify: `frontend/src/pages/admin/AdminNode.tsx`
  - Remove from active navigation or convert to unsupported-page redirect.
- Modify: `frontend/src/pages/admin/AdminOAuth.tsx`
  - Remove from active navigation or convert to unsupported-page redirect.
- Create: `frontend/src/pages/admin/AdminAudit.tsx`
  - Audit list and details, after backend audit query contract is confirmed.
- Create: `frontend/src/pages/admin/AdminConfig.tsx`
  - Schema-driven configuration surface.

### Backend Files

- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminAccessEvaluator.java`
  - Keep coarse admin check and prepare for operation-level permission codes.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminPermissionCode.java`
  - Stable permission codes for admin pages and actions.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminPermissionResponse.java`
  - DTO returned to the frontend for available admin capabilities.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminPermissionQueryApi.java`
  - Admin permission query seam.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/RuntimeAdminPermissionQueryApi.java`
  - Runtime permission query implementation backed by `identity.access.api.AdminAccessPolicy` initially.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminPermissionController.java`
  - `GET /api/admin/permissions`.
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminAuditService.java`
  - Keep the single audit write helper and make action detail contracts explicit.
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminAuditAction.java`
  - Add action names for config, storage, user, file, share, and task governance.
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminUserGovernanceService.java`
  - Ensure each write action records audit entries.
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminResourceGovernanceService.java`
  - Ensure file/share destructive actions record audit entries.
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceService.java`
  - Ensure storage create/update/status/migration actions record audit entries.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigDefinitionResponse.java`
  - Read-only config definition DTO.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigSnapshotResponse.java`
  - Read-only grouped config snapshot DTO.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigSchemaApi.java`
  - Config schema query seam.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/AdminConfigDefinition.java`
  - Internal immutable config definition.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/AdminConfigRegistry.java`
  - Code-registered config definitions.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/RuntimeAdminConfigSchemaApi.java`
  - Assembles definitions and read-only values from existing runtime settings and environment-backed properties.
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminConfigController.java`
  - `GET /api/admin/config/definitions` and `GET /api/admin/config/snapshot`.
- Later create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigValueEntity.java`
  - DB-backed config value record.
- Later create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigChangeLogEntity.java`
  - DB-backed config change history record.
- Later create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigValueRepository.java`
  - Config value repository.
- Later create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigChangeLogRepository.java`
  - Config history repository.

### Backend Tests

- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/RuntimeAdminPermissionQueryApiTest.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminAuditServiceTest.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/config/AdminConfigRegistryTest.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/web/AdminConfigControllerTest.java`
- Modify: `backend/src/test/java/com/yoyuzh/architecture/Task8OpsAdminArchitectureTest.java`

## Task 1: Dependency and Compatibility Baseline

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`

- [ ] **Step 1: Confirm direct dependency state**

Run:

```bash
cd frontend && node -e "const pkg=require('./package.json'); console.log(Boolean(pkg.dependencies['@tanstack/react-table']), Boolean(pkg.dependencies['react-hook-form']))"
```

Expected before implementation:

```text
false false
```

If `react-hook-form` is already direct by execution time, only add `@tanstack/react-table`.

- [ ] **Step 2: Add frontend dependencies**

Run from `frontend/` so the lockfile stays local:

```bash
cd frontend && npm install @tanstack/react-table react-hook-form
```

Expected:

```text
added ... packages
```

The exact package count can vary because `react-hook-form` may already be present as a transitive dependency.

- [ ] **Step 3: Verify package metadata**

Run:

```bash
cd frontend && node -e "const pkg=require('./package.json'); if(!pkg.dependencies['@tanstack/react-table']) throw new Error('missing table'); if(!pkg.dependencies['react-hook-form']) throw new Error('missing rhf'); console.log('admin deps ok')"
```

Expected:

```text
admin deps ok
```

- [ ] **Step 4: Run frontend typecheck**

Run:

```bash
cd frontend && npm run lint
```

Expected:

```text
tsc --noEmit
```

and exit code `0`.

- [ ] **Step 5: Commit dependency baseline**

Only in a clean execution branch or worktree:

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: add admin table and form dependencies"
```

## Task 2: Admin Shell and Route Taxonomy

**Files:**
- Create: `frontend/src/components/admin/adminNavigation.ts`
- Create: `frontend/src/components/admin/AdminShell.tsx`
- Modify: `frontend/src/components/AdminLayout.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/pages/admin/AdminGroup.tsx`
- Modify: `frontend/src/pages/admin/AdminNode.tsx`
- Modify: `frontend/src/pages/admin/AdminOAuth.tsx`

- [ ] **Step 1: Create navigation model**

Create `frontend/src/components/admin/adminNavigation.ts` with the route model below:

```ts
import {
  Activity,
  Database,
  FileKey,
  FolderKey,
  ListChecks,
  Settings,
  Share2,
  Shield,
  Users,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

export type AdminPermissionCode =
  | 'admin.overview.read'
  | 'admin.users.read'
  | 'admin.users.write'
  | 'admin.settings.read'
  | 'admin.settings.write'
  | 'admin.storage.read'
  | 'admin.storage.write'
  | 'admin.files.read'
  | 'admin.files.write'
  | 'admin.shares.read'
  | 'admin.shares.write'
  | 'admin.tasks.read'
  | 'admin.audit.read'
  | 'admin.system.read';

export type AdminNavItem = {
  label: string;
  path: string;
  icon: LucideIcon;
  permission: AdminPermissionCode;
};

export type AdminNavGroup = {
  label: string;
  items: AdminNavItem[];
};

export const adminNavGroups: AdminNavGroup[] = [
  {
    label: '总览',
    items: [
      { label: '运行总览', path: '/admin/home', icon: Activity, permission: 'admin.overview.read' },
      { label: '系统状态', path: '/admin/system', icon: Shield, permission: 'admin.system.read' },
    ],
  },
  {
    label: '身份与权限',
    items: [
      { label: '用户管理', path: '/admin/users', icon: Users, permission: 'admin.users.read' },
    ],
  },
  {
    label: '配置与存储',
    items: [
      { label: '配置中心', path: '/admin/config', icon: Settings, permission: 'admin.settings.read' },
      { label: '存储策略', path: '/admin/storage-policies', icon: Database, permission: 'admin.storage.read' },
    ],
  },
  {
    label: '资源治理',
    items: [
      { label: '文件治理', path: '/admin/files', icon: FileKey, permission: 'admin.files.read' },
      { label: '内容实体', path: '/admin/file-blobs', icon: FolderKey, permission: 'admin.files.read' },
      { label: '分享治理', path: '/admin/shares', icon: Share2, permission: 'admin.shares.read' },
    ],
  },
  {
    label: '任务与审计',
    items: [
      { label: '任务中心', path: '/admin/tasks', icon: ListChecks, permission: 'admin.tasks.read' },
      { label: '审计日志', path: '/admin/audits', icon: Shield, permission: 'admin.audit.read' },
    ],
  },
];
```

- [ ] **Step 2: Implement `AdminShell`**

Create `frontend/src/components/admin/AdminShell.tsx` as the replacement shell. It must:

- render grouped navigation from `adminNavGroups`;
- keep the mobile drawer behavior currently in `AdminLayout`;
- render `Topbar`;
- include a return link to `/dashboard/files`;
- avoid marketing-style hero/card nesting;
- keep content in a dense admin surface.

- [ ] **Step 3: Keep `AdminLayout` as a compatibility wrapper**

Modify `frontend/src/components/AdminLayout.tsx` so existing pages can continue to call it:

```tsx
import React from 'react';
import AdminShell from './admin/AdminShell';

interface AdminLayoutProps {
  children: React.ReactNode;
  title: string;
}

const AdminLayout: React.FC<AdminLayoutProps> = ({ children, title }) => {
  return <AdminShell title={title}>{children}</AdminShell>;
};

export default AdminLayout;
```

- [ ] **Step 4: Update route aliases in `App.tsx`**

Keep old paths temporarily as redirects and introduce real paths:

```tsx
<Route path="/admin">
  <Route index element={<Navigate to="home" replace />} />
  <Route path="home" element={<RequireAdmin><AdminHome /></RequireAdmin>} />
  <Route path="system" element={<RequireAdmin><AdminFileSystem /></RequireAdmin>} />
  <Route path="config" element={<RequireAdmin><AdminSetting /></RequireAdmin>} />
  <Route path="settings" element={<Navigate to="/admin/config" replace />} />
  <Route path="users" element={<RequireAdmin><AdminUser /></RequireAdmin>} />
  <Route path="user" element={<Navigate to="/admin/users" replace />} />
  <Route path="storage-policies" element={<RequireAdmin><AdminPolicy /></RequireAdmin>} />
  <Route path="policy" element={<Navigate to="/admin/storage-policies" replace />} />
  <Route path="files" element={<RequireAdmin><AdminFile /></RequireAdmin>} />
  <Route path="file" element={<Navigate to="/admin/files" replace />} />
  <Route path="file-blobs" element={<RequireAdmin><AdminBlob /></RequireAdmin>} />
  <Route path="blob" element={<Navigate to="/admin/file-blobs" replace />} />
  <Route path="tasks" element={<RequireAdmin><AdminTask /></RequireAdmin>} />
  <Route path="task" element={<Navigate to="/admin/tasks" replace />} />
  <Route path="shares" element={<RequireAdmin><AdminShare /></RequireAdmin>} />
  <Route path="share" element={<Navigate to="/admin/shares" replace />} />
  <Route path="filesystem" element={<Navigate to="/admin/system" replace />} />
  <Route path="group" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
  <Route path="node" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
  <Route path="oauth" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
  <Route path="*" element={<RequireAdmin><AdminUnderConstruction /></RequireAdmin>} />
</Route>
```

- [ ] **Step 5: Run frontend typecheck**

Run:

```bash
cd frontend && npm run lint
```

Expected: exit code `0`.

- [ ] **Step 6: Commit shell and route taxonomy**

```bash
git add frontend/src/App.tsx frontend/src/components/AdminLayout.tsx frontend/src/components/admin
git commit -m "feat: add admin shell route taxonomy"
```

## Task 3: Permission Baseline Before New Admin Writes

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminPermissionCode.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminPermissionResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminPermissionQueryApi.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/RuntimeAdminPermissionQueryApi.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminPermissionController.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/RuntimeAdminPermissionQueryApiTest.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/web/AdminPermissionControllerTest.java`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/queries.ts`
- Modify: `frontend/src/lib/session.ts`

- [ ] **Step 1: Add backend permission enum**

Create `AdminPermissionCode` in `ops.admin.api`:

```java
package com.yoyuzh.ops.admin.api;

public enum AdminPermissionCode {
    ADMIN_OVERVIEW_READ("admin.overview.read"),
    ADMIN_USERS_READ("admin.users.read"),
    ADMIN_USERS_WRITE("admin.users.write"),
    ADMIN_SETTINGS_READ("admin.settings.read"),
    ADMIN_SETTINGS_WRITE("admin.settings.write"),
    ADMIN_STORAGE_READ("admin.storage.read"),
    ADMIN_STORAGE_WRITE("admin.storage.write"),
    ADMIN_FILES_READ("admin.files.read"),
    ADMIN_FILES_WRITE("admin.files.write"),
    ADMIN_SHARES_READ("admin.shares.read"),
    ADMIN_SHARES_WRITE("admin.shares.write"),
    ADMIN_TASKS_READ("admin.tasks.read"),
    ADMIN_AUDIT_READ("admin.audit.read"),
    ADMIN_SYSTEM_READ("admin.system.read");

    private final String code;

    AdminPermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
```

- [ ] **Step 2: Add permission response and API seam**

Create `AdminPermissionResponse`:

```java
package com.yoyuzh.ops.admin.api;

import java.util.List;

public record AdminPermissionResponse(List<String> permissions) {
}
```

Create `AdminPermissionQueryApi`:

```java
package com.yoyuzh.ops.admin.api;

import org.springframework.security.core.Authentication;

public interface AdminPermissionQueryApi {
    AdminPermissionResponse currentPermissions(Authentication authentication);
}
```

- [ ] **Step 3: Implement coarse permission mapping**

Create `RuntimeAdminPermissionQueryApi`:

```java
package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.AdminAccessPolicy;
import com.yoyuzh.ops.admin.api.AdminPermissionCode;
import com.yoyuzh.ops.admin.api.AdminPermissionQueryApi;
import com.yoyuzh.ops.admin.api.AdminPermissionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class RuntimeAdminPermissionQueryApi implements AdminPermissionQueryApi {

    private final AdminAccessPolicy adminAccessPolicy;

    public RuntimeAdminPermissionQueryApi(AdminAccessPolicy adminAccessPolicy) {
        this.adminAccessPolicy = adminAccessPolicy;
    }

    @Override
    public AdminPermissionResponse currentPermissions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !adminAccessPolicy.hasAdminAccess(authentication)) {
            return new AdminPermissionResponse(java.util.List.of());
        }
        return new AdminPermissionResponse(Arrays.stream(AdminPermissionCode.values())
                .map(AdminPermissionCode::code)
                .toList());
    }
}
```

- [ ] **Step 4: Add controller**

Create `AdminPermissionController`:

```java
package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.api.AdminPermissionQueryApi;
import com.yoyuzh.ops.admin.api.AdminPermissionResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminPermissionQueryApi adminPermissionQueryApi;

    @GetMapping("/permissions")
    public ApiResponse<AdminPermissionResponse> permissions(Authentication authentication) {
        return ApiResponse.success(adminPermissionQueryApi.currentPermissions(authentication));
    }
}
```

- [ ] **Step 5: Add backend tests**

`RuntimeAdminPermissionQueryApiTest` should verify:

- unauthenticated authentication returns empty permissions;
- authenticated non-admin returns empty permissions;
- admin-capable authentication returns all defined permission codes.

`AdminPermissionControllerTest` should verify:

- `GET /api/admin/permissions` is under admin auth boundary;
- response shape contains `permissions`.

- [ ] **Step 6: Add frontend permission DTO/hook**

Add to `frontend/src/api/types.ts`:

```ts
export interface AdminPermissionResponse {
  permissions: string[];
}
```

Add to `frontend/src/api/queries.ts`:

```ts
export const useAdminPermissions = () =>
  useQuery({
    queryKey: ['adminPermissions'],
    queryFn: () =>
      apiRequest<AdminPermissionResponse>({
        url: '/admin/permissions',
        method: 'GET',
      }),
  });
```

- [ ] **Step 7: Run backend and frontend verification**

Run:

```bash
cd backend && mvn test
```

Expected: exit code `0`.

Run:

```bash
cd frontend && npm run lint
```

Expected: exit code `0`.

- [ ] **Step 8: Commit permission baseline**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin frontend/src/api frontend/src/lib/session.ts backend/src/test/java/com/yoyuzh/ops/admin
git commit -m "feat: add admin permission baseline"
```

## Task 4: Audit Write Discipline Before Storage and Config Writes

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminAuditAction.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminUserGovernanceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminResourceGovernanceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/RuntimeAdminSettingsGovernanceApi.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminAuditServiceTest.java`

- [ ] **Step 1: Extend audit action names**

Add action constants for all current and planned admin writes:

```java
USER_ROLE_UPDATED,
USER_STATUS_UPDATED,
USER_PASSWORD_UPDATED,
USER_PASSWORD_RESET,
USER_STORAGE_QUOTA_UPDATED,
USER_MAX_UPLOAD_SIZE_UPDATED,
FILE_DELETED,
SHARE_DELETED,
SETTINGS_UPDATED,
INVITE_CODE_UPDATED,
INVITE_CODE_ROTATED,
OFFLINE_TRANSFER_LIMIT_UPDATED,
STORAGE_POLICY_CREATED,
STORAGE_POLICY_UPDATED,
STORAGE_POLICY_STATUS_UPDATED,
STORAGE_POLICY_MIGRATION_REQUESTED,
CONFIG_VALUE_UPDATED,
CONFIG_VALUE_ROLLED_BACK
```

- [ ] **Step 2: Add audit calls to existing write services**

For each existing admin write method, call:

```java
adminAuditService.record(
        AdminAuditAction.SETTINGS_UPDATED,
        "ADMIN_SETTINGS",
        null,
        "Updated admin settings",
        Map.of("changedSection", "registration")
);
```

Use action-specific values, target types, and target IDs:

- user writes: target type `USER`, target ID `userId`;
- file writes: target type `FILE`, target ID `fileId`;
- share writes: target type `SHARE`, target ID `shareId`;
- storage policy writes: target type `STORAGE_POLICY`, target ID `policyId`;
- settings writes: target type `ADMIN_SETTINGS`, target ID `null`.

- [ ] **Step 3: Write audit service unit tests**

Test `AdminAuditService.record(...)` stores:

- actor ID when principal is `AuthenticatedUserPrincipal`;
- action type;
- target type and ID;
- details JSON.

- [ ] **Step 4: Add service-level tests for representative write actions**

Use existing admin service tests where present. If no matching tests exist, add focused tests for:

- `updateUserRole(...)` records `USER_ROLE_UPDATED`;
- `deleteShare(...)` records `SHARE_DELETED`;
- storage policy status update records `STORAGE_POLICY_STATUS_UPDATED`.

- [ ] **Step 5: Run backend verification**

Run:

```bash
cd backend && mvn test
```

Expected: exit code `0`.

- [ ] **Step 6: Commit audit baseline**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin backend/src/test/java/com/yoyuzh/ops/admin
git commit -m "feat: audit admin governance writes"
```

## Task 5: Shared Admin Frontend Primitives

**Files:**
- Create: `frontend/src/components/admin/AdminPage.tsx`
- Create: `frontend/src/components/admin/AdminDataTable.tsx`
- Create: `frontend/src/components/admin/AdminFilterBar.tsx`
- Create: `frontend/src/components/admin/AdminConfirmDialog.tsx`
- Create: `frontend/src/components/admin/AdminStatusBadge.tsx`
- Create: `frontend/src/components/admin/AdminSchemaForm.tsx`
- Create: `frontend/src/components/admin/adminSchemaTypes.ts`

- [ ] **Step 1: Create `AdminPage`**

The component props must be:

```ts
type AdminPageProps = {
  title: string;
  description?: string;
  toolbar?: React.ReactNode;
  isLoading?: boolean;
  isError?: boolean;
  errorText?: string;
  children: React.ReactNode;
};
```

Behavior:

- show loading state when `isLoading`;
- show error state when `isError`;
- otherwise render content.

- [ ] **Step 2: Create `AdminDataTable`**

The component must wrap TanStack Table with this public surface:

```ts
export type AdminColumn<T> = {
  id: string;
  header: string;
  accessor: (row: T) => React.ReactNode;
  className?: string;
};

export type AdminDataTableProps<T> = {
  rows: T[];
  columns: AdminColumn<T>[];
  getRowKey: (row: T) => string | number;
  emptyText?: string;
};
```

The implementation should keep table UI dense, use stable row keys, and avoid each page reimplementing empty states.

- [ ] **Step 3: Create `AdminConfirmDialog`**

The component props must be:

```ts
type AdminConfirmDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  danger?: boolean;
  isSubmitting?: boolean;
  onConfirm: () => void;
  onClose: () => void;
};
```

- [ ] **Step 4: Create schema form types**

Create `adminSchemaTypes.ts`:

```ts
export type AdminConfigFieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'select'
  | 'multi_select'
  | 'password'
  | 'textarea'
  | 'json'
  | 'url'
  | 'path'
  | 'size'
  | 'duration'
  | 'cron';

export type AdminConfigOption = {
  label: string;
  value: string;
};

export type AdminConfigField = {
  key: string;
  group: string;
  subgroup?: string | null;
  title: string;
  description?: string | null;
  type: AdminConfigFieldType;
  defaultValue?: unknown;
  value?: unknown;
  options?: AdminConfigOption[];
  required: boolean;
  editable: boolean;
  sensitive: boolean;
  restartRequired: boolean;
  validationRules?: Record<string, unknown>;
  permissionCode?: string | null;
  source: 'runtime' | 'environment' | 'database' | 'computed';
};
```

- [ ] **Step 5: Create `AdminSchemaForm`**

Use `react-hook-form` and MUI `Controller` internally. The public props:

```ts
type AdminSchemaFormProps = {
  fields: AdminConfigField[];
  readOnly?: boolean;
  onSubmit?: (values: Record<string, unknown>) => void;
};
```

First version supports `string`, `number`, `boolean`, `select`, and `textarea`. Other types render read-only with a clear unsupported field state.

- [ ] **Step 6: Run frontend verification**

Run:

```bash
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: both exit code `0`.

- [ ] **Step 7: Commit admin primitives**

```bash
git add frontend/src/components/admin
git commit -m "feat: add shared admin primitives"
```

## Task 6: Migrate Existing Admin Pages to the New Shell and Primitives

**Files:**
- Modify: `frontend/src/pages/admin/AdminHome.tsx`
- Modify: `frontend/src/pages/admin/AdminUser.tsx`
- Modify: `frontend/src/pages/admin/AdminPolicy.tsx`
- Modify: `frontend/src/pages/admin/AdminFile.tsx`
- Modify: `frontend/src/pages/admin/AdminBlob.tsx`
- Modify: `frontend/src/pages/admin/AdminTask.tsx`
- Modify: `frontend/src/pages/admin/AdminShare.tsx`
- Modify: `frontend/src/pages/admin/AdminFileSystem.tsx`

- [ ] **Step 1: Replace ad hoc loading/error states with `AdminPage`**

Each page should wrap its content with:

```tsx
<AdminLayout title="...">
  <AdminPage
    title="..."
    description="..."
    isLoading={isLoading}
    isError={isError}
    errorText="加载失败"
  >
    ...
  </AdminPage>
</AdminLayout>
```

- [ ] **Step 2: Replace tables with `AdminDataTable`**

For each list page, define columns near the page component:

```tsx
const columns: AdminColumn<AdminUser>[]= [
  { id: 'username', header: '用户', accessor: (user) => user.username },
  { id: 'role', header: '角色', accessor: (user) => <AdminStatusBadge label={user.role} /> },
];
```

Then render:

```tsx
<AdminDataTable rows={data?.items ?? []} columns={columns} getRowKey={(row) => row.id} />
```

- [ ] **Step 3: Replace destructive browser confirms with `AdminConfirmDialog`**

Any page currently using `window.confirm(...)` or custom inline confirm state should use `AdminConfirmDialog`.

- [ ] **Step 4: Remove unsupported page links from visible navigation**

`AdminGroup`, `AdminNode`, and `AdminOAuth` remain route-compatible, but not visible in navigation until backend endpoints exist.

- [ ] **Step 5: Run frontend verification**

Run:

```bash
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: both exit code `0`.

- [ ] **Step 6: Commit page migration batch**

```bash
git add frontend/src/pages/admin frontend/src/components/admin frontend/src/components/AdminLayout.tsx frontend/src/App.tsx
git commit -m "refactor: migrate admin pages to shared primitives"
```

## Task 7: Read-Only Config Schema Backbone

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigDefinitionResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigSnapshotResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigSchemaApi.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/AdminConfigDefinition.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/AdminConfigRegistry.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/RuntimeAdminConfigSchemaApi.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminConfigController.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/config/AdminConfigRegistryTest.java`
- Create: `backend/src/test/java/com/yoyuzh/ops/admin/internal/web/AdminConfigControllerTest.java`

- [ ] **Step 1: Define response DTOs in `ops.admin.api`**

`AdminConfigDefinitionResponse` should include:

```java
String key,
String group,
String subgroup,
String title,
String description,
String type,
Object defaultValue,
Object value,
java.util.List<Option> options,
boolean required,
boolean editable,
boolean sensitive,
boolean restartRequired,
java.util.Map<String, Object> validationRules,
String permissionCode,
String source
```

`Option` is a nested record:

```java
public record Option(String label, String value) {
}
```

- [ ] **Step 2: Implement code registry**

Register only safe first-pass config groups:

- `registration.inviteCodeRequired`
- `registration.currentInviteCode`
- `registration.managementRoles`
- `transfer.offlineTransferStorageLimitBytes`
- `media.metadataExtractionEnabled`
- `media.thumbnailGenerationEnabled`
- `media.videoPosterEnabled`
- `queue.backend`
- `queue.mediaMetadataFixedDelayMs`
- `queue.mediaMetadataInitialDelayMs`
- `server.storageProvider`
- `server.redisEnabled`

Fields backed by existing write endpoints may be marked `editable=true`; environment-backed and computed fields remain `editable=false`.

- [ ] **Step 3: Add read-only controller**

`AdminConfigController` exposes:

```java
@GetMapping("/config/definitions")
public ApiResponse<List<AdminConfigDefinitionResponse>> definitions()

@GetMapping("/config/snapshot")
public ApiResponse<AdminConfigSnapshotResponse> snapshot()
```

No generic write endpoint is allowed in this task.

- [ ] **Step 4: Test registry and controller**

Tests must assert:

- each key is unique;
- no sensitive startup secret is registered;
- `/api/admin/config/definitions` returns the expected registration and server keys;
- `/api/admin/config/snapshot` returns current values from existing settings/runtime services.

- [ ] **Step 5: Run backend verification**

Run:

```bash
cd backend && mvn test
```

Expected: exit code `0`.

- [ ] **Step 6: Commit schema backbone**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin backend/src/test/java/com/yoyuzh/ops/admin
git commit -m "feat: add read-only admin config schema"
```

## Task 8: Schema-Driven Settings UI

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/queries.ts`
- Modify: `frontend/src/pages/admin/AdminSetting.tsx`
- Create: `frontend/src/pages/admin/AdminConfig.tsx` if the settings page is split from legacy compatibility.

- [ ] **Step 1: Add config DTOs and query hooks**

Add DTOs matching backend schema:

```ts
export type AdminConfigSource = 'runtime' | 'environment' | 'database' | 'computed';

export interface AdminConfigDefinition {
  key: string;
  group: string;
  subgroup: string | null;
  title: string;
  description: string | null;
  type: AdminConfigFieldType;
  defaultValue: unknown;
  value: unknown;
  options: AdminConfigOption[];
  required: boolean;
  editable: boolean;
  sensitive: boolean;
  restartRequired: boolean;
  validationRules: Record<string, unknown>;
  permissionCode: string | null;
  source: AdminConfigSource;
}

export interface AdminConfigSnapshot {
  fields: AdminConfigDefinition[];
}
```

Add hooks:

```ts
export const useAdminConfigDefinitions = () =>
  useQuery({
    queryKey: ['adminConfigDefinitions'],
    queryFn: () =>
      apiRequest<AdminConfigDefinition[]>({
        url: '/admin/config/definitions',
        method: 'GET',
      }),
  });

export const useAdminConfigSnapshot = () =>
  useQuery({
    queryKey: ['adminConfigSnapshot'],
    queryFn: () =>
      apiRequest<AdminConfigSnapshot>({
        url: '/admin/config/snapshot',
        method: 'GET',
      }),
  });
```

- [ ] **Step 2: Render schema fields in settings page**

`AdminSetting.tsx` should:

- fetch config snapshot;
- group fields by `group`;
- use `AdminSchemaForm`;
- keep existing specific mutation buttons for invite code and offline transfer limit until generic DB-backed config write exists;
- render non-editable fields as read-only.

- [ ] **Step 3: Remove misleading tabs**

Remove or hide unsupported tabs such as captcha, VAS, email, and events unless a real backend config key exists.

- [ ] **Step 4: Run frontend verification**

Run:

```bash
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: both exit code `0`.

- [ ] **Step 5: Commit schema settings UI**

```bash
git add frontend/src/api frontend/src/pages/admin frontend/src/components/admin
git commit -m "feat: render admin settings from schema"
```

## Task 9: DB-Backed Config Values, History, and Rollback

**Files:**
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigValueEntity.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigChangeLogEntity.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigValueRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/internal/infra/config/AdminConfigChangeLogRepository.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigUpdateRequest.java`
- Create: `backend/src/main/java/com/yoyuzh/ops/admin/api/AdminConfigHistoryResponse.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/config/RuntimeAdminConfigSchemaApi.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminConfigController.java`
- Modify: `frontend/src/api/mutations.ts`
- Modify: `frontend/src/pages/admin/AdminSetting.tsx`

- [ ] **Step 1: Add persistence entities**

`AdminConfigValueEntity` fields:

- `id`
- `configKey`
- `valueJson`
- `version`
- `updatedByUserId`
- `updatedByUsername`
- `updatedAt`

`AdminConfigChangeLogEntity` fields:

- `id`
- `configKey`
- `beforeValueJson`
- `afterValueJson`
- `version`
- `reason`
- `actorUserId`
- `actorUsername`
- `createdAt`

- [ ] **Step 2: Add generic update endpoint only for registered editable DB-backed keys**

Endpoint:

```java
@PatchMapping("/config/values/{key}")
public ApiResponse<AdminConfigDefinitionResponse> updateValue(
        @PathVariable String key,
        @Valid @RequestBody AdminConfigUpdateRequest request)
```

Rules:

- reject unregistered keys;
- reject `editable=false`;
- reject source `environment`;
- validate field type before persisting;
- record `CONFIG_VALUE_UPDATED` audit action.

- [ ] **Step 3: Add history and rollback endpoints**

Endpoints:

```java
@GetMapping("/config/values/{key}/history")
public ApiResponse<PageResponse<AdminConfigHistoryResponse>> history(...)

@PostMapping("/config/values/{key}/rollback/{version}")
public ApiResponse<AdminConfigDefinitionResponse> rollback(...)
```

Rollback records `CONFIG_VALUE_ROLLED_BACK`.

- [ ] **Step 4: Backend tests**

Tests must cover:

- updating a valid editable DB-backed key;
- rejecting environment keys;
- rejecting unknown keys;
- writing history;
- rollback restoring a prior value;
- audit record written for update and rollback.

- [ ] **Step 5: Frontend update and history UI**

Add:

- save button for DB-backed editable fields;
- history drawer for fields with history;
- rollback confirm dialog.

- [ ] **Step 6: Run verification**

Run:

```bash
cd backend && mvn test
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: all exit code `0`.

- [ ] **Step 7: Commit DB config governance**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin backend/src/test/java/com/yoyuzh/ops/admin frontend/src/api frontend/src/pages/admin frontend/src/components/admin
git commit -m "feat: add admin config history and rollback"
```

## Task 10: Storage and Upload Governance Modernization

**Files:**
- Modify: `frontend/src/pages/admin/AdminPolicy.tsx`
- Modify: `frontend/src/pages/admin/AdminFileSystem.tsx`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/queries.ts`
- Modify: `frontend/src/api/mutations.ts`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceService.java`
- Modify: `backend/src/main/java/com/yoyuzh/ops/admin/internal/web/AdminStoragePolicyController.java`
- Modify: `backend/src/test/java/com/yoyuzh/ops/admin/internal/application/AdminStorageGovernanceServiceTest.java` if present, otherwise create it.

- [ ] **Step 1: Rename frontend mental model**

Use UI copy:

- `存储策略`
- `上传能力`
- `迁移任务`
- `系统状态`

Do not reintroduce Cloudreve terms `节点` or `OAuth policy`.

- [ ] **Step 2: Expose capabilities clearly**

For each policy render:

- `PROXY` / `DIRECT_SINGLE` / `DIRECT_MULTIPART` capability;
- max object size;
- CORS requirement;
- signed download support;
- default policy state;
- enabled state.

- [ ] **Step 3: Enforce audited writes**

Every create/update/status/migration request must call `AdminAuditService.record(...)`.

- [ ] **Step 4: Add tests for storage audit**

Tests must assert that:

- create policy writes audit;
- update policy writes audit;
- status update writes audit;
- migration request writes audit.

- [ ] **Step 5: Run verification**

Run:

```bash
cd backend && mvn test
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: all exit code `0`.

- [ ] **Step 6: Commit storage governance modernization**

```bash
git add backend/src/main/java/com/yoyuzh/ops/admin backend/src/test/java/com/yoyuzh/ops/admin frontend/src/api frontend/src/pages/admin
git commit -m "feat: modernize admin storage governance"
```

## Task 11: Audit Center UI

**Files:**
- Create: `frontend/src/pages/admin/AdminAudit.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/queries.ts`
- Modify: `frontend/src/components/admin/adminNavigation.ts`

- [ ] **Step 1: Add audit DTOs**

Add to `frontend/src/api/types.ts`:

```ts
export interface AdminAuditLog {
  id: number;
  actorUserId: number | null;
  actorUsername: string;
  actorAuthorities: string;
  actionType: string;
  targetType: string;
  targetId: number | null;
  summary: string;
  detailsJson: string;
  createdAt: string;
}
```

- [ ] **Step 2: Add audit query hook**

Add:

```ts
export const useAdminAudits = (params: AdminListParams & {
  actorQuery?: string;
  actionType?: string;
  targetType?: string;
  targetId?: number;
}) =>
  useQuery({
    queryKey: ['adminAudits', params],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminAuditLog>>({
        url: '/admin/audits',
        method: 'GET',
        params: {
          page: toBackendPage(params),
          size: params.page_size,
          actorQuery: params.actorQuery ?? '',
          actionType: params.actionType ?? '',
          targetType: params.targetType ?? '',
          targetId: params.targetId,
        },
      });
      return normalizePage(result);
    },
    placeholderData: (previousData) => previousData,
  });
```

- [ ] **Step 3: Build `AdminAudit` page**

Use:

- `AdminPage`
- `AdminFilterBar`
- `AdminDataTable`
- detail drawer or modal for parsed `detailsJson`.

- [ ] **Step 4: Add route**

Add:

```tsx
<Route path="audits" element={<RequireAdmin><AdminAudit /></RequireAdmin>} />
```

- [ ] **Step 5: Run frontend verification**

Run:

```bash
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: both exit code `0`.

- [ ] **Step 6: Commit audit center UI**

```bash
git add frontend/src/App.tsx frontend/src/api frontend/src/components/admin frontend/src/pages/admin/AdminAudit.tsx
git commit -m "feat: add admin audit center"
```

## Task 12: Final Verification and Handoff

**Files:**
- Modify: `docs/superpowers/plans/2026-05-09-admin-panel-modernization.md` only if execution notes need updating.

- [ ] **Step 1: Run backend verification**

Run:

```bash
cd backend && mvn test
```

Expected: exit code `0`.

- [ ] **Step 2: Run frontend verification**

Run:

```bash
cd frontend && npm run lint
cd frontend && npm run build
```

Expected: both exit code `0`.

- [ ] **Step 3: Manual admin smoke**

Start backend and frontend only if the execution task includes local runtime verification:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm run dev
```

Manual checks:

- `/admin/home` renders grouped admin shell.
- `/admin/users` renders table.
- `/admin/config` renders schema-driven settings.
- `/admin/storage-policies` renders capabilities.
- `/admin/audits` renders audit list.
- `/admin/group`, `/admin/node`, and `/admin/oauth` are not visible in navigation.
- non-admin users are redirected away from `/admin`.

- [ ] **Step 4: Review architecture guardrails**

Search for forbidden imports:

```bash
rg -n "import com\\.yoyuzh\\.(identity|files|transfer|platform)\\..*\\.internal" backend/src/main/java/com/yoyuzh/ops/admin
```

Expected: no matches.

- [ ] **Step 5: Final commit**

```bash
git status --short
git add docs/superpowers/plans/2026-05-09-admin-panel-modernization.md
git commit -m "docs: plan admin panel modernization"
```

## Self-Review

Spec coverage:

- The plan keeps the native admin approach and rejects heavy admin frameworks.
- The plan fixes the sequence from the Claude review: permissions and audit write discipline come before config/storage write expansion.
- The plan splits config into read-only schema first, DB values/history/rollback later.
- The plan preserves `ops.admin` as a governance entrypoint and calls out forbidden `internal` imports.
- The plan uses the active `frontend/` app, not legacy `front/`.
- The plan uses actual repo verification commands.

Placeholder scan:

- No incomplete marker words are used as implementation steps.
- Later-phase files are explicitly listed and marked as later ownership, not hidden placeholders.

Type consistency:

- Frontend permission strings match the backend enum `code()` values.
- Config schema field names are consistent between Java response DTOs and TypeScript DTOs.
- Audit action names are centralized in `AdminAuditAction`.

Plan complete and saved to `docs/superpowers/plans/2026-05-09-admin-panel-modernization.md`.
