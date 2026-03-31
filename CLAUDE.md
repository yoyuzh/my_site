# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Session Startup

Read these files in order before planning, coding, reviewing, or deploying:
1. `memory.md` — current project state and handoff context
2. `docs/architecture.md` — authoritative system-level source of truth
3. `docs/api-reference.md` — backend endpoint and auth boundary reference
4. `AGENTS.md` — role routing rules and hard guardrails

## Project Overview

**yoyuzh.xyz** — full-stack personal site with four functional areas:
- Personal cloud drive (netdisk)
- P2P browser-to-browser file transfer (快传 / quick-transfer)
- Account system with invite-code registration
- Admin console

Frontend is hosted on Aliyun OSS (static). Backend runs as `my-site-api.service` on a Linux server at `1.14.49.201`, jar at `/opt/yoyuzh/yoyuzh-portal-backend.jar`.

## Commands

**Do not run `npm` at the repo root.** The root has no real `package.json`.

### Frontend (`cd front`)
```bash
npm run dev       # Vite dev server on port 3000
npm run build     # Production build
npm run preview   # Preview built output
npm run lint      # tsc --noEmit  (this IS the type-check; no ESLint)
npm run test      # node --import tsx --test src/**/*.test.ts
npm run clean     # rm -rf dist
```

### Backend (`cd backend`)
```bash
mvn spring-boot:run                                   # Default profile (MySQL)
mvn spring-boot:run -Dspring-boot.run.profiles=dev    # Dev profile (H2, mock data)
mvn test
mvn package       # Produces backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar
```

There is no backend lint or typecheck command.

### Deploy
```bash
node scripts/deploy-front-oss.mjs              # Build + upload frontend to Aliyun OSS
node scripts/deploy-front-oss.mjs --dry-run    # Preview without uploading
node scripts/deploy-front-oss.mjs --skip-build # Upload only
```
Requires OSS credentials from env vars or `.env.oss.local`.

Backend deploy: `mvn package`, then SCP the jar to `/opt/yoyuzh/yoyuzh-portal-backend.jar` on the server and restart `my-site-api.service`. No checked-in backend deploy script exists.

## Architecture

### Tech Stack
| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite 6, Tailwind CSS v4, react-router-dom v7 |
| Admin UI | react-admin v5 (lazy-loaded at `/admin/*`) |
| Backend | Spring Boot 3.3.8, Java 17, Maven, Spring Security + JWT |
| ORM | Spring Data JPA / Hibernate |
| DB (prod) | MySQL 8 |
| DB (dev) | H2 (in-file, MySQL compat mode via `application-dev.yml`) |
| File storage | Aliyun OSS (`yoyuzh-files2`, Chengdu region) |

### Frontend Structure (`front/src/`)
- `App.tsx` — router entrypoint
- `lib/` — pure logic modules, each with a co-located `*.test.ts`
  - `api.ts` — all backend API calls
  - `session.ts` — JWT session management
  - `transfer-runtime.ts`, `transfer-protocol.ts` — WebRTC logic
  - `files-upload.ts` — upload orchestration
- `pages/` — page-level components (`Files.tsx`, `Transfer.tsx`, `TransferReceive.tsx`, `Login.tsx`, `Overview.tsx`, `FileShare.tsx`)
- `admin/` — react-admin console with custom `data-provider.ts`
- `components/layout/`, `components/ui/` — shared UI primitives
- `auth/AuthProvider.tsx` — auth context

Frontend API proxy: all `/api/*` calls during dev proxy to `VITE_BACKEND_URL` (default `http://localhost:8080`) via `front/vite.config.ts`. Production hardcodes `https://api.yoyuzh.xyz/api` in `front/.env.production`.

### Backend Package Structure (`com.yoyuzh`)
- `auth` — JWT, login, register, refresh token, single-device session enforcement
- `files` — file metadata CRUD, upload/download, share links; `files/storage/` abstracts local vs OSS
- `transfer` — quick-transfer signaling API only (no file content relay)
- `admin` — admin-only user/file management APIs
- `config` — `SecurityConfig`, CORS, `JwtAuthenticationFilter`
- `common` — shared exceptions and utilities

### Key Design Decisions
- **Single-device login**: `activeSessionId` in user DB record matched against `sid` JWT claim on every request. New login invalidates old device's access token immediately.
- **Quick-transfer**: backend is signaling-only. File bytes travel via WebRTC `DataChannel` between browsers — no server bandwidth consumed. Offline mode uploads to OSS for 7-day retention.
- **File storage abstraction**: `FileContentStorage` interface decouples metadata logic from disk/OSS. Upload flow: `initiate` → direct OSS upload → `complete`. Falls back to proxy upload on failure.
- **Invite-code registration**: single-use codes auto-rotate after consumption; current code displayed in admin console.

### Security Boundaries (`SecurityConfig`)
- Public: `/api/auth/**`, `/api/transfer/**`, `GET /api/files/share-links/{token}`
- Requires auth: `/api/files/**`, `/api/user/**`, `/api/admin/**`

## Testing

Frontend tests live next to the module they test (`src/lib/api.test.ts` beside `src/lib/api.ts`). Run a single test file:
```bash
cd front && node --import tsx --test src/lib/api.test.ts
```

Backend tests mirror the main package structure under `backend/src/test/java/com/yoyuzh/`. Run a single test class:
```bash
cd backend && mvn test -Dtest=ClassName
```

Dev profile (`-Dspring-boot.run.profiles=dev`) uses H2; H2 console available at `http://localhost:8080/h2-console`.

## Codex Agent Roles

Agents are defined in `.codex/agents/` and configured in `.codex/config.toml`. Use the workflow from `AGENTS.md`:

| Agent | Role | Mode |
|---|---|---|
| `orchestrator` | Default coordinator, routes to specialists | read-only |
| `planner` | File-level and command plans before code changes | read-only |
| `explorer` | Maps code paths and existing behavior | read-only |
| `implementer` | Owns code edits and nearby test updates | read-write |
| `tester` | Runs repo-backed commands, reports failures | read-only |
| `reviewer` | Reviews diffs for bugs, regressions, test gaps | read-only |
| `deployer` | Builds and publishes frontend to OSS; packages backend jar for SSH delivery | read-write |

Default workflow: orchestrator → planner (if multi-file) → explorer (if behavior unclear) → implementer → tester → reviewer → deployer.

## Known Constraints

- VS Code "final field not initialized" errors on backend are Lombok/Java LS false positives — check Lombok extension and annotation processor, not source code.
- Frontend chunk size warnings from Vite do not block builds or deploys.
- `api.yoyuzh.xyz` has TLS/SNI instability on some networks; this is not a backend code issue.
- Production frontend bundle hardcodes the API base URL — API subdomain issues surface as "network error" or "login failed" on the frontend.
- Server credentials are in local file `账号密码.txt`; never commit or output their contents.
