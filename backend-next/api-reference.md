# Backend-Next API Reference

## 1. Purpose

This document is the active API reference for the target backend architecture under `backend-next/`.

It is not a controller-by-controller dump. Its job is to answer three questions fast:

- Which target module owns this route group?
- What is the auth boundary for this capability?
- Is this route group already the target mainline, still a legacy compatibility surface, or only a migration target?

If this document conflicts with the running backend implementation, use the current runtime code as the final detail source:

- `backend/src/main/java/com/yoyuzh/**/**/*Controller.java`
- `backend/src/main/java/com/yoyuzh/config/SecurityConfig.java`

Use `docs/api-reference.md` only when you need the legacy runtime endpoint details that are not yet rewritten into the target module map.

---

## 2. Source-of-Truth Order

When working on backend API changes, follow this order:

1. `backend-next/archtecture.md`
2. `backend-next/api-reference.md`
3. `docs/backend-next/module-dependency-whitelist.md`
4. `docs/backend-next/directory-responsibilities.md`
5. `docs/backend-next/rule-ownership-matrix.md`
6. Current runtime controllers and security config in `backend/`

---

## 3. Auth Boundary Summary

| Boundary | Meaning | Typical route groups |
| --- | --- | --- |
| Public | No login required at the security boundary | `/`, `/api/app/android/**`, public share read routes, transfer lookup/join/open download routes, site ping |
| Authenticated | Requires bearer token / logged-in user | `/api/user/**`, most `/api/files/**`, `/api/v2/files/**`, `/api/v2/tasks/**`, private share management/import |
| Admin | Requires authenticated user plus admin governance access | `/api/admin/**` |

Notes:

- `ops.admin` is a governance entry only. Admin routes must call domain module APIs, not bypass them.
- `transfer` has mixed public and authenticated actions. Public exposure at the security layer does not mean every transfer action is anonymous.
- Public share viewing/downloading belongs to `files.sharing`, not to `files.workspace`.

---

## 4. Route Ownership Matrix

| Target module | Primary route groups | Auth boundary | Migration status | Ownership notes |
| --- | --- | --- | --- | --- |
| `identity.access` | `/api/auth/**`, `/api/user/**` | Public + Authenticated | Current mainline | Owns account lifecycle, login, refresh, session validity, profile and password changes. |
| `app.android` | `/api/app/android/**` | Public | Current mainline | Android distribution and client-specific adaptation only. |
| `files.workspace` | `/api/files/mkdir`, `/api/files/list`, `/api/files/recent`, `/api/files/{id}/rename`, `/api/files/{id}/move`, `/api/files/{id}/copy`, `DELETE /api/files/{id}`, `/api/files/recycle-bin/**` | Authenticated | Current mainline, target owner unchanged | Owns logical tree, path legality, same-parent naming, recycle lifecycle, restore semantics. |
| `files.content` | `/api/files/download/**`, content read/write completion behind file operations, content metadata participation in tasks | Authenticated or Public through sharing/transfer | Current mainline, target owner being clarified | Owns content asset truth, versions, blob references, and physical cleanup preconditions. |
| `files.upload` | `/api/v2/files/upload-sessions/**`, legacy `/api/files/upload/**` | Authenticated | v2 is target mainline; legacy routes are compatibility | Upload owns ingress session/process control only. Final workspace node creation must return to `files.workspace`; final content registration must return to `files.content`. |
| `files.sharing` | `/api/v2/shares/**`, legacy `/api/files/share-links/**` | Public + Authenticated | v2 is target mainline; legacy routes are compatibility | Owns share grants, password, expiry, quotas, view/download/import policy, and share visits. |
| `files.search` | `/api/v2/files/search`, file-event read models such as `/api/v2/files/events` when shaped as file retrieval support | Authenticated | Target mainline for new search API | Must depend on other modules through `api` only; must not become a table-bypass module. |
| `transfer` | `/api/transfer/**` | Mixed Public + Authenticated | Current mainline | Owns transfer sessions, pickup code flow, online/offline send-receive state, and transfer import eligibility. |
| `platform.job` | `/api/v2/tasks/**` | Authenticated | Current mainline | Owns async task lifecycle, retry, lease, and execution status, not the business truth of file/share/transfer rules. |
| `platform.storage` | Storage governance APIs under `/api/admin/storage-policies/**` and storage capability decisions consumed by upload/content/workspace flows | Admin for governance endpoints; internal API for other modules | Current governance entry, target owner unchanged | Owns storage policies, upload capability matrix, placement rules, size limits, and migration policy. |
| `ops.admin` | `/api/admin/**` except storage policy domain truth itself | Admin | Current mainline, must be tightened during migration | Owns admin orchestration, audit entry points, and governance use cases. Must not bypass domain ownership. |
| `boot` / site edge | `/`, `/api/v2/site/ping` | Public | Current mainline | Health and bootstrapping edge only; no business rule ownership. |

