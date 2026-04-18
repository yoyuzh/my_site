# Project Memory

## Product baseline

- Main product line stays on `netdisk + transfer + admin + Android distribution`.
- The live application is still `backend/` plus `front/`.
- `backend-next/` is the target backend skeleton and architecture gate, not the running Spring Boot app.

## Session startup baseline

- New sessions should read `memory.md`, `backend-next/archtecture.md`, `backend-next/api-reference.md`, `docs/backend-next/module-dependency-whitelist.md`, `docs/backend-next/directory-responsibilities.md`, and `docs/backend-next/rule-ownership-matrix.md` first.
- `docs/architecture.md` and `docs/api-reference.md` are now legacy references. Use them only when old runtime detail is needed.
- Historical implementation plans live under `docs/archive/plans` and are not startup docs.

## Durable runtime facts

- Admin access is decided by runtime `registration.managementRoles`, not by a hard-coded admin-role shortcut.
- `PUT /api/admin/settings` only writes the effective writable sections.
- The durable writable admin settings sections are `registration` and `transfer`; other sections are runtime snapshots or read-only views.
- Admin runtime settings are DB-backed and survive restart.
- Frontend test command is `cd front && npm run test`.
- Frontend type check command is `cd front && npm run lint`.

## Backend-next status

- `backend-next/archtecture.md` is the active target architecture document.
- `backend-next/api-reference.md` is the active target API reference.
- Rewrite constraint docs live in:
  - `docs/backend-next/module-dependency-whitelist.md`
  - `docs/backend-next/directory-responsibilities.md`
  - `docs/backend-next/rule-ownership-matrix.md`
- `backend-next/` already has ArchUnit-based guardrails and marker packages, but it is still structure-only. No production code migration has happened there yet.

## Migration direction

- The migration plan is `docs/plans/2026-04-13-backend-next-gradual-migration.md`.
- Strategy is strangler-style: keep `backend/` as the live runtime, move rule ownership gradually toward the target module layout, and use `backend-next/` as the architecture gate and destination map.
- Planned order remains:
  1. guardrails
  2. `identity.access`
  3. `platform.storage` and `platform.job`
  4. `files.workspace` and `files.content`
  5. `files.upload`
  6. `files.sharing` and `files.search`
  7. `transfer`
  8. `ops.admin`
  9. `boot`, `shared.kernel`, `infra` cleanup and final cutover review

## Active architectural tensions

- Legacy `/api/files/**` still mixes workspace, content, legacy upload, and legacy sharing concerns.
- Legacy `/api/files/share-links/**` and target `/api/v2/shares/**` still coexist.
- Legacy `/api/files/upload/**` and target `/api/v2/files/upload-sessions/**` still coexist.
- `ops.admin` is already a separate entry layer, but its internals still need gradual tightening so governance goes through target module APIs only.
