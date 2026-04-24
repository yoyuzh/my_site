# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Session Startup

Read these files in order before planning, coding, reviewing, or deploying:

1. `memory.md`
2. `backend-next/archtecture.md`
3. `backend-next/api-reference.md`
4. `docs/backend-next/module-dependency-whitelist.md`
5. `docs/backend-next/directory-responsibilities.md`
6. `docs/backend-next/rule-ownership-matrix.md`
7. `AGENTS.md`

## Project Overview

`yoyuzh.xyz` is a full-stack personal site: personal cloud drive, quick transfer, admin console, and Capacitor Android shell.

## Commands

### Frontend (`cd frontend`)

```bash
npm run dev          # Vite dev server
npm run build        # tsc --noEmit then vite build
npm run lint         # tsc --noEmit (this IS the type check — no separate ESLint or typecheck script)
npm run preview      # Vite preview server
```

No `npm run test` or `npm run clean` script exists in the frontend package.json.

### Backend (`cd backend`)

```bash
mvn spring-boot:run                              # default profile (MySQL)
mvn spring-boot:run -Dspring-boot.run.profiles=dev  # dev profile (H2 + mock CQU data)
mvn test
mvn package
```

No dedicated backend lint or typecheck command exists.

### Deploy

```bash
node scripts/deploy-front-oss.mjs                # frontend static deploy to S3
node scripts/deploy-front-oss.mjs --dry-run
node scripts/deploy-front-oss.mjs --skip-build
node scripts/deploy-android-apk.mjs              # full Android APK build + deploy
node scripts/deploy-android-release.mjs          # APK publish only
```

No checked-in backend deploy script. Backend delivery: `mvn package` then `scp` jar + restart via SSH using details from root `.env`.

## Architecture

### Live Runtime (`backend/` + `frontend/`)

**Frontend:** React 18, Vite 5, TypeScript, Tailwind CSS 3, MUI 6, Redux Toolkit, TanStack Query, axios. Pages in `src/pages/`, shared UI in `src/components/`, API calls in `src/api/`, hooks in `src/hooks/`, shared logic in `src/lib/`. Path alias `@/` maps to `src/`.

**Backend:** Spring Boot 3.3.8, Java 17, Maven. The live backend is already partially migrated toward the target module layout — packages like `identity.access`, `files.sharing`, `platform.job`, `transfer.internal` exist in `backend/` alongside legacy packages like `auth`, `files.share`, `admin`.

### Target Architecture (`backend-next/`)

`backend-next/` is the architecture gate and destination map, not the running app. It defines the modular-monolith target:

- **Domain modules:** `identity.access`, `files.{workspace,content,upload,sharing,search}`, `transfer`, `platform.{job,storage}`, `ops.admin`, `app.android`
- **Each module:** `api/` (public cross-module contract) + `internal/{web,application,domain,infra}` (private implementation)
- **Hard rule:** No module may depend on another module's `internal` — only on `api`
- **`backend-next/archtecture.md`** is the authoritative architecture document

### Migration Status

Strangler-style migration in progress (see `docs/plans/2026-04-13-backend-next-gradual-migration.md`). `identity.access` rule ownership has started moving out of legacy `auth` and `admin` packages. Legacy and v2 API routes coexist (e.g., `/api/files/share-links/**` and `/api/v2/shares/**`).

## Repo Conventions

- Root `.env` is the shared local secrets and deploy-config entrypoint; `.env.example` is the template
- `memory.md` is the main continuity file
- Active plans live under `docs/plans/`; historical ones under `docs/archive/plans/`
- Frontend API proxying: `frontend/vite.config.ts` proxies `/api` to `VITE_BACKEND_URL` (default `http://localhost:8080`)
- Backend dev config split between `application.yml` and `application-dev.yml`; dev profile uses H2
- Backend tests: `backend/src/test/java/com/yoyuzh/...` — add tests in matching package
- Frontend currently has no test files

## Guardrails

- Do not run `npm` commands at repo root; frontend commands belong under `frontend/`
- Do not invent backend service names, process managers, remote directories, or restart commands
- Do not echo values from root `.env` in normal responses
- Admin access is decided by runtime `registration.managementRoles`, not a hard-coded admin role
- `PUT /api/admin/settings` only writes the effective writable sections: `registration` and `transfer`
- Frontend production API base is `https://api.yoyuzh.xyz/api` (hardcoded in build config)
- Do not update `backend-next/archtecture.md` or `docs/architecture.md` during routine implementation — only when explicitly requested
- For frontend releases, use `node scripts/deploy-front-oss.mjs` over manual uploads
- For backend releases, package from `backend/` and deploy the jar; do not commit `backend/target/`

## Key Architecture Rules (from `backend-next/archtecture.md`)

Before changing backend code, always decide:

1. **Which module owns this rule?**
2. **Is this change in the correct layer?** (`web` → `application` → `domain` → `infra`)
3. **Am I crossing a module boundary correctly through `api`?**
4. **Am I accidentally turning admin/upload/search/shared into a bypass?**

Hard prohibitions: no controller→repository shortcuts; no cross-`internal` dependencies; `ops.admin` must not bypass domain boundaries; `shared.kernel` must stay small; `files.upload` is ingress only, not final business truth.
