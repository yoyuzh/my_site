# Backend AGENTS

This directory is the Spring Boot backend for `yoyuzh.xyz`. Keep changes aligned with the current package layout instead of introducing a new architecture.

## Backend layout

- `src/main/java/com/yoyuzh/boot`: bootstrap, security, global web wiring, and site-edge controllers.
- `src/main/java/com/yoyuzh/shared/kernel`: small shared abstractions only.
- `src/main/java/com/yoyuzh/identity/access`: auth, session, profile, invite code, and identity governance flows.
- `src/main/java/com/yoyuzh/files`: workspace, content, upload, sharing, and search modules.
- `src/main/java/com/yoyuzh/transfer`: browser transfer and remote download flows.
- `src/main/java/com/yoyuzh/platform`: platform job and storage policy modules.
- `src/main/java/com/yoyuzh/ops/admin`: admin governance entrypoints and orchestration.
- `src/main/java/com/yoyuzh/app/android`: Android release endpoints.
- `src/main/java/com/yoyuzh/infra`: cross-cutting technical infrastructure.
- `src/main/resources`: runtime config and logging.
- `src/test/java/com/yoyuzh/...`: matching package-level tests.

## Real backend commands

Run these from `backend/`:

- `mvn spring-boot:run`
- `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- `mvn test`
- `mvn package`

For backend release work:

- The produced artifact is `backend/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar`.
- The repository does not contain a checked-in backend deployment script.
- Deployment therefore means: package locally, then upload and restart via `ssh` / `scp` using the actual remote host and remote process details available at deploy time.

There is no dedicated backend lint command and no dedicated backend typecheck command in the checked-in Maven config or README. If a task asks for lint/typecheck, say that the backend currently does not define those commands.

## Backend rules

- Keep changes aligned with `backend-next/archtecture.md` and the matching docs under `docs/backend-next/`, even when the runtime package migration is still in progress.
- Respect the current module and layer split: `api`, `internal.web`, `internal.application`, `internal.domain`, and `internal.infra`.
- When changing `identity.access`, `files.*`, `transfer`, `platform.*`, or `ops.admin`, check whether an existing test package already covers that area before adding new files elsewhere.
- Respect the existing `dev` profile in `application-dev.yml`; do not hardcode assumptions that bypass H2 behavior.
- If a change affects file storage behavior, note that the repo currently supports local storage and OSS-related migration/deploy scripts.
- Prefer Maven-based verification from this directory instead of ad hoc shell pipelines.
- For deploy work, never invent a remote directory, service name, or restart command. Discover them from the server or ask when discovery is impossible.
