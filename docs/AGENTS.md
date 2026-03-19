# Docs AGENTS

This directory currently stores implementation plans under `docs/superpowers/plans/`. Keep docs here concrete and repository-specific.

## Docs rules

- Prefer documenting commands that already exist in `front/package.json`, `backend/pom.xml`, `backend/README.md`, `front/README.md`, or checked-in script files.
- Do not introduce placeholder commands such as an imaginary root `npm test`, backend lint script, or standalone frontend typecheck script.
- When documenting validation, state gaps explicitly. In this repo, backend lint/typecheck commands are not defined, and frontend type checking currently happens through `npm run lint`.
- Keep plan or handoff documents tied to actual repo paths like `backend/...`, `front/...`, `scripts/...`, and `docs/...`.
