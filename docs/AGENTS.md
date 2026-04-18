# Docs AGENTS

This directory stores active project docs plus active plans under `docs/plans/`. Historical implementation plans should live under `docs/archive/plans/`.

## Docs rules

- Prefer documenting commands that already exist in `front/package.json`, `backend/pom.xml`, `backend/README.md`, `front/README.md`, or checked-in script files.
- Do not introduce placeholder commands such as an imaginary root `npm test`, backend lint script, or standalone frontend typecheck script.
- When documenting validation, state gaps explicitly. In this repo, backend lint/typecheck commands are not defined, and frontend type checking currently happens through `npm run lint`.
- Keep plan or handoff documents tied to actual repo paths like `backend/...`, `front/...`, `scripts/...`, and `docs/...`.
- `backend-next/archtecture.md` is the active architecture document for new-session startup and target-backend work.
- `backend-next/api-reference.md` is the active backend API reference for new-session startup and target-backend work.
- `docs/backend-next/module-dependency-whitelist.md`, `docs/backend-next/directory-responsibilities.md`, and `docs/backend-next/rule-ownership-matrix.md` are required startup constraint docs for target-backend work.
- `docs/architecture.md` is now a legacy architecture reference and should not be treated as the default startup architecture document.
- `docs/api-reference.md` is now a legacy runtime API reference and should not be treated as the default startup API document.
- Do not edit `backend-next/archtecture.md` or `docs/architecture.md` unless the user explicitly asks for an architecture-document update.
