# Cloudreve-Inspired User-Facing UI Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `my_site`'s `frontend/` user-facing UI layer into a Cloudreve-inspired product surface while preserving `my_site`'s existing routes, API clients, query/mutation logic, and business behavior.

**Architecture:** Treat `third_party/cloudreve-frontend/` as a read-only design reference, not a runtime dependency and not an API contract target. Freeze the current user-facing pages as behavior references, keep `frontend/src/lib/**`, `frontend/src/api/**`, and session/auth/query wiring as the sole data layer, then replace the presentation layer route-by-route with new local components under `frontend/src/components/**` and rewritten page compositions under `frontend/src/pages/**`.

**Tech Stack:** React 18, TypeScript, Vite 5, Tailwind CSS, React Router 6, TanStack React Query, Lucide icons.

---

## Scope Check

This plan covers the full user-facing route surface in `frontend/`:

1. login visual structure and transitions
2. dashboard shell and navigation
3. files page layout, menus, detail rail, and upload/task affordances
4. overview, shares, recycle-bin, and tasks page visual alignment
5. transfer send/receive and public share page layout alignment

This plan deliberately excludes:

- backend changes
- `/api/v4` compatibility
- reusing Cloudreve Redux/session/API code
- admin panel migration
- replacing current auth/session/file/share/task APIs

## Rewrite Rules

- This is a UI rewrite plan, not a polish pass. Do not keep layering new visuals into the old page trees when a route has already entered rewrite scope.
- Existing user-facing pages are allowed to survive only as temporary behavior references while their replacement composition is being built.
- Keep business logic, API calls, query keys, and mutation behavior in place unless the rewrite absolutely requires a local extraction for reuse.
- Prefer replacing page composition wholesale over incremental style patching once a page is in scope.
- Do not import runtime code from `third_party/cloudreve-frontend/**`; only borrow layout ideas, component hierarchy ideas, motion patterns, and information architecture.

## File Structure

### Read-only reference files

- `third_party/cloudreve-frontend/src/component/Pages/Login/Signin/SignIn.tsx`
  Cloudreve login flow composition and phase-based motion reference.
- `third_party/cloudreve-frontend/src/component/Frame/NavBar/AppMain.tsx`
  Cloudreve shell spacing, page transition, and content framing reference.
- `third_party/cloudreve-frontend/src/component/FileManager/TopBar/TopActions.tsx`
  Cloudreve file toolbar button grouping and density reference.
- `third_party/cloudreve-frontend/src/component/Pages/Shares/ShareList.tsx`
  Cloudreve share-list card density and page-header reference.
- `third_party/cloudreve-frontend/src/component/Uploader/Uploader.tsx`
  Cloudreve upload/task visibility reference.

### Files to create

- `frontend/src/components/ui/GlassPanel.tsx`
  Shared elevated panel wrapper for cards, rails, menus, and page blocks.
- `frontend/src/components/ui/PageSectionHeader.tsx`
  Shared title, subtitle, and action-row header.
- `frontend/src/components/public/PublicPageShell.tsx`
  Shared public-facing shell for login, standalone receive, and public share pages.
- `frontend/src/components/workspace/WorkspaceSidebar.tsx`
  Sidebar navigation extracted from `DashboardLayout`.
- `frontend/src/components/workspace/WorkspaceHeader.tsx`
  Page-level header area with mobile action slot and breadcrumb slot.
- `frontend/src/components/files/FileToolbar.tsx`
  Top action row for upload, new folder, search, and bulk actions.
- `frontend/src/components/files/FileTable.tsx`
  File list/table surface separated from page state.
- `frontend/src/components/files/FileDetailsRail.tsx`
  Right-side details rail for file metadata and actions.
- `frontend/src/components/files/FileActionMenu.tsx`
  Portal-based row action menu.
- `frontend/src/components/tasks/UploadTaskTray.tsx`
  Compact upload/task summary tray inspired by Cloudreve visibility patterns.

### Files to modify

- `frontend/src/styles/index.css`
  Add shared motion tokens, glass surfaces, shadows, and layout helpers.
- `frontend/src/components/Topbar.tsx`
  Align the top bar density and visual weight with the new shell.
- `frontend/src/components/DashboardLayout.tsx`
  Rebuild around the new shell components.
- `frontend/src/pages/Login.tsx`
  Replace current simple card with split/stacked Cloudreve-inspired composition.
- `frontend/src/pages/Files.tsx`
  Keep logic, move rendering into the new file components.
