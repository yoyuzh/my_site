# Repository AGENTS

This repository is split across a Java backend, a Vite/React frontend, a small `docs/` area, and utility scripts. Use the project-level agents defined in `.codex/agents/` instead of improvising overlapping roles.

## Real project structure

- `backend/`: Spring Boot 3.3.8, Java 17, Maven, domain packages under `com.yoyuzh.{auth,cqu,files,config,common}`.
- `front/`: Vite 6, React 19, TypeScript, Tailwind CSS v4, route/page code under `src/pages`, reusable UI under `src/components`, shared logic under `src/lib`.
- `docs/`: currently contains implementation plans under `docs/superpowers/plans/`.
- `scripts/`: deployment, migration, smoke, and local startup helpers.

## Command source of truth

Use only commands that already exist in `front/package.json`, `backend/pom.xml`, `backend/README.md`, `front/README.md`, or the checked-in script files.

### Frontend commands (`cd front`)

- `npm run dev`
- `npm run build`
- `npm run preview`
- `npm run clean`
- `npm run lint`
- `npm run test`

Important: in this repo, `npm run lint` runs `tsc --noEmit`. There is no separate ESLint command, and there is no separate `typecheck` script beyond `npm run lint`.

### Backend commands (`cd backend`)

- `mvn spring-boot:run`
- `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- `mvn test`
- `mvn package`

Important: there is no dedicated backend lint command and no dedicated backend typecheck command declared in `backend/pom.xml` or `backend/README.md`. Do not invent one.

### Script files

- `scripts/deploy-front-oss.mjs`
- `scripts/migrate-file-storage-to-oss.mjs`
- `scripts/oss-deploy-lib.mjs`
- `scripts/oss-deploy-lib.test.mjs`
- `scripts/local-smoke.ps1`
- `scripts/start-backend-dev.ps1`
- `scripts/start-frontend-dev.ps1`

If you need one of these, run it explicitly from the file that already exists instead of inventing a new wrapper command.

### Release and deploy commands

- Frontend OSS publish from repo root:
  `node scripts/deploy-front-oss.mjs`
- Frontend OSS dry run from repo root:
  `node scripts/deploy-front-oss.mjs --dry-run`
- Frontend OSS publish without rebuilding from repo root:
  `node scripts/deploy-front-oss.mjs --skip-build`
- Backend package from `backend/`:
  `mvn package`

Important:

- `scripts/deploy-front-oss.mjs` expects OSS credentials from environment variables or `.env.oss.local`.
- The repository does not currently contain a checked-in backend deploy script. Backend delivery is therefore a two-step process: build `backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar`, then upload/restart it via `ssh` or `scp` using the real target host and remote procedure that are available at deploy time.
- Do not invent a backend service name, process manager, remote directory, or restart command. Discover them from the server or ask only if they cannot be discovered safely.

## Role routing

- `orchestrator`: default coordinator. It decides which specialist agent should work next, keeps cross-directory work aligned, and writes the final handoff. It should stay read-only.
- `planner`: planning only. It produces file-level plans, command plans, and sequencing. It should stay read-only.
- `explorer`: investigation only. It maps code paths, current behavior, and relevant configs/tests. It should stay read-only.
- `implementer`: code changes only. It owns edits in `backend/`, `front/`, `scripts/`, or docs, and may update nearby tests when the implementation requires it.
- `tester`: verification only. It runs existing repo-backed commands and reports exact failures or missing commands. It should not rewrite source files.
- `reviewer`: review only. It inspects diffs for correctness, regressions, missing tests, and command coverage gaps. It should stay read-only.
- `deployer`: release and publish only. It builds the frontend and backend using existing commands, runs the checked-in OSS deploy script for the frontend, and handles backend jar upload/restart over SSH when credentials and remote deployment details are available.

## Default workflow

1. Start in `orchestrator`.
2. Use `planner` when the task spans multiple files, multiple layers, or both frontend and backend.
3. Use `explorer` before implementation if the existing behavior or owning module is not obvious.
4. Use `implementer` for the actual code changes.
5. Use `tester` after implementation. Prefer the narrowest real command set that still proves the change.
6. Use `reviewer` before final delivery, especially for cross-layer changes or auth/files/storage flows.
7. Use `deployer` only after code is committed or otherwise ready to ship.

## Repo-specific guardrails

- Do not run `npm` commands at the repository root. This repo has a root `package-lock.json` but no root `package.json`.
- Frontend API proxying is defined in `front/vite.config.ts`, with `VITE_BACKEND_URL` defaulting to `http://localhost:8080`.
- Backend local development behavior is split between `backend/src/main/resources/application.yml` and `application-dev.yml`; the `dev` profile uses H2 and mock CQU data.
- Backend tests already exist under `backend/src/test/java/com/yoyuzh/...`; prefer adding or updating tests in the matching package.
- Frontend tests already exist under `front/src/**/*.test.ts`; keep new tests next to the state or library module they verify.
- For frontend releases, prefer `node scripts/deploy-front-oss.mjs` over ad hoc `ossutil` or manual uploads.
- For backend releases, package from `backend/` and deploy the produced jar; do not commit `backend/target/` artifacts to git unless the user explicitly asks for that unusual workflow.

Directory-level `AGENTS.md` files in `backend/`, `front/`, and `docs/` add more specific rules and override this file where they are more specific.