---

## 5. Current Mainlines vs Compatibility Surfaces

### Current target-aligned mainlines

- `identity.access`: `/api/auth/**`, `/api/user/**`
- `files.upload`: `/api/v2/files/upload-sessions/**`
- `files.sharing`: `/api/v2/shares/**`
- `files.search`: `/api/v2/files/search`
- `platform.job`: `/api/v2/tasks/**`
- `transfer`: `/api/transfer/**`
- `ops.admin`: `/api/admin/**`
- `app.android`: `/api/app/android/**`

### Still-live legacy compatibility surfaces

- Legacy file upload: `/api/files/upload`, `/api/files/upload/initiate`, `/api/files/upload/complete`
- Legacy share links: `/api/files/share-links/**`
- Legacy all-in-one file controller surface: large parts of `/api/files/**`

### Module ownership that must not move during migration

- Final file tree truth stays in `files.workspace`
- Final content asset truth stays in `files.content`
- Upload strategy decision stays in `platform.storage`
- Share access decision stays in `files.sharing`
- Transfer receive eligibility stays in `transfer`
- Admin remains an orchestrator, not an owner of file/share/transfer/storage truth

---

## 6. Admin Route Interpretation

`/api/admin/**` should be read as governance entrypoints grouped by target owner:

| Admin route family | Target owner behind the route | Notes |
| --- | --- | --- |
| `/api/admin/summary`, `/api/admin/audits` | `ops.admin` | Governance read models and audit entry. |
| `/api/admin/settings/**` | `ops.admin` coordinating `identity.access`, `transfer`, and future platform modules | Must not mutate core objects by bypass. |
| `/api/admin/users/**` | `ops.admin` calling `identity.access.api` | User/role/status governance. |
| `/api/admin/files/**` | `ops.admin` calling `files.workspace.api` and `files.content.api` | File governance, not direct repository mutation. |
| `/api/admin/shares/**` | `ops.admin` calling `files.sharing.api` | Share governance. |
| `/api/admin/tasks/**` | `ops.admin` calling `platform.job.api` | Job governance. |
| `/api/admin/storage-policies/**` | `platform.storage` domain with `ops.admin` as entry | Storage governance is a first-class platform domain, not a utility bag. |

---

## 7. Route-to-Module Refactor Guidance

Use these rules when moving existing controllers out of the legacy package layout:

1. Keep the public HTTP route stable unless the user explicitly asks to change the contract.
2. Move rule ownership first, controller package placement second.
3. If a legacy controller currently mixes multiple target domains, split the application/domain logic before splitting the external route.
4. Do not let `files.upload` finish a file by itself.
5. Do not let `ops.admin` read or write another module's internal repositories directly.
6. Prefer adding target `api` contracts before moving callers.

---

## 8. Known Active Tensions

- `/api/files/**` still mixes workspace, content, legacy upload, and legacy sharing concerns in the running backend.
- `/api/transfer/**` is security-public at the edge but contains operations that still require user identity deeper in the stack.
- `/api/admin/**` is already a separate route family, but its internals still need gradual migration toward strict domain API orchestration.
- `docs/api-reference.md` still contains the fuller legacy endpoint list; this file is the shorter target ownership map for new sessions.

---

## 9. Practical Review Checklist

Before adding or changing an endpoint, ask:

1. Which target module owns the rule behind this endpoint?
2. Is the endpoint exposing a target mainline capability or only preserving a legacy surface?
3. Is auth decided at the right layer: public, authenticated, or admin governance?
4. Is `ops.admin` orchestrating through APIs instead of bypassing internals?
5. Is `files.upload` only handling ingress, not final business truth?