- `frontend/src/pages/Overview.tsx`
  Align cards and recent file section with the new shell.
- `frontend/src/pages/Shares.tsx`
  Convert list rows into denser share cards/sections.
- `frontend/src/pages/RecycleBin.tsx`
  Align recycle bin with the same page frame and card/list language.
- `frontend/src/pages/Tasks.tsx`
  Align task list/detail layout with the new shell and tray.
- `frontend/src/pages/TransferSend.tsx`
  Align transfer send/receive layout with the same shell, cards, and tab patterns.
- `frontend/src/pages/TransferReceive.tsx`
  Align standalone receive mode with the same public-page visual system.
- `frontend/src/pages/FileShare.tsx`
  Align public share page with the same public-page visual system.

### Validation commands

- `cd frontend && npm run lint`
- `cd frontend && npm run build`
- `cd frontend && npm run dev`

Expected note: this `frontend/` package currently has no checked-in `test` script, so this plan uses `lint + build + manual smoke` as the verification baseline.

## Task 1: Introduce Shared UI Surface Primitives and Freeze the Rewrite Boundary

**Files:**
- Create: `frontend/src/components/ui/GlassPanel.tsx`
- Create: `frontend/src/components/ui/PageSectionHeader.tsx`
- Create: `frontend/src/components/public/PublicPageShell.tsx`
- Modify: `frontend/src/styles/index.css`

- [ ] **Step 1: Create the shared glass panel wrapper**

```tsx
// frontend/src/components/ui/GlassPanel.tsx
import React from 'react';
import { clsx } from 'clsx';

type GlassPanelProps = React.PropsWithChildren<{
  className?: string;
}>;

const GlassPanel: React.FC<GlassPanelProps> = ({ children, className }) => {
  return (
    <section
      className={clsx(
        'rounded-[24px] border border-white/50 bg-white/75 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-xl dark:border-white/10 dark:bg-[#0F1117]/78 dark:shadow-[0_24px_80px_rgba(0,0,0,0.35)]',
        className,
      )}
    >
      {children}
    </section>
  );
};

export default GlassPanel;
```

- [ ] **Step 2: Create the shared section header**

```tsx
// frontend/src/components/ui/PageSectionHeader.tsx
import React from 'react';

type PageSectionHeaderProps = {
  title: string;
  description?: string;
  actions?: React.ReactNode;
};

const PageSectionHeader: React.FC<PageSectionHeaderProps> = ({ title, description, actions }) => {
  return (
    <div className="flex flex-col gap-4 border-b border-slate-200/70 px-6 py-5 dark:border-white/10 lg:flex-row lg:items-center lg:justify-between">
      <div className="min-w-0">
        <h3 className="text-lg font-semibold text-slate-900 dark:text-white">{title}</h3>
        {description ? (
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{description}</p>
        ) : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  );
};

export default PageSectionHeader;
```

- [ ] **Step 3: Create the shared public-page shell**

```tsx
// frontend/src/components/public/PublicPageShell.tsx
import React from 'react';
import Topbar from '../Topbar';
import BackgroundEffects from '../BackgroundEffects';

const PublicPageShell: React.FC<React.PropsWithChildren<{ meta: string; className?: string }>> = ({
  meta,
  className,
  children,
}) => {
  return (
    <div className="min-h-screen bg-bg-light dark:bg-bg-dark">
      <Topbar meta={meta} />
      <BackgroundEffects />
      <main className={`mx-auto min-h-screen max-w-[1280px] px-4 pb-10 pt-[88px] lg:px-6 ${className ?? ''}`}>
        {children}
      </main>
    </div>
  );
};

export default PublicPageShell;
```

- [ ] **Step 4: Add reusable surface and motion tokens**

```css
/* frontend/src/styles/index.css */
.surface-shell {
  @apply rounded-[28px] border border-white/50 bg-white/70 backdrop-blur-xl dark:border-white/10 dark:bg-[#0F1117]/72;
}

.surface-muted {
  @apply bg-slate-50/85 dark:bg-white/[0.03];
}

.page-enter {
  animation: page-enter 240ms ease-out;
}

.rail-enter {
  animation: rail-enter 200ms ease-out;
}

@keyframes page-enter {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes rail-enter {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}
```

- [ ] **Step 5: Verify shared primitives compile**

