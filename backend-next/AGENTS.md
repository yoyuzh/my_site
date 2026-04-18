# Backend-Next AGENTS

This directory is the standalone target backend skeleton for the rewrite. It is not the active Spring Boot application yet.

## Rules

- New sessions working on the target backend should treat `backend-next/archtecture.md` as the first-read architecture document.
- New sessions working on the target backend should treat `backend-next/api-reference.md` as the first-read API reference.
- New sessions working on the target backend should also read `docs/backend-next/module-dependency-whitelist.md`, `docs/backend-next/directory-responsibilities.md`, and `docs/backend-next/rule-ownership-matrix.md` before planning or coding.
- Do not move code here from `backend/` unless the user explicitly asks for a migration step.
- Keep this tree as structure, ownership, and constraint documentation first.
- Any future implementation in this tree must follow `docs/backend-next/*.md`.
- Do not add compatibility shortcuts that violate module ownership just to mirror the old backend.
