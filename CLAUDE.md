# CLAUDE.md

This file provides guidance to Claude Code and similar agents when working in this repository.

## Session Startup

Read these files in order before planning, coding, reviewing, or deploying:

1. `memory.md`
2. `docs/architecture.md`
3. `docs/api-reference.md`
4. `AGENTS.md`

Use `docs/agents/handoff.md` only as supplemental background when the primary docs are not enough.

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
- `docs/agents/` stores only the supplemental handoff doc; `CLAUDE.md` itself stays at the repository root.

## Known Constraints

- Frontend production still hardcodes `https://api.yoyuzh.xyz/api` in `front/.env.production`.
- Vite build chunk warnings do not currently block release.
- Backend service metadata and SSH-related secrets have been consolidated into root `.env`; do not echo those values in normal responses.