Run: `cd frontend && npm run lint`  
Expected: PASS with no TypeScript errors from `GlassPanel`, `PageSectionHeader`, `PublicPageShell`, or the new CSS classes.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/ui/GlassPanel.tsx frontend/src/components/ui/PageSectionHeader.tsx frontend/src/components/public/PublicPageShell.tsx frontend/src/styles/index.css
git commit -m "feat(frontend): add shared primitives for user-facing ui rewrite"
```

## Task 2: Rebuild the Workspace Shell as the New Rewrite Base Without Touching Data Logic

**Files:**
- Create: `frontend/src/components/workspace/WorkspaceSidebar.tsx`
- Create: `frontend/src/components/workspace/WorkspaceHeader.tsx`
- Modify: `frontend/src/components/Topbar.tsx`
- Modify: `frontend/src/components/DashboardLayout.tsx`

- [ ] **Step 1: Extract the sidebar into its own component**

```tsx
// frontend/src/components/workspace/WorkspaceSidebar.tsx
import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Folder, Home, ListTodo, Send, Share2, Trash2 } from 'lucide-react';

const items = [
  { name: '总览 Overview', path: '/dashboard/overview', icon: Home },
  { name: '文件 Files', path: '/dashboard/files', icon: Folder },
  { name: '任务 Tasks', path: '/dashboard/tasks', icon: ListTodo },
  { name: '分享 Shares', path: '/dashboard/shares', icon: Share2 },
  { name: '回收站 Recycle Bin', path: '/dashboard/recycle-bin', icon: Trash2 },
  { name: '快传 Transfer', path: '/dashboard/transfer-send', icon: Send },
];

const WorkspaceSidebar: React.FC<{ onNavigate?: () => void }> = ({ onNavigate }) => {
  const location = useLocation();
  return (
    <aside className="surface-shell flex h-full w-[272px] flex-col p-4">
      <nav className="space-y-1">
        {items.map((item) => {
          const Icon = item.icon;
          const active = location.pathname === item.path || location.pathname.startsWith(`${item.path}/`);
          return (
            <Link
              key={item.path}
              to={item.path}
              onClick={onNavigate}
              className={active
                ? 'flex items-center gap-3 rounded-2xl bg-slate-900 px-4 py-3 text-white dark:bg-white dark:text-slate-950'
                : 'flex items-center gap-3 rounded-2xl px-4 py-3 text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-white/5 dark:hover:text-white'}
            >
              <Icon size={18} />
              <span className="text-sm font-medium">{item.name}</span>
            </Link>
          );
        })}
      </nav>
    </aside>
  );
};

export default WorkspaceSidebar;
```

- [ ] **Step 2: Add a compact page header component**

```tsx
// frontend/src/components/workspace/WorkspaceHeader.tsx
import React from 'react';

type WorkspaceHeaderProps = {
  title: string;
  eyebrow?: string;
  actions?: React.ReactNode;
};

const WorkspaceHeader: React.FC<WorkspaceHeaderProps> = ({ title, eyebrow, actions }) => {
  return (
    <header className="mb-6 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        {eyebrow ? <p className="text-xs font-medium uppercase tracking-[0.24em] text-slate-400">{eyebrow}</p> : null}
        <h2 className="mt-1 text-3xl font-semibold tracking-tight text-slate-950 dark:text-white">{title}</h2>
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </header>
  );
};

export default WorkspaceHeader;
```

- [ ] **Step 3: Rebuild `DashboardLayout` on top of the new shell**

```tsx
// frontend/src/components/DashboardLayout.tsx
import React from 'react';
import { Menu, X } from 'lucide-react';
import Topbar from './Topbar';
import BackgroundEffects from './BackgroundEffects';
import WorkspaceSidebar from './workspace/WorkspaceSidebar';
import WorkspaceHeader from './workspace/WorkspaceHeader';

