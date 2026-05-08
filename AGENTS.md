# Repository AGENTS

This repository is split across a Java backend, a Vite/React frontend, a small `docs/` area, and utility scripts. Use the project-level agents defined in `.codex/agents/` instead of improvising overlapping roles.

## Session startup

- Every new window / new session that starts work in this repository must read `backend-next/archtecture.md`, `backend-next/api-reference.md`, `docs/backend-next/module-dependency-whitelist.md`, `docs/backend-next/directory-responsibilities.md`, and `docs/backend-next/rule-ownership-matrix.md` first before planning, coding, reviewing, or deploying.
- Do not treat repository-local `memory.md` as the default project memory or continuity handoff. Ignore it unless the user explicitly asks to use or update it.
- Treat `backend-next/archtecture.md` as the active architecture document and source of truth for the target module boundaries and runtime structure.
- Treat `docs/backend-next/module-dependency-whitelist.md`, `docs/backend-next/directory-responsibilities.md`, and `docs/backend-next/rule-ownership-matrix.md` as required startup constraint docs for target-backend work.
- Treat `docs/architecture.md` as the legacy architecture reference until it is rewritten; do not use it as the default startup architecture document.
- Do not edit `backend-next/archtecture.md` or `docs/architecture.md` during normal implementation, refactor, review, or handoff work unless the user explicitly asks to update the architecture document itself.
- Treat `backend-next/api-reference.md` as the active backend API reference for target module ownership, endpoint grouping, and migration boundaries.
- Treat `docs/api-reference.md` as the legacy runtime API reference until it is rewritten; use it only when current controller-level route details are needed.

## Real project structure

- `backend/`: Spring Boot 3.3.8, Java 17, Maven. The active runtime package layout is centered on `com.yoyuzh.boot`, `shared.kernel`, `identity.access`, `files.{workspace,content,upload,sharing,search}`, `transfer`, `platform.{job,storage}`, `ops.admin`, `app.android`, and `infra`.
- `frontend/`: Vite 5, React 18, TypeScript, Tailwind CSS 3, route/page code under `src/pages`, reusable UI under `src/components`, shared logic under `src/lib`, and request contracts under `src/api`.
- `docs/`: active project docs, active plans under `docs/plans/`, active backend-next constraints under `docs/backend-next/`, and historical plans under `docs/archive/plans/`.
- `scripts/`: deployment, migration, dependency-check, smoke, and local startup helpers.

## Command source of truth

Use only commands that already exist in `frontend/package.json`, `backend/pom.xml`, `backend/README.md`, `README.md`, or the checked-in script files.

### Frontend commands (`cd frontend`)

- `npm run dev`
- `npm run build`
- `npm run preview`
- `npm run lint`

Important: in this repo, `npm run lint` runs `tsc --noEmit`. There is no separate ESLint command, no separate `typecheck` script beyond `npm run lint`, and no checked-in frontend `clean` or `test` script.

### Backend commands (`cd backend`)

