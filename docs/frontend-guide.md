# Frontend Guide

## 1. Purpose

This document combines two concerns that used to be split across separate files:

- current frontend page and route orchestration
- recommended component replacement boundaries

Its goal is to keep one practical source of truth for frontend structure without scattering low-value documentation.

## 2. Scope Boundaries

This guide only describes frontend structure, UI composition, and component replacement boundaries.
It does not change backend business ownership defined in `backend-next/archtecture.md` and `docs/backend-next/*.md`.

## 3. Route Inventory

### Public routes

| Route | Page component | Notes |
| --- | --- | --- |
| `/login` | `frontend/src/pages/Login.tsx` | login and register entry |
| `/share/:token` | `frontend/src/pages/FileShare.tsx` | public share access page |

### App routes

| Route | Page component | Notes |
| --- | --- | --- |
| `/overview` | `frontend/src/pages/Overview.tsx` | user overview |
| `/files` | `frontend/src/pages/files/FilesPage.tsx` | workspace main page |
| `/tasks` | `frontend/src/pages/Tasks.tsx` | async task page |
| `/shares` | `frontend/src/pages/Shares.tsx` | my shares |
| `/recycle-bin` | `frontend/src/pages/RecycleBin.tsx` | recycle bin |
| `/transfer` | `frontend/src/transfer/pages/TransferPage.tsx` | transfer |

### Admin routes

| Route | Page component | Current state |
| --- | --- | --- |
| `/admin/dashboard` | `frontend/src/admin/dashboard.tsx` | implemented |
| `/admin/settings` | `frontend/src/admin/settings.tsx` | placeholder or partial |
| `/admin/filesystem` | `frontend/src/admin/filesystem.tsx` | placeholder or partial |
| `/admin/storage-policies` | `frontend/src/admin/storage-policies-list.tsx` | implemented |
| `/admin/users` | `frontend/src/admin/users-list.tsx` | implemented |
| `/admin/files` | `frontend/src/admin/files-list.tsx` | implemented |
| `/admin/file-blobs` | `frontend/src/admin/fileblobs.tsx` | placeholder or partial |
| `/admin/shares` | `frontend/src/admin/shares.tsx` | placeholder or partial |
| `/admin/tasks` | `frontend/src/admin/tasks.tsx` | placeholder or partial |
| `/admin/oauth-apps` | `frontend/src/admin/oauthapps.tsx` | placeholder or partial |

## 4. Shared Shells And Global UI

### Desktop shell

File: `frontend/src/components/layout/Layout.tsx`

Contains:
- left navigation
- account area
- theme toggle
- logout
- `TaskSummaryPanel`
- `UploadCenter`
- main content `<Outlet />`

### Mobile shell

File: `frontend/src/mobile-components/MobileLayout.tsx`

Contains:
- top controls
- bottom navigation
- theme toggle
- logout
- `UploadCenter`
- page content `<Outlet />`

### Admin shell

File: `frontend/src/admin/AdminLayout.tsx`

Contains:
- admin sub-navigation
- admin content container

## 5. Important Page Composition

### `/login`

Main elements:
- theme toggle
- login/register mode switch
- username/password inputs
- register fields for email, phone, invite code, password, confirm password
- quick entry buttons such as transfer and dev/admin shortcuts

### `/share/:token`

Main elements:
- share metadata
- password gate when needed
- download action
- import-to-netdisk action

### `/overview`

Main elements:
- account/storage/upload-limit cards
- quick links
- recent files
- recent tasks

### `/files`

Main structure:
- directory tree column
- file list/work area
- file detail panel

Core interactions that must remain intact:
- directory loading and cache
- SSE refresh for current directory
- search isolation from directory cache
- upload file/folder
- create folder
- list/grid switch
- rename/move/copy/delete
- recycle entry

### `/transfer`

Main elements:
- send file area
- session state and pickup flow
- receive/import actions

## 6. Component Replacement Principles

### Safe to replace with OSS components

- pure presentation components
- pure interaction shells
- tables, charts, trees, split panes, drag-and-drop, upload selectors, media players
- anything that does not decide permissions or business rules

### Must stay in business code

- auth and permission decisions
- upload mode selection: `PROXY`, `DIRECT_SINGLE`, `DIRECT_MULTIPART`
- share access, download, and import decisions
- workspace path legality and duplicate-name rules
- task retry/cancel/state-machine decisions

## 7. Recommended OSS Components

| Component | Best fit | Replace only |
| --- | --- | --- |
| `@tanstack/react-table` | admin tables, tasks, shares, list-heavy pages | table rendering and interaction |
| `@tanstack/react-virtual` | large lists in files/admin/task views | virtualization only |
| `react-dropzone` | file upload entry and transfer send area | file selection and drag-drop |
| `react-arborist` | files left directory tree | tree interaction only |
| `dnd-kit` | visual drag-drop for move/sort flows | drag behavior only |
| `react-resizable-panels` | files workspace multi-panel layout | panel sizing/layout |
| `recharts` | admin dashboard charts | visualization only |
| `vidstack` | media preview/player | player UI only |
| `hls.js` | future HLS playback | playback transport only |

## 8. Recommended Priority

### P1
- `@tanstack/react-table`
- `react-dropzone`
- `react-resizable-panels`
- `recharts`

### P2
- `react-arborist`
- `@tanstack/react-virtual`
- `dnd-kit`

### P3
- `vidstack`
- `hls.js`

## 9. Maintenance Rule

If a future frontend doc does not add a new source of truth and only restates implementation details already captured here, it should not become a separate top-level document.