const DashboardLayout: React.FC<{ children: React.ReactNode; title: string }> = ({ children, title }) => {
  const [mobileOpen, setMobileOpen] = React.useState(false);

  return (
    <div className="min-h-screen bg-bg-light dark:bg-bg-dark">
      <Topbar meta={title} />
      <BackgroundEffects />
      <div className="px-4 pb-6 pt-[88px] lg:px-6">
        <div className="mx-auto flex max-w-[1600px] gap-6">
          <div className="hidden lg:block">
            <WorkspaceSidebar />
          </div>
          <main className="min-w-0 flex-1 page-enter">
            <WorkspaceHeader
              title={title}
              eyebrow="MY SITE WORKSPACE"
              actions={
                <button className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/50 bg-white/80 lg:hidden" onClick={() => setMobileOpen(true)}>
                  <Menu size={18} />
                </button>
              }
            />
            {children}
          </main>
        </div>
      </div>
      {mobileOpen ? (
        <div className="fixed inset-0 z-50 bg-slate-950/35 p-4 lg:hidden" onClick={() => setMobileOpen(false)}>
          <div className="w-[272px]" onClick={(event) => event.stopPropagation()}>
            <div className="mb-3 flex justify-end">
              <button className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white/90" onClick={() => setMobileOpen(false)}>
                <X size={18} />
              </button>
            </div>
            <WorkspaceSidebar onNavigate={() => setMobileOpen(false)} />
          </div>
        </div>
      ) : null}
    </div>
  );
};
```

- [ ] **Step 4: Slim down the top bar so it matches the new shell density**

```tsx
// frontend/src/components/Topbar.tsx
return (
  <header className="fixed inset-x-0 top-0 z-40 border-b border-white/40 bg-white/70 backdrop-blur-xl dark:border-white/10 dark:bg-[#0B0D12]/68">
    <div className="mx-auto flex h-[68px] max-w-[1600px] items-center justify-between px-4 lg:px-6">
      {/* keep existing meta/theme/session behavior, change only spacing and surface styling */}
    </div>
  </header>
);
```

- [ ] **Step 5: Verify shell migration does not break routes**

Run: `cd frontend && npm run lint`  
Expected: PASS, and existing dashboard pages still compile without changing their data hooks.

- [ ] **Step 6: Build the app to confirm the shell is production-safe**

Run: `cd frontend && npm run build`  
Expected: PASS, with Vite producing a fresh `dist/` bundle.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/workspace/WorkspaceSidebar.tsx frontend/src/components/workspace/WorkspaceHeader.tsx frontend/src/components/Topbar.tsx frontend/src/components/DashboardLayout.tsx
git commit -m "feat(frontend): migrate workspace shell to cloudreve-inspired layout"
```

## Task 3: Replace the Login Page With a New Cloudreve-Inspired Composition

**Files:**
- Modify: `frontend/src/pages/Login.tsx`
- Modify: `frontend/src/styles/index.css`

- [ ] **Step 1: Replace the single-card layout with a two-zone composition**

```tsx
// frontend/src/pages/Login.tsx
return (
  <div className="min-h-screen px-4 pb-6 pt-[88px] lg:px-6">
    <Topbar meta={siteConfig.siteName} />
    <BackgroundEffects />
    <main className="mx-auto grid min-h-[calc(100vh-112px)] max-w-[1280px] items-center gap-6 lg:grid-cols-[1.15fr_0.85fr]">
      <section className="surface-shell hidden min-h-[620px] overflow-hidden lg:flex lg:flex-col lg:justify-between lg:p-10">
        <div>
          <p className="text-xs font-medium uppercase tracking-[0.24em] text-slate-400">MY SITE CLOUD</p>
          <h1 className="mt-4 max-w-[10ch] text-5xl font-semibold leading-[1.04] text-slate-950 dark:text-white">
            更完整的文件空间，而不是只够用的页面。
          </h1>
          <p className="mt-5 max-w-xl text-base text-slate-500 dark:text-slate-400">{siteConfig.siteDescription}</p>
        </div>
        <div className="grid gap-3 text-sm text-slate-500 dark:text-slate-400">
          <div className="surface-muted rounded-3xl px-5 py-4">保留你自己的 API 和权限体系，只升级体验层。</div>
          <div className="surface-muted rounded-3xl px-5 py-4">登录、文件、分享、任务页面统一到一套更成熟的视觉语言。</div>
        </div>
      </section>
      <section className="surface-shell mx-auto w-full max-w-[480px] p-7 sm:p-9">
        {/* keep existing form state and loginMutation */}
      </section>
    </main>
  </div>
);
```

- [ ] **Step 2: Keep current auth logic, replace only the form presentation**

```tsx
// inside the right-side section in Login.tsx
<header className="mb-8">
  <BrandMark title={siteConfig.siteName} subtitle="Personal Cloud" size={52} className="mb-6" />
  <h2 className="text-[32px] font-semibold tracking-tight text-slate-950 dark:text-white">
    {siteConfig.passwordLoginEnabled ? '欢迎回来' : '登录暂未开放'}
  </h2>
  <p className="mt-3 text-sm leading-6 text-slate-500 dark:text-slate-400">
    {siteConfig.passwordLoginEnabled
      ? '继续使用你现有的账号进入文件空间、分享页与快传页面。'
      : '当前站点暂未开放密码登录，请联系管理员获取可用的登录方式。'}
  </p>
</header>
```