- `mvn spring-boot:run`
- `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- `mvn test`
- `mvn package`

Important: there is no dedicated backend lint command and no dedicated backend typecheck command declared in `backend/pom.xml` or `backend/README.md`. Do not invent one.

### Script files

- `scripts/deploy-android-apk.mjs`
- `scripts/deploy-android-release.mjs`
- `scripts/deploy-front-oss.mjs`
- `scripts/check-backend-internal-deps.py`
- `scripts/migrate-aliyun-oss-to-s3.mjs`
- `scripts/migrate-aliyun-oss-to-s3.test.mjs`
- `scripts/migrate-file-storage-to-oss.mjs`
- `scripts/oss-deploy-lib.mjs`
- `scripts/oss-deploy-lib.test.mjs`
- `scripts/local-smoke.ps1`
- `scripts/start-backend-dev.ps1`
- `scripts/start-frontend-dev.ps1`

If you need one of these, run it explicitly from the file that already exists instead of inventing a new wrapper command.

### Release and deploy commands

- Android APK build + OSS publish from repo root:
  `node scripts/deploy-android-apk.mjs`
- Android APK publish only from repo root:
  `node scripts/deploy-android-release.mjs`
- Frontend OSS publish from repo root:
  `node scripts/deploy-front-oss.mjs`
- Frontend OSS dry run from repo root:
  `node scripts/deploy-front-oss.mjs --dry-run`
- Frontend OSS publish without rebuilding from repo root:
  `node scripts/deploy-front-oss.mjs --skip-build`
- Backend package from `backend/`:
  `mvn package`

Important:

- `scripts/deploy-android-apk.mjs` 会顺序执行前端构建、`npx cap sync android`、Android `assembleDebug`、前端静态站发布，以及独立的 APK 发布脚本，并自动补回 `capacitor-cordova-android-plugins/build.gradle` 里的 Google Maven 镜像配置。
- `scripts/deploy-android-release.mjs` 只负责把 APK 和 `android/releases/latest.json` 发布到 Android 独立对象路径，默认复用文件桶 scope，而不是前端静态桶。
- `scripts/deploy-front-oss.mjs` 现在只发布 `frontend/dist` 静态站资源，不再上传 APK。
- The repository does not currently contain a checked-in backend deploy script. Backend delivery is therefore a two-step process: build `backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar`, then upload/restart it via `ssh` or `scp` using the real target host and remote procedure that are available at deploy time.
- Do not invent a backend service name, process manager, remote directory, or restart command. Discover them from the server or ask only if they cannot be discovered safely.

## Role routing

- Main server note: the primary deployment server is `8v8g` (`103.236.97.248:43471`, `root`).

- `orchestrator`: default coordinator. It decides which specialist agent should work next, keeps cross-directory work aligned, and writes the final handoff. It should stay read-only.
- `planner`: planning only. It produces file-level plans, command plans, and sequencing. It should stay read-only.
- `explorer`: investigation only. It maps code paths, current behavior, and relevant configs/tests. It should stay read-only.
- `implementer`: code changes only. It owns edits in `backend/`, `frontend/`, `scripts/`, or docs, and may update nearby tests when the implementation requires it.
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

## Project-level hard rules

### First-principles thinking

- Start from the original requirement and problem, not from assumptions about the user's preferred implementation path.
- Do not assume the user already knows exactly what they want or how to get there.
- Stay cautious about motive, goal, and scope. If the underlying objective or business target is materially unclear, pause and discuss it with the user before implementation.


### Solution and refactor rule

- Do not propose compatibility-style or patch-style solutions.
- Do not over-design. Use the shortest correct implementation path.
- Do not add fallback, downgrade, or extra solution branches that the user did not ask for.
- Do not propose any solution beyond the user's stated requirement if it could shift business logic.
- Every proposed modification or refactor plan must be logically correct and validated across the full request path before it is presented.

### Clean code standard

- For new code, refactors, and reviews, follow clean-code principles as a hard standard: prefer SOLID, DRY, KISS, YAGNI, clear separation of concerns, composition over inheritance, and dependency inversion where it improves module boundaries.
- Keep modules cohesive, keep functions small and single-purpose, use intention-revealing names, avoid duplicated logic and magic values, and prefer self-documenting code over explanatory noise.
- Comments should explain why or document public contracts when needed, not restate what the code already says.

### Project memory upkeep

- Do not update repository-local `memory.md` as part of normal implementation, refactor, review, or handoff work.
- If a durable project decision needs to be written down in the repo, put it in the relevant plan, architecture, API, or module-constraint document instead of `memory.md`.
- Do not update `backend-next/archtecture.md` or `docs/architecture.md` as part of routine implementation follow-up. These files are reserved for explicit architecture-document changes requested by the user.

## Repo-specific guardrails

- Do not run `npm` commands at the repository root. The repository root is not an application package; frontend commands belong under `frontend/`.
- Frontend API proxying is defined in `frontend/vite.config.ts`, with `VITE_BACKEND_URL` defaulting to `http://127.0.0.1:8080`.
- Backend local development behavior is split between `backend/src/main/resources/application.yml` and `application-dev.yml`; the `dev` profile uses H2 and mock CQU data.
- Backend tests already exist under `backend/src/test/java/com/yoyuzh/...`; prefer adding or updating tests in the matching package.
- Frontend test files are not a guaranteed baseline in this repo. If a task needs frontend tests, first verify the target area already has a runnable test pattern or add one next to the affected module deliberately instead of assuming a pre-existing test suite.
- For frontend releases, prefer `node scripts/deploy-front-oss.mjs` over ad hoc `ossutil` or manual uploads.
- For backend releases, package from `backend/` and deploy the produced jar; do not commit `backend/target/` artifacts to git unless the user explicitly asks for that unusual workflow.

## Debugging Discipline

- When diagnosing environment or download issues, use short probes first: prefer `curl --max-time`, `mvn -q`, `apt-get update`, `mvn dependency:get`, or similar bounded checks before any full build or long download.
- Do not wait indefinitely on a stalled download or network command. If a command shows no progress within a short probe window, stop and inspect the active proxy, DNS, and mirror path before retrying.
- For WSL-based debugging, prefer the native WSL shell plus the current mirror/proxy settings already in place. If a download path is slow, verify whether the proxy path is actually faster before forcing direct access.
- If a package source is unstable, switch to a domestic mirror only after confirming whether the failure is in DNS, proxy routing, or the upstream mirror itself.

Directory-level `AGENTS.md` files in `backend/`, `frontend/`, and `docs/` add more specific rules and override this file where they are more specific.
