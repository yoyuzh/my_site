# CLAUDE.md

This file provides guidance to Claude Code and similar agents when working in this repository.

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

`yoyuzh.xyz` is a full-stack personal site focused on:

- account/auth flows
- personal cloud drive
- quick transfer
- admin console
- Capacitor Android shell

## Commands

Do not run frontend business commands from the repository root.

### Frontend (`cd front`)

```bash
npm run dev
npm run build
npm run preview
npm run clean
npm run lint
npm run test
```

### Backend (`cd backend`)

```bash
mvn spring-boot:run
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test
mvn package
```

There is no dedicated backend lint or typecheck command.

### Deploy

```bash
node scripts/deploy-front-oss.mjs
node scripts/deploy-front-oss.mjs --dry-run
node scripts/deploy-front-oss.mjs --skip-build
node scripts/deploy-android-apk.mjs
node scripts/deploy-android-release.mjs
```

The deploy scripts now prefer the repository root `.env` file and keep `.env.oss.local` only as a legacy fallback.

## Repo Conventions

- Root `.env` is the shared local secrets and deploy-config entrypoint.
- `.env.example` is the template.
- `memory.md` is the main continuity file.
- Active plans live under `docs/plans/`; historical implementation plans live under `docs/archive/plans/`.

## Known Constraints

- Frontend production still hardcodes `https://api.yoyuzh.xyz/api` in `front/.env.production`.
- Vite build chunk warnings do not currently block release.
- Backend service metadata and SSH-related secrets have been consolidated into root `.env`; do not echo those values in normal responses.

## Migrated Project Guidance (from `AGENTS.md`)

### Repository structure

- `backend/`: Spring Boot 3.3.8, Java 17, Maven, domain packages under `com.yoyuzh.{auth,cqu,files,config,common}`.
- `front/`: Vite 6, React 19, TypeScript, Tailwind CSS v4, route/page code under `src/pages`, reusable UI under `src/components`, shared logic under `src/lib`.
- `docs/`: active project docs and active plans under `docs/plans/`; historical implementation plans live under `docs/archive/plans/`.
- `scripts/`: deployment, migration, smoke, and local startup helpers.

### Command source of truth

Use only commands that already exist in `front/package.json`, `backend/pom.xml`, `backend/README.md`, `front/README.md`, or checked-in scripts.

Important frontend note:

- In this repo, `npm run lint` runs `tsc --noEmit`.
- There is no separate ESLint command.
- There is no separate `typecheck` script beyond `npm run lint`.

Important backend note:

- There is no dedicated backend lint command.
- There is no dedicated backend typecheck command declared in `backend/pom.xml` or `backend/README.md`.

### Script files

- `scripts/deploy-android-apk.mjs`
- `scripts/deploy-android-release.mjs`
- `scripts/deploy-front-oss.mjs`
- `scripts/migrate-file-storage-to-oss.mjs`
- `scripts/oss-deploy-lib.mjs`
- `scripts/oss-deploy-lib.test.mjs`
- `scripts/local-smoke.ps1`
- `scripts/start-backend-dev.ps1`
- `scripts/start-frontend-dev.ps1`

If needed, run checked-in script files directly instead of inventing new wrapper commands.

### Deploy details

- `scripts/deploy-android-apk.mjs` sequentially performs frontend build, `npx cap sync android`, Android `assembleDebug`, frontend static deploy, and standalone APK publish. It also restores Google Maven mirror config in `capacitor-cordova-android-plugins/build.gradle`.
- `scripts/deploy-android-release.mjs` only publishes APK and `android/releases/latest.json` to Android-specific object paths, reusing file-bucket scope by default (not the static frontend bucket).
- `scripts/deploy-front-oss.mjs` only publishes `front/dist` static assets and no longer uploads APK files.
- There is no checked-in backend deploy script. Backend delivery is build jar (`backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar`) then upload/restart via `ssh`/`scp` with real target host/procedure available at deploy time.
- Do not invent backend service names, process manager names, remote directories, or restart commands.

### Role routing

- `orchestrator`: default coordinator, keeps cross-directory work aligned, writes final handoff (read-only).
- `planner`: planning only, produces file-level plans, command plans, and sequencing (read-only).
- `explorer`: investigation only, maps code paths/current behavior/config/tests (read-only).
- `implementer`: code changes only, edits `backend/`, `front/`, `scripts/`, or docs and updates nearby tests when needed.
- `tester`: verification only, runs existing repo-backed commands and reports exact failures/missing commands (no source rewrites).
- `reviewer`: review only, inspects diffs for correctness/regressions/missing tests/command coverage gaps (read-only).
- `deployer`: release and publish only, builds frontend/backend with existing commands, runs checked-in OSS deploy for frontend, and handles backend jar upload/restart over SSH when credentials/details are available.

### Default workflow

1. Start in `orchestrator`.
2. Use `planner` when task spans multiple files/layers or frontend+backend.
3. Use `explorer` before implementation if current behavior/owning module is not obvious.
4. Use `implementer` for code changes.
5. Use `tester` after implementation, with the narrowest real command set that still proves the change.
6. Use `reviewer` before final delivery, especially for cross-layer changes or auth/files/storage flows.
7. Use `deployer` only after code is committed or otherwise ready to ship.

### Project hard rules

#### First-principles thinking

- Start from original requirement/problem, not assumptions about preferred implementation path.
- Do not assume user already knows exactly what they want or how to get there.
- Keep motive/goal/scope explicit; if materially unclear, pause and discuss before implementation.

#### Solution and refactor

- Do not propose compatibility-style or patch-style solutions.
- Do not over-design; use shortest correct implementation path.
- Do not add fallback/downgrade/extra branches not requested by user.
- Do not propose beyond stated requirement if it shifts business logic.
- Validate any proposed modification/refactor across full request path before presenting.

#### Project memory upkeep

- Every task causing a major project change should update `memory.md` in the same turn before handoff.
- Only record high-value, durable memory in `memory.md`.
- Do not update `backend-next/archtecture.md` or `docs/architecture.md` during routine implementation follow-up; only change them when explicitly requested.

### Repo-specific guardrails

- Do not run `npm` commands at repository root; frontend commands belong under `front/`.
- Frontend API proxying is defined in `front/vite.config.ts`; `VITE_BACKEND_URL` defaults to `http://localhost:8080`.
- Backend local dev behavior is split between `backend/src/main/resources/application.yml` and `application-dev.yml`; `dev` profile uses H2 and mock CQU data.
- Backend tests exist under `backend/src/test/java/com/yoyuzh/...`; add/update tests in matching package when needed.
- Frontend tests exist under `front/src/**/*.test.ts`; keep new tests next to related state/lib modules.
- For frontend releases, prefer `node scripts/deploy-front-oss.mjs` over ad hoc `ossutil` or manual upload.
- For backend releases, package under `backend/` and deploy the jar; do not commit `backend/target/` artifacts unless explicitly requested.

### Debugging discipline

- For environment/download diagnosis, use short probes first (`curl --max-time`, `mvn -q`, `apt-get update`, `mvn dependency:get`, etc.) before full builds/long downloads.
- Do not wait indefinitely on stalled network/download commands; if no progress in short probe window, stop and inspect proxy/DNS/mirror path before retrying.
- For WSL debugging, prefer native WSL shell plus existing mirror/proxy settings. If download is slow, verify proxy path is actually faster before forcing direct access.
- If package source is unstable, switch to domestic mirrors only after confirming whether the issue is DNS, proxy routing, or upstream mirror.

Directory-level `AGENTS.md` files in `backend/`, `front/`, and `docs/` add more specific rules and override this file where more specific.