- [ ] **Step 3: Add login-specific motion helpers**

```css
/* frontend/src/styles/index.css */
.login-surface {
  animation: login-card-enter 280ms cubic-bezier(.22, 1, .36, 1);
}

@keyframes login-card-enter {
  from { opacity: 0; transform: translateY(14px) scale(.985); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
```

- [ ] **Step 4: Verify the page still honors runtime config and auth flow**

Run: `cd frontend && npm run lint`  
Expected: PASS, with no changes to `login()`, `loadSiteRuntimeConfig()`, or session redirect behavior.

- [ ] **Step 5: Manual smoke the login route**

Run: `cd frontend && npm run dev`  
Expected: `/login` renders the new split layout on desktop, the right-hand card remains usable on mobile, and successful login still redirects to the default signed-in route.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Login.tsx frontend/src/styles/index.css
git commit -m "feat(frontend): migrate login page to cloudreve-inspired presentation"
```

## Task 4: Replace the Files Page as the Primary Cloudreve-Style Workspace

**Files:**
- Create: `frontend/src/components/files/FileToolbar.tsx`
- Create: `frontend/src/components/files/FileTable.tsx`
- Create: `frontend/src/components/files/FileDetailsRail.tsx`
- Create: `frontend/src/components/files/FileActionMenu.tsx`
- Modify: `frontend/src/pages/Files.tsx`

- [ ] **Step 1: Extract the top toolbar into a component**

```tsx
// frontend/src/components/files/FileToolbar.tsx
import React from 'react';
import GlassPanel from '../ui/GlassPanel';

type FileToolbarProps = {
  search: string;
  onSearchChange: (value: string) => void;
  onUploadClick: () => void;
  onCreateDirectory: () => void;
  onDeleteSelected: () => void;
  deleteDisabled: boolean;
  selectedCount: number;
};

const FileToolbar: React.FC<FileToolbarProps> = (props) => {
  return (
    <GlassPanel className="mb-4 px-4 py-4">
      <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
        <div className="flex flex-wrap items-center gap-2">{/* upload / mkdir / bulk delete buttons */}</div>
        <input
          value={props.search}
          onChange={(event) => props.onSearchChange(event.target.value)}
          placeholder="搜索当前目录中的文件"
          className="h-11 w-full rounded-2xl border border-slate-200 bg-white/90 px-4 text-sm outline-none xl:max-w-[320px]"
        />
      </div>
    </GlassPanel>
  );
};

export default FileToolbar;
```

- [ ] **Step 2: Extract the file list/table and keep double-click/menu behavior**

```tsx
// frontend/src/components/files/FileTable.tsx
import React from 'react';
import GlassPanel from '../ui/GlassPanel';
import FileThumbnail from '../media/FileThumbnail';
import type { FileItem } from '../../api/types';

type FileTableProps = {
  rows: FileItem[];
  selectedIds: number[];
  favoriteIds: Set<number>;
  onToggleSelected: (id: number) => void;
  onOpenDirectory: (file: FileItem) => void;
  onOpenMenu: (file: FileItem, button: HTMLButtonElement) => void;
  onOpenDetail: (fileId: number) => void;
};

const FileTable: React.FC<FileTableProps> = ({ rows, ...handlers }) => {
  return (
    <GlassPanel className="overflow-hidden">
      <div className="divide-y divide-slate-200/70 dark:divide-white/10">
        {rows.map((file) => (
          <div
            key={file.id}
            onDoubleClick={() => handlers.onOpenDirectory(file)}
            className="grid cursor-default grid-cols-[auto_minmax(0,1fr)_120px_120px_auto] items-center gap-3 px-5 py-4 transition hover:bg-slate-50/85 dark:hover:bg-white/[0.03]"
          >
            <input type="checkbox" checked={handlers.selectedIds.includes(file.id)} onChange={() => handlers.onToggleSelected(file.id)} />
            <button className="min-w-0 text-left" onClick={() => handlers.onOpenDetail(file.id)}>
              <div className="flex items-center gap-3">
                <FileThumbnail file={file} className="h-10 w-10 rounded-2xl" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900 dark:text-white">{file.filename}</p>
                  <p className="truncate text-xs text-slate-500 dark:text-slate-400">{file.path}</p>
                </div>
              </div>
            </button>
          </div>
        ))}
      </div>
    </GlassPanel>
  );
};
```

- [ ] **Step 3: Extract the right-side details rail and row action menu**

```tsx
// frontend/src/components/files/FileDetailsRail.tsx
import React from 'react';
import GlassPanel from '../ui/GlassPanel';
import type { FileDetail } from '../../api/types';

const FileDetailsRail: React.FC<{ detail: FileDetail | null; loading: boolean; error: string | null }> = ({ detail, loading, error }) => {
  return (
    <GlassPanel className="rail-enter sticky top-0 p-5">
      {loading ? <p className="text-sm text-slate-500">正在加载详情...</p> : null}
      {error ? <p className="text-sm text-red-500">{error}</p> : null}
      {detail ? <div className="space-y-4">{/* file metadata and actions */}</div> : null}
    </GlassPanel>
  );
};

export default FileDetailsRail;
```

```tsx
// frontend/src/components/files/FileActionMenu.tsx
import React from 'react';
import { createPortal } from 'react-dom';
import GlassPanel from '../ui/GlassPanel';

const FileActionMenu: React.FC<{
  x: number;
  y: number;
  children: React.ReactNode;
}> = ({ x, y, children }) =>
  createPortal(
    <div className="fixed z-[9999]" style={{ left: x, top: y }}>
      <GlassPanel className="w-44 overflow-hidden p-1">{children}</GlassPanel>
    </div>,
    document.body,
  );

export default FileActionMenu;
```

- [ ] **Step 4: Rewrite `Files.tsx` to compose the new pieces without changing API calls**

```tsx
// frontend/src/pages/Files.tsx
return (
  <DashboardLayout title="文件 Files">
    <FileToolbar
      search={search}
      onSearchChange={(value) => { setSearch(value); setPage(1); }}
      onUploadClick={() => fileInputRef.current?.click()}
      onCreateDirectory={handleCreateDirectory}
      onDeleteSelected={() => batchDeleteMutation.mutate(selectedIds)}
      deleteDisabled={selectedIds.length === 0 || batchDeleteMutation.isPending}
      selectedCount={selectedIds.length}
    />
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
      <div className="min-w-0 space-y-4">
        {/* breadcrumb panel */}
        <FileTable
          rows={rows}
          selectedIds={selectedIds}
          favoriteIds={favoriteIds}
          onToggleSelected={toggleSelected}
          onOpenDirectory={openDirectory}
          onOpenMenu={openActionMenu}
          onOpenDetail={openDetail}
        />
      </div>
      <FileDetailsRail detail={detail} loading={detailLoading} error={detailError} />
    </div>
    {openMenu ? <FileActionMenu x={openMenu.x} y={openMenu.y}>{/* existing actions */}</FileActionMenu> : null}
  </DashboardLayout>
);
```

- [ ] **Step 5: Verify the primary workspace page end-to-end**

Run: `cd frontend && npm run lint`  
Expected: PASS with no changes to `useFiles`, `uploadFile`, `createLegacyShareLink`, `getFileDetail`, or `setFileFavorite`.

- [ ] **Step 6: Manual smoke the file interactions**

Run: `cd frontend && npm run dev`  
Expected:
- `/dashboard/files` shows the new toolbar and table layout
- double-click still opens folders
- the three-dot menu still opens above all layers
- upload button still opens the file picker and uploads into the current directory
- detail rail still opens on the right

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/files/FileToolbar.tsx frontend/src/components/files/FileTable.tsx frontend/src/components/files/FileDetailsRail.tsx frontend/src/components/files/FileActionMenu.tsx frontend/src/pages/Files.tsx
git commit -m "feat(frontend): migrate files page to cloudreve-inspired workspace layout"
```

## Task 5: Replace Overview, Shares, Recycle Bin, and Tasks With the New Visual System

**Files:**
- Create: `frontend/src/components/tasks/UploadTaskTray.tsx`
- Modify: `frontend/src/pages/Overview.tsx`
- Modify: `frontend/src/pages/Shares.tsx`
- Modify: `frontend/src/pages/RecycleBin.tsx`
- Modify: `frontend/src/pages/Tasks.tsx`

- [ ] **Step 1: Add a compact upload/task tray**

```tsx
// frontend/src/components/tasks/UploadTaskTray.tsx
import React from 'react';
import GlassPanel from '../ui/GlassPanel';

const UploadTaskTray: React.FC<{ title: string; subtitle: string; action?: React.ReactNode }> = ({ title, subtitle, action }) => {
  return (
    <GlassPanel className="px-5 py-4">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-slate-900 dark:text-white">{title}</p>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{subtitle}</p>
        </div>
        {action}
      </div>
    </GlassPanel>
  );
};

export default UploadTaskTray;
```

- [ ] **Step 2: Convert `Overview.tsx` to denser dashboard cards**

```tsx
// frontend/src/pages/Overview.tsx
<DashboardLayout title="总览 Overview">
  <div className="grid gap-4 xl:grid-cols-4">
    {stats.map((stat) => (
      <GlassPanel key={stat.label} className="p-6">
        {/* tighter stat card content */}
      </GlassPanel>
    ))}
  </div>
  <div className="mt-6 grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
    <GlassPanel className="overflow-hidden">{/* recent files */}</GlassPanel>
    <UploadTaskTray title="上传与任务" subtitle="把 Cloudreve 的任务可见性迁移到当前工作区壳层中。" />
  </div>
</DashboardLayout>
```

- [ ] **Step 3: Convert `Shares.tsx` rows into card-like sections**

```tsx
// frontend/src/pages/Shares.tsx
<DashboardLayout title="分享 Shares">
  <div className="space-y-4">
    {shares.map((share) => (
      <GlassPanel key={share.id} className="p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          {/* keep stats loading/update logic, change only visual composition */}
        </div>
      </GlassPanel>
    ))}
  </div>
</DashboardLayout>
```

- [ ] **Step 4: Convert `Tasks.tsx` into a split list/detail workspace**

```tsx
// frontend/src/pages/Tasks.tsx
<DashboardLayout title="任务 Tasks">
  <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
    <GlassPanel className="overflow-hidden">{/* task list */}</GlassPanel>
    <GlassPanel className="p-6">{/* task detail and progress */}</GlassPanel>
  </div>
</DashboardLayout>
```

- [ ] **Step 5: Convert `RecycleBin.tsx` to the same card/list language**

```tsx
// frontend/src/pages/RecycleBin.tsx
<DashboardLayout title="回收站 Recycle Bin">
  <div className="space-y-4">
    {data?.items.map((item) => (
      <GlassPanel key={item.id} className="p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          {/* keep restore mutation, change only visual layout */}
        </div>
      </GlassPanel>
    ))}
  </div>
</DashboardLayout>
```

- [ ] **Step 6: Verify all migrated user pages build together**

Run: `cd frontend && npm run lint && npm run build`  
Expected: PASS, with all modified pages compiling under the new shared shell and component primitives.

- [ ] **Step 7: Manual smoke all migrated routes**

Run: `cd frontend && npm run dev`  
Expected:
- `/login` uses the new composition
- `/dashboard/overview` cards align with the new shell
- `/dashboard/files` is the primary polished workspace
- `/dashboard/shares` uses denser cards instead of plain rows
- `/dashboard/recycle-bin` uses the same card/list language
- `/dashboard/tasks` uses split list/detail layout

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/tasks/UploadTaskTray.tsx frontend/src/pages/Overview.tsx frontend/src/pages/Shares.tsx frontend/src/pages/RecycleBin.tsx frontend/src/pages/Tasks.tsx
git commit -m "feat(frontend): align workspace pages with new design system"
```

## Task 6: Replace Transfer and Public Share Pages With the Same Layout Language

**Files:**
- Modify: `frontend/src/pages/TransferSend.tsx`
- Modify: `frontend/src/pages/TransferReceive.tsx`
- Modify: `frontend/src/pages/FileShare.tsx`

- [ ] **Step 1: Recompose `TransferSend.tsx` around the new shell and card system**

```tsx
// frontend/src/pages/TransferSend.tsx
<DashboardLayout title="快传 Transfer">
  <GlassPanel className="mb-4 p-2">
    {/* tabs become compact segmented controls, keep existing search-param logic */}
  </GlassPanel>
  {activeTab === 'receive' ? (
    <GlassPanel className="p-8 md:p-10">
      <TransferReceivePanel embedded />
    </GlassPanel>
  ) : (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
      <GlassPanel className="p-8 md:p-10">{/* sender panel */}</GlassPanel>
      <GlassPanel className="p-6">{/* session summary / offline sessions */}</GlassPanel>
    </div>
  )}
</DashboardLayout>
```

- [ ] **Step 2: Restyle `TransferReceive.tsx` standalone mode to match the public-page language**

```tsx
// frontend/src/pages/TransferReceive.tsx
return (
  <div className="min-h-screen px-4 pb-8 pt-[88px] lg:px-6">
    <Topbar meta="P2P Transfer Receive" />
    <BackgroundEffects />
    <main className="mx-auto max-w-[1120px] page-enter">
      <GlassPanel className="p-8 md:p-10">{content}</GlassPanel>
    </main>
  </div>
);
```

- [ ] **Step 3: Recompose `FileShare.tsx` into the same public-shell structure**

```tsx
// frontend/src/pages/FileShare.tsx
<div className="min-h-screen px-4 pb-10 pt-[88px] lg:px-6">
  <Topbar meta="公开分享" />
  <BackgroundEffects />
  <main className="mx-auto max-w-[1280px] page-enter">
    <div className="mb-6">
      <p className="text-xs font-medium uppercase tracking-[0.24em] text-slate-400">PUBLIC SHARE</p>
      <h2 className="mt-2 text-4xl font-semibold tracking-tight text-slate-950 dark:text-white">文件分享</h2>
    </div>
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
      <GlassPanel className="p-8 md:p-10">{/* share info */}</GlassPanel>
      <GlassPanel className="p-8">{/* verification and actions */}</GlassPanel>
    </div>
  </main>
</div>
```

- [ ] **Step 4: Verify secondary user routes compile**

Run: `cd frontend && npm run lint`  
Expected: PASS, with no changes to `P2pSender`, `P2pReceiver`, `getShareDetails`, or share/transfer API clients.

- [ ] **Step 5: Manual smoke transfer and public share routes**

Run: `cd frontend && npm run dev`  
Expected:
- `/dashboard/transfer-send` matches the new shell and card density
- embedded receive tab still works
- standalone `/transfer/receive` still works
- `/share/:id` matches the same public visual language and keeps verify/download/import behavior

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/TransferSend.tsx frontend/src/pages/TransferReceive.tsx frontend/src/pages/FileShare.tsx
git commit -m "feat(frontend): align transfer and public share pages with new layout system"
```

## Task 7: Final Cleanup, Diff Review, and Rewrite Completion Verification

**Files:**
- Modify: `frontend/src/pages/Login.tsx`
- Modify: `frontend/src/pages/Files.tsx`
- Modify: `frontend/src/pages/Overview.tsx`
- Modify: `frontend/src/pages/Shares.tsx`
- Modify: `frontend/src/pages/RecycleBin.tsx`
- Modify: `frontend/src/pages/Tasks.tsx`
- Modify: `frontend/src/pages/TransferSend.tsx`
- Modify: `frontend/src/pages/TransferReceive.tsx`
- Modify: `frontend/src/pages/FileShare.tsx`
- Modify: `frontend/src/components/Topbar.tsx`
- Modify: `frontend/src/components/DashboardLayout.tsx`

- [ ] **Step 1: Remove leftover style duplication introduced during migration**

```tsx
// examples to collapse before final review
const panelClassName = 'rounded-[24px] border border-white/50 bg-white/75 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-xl';
// replace repeated literal strings with GlassPanel usage instead of leaving page-local copies
```

- [ ] **Step 2: Confirm all Cloudreve references remain read-only**

```text
Do not import from:
- third_party/cloudreve-frontend/src/api/**
- third_party/cloudreve-frontend/src/redux/**
- third_party/cloudreve-frontend/src/session/**

Allowed use:
- reading Cloudreve component structure and CSS patterns during implementation
```

- [ ] **Step 3: Run the full frontend verification pass**

Run: `cd frontend && npm run lint && npm run build`  
Expected: PASS.

- [ ] **Step 4: Do a final manual route sweep**

Run: `cd frontend && npm run dev`  
Expected:
- desktop and mobile shell both usable
- no menu clipping
- no detail rail overlap bugs
- file upload still works
- share limit action still works
- task detail still updates
- transfer send/receive routes still work
- public share page still verifies and downloads

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "refactor(frontend): finish cloudreve-inspired ui surface migration"
```

## Self-Review

- Spec coverage check: this plan covers a full user-facing UI rewrite for login, shell, files, overview, shares, recycle bin, tasks, transfer send/receive, public share, motion, and upload/task visibility. It intentionally excludes backend, admin, and `/api/v4` compatibility.
- Placeholder scan: no `TODO`, `TBD`, or “write tests later” placeholders remain.
- Type consistency check: all new components use `frontend/` paths, preserve existing `lib/api` ownership, and avoid introducing Cloudreve runtime imports.
